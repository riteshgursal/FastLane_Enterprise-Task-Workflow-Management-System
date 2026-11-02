import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { api, errorMessage } from '../lib/api';
import type { Project, StatusCategory, Workflow } from '../lib/types';
import { useToast } from '../context/ToastContext';
import { Button, Card, Empty, PageHeader, Spinner, inputClass } from '../components/ui';

interface DraftStatus { ref: string; name: string; category: StatusCategory; color: string; initial: boolean; taskCount: number }
type Level = 0 | 1 | 2;

export function WorkflowPage() {
  const { projectId = '' } = useParams();
  const qc = useQueryClient();
  const toast = useToast();
  const project = useQuery({ queryKey: ['project', projectId], queryFn: () => api<Project>(`/projects/${projectId}`) });
  const wf = useQuery({ queryKey: ['workflow', projectId], queryFn: () => api<Workflow>(`/projects/${projectId}/workflow`) });
  const [statuses, setStatuses] = useState<DraftStatus[]>([]);
  const [matrix, setMatrix] = useState<Record<string, Level>>({});

  useEffect(() => {
    if (!wf.data) return;
    setStatuses(wf.data.statuses.map((s) => ({ ref: s.id, name: s.name, category: s.category, color: s.color, initial: s.initial, taskCount: s.taskCount })));
    setMatrix(Object.fromEntries(wf.data.transitions.map((t) => [`${t.fromStatusId}>${t.toStatusId}`, (t.requiresApproval ? 2 : 1) as Level])));
  }, [wf.data]);

  const save = useMutation({
    mutationFn: () => api<Workflow>(`/projects/${projectId}/workflow`, {
      method: 'PUT',
      body: {
        statuses: statuses.map(({ ref, name, category, color, initial }) => ({ ref, name, category, color, initial })),
        transitions: Object.entries(matrix).filter(([, v]) => v > 0).map(([k, v]) => {
          const [fromRef, toRef] = k.split('>');
          return { fromRef, toRef, requiresApproval: v === 2 };
        }).filter((t) => statuses.some((s) => s.ref === t.fromRef) && statuses.some((s) => s.ref === t.toRef)),
      },
    }),
    onSuccess: () => { toast('Workflow saved. New rules apply to the next move.'); qc.invalidateQueries({ queryKey: ['workflow', projectId] }); qc.invalidateQueries({ queryKey: ['board', projectId] }); },
    onError: (e) => toast(errorMessage(e)),
  });

  if (project.isLoading || wf.isLoading) return <Spinner />;
  if (project.data?.myRole !== 'MANAGER') return <Empty title="Managers only" body="Ask a project manager to change this workflow." />;

  const cycle = (k: string) => setMatrix((m) => ({ ...m, [k]: (((m[k] ?? 0) + 1) % 3) as Level }));
  const update = (i: number, patch: Partial<DraftStatus>) => setStatuses((s) => s.map((x, j) => (j === i ? { ...x, ...patch } : patch.initial ? { ...x, initial: false } : x)));

  return (
    <>
      <PageHeader title="Workflow" subtitle={`${project.data.name} · project settings`}
        actions={<><Link to={`/projects/${projectId}`} className="flex min-h-10 items-center rounded-[10px] border border-line bg-surface px-3.5 text-sm font-semibold">Back to board</Link>
          <Button variant="primary" onClick={() => save.mutate()} disabled={save.isPending}>Save workflow</Button></>} />
      <div className="flex flex-wrap items-start gap-5">
        <Card className="flex min-w-0 flex-[1_1_340px] flex-col gap-1 p-5">
          <h2 className="text-base font-semibold">Statuses</h2>
          <p className="pb-2 text-[13px] leading-snug text-ink-2">Each status maps to a fixed category so reports stay comparable across projects.</p>
          {statuses.map((s, i) => (
            <div key={s.ref} className="flex flex-wrap items-center gap-2 border-t border-line py-2.5">
              <input type="color" aria-label={`Colour for ${s.name}`} value={s.color} onChange={(e) => update(i, { color: e.target.value })} className="h-7 w-7 cursor-pointer rounded border-0 bg-transparent" />
              <input aria-label="Status name" className={`${inputClass} min-h-9 flex-1 text-sm`} value={s.name} maxLength={40} onChange={(e) => update(i, { name: e.target.value })} />
              <select aria-label="Category" className={`${inputClass} min-h-9 text-xs`} value={s.category} onChange={(e) => update(i, { category: e.target.value as StatusCategory })}>
                <option value="TODO">TODO</option><option value="IN_PROGRESS">IN_PROGRESS</option><option value="DONE">DONE</option><option value="CANCELLED">CANCELLED</option>
              </select>
              <label className="flex items-center gap-1 text-xs text-ink-2"><input type="radio" name="initial" checked={s.initial} onChange={() => update(i, { initial: true })} />Initial</label>
              <button type="button" aria-label={`Remove ${s.name}`} disabled={s.taskCount > 0} title={s.taskCount > 0 ? 'Move its tasks first' : 'Remove'}
                onClick={() => setStatuses((x) => x.filter((_, j) => j !== i))} className="px-1.5 text-muted hover:text-rose disabled:opacity-30">×</button>
              <span className="w-full pl-9 text-[11px] text-muted">{s.taskCount} tasks</span>
            </div>
          ))}
          <Button className="mt-2 border-dashed" onClick={() => setStatuses((s) => [...s, { ref: `new-${Date.now()}`, name: `New status ${s.length + 1}`, category: 'IN_PROGRESS', color: '#8D929E', initial: false, taskCount: 0 }])}>
            + Add status
          </Button>
        </Card>
        <Card className="flex min-w-0 flex-[2_1_520px] flex-col gap-3 p-5">
          <h2 className="text-base font-semibold">Allowed transitions</h2>
          <p className="text-[13px] leading-snug text-ink-2">Click a cell to cycle Blocked → Allowed → Needs approval. The server rejects any other move with <code className="font-mono text-xs">INVALID_TRANSITION</code>.</p>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[540px] border-collapse text-[13px]">
              <thead><tr><th scope="col" className="border-b border-line px-2 py-2.5 text-left font-semibold text-muted">From ↓ To →</th>
                {statuses.map((s) => <th key={s.ref} scope="col" className="border-b border-line px-1.5 py-2.5 font-semibold">{s.name}</th>)}</tr></thead>
              <tbody>
                {statuses.map((from) => (
                  <tr key={from.ref}>
                    <th scope="row" className="border-b border-line px-2 py-1.5 text-left font-semibold whitespace-nowrap">{from.name}</th>
                    {statuses.map((to) => {
                      if (from.ref === to.ref) return <td key={to.ref} className="border-b border-line text-center text-muted" aria-hidden="true">—</td>;
                      const k = `${from.ref}>${to.ref}`;
                      const lv = matrix[k] ?? 0;
                      return (
                        <td key={to.ref} className="border-b border-line p-1">
                          <button type="button" onClick={() => cycle(k)} aria-label={`${from.name} to ${to.name}: ${['blocked', 'allowed', 'needs approval'][lv]}`}
                            className={`min-h-10 w-full rounded-lg border text-xs font-semibold ${lv === 2 ? 'border-warn bg-warn-soft text-warn' : lv === 1 ? 'border-accent bg-accent-soft text-accent' : 'border-line'}`}>
                            {['', 'Allowed', 'Approval'][lv]}
                          </button>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      </div>
    </>
  );
}
