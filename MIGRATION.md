# Moving SG Krashi to a new Railway account

This is the runbook for moving the backend (server, MySQL database, backup job) from the current Railway
account to a **different Railway account**, because the current trial ends around **24 Oct 2026**. It was
written from an audit of the real configuration and **rehearsed end to end on throwaway local Docker MySQL 9.4
servers** (what was and was not rehearsed is in section 11). No secret value is in this file: variable
**names** only.

The plan accepts that **the backend URL will change** (no custom domain, no APK in users' hands). Section 4 is
the checklist of every place that URL lives.

Do the move well before the trial ends: **target 17-20 Oct**, leaving at least four days of margin. Do not
rely on Railway keeping trial data or services running after the trial ends.

---

## 1. What exists today (audit)

| Item | Today |
|---|---|
| Project | `sg-krashi-server` (id `5c74ecb2-67ed-4b3a-8f68-bf2ee1f4169e`), one `production` environment, region `sfo` |
| **sg-krashi-server** | Built from the `Dockerfile` in this repo's root (multi-stage Maven build, Java 21 JRE, listens on **8080**, runs as a non-root user). Deployed with `railway up` from the repo root. **GitHub auto-deploy does not work** for this repo/account pairing, so every deploy is `railway up`. Public domain `sg-krashi-server-production-8583.up.railway.app`, target port left on auto. **No healthcheck path** and no start command set. 1 replica. |
| **MySQL** | Image `mysql:9.4`, database `railway`, one 500 MB volume at `/var/lib/mysql` (about 3 MB used). Start command `docker-entrypoint.sh mysqld --innodb-use-native-aio=0 --disable-log-bin --performance_schema=0 --innodb-buffer-pool-size=1G`. Private name `mysql.railway.internal:3306`. **The public TCP proxy that used to exist was deleted on 2026-10-07**; the database is reachable only on the private network and over `railway ssh` (section 8.0). The only accounts are `root@%` and `root@localhost`. |
| **db-backup** | Cron service built from `backup/` (its own `Dockerfile` on `mysql:9.4`, with `age` and `rclone` pinned by checksum). Schedule `30 21 * * *` UTC (03:00 IST), restart policy `NEVER`. Connects to MySQL over the private network **as root**. |
| Database settings | Server charset `utf8mb4`, collation `utf8mb4_0900_ai_ci`, `sql_mode` = MySQL 9.4 default (`ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION`), `time_zone = SYSTEM` (UTC on the host), `max_allowed_packet` 64 MiB, `lower_case_table_names = 0`. All 39 tables are `utf8mb4_0900_ai_ci`. **A stock `mysql:9.4` container has exactly these defaults**, so nothing needs configuring. (The live buffer pool is 128 MiB although the start command asks for 1 GiB; the data is tiny, so either is fine.) |
| Outside Railway (they move nothing) | **Backblaze B2** bucket with the encrypted backups, the **age** key pair, **Healthchecks.io**, **Cloudinary** (all product, crop and equipment photos), **Brevo** (email), **Gemini**, **Firebase/FCM**, **Razorpay**, **Vercel** (web), **Expo/EAS** (mobile builds). |

What the audit found in the code and the database that matters for the move:

- **No Railway address is hard-coded in the server.** Not in code, not in `application*.yml`, not in any Flyway
  migration, and **not in any database row** (every text column of every table was scanned: no row contains the
  Railway address, a relative `/uploads/` path or `localhost`; the stored URLs are Cloudinary photos and official
  scheme links). The restored database therefore needs no rewriting.
- **No variable on the server or backup service contains the Railway address** (checked by value, without printing).
- **The database connection is the private address** (`mysql.railway.internal`), with no query string.
- **Google Sign-In has no redirect URI on the backend.** The backend only verifies the ID token's audience against
  `GOOGLE_WEB_CLIENT_ID`; the Google console entries are the web site's origin and the Android keystore, neither of
  which changes.
- **Brevo, Firebase, Cloudinary, Gemini, Open-Meteo and data.gov.in are outbound only.** None calls the backend.
- **Razorpay is the one inbound callback:** `POST /api/v1/payments/webhook`. Its URL lives only in the Razorpay
  dashboard.
- **The web login cookie** (`refreshToken`) is `HttpOnly; Secure; SameSite=None` with **no Domain**, so it belongs
  to the old backend host. After the move, **every web user is signed out once** and logs in again. Refresh tokens
  restored from the backup are harmless.
- **Four scheduled jobs run inside the server:** weather advisory 06:00 IST (**sends real push notifications**),
  booking completion 01:00 IST, farmer payout batch Monday 02:00 IST, mandi price sync 06:30 and catch-ups.
  A server pointed at a copy of the data runs them too. See the rehearsal rule in section 6.

---

## 2. Environment variables (names only)

Save **every value** before you start (section 5). Copy them from the Railway dashboard, service, **Variables**,
**Raw Editor**, into your password manager. A value that looks like `${{Something.NAME}}` is a **reference to
another service**: recreate the reference in the new project, do not copy the text.

The classification below comes from **starting the real server (production profile) once per variable with only that
variable removed** and everything else set to dummy values. "Stops" means the app exits at start with "Could not
resolve placeholder"; it never goes live, so a bad deploy is caught immediately.

### 2.1 sg-krashi-server

| Variable | If missing | New value in the new project |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` (= `prod`) | **Stops** (the dev profile tries to reach a local database) | Same: `prod` |
| `DB_URL` | **Stops** | **Changes**: `jdbc:mysql://mysql.railway.internal:3306/railway` (no query string) |
| `DB_USERNAME`, `DB_PASSWORD` | **Stops** | **Change**: the `sgk_app` user (section 7) |
| `JWT_SECRET` | **Stops** | **Keep the same value** (otherwise every token and reset link dies) |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | **Stops** | Same (the webhook secret must match the one in the Razorpay dashboard) |
| `MAIL_HOST`, `MAIL_PORT` | **Stops** | Same |
| `MAIL_PROVIDER` | **Stops** | Same |
| `BREVO_API_KEY` | **Stops** | Same |
| `FRONTEND_URL` | **Stops** | Same (the web site's address, used in reset emails) |
| `STORAGE_PROVIDER` | **Stops** | Same |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | **Stops** | Same |
| `GEMINI_API_KEY` | **Stops** | Same |
| `CORS_ALLOWED_ORIGINS` | Starts in the rehearsal, but **treat as required**: without the web origin the browser blocks every call from the web site | Same (the Vercel origin) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Starts (email goes through the Brevo API, not SMTP login), but keep them | Same |
| `AI_SERVICE_URL`, `AI_SERVICE_API_KEY` | Starts (legacy settings; the Gemini provider is used) | Same, keep for safety |
| `GOOGLE_WEB_CLIENT_ID` | Starts, but **Google Sign-In stops working** | Same |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Starts, but **no push notifications can be sent** | Same (a multi-line JSON: paste it carefully, no extra quotes) |
| `FCM_ENABLED` (default `false`) | Push is **off** | **`false` until the cutover, then `true`** |
| `MANDI_API_KEY` | Starts, but **mandi price sync stops** | Same |
| `CHAT_ASSISTANT_ENABLED` (default `true`), `CROP_DOCTOR_PROVIDER` (default `gemini`), `GEMINI_MODEL`, `MAIL_FROM`, `MANDI_BASE_URL`, `MANDI_RESOURCE_ID`, `GEOCODING_BASE_URL` | Defaults apply | Copy only the ones that exist today |
| `S3_BUCKET_NAME`, `S3_REGION`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_PUBLIC_URL_BASE`, `S3_ENDPOINT` | Starts: only read when `STORAGE_PROVIDER` selects S3 | Copy only if you use S3 storage |
| `PORT` | The app listens on **8080** (`server.port`), not on `PORT` | Not needed. Set the domain's target port to 8080 |

Railway-injected values (`RAILWAY_*`) are not used by the application. Check the old service's Raw Editor for
anything not listed above and carry it over; the table covers everything the code reads.

### 2.2 MySQL

`MYSQL_ROOT_PASSWORD` and `MYSQL_DATABASE` (= `railway`). The old service may also hold platform-provided
`MYSQL_URL`, `MYSQL_PUBLIC_URL`, `MYSQLHOST`, `MYSQLPORT`, `MYSQLUSER`, `MYSQLPASSWORD` and `MYSQLDATABASE`; **the
application does not read them** (it uses `DB_URL`), so they are not needed.

### 2.3 db-backup (all read by `backup/backup.sh`)

| Variable | If missing | New value |
|---|---|---|
| `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE` | The job **fails** (and pings Healthchecks `/fail`) | `mysql.railway.internal`, `3306`, `railway` |
| `MYSQL_USER`, `MYSQL_PASSWORD` | Fails | **Change**: the `sgk_backup` user (section 7) |
| `AGE_PUBLIC_KEY` | Fails (never uploads plaintext) | Same value |
| `B2_ENDPOINT`, `B2_BUCKET`, `B2_KEY_ID`, `B2_APPLICATION_KEY` | Fails | Same (the write-only key) |
| `HEALTHCHECKS_PING_URL` | Backs up, but **no alert if it stops** | Same |
| `BACKUP_PREFIX` (default `mysql/daily`) | Default applies | Same, so new backups sit next to the old ones |

---

## 3. Fresh MySQL and Flyway (verified)

- **Flyway V1 to V41 run cleanly on an empty MySQL 9.4**: 36 migrations (the numbering has gaps), all `success`,
  39 tables, **using the limited database user from section 7** (no `root`).
- **Restoring a backup that stops at V40** (which is what the current production backup is, because V41 was
  deployed after it was taken) and then starting the app **applies V41 on first start** ("Successfully applied 1
  migration ... now at version v41") with the limited user. That is expected and needs no manual step.
- A backup that is already at V41 starts with "Schema is up to date. No migration necessary."
- Flyway checks the restored `flyway_schema_history` against the migration files at start. The checksums in the
  production manifest were compared with the restored copy in the rehearsal and are identical.
- The new MySQL must be **`mysql:9.4`**. A newer major version is untested (Flyway prints a harmless warning even on
  9.4: "MySQL 9.4 is newer than this version of Flyway"). The backup tools in `backup/Dockerfile` are also 9.4.

---

## 4. URL change checklist (what must be updated after the backend URL changes)

Let `NEW_URL` be the new service's public address, for example `https://<name>.up.railway.app`. The API base is
`NEW_URL/api/v1`.

| # | Where | What to change | Notes |
|---|---|---|---|
| 1 | **Vercel** project `sg-krashi-client`, env var **`VITE_API_BASE_URL`** (Production **and** Preview) | Set to `NEW_URL/api/v1` | It is **baked into the build**: change it, then **redeploy with "Use existing Build Cache" unticked**. The preview and sitemap functions read the same variable at run time. Do **not** set `API_BASE_URL` (it would override it for the functions only). Vercel currently has just two variables: `VITE_API_BASE_URL` and `VITE_GOOGLE_CLIENT_ID`. |
| 2 | **Web code** `api/_lib/config.js` | `DEFAULT_API_BASE_URL` is a **hard-coded fallback** to the old address, used only if the env var is missing or not `https`. Update it in a code change after the move. | Reported, not changed yet |
| 3 | **Web tests** `build/checkSw.mjs`, `build/swPolicy.test.ts` | They use the old address as sample data (an "API request must not be cached" fixture). They do not talk to the server and nothing breaks if left, but update them to avoid confusion. | Reported, not changed yet |
| 4 | **Mobile app** `src/api/client.ts`, constant **`API_BASE_URL`** | Set to `NEW_URL/api/v1`, then **make a new EAS build**. | The only copy in the app. No other file (`app.json`, `eas.json`, screens) holds a backend address. An already-installed app keeps calling the old address, which is why no APK should be in use. |
| 5 | **Razorpay dashboard** webhook | Change the webhook URL to `NEW_URL/api/v1/payments/webhook`. The **webhook secret must equal `RAZORPAY_WEBHOOK_SECRET`** on the new server (reuse the same secret, or set both anew). | Only one URL can receive webhooks at a time. See the cutover for in-flight payments. |
| 6 | **CORS** `CORS_ALLOWED_ORIGINS` on the new server | **No change** while the web site stays on `https://sg-krashi-client.vercel.app`. Change only if the web address changes. | Currently one origin: the Vercel site |
| 7 | **`FRONTEND_URL`** on the new server | **No change** while the web address stays the same. It is the address inside password-reset emails. | |
| 8 | **Google Cloud console** | **Nothing.** No backend address is registered there (see section 1). | |
| 9 | **Healthchecks.io** | **Nothing for the backup check** (`HEALTHCHECKS_PING_URL` is outbound and independent of the backend URL). Reuse the same check. **If you also have an uptime monitor** (Healthchecks, UptimeRobot or similar) that calls `GET /health`, **change its URL** to `NEW_URL/health`. | Not visible from the repo: check your monitors |
| 10 | **Brevo, Firebase, Cloudinary, Gemini** | **Nothing.** | |
| 11 | **Bookmarks and notes** | Anything that points at `/swagger-ui.html`, `/health` or the old public address. | |

The one hard-coded **runtime** address in the web client is item 2 (a fallback). The web client's real config
point is `VITE_API_BASE_URL` (read in `src/shared/services/axiosInstance.ts` and `resolveMediaUrl.ts`). The mobile
app's is `API_BASE_URL` in `src/api/client.ts`. The server has no copy.

---

## 5. Pre-migration checklist (do this days before, nothing here changes production)

- [ ] **Save every environment value** of the three services into your password manager (section 2). Include the
      B2 write key, the B2 read key, `AGE_PUBLIC_KEY`, the Healthchecks ping URL, the Razorpay key id, secret and
      webhook secret, `JWT_SECRET`, the Brevo and Gemini keys, the Cloudinary keys, the Google client id, and the
      Firebase service-account JSON.
- [ ] **Keep `JWT_SECRET` the same** in the new project. A new one would invalidate every issued token and reset
      link.
- [ ] **Confirm the age private key is safe**: it must exist in your password manager and the offline copy. Without
      it no backup can be restored. (It is **not** on Railway.)
- [ ] Confirm the newest B2 backup is under 24 hours old (Healthchecks green, B2 object timestamp).
- [ ] Create the new Railway account and pick a plan that **does not expire**: a trial would just repeat this
      deadline. Add a payment method. Choose a volume of at least 1 GB.
- [ ] Choose the region. The server and the database **must be in the same region**. A region closer to India
      (for example Singapore, if your plan offers it) lowers latency; this is optional and independent of the move.
- [ ] Have these tools ready on your laptop: Docker (to run `mysql:9.4`, `age`, `rclone` from the backup image),
      the Railway CLI, the Vercel CLI, `git`.
- [ ] Decide the cutover time: a quiet night in IST, **not** between 01:00 and 03:30 (nightly jobs and the 03:00
      backup) or at 06:00 (weather advisory).
- [ ] Tell users there will be a short maintenance window (30 to 60 minutes) and that they will need to sign in
      again on the web.

---

## 6. Build the new project (no traffic yet)

> **Rehearsal rule: keep push notifications OFF on any server that holds a copy of real data until the cutover.**
> Set **`FCM_ENABLED=false`** on the new server until the cutover is verified (section 9, step 9). The weather advisory job (06:00 IST) sends real push
> notifications to the device tokens in whatever database the server is pointed at, so an idle server on restored
> data would notify real farmers a second time. Also leave the Razorpay webhook URL on the old server until the
> cutover.

1. **Create the project** in the new account (for example `sg-krashi`), environment `production`.
2. **MySQL service.** Add a service from the Docker image **`mysql:9.4`** (not Railway's template, which may pick
   another version). Set the variable **`MYSQL_ROOT_PASSWORD`** (a new long random value) and
   **`MYSQL_DATABASE=railway`**. Add a **volume mounted at `/var/lib/mysql`** (at least 1 GB). Set the start command
   to `docker-entrypoint.sh mysqld --innodb-use-native-aio=0 --disable-log-bin --performance_schema=0` (the same
   flags as today; the buffer-pool flag can stay at the default for this data size). **Name the service `MySQL`**:
   its private address becomes `mysql.railway.internal`. **Do not enable a public TCP proxy**; use `railway ssh` for
   database access (section 8.0).
3. **Create the two limited database users** instead of using `root` (section 7). Use the private address and the
   `root` password only for this one step and for the restore.
4. **Server service.** Create an empty service `sg-krashi-server`. In **Settings, Networking**, generate a public
   domain and set its **target port to 8080** explicitly (the old service left it on auto; setting it removes any
   doubt). Optionally set the **healthcheck path to `/health`** so a bad deploy is never marked live (the old
   service had none). Add the variables from section 2 (secrets from your password manager), with these changes:
   `DB_URL=jdbc:mysql://mysql.railway.internal:3306/railway`, `DB_USERNAME=sgk_app` and its password, and
   **`FCM_ENABLED=false`** for now. Deploy from the repo root:
   `railway link` to the new project, then `railway up --service sg-krashi-server`. (The build uses the repo's
   `Dockerfile`: the log should say "load build definition from Dockerfile". The dashboard's builder label is
   misleading.) Because the database is still empty, Flyway builds the schema; that is fine for a first smoke test
   and is wiped by the restore.
5. **Restore a backup** into the new database (section 8), as a **rehearsal** with the newest backup, so you know
   the steps and timings before the real night.
6. **Backup service.** Create the cron service `db-backup` and deploy it from `backup/` (`cd backup && railway up
   --service db-backup`, the same way it was first deployed, see `BACKUP.md`). Set its variables (section 2):
   the **same** B2 write key and bucket, the **same** `AGE_PUBLIC_KEY`, the same Healthchecks ping URL, and
   `MYSQL_HOST=mysql.railway.internal`, `MYSQL_PORT=3306`, `MYSQL_DATABASE=railway`, **`MYSQL_USER=sgk_backup`**
   with its password. The cron schedule `30 21 * * *` comes from `backup/railway.json`; check it in the service
   settings. **Do not let both projects' backup jobs run at the same time after the cutover** (section 9, step 4).
7. **Smoke-test the new server** on its Railway address (section 10) while it is still pointed at a copy of the
   data and has push off. Fix anything now, with no users affected.
8. **Run the restore rehearsal's timing** and write down how long each step took. Expect minutes: the database is
   about 3 MB.

---

## 7. Limited database users (instead of root)

The old setup uses `root` everywhere, both for the app and the backup. In the new project create two users. **All
three statements below were tested in the rehearsal**: the app booted, ran Flyway V1 to V41 from empty and from a
restored backup, and re-applied account deletions with `sgk_app`; the real `backup.sh` ran with `sgk_backup`.

```sql
-- run once as root, through `railway ssh --service MySQL` (section 8.0), e.g. the whole block as one -e "..." argument
CREATE USER 'sgk_app'@'%' IDENTIFIED BY '<new long random password>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES,
      CREATE TEMPORARY TABLES, LOCK TABLES ON railway.* TO 'sgk_app'@'%';

CREATE USER 'sgk_backup'@'%' IDENTIFIED BY '<another long random password>';
GRANT SELECT, SHOW VIEW, TRIGGER, EVENT, LOCK TABLES ON railway.* TO 'sgk_backup'@'%';
```

- `sgk_app` is limited to the `railway` database. It cannot see other databases, the `mysql` system schema, or
  manage users. It still needs `CREATE`/`ALTER`/`DROP`/`INDEX`/`REFERENCES` because the server runs Flyway at start.
- `sgk_backup` is read-only. The dump uses `--routines`: there are **no stored routines today**, so no extra
  privilege is needed. **If a routine is ever added, give `sgk_backup` the global `SHOW_ROUTINE` privilege.**
- Both users are **per database**, so they are **not** in the backup. They must be created in the new project
  **before** the server starts. (A restore does not touch users.)
- `root` is then used only by you, for the restore and for emergencies. **Never put `root` in a service's
  variables again.**
- Do not leave a TCP proxy enabled (see section 8.0 for the one step that may need one, and how to remove it). This
  also closes the open security items "public MySQL proxy" and "app and backup connect as root".

---

## 8. Restoring the backup (the rehearsal and the real night use the same steps)

### 8.0 Reaching the database without the public proxy (tested on the old project)

The old project's public TCP proxy was deleted (2026-10-07). Database access, including read-only checks and the
row-count verification below, goes through the Railway CLI over SSH. **One-time setup per laptop:**

1. `ssh-keygen -t ed25519 -C "railway-sg-krashi" -N "" -f ~/.ssh/railway_sg_krashi` (no passphrase, a dedicated file;
   never print or commit the private key).
2. `railway ssh keys add --key "railway-sg-krashi" --name railway-sg-krashi` (registers **only the public key**;
   the `--key <path>.pub` form failed on a path containing a space, so pass the key comment).
3. Run `railway ssh --service MySQL` once by hand and answer `yes` to the host-key question. Non-interactive runs fail
   with "Host key verification failed" until this is done.

**Command that works** (run from the folder linked to the project with `railway link`; the password is read from the
container's own environment and never printed):

```
railway ssh --service MySQL -- sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot railway -e "<read-only SQL>"'
```

Tested on the old project with `SELECT VERSION(); SELECT COUNT(*) FROM users;` (answers `9.4.0` and `12`). Use it for the
limited-user statements in section 7, the row counts in step 6 below, and any later check.

**What it does not cover.** The restore load (step 5) and `reapply-deletions.sh` (step 7) run on your laptop and need a
MySQL host and port. Two ways:

- **Pipe over SSH (not tested yet).** Replace the `-e "..."` part with nothing and feed the decrypted SQL on stdin:
  `... | railway ssh --service MySQL -- sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot railway'`. Before relying on
  it, test with `echo 'SELECT 1;' | railway ssh ...` during the rehearsal. `reapply-deletions.sh` cannot use this form.
- **Temporary TCP proxy on the NEW project, only for steps 5 and 7.** Create it in the dashboard (MySQL service,
  Settings, Networking, TCP Proxy), run the load and the script against its host and port, then **delete it at once**.
  Deleting from the CLI (this is how it was done on the old project; the CLI has no proxy command, so it uses its own
  logged-in API access, no token to copy):
  ```
  railway api 'query { tcpProxies(environmentId: "<env id>", serviceId: "<MySQL service id>") { id domain proxyPort } }'
  railway api 'mutation { tcpProxyDelete(id: "<proxy id from above>") }'
  ```
  Confirm with the first query that the list is empty.

**After the migration, clean up the SSH access:**

- [ ] Remove the key from the Railway account: `railway ssh keys remove` (key name `railway-sg-krashi`, fingerprint
      `SHA256:orX0CvsII4eHakaQXq6nScNvXe9w2tJ9fE9MZGIw19g`) or https://railway.com/account/ssh-keys.
- [ ] Delete the local files `~/.ssh/railway_sg_krashi` and `~/.ssh/railway_sg_krashi.pub` (and the Railway gateway
      line the host-key question added to `~/.ssh/known_hosts`, if you want it gone).
- [ ] If a new key was made for the new account, remove that one too once you no longer need SSH access.

You need: the newest `mysql-railway-<ts>.sql.gz.age` and its `.manifest.json` from B2, the **age private key**, and
Docker. This uses the repo's `backup/` image, which has `age`, `gzip` and the MySQL 9.4 client. **The decrypted
data never has to touch the disk**: it is streamed into MySQL.

1. **Download** the newest pair from B2 with the **read-only** key (web console, or `rclone copyto`; see
   `BACKUP.md`). Put them in an empty folder.
2. **Verify the file**: `sha256sum <file>.sql.gz.age` must equal `backup_sha256` in the manifest. (This needs no
   key.) Stop if it differs.
3. **Make the new database reachable for the load.** The checks in step 6 use `railway ssh` (section 8.0). The load
   in step 5 and the script in step 7 need a MySQL host and port: either pipe over SSH (section 8.0, untested) or
   create a **temporary TCP proxy on the new project** and note its host and port. **Delete it at step 8.**
4. **Make sure the target database is empty.** If you ran the server once (section 6, step 4), drop and recreate it:
   `DROP DATABASE railway; CREATE DATABASE railway CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;` (the users
   from section 7 survive this).
5. **Decrypt and load in one pipe** (replace the placeholders; the key file stays on your laptop, mounted read-only):
   ```
   docker run --rm -v "<folder with the backup>:/in:ro" -v "<path to the private key file>:/key:ro" \
     -e MYSQL_PWD="<root password>" --entrypoint bash <backup image or mysql:9.4 with age> -c \
     "age -d -i /key /in/<file>.sql.gz.age | gunzip | mysql -h <host> -P <port> -u root railway"
   ```
   (The image is built from `backup/Dockerfile`: `docker build -t sg-krashi-db-backup backup/`.)
   **The age private key must be kept at `C:\SG-Krashi-Secrets\` (not in Downloads)**, plus the offline copy and the
   password manager. **The person running the restore mounts the key file themselves** (the `-v` line above); an
   assistant or script must not be given it. **The key is never pasted, printed or committed.** Delete nothing else;
   there is no plaintext file to clean up.
6. **Verify against the manifest** (the rehearsal's `verify` step; run the counts through the `railway ssh` command of
   section 8.0, so no public access is needed): for every table the restored row count must be
   **between `before` and `after`** in the manifest, there must be **no missing or extra table**, and
   `flyway_schema_history` must list the same versions and checksums with every `success = 1`. A sample SQL for
   counts is in `backup/backup.sh` (`counts()`); the numbers to expect from the newest production manifest at the time
   of writing were: users 12, orders 5, order items 5, payments 2, products 19, crop listings 9, device tokens 4,
   carts 5, chat sessions 12, crop scans 2, notifications 8, audit log 55, order status history 10 (38 tables,
   559 rows, Flyway at V40).
7. **Re-apply account deletions** with the **newest** manifest (an account anonymised after an older backup would
   otherwise come back). Use the app user, which has enough rights (tested):
   ```
   MYSQL_HOST=<host> MYSQL_PORT=<port> MYSQL_USER=sgk_app MYSQL_PASSWORD=<pw> \
     backup/reapply-deletions.sh <newest manifest.json>          # dry run
   MYSQL_HOST=<host> MYSQL_PORT=<port> MYSQL_USER=sgk_app MYSQL_PASSWORD=<pw> \
     backup/reapply-deletions.sh --yes <newest manifest.json>    # apply
   ```
   It prints ids only. Tested: it skips ids that are already anonymised, and for one that is not it erases the
   profile, tokens, chats and addresses, keeps the order and replaces its address with `[removed]`.
8. **If you created a temporary TCP proxy, delete it now** (section 8.0) and confirm the proxy list is empty.
9. Start (or redeploy) the new server. It validates the migrations on boot and, for a backup that stops at V40,
   **applies V41** automatically.

---

## 9. Cutover (the real move) without losing orders

Orders and payments are written by the live server. The risk is anything saved after the last backup. The plan
**stops the old server before the final backup**, so nothing can be written in between, and accepts a short outage
instead of a risky overlap.

**Before the night:** sections 5 to 8 done, the rehearsal passed, the new server smoke-tested (push off), the new
backup job deployed and its first run successful, the Razorpay webhook still pointing at the old server.

**The night (about 30 to 60 minutes):**

1. **Announce** the window. Note the time. Open the Razorpay dashboard in a tab.
2. **Stop the old server** (old project, `sg-krashi-server`, Deployments, remove the active deployment, or
   `railway down`). The database is now quiet. Do **not** stop the old MySQL.
3. **Take a fresh manual backup in the old project**: redeploy the latest `db-backup` deployment (this runs the job
   once). Confirm a **new** object in B2 with a timestamp after step 2, that Healthchecks shows a success ping, and
   that the manifest's `before` and `after` counts are **equal** for every table (nothing changed during the dump).
   This is the backup you restore.
4. **Disable the old project's `db-backup` cron now** (remove its schedule or delete the service). **If you forget,
   its next 03:00 IST run will back up the old, frozen database as the "newest" backup and ping Healthchecks
   green, hiding any failure of the new job.** Leave the old project's MySQL service alone.
5. **Restore into the new project** with section 8 (use the new backup from step 3): download, sha256, empty the
   database, stream the restore, verify the counts (now exact), re-apply deletions with that manifest, any temporary proxy deleted.
6. **Start the new server with `FCM_ENABLED=false`** (keep it false). Check `/health`, the logs (Flyway V41
   applied for a V40 backup, no errors) and run the smoke tests (section 10). Push is switched on only in step 9,
   after the cutover is verified.
7. **Update the clients** (section 4): set `VITE_API_BASE_URL` on Vercel and redeploy without cache; change the
   Razorpay webhook to the new URL; update any uptime monitor.
8. **Check Razorpay for the gap**: any payment captured between step 2 and step 7 may have its webhook lost or sent to
   the old address. In the dashboard list captured payments for that window and compare them with the orders in the
   new database. For an order still `PENDING_PAYMENT` whose payment shows captured, an admin can confirm it (the
   server already supports an admin reconciling a missed webhook). If Razorpay's own webhook retries reach the new
   address, they are idempotent. Do not rely on this: compare by hand.
9. **Verify the cutover, then enable push.** Watch `/health`, the Railway logs, Healthchecks and a test order. Only
   when the web site and a test order work against the new server, set **`FCM_ENABLED=true`** on the new server and
   redeploy. (Do this before the 06:00 IST weather advisory only if you want that day's advisory sent.)
10. **The next 03:00 IST backup** (from the new project) must succeed and the object must appear in B2 with a new
    timestamp.
11. **Build the new mobile app** with the new `API_BASE_URL` when convenient (section 4, item 4).

**Cutover checklist (must-not-forget items):**

- [ ] **`FCM_ENABLED=false` on the new server until the cutover is verified** (step 9). An idle or half-verified
      server on real data must never send push notifications.
- [ ] **Web fallback address:** `DEFAULT_API_BASE_URL` in the web repo's `api/_lib/config.js` must be updated to the
      new address **together with** `VITE_API_BASE_URL` (Vercel), and the **two test files that use the old URL as
      sample data** (`build/checkSw.mjs`, `build/swPolicy.test.ts`) updated too (section 4, items 1 to 3).
- [ ] **All web users are signed out once**, because the refresh cookie belongs to the old backend host. Warn
      users in the maintenance announcement; this is expected and not a bug.
- [ ] **The Razorpay webhook URL is changed in the Razorpay dashboard only** (no code or server setting holds it);
      the webhook secret must still equal `RAZORPAY_WEBHOOK_SECRET` on the new server.

**Why this avoids losing orders:** the old server is down before the last backup, so every order accepted before
the window is inside it, and none is accepted during the window. The cost is a short outage. A longer
two-server overlap would need a data merge and is not recommended.

---

## 10. Smoke tests (new server, before and after cutover)

Run against `NEW_URL`, with a normal browser User-Agent where it matters:

- [ ] `GET /health` returns `{"status":"UP"}` (it checks the database).
- [ ] `GET /api/v1/products?page=0` and `GET /api/v1/crop-listings` return the restored products and crops.
- [ ] `GET /api/v1/admin/insights/signups?from=...&to=...` returns **401** without a token (protected).
- [ ] Web: open the site (after the Vercel change), browse a product, **sign in**, open **My Orders** (the restored
      orders appear), add to cart, run **one small test order** end to end including payment and the
      **webhook confirming it** (the order moves to Confirmed), then check the order timeline.
- [ ] Forgot-password email arrives (Brevo) and the link opens the reset page.
- [ ] Mobile (new build): sign in, browse the Crop Marketplace, add to cart, push token registers (a row appears in
      `device_tokens`), a status change sends a push.
- [ ] Admin: log in, open **Insights** (counts and charts), Orders, Users. The anonymous usage counters start from
      zero in the new database only if you did not restore them; they are part of the backup, so they continue.
- [ ] `usage_daily`, `device_tokens`, `refresh_tokens` have rows (data carried over).
- [ ] Logs: no `ERROR`, and **no password-reset link, email address or token** in any line (the server logs none).
- [ ] After a few hours: the backup job ran, Healthchecks is green, the B2 object is newer than the cutover.

---

## 11. Rehearsal results (what was tested, and what was not)

Tested on throwaway local `mysql:9.4` containers with the same start flags as Railway, the real server jar with
the production profile, and the real `backup.sh` / `reapply-deletions.sh`:

| Test | Result |
|---|---|
| Flyway V1 to V41 on an **empty** MySQL 9.4 **as the limited user** | 36 migrations applied, 39 tables, app `UP` |
| `backup.sh` as the limited **read-only** user (test mode, test key, synthetic data) | OK: 39 tables, Flyway 36 applied |
| Restore by streaming `age -d \| gunzip \| mysql` into an empty database | 39 tables restored, **all counts within the manifest bounds, Flyway versions and checksums identical** |
| `reapply-deletions.sh` (dry run, then `--yes`) as `sgk_app` | Erased the account, kept the order, address replaced by `[removed]` |
| App start on the restored database as `sgk_app` | `UP`, "up to date" at V41 |
| Same, after removing V41 to mimic a V40 backup | V41 applied automatically on first start, app `UP` |
| Variables: boot with each one removed | Section 2 |
| Real production backup: **sha256 of the downloaded file against its manifest** | **Matches** (no key needed) |

**Not done: the restore of the real production backup.** The real decrypt-and-restore was
**not** performed: the key is mounted only by the person running the restore (section 8, step 5), and it was never
read during the audit. Everything above used a **brand-new throwaway key pair and synthetic
data**, so the pipeline is proven, but **the real backup file has not been decrypted by anyone yet**. Do that
yourself during the rehearsal in section 6, step 5: it is the single most important check, because a backup that
has not been restored is not yet a backup.

Also not tested: Railway itself (nothing was created in any Railway account), the Razorpay webhook retry behaviour,
and the Vercel/EAS changes.

---

## 12. Rollback

- **Before step 6 of the cutover** (the new server not yet live): nothing has changed for users except the old
  server being stopped. **Roll back** by redeploying the old server (old project, `sg-krashi-server`, Deploy) and
  re-enabling the old `db-backup` schedule. The old database was never modified.
- **After the clients point at the new server:** decide within the **first hour** and only if the new project has
  accepted **no** orders. To roll back, put `VITE_API_BASE_URL` back to the old URL and redeploy, put the Razorpay
  webhook back, and redeploy the old server. If the new project has already accepted orders, **do not roll back**:
  fix forward, because the old database does not have them.
- **Keep the old project (stopped, with its database) until at least a week after a successful move and before
  the trial's own deadline.** Never run both servers at once: two writers create two histories.
- If the new database is damaged: restore the newest B2 backup into a fresh volume (section 8) and redeploy.

---

## 13. After the move

- [ ] Update `BACKUP.md` (new project, `sgk_backup` user, drill entry) and `DEPLOYMENT.md` (its variable table lists
      `DB_URL` etc. but is out of date: see section 2).
- [ ] Record the restore drill in the `BACKUP.md` drill log.
- [ ] Update the web fallback and test fixtures and the mobile constant (section 4, items 2 to 4), and build the
      mobile app.
- [ ] Make sure no TCP proxy is enabled and no service uses `root`.
- [ ] Remove the Railway SSH key `railway-sg-krashi` from the account and delete the local `~/.ssh/railway_sg_krashi*`
      files (section 8.0).
- [ ] After a week, delete the old project's services (the old Railway data is not needed any more).
- [ ] Re-check the open security items: the Farm-stay public address (server) is unaffected by this move.
