# Commercial readiness and release gates

[Deutsch](../de/commercial-readiness.md) · [فارسی](../fa/commercial-readiness.md).

## Release classification

Version 0.1.0 is a working engineering preview and commercial foundation. It is not advertised as universally compatible, production-certified, penetration-tested, highly available or ready for unrestricted sale/deployment. Source availability and a passing local test suite do not by themselves establish those properties.

## Delivered

The application includes a Java server, trilingual responsive control plane, versioned connections/providers, typed parameter sources, explicit CRUD/query/service adapters, scoped authentication, generated service URLs/OpenAPI, conservative retries, persistent idempotency, durable jobs, encrypted local state, audit metadata, reproducible tests, examples and deployment scaffolding. The exact verified subset is recorded separately. No fake CI badge, invented benchmark or unsupported availability number is used.

## Required gates before a customer-facing release

| Area | Required evidence or work | Current status |
|---|---|---|
| Vendor compatibility | Real driver/service acceptance for every supported product/version | Mostly outstanding |
| Identity | OIDC/SSO, administrative recovery and bootstrap-key rotation | Not implemented |
| State platform | Transactional metadata database, migrations and distributed claims when HA is needed | Single-node file catalog only |
| Connection lifecycle | Reviewed pooling, credential rotation and driver timeout behavior | Per-operation connections |
| Network security | Egress firewall/proxy, TLS integration, DNS-rebinding protection | Operator deployment gate |
| Quality | Native browser/CSP, Windows/container, load/soak and accessibility review | Partly unverified |
| Security | Independent penetration test, dependency review, SBOM and vulnerability response | Not completed |
| Operations | Backup/restore drill, retention policy, monitoring and support runbooks | Baseline docs/tests only |
| Compliance | Applicable contractual/privacy/legal review | No certification claimed |
| Product | Upgrade path, support commitments, onboarding and commercial packaging | Not completed |

A narrow pilot should define a small supported matrix—for example one PostgreSQL version plus one REST contract—rather than promise every database and protocol. Agree on data sensitivity, maximum load, failure handling and responsibility for reconciliation. Run destructive tests only on disposable infrastructure.

## Roadmap by dependency

First validate the exact customer connectors, close severe security findings and prove restore/reconciliation. Next replace the catalog for workloads that need HA, improve connection lifecycle and add standards-based identity. Then add full SOAP/CXF or Camel-backed protocols with their own acceptance suites. Product features such as billing, multi-region operation, visual transformations and marketplace plugins should follow verified core guarantees, not precede them.

## GitHub publication checklist

Read LICENSE and confirm the chosen MIT distribution model before publishing. Check that no secrets, customer data, runtime catalogs, driver JARs or proprietary code are present. Run the supplied scripts, inspect screenshots and reports, and enable the proposed CI workflows. Pin reviewed action/image digests for a hardened release. Define a private security-reporting channel and verify it works before inviting reports.

The repository is prepared for review under Ahad Porkar's ownership, but no GitHub repository was created and no remote push or release was performed. No interview, revenue, sale or production-success guarantee is implied.
