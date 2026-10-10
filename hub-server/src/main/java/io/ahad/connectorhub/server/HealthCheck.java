package io.ahad.connectorhub.server;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
/** Container liveness probe. Does not claim downstream readiness. */
public final class HealthCheck {
    public static void main(String[] args) throws Exception {
        String port = System.getenv().getOrDefault("HUB_PORT", "8080");
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/healthz")) .timeout(Duration.ofSeconds(3)).GET().build();
            if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() != 200) System.exit(1);
        }
    }
}
