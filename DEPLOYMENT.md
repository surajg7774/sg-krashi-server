# Deploying SG Krashi

This covers what's needed to run the backend (`sg-krashi-server`) against real
production infrastructure. Everything here is driven by environment
variables — no secrets are ever committed to the repo.

## 1. Environment variables

Activate the production profile with `SPRING_PROFILES_ACTIVE=prod`. Every
variable below is **required** when that profile is active — the app will
refuse to start with a clear error if one is missing, rather than silently
running with an empty secret.

| Variable | Example | Notes |
|---|---|---|
| `DB_URL` | `jdbc:mysql://your-db-host:3306/sgkrashi` | Full JDBC URL, not just host/port |
| `DB_USERNAME` | `sgkrashi_app` | |
| `DB_PASSWORD` | | |
| `JWT_SECRET` | | Generate a new one for production — don't reuse the local dev value. At least 256 bits, e.g. `openssl rand -base64 48` |
| `RAZORPAY_KEY_ID` | `rzp_live_...` | **Live** key, not `rzp_test_...` |
| `RAZORPAY_KEY_SECRET` | | |
| `RAZORPAY_WEBHOOK_SECRET` | | See [Razorpay webhook setup](#3-razorpay-webhook) below — this can only be obtained *after* the backend has a real deployed URL |
| `MAIL_HOST` | `smtp.sendgrid.net` | Any standard SMTP provider (SendGrid, AWS SES, Mailgun, ...) |
| `MAIL_PORT` | `587` | |
| `MAIL_USERNAME` | | |
| `MAIL_PASSWORD` | | |
| `CORS_ALLOWED_ORIGINS` | `https://sgkrashi.com,https://sgkrashi-preview.vercel.app` | Comma-separated, no wildcards |
| `STORAGE_PROVIDER` | `cloudinary` | `local`, `s3`, or `cloudinary` — see [Storage setup](#2-object-storage) below. No default; must be set explicitly |

### S3 storage variables (only when `STORAGE_PROVIDER=s3`)

| Variable | Example | Notes |
|---|---|---|
| `S3_BUCKET_NAME` | `sgkrashi-media` | |
| `S3_REGION` | `ap-south-1` | |
| `S3_ACCESS_KEY` | | |
| `S3_SECRET_KEY` | | |
| `S3_ENDPOINT` | *(blank for real AWS S3)* | Set this for a non-AWS but genuinely S3-**protocol**-compatible provider — DigitalOcean Spaces, Backblaze B2, self-hosted MinIO, etc. Cloudinary is NOT one of these (see below) — don't point it here. |
| `S3_PUBLIC_URL_BASE` | `https://sgkrashi-media.s3.ap-south-1.amazonaws.com` | The base URL uploaded images are publicly reachable at — varies by provider, so this is supplied directly rather than guessed from bucket/region |

### Cloudinary storage variables (only when `STORAGE_PROVIDER=cloudinary`)

| Variable | Example | Notes |
|---|---|---|
| `CLOUDINARY_CLOUD_NAME` | `dxyz1234` | From the Cloudinary dashboard |
| `CLOUDINARY_API_KEY` | | |
| `CLOUDINARY_API_SECRET` | | Treat this like any other secret — if it's ever exposed (committed, pasted somewhere public), regenerate it in the Cloudinary dashboard immediately, the same as an AWS key |

## 2. Object storage

The app never writes files to local disk in production — `STORAGE_PROVIDER=s3`
or `STORAGE_PROVIDER=cloudinary` switches `MediaService` off `LocalStorageProvider`
onto one of two real providers, each with its own config block above:

- **`s3`** → `S3StorageProvider`, which works against any provider that speaks
  the actual S3 protocol (real AWS S3, DigitalOcean Spaces, Backblaze B2,
  self-hosted MinIO).
- **`cloudinary`** → `CloudinaryStorageProvider`. Cloudinary is **not**
  S3-protocol-compatible — it has no S3 endpoint at all, despite an earlier
  version of this doc claiming otherwise — so it's a separate provider using
  Cloudinary's own REST upload API and its own credential shape
  (`cloud_name`/`api_key`/`api_secret`, not an access/secret key pair). Public
  read and CORS are Cloudinary account-level/upload-preset settings, not a
  bucket policy — the steps below (1-3) are S3-specific and don't apply to it.

**S3 bucket setup (only for `STORAGE_PROVIDER=s3`):**

1. Create a bucket (private by default is fine — public *read* access is
   granted below, not via ACLs on upload).
2. **Public read access.** `S3StorageProvider` deliberately does not set an
   ACL on individual objects — modern AWS buckets (Object Ownership: "Bucket
   owner enforced", the default since ~2023) reject `PutObject` requests that
   try to set one, and not every S3-compatible provider implements per-object
   ACLs the same way. Instead, grant read access at the **bucket level**.
   For AWS S3, a bucket policy like:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Sid": "PublicReadForMedia",
         "Effect": "Allow",
         "Principal": "*",
         "Action": "s3:GetObject",
         "Resource": "arn:aws:s3:::YOUR_BUCKET_NAME/*"
       }
     ]
   }
   ```

   For DigitalOcean Spaces / Backblaze B2 / others, use that provider's
   equivalent "make bucket contents public" setting.
3. **CORS.** The bucket needs to allow `GET` from your frontend's origin(s)
   so browsers can load images directly from it:

   ```json
   [
     {
       "AllowedOrigins": ["https://sgkrashi.com"],
       "AllowedMethods": ["GET"],
       "AllowedHeaders": ["*"]
     }
   ]
   ```
4. Create an access key scoped to just this bucket (`s3:PutObject`,
   `s3:GetObject`, `s3:DeleteObject`) rather than a full-account key.
5. Set `S3_PUBLIC_URL_BASE` to wherever objects in this bucket are actually
   reachable — this is provider- and configuration-specific (a raw bucket
   URL, a CDN in front of it, a custom domain, etc.), so it's supplied
   directly rather than the app trying to guess it from bucket name + region.

**Cloudinary setup (only for `STORAGE_PROVIDER=cloudinary`):**

1. `CLOUDINARY_CLOUD_NAME`/`CLOUDINARY_API_KEY`/`CLOUDINARY_API_SECRET` come
   straight from the Cloudinary dashboard's Account Details — no bucket,
   region, or endpoint to configure.
2. Uploaded assets are public to read by default under Cloudinary's own
   `secure_url` — no separate "make it public" step like the S3 bucket
   policy above.
3. No `S3_PUBLIC_URL_BASE`-equivalent to set: `CloudinaryStorageProvider`
   stores whatever `secure_url` the upload API returns directly, which is
   already a complete, publicly-reachable HTTPS URL.
4. If this key/secret pair is ever exposed (committed, pasted somewhere
   public, etc.), regenerate it from the Cloudinary dashboard immediately —
   there's no equivalent of scoping an S3 key to one bucket; a Cloudinary
   API key/secret pair has full account access.

Local development is completely unaffected — `STORAGE_PROVIDER` unset (or
`local`) keeps using `LocalStorageProvider`, exactly as before.

## 3. Razorpay webhook

The webhook secret (`RAZORPAY_WEBHOOK_SECRET`) can only be generated *after*
the backend is deployed and reachable at a real URL — Razorpay's dashboard
needs to actually deliver a test webhook to `https://your-api-domain/api/v1/payments/webhook`
to generate one. This is a chicken-and-egg step that has to happen after
first deploy, not before: deploy first with a placeholder value, then update
`RAZORPAY_WEBHOOK_SECRET` once the webhook is registered.

## 4. Building and running the backend

```bash
docker build -t sg-krashi-server .
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=... \
  # ...every variable from the table above...
  sg-krashi-server
```

The `Dockerfile` is a multi-stage build (Maven build stage → slim Alpine JRE
runtime stage) — the shipped image contains only the built jar and a JRE, not
the full Maven toolchain or source tree.

## 5. Database migrations

Flyway runs automatically on boot (`spring.flyway.enabled: true`, inherited
from the base profile) and applies cleanly to a completely empty schema —
every migration since Module 1 was written without depending on any
dev-only seed data existing first. No manual migration step is needed
against a fresh production database.

## 6. Frontend

See `sg-krashi-client/.env.production.example` — the only required build-time
variable is `VITE_API_BASE_URL`, pointed at this backend's real deployed URL.
