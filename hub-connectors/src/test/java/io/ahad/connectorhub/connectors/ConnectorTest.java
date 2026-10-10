package io.ahad.connectorhub.connectors;
import io.ahad.connectorhub.core.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import static io.ahad.connectorhub.core.TestKit.*;
public final class ConnectorTest {
    static final TestKit T=new TestKit("hub-connectors");
    static final Identity ADMIN=new Identity("test","tenant-a","ADMIN",Set.of());
    static Connector.Context context(String operation,Map<String,Object> params,Map<String,Object> page,Identity identity) {
        return new Connector.Context(Map.of("id","local"),Map.of("operation",operation,"config",Map.of("collection","people","body",Map.of("name","${name}"))),params,page,identity,"test-correlation");
    }
    public static void main(String[] args)throws Exception {
        xml();
        network();
        redis();
        local();
        metadata();
        T.finish(args.length==0?"build/reports/connectors.xml":args[0]);
    }
    static void xml() {
        T.test("XML namespace local names",()->eq("{\"root\":{\"v\":\"7\"}}",Json.stringify(SafeXml.json("<x:root xmlns:x='urn:test'><x:v>7</x:v></x:root>"))));
        T.test("XML repeated elements become array",()->eq("{\"root\":{\"v\":[\"1\",\"2\"]}}",Json.stringify(SafeXml.json("<root><v>1</v><v>2</v></root>"))));
        T.test("XML attributes and text",()-> {
            var m=Json.map(Json.object(SafeXml.json("<r id='x'>text</r>")),"r");
            eq("x",m.get("@id"));
            eq("text",m.get("#text"));
        }
        );
        T.test("XML CDATA retained as text",()->eq("<notMarkup>",Json.object(SafeXml.json("<r><![CDATA[<notMarkup>]]></r>")).get("r")));
        T.test("XML rejects external file entity",()->error("XML_INVALID",()->SafeXml.parse("<!DOCTYPE r [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><r>&x;</r>")));
        T.test("XML rejects external network DTD",()->error("XML_INVALID",()->SafeXml.parse("<!DOCTYPE r SYSTEM 'http://127.0.0.1:1/secret'><r/>")));
        T.test("XML rejects entity expansion bomb",()->error("XML_INVALID",()->SafeXml.parse("<!DOCTYPE r [<!ENTITY x 'abc'>]><r>&x;</r>")));
        T.test("XML malformed input rejected",()->error("XML_INVALID",()->SafeXml.parse("<r><a></r>")));
        T.test("XML depth bound",()->error("XML_INVALID",()->SafeXml.json("<r>".repeat(67)+"x"+"</r>".repeat(67))));
        T.test("XML size bound",()->error("XML_INVALID",()->SafeXml.parse(" ".repeat(2097153))));
        for(String ns:List.of("http://schemas.xmlsoap.org/soap/envelope/","http://www.w3.org/2003/05/soap-envelope"))T.test("SOAP namespace fault "+ns,()->yes(SafeXml.hasSoapFault(SafeXml.parse("<s:Envelope xmlns:s='"+ns+"'><s:Body><s:Fault/></s:Body></s:Envelope>"))));
        T.test("Non SOAP Fault name not misclassified",()->yes(!SafeXml.hasSoapFault(SafeXml.parse("<r><Fault/></r>"))));
        T.test("WSDL discovers operations without fetching imports",()-> {
            String wsdl="<definitions xmlns='http://schemas.xmlsoap.org/wsdl/' xmlns:s='http://schemas.xmlsoap.org/wsdl/soap/'><import location='http://127.0.0.1:1/no-fetch'/><portType name='Calculator'><operation name='Add'/></portType><binding name='B'><operation name='Add'><s:operation soapAction='urn:Add'/></operation></binding><service name='S'><port name='P'><s:address location='http://example.invalid/soap'/></port></service></definitions>";
            var d=SafeXml.wsdl(wsdl);
            eq(1,Json.list(d,"operations").size());
            eq(1,Json.list(d,"actions").size());
            eq(false,d.get("remoteImportsFollowed"));
        }
        );
    }
    static void network() {
        T.test("Explicit local destination accepted only in private mode",()->eq("127.0.0.1",new NetworkPolicy(Set.of("http://127.0.0.1:1234"),true,List.of()).approve("http://127.0.0.1:1234/path").getHost()));
        T.test("Private address denied without private opt in",()->error("DESTINATION_BLOCKED",()->new NetworkPolicy(Set.of("http://127.0.0.1:1234"),false,List.of()).approve("http://127.0.0.1:1234")));
        T.test("Allowlist does not permit a different port",()->error("DESTINATION_BLOCKED",()->new NetworkPolicy(Set.of("http://127.0.0.1:1234"),true,List.of()).approve("http://127.0.0.1:1235")));
        T.test("Cloud metadata link local denied even if listed",()->error("DESTINATION_BLOCKED",()->new NetworkPolicy(Set.of("http://169.254.169.254"),true,List.of()).approve("http://169.254.169.254")));
        T.test("Wildcard bind cannot be destination",()->error("DESTINATION_BLOCKED",()->new NetworkPolicy(Set.of("http://0.0.0.0"),true,List.of()).approve("http://0.0.0.0")));
        T.test("Origin default port normalized",()->eq("https://example.org:443",NetworkPolicy.origin(NetworkPolicy.shape("https://example.org/a"))));
        T.test("Credentials in URL rejected",()->error("INVALID_INPUT",()->NetworkPolicy.shape("https://user:password@example.org")));
        T.test("File URL rejected",()->error("INVALID_INPUT",()->NetworkPolicy.shape("file:///etc/passwd")));
        T.test("JDBC prefix deny by default",()->error("DESTINATION_BLOCKED",()->new NetworkPolicy(Set.of(),false,List.of()).approveJdbc("jdbc:h2:mem:test")));
        T.test("JDBC INIT execution rejected",()->error("DESTINATION_BLOCKED",()->new NetworkPolicy(Set.of(),false,List.of("jdbc:h2:mem:")).approveJdbc("jdbc:h2:mem:test;INIT=RUNSCRIPT FROM 'x'")));
        T.test("JDBC embedded password rejected",()->error("DESTINATION_BLOCKED",()->new NetworkPolicy(Set.of(),false,List.of("jdbc:sqlserver:")).approveJdbc("jdbc:sqlserver://db;password=secret")));
        T.test("JDBC operator prefix accepted",()->new NetworkPolicy(Set.of(),false,List.of("jdbc:postgresql://db:5432/app")).approveJdbc("jdbc:postgresql://db:5432/app"));
    }
    static Object resp(String s)throws IOException {
        return RedisConnector.read(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)),0);
    }
    static void redis() {
        T.test("RESP simple string",()->eq("OK",resp("+OK\r\n")));
        T.test("RESP integer",()->eq(42L,resp(":42\r\n")));
        T.test("RESP bulk string",()->eq("hello",resp("$5\r\nhello\r\n")));
        T.test("RESP null bulk",()->eq(null,resp("$-1\r\n")));
        T.test("RESP nested arrays",()->eq(List.of("a",List.of(1L)),resp("*2\r\n$1\r\na\r\n*1\r\n:1\r\n")));
        T.test("RESP upstream error is sanitized",()->error("REDIS_ERROR",()->resp("-ERR contains-sensitive-server-text\r\n")));
        T.test("RESP refuses oversized bulk",()->error("UPSTREAM_TOO_LARGE",()->resp("$2097153\r\n")));
        T.test("RESP refuses oversized array",()->error("UPSTREAM_TOO_LARGE",()->resp("*1001\r\n")));
        T.test("RESP invalid negative size",()->error("UPSTREAM_FORMAT",()->resp("$-2\r\n")));
        T.test("RESP invalid integer",()->error("UPSTREAM_FORMAT",()->resp(":oops\r\n")));
        T.test("RESP command lengths use UTF8 bytes",()-> {
            var out=new ByteArrayOutputStream();
            RedisConnector.command(new ByteArrayInputStream("+OK\r\n".getBytes(StandardCharsets.US_ASCII)),out,List.of("GET","\u00fc"));
            yes(out.toString(StandardCharsets.UTF_8).contains("$2\r\n\u00fc\r\n"));
        }
        );
    }
    static void local()throws Exception {
        try(Catalog cat=new Catalog(Files.createTempDirectory("local-adapter-"),Crypto.randomBytes(32))) {
            var c=new LocalConnector(cat);
            T.test("LOCAL create persisted record",()-> {
                var r=Json.map(Json.object(c.execute(context("CREATE",Map.of("id","one","name","Alice"),Map.of(),ADMIN))),"data");
                eq("Alice",r.get("name"));
                eq(1,Json.integer(r,"version",0));
            }
            );
            T.test("LOCAL duplicate ID rejected",()->error("CONFLICT",()->c.execute(context("CREATE",Map.of("id","one","name","Alice"),Map.of(),ADMIN))));
            T.test("LOCAL read by ID",()->eq(1,Json.list(Json.object(c.execute(context("READ",Map.of("id","one"),Map.of(),ADMIN))),"data").size()));
            T.test("LOCAL cross tenant isolation",()->eq(0,Json.list(Json.object(c.execute(context("READ",Map.of(),Map.of(),new Identity("other","tenant-b","ADMIN",Set.of())))),"data").size()));
            T.test("LOCAL stale update fails",()->error("VERSION_CONFLICT",()->c.execute(context("UPDATE",Map.of("id","one","name","Bob","expectedVersion",0),Map.of(),ADMIN))));
            T.test("LOCAL valid update increments version",()->eq(2,Json.integer(Json.map(Json.object(c.execute(context("UPDATE",Map.of("id","one","name","Bob","expectedVersion",1),Map.of(),ADMIN))),"data"),"version",0)));
            T.test("LOCAL filters apply before pagination",()->eq(1,Json.list(Json.object(c.execute(context("READ",Map.of(),Map.of("filters",List.of(Map.of("field","name","op","eq","value","Bob")),"size",1),ADMIN))),"data").size()));
            T.test("LOCAL missing record update fails",()->error("NOT_FOUND",()->c.execute(context("UPDATE",Map.of("id","missing","name","Bob","expectedVersion",1),Map.of(),ADMIN))));
            T.test("LOCAL stale delete fails",()->error("VERSION_CONFLICT",()->c.execute(context("DELETE",Map.of("id","one","expectedVersion",1),Map.of(),ADMIN))));
            T.test("LOCAL guarded delete succeeds",()-> {
                c.execute(context("DELETE",Map.of("id","one","expectedVersion",2),Map.of(),ADMIN));
                eq(0,Json.list(Json.object(c.execute(context("READ",Map.of(),Map.of(),ADMIN))),"data").size());
            }
            );
            T.test("LOCAL discovery is collection scoped",()->eq(List.of("people"),c.discover(context("READ",Map.of(),Map.of(),ADMIN))));
            T.test("Page size bounded",()->error("INVALID_INPUT",()->context("READ",Map.of(),Map.of("size",501),ADMIN).pageSize()));
            T.test("Negative page rejected",()->error("INVALID_INPUT",()->context("READ",Map.of(),Map.of("index",-1),ADMIN).pageIndex()));
        }
    }
    static void metadata() {
        T.test("Mongo absence is explicit rather than simulated success",()-> {
            var m=new MongoConnector(new NetworkPolicy(Set.of(),false,List.of()));
            if(!m.available())error("DRIVER_MISSING",()->m.execute(context("READ",Map.of(),Map.of(),ADMIN)));
            else yes(m.available());
        }
        );
        T.test("SOAP never automatically retried",()-> {
            try(var c=new HttpConnector("SOAP",new NetworkPolicy(Set.of(),false,List.of()))) {
                yes(!c.retrySafe(Map.of("operation","READ")));
            }
        }
        );
        T.test("REST GET is safe retry candidate",()-> {
            try(var c=new HttpConnector("REST",new NetworkPolicy(Set.of(),false,List.of()))) {
                yes(c.retrySafe(Map.of("operation","READ","config",Map.of("method","GET"))));
                yes(!c.retrySafe(Map.of("operation","CREATE","config",Map.of("method","POST"))));
            }
        }
        );
    }
}
