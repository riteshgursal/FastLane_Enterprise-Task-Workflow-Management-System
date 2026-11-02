import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { api, errorMessage } from '../lib/api';
import type { Theme, User } from '../lib/types';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { useToast } from '../context/ToastContext';
import { Button, Card, Field, Modal, PageHeader, Toggle, inputClass } from '../components/ui';

const THEMES: { value: Theme; label: string; bg: string; side: string; sf: string }[] = [
  { value: 'dark', label: 'Dark', bg: '#0A0A0B', side: '#0F1012', sf: '#16171A' },
  { value: 'light', label: 'Light', bg: '#F3F5F8', side: '#FFFFFF', sf: '#FFFFFF' },
  { value: 'system', label: 'System', bg: '#0A0A0B', side: '#16171A', sf: '#FFFFFF' },
];

const PREFS: { key: keyof User; label: string; desc: string }[] = [
  { key: 'notifyAssigned', label: 'Assigned to me', desc: 'When someone gives you a task' },
  { key: 'notifyMentions', label: 'Mentions', desc: 'When you’re @mentioned in a comment' },
  { key: 'notifyDue', label: 'Due-date reminders', desc: 'The day before, plus daily overdue alerts' },
  { key: 'notifyApprovals', label: 'Approval requests', desc: 'When a transition needs your sign-off' },
  { key: 'dailyDigest', label: 'Daily digest', desc: 'One summary email each morning' },
];

const ZONES = ['UTC', 'Asia/Kolkata', 'Europe/London', 'Europe/Berlin', 'America/New_York', 'America/Los_Angeles', 'Asia/Singapore', 'Australia/Sydney'];

export function SettingsPage() {
  const { user, setUser } = useAuth();
  const { theme, setTheme } = useTheme();
  const toast = useToast();
  const [name, setName] = useState(user?.displayName ?? '');
  const [tz, setTz] = useState(user?.timezone ?? 'UTC');
  const [pwOpen, setPwOpen] = useState(false);

  const patch = useMutation({
    mutationFn: (body: Partial<User>) => api<User>('/users/me', { method: 'PATCH', body }),
    onSuccess: (u) => setUser(u),
    onError: (e) => toast(errorMessage(e)),
  });

  if (!user) return null;

  return (
    <>
      <PageHeader title="Settings" subtitle="Your profile and preferences" />
      <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-2">
        <Card className="flex flex-col gap-3.5 p-5">
          <h2 className="text-base font-semibold">Appearance</h2>
          <div className="grid grid-cols-3 gap-2.5">
            {THEMES.map((t) => (
              <button key={t.value} type="button" aria-pressed={theme === t.value}
                onClick={() => { setTheme(t.value); patch.mutate({ theme: t.value }); toast(`${t.label} theme applied`); }}
                className={`flex flex-col gap-2 rounded-xl border-2 bg-surface-2 p-2 text-left ${theme === t.value ? 'border-lane-blue' : 'border-line'}`}>
                <span className="flex h-16 gap-1 rounded-lg p-1.5" style={{ background: t.bg }}>
                  <span className="w-1/4 rounded" style={{ background: t.side }} />
                  <span className="flex flex-1 flex-col gap-1"><span className="h-2 w-3/5 rounded-sm bg-lane-blue" /><span className="flex-1 rounded" style={{ background: t.sf }} /><span className="h-2 w-1/3 rounded-sm bg-rose-neon" /></span>
                </span>
                <span className="text-[13px] font-semibold">{t.label}</span>
              </button>
            ))}
          </div>
          <p className="text-[13px] text-muted">System follows your device. Your choice is saved to your profile.</p>
        </Card>
        <Card className="flex flex-col gap-1 p-5">
          <h2 className="pb-1.5 text-base font-semibold">Email notifications</h2>
          {PREFS.map((p) => (
            <div key={p.key} className="flex min-h-14 items-center gap-3.5 border-t border-line">
              <span className="flex flex-1 flex-col"><span className="text-sm font-semibold">{p.label}</span><span className="text-xs text-muted">{p.desc}</span></span>
              <Toggle label={p.label} on={Boolean(user[p.key])} onChange={(v) => patch.mutate({ [p.key]: v } as Partial<User>)} />
            </div>
          ))}
        </Card>
        <Card className="flex flex-col gap-3.5 p-5">
          <h2 className="text-base font-semibold">Profile</h2>
          <Field label="Full name"><input className={inputClass} value={name} onChange={(e) => setName(e.target.value)} maxLength={80} /></Field>
          <Field label="Email"><input className={inputClass} value={user.email} disabled /></Field>
          <Field label="Time zone">
            <select className={inputClass} value={tz} onChange={(e) => setTz(e.target.value)}>
              {[...new Set([tz, ...ZONES])].map((z) => <option key={z}>{z}</option>)}
            </select>
          </Field>
          <Button variant="primary" className="self-start" onClick={() => patch.mutate({ displayName: name, timezone: tz }, { onSuccess: () => toast('Profile saved') })}>Save profile</Button>
        </Card>
        <Card className="flex flex-col gap-3 p-5">
          <h2 className="text-base font-semibold">Security</h2>
          <div className="flex min-h-12 items-center justify-between gap-2.5 border-t border-line text-sm">
            <span>Password</span><Button onClick={() => setPwOpen(true)}>Change</Button>
          </div>
          <div className="flex min-h-12 items-center justify-between gap-2.5 border-t border-line text-sm">
            <span>Active sessions <span className="text-muted">· {user.activeSessions}</span></span>
            <Button variant="danger" onClick={() => api('/auth/sessions/revoke-others', { method: 'POST' })
              .then(() => api<User>('/users/me')).then((u) => { setUser(u); toast('Signed out of all other devices'); }).catch((e) => toast(errorMessage(e)))}>
              Sign out others
            </Button>
          </div>
        </Card>
      </div>
      {pwOpen && <PasswordModal onClose={() => setPwOpen(false)} />}
    </>
  );
}

function PasswordModal({ onClose }: { onClose: () => void }) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [error, setError] = useState<string | null>(null);
  const { logout } = useAuth();
  const toast = useToast();
  return (
    <Modal title="Change password" onClose={onClose} width="max-w-md">
      <form className="flex flex-col gap-4" onSubmit={async (e) => {
        e.preventDefault();
        if (next.length < 12) return setError('Use at least 12 characters.');
        try {
          await api('/users/me/password', { body: { currentPassword: current, newPassword: next } });
          toast('Password changed. Sign in again on your devices.');
          await logout();
        } catch (err) { setError(errorMessage(err)); }
      }}>
        <Field label="Current password"><input type="password" className={inputClass} value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" /></Field>
        <Field label="New password" error={error ?? undefined}><input type="password" className={inputClass} value={next} onChange={(e) => setNext(e.target.value)} autoComplete="new-password" /></Field>
        <div className="flex justify-end gap-2"><Button onClick={onClose}>Cancel</Button><Button type="submit" variant="primary">Change password</Button></div>
      </form>
    </Modal>
  );
}
