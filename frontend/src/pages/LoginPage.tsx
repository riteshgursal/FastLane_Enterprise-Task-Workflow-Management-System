import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError, errorMessage } from '../lib/api';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { LogoMark } from '../components/Logo';
import { Icon } from '../components/Icon';
import { Button, Field, inputClass } from '../components/ui';

export function LoginPage({ mode }: { mode: 'signin' | 'register' }) {
  const { user, login, register } = useAuth();
  const { resolved, setTheme } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const next = (location.state as { from?: string } | null)?.from ?? '/';
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={next} replace />;

  const submit = async () => {
    setError(null);
    setFieldErrors({});
    if (mode === 'register' && password.length < 12) return setFieldErrors({ password: 'Use at least 12 characters.' });
    setBusy(true);
    try {
      if (mode === 'signin') await login(email, password);
      else await register(name, email, password);
      navigate(next, { replace: true });
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors.length) setFieldErrors(Object.fromEntries(e.fieldErrors.map((f) => [f.field, f.message])));
      else setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-full flex-wrap">
      <section className="flex flex-[1_1_440px] flex-col justify-between gap-12 bg-[#0A0A0B] p-10 text-[#F4F5F7] lg:p-14">
        <span className="flex items-center gap-3"><LogoMark size={40} /><span className="font-display text-2xl font-bold tracking-tight">Flowlane</span></span>
        <div className="flex max-w-md flex-col gap-4">
          <h1 className="font-display text-[44px] leading-[1.06] font-bold tracking-tight">Every task, in its lane.</h1>
          <p className="text-base leading-relaxed text-[#B4B8C2]">Workspaces, boards, calendars and approvals, with workflows your server actually enforces.</p>
        </div>
        <div className="flex flex-col gap-3" aria-hidden="true">
          <div className="flex items-center gap-2.5"><span className="h-2.5 w-56 rounded-full bg-lane-blue" /><span className="h-2.5 w-2.5 rounded-full bg-lane-blue" /></div>
          <div className="flex items-center gap-2.5"><span className="h-2.5 w-36 rounded-full bg-rose-neon" /><span className="h-2.5 w-2.5 rounded-full bg-rose-neon" /></div>
          <div className="flex items-center gap-2.5"><span className="h-2.5 w-72 rounded-full bg-[#2A2C33]" /><span className="h-2.5 w-2.5 rounded-full bg-[#2A2C33]" /></div>
        </div>
      </section>
      <section className="flex flex-[1_1_440px] flex-col p-6 lg:p-8">
        <div className="flex justify-end">
          <button type="button" onClick={() => setTheme(resolved === 'dark' ? 'light' : 'dark')} aria-label="Toggle theme"
            className="flex h-11 w-11 items-center justify-center rounded-xl border border-line bg-surface text-ink-2">
            <Icon name={resolved === 'dark' ? 'sun' : 'moon'} />
          </button>
        </div>
        <div className="flex flex-1 items-center justify-center">
          <form className="flex w-full max-w-sm flex-col gap-5" onSubmit={(e) => { e.preventDefault(); submit(); }}>
            <div>
              <h2 className="font-display text-3xl font-bold tracking-tight">{mode === 'signin' ? 'Sign in' : 'Create your account'}</h2>
              <p className="mt-1 text-sm text-ink-2">{mode === 'signin' ? 'Welcome back. Use your work email.' : 'Start a workspace for your team in a minute.'}</p>
            </div>
            {mode === 'register' && (
              <Field label="Full name" error={fieldErrors.displayName}>
                <input className={inputClass} value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" required />
              </Field>
            )}
            <Field label="Work email" error={fieldErrors.email}>
              <input type="email" className={inputClass} value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@company.com" autoComplete="email" required />
            </Field>
            <Field label="Password" error={fieldErrors.password}>
              <input type="password" className={inputClass} value={password} onChange={(e) => setPassword(e.target.value)}
                placeholder={mode === 'register' ? 'At least 12 characters' : ''} autoComplete={mode === 'signin' ? 'current-password' : 'new-password'} required />
            </Field>
            {error && <p role="alert" className="rounded-lg bg-rose-soft px-3 py-2 text-sm text-rose">{error}</p>}
            <Button type="submit" variant="primary" className="min-h-12" disabled={busy}>
              {busy ? 'Please wait…' : mode === 'signin' ? 'Sign in' : 'Create account'}
            </Button>
            <p className="text-center text-sm text-ink-2">
              {mode === 'signin' ? 'New to Flowlane? ' : 'Already have an account? '}
              <Link to={mode === 'signin' ? '/register' : '/login'} state={location.state} className="font-semibold text-accent">
                {mode === 'signin' ? 'Create an account' : 'Sign in'}
              </Link>
            </p>
          </form>
        </div>
      </section>
    </div>
  );
}
