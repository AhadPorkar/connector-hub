package io.ahad.connectorhub.server;
import io.ahad.connectorhub.core.*;
import java.nio.file.*;
import java.util.*;
public record Config(String bind,int port,Path data,boolean demo,int demoPort,Set<String> origins, boolean allowPrivate,List<String> jdbcPrefixes,String masterKey,String bootstrapToken,int maxConcurrent) {
    public static Config load(String[] args) {
        Map<String,String> values=new HashMap<>();
        boolean demo=false;
        for(int i=0; i<args.length; i++) {
            if(args[i].equals("--demo"))demo=true;
            else if(Set.of("--port","--bind","--data").contains(args[i])&&i+1<args.length)values.put(args[i].substring(2),args[++i]);
            else throw new IllegalArgumentException("Unknown argument");
        }
        int demoPort=Integer.parseInt(env("HUB_DEMO_PORT","9091"));
        int port=Integer.parseInt(values.getOrDefault("port",env("HUB_PORT","8080")));
        if(port<0||port>65535||demoPort<0||demoPort>65535)throw new IllegalArgumentException("Port must be 0..65535");
        int concurrency=Integer.parseInt(env("HUB_MAX_CONCURRENT","32"));
        if(concurrency<1||concurrency>256)throw new IllegalArgumentException("HUB_MAX_CONCURRENT must be 1..256");
        Set<String> origins=new HashSet<>();
        for(String s:env("HUB_ALLOWED_ORIGINS","").split(","))if(!s.isBlank())origins.add(s.trim());
        if(demo)origins.add("http://127.0.0.1:"+demoPort);
        return new Config(values.getOrDefault("bind",env("HUB_BIND","127.0.0.1")),port,Path.of(values.getOrDefault("data",env("HUB_DATA_DIR","var"))),demo,demoPort,origins,demo||Boolean.parseBoolean(env("HUB_ALLOW_PRIVATE","false")),Arrays.stream(env("HUB_JDBC_PREFIXES","").split(",")).filter(s->!s.isBlank()).toList(),env("HUB_MASTER_KEY",""),env("HUB_ADMIN_TOKEN",""),concurrency);
    }
    private static String env(String name,String fallback) {
        String value=System.getenv(name);
        return value==null?fallback:value;
    }
    public byte[] loadKey()throws Exception {
        if(!masterKey.isBlank()) {
            byte[] b=Base64.getDecoder().decode(masterKey);
            if(b.length!=32)throw new IllegalArgumentException("HUB_MASTER_KEY must be Base64 of 32 random bytes");
            return b;
        }
        Files.createDirectories(data);
        Catalog.privateMode(data,true);
        Path path=data.resolve("master.key");
        if(!Files.exists(path)) {
            byte[] b=Crypto.randomBytes(32);
            try {
                Files.write(path,b,StandardOpenOption.CREATE_NEW);
            }
            catch(FileAlreadyExistsException ignored) {
            }
            Catalog.privateMode(path,false);
        }
        System.err.println("NOTICE: local master.key is for development. Production requires an externally managed HUB_MASTER_KEY and protected storage.");
        byte[] b=Files.readAllBytes(path);
        if(b.length!=32)throw new IllegalArgumentException("Invalid local master key");
        return b;
    }
}
