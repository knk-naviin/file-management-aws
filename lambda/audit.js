"use strict";

const { createHash } = require("node:crypto");
const { EncryptCommand } = require("@aws-sdk/client-kms");
const { PutObjectCommand, kms, s3, decodeKey, eventRecords } = require("./common");

exports.handler = async (event) => {
  const failures = [];
  for (const record of eventRecords(event)) {
    const bucket = record.s3?.bucket?.name || record.detail?.bucket?.name;
    const encodedKey = record.s3?.object?.key || record.detail?.object?.key;
    if (!bucket || !encodedKey) {
      failures.push("S3 event is missing the bucket or object key");
      continue;
    }
    const key = decodeKey(encodedKey);
    const auditId = createHash("sha256").update(JSON.stringify(record)).digest("hex");
    try {
      const eventDetails = JSON.stringify({
        auditId,
        action: "DOCUMENT_UPLOAD",
        objectKey: key,
        objectVersion: record.s3?.object?.versionId || null,
        occurredAt: record.time || new Date().toISOString(),
      });
      const encrypted = await kms.send(new EncryptCommand({
        KeyId: process.env.KMS_KEY_ARN,
        Plaintext: Buffer.from(eventDetails),
        EncryptionContext: { recordId: auditId, owner: "s3-event" },
      }));
      if (!encrypted.CiphertextBlob) throw new Error("KMS returned no encrypted audit data");
      await s3.send(new PutObjectCommand({
        Bucket: bucket,
        Key: `archives/audit/${auditId}.json.enc`,
        Body: Buffer.from(encrypted.CiphertextBlob).toString("base64"),
        ContentType: "application/octet-stream",
        ServerSideEncryption: "aws:kms",
        SSEKMSKeyId: process.env.KMS_KEY_ARN,
      }));
      console.info(JSON.stringify({ event: "upload_audit_archived", auditId, objectKey: key }));
    } catch (error) {
      console.error(JSON.stringify({ event: "upload_audit_failed", key, message: error.message }));
      failures.push(key);
    }
  }
  if (failures.length) throw new Error(`Upload audit failed for ${failures.join(", ")}`);
  return { statusCode: 200 };
};
