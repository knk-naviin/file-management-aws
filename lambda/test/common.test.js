"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { decodeKey, eventRecords, safeSvgText } = require("../common");

test("normalizes S3 direct-invocation and EventBridge records", () => {
  const direct = { Records: [{ eventName: "ObjectCreated:Put" }] };
  const eventBridge = {
    id: "event-1",
    time: "2026-10-13T01:00:00Z",
    detail: { bucket: { name: "documents" }, object: { key: "documents/one" } },
  };

  assert.equal(eventRecords(direct).length, 1);
  assert.deepEqual(eventRecords(eventBridge), [{ detail: eventBridge.detail, time: eventBridge.time }]);
});

test("decodes URL-encoded S3 event keys and escapes preview content", () => {
  assert.equal(decodeKey("documents/Annual+Report%20Q4.pdf"), "documents/Annual Report Q4.pdf");
  assert.equal(safeSvgText(`<a x="1">&'`), "&lt;a x=&quot;1&quot;&gt;&amp;&apos;");
});
