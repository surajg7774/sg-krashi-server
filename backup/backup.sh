#!/usr/bin/env bash
# SG Krashi - encrypted offsite MySQL backup. Runs as a Railway cron service
# (see ../BACKUP.md): dump -> verify -> gzip -> age-encrypt (public key only) ->
# upload to Backblaze B2 -> ping Healthchecks. Any failure exits non-zero and
# pings Healthchecks /fail, so a bad night is loud, never silent.
#
# Never prints secrets: MYSQL_PWD / B2 keys are only passed through the
# environment, and log lines mention variable *names* and step names only.
set -Eeuo pipefail

STEP="starting"
TMP=""
FAIL_PINGED=0

# To stderr, deliberately: the ERR trap runs inside the failing command's context, so a
# `cmd > /dev/null` redirect on that command would otherwise swallow the failure message.
log() { printf '[backup] %s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >&2; }

# hc <path-suffix> [body] - best-effort Healthchecks ping; never fails the job by itself.
hc() {
  [ -n "${HEALTHCHECKS_PING_URL:-}" ] || return 0
  curl -fsS -m 15 --retry 3 -o /dev/null --data-raw "${2:-}" "${HEALTHCHECKS_PING_URL%/}${1:-}" \
    || log "WARNING: could not reach Healthchecks (${1:-success})"
}

cleanup() { if [ -n "$TMP" ]; then rm -rf "$TMP"; fi; }

on_error() {
  local code=$?
  log "BACKUP FAILED during step: ${STEP} (exit ${code})"
  if [ "$FAIL_PINGED" -eq 0 ]; then
    FAIL_PINGED=1
    hc /fail "step=${STEP} exit=${code}"
  fi
  exit "$code"
}
trap on_error ERR
trap cleanup EXIT

# ---------------------------------------------------------------- configuration
STEP="checking configuration"
for v in MYSQL_HOST MYSQL_USER MYSQL_PASSWORD AGE_PUBLIC_KEY; do
  if [ -z "${!v:-}" ]; then log "missing required variable: $v"; false; fi
done
case "$AGE_PUBLIC_KEY" in
  age1*) ;;
  AGE-SECRET-KEY*) log "AGE_PUBLIC_KEY holds a PRIVATE key - refusing to run. Set the public key (starts with age1) and remove the private one."; false ;;
  *) log "AGE_PUBLIC_KEY is not an age public key (should start with age1)"; false ;;
esac

MYSQL_PORT="${MYSQL_PORT:-3306}"
DB="${MYSQL_DATABASE:-railway}"
PREFIX="${BACKUP_PREFIX:-mysql/daily}"
export MYSQL_PWD="$MYSQL_PASSWORD"
unset MYSQL_PASSWORD

if [ -n "${BACKUP_LOCAL_DIR:-}" ]; then
  log "TEST MODE: writing to local directory instead of B2"
else
  for v in B2_ENDPOINT B2_BUCKET B2_KEY_ID B2_APPLICATION_KEY; do
    if [ -z "${!v:-}" ]; then log "missing required variable: $v"; false; fi
  done
  B2_REGION="$(printf '%s' "$B2_ENDPOINT" | sed -E 's#^https?://s3\.([^.]+)\.backblazeb2\.com.*#\1#')"
  # rclone reads its S3 remote definition from these (no config file, no secrets on disk).
  export RCLONE_CONFIG_B2_TYPE=s3 RCLONE_CONFIG_B2_PROVIDER=Other
  export RCLONE_CONFIG_B2_ENDPOINT="$B2_ENDPOINT" RCLONE_CONFIG_B2_REGION="$B2_REGION"
  export RCLONE_CONFIG_B2_ACCESS_KEY_ID="$B2_KEY_ID" RCLONE_CONFIG_B2_SECRET_ACCESS_KEY="$B2_APPLICATION_KEY"
  export RCLONE_CONFIG_B2_NO_CHECK_BUCKET=true
fi
if [ -z "${HEALTHCHECKS_PING_URL:-}" ]; then
  log "WARNING: HEALTHCHECKS_PING_URL is not set - a missed or failed backup will NOT alert anyone"
fi

TMP="$(mktemp -d)"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
NAME="mysql-${DB}-${TS}.sql.gz.age"
MANIFEST="mysql-${DB}-${TS}.manifest.json"

q() { mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -N -B "$DB" -e "$1"; }

# "table<TAB>exact row count" for every base table, sorted by name (C locale, so joins line up).
counts() {
  local tables sql="" t
  tables="$(q "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' ORDER BY table_name")"
  if [ -z "$tables" ]; then log "no tables found in database ${DB}"; false; fi
  while IFS= read -r t; do
    [ -n "$sql" ] && sql="${sql} UNION ALL "
    sql="${sql}SELECT '${t}', COUNT(*) FROM \`${t}\`"
  done <<< "$tables"
  q "$sql" | LC_ALL=C sort -t $'\t' -k1,1
}

# ---------------------------------------------------------------- run
hc /start
STEP="connecting to MySQL"
q "SELECT 1" > /dev/null
log "connected to ${MYSQL_HOST}:${MYSQL_PORT}/${DB} (server $(q 'SELECT VERSION()'))"

STEP="counting rows (before dump)"
counts > "$TMP/counts_before.tsv"
TABLE_COUNT="$(wc -l < "$TMP/counts_before.tsv" | tr -d ' ')"

STEP="dumping database"
mysqldump -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
  --single-transaction --routines --triggers --events --hex-blob \
  --default-character-set=utf8mb4 --no-tablespaces --set-gtid-purged=OFF \
  "$DB" > "$TMP/dump.sql"

STEP="verifying the dump is complete"
if [ "$(tail -c 400 "$TMP/dump.sql" | grep -c -- '-- Dump completed')" -lt 1 ]; then log "dump has no completion trailer"; false; fi
DUMPED_TABLES="$(grep -c '^CREATE TABLE' "$TMP/dump.sql" || true)"
if [ "$DUMPED_TABLES" -ne "$TABLE_COUNT" ]; then log "dump has ${DUMPED_TABLES} CREATE TABLE statements, database has ${TABLE_COUNT} tables"; false; fi

STEP="counting rows (after dump)"
counts > "$TMP/counts_after.tsv"

STEP="reading Flyway state and anonymized accounts"
SERVER_VERSION="$(q 'SELECT VERSION()')"
DUMP_TOOL="$(mysqldump --version | sed 's/  */ /g')"
q "SELECT version, checksum, success FROM flyway_schema_history ORDER BY installed_rank" > "$TMP/flyway.tsv"
FLYWAY_OK="$(awk -F'\t' '$3==1' "$TMP/flyway.tsv" | wc -l | tr -d ' ')"
FLYWAY_FAILED="$(awk -F'\t' '$3!=1' "$TMP/flyway.tsv" | wc -l | tr -d ' ')"
# IDs only - never names or emails. Accounts deleted through DELETE /customers/me are the ones with the placeholder email.
ANON_IDS="$(q "SELECT id FROM users WHERE email LIKE 'deleted-%@deleted.invalid' ORDER BY id" | paste -sd, -)"

STEP="compressing and encrypting"
gzip -9 -c "$TMP/dump.sql" | age -r "$AGE_PUBLIC_KEY" -o "$TMP/$NAME"
rm -f "$TMP/dump.sql"   # the plaintext never leaves this temp dir, and not for long
BYTES="$(wc -c < "$TMP/$NAME" | tr -d ' ')"
if [ "$BYTES" -lt 512 ]; then log "encrypted backup is suspiciously small (${BYTES} bytes)"; false; fi
SHA256="$(sha256sum "$TMP/$NAME" | cut -d' ' -f1)"

STEP="writing manifest"
{
  printf '{\n'
  printf '  "format": 1,\n'
  printf '  "created_at_utc": "%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf '  "database": "%s",\n' "$DB"
  printf '  "server_version": "%s",\n' "$SERVER_VERSION"
  printf '  "dump_tool": "%s",\n' "$DUMP_TOOL"
  printf '  "backup_file": "%s",\n' "$NAME"
  printf '  "backup_bytes": %s,\n' "$BYTES"
  printf '  "backup_sha256": "%s",\n' "$SHA256"
  printf '  "encryption": "age, public-key recipient only; the private key is not on this service",\n'
  printf '  "note_counts": "rows were counted just before and just after the dump; a restored table must have a count between the two",\n'
  printf '  "tables": {\n'
  LC_ALL=C join -t $'\t' "$TMP/counts_before.tsv" "$TMP/counts_after.tsv" \
    | awk -F'\t' '{ printf "%s    \"%s\": {\"before\": %s, \"after\": %s}", (NR>1?",\n":""), $1, $2, $3 } END { printf "\n" }'
  printf '  },\n'
  printf '  "flyway": {\n'
  printf '    "applied_success": %s,\n' "$FLYWAY_OK"
  printf '    "failed": %s,\n' "$FLYWAY_FAILED"
  printf '    "migrations": [\n'
  awk -F'\t' '{ v=($1=="NULL"?"null":"\"" $1 "\""); c=($2=="NULL"?"null":$2); printf "%s      {\"version\": %s, \"checksum\": %s, \"success\": %s}", (NR>1?",\n":""), v, c, ($3==1?"true":"false") } END { printf "\n" }' "$TMP/flyway.tsv"
  printf '    ]\n'
  printf '  },\n'
  printf '  "anonymized_user_ids": [%s]\n' "$ANON_IDS"
  printf '}\n'
} > "$TMP/$MANIFEST"

upload() { # upload <local-file> <name>
  if [ -n "${BACKUP_LOCAL_DIR:-}" ]; then
    mkdir -p "$BACKUP_LOCAL_DIR" && cp "$1" "$BACKUP_LOCAL_DIR/$2"
  else
    # --no-check-dest / --s3-no-head / no bucket check: the job's key is WRITE-ONLY, so it
    # cannot list or read back; skipping those calls is what lets such a key work.
    rclone copyto "$1" "b2:${B2_BUCKET}/${PREFIX}/$2" --no-check-dest --s3-no-head --s3-no-check-bucket --retries 3 --low-level-retries 5
  fi
}

STEP="uploading backup"
upload "$TMP/$NAME" "$NAME"
STEP="uploading manifest"
upload "$TMP/$MANIFEST" "$MANIFEST"   # last: a manifest existing means the backup before it is complete

log "OK ${NAME} (${BYTES} bytes, ${TABLE_COUNT} tables, flyway ${FLYWAY_OK} applied, ${FLYWAY_FAILED} failed)"
STEP="reporting success"
hc "" "ok file=${NAME} bytes=${BYTES} tables=${TABLE_COUNT}"
