import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useWorkspaces } from '../lib/queries';
import type { Workspace } from '../lib/types';

interface WorkspaceState {
  workspace: Workspace | null;
  workspaces: Workspace[];
  loading: boolean;
  select: (id: string) => void;
}

const WorkspaceContext = createContext<WorkspaceState>({ workspace: null, workspaces: [], loading: true, select: () => {} });

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const { data = [], isLoading } = useWorkspaces();
  const [selected, setSelected] = useState<string | null>(() => localStorage.getItem('fl_ws'));

  useEffect(() => {
    if (data.length && !data.some((w) => w.id === selected)) setSelected(data[0].id);
  }, [data, selected]);

  const value = useMemo<WorkspaceState>(
    () => ({
      workspaces: data,
      workspace: data.find((w) => w.id === selected) ?? data[0] ?? null,
      loading: isLoading,
      select: (id) => {
        localStorage.setItem('fl_ws', id);
        setSelected(id);
      },
    }),
    [data, selected, isLoading],
  );
  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>;
}

export const useWorkspace = () => useContext(WorkspaceContext);
