import { useEffect, useRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { PRIORITY_CLASS, PRIORITY_LABEL } from '../lib/format';
import type { Priority, UserRef } from '../lib/types';
import { Icon } from './Icon';

type Variant = 'primary' | 'rose' | 'secondary' | 'ghost' | 'danger';

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-cerulean text-white hover:brightness-110',
  rose: 'bg-rose-btn text-white hover:brightness-110',
  secondary: 'bg-surface text-ink border border-line hover:border-line-strong',
  ghost: 'text-ink-2 hover:bg-chip',
  danger: 'bg-surface text-rose border border-line hover:border-rose',
};

export function Button({ variant = 'secondary', className = '', ...rest }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      type="button"
      {...rest}
      className={`inline-flex min-h-10 items-center justify-center gap-2 rounded-[10px] px-4 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-60 ${VARIANTS[variant]} ${className}`}
    />
  );
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`rounded-[14px] border border-line bg-surface ${className}`}>{children}</section>;
}

export function PriorityBadge({ priority }: { priority: Priority }) {
  return <span className={`rounded-md px-2 py-0.5 text-[11px] font-semibold ${PRIORITY_CLASS[priority]}`}>{PRIORITY_LABEL[priority]}</span>;
}

const AVATAR_TONES = ['bg-accent-soft text-accent', 'bg-rose-soft text-rose', 'bg-warn-soft text-warn', 'bg-ok-soft text-ok', 'bg-chip text-ink-2'];

export function Avatar({ user, size = 28 }: { user: Pick<UserRef, 'id' | 'initials' | 'displayName'> | null; size?: number }) {
  if (!user) return <span className="inline-block rounded-full border border-dashed border-line-strong" style={{ width: size, height: size }} title="Unassigned" />;
  const tone = AVATAR_TONES[parseInt(user.id.replace(/-/g, '').slice(-4), 16) % AVATAR_TONES.length];
  return (
    <span title={user.displayName} className={`inline-flex flex-none items-center justify-center rounded-full font-bold ${tone}`}
      style={{ width: size, height: size, fontSize: Math.max(10, size * 0.38) }}>
      {user.initials}
    </span>
  );
}

export function StatusDot({ color }: { color: string }) {
  return <span className="inline-block h-2 w-2 flex-none rounded-full" style={{ background: color }} />;
}

export function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <label className="flex flex-col gap-1.5 text-[13px] font-semibold text-ink-2">
      {label}
      {children}
      {error && <span className="font-normal text-rose">{error}</span>}
    </label>
  );
}

export const inputClass =
  'min-h-11 rounded-[10px] border border-line-strong bg-surface-2 px-3 text-[15px] font-normal text-ink outline-none placeholder:text-muted focus:border-lane-blue';

export function Modal({ title, onClose, children, width = 'max-w-xl' }: { title: string; onClose: () => void; children: ReactNode; width?: string }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const prev = document.activeElement as HTMLElement | null;
    ref.current?.querySelector<HTMLElement>('input, select, textarea, button')?.focus();
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      prev?.focus();
    };
  }, [onClose]);
  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 p-5" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div ref={ref} role="dialog" aria-modal="true" aria-label={title}
        className={`flex max-h-[92vh] w-full ${width} flex-col gap-4 overflow-auto rounded-2xl border border-line bg-surface p-6`}>
        <div className="flex items-center">
          <h2 className="font-display text-[22px] font-bold">{title}</h2>
          <button type="button" onClick={onClose} aria-label="Close" className="ml-auto flex h-10 w-10 items-center justify-center rounded-[10px] text-ink-2 hover:bg-chip">
            <Icon name="x" />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Empty({ title, body, action }: { title: string; body: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-12 text-center">
      <p className="font-display text-lg font-bold">{title}</p>
      <p className="max-w-sm text-sm text-muted">{body}</p>
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-3 p-10 text-sm text-muted">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-line-strong border-t-lane-blue" />
      {label}
    </div>
  );
}

export function Toggle({ on, onChange, label }: { on: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <button type="button" role="switch" aria-checked={on} aria-label={label} onClick={() => onChange(!on)}
      className={`relative h-[26px] w-11 flex-none rounded-full transition ${on ? 'bg-cerulean' : 'bg-line-strong'}`}>
      <span className={`absolute top-[3px] h-5 w-5 rounded-full bg-white transition-all ${on ? 'left-[21px]' : 'left-[3px]'}`} />
    </button>
  );
}

export function Segmented<T extends string>({ value, options, onChange, label }: { value: T; options: { value: T; label: string }[]; onChange: (v: T) => void; label: string }) {
  return (
    <div role="tablist" aria-label={label} className="flex gap-1 rounded-[10px] bg-chip p-1">
      {options.map((o) => (
        <button key={o.value} type="button" role="tab" aria-selected={value === o.value} onClick={() => onChange(o.value)}
          className={`min-h-8 rounded-[7px] px-3 text-[13px] font-semibold ${value === o.value ? 'bg-surface text-ink' : 'text-ink-2'}`}>
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end gap-3">
      <div className="mr-auto flex flex-col gap-0.5">
        {subtitle && <span className="text-[13px] text-muted">{subtitle}</span>}
        <h1 className="font-display text-[28px] font-bold tracking-tight">{title}</h1>
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}
