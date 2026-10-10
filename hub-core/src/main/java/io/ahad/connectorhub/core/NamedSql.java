package io.ahad.connectorhub.core;
import java.util.*;
/** Lexes named binds without substituting SQL text. SQL is trusted designer configuration, not a sandbox. */
public final class NamedSql {
    private NamedSql() {
    }
    public record Prepared(String sql,List<String> names) {
    }
    public static Prepared parse(String sql) {
        if(sql==null||sql.isBlank()||sql.length()>100_000)throw HubException.invalid();
        var b=new StringBuilder();
        var names=new ArrayList<String>();
        int i=0;
        while(i<sql.length()) {
            char c=sql.charAt(i);
            if(c=='\''||c=='"'||c=='`'||c=='[') {
                char end=c=='['?']':c;
                b.append(c);
                i++;
                boolean closed=false;
                while(i<sql.length()) {
                    char x=sql.charAt(i++);
                    b.append(x);
                    if(x==end) {
                        if(i<sql.length()&&sql.charAt(i)==end) {
                            b.append(sql.charAt(i++));
                        }
                        else {
                            closed=true;
                            break;
                        }
                    }
                }
                if(!closed)throw HubException.invalid();
                continue;
            }
            if(c=='-'&&i+1<sql.length()&&sql.charAt(i+1)=='-') {
                int end=sql.indexOf('\n',i);
                if(end<0)end=sql.length();
                b.append(sql,i,end);
                i=end;
                continue;
            }
            if(c=='/'&&i+1<sql.length()&&sql.charAt(i+1)=='*') {
                int start=i;
                i+=2;
                int depth=1;
                while(i<sql.length()&&depth>0) {
                    if(i+1<sql.length()&&sql.startsWith("/*",i)) {
                        depth++;
                        i+=2;
                    }
                    else if(i+1<sql.length()&&sql.startsWith("*/",i)) {
                        depth--;
                        i+=2;
                    }
                    else i++;
                }
                if(depth!=0)throw HubException.invalid();
                b.append(sql,start,i);
                continue;
            }
            if(c=='$') {
                int end=sql.indexOf('$',i+1);
                if(end>=0) {
                    String tag=sql.substring(i,end+1);
                    if(tag.matches("\\$(?:[A-Za-z_][A-Za-z0-9_]*)?\\$")) {
                        int close=sql.indexOf(tag,end+1);
                        if(close<0)throw HubException.invalid();
                        b.append(sql,i,close+tag.length());
                        i=close+tag.length();
                        continue;
                    }
                }
            }
            if(c==';')throw new HubException("MULTI_STATEMENT",400);
            if(c==':'&&i+1<sql.length()&&sql.charAt(i+1)==':') {
                b.append("::");
                i+=2;
                continue;
            }
            if(c==':'&&i+1<sql.length()&&(Character.isLetter(sql.charAt(i+1))||sql.charAt(i+1)=='_')) {
                int start=++i;
                while(i<sql.length()&&(Character.isLetterOrDigit(sql.charAt(i))||sql.charAt(i)=='_'))i++;
                names.add(sql.substring(start,i));
                b.append('?');
                continue;
            }
            if(c=='?')throw new HubException("POSITIONAL_SQL",400);
            b.append(c);
            i++;
        }
        return new Prepared(b.toString(),List.copyOf(names));
    }
    public static String identifier(String name) {
        if(name==null||!name.matches("[A-Za-z_][A-Za-z0-9_$]{0,127}"))throw new HubException("SQL_IDENTIFIER",400);
        return name;
    }
    public static String qualified(String name,String quote) {
        return Arrays.stream(name.split("\\.",-1)).map(NamedSql::identifier).map(s->quote+s+quote).reduce((a,b)->a+"."+b).orElseThrow();
    }
}
