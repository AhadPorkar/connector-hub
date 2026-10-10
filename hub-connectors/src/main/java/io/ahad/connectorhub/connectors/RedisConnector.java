package io.ahad.connectorhub.connectors;
import io.ahad.connectorhub.core.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.*;
/** Small bounded RESP2 string adapter. Not a replacement for a cluster-aware Redis client. */
public final class RedisConnector implements Connector {
    private final NetworkPolicy policy;
    public RedisConnector(NetworkPolicy policy) {
        this.policy=policy;
    }
    @Override public String type() {
        return "REDIS";
    }
    @Override public Set<String> operations() {
        return Set.of("READ","CREATE","UPDATE","DELETE");
    }
    @Override public boolean retrySafe(Map<String,Object> p) {
        return Json.text(p,"operation","").equals("READ");
    }
    @Override public Object execute(Context c) {
        URI uri=policy.approve(Json.text(c.connectionConfig(),"url",""));
        if(!Set.of("redis","rediss").contains(uri.getScheme()))throw HubException.invalid();
        String key=Json.text(c.parameters(),"key","");
        if(key.isBlank()||key.length()>200)throw HubException.invalid();
        String namespace=Json.text(c.connectionConfig(),"namespace","default");
        if(!namespace.matches("[A-Za-z0-9_-]{1,64}"))throw HubException.invalid();
        String physical="ch:"+c.identity().tenant()+":"+namespace+":"+key;
        try(Socket socket=socket(uri,c.timeoutMillis()); var in=new BufferedInputStream(socket.getInputStream()); var out=new BufferedOutputStream(socket.getOutputStream())) {
            String password=Json.text(c.secrets(),"password","");
            String user=Json.text(c.secrets(),"username","");
            if(!password.isEmpty())command(in,out,user.isEmpty()?List.of("AUTH",password):List.of("AUTH",user,password));
            int database=Json.integer(c.connectionConfig(),"database",0);
            if(database<0||database>15)throw HubException.invalid();
            if(database!=0)command(in,out,List.of("SELECT",Integer.toString(database)));
            Object result=switch(c.operation()) {
                case "READ"->command(in,out,List.of("GET",physical));
                case "DELETE"->command(in,out,List.of("DEL",physical));
                case "CREATE","UPDATE"-> {
                    if(!c.parameters().containsKey("value"))throw new HubException("REQUIRED_PARAMETER",400);
                    String value=c.parameters().get("value") instanceof String s?s:Json.stringify(c.parameters().get("value"));
                    var args=new ArrayList<>(List.of("SET",physical,value,c.operation().equals("CREATE")?"NX":"XX"));
                    int ttl=Json.integer(c.config(),"ttlSeconds",0);
                    if(ttl<0||ttl>31_536_000)throw HubException.invalid();
                    if(ttl>0) {
                        args.add("EX");
                        args.add(Integer.toString(ttl));
                    }
                    Object response=command(in,out,args);
                    if(response==null)throw new HubException("CONFLICT",409);
                    yield response;
                }
                default->throw HubException.invalid();
            }
            ;
            var response=new LinkedHashMap<String,Object>();
            response.put("data",result);
            response.put("key",key);
            return response;
        }
        catch(SocketTimeoutException e) {
            throw new HubException("UPSTREAM_TIMEOUT",504,true);
        }
        catch(IOException e) {
            throw new HubException("UPSTREAM_UNAVAILABLE",502,true);
        }
    }
    private static Socket socket(URI u,int timeout)throws IOException {
        Socket s=new Socket();
        s.connect(new InetSocketAddress(u.getHost(),u.getPort()<0?6379:u.getPort()),Math.min(timeout,5000));
        s.setSoTimeout(timeout);
        if(u.getScheme().equals("rediss")) {
            try {
                SSLSocket tls=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(s,u.getHost(),u.getPort()<0?6379:u.getPort(),true);
                SSLParameters params=tls.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");
                tls.setSSLParameters(params);
                tls.startHandshake();
                return tls;
            }
            catch(IOException e) {
                s.close();
                throw e;
            }
        }
        return s;
    }
    public static Object command(InputStream in,OutputStream out,List<String> args)throws IOException {
        out.write(("*"+args.size()+"\r\n").getBytes(StandardCharsets.US_ASCII));
        for(String a:args) {
            byte[] b=a.getBytes(StandardCharsets.UTF_8);
            if(b.length>2_097_152)throw new HubException("BODY_TOO_LARGE",413);
            out.write(("$"+b.length+"\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(b);
            out.write('\r');
            out.write('\n');
        }
        out.flush();
        return read(in,0);
    }
    public static Object read(InputStream in,int depth)throws IOException {
        if(depth>16)throw new HubException("UPSTREAM_FORMAT",502);
        int marker=in.read();
        if(marker<0)throw new EOFException();
        String line=line(in);
        return switch(marker) {
            case '+'->line;
            case '-'->throw new HubException("REDIS_ERROR",502);
            case ':'-> {
                try {
                    yield Long.parseLong(line);
                }
                catch(NumberFormatException e) {
                    throw new HubException("UPSTREAM_FORMAT",502);
                }
            }
            case '$'-> {
                int count=count(line);
                if(count==-1)yield null;
                if(count>2_097_152)throw new HubException("UPSTREAM_TOO_LARGE",502);
                byte[] bytes=in.readNBytes(count);
                if(bytes.length!=count||in.read()!='\r'||in.read()!='\n')throw new EOFException();
                yield new String(bytes,StandardCharsets.UTF_8);
            }
            case '*'-> {
                int count=count(line);
                if(count==-1)yield null;
                if(count>1000)throw new HubException("UPSTREAM_TOO_LARGE",502);
                var list=new ArrayList<>();
                for(int i=0; i<count; i++)list.add(read(in,depth+1));
                yield list;
            }
            default->throw new HubException("UPSTREAM_FORMAT",502);
        }
        ;
    }
    private static int count(String s) {
        try {
            int n=Integer.parseInt(s);
            if(n< -1)throw new NumberFormatException();
            return n;
        }
        catch(NumberFormatException e) {
            throw new HubException("UPSTREAM_FORMAT",502);
        }
    }
    private static String line(InputStream in)throws IOException {
        var b=new ByteArrayOutputStream();
        while(b.size()<8192) {
            int x=in.read();
            if(x<0)throw new EOFException();
            if(x=='\r') {
                if(in.read()!='\n')throw new EOFException();
                return b.toString(StandardCharsets.UTF_8);
            }
            b.write(x);
        }
        throw new HubException("UPSTREAM_TOO_LARGE",502);
    }
}
