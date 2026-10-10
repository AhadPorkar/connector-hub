package io.ahad.connectorhub.server;
import io.ahad.connectorhub.core.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
/** Durable duplicate suppression is not distributed exactly-once delivery. Ambiguous writes stay UNKNOWN. */
public final class ExecutionEngine implements AutoCloseable {
    private final HubService hub;
    private final Resilience resilience=new Resilience();
    private final Semaphore concurrency;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    public ExecutionEngine(HubService hub) {
        this.hub=hub;
        concurrency=new Semaphore(hub.options.maxConcurrent());
        recover();
        worker.scheduleWithFixedDelay(this::tickSafe,500,500,TimeUnit.MILLISECONDS);
    }
    private void recover() {
        hub.catalog.update(s-> {
            for(Object o:Json.map(s,"jobs").values()) {
                var job=Json.object(o);
                if("RUNNING".equals(job.get("status"))) {
                    job.put("status","UNKNOWN");
                    job.put("code","EXECUTION_UNKNOWN");
                }
            }
            for(Object o:Json.map(s,"idempotency").values()) {
                var entry=Json.object(o);
                if("STARTED".equals(entry.get("status")))entry.put("status","UNKNOWN");
            }
            return null;
        }
        );
    }
    public Object execute(Identity actor,Map<String,Object> provider,String scope,Map<String,Object> request,String idempotency) {
        String providerId=Json.text(provider,"id","");
        if(!actor.canInvoke(providerId))throw new HubException("FORBIDDEN",403);
        var connection=hub.entity("connections",Json.text(provider,"connectionId",""),actor);
        Connector connector=hub.connector(Json.text(connection,"type",""));
        boolean safe=connector.retrySafe(provider);
        validateKey(idempotency,!safe);
        String correlation=UUID.randomUUID().toString();
        var system=Map.<String,Object>of("tenant",actor.tenant(),"principal",actor.keyId(),"correlationId",correlation,"now",Instant.now().toString());
        var settings=hub.catalog.read(s->Json.map(Json.map(s,"settings"),actor.tenant()));
        var parameters=Parameters.resolve(Json.list(provider,"parameters"),Json.map(request,"input"),system,settings,Json.map(connection,"secrets"));
        var page=Json.map(request,"page");
        if(Json.list(page,"filters").size()>20||Json.list(page,"orders").size()>5)throw HubException.invalid();
        var context=new Connector.Context(connection,provider,parameters,page,actor,correlation);
        context.pageSize();
        context.pageIndex();
        context.timeoutMillis();
        resilience.rateLimit(actor.tenant()+":"+actor.keyId()+":"+providerId,Json.integer(provider,"rateLimit",120));
        if(!concurrency.tryAcquire())throw new HubException("CAPACITY",503);
        String key=idempotency==null||idempotency.isBlank()?null:Crypto.sha256(actor.tenant()+"|"+actor.keyId()+"|"+providerId+"|"+scope+"|"+idempotency);
        String fingerprint=Crypto.sha256(Json.canonical(Map.of("input",Json.map(request,"input"),"page",page,"provider",provider)));
        long started=System.nanoTime();
        boolean claimed=false;
        AtomicInteger attempts=new AtomicInteger();
        try {
            if(key!=null) {
                Map<String,Object> cached=hub.catalog.update(s-> {
                    var entries=Json.map(s,"idempotency");
                    Object old=entries.get(key);
                    if(old!=null) {
                        var e=Json.object(old);
                        if(!fingerprint.equals(e.get("fingerprint")))throw new HubException("IDEMPOTENCY_CONFLICT",409);
                        return e;
                    }
                    if(entries.size()>=5000)throw new HubException("CAPACITY",503);
                    var e=new LinkedHashMap<String,Object>();
                    e.put("fingerprint",fingerprint);
                    e.put("tenant",actor.tenant());
                    e.put("principal",actor.keyId());
                    e.put("providerId",providerId);
                    e.put("status","STARTED");
                    e.put("createdAt",Instant.now().toString());
                    entries.put(key,e);
                    return null;
                }
                );
                if(cached!=null) {
                    String status=Json.text(cached,"status","");
                    if(status.equals("DONE")) {
                        var replay=Json.object(cached.get("result"));
                        replay.put("replayed",true);
                        return replay;
                    }
                    if(status.equals("FAILED"))throw new HubException(Json.text(cached,"code","UPSTREAM_UNAVAILABLE"),Json.integer(cached,"httpStatus",502));
                    throw new HubException("EXECUTION_UNKNOWN",409);
                }
                claimed=true;
            }
            Object raw=resilience.execute(actor.tenant()+":"+connection.get("id"),safe,Json.integer(provider,"maxAttempts",1),()-> {
                attempts.incrementAndGet();
                return connector.execute(context);
            }
            );
            Object mapped=Parameters.mapOutput(raw,Json.text(provider,"fieldsRoot","/data"),Json.list(provider,"outputMappings"));
            var result=new LinkedHashMap<String,Object>();
            result.put("executionId",correlation);
            result.put("status","SUCCEEDED");
            result.put("data",mapped);
            result.put("durationMs",elapsed(started));
            result.put("attempts",attempts.get());
            result.put("replayed",false);
            if(raw instanceof Map<?,?>) {
                var meta=new LinkedHashMap<>(Json.object(raw));
                meta.remove("data");
                result.put("meta",meta);
            }
            if(Json.stringify(result).length()>2_097_152)throw new HubException("UPSTREAM_TOO_LARGE",502);
            if(key!=null)hub.catalog.update(s-> {
                var e=Json.object(Json.map(s,"idempotency").get(key));
                e.put("status","DONE");
                e.put("result",result);
                e.put("completedAt",Instant.now().toString());
                return null;
            }
            );
            hub.audit(actor,"execute",providerId,"SUCCEEDED",null,elapsed(started));
            return result;
        }
        catch(HubException e) {
            if(claimed) {
                String state=safe||attempts.get()==0?"FAILED":"UNKNOWN";
                hub.catalog.update(s-> {
                    var entry=Json.object(Json.map(s,"idempotency").get(key));
                    if("DONE".equals(entry.get("status")))return null;
                    entry.put("status",state);
                    entry.put("code",e.code());
                    entry.put("httpStatus",e.status());
                    entry.put("completedAt",Instant.now().toString());
                    return null;
                }
                );
            }
            hub.audit(actor,"execute",providerId,"FAILED",e.code(),elapsed(started));
            throw e;
        }
        catch(RuntimeException e) {
            if(claimed)hub.catalog.update(s-> {
                var entry=Json.object(Json.map(s,"idempotency").get(key));
                if("DONE".equals(entry.get("status")))return null;
                entry.put("status","UNKNOWN");
                entry.put("code","EXECUTION_UNKNOWN");
                return null;
            }
            );
            hub.audit(actor,"execute",providerId,"UNKNOWN","EXECUTION_UNKNOWN",elapsed(started));
            throw new HubException("EXECUTION_UNKNOWN",500);
        }
        finally {
            concurrency.release();
        }
    }
    public Object enqueue(Identity actor,Map<String,Object> provider,String scope,Map<String,Object> request,String key) {
        String providerId=Json.text(provider,"id","");
        if(!actor.canInvoke(providerId))throw new HubException("FORBIDDEN",403);
        var conn=hub.entity("connections",Json.text(provider,"connectionId",""),actor);
        validateKey(key,!hub.connector(Json.text(conn,"type","")).retrySafe(provider));
        String digest=key==null?null:Crypto.sha256(actor.tenant()+"|"+actor.keyId()+"|"+providerId+"|"+scope+"|"+key);
        String fingerprint=Crypto.sha256(Json.canonical(Map.of("input",Json.map(request,"input"),"page",Json.map(request,"page"),"provider",provider)));
        return hub.catalog.update(s-> {
            var jobs=Json.map(s,"jobs");
            if(digest!=null)for(Object o:jobs.values()) {
                var old=Json.object(o);
                if(digest.equals(old.get("dedupe"))) {
                    if(!fingerprint.equals(old.get("fingerprint")))throw new HubException("IDEMPOTENCY_CONFLICT",409);
                    return publicJob(old,actor);
                }
            }
            if(jobs.size()>=1000)throw new HubException("CAPACITY",503);
            String id=UUID.randomUUID().toString();
            var job=new LinkedHashMap<String,Object>();
            job.put("id",id);
            job.put("tenant",actor.tenant());
            job.put("principal",actor.keyId());
            job.put("providerId",providerId);
            job.put("name",provider.get("name"));
            job.put("providerSnapshot",provider);
            job.put("scope",scope);
            job.put("request",request);
            job.put("idempotencyKey",key);
            job.put("dedupe",digest);
            job.put("fingerprint",fingerprint);
            job.put("status","QUEUED");
            job.put("createdAt",Instant.now().toString());
            jobs.put(id,job);
            return publicJob(job,actor);
        }
        );
    }
    private void tickSafe() {
        try {
            tick();
        }
        catch(Exception e) {
            System.err.println("Queue tick failed: "+e.getClass().getSimpleName());
        }
    }
    private void tick() {
        if(!hub.catalog.read(s->Json.map(s,"jobs").values().stream().map(Json::object).anyMatch(j->"QUEUED".equals(j.get("status")))))return;
        Map<String,Object> job=hub.catalog.update(s-> {
            for(Object o:Json.map(s,"jobs").values()) {
                var j=Json.object(o);
                if(Json.text(j,"status","").equals("QUEUED")) {
                    j.put("status","RUNNING");
                    j.put("startedAt",Instant.now().toString());
                    return Json.object(Json.copy(j));
                }
            }
            return null;
        }
        );
        if(job==null)return;
        String id=Json.text(job,"id","");
        try {
            Identity actor=hub.keys.byId(Json.text(job,"principal",""));
            if(!actor.tenant().equals(job.get("tenant")))throw new HubException("FORBIDDEN",403);
            Object result=execute(actor,Json.map(job,"providerSnapshot"),Json.text(job,"scope",""),Json.map(job,"request"),Json.text(job,"idempotencyKey",null));
            hub.catalog.update(s-> {
                var j=Json.object(Json.map(s,"jobs").get(id));
                j.put("status","SUCCEEDED");
                j.put("result",result);
                j.put("completedAt",Instant.now().toString());
                return null;
            }
            );
        }
        catch(HubException e) {
            hub.catalog.update(s-> {
                var j=Json.object(Json.map(s,"jobs").get(id));
                var durable=Json.map(s,"idempotency").get(Json.text(j,"dedupe",""));
                boolean unknown=durable instanceof Map<?,?>&&"UNKNOWN".equals(Json.object(durable).get("status"));
                j.put("status",e.code().equals("EXECUTION_UNKNOWN")||unknown?"UNKNOWN":"FAILED");
                j.put("code",e.code());
                j.put("completedAt",Instant.now().toString());
                return null;
            }
            );
        }
    }
    public List<Object> jobs(Identity actor) {
        return hub.catalog.read(s->Json.map(s,"jobs").values().stream().map(Json::object).filter(j->actor.tenant().equals(j.get("tenant"))&&(actor.auditor()||actor.keyId().equals(j.get("principal")))).sorted(Comparator.comparing(j->Json.text(j,"createdAt","") ,Comparator.reverseOrder())).map(j->(Object)publicJob(j,actor)).limit(500).toList());
    }
    public Map<String,Object> job(Identity actor,String id) {
        var job=hub.entity("jobs",id,actor);
        if(!actor.auditor()&&!actor.keyId().equals(job.get("principal")))throw new HubException("NOT_FOUND",404);
        return publicJob(job,actor);
    }
    public void cancel(Identity actor,String id) {
        hub.catalog.update(s-> {
            var job=HubService.owned(Json.map(s,"jobs"),id,actor);
            if(!actor.designer()&&!actor.keyId().equals(job.get("principal")))throw new HubException("FORBIDDEN",403);
            if(!Json.text(job,"status","").equals("QUEUED"))throw new HubException("JOB_NOT_QUEUED",409);
            job.put("status","CANCELLED");
            return null;
        }
        );
    }
    public Object retryReadJob(Identity actor,String id) {
        actor.requireDesigner();
        var job=hub.entity("jobs",id,actor);
        String status=Json.text(job,"status","");
        if(!Set.of("FAILED","UNKNOWN").contains(status))throw new HubException("JOB_NOT_FAILED",409);
        var p=Json.map(job,"providerSnapshot");
        var conn=hub.entity("connections",Json.text(p,"connectionId",""),actor);
        if(!hub.connector(Json.text(conn,"type","")).retrySafe(p))throw new HubException("UNSAFE_RETRY",409);
        return enqueue(actor,p,Json.text(job,"scope",""),Json.map(job,"request"),UUID.randomUUID().toString());
    }
    private static Map<String,Object> publicJob(Map<String,Object> job,Identity actor) {
        var copy=new LinkedHashMap<>(job);
        for(String key:List.of("providerSnapshot","request","idempotencyKey","dedupe","fingerprint"))copy.remove(key);
        if(!actor.designer()&&!actor.keyId().equals(job.get("principal")))copy.remove("result");
        return copy;
    }
    private static void validateKey(String key,boolean required) {
        if(required&&(key==null||key.isBlank()))throw new HubException("IDEMPOTENCY_REQUIRED",400);
        if(key!=null&&!key.matches("[A-Za-z0-9_.:-]{8,128}"))throw new HubException("IDEMPOTENCY_INVALID",400);
    }
    private static long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
    }
    @Override public void close() {
        worker.shutdown();
        try {
            if(!worker.awaitTermination(65,TimeUnit.SECONDS))worker.shutdownNow();
        }
        catch(InterruptedException e) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
