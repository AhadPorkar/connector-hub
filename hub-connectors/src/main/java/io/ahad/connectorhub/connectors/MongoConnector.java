package io.ahad.connectorhub.connectors;
import io.ahad.connectorhub.core.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Optional official-driver bridge. Requires org.mongodb:mongodb-driver-sync plus its dependencies.
* Uses public driver interfaces only. Single-host deployments, bounded equality filters and string IDs.
* This bridge is not advertised as live-verified until the MongoDB acceptance suite has passed. */
public final class MongoConnector implements Connector {
    private final NetworkPolicy policy;
    public MongoConnector(NetworkPolicy policy) {
        this.policy=policy;
    }
    @Override public String type() {
        return "MONGODB";
    }
    @Override public Set<String> operations() {
        return Set.of("READ","CREATE","UPDATE","DELETE");
    }
    @Override public boolean available() {
        try {
            Class.forName("com.mongodb.client.MongoClients");
            return true;
        }
        catch(ClassNotFoundException e) {
            return false;
        }
    }
    @Override public boolean retrySafe(Map<String,Object> p) {
        return Json.text(p,"operation","").equals("READ");
    }
    @Override public Object execute(Context c) {
        if(!available())throw new HubException("DRIVER_MISSING",412);
        URI uri=policy.approve(Json.text(c.connectionConfig(),"url",""));
        if(!uri.getScheme().equals("mongodb")||uri.getRawQuery()!=null||uri.getPath()!=null&&!uri.getPath().isEmpty()&&!uri.getPath().equals("/"))throw HubException.invalid();
        String db=identifier(Json.text(c.connectionConfig(),"database","")),collection=identifier(Json.text(c.config(),"collection",""));
        String password=Json.text(c.secrets(),"password",""),user=Json.text(c.secrets(),"username","");
        String auth=user.isEmpty()?"":encode(user)+":"+encode(password)+"@";
        String url="mongodb://"+auth+uri.getRawAuthority()+"/?connectTimeoutMS="+Math.min(5000,c.timeoutMillis())+"&socketTimeoutMS="+c.timeoutMillis()+"&serverSelectionTimeoutMS="+c.timeoutMillis()+"&retryWrites=false&retryReads=false";
        String authDb=Json.text(c.connectionConfig(),"authSource","admin");
        url+="&authSource="+encode(identifier(authDb));
        if(Json.bool(c.connectionConfig(),"tls",true))url+="&tls=true";
        Object client=null;
        try {
            client=Class.forName("com.mongodb.client.MongoClients").getMethod("create",String.class).invoke(null,url);
            Object database=invoke("com.mongodb.client.MongoClient",client,"getDatabase",new Class<?>[] {
                String.class
            }
            ,db);
            Object coll=invoke("com.mongodb.client.MongoDatabase",database,"getCollection",new Class<?>[] {
                String.class
            }
            ,collection);
            Class<?> bson=Class.forName("org.bson.conversions.Bson");
            var filter=new LinkedHashMap<String,Object>();
            for(var e:Json.map(c.config(),"filter").entrySet()) {
                safeField(e.getKey());
                Object value=Parameters.template(e.getValue(),c.parameters());
                safeDocument(value);
                filter.put(e.getKey(),Collections.singletonMap("$eq",value));
            }
            String id=Json.text(c.parameters(),"id","");
            if(!id.isEmpty())filter.put("_id",id);
            Object result;
            switch(c.operation()) {
                case "READ"-> {
                    Object iterable=invoke("com.mongodb.client.MongoCollection",coll,"find",new Class<?>[] {
                        bson
                    }
                    ,document(filter));
                    iterable=invoke("com.mongodb.client.FindIterable",iterable,"skip",new Class<?>[] {
                        int.class
                    }
                    ,c.pageIndex()*c.pageSize());
                    iterable=invoke("com.mongodb.client.FindIterable",iterable,"limit",new Class<?>[] {
                        int.class
                    }
                    ,c.pageSize());
                    Object cursor=invoke("com.mongodb.client.MongoIterable",iterable,"iterator",new Class<?>[] {
                    }
                    );
                    var rows=new ArrayList<Object>();
                    try {
                        while((boolean)invoke("com.mongodb.client.MongoCursor",cursor,"hasNext",new Class<?>[] {
                        }
                        )) {
                            Object row=invoke("com.mongodb.client.MongoCursor",cursor,"next",new Class<?>[] {
                            }
                            );
                            rows.add(Json.parse((String)invoke("org.bson.Document",row,"toJson",new Class<?>[] {
                            }
                            )));
                        }
                    }
                    finally {
                        invoke("com.mongodb.client.MongoCursor",cursor,"close",new Class<?>[] {
                        }
                        );
                    }
                    result=rows;
                }
                case "CREATE"-> {
                    var value=Json.object(Parameters.template(Json.map(c.config(),"body"),c.parameters()));
                    safeDocument(value);
                    value.put("_id",id.isEmpty()?UUID.randomUUID().toString():id);
                    invoke("com.mongodb.client.MongoCollection",coll,"insertOne",new Class<?>[] {
                        Object.class
                    }
                    ,document(value));
                    result=value;
                }
                case "UPDATE"-> {
                    if(id.isEmpty())throw new HubException("REQUIRED_PARAMETER",400);
                    var value=Json.object(Parameters.template(Json.map(c.config(),"body"),c.parameters()));
                    safeDocument(value);
                    value.remove("_id");
                    Object changed=invoke("com.mongodb.client.MongoCollection",coll,"updateOne",new Class<?>[] {
                        bson,bson
                    }
                    ,document(Map.of("_id",id)),document(Map.of("$set",document(value))));
                    result=Map.of("affected",invoke("com.mongodb.client.result.UpdateResult",changed,"getModifiedCount",new Class<?>[] {
                    }
                    ));
                }
                case "DELETE"-> {
                    if(id.isEmpty())throw new HubException("REQUIRED_PARAMETER",400);
                    Object changed=invoke("com.mongodb.client.MongoCollection",coll,"deleteOne",new Class<?>[] {
                        bson
                    }
                    ,document(Map.of("_id",id)));
                    result=Map.of("affected",invoke("com.mongodb.client.result.DeleteResult",changed,"getDeletedCount",new Class<?>[] {
                    }
                    ));
                }
                default->throw HubException.invalid();
            }
            return Map.of("data",result);
        }
        catch(HubException e) {
            throw e;
        }
        catch(ReflectiveOperationException e) {
            throw new HubException("MONGODB_ERROR",502);
        }
        finally {
            if(client!=null)try {
                invoke("com.mongodb.client.MongoClient",client,"close",new Class<?>[] {
                }
                );
            }
            catch(Exception ignored) {
            }
        }
    }
    private static Object document(Map<String,Object> m)throws ReflectiveOperationException {
        return Class.forName("org.bson.Document").getConstructor(Map.class).newInstance(m);
    }
    private static Object invoke(String owner,Object target,String name,Class<?>[] types,Object...args)throws ReflectiveOperationException {
        return Class.forName(owner).getMethod(name,types).invoke(target,args);
    }
    private static String identifier(String s) {
        if(!s.matches("[A-Za-z_][A-Za-z0-9_-]{0,63}"))throw HubException.invalid();
        return s;
    }
    private static void safeDocument(Object value) {
        if(value instanceof Map<?,?> m) {
            for(var e:m.entrySet()) {
                safeField(e.getKey().toString());
                safeDocument(e.getValue());
            }
        }
        else if(value instanceof List<?> a) {
            for(Object item:a)safeDocument(item);
        }
    }
    private static void safeField(String s) {
        if(s.startsWith("$")||s.contains(".")||s.contains("\0"))throw HubException.invalid();
    }
    private static String encode(String s) {
        return URLEncoder.encode(s,StandardCharsets.UTF_8).replace("+","%20");
    }
}
