import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useProjects, useUnreadCount } from '../lib/queries';
import type { Task } from '../lib/types';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { useWorkspace } from '../context/WorkspaceContext';
import { Icon, type IconName } from '../components/Icon';
import { Logo } from '../components/Logo';
import { NewTaskModal } from '../components/NewTaskModal';
import { NewProjectModal } from '../components/NewProjectModal';
import { NewWorkspaceForm } from '../components/NewWorkspaceForm';
import { Avatar, Modal, Spinner, StatusDot } from '../components/ui';

const NAV: { to: string; label: string; icon: IconName }[] = [
  { to: '/', label: 'Dashboard', icon: 'dashboard' },
  { to: '/my-tasks', label: 'My tasks', icon: 'tasks' },
  { to: '/calendar', label: 'Calendar', icon: 'calendar' },
  { to: '/reports', label: 'Reports', icon: 'reports' },
  { to: '/members', label: 'Members', icon: 'members' },
  { to: '/notifications', label: 'Notifications', icon: 'bell' },
  { to: '/settings', label: 'Settings', icon: 'settings' },
];

const navClass = ({ isActive }: { isActive: boolean }) =>
  `flex min-h-10 items-center gap-2.5 rounded-[9px] px-2.5 text-sm ${isActive ? 'bg-accent-soft font-semibold text-accent' : 'font-medium text-ink-2 hover:bg-chip'}`;

export function AppShell() {
  const { user, logout } = useAuth();
  const { resolved, setTheme } = useTheme();
  const { workspace, workspaces, select, loading } = useWorkspace();
  const { data: projects = [] } = useProjects(workspace?.id);
  const { data: unread } = useUnreadCount();
  const [wsOpen, setWsOpen] = useState(false);
  const [modal, setModal] = useState<'task' | 'project' | 'workspace' | null>(null);
  const [navOpen, setNavOpen] = useState(false);
  const [q, setQ] = useState('');
  const navigate = useNavigate();
  const location = useLocation();
  const searchRef = useRef<HTMLInputElement>(null);

  useEffect(() => { setNavOpen(false); setWsOpen(false); setQ(''); }, [location.pathname]);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement).tagName;
      if (e.key === '/' && !['INPUT', 'TEXTAREA', 'SELECT'].includes(tag)) { e.preventDefault(); searchRef.current?.focus(); }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  const results = useQuery({
    queryKey: ['search', workspace?.id, q],
    queryFn: () => api<Task[]>(`/workspaces/${workspace!.id}/search?q=${encodeURIComponent(q)}`),
    enabled: !!workspace && q.trim().length >= 2,
  });

  if (loading) return <Spinner label="Loading your workspaces" />;
  if (!workspace) {
    return (
      <div className="flex min-h-full items-center justify-center p-6">
        <div className="flex w-full max-w-md flex-col gap-6">
          <Logo />
          <div>
            <h1 className="font-display text-3xl font-bold tracking-tight">Create your workspace</h1>
            <p className="mt-1 text-sm text-ink-2">A workspace holds your team’s projects. You can invite people next.</p>
          </div>
          <NewWorkspaceForm />
        </div>
      </div>
    );
  }

  const unreadCount = unread?.count ?? 0;

  return (
    <div className="flex min-h-full flex-col lg:flex-row">
      <aside className={`${navOpen ? 'flex' : 'hidden'} w-full flex-col gap-0.5 border-line bg-side px-3.5 py-4 lg:sticky lg:top-0 lg:flex lg:h-screen lg:w-[248px] lg:flex-none lg:border-r lg:overflow-y-auto`}>
        <div className="hidden px-2 pb-4 lg:block"><Logo /></div>
        <div className="relative mb-3.5">
          <button type="button" onClick={() => setWsOpen(!wsOpen)} aria-expanded={wsOpen}
            className="flex min-h-12 w-full items-center gap-2.5 rounded-[10px] border border-line bg-surface-2 px-2.5 text-left">
            <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-accent-soft text-xs font-bold text-accent">{workspace.name[0]}</span>
            <span className="flex flex-1 flex-col"><span className="text-[11px] text-muted">Workspace</span><span className="text-sm font-semibold">{workspace.name}</span></span>
            <Icon name="chevron" size={16} />
          </button>
          {wsOpen && (
            <div className="absolute top-[54px] right-0 left-0 z-30 flex flex-col gap-0.5 rounded-xl border border-line bg-surface p-1.5 shadow-2xl">
              {workspaces.map((w) => (
                <button key={w.id} type="button" onClick={() => { select(w.id); setWsOpen(false); navigate('/'); }}
                  className={`flex min-h-11 items-center gap-2.5 rounded-lg px-2.5 text-left text-sm ${w.id === workspace.id ? 'bg-accent-soft' : 'hover:bg-chip'}`}>
                  <span className="flex h-6 w-6 items-center justify-center rounded-md bg-accent-soft text-[11px] font-bold text-accent">{w.name[0]}</span>
                  <span className="flex-1">{w.name}</span>
                  <span className="text-xs text-muted">{w.projectCount} projects</span>
                </button>
              ))}
              <button type="button" onClick={() => { setWsOpen(false); setModal('workspace'); }}
                className="min-h-11 border-t border-line px-2.5 text-left text-sm font-semibold text-accent">+ Create workspace</button>
            </div>
          )}
        </div>
        {NAV.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.to === '/'} className={navClass}>
            <Icon name={n.icon} />
            <span className="flex-1">{n.label}</span>
            {n.to === '/notifications' && unreadCount > 0 && (
              <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-rose-btn px-1.5 text-[11px] font-bold text-white">{unreadCount}</span>
            )}
          </NavLink>
        ))}
        <div className="flex items-center justify-between px-2.5 pt-4 pb-1.5">
          <span className="text-xs font-semibold text-muted">Projects</span>
          <button type="button" onClick={() => setModal('project')} aria-label="New project" className="flex h-7 w-7 items-center justify-center rounded-md text-muted hover:bg-chip">
            <Icon name="plus" size={16} />
          </button>
        </div>
        {projects.map((p) => (
          <NavLink key={p.id} to={`/projects/${p.id}`} className={navClass}>
            <span className="h-2.5 w-2.5 rounded-[3px]" style={{ background: p.color }} />
            <span className="flex-1 truncate">{p.name}</span>
            <span className="font-mono text-[11px] text-muted">{p.key}</span>
          </NavLink>
        ))}
        {projects.length === 0 && <p className="px-2.5 text-xs text-muted">No projects yet.</p>}
        <div className="mt-auto flex items-center gap-2 border-t border-line pt-4">
          <button type="button" onClick={() => navigate('/settings')} className="flex min-h-12 flex-1 items-center gap-2.5 rounded-[10px] px-1.5 text-left">
            {user && <Avatar user={user} size={34} />}
            <span className="flex flex-col"><span className="text-sm font-semibold">{user?.displayName}</span><span className="text-xs capitalize text-muted">{workspace.myRole.toLowerCase()}</span></span>
          </button>
          <button type="button" onClick={() => logout().then(() => navigate('/login'))} aria-label="Sign out"
            className="flex h-10 w-10 items-center justify-center rounded-[10px] border border-line text-ink-2 hover:bg-chip">
            <Icon name="logout" size={16} />
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex flex-wrap items-center gap-3 border-b border-line bg-bg/90 px-5 py-3 backdrop-blur lg:px-8">
          <button type="button" className="flex h-11 w-11 items-center justify-center rounded-xl border border-line lg:hidden" aria-label="Menu" onClick={() => setNavOpen(!navOpen)}>
            <Icon name="menu" />
          </button>
          <span className="lg:hidden"><Logo size={28} /></span>
          <div className="relative ml-auto min-w-[200px] flex-[0_1_340px]">
            <label className="flex min-h-11 items-center gap-2 rounded-xl border border-line bg-surface px-3 text-muted">
              <Icon name="search" size={16} />
              <span className="sr-only">Search tasks</span>
              <input ref={searchRef} type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search tasks or keys"
                className="min-w-0 flex-1 bg-transparent text-sm text-ink outline-none" />
              <kbd className="rounded border border-line px-1.5 font-mono text-[11px]">/</kbd>
            </label>
            {q.trim().length >= 2 && (
              <div className="absolute top-[50px] right-0 left-0 z-40 flex flex-col rounded-xl border border-line bg-surface p-1.5 shadow-2xl">
                {results.data?.map((t) => (
                  <button key={t.id} type="button" onClick={() => navigate(`/tasks/${t.id}`)}
                    className="flex min-h-10 items-center gap-2.5 rounded-lg px-2.5 text-left text-sm hover:bg-chip">
                    <span className="w-16 font-mono text-xs text-muted">{t.key}</span>
                    <span className="flex-1 truncate">{t.title}</span>
                    <StatusDot color={t.statusColor} />
                  </button>
                ))}
                {results.data?.length === 0 && <p className="p-3 text-sm text-muted">No tasks match “{q}”.</p>}
                {results.isLoading && <p className="p-3 text-sm text-muted">Searching…</p>}
              </div>
            )}
          </div>
          <button type="button" onClick={() => setTheme(resolved === 'dark' ? 'light' : 'dark')}
            aria-label={resolved === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
            className="flex h-11 w-11 items-center justify-center rounded-xl border border-line bg-surface text-ink-2">
            <Icon name={resolved === 'dark' ? 'sun' : 'moon'} />
          </button>
          <button type="button" onClick={() => navigate('/notifications')} aria-label={`Notifications, ${unreadCount} unread`}
            className="relative flex h-11 w-11 items-center justify-center rounded-xl border border-line bg-surface text-ink-2">
            <Icon name="bell" />
            {unreadCount > 0 && <span className="absolute top-2.5 right-2.5 h-2.5 w-2.5 rounded-full border-2 border-surface bg-rose-btn" />}
          </button>
          <button type="button" onClick={() => setModal('task')} className="min-h-11 rounded-xl bg-rose-btn px-4 text-sm font-semibold text-white hover:brightness-110">
            + New task
          </button>
        </header>
        <main className="flex min-w-0 flex-1 flex-col gap-5 px-5 py-6 lg:px-8">
          <Outlet />
        </main>
      </div>

      {modal === 'task' && <NewTaskModal onClose={() => setModal(null)} defaultProjectId={location.pathname.match(/^\/projects\/([^/]+)/)?.[1]} />}
      {modal === 'project' && <NewProjectModal workspaceId={workspace.id} onClose={() => setModal(null)} />}
      {modal === 'workspace' && (
        <Modal title="Create workspace" onClose={() => setModal(null)} width="max-w-md">
          <NewWorkspaceForm onDone={() => { setModal(null); navigate('/'); }} />
        </Modal>
      )}
    </div>
  );
}
