package io.ahad.connectorhub.server;
import io.ahad.connectorhub.core.*;
import io.ahad.connectorhub.connectors.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
public final class HubService implements AutoCloseable {
    public static final String VERSION="0.1.0";
    public final Catalog catalog;
    public final ApiKeys keys;
    public final Map<String,Connector> connectors=new LinkedHashMap<>();
    public final NetworkPolicy network;
    public final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    public final Config options;
    public HubService(Config options,Catalog catalog) {
        this.options=options;
        this.catalog=catalog;
        keys=new ApiKeys(catalog);
        network=new NetworkPolicy(options.origins(),options.allowPrivate(),options.jdbcPrefixes());
        register(new LocalConnector(catalog));
        register(new JdbcConnector(network,executor));
        for(String type:List.of("REST","SOAP","GRAPHQL","ODATA"))register(new HttpConnector(type,network));
        register(new RedisConnector(network));
        register(new MongoConnector(network));
        for(Connector plugin:ServiceLoader.load(Connector.class))register(plugin);
    }
    private void register(Connector c) {
        if(connectors.putIfAbsent(c.type(),c)!=null)throw new IllegalStateException("Duplicate connector type "+c.type());
    }
    public Connector connector(String type) {
        Connector c=connectors.get(type);
        if(c==null)throw new HubException("CONNECTOR_UNSUPPORTED",400);
        return c;
    }
    public Map<String,Object> system(Identity id) {
        return Map.of("name","Connector Hub","version",VERSION,"tenant",id.tenant(),"role",id.role(),"demo",options.demo(),"deployment","single-node","connectors",connectors.values().stream().map(c->Map.of("type",c.type(),"operations",c.operations(),"available",c.available())).toList(),"limits",Map.of("requestBytes",1_048_576,"responseBytes",2_097_152,"rows",500,"maxConcurrent",options.maxConcurrent()));
    }
    public Map<String,Object> entity(String collection,String id,Identity actor) {
        return catalog.read(s-> {
            Object v=Json.map(s,collection).get(id);
            if(v==null||!actor.tenant().equals(Json.object(v).get("tenant")))throw new HubException("NOT_FOUND",404);
            return Json.object(v);
        }
        );
    }
    public List<Object> list(String collection,Identity actor) {
        return catalog.read(s->Json.map(s,collection).values().stream().map(Json::object).filter(v->actor.tenant().equals(v.get("tenant"))).sorted(Comparator.comparing(v->Json.text(v,"name",Json.text(v,"createdAt","")))).map(v->(Object)(collection.equals("connections")?publicConnection(v):v)).toList());
    }
    public Map<String,Object> saveConnection(Identity actor,String id,Map<String,Object> request) {
        actor.requireAdmin();
        String type=Json.text(request,"type","");
        connector(type);
        var cfg=Json.map(request,"config");
        switch(type) {
            case "REST","SOAP","GRAPHQL","ODATA"-> {
                var uri=network.approve(Json.text(cfg,"baseUrl",""));
                if(!Set.of("http","https").contains(uri.getScheme()))throw HubException.invalid();
            }
            case "REDIS","MONGODB"->network.approve(Json.text(cfg,"url",""));
            case "JDBC"->network.approveJdbc(Json.text(cfg,"url",""));
            default-> {
            }
        }
        String name=validName(request);
        String uid=id==null?UUID.randomUUID().toString():id;
        Map<String,Object> result=catalog.update(s-> {
            var all=Json.map(s,"connections");
            var previous=existing(all,id,actor,request);
            if(previous!=null&&!type.equals(previous.get("type")))throw new HubException("CONNECTION_TYPE_IMMUTABLE",409);
            if(all.size()>=500&&previous==null)throw new HubException("CAPACITY",503);
            var value=new LinkedHashMap<String,Object>();
            value.put("id",uid);
            value.put("tenant",actor.tenant());
            value.put("name",name);
            value.put("type",type);
            value.put("config",cfg);
            value.put("version",previous==null?1:Json.integer(previous,"version",0)+1);
            value.put("updatedAt",Instant.now().toString());
            var secrets=previous==null?new LinkedHashMap<String,Object>():Json.map(previous,"secrets");
            for(var e:Json.map(request,"secrets").entrySet()) {
                if(!e.getKey().matches("[A-Za-z][A-Za-z0-9_]{0,63}")||!(e.getValue() instanceof String str)||str.length()>65_536)throw HubException.invalid();
                if(!str.isEmpty())secrets.put(e.getKey(),str);
            }
            for(Object key:Json.list(request,"clearSecrets"))secrets.remove(key.toString());
            value.put("secrets",secrets);
            all.put(uid,value);
            return publicConnection(value);
        }
        );
        audit(actor,"connection.save",uid,"SUCCEEDED",null,0);
        return result;
    }
    public void deleteConnection(Identity actor,String id,long version) {
        actor.requireAdmin();
        catalog.update(s-> {
            var all=Json.map(s,"connections");
            var existing=owned(all,id,actor);
            if(Json.number(existing,"version",0)!=version)throw new HubException("VERSION_CONFLICT",409);
            for(String collection:List.of("providers","publications"))for(Object o:Json.map(s,collection).values()) {
                var v=Json.object(o);
                var p=collection.equals("publications")?Json.map(v,"snapshot"):v;
                if(actor.tenant().equals(v.get("tenant"))&&id.equals(p.get("connectionId")))throw new HubException("RESOURCE_REFERENCED",409);
            }
            all.remove(id);
            return null;
        }
        );
        audit(actor,"connection.delete",id,"SUCCEEDED",null,0);
    }
    public Map<String,Object> saveProvider(Identity actor,String id,Map<String,Object> request) {
        actor.requireDesigner();
        String name=validName(request),connectionId=Json.text(request,"connectionId","");
        var connection=entity("connections",connectionId,actor);
        Connector connector=connector(Json.text(connection,"type",""));
        String operation=Json.text(request,"operation","READ");
        if(!connector.operations().contains(operation))throw new HubException("OPERATION_UNSUPPORTED",400);
        String slug=Json.text(request,"slug","");
        if(!slug.matches("[a-z][a-z0-9-]{1,63}"))throw new HubException("SLUG_INVALID",400);
        Parameters.validateDefinitions(Json.list(request,"parameters"));
        if(Json.integer(request,"timeoutMs",10000)<100||Json.integer(request,"timeoutMs",10000)>60_000)throw HubException.invalid();
        if(Json.integer(request,"maxAttempts",1)<1||Json.integer(request,"maxAttempts",1)>4)throw HubException.invalid();
        if(Json.integer(request,"rateLimit",120)<1||Json.integer(request,"rateLimit",120)>10_000)throw HubException.invalid();
        if(Json.list(request,"outputMappings").size()>100)throw HubException.invalid();
        String root=Json.text(request,"fieldsRoot","/data");
        if(!root.isEmpty()&&!root.startsWith("/"))throw HubException.invalid();
        String uid=id==null?UUID.randomUUID().toString():id;
        var result=catalog.update(s-> {
            var all=Json.map(s,"providers");
            var previous=existing(all,id,actor,request);
            if(all.size()>=1000&&previous==null)throw new HubException("CAPACITY",503);
            for(Object o:all.values()) {
                var other=Json.object(o);
                if(actor.tenant().equals(other.get("tenant"))&&slug.equals(other.get("slug"))&&!uid.equals(other.get("id")))throw new HubException("SLUG_CONFLICT",409);
            }
            var v=new LinkedHashMap<String,Object>();
            for(String key:List.of("description","parameters","outputMappings","config","fieldsRoot","timeoutMs","maxAttempts","rateLimit"))if(request.containsKey(key))v.put(key,request.get(key));
            v.put("id",uid);
            v.put("tenant",actor.tenant());
            v.put("name",name);
            v.put("slug",slug);
            v.put("connectionId",connectionId);
            v.put("operation",operation);
            v.put("version",previous==null?1:Json.integer(previous,"version",0)+1);
            v.put("updatedAt",Instant.now().toString());
            all.put(uid,v);
            return v;
        }
        );
        audit(actor,"provider.save",uid,"SUCCEEDED",null,0);
        return result;
    }
    public void deleteProvider(Identity actor,String id,long version) {
        actor.requireDesigner();
        catalog.update(s-> {
            var all=Json.map(s,"providers");
            var p=owned(all,id,actor);
            if(Json.number(p,"version",0)!=version)throw new HubException("VERSION_CONFLICT",409);
            for(Object o:Json.map(s,"publications").values()) {
                var publication=Json.object(o);
                if(id.equals(publication.get("providerId"))&&Json.bool(publication,"active",false))throw new HubException("RESOURCE_REFERENCED",409);
            }
            all.remove(id);
            return null;
        }
        );
        audit(actor,"provider.delete",id,"SUCCEEDED",null,0);
    }
    public Map<String,Object> publish(Identity actor,String id,long expected) {
        actor.requireDesigner();
        var result=catalog.update(s-> {
            var p=owned(Json.map(s,"providers"),id,actor);
            if(Json.number(p,"version",0)!=expected)throw new HubException("VERSION_CONFLICT",409);
            var pubs=Json.map(s,"publications");
            int revision=1;
            String slug=Json.text(p,"slug","");
            for(Object o:pubs.values()) {
                var other=Json.object(o);
                if(actor.tenant().equals(other.get("tenant"))&&slug.equals(other.get("slug")))revision=Math.max(revision,Json.integer(other,"revision",0)+1);
            }
            if(pubs.size()>=5000)throw new HubException("CAPACITY",503);
            String pid=UUID.randomUUID().toString();
            var publication=new LinkedHashMap<String,Object>();
            publication.put("id",pid);
            publication.put("tenant",actor.tenant());
            publication.put("providerId",id);
            publication.put("name",p.get("name"));
            publication.put("slug",slug);
            publication.put("revision",revision);
            publication.put("sourceVersion",p.get("version"));
            publication.put("active",true);
            publication.put("path","/api/services/"+slug+"/v"+revision);
            publication.put("createdAt",Instant.now().toString());
            publication.put("snapshot",Json.copy(p));
            pubs.put(pid,publication);
            return publication;
        }
        );
        audit(actor,"provider.publish",id,"SUCCEEDED",null,0);
        return result;
    }
    public void unpublish(Identity actor,String id) {
        actor.requireDesigner();
        catalog.update(s-> {
            var p=owned(Json.map(s,"publications"),id,actor);
            p.put("active",false);
            return null;
        }
        );
        audit(actor,"provider.unpublish",id,"SUCCEEDED",null,0);
    }
    public Map<String,Object> publication(Identity actor,String slug,int revision) {
        return catalog.read(s-> {
            for(Object o:Json.map(s,"publications").values()) {
                var p=Json.object(o);
                if(actor.tenant().equals(p.get("tenant"))&&slug.equals(p.get("slug"))&&revision==Json.integer(p,"revision",0)&&Json.bool(p,"active",false))return p;
            }
            throw new HubException("NOT_FOUND",404);
        }
        );
    }
    public Object discover(Identity actor,String id,Map<String,Object> input) {
        actor.requireDesigner();
        var conn=entity("connections",id,actor);
        var provider=Map.<String,Object>of("config",input,"operation","READ","timeoutMs",10000);
        return connector(Json.text(conn,"type","")).discover(new Connector.Context(conn,provider,Map.of(),Map.of(),actor,UUID.randomUUID().toString()));
    }
    public List<Object> generateCrud(Identity actor,String id,Map<String,Object> input) {
        actor.requireDesigner();
        var conn=entity("connections",id,actor);
        String type=Json.text(conn,"type","");
        String table=Json.text(input,"table","records");
        List<Object> created=new ArrayList<>();
        if(type.equals("LOCAL")) {
            for(String op:List.of("READ","CREATE","UPDATE","DELETE")) {
                var p=new LinkedHashMap<String,Object>();
                p.put("name",table+" / "+op);
                p.put("slug",slug(table)+"-"+op.toLowerCase(Locale.ROOT)+"-"+UUID.randomUUID().toString().substring(0,6));
                p.put("connectionId",id);
                p.put("operation",op);
                var params=new ArrayList<Object>();
                if(!op.equals("CREATE"))params.add(parameter("id","STRING",!op.equals("READ")));
                if(op.equals("CREATE")||op.equals("UPDATE"))params.add(parameter("name","STRING",true));
                if(op.equals("UPDATE")||op.equals("DELETE"))params.add(parameter("expectedVersion","INTEGER",true));
                p.put("parameters",params);
                p.put("config",Map.of("collection",table,"body",Map.of("name","${name}")));
                created.add(saveProvider(actor,null,p));
            }
            return created;
        }
        if(!type.equals("JDBC"))throw new HubException("GENERATOR_UNSUPPORTED",400);
        var discovery=Json.object(discover(actor,id,input));
        String schema=Json.text(input,"schema","");
        Map<String,Object> match=null;
        for(Object o:Json.list(discovery,"tables")) {
            var t=Json.object(o);
            if(table.equals(t.get("name"))&&(schema.isBlank()||schema.equals(t.get("schema")))) {
                if(match!=null)throw new HubException("SCHEMA_REQUIRED",400);
                match=t;
            }
        }
        if(match==null)throw new HubException("TABLE_NOT_FOUND",404);
        var pk=Json.list(match,"primaryKeys");
        if(pk.size()!=1)throw new HubException("SINGLE_PRIMARY_KEY_REQUIRED",400);
        String key=pk.getFirst().toString();
        String actualSchema=Json.text(match,"schema","");
        for(String op:List.of("READ","CREATE","UPDATE","DELETE")) {
            var p=new LinkedHashMap<String,Object>();
            p.put("name",table+" / "+op);
            p.put("slug",slug(table)+"-"+op.toLowerCase(Locale.ROOT)+"-"+UUID.randomUUID().toString().substring(0,6));
            p.put("connectionId",id);
            p.put("operation",op);
            var params=new ArrayList<Object>();
            var bindings=new LinkedHashMap<String,Object>();
            var columns=new ArrayList<Object>();
            for(Object co:Json.list(match,"columns")) {
                var column=Json.object(co);
                String name=Json.text(column,"name","");
                columns.add(name);
                if((op.equals("CREATE")&&!Json.bool(column,"autoIncrement",false))||(op.equals("UPDATE")&&!name.equals(key))) {
                    bindings.put(name,name);
                    params.add(parameter(name,inputType(Json.integer(column,"jdbcType",12)),op.equals("CREATE")&&!Json.bool(column,"nullable",true)));
                }
            }
            if(op.equals("UPDATE")||op.equals("DELETE")) {
                String keyType="STRING";
                for(Object co:Json.list(match,"columns")) {
                    var column=Json.object(co);
                    if(key.equals(column.get("name")))keyType=inputType(Json.integer(column,"jdbcType",12));
                }
                params.add(parameter("id",keyType,true));
            }
            p.put("parameters",params);
            p.put("config",Map.of("table",table,"schema",actualSchema,"keyColumn",key,"keyParameter","id","bindings",bindings,"columns",columns));
            created.add(saveProvider(actor,null,p));
        }
        return created;
    }
    public static Map<String,Object> parameter(String name,String type,boolean required) {
        return Map.of("name",name,"type",type,"source","INPUT","required",required);
    }
    private static String inputType(int sqlType) {
        return switch(sqlType) {
            case -6,5,4,-5->"INTEGER";
            case 2,3,6,7,8->"DECIMAL";
            case -7,16->"BOOLEAN";
            case 91->"DATE";
            default->"STRING";
        }
        ;
    }
    private static String slug(String text) {
        String s=text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");
        if(s.isBlank()||!Character.isLetter(s.charAt(0)))s="table-"+s;
        return s.substring(0,Math.min(35,s.length()));
    }
    public Object settings(Identity actor) {
        actor.requireAdmin();
        return catalog.read(s->Json.map(Json.map(s,"settings"),actor.tenant()));
    }
    public void saveSettings(Identity actor,Map<String,Object> values) {
        actor.requireAdmin();
        if(values.size()>100||Json.stringify(values).length()>100_000)throw HubException.invalid();
        catalog.update(s-> {
            Json.map(s,"settings").put(actor.tenant(),values);
            return null;
        }
        );
        audit(actor,"settings.save",actor.tenant(),"SUCCEEDED",null,0);
    }
    public void audit(Identity actor,String action,String resource,String status,String code,long duration) {
        catalog.update(s-> {
            var logs=Json.list(s,"audit");
            var entry=new LinkedHashMap<String,Object>();
            entry.put("id",UUID.randomUUID().toString());
            entry.put("tenant",actor.tenant());
            entry.put("principal",actor.keyId());
            entry.put("action",action);
            entry.put("resource",resource);
            entry.put("status",status);
            entry.put("code",code);
            entry.put("durationMs",duration);
            entry.put("createdAt",Instant.now().toString());
            logs.add(entry);
            while(logs.size()>5000)logs.removeFirst();
            return null;
        }
        );
    }
    public List<Object> auditLog(Identity actor) {
        if(!actor.auditor())throw new HubException("FORBIDDEN",403);
        return catalog.read(s-> {
            var result=new ArrayList<Object>();
            for(Object o:Json.list(s,"audit"))if(actor.tenant().equals(Json.object(o).get("tenant")))result.add(o);
            Collections.reverse(result);
            return result.stream().limit(500).toList();
        }
        );
    }
    public Object dashboard(Identity actor) {
        if(!actor.auditor())throw new HubException("FORBIDDEN",403);
        return catalog.read(s-> {
            long connections=count(s,"connections",actor),providers=count(s,"providers",actor);
            var pubs=Json.map(s,"publications").values().stream().map(Json::object).filter(v->actor.tenant().equals(v.get("tenant"))&&Json.bool(v,"active",false)).count();
            var events=Json.list(s,"audit").stream().map(Json::object).filter(v->actor.tenant().equals(v.get("tenant"))&&Json.text(v,"action","").equals("execute")).toList();
            long failures=events.stream().filter(e->!Json.text(e,"status","").equals("SUCCEEDED")).count();
            double avg=events.stream().mapToLong(e->Json.number(e,"durationMs",0)).average().orElse(0);
            return Map.of("connections",connections,"providers",providers,"published",pubs,"executions",events.size(),"failures",failures,"averageMs",Math.round(avg),"window","retained audit events, maximum 5000 across all tenants");
        }
        );
    }
    private static long count(Map<String,Object> s,String coll,Identity actor) {
        return Json.map(s,coll).values().stream().map(Json::object).filter(v->actor.tenant().equals(v.get("tenant"))).count();
    }
    private static String validName(Map<String,Object> m) {
        String s=Json.text(m,"name","");
        if(s.isBlank()||s.length()>120)throw HubException.invalid();
        return s;
    }
    private static Map<String,Object> existing(Map<String,Object> all,String id,Identity actor,Map<String,Object> request) {
        if(id==null)return null;
        var previous=owned(all,id,actor);
        if(Json.integer(request,"version",-1)!=Json.integer(previous,"version",0))throw new HubException("VERSION_CONFLICT",409);
        return previous;
    }
    public static Map<String,Object> owned(Map<String,Object> all,String id,Identity actor) {
        Object previous=all.get(id);
        if(previous==null||!actor.tenant().equals(Json.object(previous).get("tenant")))throw new HubException("NOT_FOUND",404);
        return Json.object(previous);
    }
    public static Map<String,Object> publicConnection(Map<String,Object> value) {
        var output=new LinkedHashMap<>(value);
        output.remove("secrets");
        output.put("configuredSecrets",Json.map(value,"secrets").keySet());
        return output;
    }
    @Override public void close() {
        for(Connector c:connectors.values())if(c instanceof AutoCloseable a)try {
            a.close();
        }
        catch(Exception ignored) {
        }
        executor.close();
    }
}
