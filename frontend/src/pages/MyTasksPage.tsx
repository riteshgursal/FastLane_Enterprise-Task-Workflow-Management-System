import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../lib/api';
import type { Page, Task } from '../lib/types';
import { useWorkspace } from '../context/WorkspaceContext';
import { TaskTable } from '../components/TaskTable';
import { Button, Empty, PageHeader, Segmented, Spinner } from '../components/ui';

type Filter = 'open' | 'overdue' | 'all';

export function MyTasksPage() {
  const { workspace } = useWorkspace();
  const ws = workspace!.id;
  const [filter, setFilter] = useState<Filter>('open');
  const [page, setPage] = useState(0);
  const q = useQuery({ queryKey: ['tasks', 'mine', ws, page], queryFn: () => api<Page<Task>>(`/workspaces/${ws}/my-tasks?size=50&page=${page}&sort=dueDate`) });
  const all = q.data?.content ?? [];
  const rows = all.filter((t) => (filter === 'all' ? true : filter === 'overdue' ? t.overdue : t.category !== 'DONE' && t.category !== 'CANCELLED'));
  return (
    <>
      <PageHeader title="My tasks" subtitle="Assigned to you across every project"
        actions={<Segmented label="Filter" value={filter} onChange={setFilter} options={[{ value: 'open', label: 'Open' }, { value: 'overdue', label: 'Overdue' }, { value: 'all', label: 'All' }]} />} />
      {q.isLoading ? <Spinner /> : rows.length ? <TaskTable tasks={rows} /> : <Empty title="You’re clear" body="No tasks match this filter." />}
      {q.data && q.data.totalPages > 1 && (
        <div className="flex items-center justify-end gap-2 text-sm text-muted">
          <Button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>Previous</Button>
          Page {page + 1} of {q.data.totalPages}
          <Button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= q.data.totalPages}>Next</Button>
        </div>
      )}
    </>
  );
}
