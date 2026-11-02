import { useRef, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, download, errorMessage } from '../lib/api';
import { fileSize, longDate, relativeTime } from '../lib/format';
import type { Attachment, Comment, Dependency, HistoryEntry, Priority, Project, Recurrence, Task, Workflow } from '../lib/types';
import { useMembers } from '../lib/queries';
import { useToast } from '../context/ToastContext';
import { useWorkspace } from '../context/WorkspaceContext';
import { NewTaskModal } from '../components/NewTaskModal';
import { Avatar, Button, Card, Empty, PriorityBadge, Spinner, StatusDot, inputClass } from '../components/ui';

export function TaskDetailPage() {
  const { taskId = '' } = useParams();
  const { workspace } = useWorkspace();
  const qc = useQueryClient();
  const toast = useToast();
  const navigate = useNavigate();
  const fileInput = useRef<HTMLInputElement>(null);
  const [tab, setTab] = useState<'comments' | 'history'>('comments');
  const [comment, setComment] = useState('');
  const [editingTitle, setEditingTitle] = useState<string | null>(null);
  const [descDraft, setDescDraft] = useState<string | null>(null);
  const [depKey, setDepKey] = useState('');
  const [subtaskOpen, setSubtaskOpen] = useState(false);

  const task = useQuery({ queryKey: ['task', taskId], queryFn: () => api<Task>(`/tasks/${taskId}`) });
  const t = task.data;
  const project = useQuery({ queryKey: ['project', t?.projectId], queryFn: () => api<Project>(`/projects/${t!.projectId}`), enabled: !!t });
  const workflow = useQuery({ queryKey: ['workflow', t?.projectId], queryFn: () => api<Workflow>(`/projects/${t!.projectId}/workflow`), enabled: !!t });
  const subtasks = useQuery({ queryKey: ['task', taskId, 'subtasks'], queryFn: () => api<Task[]>(`/tasks/${taskId}/subtasks`) });
  const comments = useQuery({ queryKey: ['task', taskId, 'comments'], queryFn: () => api<Comment[]>(`/tasks/${taskId}/comments`) });
  const history = useQuery({ queryKey: ['task', taskId, 'history'], queryFn: () => api<HistoryEntry[]>(`/tasks/${taskId}/history`) });
  const attachments = useQuery({ queryKey: ['task', taskId, 'attachments'], queryFn: () => api<Attachment[]>(`/tasks/${taskId}/attachments`) });
  const deps = useQuery({ queryKey: ['task', taskId, 'deps'], queryFn: () => api<Dependency[]>(`/tasks/${taskId}/dependencies`) });
  const { data: members = [] } = useMembers(workspace?.id);

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['task', taskId] });
    qc.invalidateQueries({ queryKey: ['board'] });
    qc.invalidateQueries({ queryKey: ['tasks'] });
    qc.invalidateQueries({ queryKey: ['approvals'] });
  };
  const run = <T,>(fn: () => Promise<T>, ok?: string) => fn().then(() => { if (ok) toast(ok); invalidate(); }).catch((e) => toast(errorMessage(e)));

  const update = useMutation({
    mutationFn: (patch: Record<string, unknown>) => api<Task>(`/tasks/${taskId}`, { method: 'PATCH', body: { version: t!.version, ...patch } }),
    onSuccess: () => invalidate(),
    onError: (e) => { toast(errorMessage(e)); invalidate(); },
  });

  if (task.isLoading) return <Spinner />;
  if (!t) return <Empty title="Task not found" body="It may have been deleted, or you don’t have access." />;

  const role = project.data?.myRole;
  const canEdit = role === 'MANAGER' || role === 'CONTRIBUTOR';
  const isManager = role === 'MANAGER';
  const statuses = workflow.data?.statuses ?? [];
  const nextStatuses = (workflow.data?.transitions ?? [])
    .filter((x) => x.fromStatusId === t.statusId)
    .map((x) => ({ ...x, status: statuses.find((s) => s.id === x.toStatusId)! }))
    .filter((x) => x.status);

  const upload = async (file: File) => {
    const form = new FormData();
    form.append('file', file);
    await run(() => api(`/tasks/${taskId}/attachments`, { form }), `${file.name} uploaded`);
  };

  const fields: [string, ReactNode][] = [
    ['Assignee', canEdit ? (
      <select aria-label="Assignee" className="bg-transparent text-right text-sm font-medium outline-none" value={t.assignee?.id ?? ''}
        onChange={(e) => update.mutate(e.target.value ? { assigneeId: e.target.value } : { clearAssignee: true })}>
        <option value="">Unassigned</option>
        {members.map((m) => <option key={m.userId} value={m.userId}>{m.displayName}</option>)}
      </select>
    ) : t.assignee?.displayName ?? 'Unassigned'],
    ['Reporter', t.reporter.displayName],
    ['Priority', canEdit ? (
      <select aria-label="Priority" className="bg-transparent text-right text-sm font-medium outline-none" value={t.priority} onChange={(e) => update.mutate({ priority: e.target.value as Priority })}>
        <option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option><option value="URGENT">Urgent</option>
      </select>
    ) : t.priority],
    ['Due date', canEdit ? (
      <input type="date" aria-label="Due date" className={`bg-transparent text-right text-sm font-medium outline-none ${t.overdue ? 'text-rose' : ''}`} value={t.dueDate ?? ''}
        onChange={(e) => update.mutate(e.target.value ? { dueDate: e.target.value } : { clearDueDate: true })} />
    ) : longDate(t.dueDate) || 'None'],
    ['Repeats', canEdit ? (
      <select aria-label="Repeats" className="bg-transparent text-right text-sm font-medium outline-none" value={t.recurrence} onChange={(e) => update.mutate({ recurrence: e.target.value as Recurrence })}>
        <option value="NONE">Never</option><option value="WEEKLY">Every week</option><option value="MONTHLY">Every month</option>
      </select>
    ) : t.recurrence],
    ['Estimate', t.estimatePoints != null ? `${t.estimatePoints} points` : '—'],
    ['Labels', t.labels.join(', ') || 'None'],
    ['Created', longDate(t.createdAt)],
  ];

  return (
    <>
      <nav aria-label="Breadcrumb" className="flex items-center gap-2 text-[13px] text-muted">
        <Link to={`/projects/${t.projectId}`} className="flex items-center gap-1.5 hover:text-accent"><span className="h-2 w-2 rounded-sm" style={{ background: t.projectColor }} />{t.projectName}</Link>
        <span>/</span><span className="font-mono">{t.key}</span>
      </nav>
      <div className="flex flex-wrap items-start gap-5">
        <div className="flex min-w-0 flex-[999_1_460px] flex-col gap-5">
          <Card className="flex flex-col gap-3.5 p-6">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-[13px] text-muted">{t.key}</span>
              <PriorityBadge priority={t.priority} />
              {t.recurrence !== 'NONE' && <span className="rounded-md bg-accent-soft px-2 py-0.5 text-xs font-semibold text-accent">↻ Repeats {t.recurrence.toLowerCase()}</span>}
            </div>
            {editingTitle !== null ? (
              <input autoFocus className={`${inputClass} font-display text-2xl font-bold`} value={editingTitle} onChange={(e) => setEditingTitle(e.target.value)}
                onBlur={() => { if (editingTitle.trim() && editingTitle !== t.title) update.mutate({ title: editingTitle }); setEditingTitle(null); }}
                onKeyDown={(e) => e.key === 'Enter' && (e.target as HTMLInputElement).blur()} />
            ) : (
              <h1 className="font-display text-[26px] font-bold tracking-tight">
                {canEdit ? <button type="button" className="text-left" onClick={() => setEditingTitle(t.title)}>{t.title}</button> : t.title}
              </h1>
            )}
            {descDraft !== null ? (
              <div className="flex flex-col gap-2">
                <textarea rows={6} className={`${inputClass} py-2`} value={descDraft} onChange={(e) => setDescDraft(e.target.value)} />
                <div className="flex justify-end gap-2">
                  <Button onClick={() => setDescDraft(null)}>Cancel</Button>
                  <Button variant="primary" onClick={() => { update.mutate({ description: descDraft }); setDescDraft(null); }}>Save description</Button>
                </div>
              </div>
            ) : (
              <div className="text-[15px] leading-relaxed whitespace-pre-wrap text-ink-2">
                {t.description || <span className="text-muted">No description yet.</span>}
                {canEdit && <button type="button" onClick={() => setDescDraft(t.description ?? '')} className="ml-2 text-sm font-semibold text-accent">Edit</button>}
              </div>
            )}
            {!t.parentId && (
              <div className="flex flex-col">
                <div className="flex items-center justify-between py-1.5">
                  <h2 className="text-[15px] font-semibold">Subtasks</h2>
                  <span className="text-[13px] text-muted">{t.subtaskCount ? `${t.subtaskDone} of ${t.subtaskCount} done` : 'None yet'}</span>
                </div>
                {subtasks.data?.map((s) => (
                  <Link key={s.id} to={`/tasks/${s.id}`} className="flex min-h-11 items-center gap-2.5 border-t border-line text-sm">
                    <StatusDot color={s.statusColor} />
                    <span className={`flex-1 ${s.category === 'DONE' ? 'text-muted line-through' : ''}`}>{s.title}</span>
                    <span className="font-mono text-xs text-muted">{s.key}</span>
                  </Link>
                ))}
                {canEdit && <button type="button" onClick={() => setSubtaskOpen(true)} className="mt-1 self-start text-sm font-semibold text-accent">+ Add subtask</button>}
              </div>
            )}
          </Card>

          <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
            <Card className="flex flex-col gap-2.5 p-5">
              <h2 className="text-[15px] font-semibold">Attachments</h2>
              {attachments.data?.map((a) => (
                <div key={a.id} className="flex items-center gap-2.5 rounded-[10px] border border-line bg-surface-2 p-2.5">
                  <span className={`flex h-9 w-9 items-center justify-center rounded-lg text-[10px] font-bold ${a.contentType.includes('pdf') ? 'bg-rose-soft text-rose' : 'bg-accent-soft text-accent'}`}>
                    {(a.fileName.split('.').pop() ?? 'FILE').slice(0, 4).toUpperCase()}
                  </span>
                  <button type="button" onClick={() => download(`/attachments/${a.id}/download`, a.fileName).catch((e) => toast(errorMessage(e)))} className="flex min-w-0 flex-1 flex-col text-left">
                    <span className="truncate text-sm font-medium">{a.fileName}</span>
                    <span className="text-xs text-muted">{fileSize(a.sizeBytes)} · {a.uploader?.displayName}</span>
                  </button>
                  {canEdit && <button type="button" aria-label={`Remove ${a.fileName}`} onClick={() => run(() => api(`/attachments/${a.id}`, { method: 'DELETE' }), 'Attachment removed')} className="px-2 text-muted hover:text-rose">×</button>}
                </div>
              ))}
              {canEdit && (
                <>
                  <input ref={fileInput} type="file" className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f) upload(f); e.target.value = ''; }} />
                  <button type="button" onClick={() => fileInput.current?.click()}
                    onDragOver={(e) => e.preventDefault()} onDrop={(e) => { e.preventDefault(); const f = e.dataTransfer.files[0]; if (f) upload(f); }}
                    className="min-h-16 rounded-[10px] border-[1.5px] border-dashed border-line-strong text-[13px] text-ink-2">
                    Drop a file or <b className="text-accent">browse</b> · up to 25 MB
                  </button>
                </>
              )}
            </Card>
            <Card className="flex flex-col gap-2.5 p-5">
              <h2 className="text-[15px] font-semibold">Dependencies</h2>
              {deps.data?.length === 0 && <p className="text-sm text-muted">No dependencies.</p>}
              {deps.data?.map((d) => (
                <div key={d.task.id + d.relation} className="flex min-h-11 items-center gap-2.5 rounded-[10px] border border-line bg-surface-2 px-3 text-sm">
                  <span className={`w-20 text-xs font-semibold ${d.relation === 'BLOCKED_BY' ? 'text-rose' : 'text-accent'}`}>{d.relation === 'BLOCKED_BY' ? 'Blocked by' : 'Blocks'}</span>
                  <Link to={`/tasks/${d.task.id}`} className="flex min-w-0 flex-1 items-center gap-2">
                    <span className="font-mono text-xs text-muted">{d.task.key}</span><span className="truncate">{d.task.title}</span>
                  </Link>
                  <StatusDot color={d.task.statusColor} />
                  {canEdit && d.relation === 'BLOCKED_BY' && (
                    <button type="button" aria-label="Remove dependency" className="px-1 text-muted hover:text-rose"
                      onClick={() => run(() => api(`/tasks/${taskId}/dependencies/${d.task.id}`, { method: 'DELETE' }))}>×</button>
                  )}
                </div>
              ))}
              {canEdit && (
                <form className="flex gap-2" onSubmit={async (e) => {
                  e.preventDefault();
                  if (!depKey.trim() || !workspace) return;
                  try {
                    const blocker = await api<Task>(`/workspaces/${workspace.id}/tasks/by-key/${encodeURIComponent(depKey.trim())}`);
                    await run(() => api(`/tasks/${taskId}/dependencies`, { body: { blockedById: blocker.id } }), `Now blocked by ${blocker.key}`);
                    setDepKey('');
                  } catch (err) { toast(errorMessage(err)); }
                }}>
                  <input className={`${inputClass} min-h-9 flex-1 font-mono text-sm uppercase`} placeholder="Blocked by, e.g. WEB-3" value={depKey} onChange={(e) => setDepKey(e.target.value)} aria-label="Blocking task key" />
                  <Button type="submit">Link</Button>
                </form>
              )}
            </Card>
          </div>

          <Card className="flex flex-col gap-3.5 px-6 pt-2 pb-6">
            <div role="tablist" className="flex gap-5 border-b border-line">
              {(['comments', 'history'] as const).map((k) => (
                <button key={k} type="button" role="tab" aria-selected={tab === k} onClick={() => setTab(k)}
                  className={`min-h-11 border-b-2 text-sm font-semibold capitalize ${tab === k ? 'border-lane-blue text-ink' : 'border-transparent text-ink-2'}`}>
                  {k} {k === 'comments' ? comments.data?.length ?? '' : history.data?.length ?? ''}
                </button>
              ))}
            </div>
            {tab === 'comments' && (
              <>
                {comments.data?.map((c) => (
                  <div key={c.id} className="flex gap-3">
                    <Avatar user={c.author} size={32} />
                    <div className="flex flex-1 flex-col gap-1">
                      <span className="text-sm"><b className="font-semibold">{c.author?.displayName}</b> <span className="text-xs text-muted">{relativeTime(c.createdAt)}{c.editedAt ? ' · edited' : ''}</span></span>
                      <p className="text-sm leading-relaxed whitespace-pre-wrap text-ink-2">{c.body}</p>
                      {c.mine && <button type="button" className="self-start text-xs text-muted hover:text-rose" onClick={() => run(() => api(`/comments/${c.id}`, { method: 'DELETE' }), 'Comment deleted')}>Delete</button>}
                    </div>
                  </div>
                ))}
                {canEdit && (
                  <form className="flex flex-col gap-2" onSubmit={(e) => {
                    e.preventDefault();
                    if (!comment.trim()) return;
                    run(() => api(`/tasks/${taskId}/comments`, { body: { body: comment } }), comment.includes('@') ? 'Comment posted. Mentioned people were notified.' : 'Comment posted').then(() => setComment(''));
                  }}>
                    <label className="text-[13px] font-semibold text-ink-2" htmlFor="comment">Add a comment</label>
                    <textarea id="comment" rows={3} className={`${inputClass} py-2 text-sm`} value={comment} onChange={(e) => setComment(e.target.value)} placeholder="Write a comment. Type @Firstname to mention someone." />
                    <div className="flex items-center gap-2"><span className="text-xs text-muted">Mentions send an in-app and email notification.</span><Button type="submit" variant="primary" className="ml-auto">Comment</Button></div>
                  </form>
                )}
              </>
            )}
            {tab === 'history' && (
              <ol className="flex flex-col">
                {history.data?.map((h) => (
                  <li key={h.id} className="flex gap-3 border-b border-line py-2.5 text-sm last:border-0">
                    <span className="mt-1.5 h-2 w-2 flex-none rounded-full bg-accent" />
                    <span className="flex-1 text-ink-2"><b className="font-semibold text-ink">{h.actorName}</b> {h.detail}</span>
                    <span className="text-xs text-muted">{relativeTime(h.at)}</span>
                  </li>
                ))}
              </ol>
            )}
          </Card>
        </div>

        <aside className="flex min-w-0 flex-[1_1_300px] flex-col gap-4">
          <Card className="flex flex-col gap-3 p-5">
            <div className="flex items-center justify-between"><span className="text-[13px] text-ink-2">Status</span><span className="rounded bg-chip px-1.5 py-0.5 font-mono text-[10px] text-ink-2">{t.category}</span></div>
            <div className="flex items-center gap-2.5 font-display text-xl font-bold"><StatusDot color={t.statusColor} />{t.statusName}</div>
            {t.pendingApprovalStatusId ? (
              <div className="flex flex-col gap-2.5 rounded-[10px] bg-warn-soft p-3">
                <span className="text-[13px] leading-snug text-warn"><b>Approval pending.</b> Moving to {t.pendingApprovalStatusName} needs a project manager.</span>
                {isManager && (
                  <div className="flex gap-2">
                    <Button variant="primary" className="flex-1 !bg-ok-btn" onClick={() => run(() => api(`/tasks/${taskId}/approval/approve`, { method: 'POST' }), 'Approved')}>Approve</Button>
                    <Button className="flex-1" onClick={() => run(() => api(`/tasks/${taskId}/approval/reject`, { body: { reason: '' } }), 'Sent back')}>Send back</Button>
                  </div>
                )}
              </div>
            ) : canEdit && nextStatuses.length > 0 ? (
              <>
                <span className="text-xs text-muted">Move to · only allowed transitions</span>
                <div className="flex flex-wrap gap-2">
                  {nextStatuses.map((n) => (
                    <Button key={n.toStatusId} onClick={() => run(() => api<Task>(`/tasks/${taskId}/transitions`, { body: { toStatusId: n.toStatusId } }),
                      n.requiresApproval ? `Approval requested to move to ${n.status.name}` : `Moved to ${n.status.name}`)}>
                      {n.status.name}{n.requiresApproval ? ' · approval' : ''}
                    </Button>
                  ))}
                </div>
              </>
            ) : null}
          </Card>
          <Card className="px-5 py-1.5">
            {fields.map(([label, value]) => (
              <div key={label} className="flex min-h-11 items-center justify-between gap-3 border-b border-line text-sm last:border-0">
                <span className="text-ink-2">{label}</span><span className="text-right font-medium">{value}</span>
              </div>
            ))}
          </Card>
          {canEdit && (
            <Button variant="danger" onClick={() => {
              if (!confirm(`Move ${t.key} to the trash?`)) return;
              api(`/tasks/${taskId}`, { method: 'DELETE' }).then(() => { toast(`${t.key} moved to the trash`); invalidate(); navigate(`/projects/${t.projectId}`); }).catch((e) => toast(errorMessage(e)));
            }}>Delete task</Button>
          )}
        </aside>
      </div>
      {subtaskOpen && <NewTaskModal onClose={() => setSubtaskOpen(false)} defaultProjectId={t.projectId} parentTaskId={t.id} />}
    </>
  );
}
