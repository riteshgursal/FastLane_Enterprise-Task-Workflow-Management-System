import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { relativeTime, shortDate } from '../lib/format';
import type { HistoryEntry, Overview, Page, Task } from '../lib/types';
import { useWorkspace } from '../context/WorkspaceContext';
import { Card, Empty, PageHeader, Spinner } from '../components/ui';

const CATS = [
  { key: 'TODO', label: 'To do', color: 'var(--fl-mu)' },
  { key: 'IN_PROGRESS', label: 'In progress', color: '#1DA1D8' },
  { key: 'DONE', label: 'Done', color: 'var(--fl-ok)' },
  { key: 'CANCELLED', label: 'Cancelled', color: 'var(--fl-bd2)' },
] as const;

export function DashboardPage() {
  const { workspace } = useWorkspace();
  const ws = workspace!.id;
  const overview = useQuery({ queryKey: ['overview', ws, 6], queryFn: () => api<Overview>(`/workspaces/${ws}/reports/overview?weeks=6`) });
  const mine = useQuery({ queryKey: ['tasks', 'mine', ws], queryFn: () => api<Page<Task>>(`/workspaces/${ws}/my-tasks?size=50&sort=dueDate`) });
  const activity = useQuery({ queryKey: ['activity', ws], queryFn: () => api<HistoryEntry[]>(`/workspaces/${ws}/activity`) });
  const approvals = useQuery({ queryKey: ['approvals', ws], queryFn: () => api<Task[]>(`/workspaces/${ws}/approvals`) });

  if (overview.isLoading) return <Spinner />;
  const o = overview.data;
  const total = o ? Object.values(o.byCategory).reduce((a, b) => a + b, 0) : 0;
  const upcoming = (mine.data?.content ?? []).filter((t) => t.category !== 'DONE' && t.category !== 'CANCELLED' && t.dueDate).slice(0, 6);
  const delta = o ? o.completedThisWeek - o.completedLastWeek : 0;

  return (
    <>
      <PageHeader title="Dashboard" subtitle={`${workspace!.name} · ${workspace!.projectCount} projects`} />
      {o && (
        <div className="grid grid-cols-[repeat(auto-fit,minmax(180px,1fr))] gap-3.5">
          <Metric label="Open tasks" value={o.byCategory.TODO} note="Category TODO" />
          <Metric label="In progress" value={o.byCategory.IN_PROGRESS} note="Includes review" tone="text-lane-blue" />
          <Metric label="Done this week" value={o.completedThisWeek} note={`${delta >= 0 ? '+' : ''}${delta} vs last week`} tone="text-ok" />
          <Metric label="Overdue" value={o.overdue} note="Across your projects" tone="text-rose" border={o.overdue > 0 ? 'border-rose' : undefined} />
        </div>
      )}
      <div className="grid grid-cols-1 gap-3.5 xl:grid-cols-2">
        <Card className="flex flex-col gap-4 p-5">
          <h2 className="text-base font-semibold">Project progress</h2>
          {o?.projects.length === 0 && <Empty title="No projects yet" body="Create a project from the sidebar to start planning work." />}
          {o?.projects.map((p) => {
            const pct = p.total ? Math.round((p.done / p.total) * 100) : 0;
            return (
              <Link key={p.id} to={`/projects/${p.id}`} className="flex flex-col gap-2">
                <span className="flex justify-between text-sm"><span>{p.name} <span className="font-mono text-[11px] text-muted">{p.key}</span></span><span className="font-semibold">{pct}%</span></span>
                <span className="block h-2 rounded-full bg-chip"><span className="block h-2 rounded-full" style={{ width: `${pct}%`, background: p.color }} /></span>
              </Link>
            );
          })}
          {o && total > 0 && (
            <div className="flex flex-col gap-2.5 border-t border-line pt-4">
              <span className="text-[13px] font-semibold text-ink-2">Tasks by status category</span>
              <div className="flex h-3 overflow-hidden rounded-full bg-chip">
                {CATS.map((c) => <span key={c.key} style={{ width: `${(o.byCategory[c.key] / total) * 100}%`, background: c.color }} />)}
              </div>
              <div className="flex flex-wrap gap-3.5 text-xs text-ink-2">
                {CATS.map((c) => <span key={c.key} className="flex items-center gap-1.5"><span className="h-2 w-2 rounded-sm" style={{ background: c.color }} />{c.label} {o.byCategory[c.key]}</span>)}
              </div>
            </div>
          )}
        </Card>
        <Card className="flex flex-col p-5">
          <div className="flex items-center justify-between pb-2"><h2 className="text-base font-semibold">My upcoming deadlines</h2><Link to="/my-tasks" className="text-[13px] font-semibold text-accent">View all</Link></div>
          {upcoming.length === 0 && <Empty title="Nothing due" body="Tasks assigned to you with a due date show up here." />}
          {upcoming.map((t) => (
            <Link key={t.id} to={`/tasks/${t.id}`} className="flex min-h-11 items-center gap-3 border-b border-line text-sm last:border-0">
              <span className="w-16 font-mono text-xs text-muted">{t.key}</span>
              <span className="flex-1 truncate">{t.title}</span>
              <span className={`rounded-md px-2 py-0.5 text-xs font-semibold ${t.overdue ? 'bg-rose-soft text-rose' : 'bg-chip text-ink-2'}`}>
                {t.overdue ? 'Overdue · ' : ''}{shortDate(t.dueDate)}
              </span>
            </Link>
          ))}
        </Card>
      </div>
      <div className="grid grid-cols-1 gap-3.5 xl:grid-cols-2">
        <Card className="flex flex-col p-5">
          <h2 className="pb-2 text-base font-semibold">Recent activity</h2>
          {activity.data?.length === 0 && <Empty title="No activity yet" body="Changes to tasks appear here as your team works." />}
          {activity.data?.slice(0, 8).map((a) => (
            <div key={a.id} className="flex min-h-11 items-center gap-3 border-b border-line text-sm last:border-0">
              <span className="flex h-7 w-7 flex-none items-center justify-center rounded-full bg-accent-soft text-[11px] font-bold text-accent">{a.actorInitials}</span>
              <span className="flex-1 text-ink-2"><b className="font-semibold text-ink">{a.actorName}</b> {a.detail}{a.taskKey && <> · <Link to={`/tasks/${a.taskId}`} className="font-mono text-xs text-accent">{a.taskKey}</Link></>}</span>
              <span className="text-xs text-muted">{relativeTime(a.at)}</span>
            </div>
          ))}
        </Card>
        <Card className="flex flex-col gap-2.5 p-5">
          <h2 className="text-base font-semibold">Waiting for your approval</h2>
          {approvals.data?.length === 0 && <p className="text-sm text-muted">Nothing waiting. Approvals you need to give show up here.</p>}
          {approvals.data?.map((t) => (
            <Link key={t.id} to={`/tasks/${t.id}`} className="flex items-center gap-3 rounded-[10px] bg-warn-soft px-3.5 py-3 text-sm">
              <span className="font-mono text-xs text-warn">{t.key}</span>
              <span className="flex-1 truncate">{t.title}</span>
              <span className="text-xs font-semibold text-warn">{t.statusName} → {t.pendingApprovalStatusName}</span>
            </Link>
          ))}
        </Card>
      </div>
    </>
  );
}

function Metric({ label, value, note, tone = 'text-ink', border }: { label: string; value: number; note: string; tone?: string; border?: string }) {
  return (
    <div className={`flex flex-col gap-1.5 rounded-[14px] border bg-surface px-5 py-4 ${border ?? 'border-line'}`}>
      <span className="text-[13px] text-ink-2">{label}</span>
      <span className={`font-display text-[34px] font-bold ${tone}`}>{value}</span>
      <span className="text-xs text-muted">{note}</span>
    </div>
  );
}
