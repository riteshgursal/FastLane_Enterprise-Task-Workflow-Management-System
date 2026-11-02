import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api, ApiError, errorMessage } from '../lib/api';
import type { Project } from '../lib/types';
import { useToast } from '../context/ToastContext';
import { Button, Field, Modal, inputClass } from './ui';

const COLORS = ['#1DA1D8', '#FF2E7E', '#F5B544', '#4AD08A', '#A78BFA', '#007BA7'];

export function NewProjectModal({ workspaceId, onClose }: { workspaceId: string; onClose: () => void }) {
  const [name, setName] = useState('');
  const [key, setKey] = useState('');
  const [keyTouched, setKeyTouched] = useState(false);
  const [color, setColor] = useState(COLORS[0]);
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);
  const qc = useQueryClient();
  const toast = useToast();
  const navigate = useNavigate();

  const suggested = name.replace(/[^A-Za-z0-9 ]/g, '').split(/\s+/).filter(Boolean).map((w) => w[0]).join('').toUpperCase().slice(0, 4)
    || name.replace(/[^A-Za-z]/g, '').slice(0, 3).toUpperCase();
  const finalKey = keyTouched ? key : suggested.length >= 2 ? suggested : (suggested + 'XX').slice(0, 3);

  const create = useMutation({
    mutationFn: () => api<Project>(`/workspaces/${workspaceId}/projects`, { body: { name, key: finalKey, color, description: description || null } }),
    onSuccess: (p) => {
      qc.invalidateQueries({ queryKey: ['projects', workspaceId] });
      qc.invalidateQueries({ queryKey: ['workspaces'] });
      toast(`${p.name} created with the default workflow`);
      onClose();
      navigate(`/projects/${p.id}`);
    },
    onError: (e) => setError(e instanceof ApiError && e.fieldErrors.length ? e.fieldErrors.map((f) => `${f.field}: ${f.message}`).join(' · ') : errorMessage(e)),
  });

  return (
    <Modal title="New project" onClose={onClose} width="max-w-lg">
      <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); if (!name.trim()) return setError('Enter a project name.'); create.mutate(); }}>
        <Field label="Name">
          <input className={inputClass} value={name} onChange={(e) => setName(e.target.value)} placeholder="Website redesign" maxLength={80} />
        </Field>
        <Field label="Key · used in task IDs like WEB-12">
          <input className={`${inputClass} font-mono uppercase`} value={finalKey} maxLength={10}
            onChange={(e) => { setKeyTouched(true); setKey(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '')); }} />
        </Field>
        <div className="flex flex-col gap-1.5 text-[13px] font-semibold text-ink-2">
          Colour
          <div className="flex gap-2">
            {COLORS.map((c) => (
              <button key={c} type="button" aria-label={`Colour ${c}`} aria-pressed={color === c} onClick={() => setColor(c)}
                className={`h-9 w-9 rounded-lg border-2 ${color === c ? 'border-ink' : 'border-transparent'}`} style={{ background: c }} />
            ))}
          </div>
        </div>
        <Field label="Description">
          <textarea rows={2} className={`${inputClass} py-2`} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        {error && <p className="text-sm text-rose">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="primary" disabled={create.isPending}>Create project</Button>
        </div>
      </form>
    </Modal>
  );
}
