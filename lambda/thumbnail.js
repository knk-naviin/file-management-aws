"use strict";

const { decodeKey, decryptObject, eventRecords, safeSvgText, s3, PutObjectCommand } = require("./common");

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
    try {
      const { plaintext, metadata, documentId } = await decryptObject(bucket, key);
      try {
        const name = metadata["file-name"] || documentId;
        const extension = name.includes(".") ? name.split(".").pop().slice(0, 5).toUpperCase() : "FILE";
        const preview = `<svg xmlns="http://www.w3.org/2000/svg" width="320" height="200" viewBox="0 0 320 200"><rect width="320" height="200" rx="14" fill="#17283a"/><rect x="28" y="27" width="52" height="66" rx="7" fill="#203e4a" stroke="#78b79f"/><path d="M41 47h26M41 56h26M41 65h19" stroke="#9bd0ba" stroke-width="3" stroke-linecap="round"/><text x="54" y="83" text-anchor="middle" font-family="Arial,sans-serif" font-size="8" fill="#9bd0ba">${safeSvgText(extension)}</text><text x="28" y="127" font-family="Arial,sans-serif" font-size="14" fill="#e6edf0">${safeSvgText(name.slice(0, 38))}</text><text x="28" y="151" font-family="Arial,sans-serif" font-size="10" fill="#94a8b5">${plaintext.length.toLocaleString()} bytes · KMS protected</text></svg>`;
        await s3.send(new PutObjectCommand({
          Bucket: bucket,
          Key: `thumbnails/${documentId}.svg`,
          Body: preview,
          ContentType: "image/svg+xml",
          ServerSideEncryption: "aws:kms",
          SSEKMSKeyId: process.env.KMS_KEY_ARN,
        }));
        console.info(JSON.stringify({ event: "thumbnail_generated", documentId, sourceKey: key }));
      } finally {
        plaintext.fill(0);
      }
    } catch (error) {
      console.error(JSON.stringify({ event: "thumbnail_generation_failed", key, message: error.message }));
      failures.push(key);
    }
  }
  if (failures.length) throw new Error(`Thumbnail processing failed for ${failures.join(", ")}`);
  return { statusCode: 200 };
};
