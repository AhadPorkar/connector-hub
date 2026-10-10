# User guide

[Deutsch](../de/user-guide.md) · [فارسی](../fa/user-guide.md) · [README](../../README.md).

## Start and sign in

Install JDK 21 or newer. From the repository root run `bash scripts/run-demo.sh` on Linux/macOS or `./scripts/run-demo.ps1` in PowerShell. Open `http://127.0.0.1:8080`. Copy the `BOOTSTRAP_TOKEN` printed on first startup into the sign-in form. It is shown once; only its hash is retained. Do not paste it into issues or commit it. Language selection is available before and after sign-in. Persian switches the document to RTL; identifiers and code remain LTR. The token is held in browser memory, not local storage. Refreshing requires signing in again.

The demo uses five connections and eleven published providers, with explicitly simulated upstream APIs. The three sample organizations are fictional. No customer or employer data is embedded. Stop with Ctrl+C. The encrypted catalog lives in `var/`; restarting preserves configuration and completed execution records.

## Connection → provider → published service

A connection owns its connector type, destination settings and secrets. A provider owns an operation, typed parameters, request/query configuration, output selection and execution policy. Publication creates an immutable snapshot of a provider revision and a callable URL. Updating a draft does not update existing publications. Connection credentials remain referenced at runtime so they can be rotated independently; a publication is therefore not a frozen copy of the entire environment.

An administrator creates connections. A designer can maintain providers and publish them. An invoker receives only the explicitly granted provider scopes; an auditor can inspect permitted operational information. The bootstrap administrator can create administrators for additional tenants. This is not a public self-service signup or billing system.

## Create a connection

Choose Connections → New connection. Select LOCAL for a fully offline collection, REST/SOAP/GRAPHQL/ODATA for HTTP services, JDBC for relational databases, or REDIS/MONGODB for the limited adapters documented separately. Enter a name, configuration JSON and credentials in the designated secret fields. Empty secret fields preserve the previous secret during an edit; explicitly selected clear operations remove it. Returned connection objects contain configured-secret names, never their values.

External destinations are denied unless the operator adds their exact origins to `HUB_ALLOWED_ORIGINS`. Private addresses additionally require `HUB_ALLOW_PRIVATE=true`. JDBC connections require an installed vendor driver and `HUB_JDBC_PREFIXES`. The web interface cannot relax these operator boundaries. Do not put a password in a URL, query, provider constant or arbitrary configuration field. Such values are not automatically classified as secrets.

Inspect performs JDBC metadata discovery, LOCAL collection discovery, or protocol descriptor inspection. For SOAP, paste a self-contained WSDL 1.1 document. HTTP inspection is not a successful connection-health probe and does not automatically fetch remote specifications.

## Generate CRUD providers

For LOCAL, Inspect → Generate CRUD asks for a collection name and creates READ, CREATE, UPDATE and DELETE drafts. For JDBC, enter the exact table name and optional schema. Generation requires one metadata-verified primary key. Composite keys and tables without a primary key need a deliberately designed query/provider rather than an unsafe generated update. Generated providers must be reviewed, tested and explicitly published. The generator does not create a business authorization policy for the rows in a customer database.

## Configure parameters

Each parameter has a unique identifier, type, source, required flag and optional default/reference. Supported types are STRING, INTEGER, DECIMAL, BOOLEAN, JSON, DATE and DATETIME. DATE uses ISO `YYYY-MM-DD`; DATETIME requires an offset, for example `2026-10-10T12:00:00Z`. INTEGER must fit a Java signed 64-bit integer. The browser uses JavaScript numbers, so submit precision-sensitive values as strings with a declared numeric type.

| Source | Resolution | Caller can override? |
|---|---|---|
| INPUT | Invocation input or declared default | Yes |
| CONSTANT | Provider definition value | No |
| SYSTEM | tenant, principal, correlationId or now | No |
| CONFIG | Tenant configuration key | No |
| SECRET | Connection secret key | No |

Undeclared caller inputs are rejected. `${customerId}` substitutes a whole typed value in a JSON template; interpolation inside a longer string produces a string. SOAP values are XML-escaped. SQL uses named `:customerId` bindings instead of `${...}` text substitution. Never concatenate caller input into SQL identifiers or SQL text. The editor can discover template bindings, but a human must assign correct types and required flags.

## Request and output configuration

REST supports query parameters, headers, path templates, JSON, text, form and multipart bodies. Binary responses are bounded and represented as Base64 plus media metadata. SOAP uses an explicit envelope, SOAP version and action. GraphQL uses a document plus variables; mutations must be treated as writes. OData uses ordinary HTTP operations with bounded `$top` and `$skip` support, not a complete OData client.

The connector result is an envelope, usually containing `data`. `fieldsRoot` is a JSON Pointer such as `/data/items`, evaluated before output mappings. Mapping pointers are relative to each selected item. For example, root `/data/items` and mapping `{"name":"customerName","pointer":"/name","type":"STRING"}` transform every selected row. XML uses local element names, repeated-element arrays, `@attribute` keys and `#text`; namespaces with identical local names can be ambiguous. Text and binary outputs are not automatically converted into structured records.

## Test, publish and consume

Use Test to execute a draft with synthetic inputs. Choose synchronous or queued execution. Keep the same idempotency key when retrying the same logical request; generate a new key for a genuinely new action. Changing the payload while reusing a key produces a conflict. Publish the reviewed version, then inspect Published services and download the generated OpenAPI 3.1.1 document. All generated consumer endpoints are authenticated POST endpoints, even when the downstream operation is a read.

```bash
curl -X POST http://127.0.0.1:8080/api/services/customer-directory/v1 \
  -H "Authorization: Bearer $HUB_TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: customer-read-example-001' \
  --data '{"input":{},"page":{"index":0,"size":25}}'
```

Create a short-lived INVOKER key scoped to the required provider IDs. Do not embed the bootstrap token in a frontend or distribute it to consumers. Existing snapshots retain their provider IDs for scope checking. Unpublishing prevents new resolution of the public URL; it does not undo downstream effects.

## Failures and recovery

Only adapters that classify the operation as retry-safe can retry transient failures automatically. Writes are not retried simply because an HTTP method is PUT or a label says READ. After an uncertain external write, the durable state becomes UNKNOWN. Inspect the destination independently, then record CONFIRMED_SUCCESS or CONFIRMED_NO_EFFECT through the administrator recovery interface. Neither decision automatically repeats the write. Use a new logical execution only after reconciliation. See [operations](operations.md) for the recovery procedure and retention boundary.

The interface includes jobs, metadata-only audit history, API keys, settings, configuration export and these localized guides. OpenAPI import creates drafts from a limited JSON subset; it does not resolve arbitrary remote references or guarantee a complete imported contract.
