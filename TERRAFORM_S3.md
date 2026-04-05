# Infrastructure Setup: S3 Ingest for SC Collector

To allow the mobile app to securely write to S3 without baking AWS credentials into the APK, we use the **Pre-signed URL** pattern.

## 1. AWS Infrastructure (Terraform)
Create a file named `ingest.tf` and run `terraform apply`. This creates a private bucket and an IAM user that the *server* (not the app) will use to generate upload links.

```hcl
provider "aws" {
  region = "us-east-1" # Change to your region
}

resource "aws_s3_bucket" "sc_ingest" {
  bucket = "shadowcheck-ingest-raw-data"
}

# Block all public access
resource "aws_s3_bucket_public_access_block" "sc_ingest_block" {
  bucket = aws_s3_bucket.sc_ingest.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# IAM User for the Web Server to generate URLs
resource "aws_iam_user" "sc_server_user" {
  name = "shadowcheck-server-ingest-manager"
}

resource "aws_iam_user_policy" "sc_server_policy" {
  name = "S3IngestPolicy"
  user = aws_iam_user.sc_server_user.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action   = ["s3:PutObject"]
        Effect   = "Allow"
        Resource = "${aws_s3_bucket.sc_ingest.arn}/*"
      }
    ]
  })
}
```

## 2. Server-Side Logic (shadowcheck-web)
Add this route to your Express/Node.js server. The mobile app will call this first.

```typescript
import { S3Client, PutObjectCommand } from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";

const s3Client = new S3Client({ region: "us-east-1" });

app.post("/api/v1/ingest/request-upload", async (req, res) => {
  const { api_key, fileName, case_id } = req.body;

  // 1. Validate API Key
  if (api_key !== process.env.SHADOWCHECK_API_KEY) {
    return res.status(401).send("Unauthorized");
  }

  // 2. Generate unique S3 key
  const s3Key = `raw/${case_id || 'default'}/${Date.now()}_${fileName}`;

  // 3. Create Pre-signed URL (valid for 15 minutes)
  const command = new PutObjectCommand({
    Bucket: "shadowcheck-ingest-raw-data",
    Key: s3Key,
    ContentType: "application/x-sqlite3"
  });

  const uploadUrl = await getSignedUrl(s3Client, command, { expiresIn: 900 });

  res.json({ uploadUrl, s3Key });
});
```

## 3. Mobile App Integration
I have already implemented the base uploader. If you move to this pre-signed pattern, the `ShadowCheckUploader.java` will be updated to:
1. `POST` to `/api/v1/ingest/request-upload` to get the URL.
2. `PUT` the binary file directly to the returned `uploadUrl`.

This is the industry standard for secure mobile-to-cloud data ingestion.
