import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api, errorMessage } from '../lib/api';
import { useMembers, useProjects } from '../lib/queries';
import type { Priority, Recurrence, Task } from '../lib/types';
import { useWorkspace } from '../context/WorkspaceContext';
import { useToast } from '../context/ToastContext';
import { Button, Field, Modal, inputClass } from './ui';

export function NewTaskModal({ onClose, defaultProjectId, defaultStatusId, parentTaskId }: {
  onClose: () => void; defaultProjectId?: string; defaultStatusId?: string; parentTaskId?: string;
}) {
  const { workspace } = useWorkspace();
  const { data: projects = [] } = useProjects(workspace?.id);
  const { data: members = [] } = useMembers(workspace?.id);
  const writable = projects.filter((p) => p.myRole !== 'VIEWER');
  const [projectId, setProjectId] = useState(defaultProjectId ?? writable[0]?.id ?? '');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<Priority>('MEDIUM');
  const [assigneeId, setAssigneeId] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [labels, setLabels] = useState('');
  const [recurrence, setRecurrence] = useState<Recurrence>('NONE');
  const [error, setError] = useState<string | null>(null);
  const qc = useQueryClient();
  const toast = useToast();
  const navigate = useNavigate();

  const create = useMutation({
    mutationFn: () =>
      api<Task>(`/projects/${projectId || writable[0]?.id}/tasks`, {
        body: {
          title, description: description || null, priority, assigneeId: assigneeId || null, dueDate: dueDate || null,
          statusId: defaultStatusId ?? null, parentTaskId: parentTaskId ?? null, recurrence,
          labels: labels.split(',').map((l) => l.trim()).filter(Boolean),
        },
      }),
    onSuccess: (t) => {
      qc.invalidateQueries({ queryKey: ['board'] });
      qc.invalidateQueries({ queryKey: ['tasks'] });
      qc.invalidateQueries({ queryKey: ['task'] });
      qc.invalidateQueries({ queryKey: ['overview'] });
      toast(`${t.key} created`);
      onClose();
      if (!parentTaskId && !defaultProjectId) navigate(`/projects/${t.projectId}`);
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const submit = () => {
    if (!title.trim()) return setError('Enter a title for the task.');
    if (!(projectId || writable[0])) return setError('Create a project first.');
    setError(null);
    create.mutate();
  };

  return (
    <Modal title={parentTaskId ? 'New subtask' : 'New task'} onClose={onClose}>
      <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); submit(); }}>
        <Field label="Title">
          <input className={inputClass} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Design the onboarding checklist" maxLength={200} />
        </Field>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {!parentTaskId && (
            <Field label="Project">
              <select className={inputClass} value={projectId || writable[0]?.id || ''} onChange={(e) => setProjectId(e.target.value)} disabled={!!defaultProjectId}>
                {writable.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </Field>
          )}
          <Field label="Priority">
            <select className={inputClass} value={priority} onChange={(e) => setPriority(e.target.value as Priority)}>
              <option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option><option value="URGENT">Urgent</option>
            </select>
          </Field>
          <Field label="Assignee">
            <select className={inputClass} value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)}>
              <option value="">Unassigned</option>
              {members.map((m) => <option key={m.userId} value={m.userId}>{m.displayName}</option>)}
            </select>
          </Field>
          <Field label="Due date">
            <input type="date" className={inputClass} value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
          </Field>
          <Field label="Repeat">
            <select className={inputClass} value={recurrence} onChange={(e) => setRecurrence(e.target.value as Recurrence)}>
              <option value="NONE">Never</option><option value="WEEKLY">Every week</option><option value="MONTHLY">Every month</option>
            </select>
          </Field>
          <Field label="Labels">
            <input className={inputClass} value={labels} onChange={(e) => setLabels(e.target.value)} placeholder="Frontend, Auth" />
          </Field>
        </div>
        <Field label="Description">
          <textarea rows={3} className={`${inputClass} py-2`} value={description} onChange={(e) => setDescription(e.target.value)}
            placeholder="What needs to happen, and how will you know it’s done?" />
        </Field>
        {error && <p className="text-sm text-rose">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="rose" disabled={create.isPending}>{create.isPending ? 'Creating…' : 'Create task'}</Button>
        </div>
      </form>
    </Modal>
  );
}
