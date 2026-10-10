package io.ahad.connectorhub.server;
import io.ahad.connectorhub.core.*;
import java.util.*;
public final class DemoSeed {
    private DemoSeed() {
    }
    public static void seed(HubService hub,int upstreamPort) {
        Identity actor=hub.keys.byId("bootstrap");
        if(!hub.list("connections",actor).isEmpty())return;
        var local=hub.saveConnection(actor,null,Map.of("name","Workspace demo store","type","LOCAL","config",Map.of()));
        String localId=Json.text(local,"id","");
        var crud=hub.generateCrud(actor,localId,Map.of("table","customers"));
        for(Object o:crud) {
            var p=Json.object(o);
            hub.publish(actor,Json.text(p,"id",""),Json.number(p,"version",1));
        }
        String base="http://127.0.0.1:"+upstreamPort;
        var rest=connection(hub,actor,"Simulated customer API","REST",base);
        provider(hub,actor,rest,"Customer directory","customer-directory","READ",Map.of("method","GET","path","/customers","responseType","JSON"),List.of(),"/data/items",3);
        provider(hub,actor,rest,"Recovery demonstration","recovery-demo","READ",Map.of("method","GET","path","/flaky","responseType","JSON"),List.of(),"/data",3);
        provider(hub,actor,rest,"HTTP request laboratory","http-request-lab","EXECUTE",Map.of("method","POST","path","/echo","body",Map.of("message","${message}")),List.of(HubService.parameter("message","STRING",true)),"/data",1);
        provider(hub,actor,rest,"CSV download","csv-download","READ",Map.of("method","GET","path","/download","responseType","BINARY"),List.of(),"/data",1);
        String soap=connection(hub,actor,"Simulated SOAP calculator","SOAP",base);
        provider(hub,actor,soap,"SOAP addition","soap-addition","EXECUTE",Map.of("path","/soap","soapVersion","1.1","soapAction","urn:demo/Add","envelope","<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:d=\"urn:demo\"><s:Body><d:Add><d:a>${a}</d:a><d:b>${b}</d:b></d:Add></s:Body></s:Envelope>"),List.of(HubService.parameter("a","INTEGER",true),HubService.parameter("b","INTEGER",true)),"/data/Envelope/Body/AddResponse/AddResult",1);
        String graph=connection(hub,actor,"Simulated GraphQL work orders","GRAPHQL",base);
        provider(hub,actor,graph,"Work order query","work-orders","READ",Map.of("path","/graphql","document","query WorkOrders { workOrders { id status priority } }","variables",Map.of()),List.of(),"/data/data/workOrders",1);
        String odata=connection(hub,actor,"Simulated OData product catalog","ODATA",base);
        provider(hub,actor,odata,"Product catalog","product-catalog","READ",Map.of("path","/odata/Products","method","GET"),List.of(),"/data/value",2);
        hub.catalog.update(s-> {
            var records=new LinkedHashMap<String,Object>();
            for(int i=1; i<=3; i++)records.put("demo-"+i,Map.of("id","demo-"+i,"name",List.of("Nordwerk Manufacturing","Rhein Systems","Isar Automation").get(i-1),"version",1));
            Json.map(s,"documents").put("default:"+localId+":customers",records);
            return null;
        }
        );
    }
    private static String connection(HubService hub,Identity actor,String name,String type,String url) {
        return Json.text(hub.saveConnection(actor,null,Map.of("name",name,"type",type,"config",Map.of("baseUrl",url,"authType","NONE"))),"id","");
    }
    private static void provider(HubService hub,Identity actor,String connection,String name,String slug,String operation,Map<String,Object> config,List<Object> params,String root,int attempts) {
        var p=new LinkedHashMap<String,Object>();
        p.put("name",name);
        p.put("slug",slug);
        p.put("connectionId",connection);
        p.put("operation",operation);
        p.put("config",config);
        p.put("parameters",params);
        p.put("fieldsRoot",root);
        p.put("maxAttempts",attempts);
        p.put("description","Local simulation. No live vendor integration or performance claim.");
        var saved=hub.saveProvider(actor,null,p);
        hub.publish(actor,Json.text(saved,"id",""),Json.number(saved,"version",1));
    }
}
