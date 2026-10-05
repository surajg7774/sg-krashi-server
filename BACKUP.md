# Database backup and restore

Production data lives in one Railway MySQL service (`MySQL`, image `mysql:9.4`,
database `railway`, one 500 MB volume). This document covers the **independent,
encrypted, offsite backup** that does not depend on Railway, and how to restore it.
No secrets are in this file; variable *names* only.

## How it works

A Railway cron service named **`db-backup`** (source: `backup/` in this repo) runs every
day at **03:00 IST** (`30 21 * * *` UTC, no auto-retry). It runs inside Railway's private
network, so the database is never exposed to the internet for backups.

1. `mysqldump --single-transaction` of the `railway` database (client is mysql:9.4, same
   as the server), checked for the `-- Dump completed` trailer and a table count.
2. Row counts of every table are taken just **before** and just **after** the dump.
3. gzip, then **age** encryption to a **public key only**. The private key is not on
   Railway (see "The key" below).
4. Upload to a private **Backblaze B2** bucket under `mysql/daily/` using a **write-only**
   key, then a `.manifest.json` next to it (uploaded last, so a manifest means the backup
   before it is complete).
5. Ping **Healthchecks.io** (`/start`, then success, or `/fail`). Any failure also exits
   non-zero, which Railway shows as a failed run.

Retention: a B2 lifecycle rule on prefix `mysql/` (hide after 30 days, delete 1 day after
hiding) keeps about **30 daily backups**. Nothing in the job deletes anything.

Optional second layer: Railway's native volume backups (Backups tab of the `MySQL`
service), **daily and weekly schedules only** so retention stays within 30 days. These are
snapshots of the live data files, restorable only into the same Railway project, and they
disappear with the volume/account, which is why the offsite backup exists.

### Variables on the `db-backup` service

| Name | Purpose |
|---|---|
| `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_DATABASE` | references to the `MySQL` service (private network) |
| `AGE_PUBLIC_KEY` | age **public** key (`age1…`). The job refuses to run if given a private key |
| `B2_ENDPOINT`, `B2_BUCKET` | S3 endpoint (`https://s3.<region>.backblazeb2.com`) and bucket name |
| `B2_KEY_ID`, `B2_APPLICATION_KEY` | the **write-only** application key, limited to this bucket |
| `HEALTHCHECKS_PING_URL` | the Healthchecks check's ping URL (period 1 day, grace 12 h = alert after 36 h) |
| `B2_READ_KEY_ID`, `B2_READ_APPLICATION_KEY` | optional **read-only** key used by people for verification and restores; the job never reads them |

Rotating a key: create the new one in B2, update the variable, run the job once, then
delete the old key in B2.

### The key (read this)

Backups can only be decrypted with the age **private key**, which lives **outside** this
repo and outside Railway: a password-manager entry plus an offline copy. **If it is lost,
every backup is unreadable.** If it leaks, anyone who also obtains a backup file can read
your data. To replace it: generate a new keypair (`age-keygen`), set the new public key
in `AGE_PUBLIC_KEY`, and keep the old private key until the old backups have expired (30 days).

## Checking that the last backup succeeded

- **Healthchecks.io** dashboard: the check is green and "last ping" is within about a day.
  It emails when no success ping arrives within 36 hours, or on `/fail`.
- **Railway**: `db-backup` service, Deployments: the most recent run's status and logs
  (`OK mysql-railway-<timestamp>.sql.gz.age (…)` on success; `BACKUP FAILED during step: …`
  on failure).
- **B2**: list the bucket's `mysql/daily/` prefix; the newest `.sql.gz.age` and its
  `.manifest.json` should be from the last night:
  ```
  rclone lsl --s3-provider Other … b2:<bucket>/mysql/daily/   # with the read-only key
  ```

## Restoring

You need: the encrypted file, its manifest, the **private key file**, and the tools
`age`, `gzip` and a MySQL **9.4** server/client.

1. **Download** the newest `mysql-railway-<ts>.sql.gz.age` and `mysql-railway-<ts>.manifest.json`
   from B2 (web console, or `rclone copy` with the read-only key).
2. **Verify** the file: `sha256sum <file>` must equal `backup_sha256` in the manifest.
3. **Decrypt:** `age -d -i <path-to-private-key> <file> | gunzip > dump.sql`
   (the plaintext contains personal data; delete it when finished).
4. **Load into an empty database** of a MySQL 9.4 server:
   `mysql -h <host> -u <user> -p railway < dump.sql`
5. **Check** against the manifest: each table's restored row count must be between its
   `before` and `after` values (they differ only if rows changed during the dump), and
   `flyway_schema_history` must have the same versions and checksums with every `success = 1`.
6. **Re-apply account deletions** (next section), *then* point the application at it.

To restore production: stop `sg-krashi-server`, restore into a fresh volume or an emptied
database, run the deletion re-apply, start the server (Flyway validates the migrations on
boot), then check `/health`.

### Deleted accounts come back after a restore: re-apply them

An account deleted through `DELETE /api/v1/customers/me` is **anonymized in place**. A
backup taken *before* that deletion still holds the person's name, email, phone and
addresses, so restoring it would silently bring them back.

Every manifest lists `anonymized_user_ids` (**ids only**, found as users whose email is
the `deleted-<id>@deleted.invalid` placeholder). The newest manifest therefore covers
every deletion made before that backup. After restoring **any** backup, run:

```
MYSQL_HOST=… MYSQL_USER=… MYSQL_PASSWORD=… backup/reapply-deletions.sh <newest manifest.json>           # dry run
MYSQL_HOST=… MYSQL_USER=… MYSQL_PASSWORD=… backup/reapply-deletions.sh --yes <newest manifest.json>     # apply
```

It skips ids that are already anonymized or absent, and applies the same changes as the
server's `AccountErasureRepository.erase()` (`backup/reapply-deletions.sql`; a unit test
fails if the two drift). It prints ids only.

Known gaps:
- A deletion made **after the newest backup** is not in any manifest. The window is under
  24 hours. The server logs one line per deletion (`Account deleted: userId=<id>`) in Railway
  logs; look there, and pass those ids directly: `reapply-deletions.sh --yes 12,15`.
- Cloudinary images of deleted users' crop scans were already removed at deletion time;
  after a restore the rows are erased again, so nothing further is needed.
- The accounts' emails would be free to re-register in the meantime; nothing here changes that.

## Restore drill

A backup that has never been restored is not a backup. Run a drill after any change to the
backup job, and at least quarterly:

1. Download the newest backup and manifest; verify the sha256.
2. Decrypt with the private key into a **throwaway** `mysql:9.4` container (never production).
3. Compare per-table counts and the Flyway list with the manifest.
4. Run `reapply-deletions.sh` against it (dry run, then `--yes`) and confirm it works.
5. Delete the container and the decrypted dump.

Last drill: see the entry at the bottom of this file.

## Retention and personal data

Backups contain personal data (names, emails, addresses, chat and scan history). They are
encrypted, stored privately, and expire after about 30 days. The Privacy Policy says a
deleted account may remain in backups for up to that long and is removed again if a backup
is ever restored.

## Known limitations

- If the cron stops silently for 30 days, the lifecycle rule would eventually expire every
  backup. Healthchecks alerts after 36 hours; act on it.
- The job currently connects as the same database user the application uses (root). A
  dedicated read-only backup user is planned with the broader database-security work.
- The plaintext dump exists briefly on the container's ephemeral disk during a run.
- Cron runs in UTC; if a run is still going when the next is due, Railway skips the new one.
- Row counts are taken around the dump, not inside its transaction, hence the before/after range.

## Drill log

| Date | Backup restored | Result |
|---|---|---|
| _(filled in after the first production drill)_ | | |
