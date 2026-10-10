package io.ahad.connectorhub.server;
import io.ahad.connectorhub.core.*;
import io.ahad.connectorhub.connectors.SafeXml;
import com.sun.net.httpserver.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
/** Clearly labeled loopback-only simulator. These fixtures are not real customer or industrial data. */
public final class DemoUpstream implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger flaky=new AtomicInteger();
    private final AtomicInteger writes=new AtomicInteger();
    public DemoUpstream(int port)throws IOException {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),32);
        server.setExecutor(executor);
        server.createContext("/",this::handle);
        server.start();
    }
    public int port() {
        return server.getAddress().getPort();
    }
    private void handle(HttpExchange e)throws IOException {
        try {
            String path=e.getRequestURI().getPath();
            byte[] body=e.getRequestBody().readNBytes(2_097_153);
            int status=200;
            String content="application/json";
            String text;
            switch(path) {
                case "/customers"->text=Json.stringify(Map.of("items",List.of(Map.of("id",101,"name","Nordwerk Manufacturing","city","Hamburg"),Map.of("id",102,"name","Rhein Systems","city","Cologne"),Map.of("id",103,"name","Isar Automation","city","Munich")),"source","SIMULATED"));
                case "/echo"->text=Json.stringify(Map.of("method",e.getRequestMethod(),"body",new String(body,StandardCharsets.UTF_8),"query",e.getRequestURI().getRawQuery()==null?"":e.getRequestURI().getRawQuery(),"source","SIMULATED"));
                case "/graphql"->text=Json.stringify(Map.of("data",Map.of("workOrders",List.of(Map.of("id","WO-1042","status","READY","priority",2),Map.of("id","WO-1043","status","IN_REVIEW","priority",1))),"source","SIMULATED"));
                case "/odata/Products"->text=Json.stringify(Map.of("@odata.context","http://127.0.0.1:"+port()+"/odata/$metadata#Products","value",List.of(Map.of("ID",1,"Name","Sensor module","Price",149),Map.of("ID",2,"Name","Control unit","Price",349)),"source","SIMULATED"));
                case "/soap"-> {
                    var doc=SafeXml.parse(new String(body,StandardCharsets.UTF_8));
                    int a=Integer.parseInt(doc.getElementsByTagNameNS("urn:demo","a").item(0).getTextContent()),b=Integer.parseInt(doc.getElementsByTagNameNS("urn:demo","b").item(0).getTextContent());
                    content="text/xml";
                    text="<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><AddResponse xmlns=\"urn:demo\"><AddResult>"+(a+b)+"</AddResult></AddResponse></s:Body></s:Envelope>";
                }
                case "/flaky"-> {
                    if(flaky.incrementAndGet()%3!=0) {
                        status=503;
                        text="{\"error\":\"simulated temporary outage\"}";
                    }
                    else text="{\"recovered\":true,\"source\":\"SIMULATED\"}";
                }
                case "/slow"-> {
                    try {
                        Thread.sleep(1500);
                    }
                    catch(InterruptedException x) {
                        Thread.currentThread().interrupt();
                    }
                    text="{\"slow\":true}";
                }
                case "/redirect"-> {
                    status=302;
                    e.getResponseHeaders().set("Location","http://169.254.169.254/latest/meta-data/");
                    text="{}";
                }
                case "/download"-> {
                    content="text/csv";
                    text="id,name\n1,Simulation\n";
                }
                case "/invalid-json"-> {
                    text="not json";
                }
                case "/big"-> {
                    content="text/plain";
                    text="x".repeat(2_097_153);
                }
                case "/graphql-errors"->text="{\"errors\":[{\"message\":\"simulated failure\"}]}";
                case "/write-ambiguous"-> {
                    writes.incrementAndGet();
                    status=503;
                    text="{}";
                }
                case "/write-count"->text=Json.stringify(Map.of("count",writes.get()));
                case "/auth/basic","/auth/bearer","/auth/key"-> {
                    String actual=e.getRequestHeaders().getFirst(path.endsWith("key")?"X-Demo-Key":"Authorization");
                    String expected=path.endsWith("basic")?"Basic ZGVtbzp0ZXN0":path.endsWith("bearer")?"Bearer fixture-secret":"fixture-secret";
                    boolean accepted=expected.equals(actual);
                    status=accepted?200:401;
                    text=Json.stringify(Map.of("accepted",accepted));
                }
                case "/soap-fault","/soap-fault-500"-> {
                    if(path.endsWith("500"))status=500;
                    content="text/xml";
                    text="<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><s:Fault><faultcode>s:Server</faultcode><faultstring>Simulated fault</faultstring></s:Fault></s:Body></s:Envelope>";
                }
                default-> {
                    status=404;
                    text="{}";
                }
            }
            byte[] output=text.getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type",content+"; charset=utf-8");
            if(e.getRequestMethod().equals("HEAD")) {
                e.sendResponseHeaders(status,-1);
            }
            else {
                e.sendResponseHeaders(status,output.length);
                e.getResponseBody().write(output);
            }
        }
        catch(Exception ex) {
            byte[] bytes="{\"error\":\"simulation input rejected\"}".getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(400,bytes.length);
            e.getResponseBody().write(bytes);
        }
        finally {
            e.close();
        }
    }
    @Override public void close() {
        server.stop(0);
        executor.close();
    }
}
