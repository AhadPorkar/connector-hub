package io.ahad.connectorhub.connectors;
import io.ahad.connectorhub.core.*;
import java.util.*;
/** Small persistent document collection for offline demonstrations, not a relational database substitute. */
public final class LocalConnector implements Connector {
    private final Catalog catalog;
    public LocalConnector(Catalog catalog) {
        this.catalog=catalog;
    }
    @Override public String type() {
        return "LOCAL";
    }
    @Override public Set<String> operations() {
        return Set.of("READ","CREATE","UPDATE","DELETE");
    }
    @Override public boolean retrySafe(Map<String,Object> provider) {
        return Json.text(provider,"operation","").equals("READ");
    }
    private String collection(Context c) {
        String name=Json.text(c.config(),"collection","records");
        if(!name.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"))throw HubException.invalid();
        return c.identity().tenant()+":"+Json.text(c.connection(),"id","")+":"+name;
    }
    @Override public Object discover(Context c) {
        String prefix=c.identity().tenant()+":"+Json.text(c.connection(),"id","")+":";
        return catalog.read(s->Json.map(s,"documents").keySet().stream().filter(k->k.startsWith(prefix)).map(k->k.substring(prefix.length())).toList());
    }
    @Override public Object execute(Context c) {
        String name=collection(c);
        if(c.operation().equals("READ"))return catalog.read(s-> {
            var rows=new ArrayList<Object>(Json.map(Json.map(s,"documents"),name).values());
            String id=Json.text(c.parameters(),"id","");
            if(!id.isBlank())rows.removeIf(o->!id.equals(Json.text(Json.object(o),"id","")));
            String search=Json.text(c.parameters(),"search","").toLowerCase(Locale.ROOT);
            if(!search.isEmpty())rows.removeIf(o->!Json.stringify(o).toLowerCase(Locale.ROOT).contains(search));
            for(Object o:Json.list(c.page(),"filters")) {
                var f=Json.object(o);
                String field=Json.text(f,"field","");
                String op=Json.text(f,"op","eq");
                if(!Set.of("eq","ne","contains").contains(op))throw HubException.invalid();
                rows.removeIf(row-> {
                    Object actual=Json.object(row).get(field);
                    boolean eq=Objects.equals(Json.canonical(actual),Json.canonical(f.get("value")));
                    return switch(op) {
                        case "eq"->!eq;
                        case "ne"->eq;
                        default->actual==null||!actual.toString().contains(String.valueOf(f.get("value")));
                    }
                    ;
                }
                );
            }
            var orders=Json.list(c.page(),"orders");
            if(!orders.isEmpty()) {
                var order=Json.object(orders.getFirst());
                String field=Json.text(order,"field","id");
                String dir=Json.text(order,"direction","asc");
                if(!Set.of("asc","desc").contains(dir))throw HubException.invalid();
                rows.sort((a,b)-> {
                    Object x=Json.object(a).get(field),y=Json.object(b).get(field);
                    int result=compare(x,y);
                    return dir.equals("desc")?-result:result;
                }
                );
            }
            int total=rows.size(),from=Math.min(c.pageIndex()*c.pageSize(),total),to=Math.min(from+c.pageSize(),total);
            return Map.of("data",new ArrayList<>(rows.subList(from,to)),"total",total,"page",c.pageIndex(),"size",c.pageSize());
        }
        );
        return catalog.update(s-> {
            var all=Json.map(s,"documents");
            if(!all.containsKey(name))all.put(name,new LinkedHashMap<String,Object>());
            var records=Json.map(all,name);
            String id=Json.text(c.parameters(),"id","");
            if(id.isEmpty()&&c.operation().equals("CREATE"))id=UUID.randomUUID().toString();
            if(id.isEmpty()||id.length()>128)throw HubException.invalid();
            var previous=records.containsKey(id)?Json.object(records.get(id)):null;
            switch(c.operation()) {
                case "CREATE"-> {
                    if(previous!=null)throw new HubException("CONFLICT",409);
                    if(records.size()>=10_000)throw new HubException("CAPACITY",503);
                    var row=body(c);
                    row.put("id",id);
                    row.put("version",1);
                    records.put(id,row);
                    return Map.of("data",row,"affected",1);
                }
                case "UPDATE","DELETE"-> {
                    if(previous==null)throw new HubException("NOT_FOUND",404);
                    long expected=Json.number(c.parameters(),"expectedVersion",-1);
                    if(expected!=Json.number(previous,"version",0))throw new HubException("VERSION_CONFLICT",409);
                    if(c.operation().equals("DELETE")) {
                        records.remove(id);
                        return Map.of("data",Map.of("id",id),"affected",1);
                    }
                    var row=body(c);
                    row.put("id",id);
                    row.put("version",expected+1);
                    records.put(id,row);
                    return Map.of("data",row,"affected",1);
                }
                default->throw new HubException("OPERATION_UNSUPPORTED",400);
            }
        }
        );
    }
    private static int compare(Object a,Object b) {
        if(a==null)return b==null?0:-1;
        if(b==null)return 1;
        if(a instanceof Number&&b instanceof Number)return new java.math.BigDecimal(a.toString()).compareTo(new java.math.BigDecimal(b.toString()));
        return a.toString().compareToIgnoreCase(b.toString());
    }
    private static Map<String,Object> body(Context c) {
        Object template=c.config().getOrDefault("body",Map.of("name","${name}"));
        var row=Json.object(Parameters.template(template,c.parameters()));
        row.remove("id");
        row.remove("version");
        return row;
    }
}
