package io.ahad.connectorhub.server;
import io.ahad.connectorhub.core.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static io.ahad.connectorhub.core.TestKit.*;
public final class ServerTest {
    static final TestKit T=new TestKit("hub-server");
    static final String TOKEN="synthetic-unit-test-token-not-a-real-secret";
    static final class Fixture implements AutoCloseable {
        final Catalog catalog;
        final HubService hub;
        final Identity admin;
        Fixture()throws Exception {
            Path dir=Files.createTempDirectory("hub-server-unit-");
            catalog=new Catalog(dir,Crypto.randomBytes(32));
            hub=new HubService(new Config("127.0.0.1",0,dir,false,0,Set.of(),false,List.of(),"",TOKEN,8),catalog);
            hub.keys.bootstrap(TOKEN);
            admin=hub.keys.authenticate(TOKEN);
        }
        Map<String,Object> connection() {
            return hub.saveConnection(admin,null,Map.of("name","Local","type","LOCAL","config",Map.of()));
        }
        Map<String,Object> provider(String connection) {
            return hub.saveProvider(admin,null,Map.of("name","Records","slug","records","connectionId",connection,"operation","READ","parameters",List.of(),"config",Map.of("collection","records")));
        }
        public void close()throws Exception {
            hub.close();
            catalog.close();
        }
    }
    public static void main(String[] args)throws Exception {
        T.test("Bootstrap only creates one key",()-> {
            try(var f=new Fixture()) {
                eq(null,f.hub.keys.bootstrap("another-token-that-must-not-replace-the-admin"));
                eq(1,f.hub.keys.list(f.admin).size());
            }
        }
        );
        T.test("Expired key cannot authenticate",()-> {
            try(var f=new Fixture()) {
                var key=f.hub.keys.create(f.admin,Map.of("role","AUDITOR"));
                String id=key.get("id").toString();
                f.catalog.update(s-> {
                    Json.object(Json.map(s,"keys").get(id)).put("expiresAt","2000-01-01T00:00:00Z");
                    return null;
                }
                );
                error("UNAUTHORIZED",()->f.hub.keys.authenticate(key.get("token").toString()));
            }
        }
        );
        T.test("Revocation blocks queued key lookup",()-> {
            try(var f=new Fixture()) {
                var key=f.hub.keys.create(f.admin,Map.of("role","AUDITOR"));
                String id=key.get("id").toString();
                f.hub.keys.revoke(f.admin,id);
                error("UNAUTHORIZED",()->f.hub.keys.byId(id));
            }
        }
        );
        T.test("Key lifetime is bounded",()-> {
            try(var f=new Fixture()) {
                error("INVALID_INPUT",()->f.hub.keys.create(f.admin,Map.of("role","ADMIN","expiresInDays",366)));
            }
        }
        );
        T.test("Unsafe empty consumer scope requires explicit opt in",()-> {
            try(var f=new Fixture()) {
                error("SCOPE_REQUIRED",()->f.hub.keys.create(f.admin,Map.of("role","INVOKER")));
            }
        }
        );
        T.test("Unknown key role rejected",()-> {
            try(var f=new Fixture()) {
                error("INVALID_INPUT",()->f.hub.keys.create(f.admin,Map.of("role","SUPERUSER")));
            }
        }
        );
        T.test("Credentials absent from connection return",()-> {
            try(var f=new Fixture()) {
                var c=f.hub.saveConnection(f.admin,null,Map.of("name","Local","type","LOCAL","config",Map.of(),"secrets",Map.of("password","hidden")));
                yes(!Json.stringify(c).contains("hidden"));
            }
        }
        );
        T.test("Blank credential patch preserves old secret",()-> {
            try(var f=new Fixture()) {
                var c=f.hub.saveConnection(f.admin,null,Map.of("name","Local","type","LOCAL","config",Map.of(),"secrets",Map.of("password","hidden")));
                var update=new LinkedHashMap<>(c);
                update.put("secrets",Map.of("password",""));
                f.hub.saveConnection(f.admin,c.get("id").toString(),update);
                eq("hidden",Json.map(f.hub.entity("connections",c.get("id").toString(),f.admin),"secrets").get("password"));
            }
        }
        );
        T.test("Explicit clear removes a credential",()-> {
            try(var f=new Fixture()) {
                var c=f.hub.saveConnection(f.admin,null,Map.of("name","Local","type","LOCAL","config",Map.of(),"secrets",Map.of("password","hidden")));
                var update=new LinkedHashMap<>(c);
                update.put("clearSecrets",List.of("password"));
                f.hub.saveConnection(f.admin,c.get("id").toString(),update);
                yes(Json.map(f.hub.entity("connections",c.get("id").toString(),f.admin),"secrets").isEmpty());
            }
        }
        );
        T.test("Cross tenant provider reference rejected",()-> {
            try(var f=new Fixture()) {
                var c=f.connection();
                var other=new Identity("other","other","DESIGNER",Set.of());
                error("NOT_FOUND",()->f.hub.saveProvider(other,null,Map.of("name","No","slug","no","connectionId",c.get("id"),"operation","READ","config",Map.of())));
            }
        }
        );
        T.test("Publication revision is monotonically increasing",()-> {
            try(var f=new Fixture()) {
                var p=f.provider(f.connection().get("id").toString());
                eq(1,Json.integer(f.hub.publish(f.admin,p.get("id").toString(),1),"revision",0));
                eq(2,Json.integer(f.hub.publish(f.admin,p.get("id").toString(),1),"revision",0));
            }
        }
        );
        T.test("Publication rejects stale draft version",()-> {
            try(var f=new Fixture()) {
                var p=f.provider(f.connection().get("id").toString());
                error("VERSION_CONFLICT",()->f.hub.publish(f.admin,p.get("id").toString(),0));
            }
        }
        );
        T.test("OpenAPI active revision disappears after unpublish",()-> {
            try(var f=new Fixture()) {
                var p=f.provider(f.connection().get("id").toString());
                var pub=f.hub.publish(f.admin,p.get("id").toString(),1);
                f.hub.unpublish(f.admin,pub.get("id").toString());
                eq(0,Json.map(OpenApi.document(f.hub,f.admin),"paths").size());
            }
        }
        );
        T.test("Unknown resources remain tenant opaque",()-> {
            try(var f=new Fixture()) {
                error("NOT_FOUND",()->f.hub.entity("providers","unknown",f.admin));
            }
        }
        );
        T.test("Audit records contain no supplied input",()-> {
            try(var f=new Fixture()) {
                f.hub.audit(f.admin,"execute","provider","SUCCEEDED",null,12);
                var log=Json.object(f.hub.auditLog(f.admin).getFirst());
                eq(12,Json.integer(log,"durationMs",0));
                yes(!log.containsKey("input"));
            }
        }
        );
        T.test("Settings isolation",()-> {
            try(var f=new Fixture()) {
                f.hub.saveSettings(f.admin,Map.of("region","DE"));
                eq(Map.of(),f.hub.settings(new Identity("other","other","ADMIN",Set.of())));
            }
        }
        );
        T.test("Recovery quarantines STARTED but preserves DONE and FAILED",()-> {
            try(var f=new Fixture()) {
                f.catalog.update(s-> {
                    var all=Json.map(s,"idempotency");
                    for(String status:List.of("STARTED","DONE","FAILED","UNKNOWN"))all.put(status,new LinkedHashMap<>(Map.of("status",status)));
                    return null;
                }
                );
                try(var e=new ExecutionEngine(f.hub)) {
                    var all=f.catalog.read(s->Json.map(s,"idempotency"));
                    eq("UNKNOWN",Json.map(all,"STARTED").get("status"));
                    eq("DONE",Json.map(all,"DONE").get("status"));
                    eq("FAILED",Json.map(all,"FAILED").get("status"));
                    eq("UNKNOWN",Json.map(all,"UNKNOWN").get("status"));
                }
            }
        }
        );
        T.test("Recovery marks interrupted job unknown",()-> {
            try(var f=new Fixture()) {
                f.catalog.update(s-> {
                    Json.map(s,"jobs").put("job",new LinkedHashMap<>(Map.of("status","RUNNING")));
                    return null;
                }
                );
                try(var e=new ExecutionEngine(f.hub)) {
                    eq("UNKNOWN",f.catalog.read(s->Json.map(Json.map(s,"jobs"),"job").get("status")));
                }
            }
        }
        );
        T.test("Queued job can be cancelled by owning principal",()-> {
            try(var f=new Fixture()) {
                try(var e=new ExecutionEngine(f.hub)) {
                    f.catalog.update(s-> {
                        Json.map(s,"jobs").put("job",new LinkedHashMap<>(Map.of("status","QUEUED","tenant","default","principal","bootstrap")));
                        return null;
                    }
                    );
                    e.cancel(f.admin,"job");
                    eq("CANCELLED",f.catalog.read(s->Json.map(Json.map(s,"jobs"),"job").get("status")));
                }
            }
        }
        );
        T.test("Completed job cannot be cancelled",()-> {
            try(var f=new Fixture()) {
                try(var e=new ExecutionEngine(f.hub)) {
                    f.catalog.update(s-> {
                        Json.map(s,"jobs").put("job",new LinkedHashMap<>(Map.of("status","SUCCEEDED","tenant","default","principal","bootstrap")));
                        return null;
                    }
                    );
                    error("JOB_NOT_QUEUED",()->e.cancel(f.admin,"job"));
                }
            }
        }
        );
        T.test("Audit reader cannot see other tenant history",()-> {
            try(var f=new Fixture()) {
                f.connection();
                eq(List.of(),f.hub.auditLog(new Identity("other","other","AUDITOR",Set.of())));
            }
        }
        );
        T.test("Discovery does not claim live health for HTTP descriptors",()-> {
            try(var f=new Fixture()) {
                var result=f.hub.connector("REST").discover(new Connector.Context(Map.of(),Map.of("config",Map.of()),Map.of(),Map.of(),f.admin,"x"));
                eq(false,Json.object(result).get("remoteSchemaFetch"));
            }
        }
        );
        T.test("Unsupported protocol is an explicit error",()-> {
            try(var f=new Fixture()) {
                error("CONNECTOR_UNSUPPORTED",()->f.hub.connector("GRPC"));
            }
        }
        );
        T.finish(args.length==0?"build/reports/server.xml":args[0]);
    }
}
