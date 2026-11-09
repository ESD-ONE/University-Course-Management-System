import { SearchableSelectOption } from './searchable-select.component';

/** How many of these filter values are set (non-empty / true). */
export function countActive(...values: unknown[]): number {
  return values.filter((v) => v !== '' && v !== null && v !== undefined && v !== false).length;
}

/** Case-insensitive "contains" across several fields; an empty query matches everything. */
export function matchesText(query: string, ...fields: (string | number | null | undefined)[]): boolean {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  return fields.some((f) => f !== null && f !== undefined && String(f).toLowerCase().includes(q));
}

/** One option per distinct value in `items`, sorted by label. */
export function uniqueOptions<T>(
  items: T[],
  value: (item: T) => string | null | undefined,
  label: (item: T) => string,
  sublabel?: (item: T) => string | undefined,
): SearchableSelectOption[] {
  const seen = new Map<string, SearchableSelectOption>();
  for (const item of items) {
    const v = value(item);
    if (v === null || v === undefined || v === '' || seen.has(v)) continue;
    seen.set(v, { value: v, label: label(item), sublabel: sublabel?.(item) });
  }
  return [...seen.values()].sort((a, b) => a.label.localeCompare(b.label, undefined, { numeric: true }));
}

/** Options for a numeric column (e.g. "3 years", "120 credits"), in ascending order. */
export function numberOptions(values: (number | null | undefined)[], label: (n: number) => string): SearchableSelectOption[] {
  return [...new Set(values.filter((n): n is number => typeof n === 'number'))]
    .sort((a, b) => a - b)
    .map((n) => ({ value: String(n), label: label(n) }));
}

/** Options from a fixed list of statuses, e.g. ['ACTIVE', 'INACTIVE'] → "Active", "Inactive". */
export function statusOptions(statuses: string[]): SearchableSelectOption[] {
  return statuses.map((s) => ({ value: s, label: s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ') }));
}

export const plural = (n: number, word: string): string => `${n} ${word}${n === 1 ? '' : 's'}`;

export function showingSummary(shown: number, total: number, noun: string): string {
  return shown === total ? `Showing all ${plural(total, noun)}` : `Showing ${shown} of ${plural(total, noun)}`;
}
