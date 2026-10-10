package io.ahad.connectorhub.connectors;
import io.ahad.connectorhub.core.*;
import java.sql.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executor;
/** JDBC metadata discovery, bound SQL, guarded CRUD, and ordered IN/OUT routine parameters. */
public final class JdbcConnector implements Connector {
    private final NetworkPolicy policy;
    private final Executor executor;
    public JdbcConnector(NetworkPolicy policy,Executor executor) {
        this.policy=policy;
        this.executor=executor;
    }
    @Override public String type() {
        return "JDBC";
    }
    @Override public Set<String> operations() {
        return Set.of("READ","CREATE","UPDATE","DELETE","QUERY","EXECUTE","PROCEDURE","FUNCTION");
    }
    @Override public boolean retrySafe(Map<String,Object> p) {
        return Json.text(p,"operation","").equals("READ");
    }
    private Connection open(Context c)throws SQLException {
        String url=Json.text(c.connectionConfig(),"url","");
        policy.approveJdbc(url);
        String driver=Json.text(c.connectionConfig(),"driverClass","");
        if(!driver.isBlank()) {
            if(!driver.matches("[A-Za-z_$][A-Za-z0-9_$.]{0,200}"))throw HubException.invalid();
            try {
                Class.forName(driver);
            }
            catch(ClassNotFoundException e) {
                throw new HubException("DRIVER_MISSING",412);
            }
        }
        var props=new Properties();
        props.setProperty("user",Json.text(c.secrets(),"username",""));
        props.setProperty("password",Json.text(c.secrets(),"password",""));
        Connection conn;
        try {
            conn=DriverManager.getConnection(url,props);
        }
        catch(SQLException e) {
            if(e.getMessage()!=null&&e.getMessage().contains("No suitable driver"))throw new HubException("DRIVER_MISSING",412);
            throw e;
        }
        try {
            conn.setNetworkTimeout(executor,c.timeoutMillis());
        }
        catch(SQLException|AbstractMethodError|UnsupportedOperationException ignored) {
            /* Driver support is explicitly reported as a deployment validation requirement. */
        }
        return conn;
    }
    @Override public Object discover(Context c) {
        try(Connection conn=open(c)) {
            var meta=conn.getMetaData();
            var result=new ArrayList<Object>();
            String schema=Json.text(c.config(),"schema","");
            try(ResultSet tables=meta.getTables(conn.getCatalog(),schema.isBlank()?null:schema,"%",new String[] {
                "TABLE","VIEW"
            }
            )) {
                while(tables.next()&&result.size()<200) {
                    String table=tables.getString("TABLE_NAME"),s=tables.getString("TABLE_SCHEM");
                    var cols=new ArrayList<Object>();
                    var keys=new ArrayList<String>();
                    try(ResultSet pk=meta.getPrimaryKeys(conn.getCatalog(),s,table)) {
                        while(pk.next())keys.add(pk.getString("COLUMN_NAME"));
                    }
                    try(ResultSet columns=meta.getColumns(conn.getCatalog(),s,table,"%")) {
                        while(columns.next()&&cols.size()<1000) {
                            var column=new LinkedHashMap<String,Object>();
                            column.put("name",columns.getString("COLUMN_NAME"));
                            column.put("type",columns.getString("TYPE_NAME"));
                            column.put("jdbcType",columns.getInt("DATA_TYPE"));
                            column.put("nullable",columns.getInt("NULLABLE")!=DatabaseMetaData.columnNoNulls);
                            column.put("size",columns.getInt("COLUMN_SIZE"));
                            try {
                                column.put("autoIncrement","YES".equals(columns.getString("IS_AUTOINCREMENT")));
                            }
                            catch(SQLException ignored) {
                                column.put("autoIncrement",false);
                            }
                            cols.add(column);
                        }
                    }
                    var item=new LinkedHashMap<String,Object>();
                    item.put("name",table);
                    item.put("schema",s);
                    item.put("columns",cols);
                    item.put("primaryKeys",keys);
                    result.add(item);
                }
            }
            return Map.of("product",meta.getDatabaseProductName(),"version",meta.getDatabaseProductVersion(),"tables",result,"limit",200);
        }
        catch(SQLException e) {
            throw sqlError(e);
        }
    }
    @Override public Object execute(Context c) {
        try(Connection conn=open(c)) {
            return switch(c.operation()) {
                case "READ","CREATE","UPDATE","DELETE"->crud(conn,c);
                case "QUERY","EXECUTE"->query(conn,c);
                case "PROCEDURE","FUNCTION"->routine(conn,c);
                default->throw new HubException("OPERATION_UNSUPPORTED",400);
            }
            ;
        }
        catch(SQLException e) {
            throw sqlError(e);
        }
    }
    private Object query(Connection conn,Context c)throws SQLException {
        var p=NamedSql.parse(Json.text(c.config(),"sql",""));
        try(PreparedStatement stmt=conn.prepareStatement(p.sql())) {
            configure(stmt,c);
            int i=1;
            for(String name:p.names()) {
                if(!c.parameters().containsKey(name))throw new HubException("MISSING_BINDING",400);
                bind(stmt,i++,c.parameters().get(name));
            }
            if(c.operation().equals("QUERY")) {
                try(ResultSet rs=stmt.executeQuery()) {
                    return Map.of("data",rows(rs,c.pageSize()),"bounded",true);
                }
            }
            return Map.of("data",Map.of("affected",stmt.executeUpdate()));
        }
    }
    private Object crud(Connection conn,Context c)throws SQLException {
        var cfg=c.config();
        String table=NamedSql.identifier(Json.text(cfg,"table",""));
        String schema=Json.text(cfg,"schema","");
        if(!schema.isBlank())NamedSql.identifier(schema);
        String q=conn.getMetaData().getIdentifierQuoteString().trim();
        if(q.isEmpty())q="";
        String relation=(schema.isBlank()?"":quoted(schema,q)+".")+quoted(table,q);
        Set<String> allowed=columns(conn,schema,table);
        if(allowed.isEmpty())throw new HubException("TABLE_NOT_FOUND",404);
        var bindings=Json.map(cfg,"bindings");
        List<String> selected=new ArrayList<>();
        for(Object o:Json.list(cfg,"columns")) {
            String name=o.toString();
            checkColumn(name,allowed);
            selected.add(name);
        }
        if(selected.isEmpty())selected.addAll(allowed);
        String op=c.operation();
        List<Object> values=new ArrayList<>();
        String sql;
        if(op.equals("READ")) {
            List<String> filters=new ArrayList<>();
            for(Object o:Json.list(c.page(),"filters")) {
                var f=Json.object(o);
                String col=Json.text(f,"field","");
                checkColumn(col,allowed);
                String comparison=switch(Json.text(f,"op","eq")) {
                    case "eq"->"=";
                    case "ne"->"<>";
                    case "gt"->">";
                    case "gte"->">=";
                    case "lt"->"<";
                    case "lte"->"<=";
                    case "contains"->"LIKE";
                    default->throw HubException.invalid();
                }
                ;
                Object value=f.get("value");
                if(value==null) {
                    if(!comparison.equals("=")&&!comparison.equals("<>"))throw HubException.invalid();
                    filters.add(quoted(col,q)+(comparison.equals("=")?" IS NULL":" IS NOT NULL"));
                }
                else {
                    filters.add(quoted(col,q)+" "+comparison+" ?");
                    values.add(comparison.equals("LIKE")?"%"+value+"%":value);
                }
            }
            for(var e:Json.map(cfg,"where").entrySet()) {
                checkColumn(e.getKey(),allowed);
                Object v=resolveBinding(e.getValue(),c.parameters());
                filters.add(quoted(e.getKey(),q)+(v==null?" IS NULL":" = ?"));
                if(v!=null)values.add(v);
            }
            String quote=q;
            sql="SELECT "+String.join(",",selected.stream().map(n->quoted(n,quote)).toList())+" FROM "+relation+(filters.isEmpty()?"":" WHERE "+String.join(" AND ",filters));
            var orders=new ArrayList<String>();
            for(Object o:Json.list(c.page(),"orders")) {
                var order=Json.object(o);
                String col=Json.text(order,"field","");
                checkColumn(col,allowed);
                String dir=Json.text(order,"direction","asc").toUpperCase(Locale.ROOT);
                if(!Set.of("ASC","DESC").contains(dir))throw HubException.invalid();
                orders.add(quoted(col,q)+" "+dir);
            }
            if(orders.isEmpty()) {
                String key=Json.text(cfg,"keyColumn",selected.getFirst());
                checkColumn(key,allowed);
                orders.add(quoted(key,q)+" ASC");
            }
            sql+=" ORDER BY "+String.join(",",orders);
            try(PreparedStatement stmt=conn.prepareStatement(sql)) {
                configure(stmt,c);
                int skip=c.pageIndex()*c.pageSize();
                if(skip>100_000)throw new HubException("PAGE_TOO_DEEP",400);
                stmt.setMaxRows(skip+c.pageSize()+1);
                int i=1;
                for(Object value:values)bind(stmt,i++,value);
                try(ResultSet rs=stmt.executeQuery()) {
                    for(int n=0; n<skip; n++)if(!rs.next())return Map.of("data",List.of(),"hasMore",false);
                    var data=rows(rs,c.pageSize()+1);
                    boolean more=data.size()>c.pageSize();
                    if(more)data.removeLast();
                    return Map.of("data",data,"hasMore",more,"page",c.pageIndex(),"size",c.pageSize());
                }
            }
        }
        String key=Json.text(cfg,"keyColumn","");
        List<String> columnNames=new ArrayList<>(),assignments=new ArrayList<>();
        for(var e:bindings.entrySet()) {
            checkColumn(e.getKey(),allowed);
            String parameter=e.getValue().toString();
            if(!c.parameters().containsKey(parameter)) {
                if(op.equals("CREATE"))continue;
                else if(op.equals("UPDATE"))continue;
            }
            if(op.equals("CREATE")||op.equals("UPDATE")) {
                columnNames.add(e.getKey());
                assignments.add(quoted(e.getKey(),q)+" = ?");
                values.add(resolveBinding(e.getValue(),c.parameters()));
            }
        }
        if(op.equals("CREATE")) {
            if(columnNames.isEmpty())throw HubException.invalid();
            String quote=q;
            sql="INSERT INTO "+relation+" ("+String.join(",",columnNames.stream().map(n->quoted(n,quote)).toList())+") VALUES ("+String.join(",",Collections.nCopies(values.size(),"?"))+")";
        }
        else {
            checkColumn(key,allowed);
            var primaryKeys=new ArrayList<String>();
            try(ResultSet keys=conn.getMetaData().getPrimaryKeys(conn.getCatalog(),schema.isBlank()?null:schema,table)) {
                while(keys.next())primaryKeys.add(keys.getString("COLUMN_NAME"));
            }
            if(primaryKeys.size()!=1||!primaryKeys.getFirst().equals(key))throw new HubException("SINGLE_PRIMARY_KEY_REQUIRED",400);
            if(op.equals("UPDATE")&&bindings.containsKey(key))throw HubException.invalid();
            Object id=c.parameters().get(Json.text(cfg,"keyParameter","id"));
            if(id==null)throw new HubException("REQUIRED_PARAMETER",400);
            String version=Json.text(cfg,"versionColumn","");
            String condition=quoted(key,q)+" = ?";
            if(op.equals("UPDATE")) {
                if(assignments.isEmpty())throw HubException.invalid();
                if(!version.isBlank()) {
                    checkColumn(version,allowed);
                    if(columnNames.contains(version))throw HubException.invalid();
                    assignments.add(quoted(version,q)+" = "+quoted(version,q)+" + 1");
                }
                sql="UPDATE "+relation+" SET "+String.join(",",assignments)+" WHERE "+condition;
            }
            else if(op.equals("DELETE")) {
                values.clear();
                sql="DELETE FROM "+relation+" WHERE "+condition;
            }
            else throw HubException.invalid();
            values.add(id);
            if(!version.isBlank()) {
                checkColumn(version,allowed);
                Object expected=c.parameters().get("expectedVersion");
                if(expected==null)throw new HubException("REQUIRED_PARAMETER",400);
                sql+=" AND "+quoted(version,q)+" = ?";
                values.add(expected);
            }
        }
        try(PreparedStatement stmt=conn.prepareStatement(sql)) {
            configure(stmt,c);
            int i=1;
            for(Object v:values)bind(stmt,i++,v);
            int affected=stmt.executeUpdate();
            if(affected==0&&!op.equals("CREATE"))throw new HubException("VERSION_CONFLICT",409);
            return Map.of("data",Map.of("affected",affected));
        }
    }
    private Object routine(Connection conn,Context c)throws SQLException {
        String name=Json.text(c.config(),"routine","");
        for(String piece:name.split("\\.",-1))NamedSql.identifier(piece);
        var defs=Json.list(c.config(),"routineParameters");
        boolean function=c.operation().equals("FUNCTION");
        String call="{"+(function?"? = ":"")+"call "+name+"("+String.join(",",Collections.nCopies(defs.size(),"?"))+")}";
        try(CallableStatement stmt=conn.prepareCall(call)) {
            configure(stmt,c);
            if(function)stmt.registerOutParameter(1,jdbcType(Json.text(c.config(),"returnType","VARCHAR")));
            int i=function?2:1;
            for(Object o:defs) {
                var d=Json.object(o);
                String mode=Json.text(d,"mode","IN");
                if(!Set.of("IN","OUT","INOUT").contains(mode))throw HubException.invalid();
                int type=jdbcType(Json.text(d,"type","VARCHAR"));
                String param=Json.text(d,"name","");
                if(!mode.equals("OUT")) {
                    if(!c.parameters().containsKey(param))throw new HubException("MISSING_BINDING",400);
                    if(c.parameters().get(param)==null)stmt.setNull(i,type);
                    else stmt.setObject(i,c.parameters().get(param),type);
                }
                if(!mode.equals("IN"))stmt.registerOutParameter(i,type);
                i++;
            }
            boolean hasRows=stmt.execute();
            var sets=new ArrayList<Object>();
            int guard=0;
            while(guard++<20) {
                if(hasRows) {
                    try(ResultSet rs=stmt.getResultSet()) {
                        sets.add(rows(rs,c.pageSize()));
                    }
                }
                else if(stmt.getUpdateCount()==-1)break;
                hasRows=stmt.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
            }
            var output=new LinkedHashMap<String,Object>();
            if(function)output.put("returnValue",normalize(stmt.getObject(1)));
            i=function?2:1;
            for(Object o:defs) {
                var d=Json.object(o);
                if(!Json.text(d,"mode","IN").equals("IN"))output.put(Json.text(d,"name",""),normalize(stmt.getObject(i)));
                i++;
            }
            return Map.of("data",output,"resultSets",sets);
        }
    }
    private static Set<String> columns(Connection conn,String schema,String table)throws SQLException {
        Set<String> result=new LinkedHashSet<>();
        try(ResultSet rs=conn.getMetaData().getColumns(conn.getCatalog(),schema.isBlank()?null:schema,table,"%")) {
            while(rs.next()) {
                if(!table.equals(rs.getString("TABLE_NAME")))continue;
                if(!schema.isBlank()&&!schema.equals(rs.getString("TABLE_SCHEM")))continue;
                result.add(rs.getString("COLUMN_NAME"));
            }
        }
        return result;
    }
    private static void checkColumn(String c,Set<String> allowed) {
        NamedSql.identifier(c);
        if(!allowed.contains(c))throw new HubException("COLUMN_NOT_FOUND",400);
    }
    private static String quoted(String s,String quote) {
        return quote+NamedSql.identifier(s)+quote;
    }
    private static Object resolveBinding(Object name,Map<String,Object> parameters) {
        if(!parameters.containsKey(name.toString()))throw new HubException("MISSING_BINDING",400);
        return parameters.get(name.toString());
    }
    private static void configure(Statement stmt,Context c)throws SQLException {
        stmt.setQueryTimeout(Math.max(1,c.timeoutMillis()/1000));
        stmt.setMaxRows(c.pageSize()+1);
    }
    private static void bind(PreparedStatement stmt,int index,Object v)throws SQLException {
        if(v==null)stmt.setNull(index,Types.NULL);
        else if(v instanceof Map||v instanceof List)stmt.setString(index,Json.stringify(v));
        else stmt.setObject(index,v);
    }
    private static List<Object> rows(ResultSet rs,int max)throws SQLException {
        var result=new ArrayList<Object>();
        long bytes=0;
        var metadata=rs.getMetaData();
        if(metadata.getColumnCount()>1000)throw new HubException("UPSTREAM_TOO_LARGE",502);
        while(result.size()<max&&rs.next()) {
            var row=new LinkedHashMap<String,Object>();
            for(int i=1; i<=metadata.getColumnCount(); i++) {
                String name=metadata.getColumnLabel(i);
                if(row.containsKey(name))throw new HubException("DUPLICATE_COLUMN",502);
                Object value=normalize(rs.getObject(i));
                bytes+=name.length()*3L+Json.stringify(value).getBytes(java.nio.charset.StandardCharsets.UTF_8).length+8;
                if(bytes>2_097_152)throw new HubException("UPSTREAM_TOO_LARGE",502);
                row.put(name,value);
            }
            result.add(row);
        }
        return result;
    }
    private static Object normalize(Object v)throws SQLException {
        if(v instanceof String text) {
            if(text.length()>2_097_152)throw new HubException("UPSTREAM_TOO_LARGE",502);
            return text;
        }
        if(v==null||v instanceof Boolean||v instanceof Number)return v;
        if(v instanceof byte[] b) {
            if(b.length>2_097_152)throw new HubException("UPSTREAM_TOO_LARGE",502);
            return Base64.getEncoder().encodeToString(b);
        }
        if(v instanceof Clob clob) {
            try {
                if(clob.length()>2_097_152)throw new HubException("UPSTREAM_TOO_LARGE",502);
                return clob.getSubString(1,(int)clob.length());
            }
            finally {
                clob.free();
            }
        }
        if(v instanceof Blob blob) {
            try {
                if(blob.length()>2_097_152)throw new HubException("UPSTREAM_TOO_LARGE",502);
                return Base64.getEncoder().encodeToString(blob.getBytes(1,(int)blob.length()));
            }
            finally {
                blob.free();
            }
        }
        return v.toString();
    }
    public static int jdbcType(String name) {
        return switch(name) {
            case "VARCHAR"->Types.VARCHAR;
            case "INTEGER"->Types.INTEGER;
            case "BIGINT"->Types.BIGINT;
            case "DECIMAL"->Types.DECIMAL;
            case "BOOLEAN"->Types.BOOLEAN;
            case "DATE"->Types.DATE;
            case "TIMESTAMP"->Types.TIMESTAMP;
            case "DOUBLE"->Types.DOUBLE;
            case "BINARY"->Types.VARBINARY;
            default->throw HubException.invalid();
        }
        ;
    }
    private static HubException sqlError(SQLException e) {
        String state=e.getSQLState();
        if(state!=null&&state.startsWith("23"))return new HubException("CONFLICT",409);
        if(e instanceof SQLTimeoutException)return new HubException("UPSTREAM_TIMEOUT",504,true);
        if(e instanceof SQLTransientException||state!=null&&state.startsWith("08"))return new HubException("UPSTREAM_UNAVAILABLE",502,true);
        return new HubException("DATABASE_ERROR",502);
    }
}
