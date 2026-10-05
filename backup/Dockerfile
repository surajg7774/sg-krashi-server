# Encrypted offsite MySQL backup job (see ../BACKUP.md).
#
# Based on the official mysql:9.4 image only so that `mysqldump`/`mysql` are the
# same major version as the production server (mysql:9.4) — a mismatched client
# is the classic way to get a dump that won't restore. The image's server entrypoint
# is replaced; nothing here ever starts mysqld.
FROM mysql:9.4

USER root

# Pinned tool versions, each verified against the publisher's SHA-256 at build
# time — the build fails if a download is ever altered or truncated.
ARG AGE_VERSION=1.3.2
ARG AGE_SHA256=cbe24006683f8eb669266162894b9a522a1af52f2665fbc63a4bb032ed26ac10
ARG RCLONE_VERSION=1.75.1
ARG RCLONE_SHA256=982b5aa772841168f8e380f139e9e787b2a105403e32b94da8676a0e1c0a13ab

RUN microdnf install -y unzip gzip tar findutils \
    && (command -v curl >/dev/null 2>&1 || microdnf install -y curl) \
    && microdnf clean all \
    && curl -fsSL -o /tmp/age.tgz "https://github.com/FiloSottile/age/releases/download/v${AGE_VERSION}/age-v${AGE_VERSION}-linux-amd64.tar.gz" \
    && echo "${AGE_SHA256}  /tmp/age.tgz" | sha256sum -c - \
    && tar -xzf /tmp/age.tgz -C /tmp \
    && install -m 0755 /tmp/age/age /usr/local/bin/age \
    && curl -fsSL -o /tmp/rclone.zip "https://github.com/rclone/rclone/releases/download/v${RCLONE_VERSION}/rclone-v${RCLONE_VERSION}-linux-amd64.zip" \
    && echo "${RCLONE_SHA256}  /tmp/rclone.zip" | sha256sum -c - \
    && unzip -q /tmp/rclone.zip -d /tmp \
    && install -m 0755 "/tmp/rclone-v${RCLONE_VERSION}-linux-amd64/rclone" /usr/local/bin/rclone \
    && rm -rf /tmp/age* /tmp/rclone* \
    && age --version && rclone version | head -1 && mysqldump --version

COPY backup.sh reapply-deletions.sh reapply-deletions.sql /opt/backup/
RUN chmod 0755 /opt/backup/backup.sh /opt/backup/reapply-deletions.sh

ENTRYPOINT ["/opt/backup/backup.sh"]
