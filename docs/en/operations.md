# Operations and recovery runbook

[Deutsch](../de/operations.md) · [فارسی](../fa/operations.md).

## Start, stop and health

Run one instance per catalog directory. Use the classpath launcher when adding drivers. `/healthz` reports process liveness and version only; it does not prove any destination is healthy. The dashboard summarizes retained audit events, not a monitoring platform or an all-time SLA. Stop gracefully with Ctrl+C or SIGTERM. Partial startup now closes acquired resources if the listener cannot bind.

The queue has one worker. A graceful stop can wait for active work; an abrupt process death may leave uncertain external effects. Never restart repeatedly hoping UNKNOWN records will disappear. Preserve evidence and reconcile.

## Backup and restore

Stop the process and confirm the file lock is released. Copy the entire catalog directory consistently. Store the master key through a separate protected mechanism; when using a local development `master.key`, remember that copying it beside the ciphertext defeats separation against backup theft. Record application version and configuration policy. Test restoration into a new isolated directory with the matching key before relying on the backup.

Do not copy a changing catalog as a supported hot-backup procedure, mount one catalog into multiple active replicas, or treat configuration export as a backup. Export omits secrets, jobs, execution outcomes and business data. Encrypted authentication detects corruption but not rollback to an older valid backup. Restoring old state can lose recent deduplication records while downstream writes remain committed. Reconcile external effects before resuming consumers after restoration.

## UNKNOWN write procedure

1. Stop new invocations of the affected publication or revoke its consumer key when necessary. Do not delete the durable record.
2. Record the request ID, provider revision, caller, timestamp and idempotency metadata. Do not post secrets or customer payloads to public issues.
3. Inspect the destination through an independent authorized path. Search using a business identifier, not merely a hub HTTP error.
4. If the destination committed the intended action, record CONFIRMED_SUCCESS and an appropriate verified result. If no effect occurred, record CONFIRMED_NO_EFFECT. If uncertain, leave UNKNOWN.
5. A confirmed no-effect decision keeps a failed tombstone under the old key. A new attempt requires an explicit new logical invocation/key. Confirmed success can be replayed from the hub's committed result.

This is conservative recovery, not an automatic distributed transaction rollback. A local failure after adapter entry may be classified UNKNOWN even when investigation later proves no effect. Safety takes precedence over convenient repeated writes.

## Job lifecycle

QUEUED can be cancelled. RUNNING cannot be reliably cancelled through this API. SUCCEEDED and FAILED retain their recorded outcomes. Interrupted RUNNING jobs become UNKNOWN after restart. Automatic/manual job retry is limited to adapter-classified safe reads and uses a new execution identity. Revoked or expired initiating keys are rechecked before queued work runs.

## Retention and capacity

The snapshot limit is 64 MiB, retained audit history is 5,000 events across tenants, jobs are capped at 1,000 and idempotency entries at 5,000. LOCAL collections are capped at 10,000 records. These are protection limits, not recommended throughput targets. Admin maintenance prunes terminal history older than seven days; it retains UNKNOWN records. A pruned key is no longer protected from duplicate execution. Consumers must not reuse ancient business-operation keys after the agreed retention period.

Queue payloads and results can contain personal or confidential data inside encrypted storage. Define access and retention controls accordingly. Monitor disk space, process memory, queue depth and failure trends externally. There is no Prometheus/OpenTelemetry exporter or external immutable audit sink in this release.

## Credential and connection changes

Edit credentials only in secret fields. Empty patches preserve existing values; explicitly clear old values when removing them. Connection changes affect published providers that reference the connection. Test a staged connection before repointing a critical provider. Existing publications are immutable only with respect to provider snapshots, not credentials or destination availability.

The bootstrap key is non-expiring and has no normal revoke/rotate endpoint. A reviewed offline recovery/rotation procedure is a commercial gate. Losing it must not prompt destructive deletion of the production catalog. Likewise, master-key rotation is not implemented; changing the environment key without re-encryption makes the existing catalog unreadable.

## Incident checklist

For timeouts, determine read versus write before retrying. For DRIVER_MISSING, verify classpath and dependencies, not just the presence of a random JAR. For destination denial, review the exact configured origin and egress policy; do not broadly disable protection. For VERSION_CONFLICT, reload current state and review the competing change. For CAPACITY, inspect retained payload size and queue state before pruning. For encrypted-state failure, preserve the file and restore a tested backup with the correct key.
