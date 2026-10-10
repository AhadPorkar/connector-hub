package io.ahad.connectorhub.server;
import io.ahad.connectorhub.core.*;
import java.util.*;
/** Published contracts include caller INPUT parameters only. Secrets and constants are never exposed. */
public final class OpenApi {
    private OpenApi() {
    }
    public static Map<String,Object> document(HubService hub,Identity actor) {
        var paths=new LinkedHashMap<String,Object>();
        for(Object o:hub.list("publications",actor)) {
            var publication=Json.object(o);
            if(!Json.bool(publication,"active",false))continue;
            var p=Json.map(publication,"snapshot");
            if(!actor.canInvoke(Json.text(p,"id",""))&&!actor.designer())continue;
            paths.put(Json.text(publication,"path",""),path(p,Json.text(publication,"id","")));
        }
        return Map.of("openapi","3.1.1","info",Map.of("title","Connector Hub published services","version",HubService.VERSION,"description","Tenant inferred exclusively from the bearer credential. Published provider snapshots are immutable; connection credentials may rotate."),"paths",paths,"components",Map.of("securitySchemes",Map.of("BearerAuth",Map.of("type","http","scheme","bearer"))),"security",List.of(Map.of("BearerAuth",List.of())));
    }
    public static Map<String,Object> path(Map<String,Object> p,String operationId) {
        var properties=new LinkedHashMap<String,Object>();
        var required=new ArrayList<String>();
        for(Object o:Json.list(p,"parameters")) {
            var d=Json.object(o);
            if(!Json.text(d,"source","INPUT").equals("INPUT"))continue;
            String name=Json.text(d,"name","");
            properties.put(name,type(Json.text(d,"type","STRING")));
            if(Json.bool(d,"required",false))required.add(name);
        }
        var inputSchema=new LinkedHashMap<String,Object>();
        inputSchema.put("type","object");
        inputSchema.put("properties",properties);
        inputSchema.put("additionalProperties",false);
        if(!required.isEmpty())inputSchema.put("required",required);
        var requestSchema=Map.of("type","object","properties",Map.of("input",inputSchema,"page",Map.of("type","object","properties",Map.of("index",Map.of("type","integer","minimum",0,"maximum",10000),"size",Map.of("type","integer","minimum",1,"maximum",500),"filters",Map.of("type","array","maxItems",20,"items",Map.of("type","object")),"orders",Map.of("type","array","maxItems",5,"items",Map.of("type","object")))),"async",Map.of("type","boolean")),"additionalProperties",false);
        var post=new LinkedHashMap<String,Object>();
        post.put("operationId","invoke_"+operationId.replace('-','_'));
        post.put("summary",Json.text(p,"name",""));
        post.put("description",Json.text(p,"description","")+" Idempotency-Key is required for writes and operations not classified as retry-safe reads.");
        post.put("parameters",List.of(Map.of("name","Idempotency-Key","in","header","required",false,"schema",Map.of("type","string","minLength",8,"maxLength",128))));
        post.put("requestBody",Map.of("required",true,"content",Map.of("application/json",Map.of("schema",requestSchema))));
        var responses=new LinkedHashMap<String,Object>();
        var responseSchema=Map.of("type","object","properties",Map.of("executionId",Map.of("type","string"),"data",Map.of(),"replayed",Map.of("type","boolean")));
        responses.put("200",Map.of("description","Successful execution or durable replay","content",Map.of("application/json",Map.of("schema",responseSchema))));
        responses.put("202",Map.of("description","Durable job accepted"));
        responses.put("400",Map.of("description","Invalid input or missing idempotency key"));
        responses.put("401",Map.of("description","Missing or expired credential"));
        responses.put("403",Map.of("description","Scope denied"));
        responses.put("409",Map.of("description","Version conflict or ambiguous previous execution"));
        responses.put("429",Map.of("description","Rate limit exceeded"));
        responses.put("502",Map.of("description","Upstream failure"));
        responses.put("503",Map.of("description","Capacity or circuit breaker limit"));
        post.put("responses",responses);
        return Map.of("post",post);
    }
    private static Map<String,Object> type(String name) {
        return switch(name) {
            case "INTEGER"->Map.of("type","integer");
            case "DECIMAL"->Map.of("type","number");
            case "BOOLEAN"->Map.of("type","boolean");
            case "JSON"->Map.of();
            case "DATE"->Map.of("type","string","format","date");
            case "DATETIME"->Map.of("type","string","format","date-time");
            default->Map.of("type","string");
        }
        ;
    }
    public static List<Object> importDocument(HubService hub,Identity actor,Map<String,Object> body) {
        actor.requireDesigner();
        String connection=Json.text(body,"connectionId","");
        var selected=hub.entity("connections",connection,actor);
        if(!Set.of("REST","ODATA").contains(Json.text(selected,"type","")))throw new HubException("CONNECTOR_UNSUPPORTED",400);
        var doc=Json.map(body,"document");
        if(!Json.text(doc,"openapi","").startsWith("3."))throw new HubException("OPENAPI_VERSION",400);
        var output=new ArrayList<Object>();
        for(var entry:Json.map(doc,"paths").entrySet()) {
            String path=entry.getKey();
            for(var operation:Json.object(entry.getValue()).entrySet()) {
                String method=operation.getKey().toUpperCase(Locale.ROOT);
                if(!Set.of("GET","POST","PUT","PATCH","DELETE").contains(method))continue;
                var spec=Json.object(operation.getValue());
                var params=new ArrayList<Object>();
                var allParams=new ArrayList<Object>(Json.list(Json.object(entry.getValue()),"parameters"));
                allParams.addAll(Json.list(spec,"parameters"));
                for(Object o:allParams) {
                    var d=Json.object(o);
                    if(d.containsKey("$ref"))throw new HubException("OPENAPI_REFERENCE",400);
                    String name=Json.text(d,"name","");
                    String location=Json.text(d,"in","query");
                    if(!Set.of("path","query","header").contains(location))continue;
                    String type=switch(Json.text(Json.map(d,"schema"),"type","string")) {
                        case "integer"->"INTEGER";
                        case "number"->"DECIMAL";
                        case "boolean"->"BOOLEAN";
                        default->"STRING";
                    }
                    ;
                    var p=new LinkedHashMap<>(HubService.parameter(name,type,Json.bool(d,"required",false)));
                    p.put("location",location.toUpperCase(Locale.ROOT));
                    params.add(p);
                }
                String name=Json.text(spec,"summary",method+" "+path);
                String slug=Json.text(spec,"operationId","imported").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]","-");
                if(slug.isEmpty()||!Character.isLetter(slug.charAt(0)))slug="service-"+slug;
                slug=slug.substring(0,Math.min(40,slug.length()))+"-"+UUID.randomUUID().toString().substring(0,6);
                var config=new LinkedHashMap<String,Object>();
                config.put("method",method);
                config.put("path",path.replaceAll("\\{([A-Za-z_][A-Za-z0-9_]*)}","\\${$1}"));
                config.put("responseType","JSON");
                if(spec.containsKey("requestBody")) {
                    params.add(HubService.parameter("body","JSON",true));
                    config.put("body","${body}");
                }
                var provider=new LinkedHashMap<String,Object>();
                provider.put("name",name);
                provider.put("slug",slug);
                provider.put("connectionId",connection);
                provider.put("operation",method.equals("GET")?"READ":"EXECUTE");
                provider.put("parameters",params);
                provider.put("config",config);
                provider.put("description","Imported draft: review authentication, references, response mapping and idempotency before publishing.");
                if(output.size()>=100)throw new HubException("CAPACITY",413);
                output.add(hub.saveProvider(actor,null,provider));
            }
        }
        return output;
    }
}
