package io.ahad.connectorhub.example;

import io.ahad.connectorhub.core.Connector;
import java.util.Map;
import java.util.Set;

/** Trusted startup extension. Does not make outbound requests or execute arbitrary code. */
public final class EchoConnector implements Connector {
    @Override public String type() { return "ECHO"; }
    @Override public Set<String> operations() { return Set.of("READ"); }
    @Override public boolean retrySafe(Map<String, Object> provider) { return true; }
    @Override public Object execute(Context context) {
        return Map.of("data", Map.of("input", context.parameters(), "tenant", context.identity().tenant()));
    }
}
