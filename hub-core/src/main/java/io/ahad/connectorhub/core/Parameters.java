package io.ahad.connectorhub.core;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.regex.*;
/** No scripting engine: explicit parameter sources and typed structural templates. */
public final class Parameters {
    private static final Pattern NAME=Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Pattern TOKEN=Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private Parameters() {
    }
    public static void validateDefinitions(List<Object> definitions) {
        if(definitions.size()>100)throw HubException.invalid();
        Set<String> names=new HashSet<>();
        for(Object o:definitions) {
            var d=Json.object(o);
            String n=Json.text(d,"name","");
            if(!NAME.matcher(n).matches()||!names.add(n))throw HubException.invalid();
            if(!Set.of("INPUT","CONSTANT","SYSTEM","CONFIG","SECRET").contains(Json.text(d,"source","INPUT")))throw HubException.invalid();
            if(!Set.of("STRING","INTEGER","DECIMAL","BOOLEAN","JSON","DATE","DATETIME").contains(Json.text(d,"type","STRING")))throw HubException.invalid();
            if(Json.text(d,"source","INPUT").equals("SYSTEM")&&!Set.of("tenant","principal","correlationId","now").contains(Json.text(d,"value","")))throw HubException.invalid();
        }
    }
    public static Map<String,Object> resolve(List<Object> defs,Map<String,Object> input,Map<String,Object> system,Map<String,Object> config,Map<String,Object> secrets) {
        validateDefinitions(defs);
        Set<String> allowed=new HashSet<>();
        for(Object o:defs) {
            var d=Json.object(o);
            if(Json.text(d,"source","INPUT").equals("INPUT"))allowed.add(Json.text(d,"name",""));
        }
        if(!allowed.containsAll(input.keySet()))throw new HubException("UNKNOWN_PARAMETER",400);
        var out=new LinkedHashMap<String,Object>();
        for(Object o:defs) {
            var d=Json.object(o);
            String name=Json.text(d,"name","");
            String source=Json.text(d,"source","INPUT");
            String ref=Json.text(d,"value","");
            Object v=switch(source) {
                case "INPUT"->input.containsKey(name)?input.get(name):d.get("value");
                case "CONSTANT"->d.get("value");
                case "SYSTEM"->system.get(ref);
                case "CONFIG"->config.get(ref);
                case "SECRET"->secrets.get(ref);
                default->throw HubException.invalid();
            }
            ;
            if(v==null&&Json.bool(d,"required",false))throw new HubException("REQUIRED_PARAMETER",400);
            if(v!=null) {
                v=convert(v,Json.text(d,"type","STRING"));
                if(v instanceof String s&&s.length()>Json.integer(d,"maxLength",65_536))throw HubException.invalid();
            }
            if(v!=null||input.containsKey(name)||d.containsKey("value")||!source.equals("INPUT"))out.put(name,v);
        }
        return out;
    }
    public static Object convert(Object v,String type) {
        if(v==null)return null;
        try {
            return switch(type) {
                case "STRING"-> {
                    if(v instanceof Map||v instanceof List)throw HubException.invalid();
                    yield v.toString();
                }
                case "INTEGER"->new BigDecimal(v.toString()).longValueExact();
                case "DECIMAL"->new BigDecimal(v.toString());
                case "BOOLEAN"-> {
                    if(v instanceof Boolean b)yield b;
                    if("true".equalsIgnoreCase(v.toString()))yield true;
                    if("false".equalsIgnoreCase(v.toString()))yield false;
                    throw HubException.invalid();
                }
                case "DATE"->LocalDate.parse(v.toString()).toString();
                case "DATETIME"->OffsetDateTime.parse(v.toString()).toString();
                case "JSON"->v instanceof String s?Json.parse(s):Json.copy(v);
                default->throw HubException.invalid();
            }
            ;
        }
        catch(Exception e) {
            throw new HubException("PARAMETER_TYPE",400);
        }
    }
    public static Object template(Object value,Map<String,Object> parameters) {
        if(value instanceof String s) {
            Matcher m=TOKEN.matcher(s);
            if(m.matches()) {
                if(!parameters.containsKey(m.group(1)))throw new HubException("MISSING_BINDING",400);
                return parameters.get(m.group(1));
            }
            return interpolate(s,parameters,false);
        }
        if(value instanceof Map<?,?> map) {
            var out=new LinkedHashMap<String,Object>();
            for(var e:map.entrySet())out.put(e.getKey().toString(),template(e.getValue(),parameters));
            return out;
        }
        if(value instanceof List<?> l) {
            var out=new ArrayList<>();
            for(Object x:l)out.add(template(x,parameters));
            return out;
        }
        return value;
    }
    public static String interpolate(String s,Map<String,Object> p,boolean xml) {
        var m=TOKEN.matcher(s);
        var b=new StringBuffer();
        while(m.find()) {
            if(!p.containsKey(m.group(1)))throw new HubException("MISSING_BINDING",400);
            Object v=p.get(m.group(1));
            String replacement=v==null?"":(v instanceof Map||v instanceof List?Json.stringify(v):v.toString());
            m.appendReplacement(b,Matcher.quoteReplacement(xml?xmlEscape(replacement):replacement));
        }
        m.appendTail(b);
        return b.toString();
    }
    public static String xmlEscape(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");
    }
    public static Object pointer(Object source,String pointer) {
        if(pointer==null||pointer.isEmpty())return source;
        if(!pointer.startsWith("/"))throw HubException.invalid();
        Object current=source;
        for(String segment:pointer.substring(1).split("/",-1)) {
            if(segment.matches(".*~([^01]|$).*"))throw HubException.invalid();
            String key=segment.replace("~1","/").replace("~0","~");
            if(current instanceof Map<?,?> m)current=m.get(key);
            else if(current instanceof List<?> l) {
                try {
                    if(!key.matches("0|[1-9][0-9]*"))return null;
                    int i=Integer.parseInt(key);
                    current=i<l.size()?l.get(i):null;
                }
                catch(NumberFormatException e) {
                    return null;
                }
            }
            else return null;
        }
        return current;
    }
    public static Object mapOutput(Object data,String root,List<Object> mappings) {
        Object selected=pointer(data,root);
        if(mappings.isEmpty())return selected;
        if(selected instanceof List<?> list) {
            var out=new ArrayList<>();
            for(Object item:list)out.add(mapOne(item,mappings));
            return out;
        }
        return mapOne(selected,mappings);
    }
    private static Map<String,Object> mapOne(Object item,List<Object> defs) {
        var result=new LinkedHashMap<String,Object>();
        for(Object o:defs) {
            var d=Json.object(o);
            String name=Json.text(d,"name","");
            if(!NAME.matcher(name).matches()||result.containsKey(name))throw HubException.invalid();
            Object v=pointer(item,Json.text(d,"pointer",""));
            if(v==null&&Json.bool(d,"required",false))throw new HubException("OUTPUT_MAPPING",502);
            try {
                result.put(name,convert(v,Json.text(d,"type","JSON")));
            }
            catch(HubException e) {
                throw new HubException("OUTPUT_MAPPING",502);
            }
        }
        return result;
    }
}
