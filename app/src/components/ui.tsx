import { ReactNode, ButtonHTMLAttributes, InputHTMLAttributes, forwardRef } from 'react';
import { Loader2 } from 'lucide-react';

export const Card = ({ children, className = '' }: { children: ReactNode; className?: string }) =>
  <div className={`rounded-2xl border border-line bg-surface p-4 ${className}`}>{children}</div>;

export function Button({ variant = 'solid', loading, className = '', children, ...p }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'solid' | 'ghost' | 'danger'; loading?: boolean }) {
  const v = variant === 'solid' ? 'bg-accent text-white' : variant === 'danger' ? 'border border-bad text-bad' : 'border border-accent text-accent';
  return <button {...p} disabled={p.disabled || loading} className={`inline-flex items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-semibold disabled:opacity-40 ${v} ${className}`}>
    {loading && <Loader2 className="h-4 w-4 animate-spin" />}{children}</button>;
}
export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input(p, ref) {
  return <input ref={ref} {...p} className={`w-full rounded-xl border border-line px-3 py-2.5 text-sm outline-none focus:border-accent ${p.className ?? ''}`} />;
});
export const Field = ({ label, error, children }: { label: string; error?: string; children: ReactNode }) =>
  <label className="block"><span className="mb-1 block text-xs text-mute">{label}</span>{children}{error && <span className="mt-1 block text-xs text-bad">{error}</span>}</label>;
export const Spinner = () => <div className="flex justify-center p-8"><Loader2 className="h-6 w-6 animate-spin text-mute" /></div>;
export const Note = ({ children, tone = 'bad' }: { children: ReactNode; tone?: 'bad' | 'ok' | 'mute' }) =>
  children ? <p className={`text-sm ${tone === 'bad' ? 'text-bad' : tone === 'ok' ? 'text-ok' : 'text-mute'}`}>{children}</p> : null;
export const Chip = ({ children }: { children: ReactNode }) => <span className="rounded-full bg-page px-2.5 py-0.5 text-xs font-semibold">{children}</span>;
export const Row = ({ children }: { children: ReactNode }) => <div className="flex items-center justify-between gap-3 border-t border-line py-2.5 first:border-t-0">{children}</div>;

export function Tabs({ tabs, value, onChange }: { tabs: [string, string][]; value: string; onChange(v: string): void }) {
  return <div className="flex gap-1 overflow-x-auto rounded-xl bg-surface p-1">{tabs.map(([k, l]) =>
    <button key={k} onClick={() => onChange(k)} className={`flex-1 whitespace-nowrap rounded-lg px-3 py-2 text-sm font-semibold ${value === k ? 'bg-accent text-white' : 'text-mute'}`}>{l}</button>)}</div>;
}
export function Stat({ label, value, sub }: { label: string; value: ReactNode; sub?: string }) {
  return <div className="rounded-xl border border-line bg-surface p-3"><div className="text-xl font-extrabold">{value}</div><div className="text-xs text-mute">{label}</div>{sub && <div className="text-[11px] text-mute">{sub}</div>}</div>;
}
/** Simple horizontal bar chart (no chart library needed). */
export function Bars({ items, format }: { items: { label: string; value: number }[]; format?: (n: number) => string }) {
  const max = Math.max(1, ...items.map((i) => i.value));
  return <div className="grid gap-2">{items.map((i) => (
    <div key={i.label}><div className="flex justify-between text-xs"><span className="truncate pr-2">{i.label}</span><span className="text-mute">{format ? format(i.value) : i.value}</span></div>
      <div className="h-2 rounded-full bg-page"><div className="h-2 rounded-full bg-accent" style={{ width: `${(i.value / max) * 100}%` }} /></div></div>))}</div>;
}
