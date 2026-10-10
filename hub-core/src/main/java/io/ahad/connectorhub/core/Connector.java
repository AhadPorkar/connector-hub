package io.ahad.connectorhub.core;
import java.util.*;
/** Deliberately narrow SPI. Install trusted plugins at process startup, never from the HTTP API. */
public interface Connector {
    String type();
    Set<String> operations();
    Object execute(Context context);
    default Object discover(Context context) {
        throw new HubException("DISCOVERY_UNSUPPORTED",400);
    }
    default boolean retrySafe(Map<String,Object> provider) {
        return false;
    }
    default boolean available() {
        return true;
    }
    record Context(Map<String,Object> connection,Map<String,Object> provider,Map<String,Object> parameters, Map<String,Object> page,Identity identity,String correlationId) {
        public Map<String,Object> connectionConfig() {
            return Json.map(connection,"config");
        }
        public Map<String,Object> secrets() {
            return Json.map(connection,"secrets");
        }
        public Map<String,Object> config() {
            return Json.map(provider,"config");
        }
        public String operation() {
            return Json.text(provider,"operation","READ");
        }
        public int pageSize() {
            int n=Json.integer(page,"size",50);
            if(n<1||n>500)throw HubException.invalid();
            return n;
        }
        public int pageIndex() {
            int n=Json.integer(page,"index",0);
            if(n<0||n>10_000)throw HubException.invalid();
            return n;
        }
        public int timeoutMillis() {
            int n=Json.integer(provider,"timeoutMs",10_000);
            if(n<100||n>60_000)throw HubException.invalid();
            return n;
        }
    }
}
