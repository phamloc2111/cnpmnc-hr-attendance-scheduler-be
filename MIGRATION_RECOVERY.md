# Lost V10 migration recovery

The connected database already applied `V10__shift_registrations.sql` with checksum
`-1798121770`. Its original source is unavailable. The V10 in this branch is an
explicit reconstruction from inspected columns, constraints, foreign keys and
indexes of `shift_registrations` and `registration_reopens` on 2026-11-09, with
checksum `421065846`. It is not the original file. Workplace settings belong to V11.

Fresh databases run the reconstructed V10 and V11 normally. Existing databases
with the original checksum require a reviewed, one-time history reconciliation.
Do not disable validation, delete history, drop application tables, or run the
reconstructed CREATE statements against tables that already exist.

Stop backend instances during recovery. Before execution, save the result of:

```sql
SELECT * FROM public.flyway_schema_history WHERE version = '10';
```

Review and execute [scripts/recover-v10-history.sql](scripts/recover-v10-history.sql)
using a PostgreSQL client connected to the same database as the backend. It checks
the inspected schema fingerprint and the exact successful V10 history entry,
then updates only that entry's checksum from `-1798121770` to `421065846` in one
atomic transaction. It preserves application schema and rows. Unexpected schema
or history aborts without updating anything. Repeating a successful repair is safe.
Other migration errors must be investigated separately.

Then start the backend with this branch's migrations. Flyway validation remains
enabled and V11 creates the workplace settings table. The recovery script is
outside migration locations and is never executed automatically.

If cancelling recovery before deploying the reconstructed file, the history-only
change can be reversed with this guarded statement (only with the matching old
application artifact; it does not undo V11):

```sql
UPDATE public.flyway_schema_history SET checksum = -1798121770
WHERE version = '10' AND checksum = 421065846
  AND script = 'V10__shift_registrations.sql' AND success = true;
```

Validation: `MigrationRecoveryTests` uses an isolated PostgreSQL container to
reproduce the old checksum mismatch, reject schema drift/unexpected checksums,
recover history, preserve an existing registration reopening, validate again,
and migrate to V11. The regular integration suite also covers a fresh database.

Flyway describes checksum reconciliation in its
[repair documentation](https://documentation.red-gate.com/flyway/reference/commands/repair).
This narrowly scoped SQL procedure is specific to this lost-file incident.
