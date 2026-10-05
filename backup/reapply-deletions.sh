#!/usr/bin/env bash
# Re-applies account deletions after a restore. See ../BACKUP.md ("Deleted accounts").
#
#   reapply-deletions.sh [--yes] <manifest.json | id[,id,...]>
#
# Reads the "anonymized_user_ids" list from a backup manifest (take the NEWEST
# manifest - its list covers every deletion up to that backup), or takes ids
# directly. DRY RUN unless --yes is given. Prints ids only, never emails/names.
# Connection: MYSQL_HOST, MYSQL_PORT (3306), MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE (railway).
set -Eeuo pipefail

APPLY=0
if [ "${1:-}" = "--yes" ]; then APPLY=1; shift; fi
SRC="${1:-}"
if [ -z "$SRC" ]; then sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 2; fi
for v in MYSQL_HOST MYSQL_USER MYSQL_PASSWORD; do
  if [ -z "${!v:-}" ]; then echo "missing variable: $v" >&2; exit 2; fi
done
export MYSQL_PWD="$MYSQL_PASSWORD"
DB="${MYSQL_DATABASE:-railway}"
SQL_FILE="$(dirname "$0")/reapply-deletions.sql"
m() { mysql -h "$MYSQL_HOST" -P "${MYSQL_PORT:-3306}" -u "$MYSQL_USER" -N -B "$DB" "$@"; }

if [ -f "$SRC" ]; then
  IDS="$(sed -n 's/.*"anonymized_user_ids": *\[\([0-9, ]*\)\].*/\1/p' "$SRC" | tr -d ' ')"
else
  IDS="$(printf '%s' "$SRC" | tr -d ' ')"
fi
if [ -z "$IDS" ]; then echo "no anonymized user ids to process"; exit 0; fi

[ "$APPLY" -eq 1 ] && echo "APPLYING deletions" || echo "DRY RUN (add --yes to apply)"
done_n=0; skipped_n=0
IFS=',' read -ra LIST <<< "$IDS"
for id in "${LIST[@]}"; do
  case "$id" in ''|*[!0-9]*) echo "ignoring non-numeric id '$id'"; continue ;; esac
  email="$(m -e "SELECT email FROM users WHERE id = $id")"
  if [ -z "$email" ]; then echo "id $id: not present in this database - skipped"; skipped_n=$((skipped_n+1)); continue; fi
  case "$email" in deleted-*@deleted.invalid) echo "id $id: already anonymized - skipped"; skipped_n=$((skipped_n+1)); continue ;; esac
  if [ "$APPLY" -ne 1 ]; then echo "id $id: would be erased/anonymized"; continue; fi
  {
    printf 'SET @uid = %s;\nSET @orig_email = (SELECT email FROM users WHERE id = @uid);\nSTART TRANSACTION;\n' "$id"
    cat "$SQL_FILE"
    printf 'COMMIT;\n'
  } | m   # an error aborts the session, so the open transaction rolls back
  echo "id $id: erased/anonymized"; done_n=$((done_n+1))
done
echo "done: ${done_n} applied, ${skipped_n} skipped"
