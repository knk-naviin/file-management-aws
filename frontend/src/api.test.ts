import { describe, expect, it, vi } from "vitest";
import { DocumentsApi, fileExtension, formatBytes } from "./api";

describe("DocumentsApi", () => {
  it("sends bearer authorization for protected APIs", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify([]), { status: 200 }),
    );
    const client = new DocumentsApi("/api", fetcher);

    await client.listDocuments("access-token");

    expect(fetcher.mock.calls[0][0]).toBe("/api/documents");
    expect(new Headers(fetcher.mock.calls[0][1]?.headers).get("Authorization"))
      .toBe("Bearer access-token");
  });

  it("does not set a JSON content type for multipart uploads", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ id: "doc-1" }), { status: 201 }),
    );
    const client = new DocumentsApi("/api", fetcher);

    await client.upload("access-token", new File(["content"], "report.pdf"));

    const init = fetcher.mock.calls[0][1];
    expect(new Headers(init?.headers).has("Content-Type")).toBe(false);
    expect(init?.body).toBeInstanceOf(FormData);
  });

  it("surfaces structured API errors", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ detail: "Token expired" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      }),
    );
    const client = new DocumentsApi("/api", fetcher);

    await expect(client.listDocuments("expired")).rejects.toMatchObject({
      message: "Token expired",
      status: 401,
    });
  });
});

describe("formatBytes and fileExtension", () => {
  it("formats file sizes using decimal units", () => {
    expect(formatBytes(0)).toBe("0 B");
    expect(formatBytes(1536)).toBe("1.5 KB");
    expect(formatBytes(2_500_000)).toBe("2.5 MB");
  });

  it("handles extensionless and uppercase file names", () => {
    expect(fileExtension("Quarterly Report.PDF")).toBe("pdf");
    expect(fileExtension("README")).toBe("file");
  });
});
