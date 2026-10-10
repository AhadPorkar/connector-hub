# GraphBPMS Provider concepts and clean-room scope

[Deutsch](../de/provider-parity.md) · [فارسی](../fa/provider-parity.md).

This project is an independent implementation of an integration-provider pattern. It contains no copied GraphBPMS source, proprietary database schema, customer configuration or compatibility claim. Similar concepts are mapped deliberately; names and HTTP contracts are not drop-in replacements.

| GraphBPMS-style concept | Connector Hub implementation | Boundary |
|---|---|---|
| Provider definition | Versioned provider draft | Different schema/API |
| Database/web-service configuration | Tenant-owned connection + secret bag | Explicit installed drivers and allowlists |
| Normal parameter | INPUT | Caller can supply only declared names |
| Constant parameter | CONSTANT | Resolved server-side |
| Systemic parameter | SYSTEM | tenant, principal, correlationId, now only |
| System configuration | CONFIG | Tenant-admin key/value settings |
| Secret/configuration credential | SECRET | Connection secret references, not arbitrary environment reads |
| Query provider | JDBC QUERY/EXECUTE | Bound named values; trusted authored SQL |
| Stored procedure/function | PROCEDURE/FUNCTION | Ordered IN/OUT/INOUT, driver-dependent acceptance |
| REST/WSDL service | HTTP families + WSDL inspection | No universal generated SOAP binding |
| FieldsRoot | JSON Pointer fieldsRoot | Evaluated before output mapping |
| Output fields | Typed outputMappings | Relative JSON Pointers |
| Paging/filter/order | Explicit page envelope | Connector-specific subset, not universal translation |
| Provider consumption | Published authenticated POST URL | Versioned snapshot + scoped key |
| Async execution | Durable single-worker jobs | No distributed scheduler/service bus |

## Intentional differences

The caller sends a JSON object under `input`, not GraphBPMS's original list of Name/FieldType/Value records. Only INPUT names are accepted. System/config/constant/secret values are resolved from trusted server context. No web configuration file can be read by an arbitrary provider. `FieldsRoot` is an RFC-style JSON Pointer, not an undocumented expression language or general JavaScript evaluation.

Input templates preserve types when a whole `${name}` token is used. Embedded substitutions create strings; SOAP values are escaped. SQL values use `:name` bindings. Output mappings apply after root extraction and can convert fields to declared types. Binary and plain-text results remain explicit output types rather than being misrepresented as rows.

Generated JDBC CRUD is deliberately conservative: one verified primary key, checked identifiers and optional version comparison. Arbitrary OptionalItems, AdvancedEditor behaviors, SystemEntity rules, dynamic forms/reports and proprietary provider categories are not emulated. There is no `absoluteMethodHelper.runProvider` function, although an application can call the published HTTP endpoint using its own server-side client.

## Not implemented

Arbitrary C#/Java/script execution from the interface, WCF-specific binding/security stacks, GraphBPMS service-bus endpoints, proprietary form submission, process instance variables, report designers, hierarchical entity permissions and drop-in migration of existing providers are outside this release. This avoids shipping a remote-code-execution feature disguised as flexibility. Trusted Java extensions can be installed through the startup SPI after review.

## Migration approach

Inventory an existing provider's destination, authorization, parameter sources, null semantics, timeout behavior, output shape and consumers. Recreate one equivalent definition with synthetic data. Verify each operation against an isolated destination, including errors and duplicate requests. Compare output contracts and paging semantics explicitly. Publish a new version and switch one consumer at a time. Never bulk-import an old catalog and assume behavior is identical.

The useful parity is the authoring workflow—connect, parameterize, execute, map and publish—not a claim of feature-for-feature replacement of a mature BPMS module.
