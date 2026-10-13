"use strict";

const { KMSClient, DecryptCommand } = require("@aws-sdk/client-kms");
const { S3Client, GetObjectCommand, PutObjectCommand } = require("@aws-sdk/client-s3");

const region = process.env.AWS_REGION;
const kms = new KMSClient({ region });
const s3 = new S3Client({ region });

function decodeKey(key) {
  return decodeURIComponent(key.replace(/\+/g, " "));
}

function safeSvgText(value) {
  return String(value).replace(/[&<>"']/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&apos;",
  })[character]);
}

function eventRecords(event) {
  if (Array.isArray(event.Records)) return event.Records;
  if (event.detail) return [{ detail: event.detail, time: event.time }];
  return [];
}

async function decryptObject(bucket, key) {
  const result = await s3.send(new GetObjectCommand({ Bucket: bucket, Key: key }));
  const metadata = result.Metadata || {};
  const documentId = metadata["document-id"];
  const owner = metadata.owner;
  const wrappedKey = metadata["encrypted-data-key"];
  if (!documentId || !owner || !wrappedKey) {
    throw new Error(`Object ${key} is missing the required encrypted document metadata`);
  }

  const decrypted = await kms.send(new DecryptCommand({
    CiphertextBlob: Buffer.from(wrappedKey, "base64"),
    EncryptionContext: { recordId: documentId, owner },
  }));
  if (!decrypted.Plaintext) throw new Error("KMS returned no plaintext data key");
  try {
    const ciphertext = await result.Body.transformToByteArray();
    if (ciphertext.length <= 12 + 16) throw new Error(`Encrypted object ${key} is incomplete`);
    const nonce = Buffer.from(ciphertext.subarray(0, 12));
    const body = Buffer.from(ciphertext.subarray(12));
    const authTag = body.subarray(body.length - 16);
    const encryptedBody = body.subarray(0, body.length - 16);
    const decipher = require("node:crypto").createDecipheriv("aes-256-gcm", decrypted.Plaintext, nonce);
    decipher.setAuthTag(authTag);
    const plaintext = Buffer.concat([decipher.update(encryptedBody), decipher.final()]);
    return { plaintext, metadata, documentId, owner };
  } finally {
    decrypted.Plaintext.fill(0);
  }
}

module.exports = { decodeKey, decryptObject, eventRecords, safeSvgText, s3, kms, PutObjectCommand };
