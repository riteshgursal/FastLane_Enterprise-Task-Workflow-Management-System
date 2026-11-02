import { useState, type DragEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api, errorMessage } from '../lib/api';
import { shortDate } from '../lib/format';
import type { Board, Page, Priority, Project, Task } from '../lib/types';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { NewTaskModal } from '../components/NewTaskModal';
import { TaskTable } from '../components/TaskTable';
import { Avatar, Button, Empty, PageHeader, PriorityBadge, Segmented, Spinner } from '../components/ui';

type ViewMode = 'board' | 'list' | 'calendar';

const PRIORITIES: (Priority | 'ALL')[] = ['ALL', 'URGENT', 'HIGH', 'MEDIUM', 'LOW'];

export function BoardPage() {
  const { projectId = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const view: ViewMode = params.get('view') === 'list' ? 'list' : 'board';
  const [prio, setPrio] = useState<Priority | 'ALL'>('ALL');
  const [newIn, setNewIn] = useState<string | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [dragId, setDragId] = useState<string | null>(null);
  const qc = useQueryClient();
  const toast = useToast();
  const navigate = useNavigate();
  const { user } = useAuth();

  const project = useQuery({ queryKey: ['project', projectId], queryFn: () => api<Project>(`/projects/${projectId}`) });
  const board = useQuery({ queryKey: ['board', projectId], queryFn: () => api<Board>(`/projects/${projectId}/board`), enabled: view === 'board' });
  const list = useQuery({
    queryKey: ['tasks', 'project', projectId, prio],
    queryFn: () => api<Page<Task>>(`/projects/${projectId}/tasks?size=100&sort=number,desc${prio !== 'ALL' ? `&priority=${prio}` : ''}`),
    enabled: view === 'list',
  });

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['board', projectId] });
    qc.invalidateQueries({ queryKey: ['tasks'] });
    qc.invalidateQueries({ queryKey: ['overview'] });
  };

  const move = useMutation({
    mutationFn: (v: { taskId: string; statusId: string; afterTaskId?: string; beforeTaskId?: string }) =>
      api<Task>(`/tasks/${v.taskId}/move`, { body: { statusId: v.statusId, afterTaskId: v.afterTaskId ?? null, beforeTaskId: v.beforeTaskId ?? null } }),
    onSuccess: (t) => {
      if (t.pendingApprovalStatusId) toast(`Approval requested to move ${t.key} to ${t.pendingApprovalStatusName}`);
      refresh();
    },
    onError: (e) => { toast(errorMessage(e)); refresh(); },
  });

  const bulk = useMutation({
    mutationFn: (body: { assigneeId?: string; priority?: Priority }) => api<{ updated: number }>(`/projects/${projectId}/tasks/bulk`, { body: { taskIds: [...selected], ...body } }),
    onSuccess: (r) => { toast(`${r.updated} tasks updated`); setSelected(new Set()); refresh(); },
    onError: (e) => toast(errorMessage(e)),
  });

  const onDrop = (e: DragEvent, statusId: string, cards: Task[], index: number) => {
    e.preventDefault();
    e.stopPropagation();
    const id = e.dataTransfer.getData('text/plain') || dragId;
    setDragId(null);
    if (!id) return;
    const others = cards.filter((c) => c.id !== id);
    const at = Math.min(index, others.length);
    move.mutate({ taskId: id, statusId, afterTaskId: others[at - 1]?.id, beforeTaskId: others[at]?.id });
  };

  if (project.isLoading) return <Spinner />;
  if (project.error || !project.data) return <Empty title="Project not found" body="It may have been archived, or you don’t have access." />;
  const p = project.data;
  const canManage = p.myRole === 'MANAGER';
  const canEdit = p.myRole !== 'VIEWER';

  return (
    <>
      <PageHeader title={p.name} subtitle={`Projects / ${p.key}`}
        actions={<>
          {canManage && <Link to={`/projects/${p.id}/workflow`} className="flex min-h-10 items-center rounded-[10px] border border-line bg-surface px-3.5 text-sm font-semibold">Workflow settings</Link>}
          {canEdit && <Button variant="rose" onClick={() => setNewIn('')}>+ New task</Button>}
        </>} />
      <div className="flex flex-wrap items-center gap-2.5 border-b border-line pb-3.5">
        <Segmented<ViewMode> label="View" value={view} onChange={(v) => (v === 'calendar' ? navigate('/calendar') : setParams(v === 'board' ? {} : { view: v }))}
          options={[{ value: 'board', label: 'Board' }, { value: 'list', label: 'List' }, { value: 'calendar', label: 'Calendar' }]} />
        {PRIORITIES.map((x) => (
          <button key={x} type="button" aria-pressed={prio === x} onClick={() => setPrio(x)}
            className={`min-h-8 rounded-full border px-3 text-[13px] ${prio === x ? 'border-accent bg-accent-soft text-accent' : 'border-line bg-surface text-ink-2'}`}>
            {x === 'ALL' ? 'All priorities' : x[0] + x.slice(1).toLowerCase()}
          </button>
        ))}
        <span className="ml-auto text-[13px] text-muted">{p.totalTasks} tasks · {p.overdueTasks} overdue</span>
      </div>

      {view === 'board' && (board.isLoading ? <Spinner /> : (
        <div className="overflow-x-auto pb-2">
          <div className="grid min-w-[960px] gap-3.5" style={{ gridTemplateColumns: `repeat(${board.data?.columns.length ?? 4}, minmax(230px, 1fr))` }}>
            {board.data?.columns.map((col) => {
              const cards = col.tasks.filter((t) => prio === 'ALL' || t.priority === prio);
              return (
                <section key={col.statusId} onDragOver={(e) => canEdit && e.preventDefault()} onDrop={(e) => onDrop(e, col.statusId, col.tasks, col.tasks.length)}
                  className="flex min-h-[520px] flex-col gap-2.5 rounded-[14px] border border-line bg-lane p-3" aria-label={col.name}>
                  <div className="flex items-center gap-2 px-1">
                    <span className="h-2.5 w-2.5 rounded-full" style={{ background: col.color }} />
                    <h2 className="text-sm font-semibold">{col.name}</h2>
                    <span className="text-xs text-muted">{cards.length}</span>
                    <span className="ml-auto rounded bg-chip px-1.5 py-0.5 font-mono text-[10px] text-ink-2">{col.category}</span>
                  </div>
                  {cards.map((t) => (
                    <div key={t.id} draggable={canEdit}
                      onDragStart={(e) => { e.dataTransfer.setData('text/plain', t.id); setDragId(t.id); }}
                      onDragOver={(e) => canEdit && e.preventDefault()}
                      onDrop={(e) => onDrop(e, col.statusId, col.tasks, col.tasks.findIndex((c) => c.id === t.id))}>
                      <Link to={`/tasks/${t.id}`}
                        className={`flex flex-col gap-2.5 rounded-xl border bg-surface p-3 hover:border-line-strong ${dragId === t.id ? 'opacity-40' : ''} ${t.pendingApprovalStatusId ? 'border-warn' : t.overdue ? 'border-rose' : 'border-line'}`}>
                        <span className="flex items-center justify-between"><span className="font-mono text-xs text-muted">{t.key}</span><PriorityBadge priority={t.priority} /></span>
                        <span className="text-sm leading-snug font-semibold">{t.title}</span>
                        {t.labels.length > 0 && <span className="flex flex-wrap gap-1.5">{t.labels.map((l) => <span key={l} className="rounded bg-chip px-1.5 py-0.5 text-[11px] text-ink-2">{l}</span>)}</span>}
                        <span className="flex items-center justify-between text-xs">
                          <span className={t.overdue ? 'font-semibold text-rose' : 'text-ink-2'}>
                            {t.pendingApprovalStatusId ? `Awaiting approval → ${t.pendingApprovalStatusName}` : t.dueDate ? `${t.overdue ? 'Overdue · ' : ''}${shortDate(t.dueDate)}` : ''}
                          </span>
                          <span className="flex items-center gap-2 text-muted">
                            {t.recurrence !== 'NONE' && <span title="Repeats">↻</span>}
                            {t.subtaskCount > 0 && <span>✓ {t.subtaskDone}/{t.subtaskCount}</span>}
                            <Avatar user={t.assignee} size={24} />
                          </span>
                        </span>
                      </Link>
                    </div>
                  ))}
                  {canEdit && (
                    <button type="button" onClick={() => setNewIn(col.statusId)} className="min-h-10 rounded-[10px] border border-dashed border-line-strong text-[13px] text-ink-2 hover:bg-chip">
                      + Add task
                    </button>
                  )}
                </section>
              );
            })}
          </div>
        </div>
      ))}

      {view === 'list' && (
        <>
          {selected.size > 0 && canManage && (
            <div className="flex flex-wrap items-center gap-2.5 rounded-xl bg-accent-soft px-3.5 py-2.5 text-sm">
              <b className="font-semibold text-accent">{selected.size} selected</b>
              <Button onClick={() => user && bulk.mutate({ assigneeId: user.id })}>Assign to me</Button>
              <Button onClick={() => bulk.mutate({ priority: 'HIGH' })}>Set priority: High</Button>
              <Button variant="ghost" className="ml-auto" onClick={() => setSelected(new Set())}>Clear selection</Button>
            </div>
          )}
          {list.isLoading ? <Spinner /> : list.data?.content.length ? (
            <TaskTable tasks={list.data.content} showProject={false} selected={selected}
              onToggle={canManage ? (id) => setSelected((s) => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; }) : undefined} />
          ) : <Empty title="No tasks here" body="Create a task or change the priority filter." />}
        </>
      )}

      {newIn !== null && <NewTaskModal onClose={() => setNewIn(null)} defaultProjectId={projectId} defaultStatusId={newIn || undefined} />}
    </>
  );
}
