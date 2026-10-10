package io.ahad.connectorhub.server;
import io.ahad.connectorhub.core.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
/** Small same-origin control plane. No cookies, unauthenticated execution, CORS wildcards, or remote code. */
public final class HubHttpServer implements AutoCloseable {
    private final HubService hub;
    private final ExecutionEngine engine;
    private final HttpServer server;
    private final Semaphore requests=new Semaphore(128);
    private final Resilience authLimiter=new Resilience();
    private static final Set<String> STATIC=Set.of("/","/index.html","/app.js","/app.css","/favicon.svg","/locales/en.json","/locales/de.json","/locales/fa.json");
    public HubHttpServer(HubService hub,ExecutionEngine engine)throws IOException {
        this.hub=hub;
        this.engine=engine;
        server=HttpServer.create(new InetSocketAddress(hub.options.bind(),hub.options.port()),64);
        server.setExecutor(hub.executor);
        server.createContext("/",this::handle);
    }
    public void start() {
        server.start();
    }
    public int port() {
        return server.getAddress().getPort();
    }
    private void handle(HttpExchange exchange) {
        boolean acquired=false;
        String requestId=UUID.randomUUID().toString();
        try {
            headers(exchange,requestId);
            if(!requests.tryAcquire())throw new HubException("CAPACITY",503);
            acquired=true;
            String path=exchange.getRequestURI().getPath(),method=exchange.getRequestMethod();
            if(exchange.getRequestURI().getRawPath().contains("%")||path.contains("..")||path.contains("\\"))throw new HubException("NOT_FOUND",404);
            if(path.equals("/healthz")&&method.equals("GET")) {
                send(exchange,200,Map.of("status","UP","version",HubService.VERSION));
                return;
            }
            if(!path.startsWith("/api/")) {
                if(!method.equals("GET")&&!method.equals("HEAD"))throw new HubException("METHOD_NOT_ALLOWED",405);
                staticFile(exchange,path,method.equals("HEAD"));
                return;
            }
            String auth=exchange.getRequestHeaders().getFirst("Authorization");
            Identity actor;
            try {
                if(auth==null||!auth.startsWith("Bearer "))throw new HubException("UNAUTHORIZED",401);
                actor=hub.keys.authenticate(auth.substring(7));
            }
            catch(HubException e) {
                authLimiter.rateLimit(exchange.getRemoteAddress().getAddress().getHostAddress(),120);
                throw e;
            }
            String key=exchange.getRequestHeaders().getFirst("Idempotency-Key");
            String[] parts=path.split("/");
            Object result=null;
            int status=200;
            if(path.equals("/api/me")&&method.equals("GET"))result=hub.system(actor);
            else if(path.equals("/api/dashboard")&&method.equals("GET"))result=hub.dashboard(actor);
            else if(path.equals("/api/connections")) {
                actor.requireDesigner();
                if(method.equals("GET"))result=hub.list("connections",actor);
                else if(method.equals("POST")) {
                    result=hub.saveConnection(actor,null,body(exchange));
                    status=201;
                }
                else throw new HubException("METHOD_NOT_ALLOWED",405);
            }
            else if(parts.length>=4&&parts[2].equals("connections")) {
                actor.requireDesigner();
                String id=parts[3];
                if(parts.length==4) {
                    switch(method) {
                        case "GET"->result=HubService.publicConnection(hub.entity("connections",id,actor));
                        case "PUT"->result=hub.saveConnection(actor,id,body(exchange));
                        case "DELETE"-> {
                            hub.deleteConnection(actor,id,expectedVersion(exchange));
                            status=204;
                        }
                        default->throw new HubException("METHOD_NOT_ALLOWED",405);
                    }
                }
                else if(parts.length==5&&method.equals("POST")) {
                    switch(parts[4]) {
                        case "discover","test"->result=hub.discover(actor,id,body(exchange));
                        case "generate-crud"-> {
                            result=hub.generateCrud(actor,id,body(exchange));
                            status=201;
                        }
                        default->throw new HubException("NOT_FOUND",404);
                    }
                }
                else throw new HubException("NOT_FOUND",404);
            }
            else if(path.equals("/api/providers")) {
                actor.requireDesigner();
                if(method.equals("GET"))result=hub.list("providers",actor);
                else if(method.equals("POST")) {
                    result=hub.saveProvider(actor,null,body(exchange));
                    status=201;
                }
                else throw new HubException("METHOD_NOT_ALLOWED",405);
            }
            else if(parts.length>=4&&parts[2].equals("providers")) {
                actor.requireDesigner();
                String id=parts[3];
                if(parts.length==4) {
                    switch(method) {
                        case "GET"->result=hub.entity("providers",id,actor);
                        case "PUT"->result=hub.saveProvider(actor,id,body(exchange));
                        case "DELETE"-> {
                            hub.deleteProvider(actor,id,expectedVersion(exchange));
                            status=204;
                        }
                        default->throw new HubException("METHOD_NOT_ALLOWED",405);
                    }
                }
                else if(parts.length==5&&parts[4].equals("publish")&&method.equals("POST")) {
                    result=hub.publish(actor,id,Json.number(body(exchange),"version",-1));
                    status=201;
                }
                else if(parts.length==5&&parts[4].equals("execute")&&method.equals("POST")) {
                    var p=hub.entity("providers",id,actor);
                    var request=executionBody(exchange);
                    String scope="draft:"+p.get("version");
                    if(Json.bool(request,"async",false)) {
                        result=engine.enqueue(actor,p,scope,request,key);
                        status=202;
                    }
                    else result=engine.execute(actor,p,scope,request,key);
                }
                else throw new HubException("NOT_FOUND",404);
            }
            else if(path.equals("/api/publications")&&method.equals("GET")) {
                actor.requireDesigner();
                result=hub.list("publications",actor).stream().map(Json::object).map(p-> {
                    var copy=new LinkedHashMap<>(p);
                    copy.remove("snapshot");
                    return copy;
                }
                ).toList();
            }
            else if(parts.length==4&&parts[2].equals("publications")&&method.equals("DELETE")) {
                hub.unpublish(actor,parts[3]);
                status=204;
            }
            else if(parts.length==5&&parts[2].equals("services")&&method.equals("POST")) {
                if(!parts[4].matches("v[1-9][0-9]{0,5}"))throw new HubException("NOT_FOUND",404);
                var pub=hub.publication(actor,parts[3],Integer.parseInt(parts[4].substring(1)));
                var p=Json.map(pub,"snapshot");
                var request=executionBody(exchange);
                if(Json.bool(request,"async",false)) {
                    result=engine.enqueue(actor,p,Json.text(pub,"id",""),request,key);
                    status=202;
                }
                else result=engine.execute(actor,p,Json.text(pub,"id",""),request,key);
            }
            else if(path.equals("/api/openapi")&&method.equals("GET"))result=OpenApi.document(hub,actor);
            else if(path.equals("/api/import/openapi")&&method.equals("POST")) {
                result=OpenApi.importDocument(hub,actor,body(exchange));
                status=201;
            }
            else if(path.equals("/api/jobs")&&method.equals("GET"))result=engine.jobs(actor);
            else if(parts.length>=4&&parts[2].equals("jobs")) {
                if(parts.length==4&&method.equals("GET"))result=engine.job(actor,parts[3]);
                else if(parts.length==5&&parts[4].equals("cancel")&&method.equals("POST")) {
                    engine.cancel(actor,parts[3]);
                    status=204;
                }
                else if(parts.length==5&&parts[4].equals("retry")&&method.equals("POST")) {
                    result=engine.retryReadJob(actor,parts[3]);
                    status=202;
                }
                else throw new HubException("NOT_FOUND",404);
            }
            else if(path.equals("/api/keys")) {
                if(method.equals("GET"))result=hub.keys.list(actor);
                else if(method.equals("POST")) {
                    result=hub.keys.create(actor,body(exchange));
                    hub.audit(actor,"key.create",Json.text(Json.object(result),"id",""),"SUCCEEDED",null,0);
                    status=201;
                }
                else throw new HubException("METHOD_NOT_ALLOWED",405);
            }
            else if(parts.length==4&&parts[2].equals("keys")&&method.equals("DELETE")) {
                hub.keys.revoke(actor,parts[3]);
                hub.audit(actor,"key.revoke",parts[3],"SUCCEEDED",null,0);
                status=204;
            }
            else if(path.equals("/api/audit")&&method.equals("GET"))result=hub.auditLog(actor);
            else if(path.equals("/api/settings")) {
                if(method.equals("GET"))result=hub.settings(actor);
                else if(method.equals("PUT")) {
                    hub.saveSettings(actor,body(exchange));
                    status=204;
                }
                else throw new HubException("METHOD_NOT_ALLOWED",405);
            }
            else if(path.equals("/api/export")&&method.equals("GET")) {
                actor.requireAdmin();
                result=Map.of("format","connector-hub/config-v1","version",HubService.VERSION,"secretsIncluded",false,"connections",hub.list("connections",actor),"providers",redactedProviders(hub.list("providers",actor)));
            }
            else if(path.equals("/api/idempotency")&&method.equals("GET")) {
                actor.requireAdmin();
                result=hub.catalog.read(s-> {
                    var rows=new ArrayList<Object>();
                    for(var e:Json.map(s,"idempotency").entrySet()) {
                        var value=Json.object(e.getValue());
                        if(actor.tenant().equals(value.get("tenant"))) {
                            var row=new LinkedHashMap<>(value);
                            row.remove("result");
                            row.remove("fingerprint");
                            row.put("id",e.getKey());
                            rows.add(row);
                        }
                    }
                    return rows;
                }
                );
            }
            else if(parts.length==5&&parts[2].equals("idempotency")&&parts[4].equals("resolve")&&method.equals("POST")) {
                actor.requireAdmin();
                var input=body(exchange);
                String decision=Json.text(input,"decision","");
                if(!Set.of("CONFIRMED_SUCCESS","CONFIRMED_NO_EFFECT").contains(decision))throw HubException.invalid();
                hub.catalog.update(s-> {
                    var e=HubService.owned(Json.map(s,"idempotency"),parts[3],actor);
                    if(!Json.text(e,"status","").equals("UNKNOWN"))throw new HubException("CONFLICT",409);
                    if(decision.equals("CONFIRMED_SUCCESS")) {
                        e.put("status","DONE");
                        e.put("result",Map.of("status","SUCCEEDED","data",input.getOrDefault("result",Map.of()),"reconciled",true));
                    }
                    else {
                        e.put("status","FAILED");
                        e.put("code","RECONCILED_NO_EFFECT");
                        e.put("httpStatus",409);
                    }
                    e.put("resolvedBy",actor.keyId());
                    e.put("completedAt",Instant.now().toString());
                    return null;
                }
                );
                hub.audit(actor,"idempotency.resolve",parts[3],decision,null,0);
                status=204;
            }
            else if(path.equals("/api/maintenance/prune")&&method.equals("POST")) {
                actor.requireAdmin();
                Instant cutoff=Instant.now().minus(Duration.ofDays(7));
                result=hub.catalog.update(s-> {
                    int removed=0;
                    for(String collection:List.of("jobs","idempotency")) {
                        var all=Json.map(s,collection);
                        var it=all.entrySet().iterator();
                        while(it.hasNext()) {
                            var entry=it.next();
                            var v=Json.object(entry.getValue());
                            if(actor.tenant().equals(v.get("tenant"))&&Set.of("DONE","FAILED","SUCCEEDED","CANCELLED").contains(Json.text(v,"status",""))&&Instant.parse(Json.text(v,"createdAt",Instant.now().toString())).isBefore(cutoff)) {
                                it.remove();
                                removed++;
                            }
                        }
                    }
                    return Map.of("removed",removed,"unknownEntriesRetained",true);
                }
                );
                hub.audit(actor,"maintenance.prune",actor.tenant(),"SUCCEEDED",null,0);
            }
            else throw new HubException("NOT_FOUND",404);
            if(result instanceof Map<?,?> map&&map.containsKey("version"))exchange.getResponseHeaders().set("ETag","\""+map.get("version")+"\"");
            send(exchange,status,result);
        }
        catch(HubException e) {
            try {
                if(e.status()==401)exchange.getResponseHeaders().set("WWW-Authenticate","Bearer");
                if(e.status()==429)exchange.getResponseHeaders().set("Retry-After","60");
                send(exchange,e.status(),Map.of("error",Map.of("code",e.code(),"message",message(e.code(),exchange.getRequestHeaders().getFirst("Accept-Language")),"requestId",requestId)));
            }
            catch(IOException ignored) {
            }
        }
        catch(Exception e) {
            System.err.println("Request "+requestId+" failed: "+e.getClass().getSimpleName());
            try {
                send(exchange,500,Map.of("error",Map.of("code","INTERNAL_ERROR","message",message("INTERNAL_ERROR",exchange.getRequestHeaders().getFirst("Accept-Language")),"requestId",requestId)));
            }
            catch(IOException ignored) {
            }
        }
        finally {
            if(acquired)requests.release();
            exchange.close();
        }
    }
    private static List<Object> redactedProviders(List<Object> list) {
        for(Object o:list)for(Object p:Json.list(Json.object(o),"parameters")) {
            var d=Json.object(p);
            if(Json.bool(d,"sensitive",false)&&d.containsKey("value")&&!Json.text(d,"source","").equals("SECRET"))d.put("value","[REDACTED]");
        }
        return list;
    }
    private static long expectedVersion(HttpExchange e) {
        String value=e.getRequestHeaders().getFirst("If-Match");
        try {
            return Long.parseLong(value==null?"-1":value.replace("\"",""));
        }
        catch(NumberFormatException x) {
            throw HubException.invalid();
        }
    }
    private static Map<String,Object> executionBody(HttpExchange e)throws IOException {
        var b=body(e);
        if(!Set.of("input","page","async").containsAll(b.keySet()))throw HubException.invalid();
        return b;
    }
    private static Map<String,Object> body(HttpExchange e)throws IOException {
        String encoding=e.getRequestHeaders().getFirst("Content-Encoding");
        if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))throw new HubException("UNSUPPORTED_MEDIA",415);
        String type=e.getRequestHeaders().getFirst("Content-Type");
        if(type==null||!type.toLowerCase(Locale.ROOT).startsWith("application/json"))throw new HubException("UNSUPPORTED_MEDIA",415);
        String length=e.getRequestHeaders().getFirst("Content-Length");
        try {
            if(length!=null&&Long.parseLong(length)>1_048_576)throw new HubException("BODY_TOO_LARGE",413);
        }
        catch(NumberFormatException ex) {
            throw HubException.invalid();
        }
        byte[] bytes=e.getRequestBody().readNBytes(1_048_577);
        if(bytes.length>1_048_576)throw new HubException("BODY_TOO_LARGE",413);
        if(bytes.length==0)return new LinkedHashMap<>();
        try {
            return Json.obj(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());
        }
        catch(CharacterCodingException ex) {
            throw HubException.invalid();
        }
    }
    private static void headers(HttpExchange e,String request) {
        var h=e.getResponseHeaders();
        h.set("X-Request-ID",request);
        h.set("X-Content-Type-Options","nosniff");
        h.set("X-Frame-Options","DENY");
        h.set("Referrer-Policy","no-referrer");
        h.set("Cache-Control","no-store");
        h.set("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        h.set("Permissions-Policy","camera=(), microphone=(), geolocation=()");
    }
    private static void send(HttpExchange e,int status,Object value)throws IOException {
        if(status==204) {
            e.sendResponseHeaders(204,-1);
            return;
        }
        byte[] data=Json.stringify(value).getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        e.sendResponseHeaders(status,data.length);
        e.getResponseBody().write(data);
    }
    private static void staticFile(HttpExchange e,String path,boolean head)throws IOException {
        boolean doc=path.matches("/docs/(en|de|fa)/[a-z-]+\\.md");
        if(!STATIC.contains(path)&&!doc)throw new HubException("NOT_FOUND",404);
        String resource=path.equals("/")?"/public/index.html":"/public"+path;
        try(InputStream in=HubHttpServer.class.getResourceAsStream(resource)) {
            if(in==null)throw new HubException("NOT_FOUND",404);
            byte[] data=in.readAllBytes();
            String content=path.endsWith(".js")?"text/javascript":path.endsWith(".css")?"text/css":path.endsWith(".svg")?"image/svg+xml":path.endsWith(".json")?"application/json":doc?"text/plain":"text/html";
            e.getResponseHeaders().set("Content-Type",content+"; charset=utf-8");
            e.sendResponseHeaders(200,head?-1:data.length);
            if(!head)e.getResponseBody().write(data);
        }
    }
    private static final Map<String,Map<String,Object>> MESSAGES=new ConcurrentHashMap<>();
    private static String message(String code,String language) {
        String lang=language!=null&&language.toLowerCase(Locale.ROOT).startsWith("fa")?"fa":language!=null&&language.toLowerCase(Locale.ROOT).startsWith("de")?"de":"en";
        var strings=MESSAGES.computeIfAbsent(lang,l-> {
            try(InputStream in=HubHttpServer.class.getResourceAsStream("/public/locales/"+l+".json")) {
                return in==null?Map.of():Json.obj(new String(in.readAllBytes(),StandardCharsets.UTF_8));
            }
            catch(Exception e) {
                return Map.of();
            }
        }
        );
        return Json.text(strings,"error."+code,code);
    }
    @Override public void close() {
        server.stop(2);
    }
}
