import { HttpErrorResponse } from '@angular/common/http';

export const CHART_COLORS = ['#3450D6', '#7A4FD6', '#0E8F8F', '#B4791F', '#1F8A5F', '#C2410C', '#8991A8'];

export interface Segment {
  label: string;
  value: number;
  color: string;
}

/** conic-gradient for the CSS-only .donut-chart (styles.scss). */
export function donutGradient(segments: Segment[]): string {
  const total = segments.reduce((a, s) => a + s.value, 0);
  if (!total) return 'conic-gradient(var(--line) 0deg 360deg)';
  let angle = 0;
  const stops = segments.map((s) => {
    const start = angle;
    angle += (s.value / total) * 360;
    return `${s.color} ${start}deg ${angle}deg`;
  });
  return `conic-gradient(${stops.join(', ')})`;
}

export function withColors(points: { label: string; value: number }[]): Segment[] {
  return points.map((p, i) => ({ ...p, color: CHART_COLORS[i % CHART_COLORS.length] }));
}

export function percentOf(value: number, max: number): string {
  return max > 0 ? `${Math.max(2, Math.round((value / max) * 100))}%` : '0%';
}

/** The backend's {message} if present, else the given fallback. */
export function errorMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Could not reach the server — check your connection.';
    const msg = err.error?.message;
    if (typeof msg === 'string' && msg) return msg;
    if (err.status === 429) {
      const wait = Number(err.headers.get('Retry-After'));
      return wait > 0
        ? `Too many requests — please wait ${wait} second${wait === 1 ? '' : 's'} and try again.`
        : 'Too many requests — please wait a moment and try again.';
    }
  }
  return fallback;
}

/** The machine-readable code from a 409 (e.g. COURSE_FULL), if any. */
export function errorCode(err: unknown): string | null {
  return err instanceof HttpErrorResponse && typeof err.error?.code === 'string' ? err.error.code : null;
}

export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

const PREVIEWABLE: Record<string, string> = {
  pdf: 'application/pdf',
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  gif: 'image/gif',
  webp: 'image/webp',
  svg: 'image/svg+xml',
  txt: 'text/plain',
  mp4: 'video/mp4',
};

/** True if the browser can show this file itself (PDF, image, text, video). */
export function canPreview(fileName: string | null | undefined): boolean {
  const ext = (fileName ?? '').split('.').pop()?.toLowerCase() ?? '';
  return ext in PREVIEWABLE;
}

/** Opens the file in a new tab when the browser can render it, otherwise downloads it.
 * The type is taken from the file name because some endpoints serve every file as
 * application/octet-stream, which a browser would only ever download. */
export function openBlob(blob: Blob, fileName: string): void {
  const ext = fileName.split('.').pop()?.toLowerCase() ?? '';
  const type = PREVIEWABLE[ext];
  if (!type) {
    saveBlob(blob, fileName);
    return;
  }
  const url = URL.createObjectURL(new Blob([blob], { type }));
  // No 'noopener' here: with it window.open always returns null, which would look like a
  // blocked pop-up and trigger the download fallback as well. A blob: URL is same-origin.
  const win = window.open(url, '_blank');
  if (!win) saveBlob(blob, fileName);
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** A small emoji for a file, by extension — used in attachment chips. */
export function fileIcon(fileName: string | null | undefined): string {
  const ext = (fileName ?? '').split('.').pop()?.toLowerCase() ?? '';
  if (ext === 'pdf') return '📕';
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg'].includes(ext)) return '🖼️';
  if (['doc', 'docx', 'odt', 'txt'].includes(ext)) return '📄';
  if (['ppt', 'pptx', 'key'].includes(ext)) return '📊';
  if (['xls', 'xlsx', 'csv'].includes(ext)) return '📈';
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return '🗜️';
  if (['mp4', 'mov', 'webm'].includes(ext)) return '🎬';
  return '📎';
}

export function initials(name: string | null | undefined): string {
  return (name ?? '?')
    .split(' ')
    .filter(Boolean)
    .map((p) => p[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();
}

/** A tiny toast holder: components keep one of these and render `.toast`. */
export class Toaster {
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor(private set: (t: { message: string; error: boolean } | null) => void) {}

  show(message: string, error = false): void {
    if (this.timer) clearTimeout(this.timer);
    this.set({ message, error });
    this.timer = setTimeout(() => this.set(null), 3200);
  }
}
