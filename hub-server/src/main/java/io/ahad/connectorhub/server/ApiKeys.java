package io.ahad.connectorhub.server;
import io.ahad.connectorhub.core.*;
import java.time.*;
import java.util.*;
public final class ApiKeys {
    private final Catalog catalog;
    public ApiKeys(Catalog catalog) {
        this.catalog=catalog;
    }
    public String bootstrap(String requested) {
        return catalog.update(s-> {
            var keys=Json.map(s,"keys");
            if(!keys.isEmpty())return null;
            String token=requested.isBlank()?Crypto.token():requested;
            if(token.length()<32||token.length()>200)throw new IllegalArgumentException("Bootstrap token must have at least 32 characters");
            var key=record("bootstrap","Workspace administrator","default","ADMIN",List.of(),token,null);
            keys.put("bootstrap",key);
            return token;
        }
        );
    }
    public Identity authenticate(String token) {
        if(token==null||token.length()<32||token.length()>200)throw new HubException("UNAUTHORIZED",401);
        String digest=Crypto.sha256(token);
        return catalog.read(s-> {
            for(Object v:Json.map(s,"keys").values()) {
                var k=Json.object(v);
                if(Crypto.equal(Json.text(k,"hash",""),digest))return identity(k);
            }
            throw new HubException("UNAUTHORIZED",401);
        }
        );
    }
    public Identity byId(String id) {
        return catalog.read(s-> {
            Object key=Json.map(s,"keys").get(id);
            if(key==null)throw new HubException("UNAUTHORIZED",401);
            return identity(Json.object(key));
        }
        );
    }
    private static Identity identity(Map<String,Object> k) {
        if(Json.bool(k,"revoked",false))throw new HubException("UNAUTHORIZED",401);
        String expires=Json.text(k,"expiresAt","");
        if(!expires.isBlank()&&!Instant.parse(expires).isAfter(Instant.now()))throw new HubException("UNAUTHORIZED",401);
        Set<String> providers=new HashSet<>();
        for(Object p:Json.list(k,"providers"))providers.add(p.toString());
        return new Identity(Json.text(k,"id",""),Json.text(k,"tenant",""),Json.text(k,"role",""),Set.copyOf(providers));
    }
    public Map<String,Object> create(Identity actor,Map<String,Object> input) {
        actor.requireAdmin();
        String tenant=Json.text(input,"tenant",actor.tenant());
        if(!tenant.matches("[a-z][a-z0-9_-]{0,47}"))throw HubException.invalid();
        if(!tenant.equals(actor.tenant())&&!actor.keyId().equals("bootstrap"))throw new HubException("FORBIDDEN",403);
        String role=Json.text(input,"role","INVOKER");
        if(!Set.of("ADMIN","DESIGNER","INVOKER","AUDITOR").contains(role))throw HubException.invalid();
        List<Object> providers=Json.list(input,"providers");
        if(providers.size()>100)throw HubException.invalid();
        if(role.equals("INVOKER")&&providers.isEmpty()&&!Json.bool(input,"allProviders",false))throw new HubException("SCOPE_REQUIRED",400);
        int days=Json.integer(input,"expiresInDays",30);
        if(days<1||days>365)throw HubException.invalid();
        String token=Crypto.token(),id=UUID.randomUUID().toString();
        String name=Json.text(input,"name","API consumer");
        if(name.isBlank()||name.length()>100)throw HubException.invalid();
        var key=record(id,name,tenant,role,providers,token,Instant.now().plus(Duration.ofDays(days)).toString());
        catalog.update(s-> {
            var keys=Json.map(s,"keys");
            if(keys.size()>=500)throw new HubException("CAPACITY",503);
            keys.put(id,key);
            return null;
        }
        );
        var response=publicKey(key);
        response.put("token",token);
        return response;
    }
    public List<Object> list(Identity actor) {
        actor.requireAdmin();
        return catalog.read(s->Json.map(s,"keys").values().stream().map(Json::object).filter(k->actor.keyId().equals("bootstrap")||actor.tenant().equals(k.get("tenant"))).map(k->(Object)publicKey(k)).toList());
    }
    public void revoke(Identity actor,String id) {
        actor.requireAdmin();
        if(actor.keyId().equals(id)||id.equals("bootstrap"))throw new HubException("KEY_SELF_REVOKE",409);
        catalog.update(s-> {
            Object o=Json.map(s,"keys").get(id);
            if(o==null)throw new HubException("NOT_FOUND",404);
            var k=Json.object(o);
            if(!actor.keyId().equals("bootstrap")&&!actor.tenant().equals(k.get("tenant")))throw new HubException("NOT_FOUND",404);
            k.put("revoked",true);
            return null;
        }
        );
    }
    private static Map<String,Object> record(String id,String name,String tenant,String role,List<?> providers,String token,String expires) {
        var k=new LinkedHashMap<String,Object>();
        k.put("id",id);
        k.put("name",name);
        k.put("tenant",tenant);
        k.put("role",role);
        k.put("providers",providers);
        k.put("hash",Crypto.sha256(token));
        k.put("prefix",token.substring(0,Math.min(10,token.length())));
        k.put("createdAt",Instant.now().toString());
        k.put("expiresAt",expires);
        k.put("revoked",false);
        return k;
    }
    private static Map<String,Object> publicKey(Map<String,Object> key) {
        var output=new LinkedHashMap<>(key);
        output.remove("hash");
        return output;
    }
}
