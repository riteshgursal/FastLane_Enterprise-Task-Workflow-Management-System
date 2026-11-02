import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../lib/api';
import type { Workspace } from '../lib/types';
import { useWorkspace } from '../context/WorkspaceContext';
import { Button, Field, inputClass } from './ui';

export function NewWorkspaceForm({ onDone }: { onDone?: () => void }) {
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const qc = useQueryClient();
  const { select } = useWorkspace();
  const create = useMutation({
    mutationFn: () => api<Workspace>('/workspaces', { body: { name } }),
    onSuccess: async (w) => {
      await qc.invalidateQueries({ queryKey: ['workspaces'] });
      select(w.id);
      onDone?.();
    },
    onError: (e) => setError(errorMessage(e)),
  });
  return (
    <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); if (!name.trim()) return setError('Enter a workspace name.'); create.mutate(); }}>
      <Field label="Workspace name" error={error ?? undefined}>
        <input className={inputClass} value={name} onChange={(e) => setName(e.target.value)} placeholder="Acme Corp" maxLength={80} />
      </Field>
      <Button type="submit" variant="primary" disabled={create.isPending}>Create workspace</Button>
    </form>
  );
}
