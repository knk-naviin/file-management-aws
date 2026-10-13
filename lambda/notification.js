"use strict";

const { SNSClient, PublishCommand } = require("@aws-sdk/client-sns");
const { decodeKey, eventRecords } = require("./common");

const sns = new SNSClient({ region: process.env.AWS_REGION });

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
      const message = JSON.stringify({
        event: "DOCUMENT_DELETED",
        bucket,
        objectKey: key,
        occurredAt: record.time || new Date().toISOString(),
      });
      await sns.send(new PublishCommand({
        TopicArn: process.env.SNS_TOPIC_ARN,
        Subject: "Secure document deleted",
        Message: message,
      }));
      console.info(JSON.stringify({ event: "deletion_notification_published", objectKey: key }));
    } catch (error) {
      console.error(JSON.stringify({ event: "deletion_notification_failed", key, message: error.message }));
      failures.push(key);
    }
  }
  if (failures.length) throw new Error(`Deletion notification failed for ${failures.join(", ")}`);
  return { statusCode: 200 };
};
