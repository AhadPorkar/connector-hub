# Build and deployment

[Deutsch](../de/deployment.md) · [فارسی](../fa/deployment.md).

## Verified offline path

The baseline requires a JDK, not just a JRE, for source compilation. Java 21 is the target. Python 3 is needed for HTTP/repository tests; Node is optional for JavaScript syntax checks. No Maven, npm registry, database, CDN or internet connection is required to build and run the core demo.

```bash
bash scripts/build.sh
bash scripts/run-demo.sh --port 8080 --data var
bash scripts/test.sh
```

PowerShell equivalents are `./scripts/build.ps1`, `./scripts/run-demo.ps1 --port 8080`, and `./scripts/test.ps1`. These scripts are supplied with a Windows CI job, but Windows execution was not available in the authoring environment. A standalone JAR starts with `java -jar build/connector-hub.jar --demo`; external drivers/plugins require the classpath launch script instead.

If port 8080 is occupied, use `--port 18080`. The demo upstream uses 9091 by default; change `HUB_DEMO_PORT` if needed. Choose a separate data directory for each independent instance. Never share one directory across replicas. Unknown command-line options are rejected. CLI supports `--demo`, `--port`, `--bind` and `--data`.

## Environment settings

| Variable | Default | Purpose |
|---|---|---|
| HUB_BIND | 127.0.0.1 | Listener address |
| HUB_PORT | 8080 | Listener port |
| HUB_DATA_DIR | var | Encrypted catalog directory |
| HUB_DEMO_PORT | 9091 | Local simulated upstream port |
| HUB_ALLOWED_ORIGINS | empty | Comma-separated exact approved origins |
| HUB_ALLOW_PRIVATE | false | Additional explicit private-network permission |
| HUB_JDBC_PREFIXES | empty | Comma-separated approved JDBC URL prefixes |
| HUB_MASTER_KEY | empty | Base64 of 32 random bytes; otherwise local development key |
| HUB_ADMIN_TOKEN | empty | Initial high-entropy key; generated when omitted |
| HUB_MAX_CONCURRENT | 32 | Execution concurrency, range 1–256 |
| JAVA_OPTS | empty | Optional JVM flags for the POSIX launcher |

CLI values override bind/port/data environment values. Demo mode adds its local upstream origin and enables private destinations, but does not remove the origin allowlist. A bootstrap token must be sufficiently long; prefer a cryptographically random token rather than a human password. It is used only for initial catalog creation.

Generate secrets outside source control, for example with Python `secrets.token_bytes(32)` and Base64 for the master key, and `secrets.token_urlsafe(32)` for the token. Supply them through your deployment secret manager. Do not publish your `.env`, `var/`, logs containing the first-start token, or driver license keys.

## Optional connected Maven build

`mvn verify` builds the three modules and invokes their Java test runners through exec-maven-plugin. Surefire is intentionally skipped because these offline tests use a small explicit runner, not JUnit annotations. This Maven path and artifact resolution were not executed locally. The canonical `scripts/test.sh` additionally runs the real HTTP suite and repository checks. Server shading includes core/connectors and resources; optional vendor drivers are deliberately separate.

## Containers and TLS

`docker compose -f compose.demo.yml up --build` provides a loopback-only demo mapping. `compose.yml` requires external master/token values, disables demo mode and mounts driver/plugin directories read-only. The image runs as UID 10001 with a writable data volume and a read-only root filesystem. The health probe checks the hub process, not downstream readiness. Docker image building and runtime behavior were not verified in this environment and must pass your deployment gate.

The JVM endpoint is plain HTTP by design. Use a reviewed HTTPS reverse proxy, for example the supplied Caddy configuration. Update hostnames, certificates, network routes and firewall rules for your environment. Never expose a bootstrap-authenticated plain HTTP listener publicly. Container tags are version-family tags, not immutable supply-chain attestations; pin reviewed image digests for a release.

## Driver installation

Place trusted vendor JARs in `drivers/` and trusted SPI extensions in `plugins/`, then restart using `scripts/run.sh` or `run.ps1`. The optional Maven driver profiles require network access and vendor license/advisory review. No external JAR is automatically downloaded by the running application.

## Capacity and release status

This is one JVM with a 64 MiB encrypted in-memory snapshot, one queued worker, no database pool and no HA control plane. It is intended for evaluation and narrow controlled pilots after local validation. Measure actual memory, throughput and recovery under your workload. No SLA, throughput benchmark, container security certification or zero-downtime upgrade promise is included.
