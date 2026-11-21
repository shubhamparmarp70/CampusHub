export const money = (n: number | string | null | undefined) => '₹' + Number(n ?? 0).toFixed(2).replace(/\.00$/, '');
/** The API sends times in UTC. When a value has no zone marker, treat it as UTC so it shows in the viewer's local time. */
export const when = (s?: string) => {
  if (!s) return '';
  const iso = /(Z|[+-]\d{2}:?\d{2})$/.test(s) ? s : s + 'Z';
  return new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
};
export const SANDBOX = import.meta.env.VITE_SANDBOX !== 'false';
export const FLOW: Record<string, string[]> = {
  FOOD: ['PLACED', 'ACCEPTED', 'PROCESSING', 'READY', 'COLLECTED'],
  DESK: ['PLACED', 'ACCEPTED', 'PROCESSING', 'PRINTING', 'BINDING', 'READY', 'COLLECTED'],
};
