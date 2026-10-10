package io.ahad.connectorhub.connectors;
import io.ahad.connectorhub.core.*;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.regex.*;
/** REST, SOAP, GraphQL and OData share one bounded transport, without redirects or trust-all TLS. */
public final class HttpConnector implements Connector,AutoCloseable {
    private final String type;
    private final NetworkPolicy policy;
    private final HttpClient client;
    private static final int MAX_BYTES=2_097_152;
    private static final Set<String> RESERVED=Set.of("host","content-length","connection","transfer-encoding","upgrade","authorization","proxy-authorization","cookie","set-cookie");
    public HttpConnector(String type,NetworkPolicy policy) {
        this.type=type;
        this.policy=policy;
        client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_2).build();
    }
    @Override public String type() {
        return type;
    }
    @Override public Set<String> operations() {
        return Set.of("READ","CREATE","UPDATE","DELETE","EXECUTE");
    }
    @Override public boolean retrySafe(Map<String,Object> provider) {
        return (type.equals("REST")||type.equals("ODATA"))&&Set.of("GET","HEAD").contains(Json.text(Json.map(provider,"config"),"method","GET"))&&Json.text(provider,"operation","").equals("READ");
    }
    @Override public Object execute(Context c) {
        var config=c.config();
        String method=Json.text(config,"method",c.operation().equals("READ")?"GET":"POST").toUpperCase(Locale.ROOT);
        if(type.equals("SOAP")||type.equals("GRAPHQL"))method="POST";
        if(!Set.of("GET","HEAD","POST","PUT","PATCH","DELETE","OPTIONS").contains(method))throw HubException.invalid();
        String base=Json.text(c.connectionConfig(),"baseUrl","");
        URI baseUri=NetworkPolicy.shape(base);
        if(!Set.of("http","https").contains(baseUri.getScheme())||baseUri.getRawQuery()!=null)throw HubException.invalid();
        String path=renderPath(Json.text(config,"path",""),c.parameters());
        if(path.startsWith("//")||path.contains("://")||path.contains("#")||path.contains("?")||path.contains("\\"))throw new HubException("DESTINATION_BLOCKED",403);
        String address=base.replaceAll("/+$","")+(path.isEmpty()?"":("/"+path.replaceAll("^/+","")));
        var query=Json.object(Parameters.template(Json.map(config,"query"),c.parameters()));
        var headers=new LinkedHashMap<String,String>();
        for(var e:Json.map(config,"headers").entrySet())setHeader(headers,e.getKey(),String.valueOf(Parameters.template(e.getValue(),c.parameters())));
        for(Object o:Json.list(c.provider(),"parameters")) {
            var p=Json.object(o);
            String name=Json.text(p,"name","");
            if(!c.parameters().containsKey(name))continue;
            String target=Json.text(p,"targetName",name);
            Object value=c.parameters().get(name);
            switch(Json.text(p,"location","BODY")) {
                case "QUERY"->query.put(target,value);
                case "HEADER"->setHeader(headers,target,value==null?"":value.toString());
                default-> {
                }
            }
        }
        if(type.equals("ODATA")&&method.equals("GET")) {
            query.putIfAbsent("$top",c.pageSize());
            query.putIfAbsent("$skip",c.pageIndex()*c.pageSize());
        }
        if(!query.isEmpty()) {
            var pairs=new ArrayList<String>();
            for(var e:query.entrySet())if(e.getValue()!=null) {
                if(e.getValue() instanceof List<?> l) {
                    for(Object item:l)pairs.add(enc(e.getKey())+"="+enc(String.valueOf(item)));
                }
                else pairs.add(enc(e.getKey())+"="+enc(String.valueOf(e.getValue())));
            }
            address+="?"+String.join("&",pairs);
        }
        URI uri=policy.approve(address);
        if(!NetworkPolicy.origin(uri).equals(NetworkPolicy.origin(baseUri)))throw new HubException("DESTINATION_BLOCKED",403);
        var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(c.timeoutMillis())).header("X-Correlation-ID",c.correlationId());
        headers.forEach(builder::header);
        applyAuth(builder,c);
        String contentType=Json.text(config,"contentType","application/json");
        byte[] body=new byte[0];
        if(type.equals("SOAP")) {
            String xml=Parameters.interpolate(Json.text(config,"envelope",""),c.parameters(),true);
            SafeXml.parse(xml);
            String action=Json.text(config,"soapAction","");
            if(action.indexOf('"')>=0||action.indexOf('\r')>=0||action.indexOf('\n')>=0)throw HubException.invalid();
            if(Json.text(config,"soapVersion","1.1").equals("1.2"))contentType="application/soap+xml; charset=UTF-8"+(action.isBlank()?"":"; action=\""+action+"\"");
            else {
                contentType="text/xml; charset=UTF-8";
                builder.header("SOAPAction","\""+action+"\"");
            }
            body=xml.getBytes(StandardCharsets.UTF_8);
        }
        else if(type.equals("GRAPHQL")) {
            String document=Json.text(config,"document","");
            if(document.isBlank())throw HubException.invalid();
            var graph=new LinkedHashMap<String,Object>();
            graph.put("query",document);
            graph.put("variables",Parameters.template(Json.map(config,"variables"),c.parameters()));
            String op=Json.text(config,"operationName","");
            if(!op.isBlank())graph.put("operationName",op);
            body=Json.stringify(graph).getBytes(StandardCharsets.UTF_8);
        }
        else if(!method.equals("GET")&&!method.equals("HEAD")) {
            Object value=Parameters.template(config.getOrDefault("body",new LinkedHashMap<>()),c.parameters());
            String mode=Json.text(config,"bodyMode","JSON");
            switch(mode) {
                case "JSON"->body=Json.stringify(value).getBytes(StandardCharsets.UTF_8);
                case "TEXT"->body=String.valueOf(value).getBytes(StandardCharsets.UTF_8);
                case "FORM"-> {
                    contentType="application/x-www-form-urlencoded";
                    var fields=new ArrayList<String>();
                    for(var e:Json.object(value).entrySet())fields.add(enc(e.getKey())+"="+enc(e.getValue()==null?"":String.valueOf(e.getValue())));
                    body=String.join("&",fields).getBytes(StandardCharsets.UTF_8);
                }
                case "MULTIPART"-> {
                    String boundary="ch-"+UUID.randomUUID();
                    contentType="multipart/form-data; boundary="+boundary;
                    body=multipart(Json.object(value),boundary);
                }
                default->throw HubException.invalid();
            }
        }
        if(body.length>MAX_BYTES)throw new HubException("BODY_TOO_LARGE",413);
        builder.setHeader("Content-Type",contentType);
        builder.method(method,body.length==0?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
        CompletableFuture<HttpResponse<byte[]>> future=client.sendAsync(builder.build(),info->new LimitedBody(MAX_BYTES));
        try {
            var response=future.get(c.timeoutMillis(),TimeUnit.MILLISECONDS);
            int status=response.statusCode();
            if(status>=300&&status<400)throw new HubException("UPSTREAM_REDIRECT",502);
            if(status==429||status==502||status==503||status==504)throw new HubException("UPSTREAM_UNAVAILABLE",502,true);
            if(type.equals("SOAP")&&status==500) {
                try {
                    if(SafeXml.hasSoapFault(SafeXml.parse(new String(response.body(),StandardCharsets.UTF_8))))throw new HubException("SOAP_FAULT",502);
                }
                catch(HubException fault) {
                    if(fault.code().equals("SOAP_FAULT"))throw fault;
                }
            }
            if(status<200||status>=300)throw new HubException("UPSTREAM_REJECTED",502);
            String output=Json.text(config,"responseType",type.equals("SOAP")?"XML":"JSON");
            byte[] bytes=response.body();
            String text=new String(bytes,StandardCharsets.UTF_8);
            Object data;
            if(output.equals("BINARY")) {
                data=Map.of("contentBase64",Base64.getEncoder().encodeToString(bytes),"contentType",response.headers().firstValue("Content-Type").orElse("application/octet-stream"),"filename","download.bin");
            }
            else if(bytes.length==0)data=null;
            else if(output.equals("XML")) {
                var doc=SafeXml.parse(text);
                if(type.equals("SOAP")&&SafeXml.hasSoapFault(doc))throw new HubException("SOAP_FAULT",502);
                data=SafeXml.json(text);
            }
            else if(output.equals("TEXT"))data=text;
            else if(output.equals("JSON")) {
                try {
                    data=Json.parse(text);
                }
                catch(HubException e) {
                    throw new HubException("UPSTREAM_FORMAT",502);
                }
                if(type.equals("GRAPHQL")&&data instanceof Map<?,?> m&&m.get("errors") instanceof List<?> errors&&!errors.isEmpty())throw new HubException("GRAPHQL_ERROR",502);
            }
            else throw HubException.invalid();
            var result=new LinkedHashMap<String,Object>();
            result.put("data",data);
            result.put("status",status);
            var selectedHeaders=new LinkedHashMap<String,String>();
            for(String h:List.of("content-type","etag","last-modified","retry-after"))response.headers().firstValue(h).ifPresent(v->selectedHeaders.put(h,v));
            result.put("headers",selectedHeaders);
            return result;
        }
        catch(TimeoutException e) {
            future.cancel(true);
            throw new HubException("UPSTREAM_TIMEOUT",504,true);
        }
        catch(InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new HubException("INTERRUPTED",503);
        }
        catch(ExecutionException e) {
            Throwable cause=e.getCause();
            while(cause!=null) {
                if(cause instanceof HubException h)throw h;
                cause=cause.getCause();
            }
            throw new HubException("UPSTREAM_UNAVAILABLE",502,true);
        }
    }
    @Override public Object discover(Context c) {
        String xml=Json.text(c.config(),"wsdl","");
        if(type.equals("SOAP")&&!xml.isBlank())return SafeXml.wsdl(xml);
        return Map.of("type",type,"operations",operations(),"remoteSchemaFetch",false);
    }
    private static void applyAuth(HttpRequest.Builder b,Context c) {
        String auth=Json.text(c.connectionConfig(),"authType","NONE");
        var s=c.secrets();
        switch(auth) {
            case "NONE"-> {
            }
            case "BASIC"->b.header("Authorization","Basic "+Base64.getEncoder().encodeToString((Json.text(s,"username","")+":"+Json.text(s,"password","")).getBytes(StandardCharsets.UTF_8)));
            case "BEARER"->b.header("Authorization","Bearer "+Json.text(s,"token",""));
            case "API_KEY"-> {
                String h=Json.text(c.connectionConfig(),"apiKeyHeader","X-API-Key");
                if(!h.matches("[A-Za-z0-9-]{1,64}")||RESERVED.contains(h.toLowerCase(Locale.ROOT)))throw HubException.invalid();
                b.header(h,Json.text(s,"apiKey",""));
            }
            default->throw HubException.invalid();
        }
    }
    private static void setHeader(Map<String,String> out,String key,String value) {
        if(!key.matches("[A-Za-z0-9-]{1,64}")||RESERVED.contains(key.toLowerCase(Locale.ROOT))||value.indexOf('\r')>=0||value.indexOf('\n')>=0||value.length()>8192)throw new HubException("HEADER_BLOCKED",400);
        out.put(key,value);
    }
    private static String enc(String s) {
        return URLEncoder.encode(s,StandardCharsets.UTF_8).replace("+","%20");
    }
    public static String renderPath(String text,Map<String,Object> parameters) {
        var m=Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}").matcher(text);
        var b=new StringBuffer();
        while(m.find()) {
            Object v=parameters.get(m.group(1));
            if(v==null)throw new HubException("MISSING_BINDING",400);
            String part=v.toString();
            if(part.equals(".")||part.equals(".."))throw HubException.invalid();
            m.appendReplacement(b,Matcher.quoteReplacement(enc(part)));
        }
        m.appendTail(b);
        return b.toString();
    }
    private static byte[] multipart(Map<String,Object> fields,String boundary) {
        try {
            var out=new ByteArrayOutputStream();
            for(var e:fields.entrySet()) {
                if(!e.getKey().matches("[A-Za-z0-9_-]{1,64}"))throw HubException.invalid();
                out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+e.getKey()+"\"").getBytes(StandardCharsets.UTF_8));
                if(e.getValue() instanceof Map<?,?>) {
                    var f=Json.object(e.getValue());
                    String name=Json.text(f,"filename","file.bin").replaceAll("[^A-Za-z0-9._-]","_");
                    String contentType=Json.text(f,"contentType","application/octet-stream");
                    if(!contentType.matches("[A-Za-z0-9.+_-]+/[A-Za-z0-9.+_-]+"))throw HubException.invalid();
                    out.write(("; filename=\""+name+"\"\r\nContent-Type: "+contentType+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    byte[] bytes;
                    try {
                        bytes=Base64.getDecoder().decode(Json.text(f,"contentBase64",""));
                    }
                    catch(IllegalArgumentException x) {
                        throw HubException.invalid();
                    }
                    out.write(bytes);
                }
                else {
                    out.write("\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                    out.write(String.valueOf(e.getValue()).getBytes(StandardCharsets.UTF_8));
                }
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                if(out.size()>MAX_BYTES)throw new HubException("BODY_TOO_LARGE",413);
            }
            out.write(("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        }
        catch(IOException e) {
            throw new IllegalStateException(e);
        }
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        final CompletableFuture<byte[]> result=new CompletableFuture<>();
        final ByteArrayOutputStream out=new ByteArrayOutputStream();
        final int limit;
        Flow.Subscription subscription;
        LimitedBody(int limit) {
            this.limit=limit;
        }
        public CompletionStage<byte[]> getBody() {
            return result;
        }
        public void onSubscribe(Flow.Subscription s) {
            subscription=s;
            result.whenComplete((r,e)-> {
                if(result.isCancelled())s.cancel();
            }
            );
            s.request(1);
        }
        public void onNext(List<ByteBuffer> items) {
            for(ByteBuffer b:items) {
                if(out.size()+b.remaining()>limit) {
                    subscription.cancel();
                    result.completeExceptionally(new HubException("UPSTREAM_TOO_LARGE",502));
                    return;
                }
                byte[] bytes=new byte[b.remaining()];
                b.get(bytes);
                out.writeBytes(bytes);
            }
            subscription.request(1);
        }
        public void onError(Throwable e) {
            result.completeExceptionally(e);
        }
        public void onComplete() {
            result.complete(out.toByteArray());
        }
    }
    @Override public void close() {
        client.close();
    }
}
