package io.ahad.connectorhub.server;
import io.ahad.connectorhub.core.Catalog;
import java.util.concurrent.CountDownLatch;
/** Application entry point. Every acquired resource is closed even after partial startup. */
public final class Main {
    private Main() {
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("sun.net.httpserver.maxReqTime", "15");
        System.setProperty("sun.net.httpserver.maxRspTime", "65");
        Config config = Config.load(args);
        Catalog catalog = null;
        HubService hub = null;
        DemoUpstream upstream = null;
        ExecutionEngine engine = null;
        HubHttpServer http = null;
        boolean started = false;
        try {
            catalog = new Catalog(config.data(), config.loadKey());
            hub = new HubService(config, catalog);
            String token = hub.keys.bootstrap(config.bootstrapToken());
            if (config.demo()) {
                upstream = new DemoUpstream(config.demoPort());
                DemoSeed.seed(hub, upstream.port());
            }
            engine = new ExecutionEngine(hub);
            http = new HubHttpServer(hub, engine);
            http.start();
            final AutoCloseable[] resources = {
                http, engine, upstream, hub, catalog
            }
            ;
            var stopped = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                closeAll(resources);
                stopped.countDown();
            }
            , "hub-shutdown"));
            started = true;
            System.out.println("Connector Hub " + HubService.VERSION + " -> http://" + config.bind() + ":" + http.port());
            System.out.println("Runtime: Java " + Runtime.version() + "; single-node catalog; " + (config.demo() ? "SIMULATED DEMO UPSTREAM ENABLED" : "demo disabled"));
            if (token != null) {
                System.out.println("BOOTSTRAP_TOKEN=" + token);
                System.out.println("Copy this token now. Only its hash is persisted. Keep it outside source control.");
            }
            stopped.await();
        }
        finally {
            if (!started) closeAll(http, engine, upstream, hub, catalog);
        }
    }
    private static void closeAll(AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            if (resource == null) continue;
            try {
                resource.close();
            }
            catch (Exception exception) {
                System.err.println("Resource cleanup failed: " + exception.getClass().getSimpleName());
            }
        }
    }
}
