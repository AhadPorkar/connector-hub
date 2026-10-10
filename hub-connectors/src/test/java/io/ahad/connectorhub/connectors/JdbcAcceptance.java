package io.ahad.connectorhub.connectors;
import io.ahad.connectorhub.core.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static io.ahad.connectorhub.core.TestKit.*;
/** Destructive acceptance suite. Use only a disposable database; never production credentials. */
public final class JdbcAcceptance {
    static String env(String key,String def) {
        return System.getenv().getOrDefault(key,def);
    }
    public static void main(String[] args)throws Exception {
        if(!env("HUB_ALLOW_DESTRUCTIVE_TESTS","false").equals("true"))throw new IllegalStateException("Disposable DB opt-in required: HUB_ALLOW_DESTRUCTIVE_TESTS=true");
        String url=env("HUB_TEST_JDBC_URL",""),driver=env("HUB_TEST_JDBC_DRIVER",""),user=env("HUB_TEST_JDBC_USER",""),password=env("HUB_TEST_JDBC_PASSWORD","");
        if(url.isBlank())throw new IllegalStateException("HUB_TEST_JDBC_URL required");
        if(!driver.isBlank())Class.forName(driver);
        var t=new TestKit("jdbc-acceptance");
        try(var executor=Executors.newVirtualThreadPerTaskExecutor(); Connection db=DriverManager.getConnection(url,user,password)) {
            String quote=db.getMetaData().getIdentifierQuoteString().trim();
            String table=quote+"CH_ACCEPTANCE"+quote;
            String id=quote+"ID"+quote,name=quote+"NAME"+quote,version=quote+"VERSION"+quote;
            try(Statement s=db.createStatement()) {
                try {
                    s.executeUpdate("DROP TABLE "+table);
                }
                catch(SQLException ignored) {
                }
                s.executeUpdate("CREATE TABLE "+table+" ("+id+" INTEGER PRIMARY KEY,"+name+" VARCHAR(100),"+version+" INTEGER NOT NULL)");
            }
            var connector=new JdbcConnector(new NetworkPolicy(Set.of(),false,List.of(url)),executor);
            var connection=Map.<String,Object>of("id","jdbc-test","config",Map.of("url",url,"driverClass",driver),"secrets",Map.of("username",user,"password",password));
            var identity=new Identity("test","default","ADMIN",Set.of());
            class Run {
                Object call(String op,Map<String,Object> cfg,Map<String,Object> params,Map<String,Object> page) {
                    return connector.execute(new Connector.Context(connection,Map.of("operation",op,"config",cfg,"timeoutMs",5000),params,page,identity,"jdbc-test"));
                }
                Map<String,Object> cfg() {
                    return new LinkedHashMap<>(Map.of("table","CH_ACCEPTANCE","keyColumn","ID","versionColumn","VERSION","bindings",Map.of("NAME","name")));
                }
            }
            var r=new Run();
            t.test("JDBC metadata sees real disposable table",()-> {
                var d=Json.object(connector.discover(new Connector.Context(connection,Map.of("config",Map.of()),Map.of(),Map.of(),identity,"jdbc")));
                yes(Json.list(d,"tables").stream().map(Json::object).anyMatch(x->"CH_ACCEPTANCE".equals(x.get("name"))));
            }
            );
            t.test("JDBC bound CREATE",()-> {
                var cfg=r.cfg();
                cfg.put("bindings",Map.of("ID","id","NAME","name","VERSION","version"));
                eq(1,Json.integer(Json.map(Json.object(r.call("CREATE",cfg,Map.of("id",1,"name","Alice","version",1),Map.of())),"data"),"affected",0));
            }
            );
            t.test("JDBC bound READ",()->eq(1,Json.list(Json.object(r.call("READ",r.cfg(),Map.of(),Map.of())),"data").size()));
            t.test("JDBC hostile filter remains data",()->eq(0,Json.list(Json.object(r.call("READ",r.cfg(),Map.of(),Map.of("filters",List.of(Map.of("field","NAME","value","x' OR 1=1 --"))))),"data").size()));
            t.test("JDBC guarded UPDATE",()->eq(1,Json.integer(Json.map(Json.object(r.call("UPDATE",r.cfg(),Map.of("id",1,"name","Bob","expectedVersion",1),Map.of())),"data"),"affected",0)));
            t.test("JDBC stale UPDATE rejected",()->error("VERSION_CONFLICT",()->r.call("UPDATE",r.cfg(),Map.of("id",1,"name","Bad","expectedVersion",1),Map.of())));
            t.test("JDBC non-primary-key UPDATE rejected",()-> {
                var cfg=r.cfg();
                cfg.put("keyColumn","NAME");
                error("SINGLE_PRIMARY_KEY_REQUIRED",()->r.call("UPDATE",cfg,Map.of("id","Bob","name","Bad","expectedVersion",2),Map.of()));
            }
            );
            t.test("JDBC unknown column rejected",()-> {
                var cfg=r.cfg();
                cfg.put("columns",List.of("MISSING"));
                error("COLUMN_NOT_FOUND",()->r.call("READ",cfg,Map.of(),Map.of()));
            }
            );
            t.test("JDBC named QUERY preserves literal",()->eq(1,Json.list(Json.object(r.call("QUERY",Map.of("sql","SELECT "+name+" FROM "+table+" WHERE "+id+" = :id"),Map.of("id",1),Map.of())),"data").size()));
            t.test("JDBC multi-statement SQL rejected",()->error("MULTI_STATEMENT",()->r.call("QUERY",Map.of("sql","SELECT 1; DELETE FROM "+table),Map.of(),Map.of())));
            t.test("JDBC guarded DELETE",()->eq(1,Json.integer(Json.map(Json.object(r.call("DELETE",r.cfg(),Map.of("id",1,"expectedVersion",2),Map.of())),"data"),"affected",0)));
            t.test("JDBC deleted record absent",()->eq(0,Json.list(Json.object(r.call("READ",r.cfg(),Map.of(),Map.of())),"data").size()));
            try(Statement s=db.createStatement()) {
                s.executeUpdate("DROP TABLE "+table);
            }
            System.out.println("Actual vendor: "+db.getMetaData().getDatabaseProductName()+" "+db.getMetaData().getDatabaseProductVersion());
        }
        t.finish(args.length==0?"build/reports/jdbc.xml":args[0]);
    }
}
