package io.ahad.connectorhub.core;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static io.ahad.connectorhub.core.TestKit.*;
/** Real assertions over the dependency-free core, runnable on a clean JDK 21. */
public final class CoreTest {
    static final TestKit T = new TestKit("hub-core");
    public static void main(String[] args) throws Exception {
        json();
        parameters();
        sql();
        crypto();
        storage();
        roles();
        resilience();
        T.finish(args.length == 0 ? "build/reports/core.xml" : args[0]);
    }
    static void json() {
        for (String text : List.of("null", "true", "false", "0", "-7", "1.25", "1e3", "[]", "{}", "[1,\"x\",null]", "{\"a\":{\"b\":[true]}}", "\"\\uD83D\\uDE80\"")) {
            T.test("JSON round trip " + text, () -> eq(Json.canonical(Json.parse(text)), Json.canonical(Json.parse(Json.stringify(Json.parse(text))))));
        }
        String[] bad = {
            "", "01", "+1", "1.", ".5", "1e", "NaN", "Infinity", "true false", "[1,]", "{\"a\":1,}", "{a:1}", "{\"a\":1,\"a\":2}", "\"\\uD800\"", "\"\\uDC00\"", "\"a\nb\"", "\"\\x20\"", "/*x*/1", "1e10001", "[".repeat(66) + "0" + "]".repeat(66)
        }
        ;
        for (int i=0; i<bad.length; i++) {
            String input=bad[i];
            int n=i;
            T.test("JSON rejects malformed input " + n, () -> error("INVALID_INPUT",()->Json.parse(input)));
        }
        T.test("JSON deterministic key order",()->eq("{\"a\":2,\"z\":1}",Json.canonical(Map.of("z",1,"a",2))));
        T.test("JSON precision preserved",()->eq(new BigDecimal("9007199254740993"),Json.parse("9007199254740993")));
        T.test("JSON rejects nonfinite output",()->error("INVALID_INPUT",()->Json.stringify(Double.NaN)));
        T.test("JSON integer must be exact",()->error("INVALID_INPUT",()->Json.integer(Map.of("x",1.2),"x",0)));
        T.test("JSON copy detaches nested mutation",()-> {
            var a=Json.obj("{\"x\":{\"v\":1}}");
            var b=Json.object(Json.copy(a));
            Json.map(b,"x").put("v",2);
            eq(1,Json.integer(Json.map(a,"x"),"v",0));
        }
        );
    }
    static Map<String,Object> def(String name,String type,String source,Object value) {
        var d=new LinkedHashMap<String,Object>();
        d.put("name",name);
        d.put("type",type);
        d.put("source",source);
        if(value!=null)d.put("value",value);
        return d;
    }
    static void parameters() {
        Object[][] values= {
            {
                "STRING",42,"42"
            }
            , {
                "INTEGER","23",23L
            }
            , {
                "DECIMAL","12.50",new BigDecimal("12.50")
            }
            , {
                "BOOLEAN","TRUE",true
            }
            , {
                "DATE","2026-10-10","2026-10-10"
            }
            , {
                "DATETIME","2026-10-10T10:20:00Z","2026-10-10T10:20Z"
            }
        }
        ;
        for(Object[] row:values)T.test("Convert "+row[0],()->eq(row[2],Parameters.convert(row[1],row[0].toString())));
        for(String type:List.of("INTEGER","DECIMAL","BOOLEAN","DATE","DATETIME","JSON"))T.test("Reject invalid "+type,()->error("PARAMETER_TYPE",()->Parameters.convert("not-valid",type)));
        T.test("Resolve isolated parameter sources",()-> {
            var defs=List.<Object>of(def("a","INTEGER","INPUT",null),def("b","STRING","CONSTANT","fixed"),def("c","STRING","SYSTEM","tenant"),def("d","STRING","CONFIG","region"),def("e","STRING","SECRET","api"));
            var result=Parameters.resolve(defs,Map.of("a",3),Map.of("tenant","north"),Map.of("region","DE"),Map.of("api","hidden"));
            eq("north",result.get("c"));
            eq("hidden",result.get("e"));
            eq(3L,result.get("a"));
        }
        );
        T.test("Caller cannot override constant",()->error("UNKNOWN_PARAMETER",()->Parameters.resolve(List.of(def("x","STRING","CONSTANT","fixed")),Map.of("x","evil"),Map.of(),Map.of(),Map.of())));
        T.test("Reject unknown input",()->error("UNKNOWN_PARAMETER",()->Parameters.resolve(List.of(),Map.of("x",1),Map.of(),Map.of(),Map.of())));
        T.test("Missing required input",()-> {
            var d=def("x","STRING","INPUT",null);
            d.put("required",true);
            error("REQUIRED_PARAMETER",()->Parameters.resolve(List.of(d),Map.of(),Map.of(),Map.of(),Map.of()));
        }
        );
        T.test("Duplicate definitions",()->error("INVALID_INPUT",()->Parameters.validateDefinitions(List.of(def("x","STRING","INPUT",null),def("x","INTEGER","INPUT",null)))));
        T.test("No arbitrary system property access",()->error("INVALID_INPUT",()->Parameters.validateDefinitions(List.of(def("x","STRING","SYSTEM","user.home")))));
        T.test("Typed template retains JSON object",()->eq(Map.of("x",true),Parameters.template("${payload}",Map.of("payload",Map.of("x",true)))));
        T.test("Nested structural template",()->eq("{\"x\":[7]}",Json.stringify(Parameters.template(Map.of("x",List.of("${v}")),Map.of("v",7)))));
        T.test("Missing template binding",()->error("MISSING_BINDING",()->Parameters.template("${missing}",Map.of())));
        T.test("Replacement cannot become regex code",()->eq("value $1\\",Parameters.interpolate("value ${x}",Map.of("x","$1\\"),false)));
        T.test("XML template escapes injection",()->eq("&lt;&amp;&quot;&apos;&gt;",Parameters.interpolate("${x}",Map.of("x","<&\"'>"),true)));
        T.test("Pointer escaped segments",()->eq(7,Parameters.pointer(Map.of("a/b",Map.of("~key",7)),"/a~1b/~0key")));
        T.test("Pointer invalid escape",()->error("INVALID_INPUT",()->Parameters.pointer(Map.of(),"/a~2")));
        T.test("Pointer missing segment",()->eq(null,Parameters.pointer(Map.of("a",1),"/b")));
        T.test("Pointer array bound",()->eq(null,Parameters.pointer(List.of(1),"/1")));
        T.test("Pointer rejects leading zero index",()->eq(null,Parameters.pointer(List.of(1),"/00")));
        T.test("Root selected before row mappings",()-> {
            Object result=Parameters.mapOutput(Map.of("data",List.of(Map.of("v","23"))),"/data",List.of(Map.of("name","value","pointer","/v","type","INTEGER")));
            eq("[{\"value\":23}]",Json.stringify(result));
        }
        );
        T.test("Required output cannot disappear",()->error("OUTPUT_MAPPING",()->Parameters.mapOutput(Map.of(),"",List.of(Map.of("name","x","pointer","/missing","required",true)))));
    }
    static void sql() {
        T.test("Named SQL preserves binding order and duplicate names",()-> {
            var p=NamedSql.parse("SELECT :a,:b,:a");
            eq("SELECT ?,?,?",p.sql());
            eq(List.of("a","b","a"),p.names());
        }
        );
        for(String sql:List.of("SELECT ':x',:id", "SELECT \"a:x\",:id", "SELECT [a:x],:id", "SELECT `a:x`,:id", "SELECT ':x''a',:id", "SELECT :id -- :x", "SELECT /* :x /* :y */ */ :id", "SELECT $$:x$$,:id", "SELECT $tag$:x$tag$,:id", "SELECT :id::integer"))T.test("SQL lexer "+sql,()->eq(List.of("id"),NamedSql.parse(sql).names()));
        T.test("Reject stacked SQL",()->error("MULTI_STATEMENT",()->NamedSql.parse("SELECT :x; DELETE FROM t")));
        T.test("Reject positional SQL",()->error("POSITIONAL_SQL",()->NamedSql.parse("SELECT ?")));
        T.test("Reject unterminated quote",()->error("INVALID_INPUT",()->NamedSql.parse("SELECT 'x")));
        T.test("Reject unterminated comment",()->error("INVALID_INPUT",()->NamedSql.parse("SELECT /*")));
        T.test("Reject identifier injection",()->error("SQL_IDENTIFIER",()->NamedSql.identifier("x;DROP TABLE t")));
        T.test("Qualified identifiers individually quoted",()->eq("\"app\".\"users\"",NamedSql.qualified("app.users","\"")));
    }
    static void crypto() {
        T.test("Authenticated encryption round trip",()-> {
            byte[] k=Crypto.randomBytes(32),p="private-data".getBytes(StandardCharsets.UTF_8);
            yes(Arrays.equals(p,Crypto.decrypt(k,Crypto.encrypt(k,p,"a"),"a")));
        }
        );
        T.test("Encryption uses fresh nonce",()-> {
            byte[] k=Crypto.randomBytes(32);
            yes(!Arrays.equals(Crypto.encrypt(k,new byte[5],"a"),Crypto.encrypt(k,new byte[5],"a")));
        }
        );
        T.test("Ciphertext tamper rejected",()-> {
            byte[] k=Crypto.randomBytes(32),c=Crypto.encrypt(k,new byte[5],"a");
            c[c.length-1]^=1;
            error("STORE_INTEGRITY",()->Crypto.decrypt(k,c,"a"));
        }
        );
        T.test("AAD binds catalog context",()-> {
            byte[] k=Crypto.randomBytes(32),c=Crypto.encrypt(k,new byte[5],"a");
            error("STORE_INTEGRITY",()->Crypto.decrypt(k,c,"b"));
        }
        );
        T.test("Wrong key rejected",()-> {
            byte[] c=Crypto.encrypt(Crypto.randomBytes(32),new byte[5],"a");
            error("STORE_INTEGRITY",()->Crypto.decrypt(Crypto.randomBytes(32),c,"a"));
        }
        );
        T.test("Token entropy format",()-> {
            String a=Crypto.token(),b=Crypto.token();
            eq(46,a.length());
            yes(!a.equals(b));
            yes(a.matches("ch_[A-Za-z0-9_-]{43}"));
        }
        );
        T.test("SHA256 known vector",()->eq("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",Crypto.sha256("abc")));
        T.test("Constant time comparison semantics",()-> {
            yes(Crypto.equal("abc","abc"));
            yes(!Crypto.equal("abc","abd"));
            yes(!Crypto.equal(null,"x"));
        }
        );
    }
    static void storage() {
        T.test("Catalog survives restart",()-> {
            Path dir=Files.createTempDirectory("hub-catalog-");
            byte[] key=Crypto.randomBytes(32);
            try(Catalog c=new Catalog(dir,key)) {
                c.update(s-> {
                    Json.map(s,"settings").put("x","value");
                    return null;
                }
                );
            }
            try(Catalog c=new Catalog(dir,key)) {
                eq("value",c.read(s->Json.map(s,"settings").get("x")));
            }
        }
        );
        T.test("Catalog rejects second process lock",()-> {
            Path dir=Files.createTempDirectory("hub-lock-");
            byte[] key=Crypto.randomBytes(32);
            try(Catalog c=new Catalog(dir,key)) {
                try(Catalog second=new Catalog(dir,key)) {
                    throw new AssertionError("Lock not enforced");
                }
                catch(java.io.IOException expected) {
                    yes(expected.getMessage().contains("in use"));
                }
            }
        }
        );
        T.test("Failed transaction rolls back",()-> {
            try(Catalog c=new Catalog(Files.createTempDirectory("hub-rollback-"),Crypto.randomBytes(32))) {
                error("INVALID_INPUT",()->c.update(s-> {
                    Json.map(s,"settings").put("x",true);
                    throw HubException.invalid();
                }
                ));
                yes(c.read(s->Json.map(s,"settings").isEmpty()));
            }
        }
        );
        T.test("Transaction result cannot mutate live state",()-> {
            try(Catalog c=new Catalog(Files.createTempDirectory("hub-alias-"),Crypto.randomBytes(32))) {
                Map<String,Object> result=c.update(s-> {
                    var m=Json.map(s,"settings");
                    m.put("x",1);
                    return m;
                }
                );
                result.put("x",9);
                eq(1,c.read(s->Json.integer(Json.map(s,"settings"),"x",0)));
            }
        }
        );
        T.test("Concurrent transactions do not lose updates",()-> {
            try(Catalog c=new Catalog(Files.createTempDirectory("hub-concurrent-"),Crypto.randomBytes(32)); var executor=Executors.newVirtualThreadPerTaskExecutor()) {
                var tasks=new ArrayList<Future<?>>();
                for(int n=0; n<40; n++)tasks.add(executor.submit(()->c.update(s-> {
                    var m=Json.map(s,"settings");
                    m.put("n",Json.integer(m,"n",0)+1);
                    return null;
                }
                )));
                for(var f:tasks)f.get();
                eq(40,c.read(s->Json.integer(Json.map(s,"settings"),"n",0)));
            }
        }
        );
        T.test("Secrets absent from plaintext snapshot",()-> {
            Path dir=Files.createTempDirectory("hub-secret-");
            try(Catalog c=new Catalog(dir,Crypto.randomBytes(32))) {
                c.update(s-> {
                    Json.map(s,"settings").put("secret","unique-plain-secret");
                    return null;
                }
                );
                yes(!new String(Files.readAllBytes(dir.resolve("catalog.enc")),StandardCharsets.ISO_8859_1).contains("unique-plain-secret"));
            }
        }
        );
    }
    static void roles() {
        for(String role:List.of("ADMIN","DESIGNER","INVOKER","AUDITOR"))T.test("Role invoke semantics "+role,()->eq(!role.equals("AUDITOR"),new Identity("k","t",role,Set.of()).canInvoke("p")));
        T.test("Scoped key denies other providers",()-> {
            var i=new Identity("k","t","INVOKER",Set.of("p"));
            yes(i.canInvoke("p"));
            yes(!i.canInvoke("q"));
        }
        );
        T.test("Designer cannot create credentials",()->error("FORBIDDEN",()->new Identity("k","t","DESIGNER",Set.of()).requireAdmin()));
        T.test("Auditor cannot design",()->error("FORBIDDEN",()->new Identity("k","t","AUDITOR",Set.of()).requireDesigner()));
    }
    static void resilience() {
        T.test("Safe read retries transient failures",()-> {
            var count=new AtomicInteger();
            var r=new Resilience();
            eq("ok",r.execute("read",true,3,()-> {
                if(count.incrementAndGet()<3)throw new HubException("UPSTREAM_UNAVAILABLE",503,true);
                return "ok";
            }
            ));
            eq(3,count.get());
        }
        );
        T.test("Unsafe write never auto retries",()-> {
            var n=new AtomicInteger();
            error("UPSTREAM_UNAVAILABLE",()->new Resilience().execute("write",false,4,()-> {
                n.incrementAndGet();
                throw new HubException("UPSTREAM_UNAVAILABLE",503,true);
            }
            ));
            eq(1,n.get());
        }
        );
        T.test("Business failure is not retryable",()-> {
            var n=new AtomicInteger();
            error("BAD",()->new Resilience().execute("read",true,4,()-> {
                n.incrementAndGet();
                throw new HubException("BAD",400);
            }
            ));
            eq(1,n.get());
        }
        );
        T.test("Rate limit enforces threshold",()-> {
            var r=new Resilience();
            r.rateLimit("k",2);
            r.rateLimit("k",2);
            error("RATE_LIMIT",()->r.rateLimit("k",2));
            r.rateLimit("other",2);
        }
        );
        T.test("Circuit opens after failure threshold",()-> {
            var r=new Resilience(Clock.systemUTC(),30000,2);
            for(int i=0; i<2; i++)error("UNAVAILABLE",()->r.execute("c",true,1,()-> {
                throw new HubException("UNAVAILABLE",503,true);
            }
            ));
            error("CIRCUIT_OPEN",()->r.execute("c",true,1,()->"not called"));
        }
        );
        T.test("Success clears failure count",()-> {
            var r=new Resilience(Clock.systemUTC(),30000,2);
            error("UNAVAILABLE",()->r.execute("c",true,1,()-> {
                throw new HubException("UNAVAILABLE",503,true);
            }
            ));
            eq("ok",r.execute("c",true,1,()->"ok"));
            error("UNAVAILABLE",()->r.execute("c",true,1,()-> {
                throw new HubException("UNAVAILABLE",503,true);
            }
            ));
            eq("still closed",r.execute("c",true,1,()->"still closed"));
        }
        );
    }
}
