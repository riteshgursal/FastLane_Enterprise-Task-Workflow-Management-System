import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api, download, errorMessage } from '../lib/api';
import { shortDate } from '../lib/format';
import type { Overview } from '../lib/types';
import { useToast } from '../context/ToastContext';
import { useWorkspace } from '../context/WorkspaceContext';
import { Button, Card, PageHeader, Segmented, Spinner } from '../components/ui';

export function ReportsPage() {
  const { workspace } = useWorkspace();
  const ws = workspace!.id;
  const [weeks, setWeeks] = useState<'4' | '6' | '13'>('6');
  const toast = useToast();
  const q = useQuery({ queryKey: ['overview', ws, Number(weeks)], queryFn: () => api<Overview>(`/workspaces/${ws}/reports/overview?weeks=${weeks}`) });
  const o = q.data;
  const maxWeek = Math.max(1, ...(o?.completedPerWeek.map((w) => w.completed) ?? [1]));
  const maxLoad = Math.max(1, ...(o?.workload.map((w) => w.openTasks) ?? [1]));

  return (
    <>
      <PageHeader title="Reports" subtitle={`Analytics for ${workspace!.name}`}
        actions={<>
          <Segmented label="Range" value={weeks} onChange={setWeeks} options={[{ value: '4', label: '4 weeks' }, { value: '6', label: '6 weeks' }, { value: '13', label: 'Quarter' }]} />
          <Button onClick={() => download(`/workspaces/${ws}/reports/overview.csv?weeks=${weeks}`, 'flowlane-report.csv').then(() => toast('CSV downloaded')).catch((e) => toast(errorMessage(e)))}>Export CSV</Button>
          <Button onClick={() => window.print()}>Print or save PDF</Button>
        </>} />
      {q.isLoading || !o ? <Spinner /> : (
        <>
          <div className="grid grid-cols-1 gap-3.5 xl:grid-cols-2">
            <Card className="flex flex-col gap-3.5 p-5">
              <div className="flex items-baseline justify-between"><h2 className="text-base font-semibold">Tasks completed per week</h2><span className="text-xs text-muted">Category DONE</span></div>
              <div className="flex h-52 items-end gap-3 border-b border-line pt-2.5" role="img" aria-label="Bar chart of tasks completed per week">
                {o.completedPerWeek.map((w, i) => (
                  <div key={w.weekStart} className="flex h-full flex-1 flex-col items-center justify-end gap-1.5">
                    <span className="text-xs font-semibold text-ink-2">{w.completed}</span>
                    <span className="w-full max-w-11 rounded-t-md" style={{ height: `${(w.completed / maxWeek) * 160}px`, background: i === o.completedPerWeek.length - 1 ? '#FF2E7E' : '#1DA1D8' }} />
                  </div>
                ))}
              </div>
              <div className="flex gap-3">{o.completedPerWeek.map((w) => <span key={w.weekStart} className="flex-1 text-center text-[11px] text-muted">{shortDate(w.weekStart)}</span>)}</div>
            </Card>
            <Card className="flex flex-col gap-3.5 p-5">
              <div className="flex items-baseline justify-between"><h2 className="text-base font-semibold">Workload by assignee</h2><span className="text-xs text-muted">Open tasks</span></div>
              {o.workload.length === 0 && <p className="text-sm text-muted">No open tasks are assigned yet.</p>}
              {o.workload.map((w) => (
                <div key={w.userId} className="flex items-center gap-3 text-sm">
                  <span className="w-28 truncate text-ink-2">{w.displayName}</span>
                  <span className="h-5.5 flex-1 rounded-md bg-chip"><span className="block h-5.5 rounded-md" style={{ width: `${(w.openTasks / maxLoad) * 100}%`, background: w.openTasks > 4 ? '#FF2E7E' : '#1DA1D8' }} /></span>
                  <span className="w-6 text-right font-semibold">{w.openTasks}</span>
                </div>
              ))}
              <p className="text-xs text-muted">Rose marks anyone with more than 4 open tasks.</p>
            </Card>
          </div>
          <div className="grid grid-cols-[repeat(auto-fit,minmax(240px,1fr))] gap-3.5">
            {o.projects.map((p) => {
              const risk = p.overdue > 0;
              return (
                <Link key={p.id} to={`/projects/${p.id}`} className="flex flex-col gap-2 rounded-[14px] border border-line bg-surface px-5 py-4">
                  <span className="flex items-center gap-2 font-semibold"><span className="h-2.5 w-2.5 rounded-[3px]" style={{ background: p.color }} />{p.name}</span>
                  <span className={`self-start rounded-md px-2 py-0.5 text-xs font-semibold ${risk ? 'bg-rose-soft text-rose' : 'bg-ok-soft text-ok'}`}>{risk ? 'At risk' : 'On track'}</span>
                  <span className="text-[13px] text-ink-2">{p.done} of {p.total} done{risk ? ` · ${p.overdue} overdue` : ''}</span>
                </Link>
              );
            })}
          </div>
        </>
      )}
    </>
  );
}
