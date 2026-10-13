export type UserProfile = {
  id: number;
  username: string;
  email: string;
  role: "ADMIN" | "USER";
};

export type DocumentRecord = {
  id: string;
  fileName: string;
  contentType: string;
  size: number;
  uploadedBy: string;
  uploadedDate: string;
  status: string;
};

export type Session = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
};

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export class DocumentsApi {
  constructor(
    private readonly baseUrl: string,
    private readonly fetcher: typeof fetch = globalThis.fetch.bind(globalThis),
  ) {}

  async login(username: string, password: string): Promise<Session> {
    return this.request<Session>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    });
  }

  async register(username: string, email: string, password: string): Promise<UserProfile> {
    return this.request<UserProfile>("/auth/register", {
      method: "POST",
      body: JSON.stringify({ username, email, password }),
    });
  }

  async refresh(refreshToken: string): Promise<Session> {
    return this.request<Session>("/auth/refresh", {
      method: "POST",
      body: JSON.stringify({ refreshToken }),
    });
  }

  async profile(accessToken: string): Promise<UserProfile> {
    return this.request<UserProfile>("/auth/profile", {}, accessToken);
  }

  async logout(accessToken: string, refreshToken: string): Promise<void> {
    await this.request<void>(
      "/auth/logout",
      {
        method: "POST",
        body: JSON.stringify({ refreshToken }),
      },
      accessToken,
    );
  }

  async listDocuments(accessToken: string): Promise<DocumentRecord[]> {
    return this.request<DocumentRecord[]>("/documents", {}, accessToken);
  }

  async upload(accessToken: string, file: File): Promise<DocumentRecord> {
    const body = new FormData();
    body.append("file", file);
    return this.request<DocumentRecord>(
      "/documents/upload",
      { method: "POST", body },
      accessToken,
    );
  }

  async download(accessToken: string, documentId: string): Promise<Blob> {
    const response = await this.send(`/documents/${encodeURIComponent(documentId)}`, {}, accessToken);
    return response.blob();
  }

  async delete(accessToken: string, documentId: string): Promise<void> {
    await this.request<void>(
      `/documents/${encodeURIComponent(documentId)}`,
      { method: "DELETE" },
      accessToken,
    );
  }

  private async request<T>(
    path: string,
    init: RequestInit,
    accessToken?: string,
  ): Promise<T> {
    const response = await this.send(path, init, accessToken);
    if (response.status === 204) return undefined as T;
    return (await response.json()) as T;
  }

  private async send(path: string, init: RequestInit, accessToken?: string): Promise<Response> {
    const headers = new Headers(init.headers);
    if (!(init.body instanceof FormData)) headers.set("Content-Type", "application/json");
    if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
    const response = await this.fetcher(`${this.baseUrl}${path}`, {
      ...init,
      headers,
    });
    if (!response.ok) {
      const body = (await response.json().catch(() => ({}))) as {
        detail?: string;
        message?: string;
        title?: string;
      };
      throw new ApiError(
        body.detail ?? body.message ?? body.title ?? `Request failed (${response.status})`,
        response.status,
      );
    }
    return response;
  }
}

export const api = new DocumentsApi(import.meta.env.VITE_API_BASE_URL ?? "/api");

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return "—";
  if (bytes < 1000) return `${bytes} B`;
  const units = ["KB", "MB", "GB", "TB"];
  let value = bytes / 1000;
  let unit = 0;
  while (value >= 1000 && unit < units.length - 1) {
    value /= 1000;
    unit += 1;
  }
  return `${value.toFixed(value < 10 ? 1 : 0)} ${units[unit]}`;
}

export function fileExtension(fileName: string): string {
  const extension = fileName.split(".").pop()?.toLowerCase();
  return extension && extension !== fileName.toLowerCase() && extension.length <= 5
    ? extension
    : "file";
}
