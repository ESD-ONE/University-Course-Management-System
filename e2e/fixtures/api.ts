import { APIRequestContext, Browser, request } from '@playwright/test';
import { accessTokenFrom } from './login';
import { Role, sessionFile } from './users';

/** Calls the gateway (/api/...) as one signed-in user, for setting up test data. */
export class Api {
  constructor(private readonly http: APIRequestContext, private readonly token: string) {}

  get<T = any>(path: string): Promise<T> {
    return this.send('GET', path);
  }

  post<T = any>(path: string, data?: unknown): Promise<T> {
    return this.send('POST', path, data);
  }

  put<T = any>(path: string, data?: unknown): Promise<T> {
    return this.send('PUT', path, data);
  }

  patch<T = any>(path: string, data?: unknown): Promise<T> {
    return this.send('PATCH', path, data);
  }

  delete<T = any>(path: string): Promise<T> {
    return this.send('DELETE', path);
  }

  /** Uploads one file as a multipart form, the way the app's upload screens do. */
  upload<T = any>(path: string, file: { name: string; mimeType: string; buffer: Buffer }): Promise<T> {
    return this.send('POST', path, undefined, { file });
  }

  /**
   * Sends one request. A 429 is retried after the wait the gateway asks for: setup calls
   * come in bursts, and the gateway rate-limits each user as it does in production.
   */
  private async send<T>(
    method: string,
    path: string,
    data?: unknown,
    multipart?: Record<string, { name: string; mimeType: string; buffer: Buffer }>,
  ): Promise<T> {
    for (let attempt = 0; ; attempt++) {
      const response = await this.http.fetch(path, {
        method,
        data,
        multipart,
        headers: { Authorization: `Bearer ${this.token}` },
      });
      if (response.status() === 429 && attempt < 5) {
        const seconds = Number(response.headers()['retry-after'] ?? '1');
        await new Promise((resolve) => setTimeout(resolve, (seconds || 1) * 1000));
        continue;
      }
      if (!response.ok()) {
        throw new ApiError(method, path, response.status(), await response.text());
      }
      const text = await response.text();
      return (text ? JSON.parse(text) : undefined) as T;
    }
  }
}

export class ApiError extends Error {
  constructor(method: string, path: string, readonly status: number, readonly body: string) {
    super(`${method} ${path} -> ${status}: ${body}`);
  }
}

/**
 * An {@link Api} for a role, using that role's saved session to get a fresh token. Access
 * tokens last five minutes, so one is fetched per test rather than kept for the run.
 */
export async function apiFor(browser: Browser, role: Role, baseURL: string): Promise<Api> {
  const context = await browser.newContext({ storageState: sessionFile(role), baseURL, ignoreHTTPSErrors: true });
  try {
    const page = await context.newPage();
    const token = await accessTokenFrom(page, () => page.goto('/app/dashboard'));
    return new Api(await request.newContext({ baseURL, ignoreHTTPSErrors: true }), token);
  } finally {
    await context.close();
  }
}
