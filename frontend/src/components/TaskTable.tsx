import { Link } from 'react-router-dom';
import { shortDate } from '../lib/format';
import type { Task } from '../lib/types';
import { Avatar, PriorityBadge, StatusDot } from './ui';

export function TaskTable({ tasks, selected, onToggle, showProject = true }: {
  tasks: Task[]; selected?: Set<string>; onToggle?: (id: string) => void; showProject?: boolean;
}) {
  return (
    <div className="overflow-x-auto rounded-[14px] border border-line bg-surface">
      <table className="w-full min-w-[820px] border-collapse text-sm">
        <thead>
          <tr className="text-left text-xs text-muted">
            {onToggle && <th scope="col" className="w-10 border-b border-line px-4 py-3"><span className="sr-only">Select</span></th>}
            <th scope="col" className="border-b border-line px-2 py-3 font-semibold">Key</th>
            <th scope="col" className="border-b border-line px-2 py-3 font-semibold">Task</th>
            {showProject && <th scope="col" className="border-b border-line px-2 py-3 font-semibold">Project</th>}
            <th scope="col" className="border-b border-line px-2 py-3 font-semibold">Status</th>
            <th scope="col" className="border-b border-line px-2 py-3 font-semibold">Priority</th>
            <th scope="col" className="border-b border-line px-2 py-3 font-semibold">Assignee</th>
            <th scope="col" className="border-b border-line px-4 py-3 font-semibold">Due</th>
          </tr>
        </thead>
        <tbody>
          {tasks.map((t) => (
            <tr key={t.id} className={selected?.has(t.id) ? 'bg-accent-soft' : ''}>
              {onToggle && (
                <td className="border-b border-line px-4 py-2.5">
                  <input type="checkbox" checked={selected?.has(t.id) ?? false} onChange={() => onToggle(t.id)} aria-label={`Select ${t.key}`} className="h-4 w-4 accent-[#007BA7]" />
                </td>
              )}
              <td className="border-b border-line px-2 py-2.5 font-mono text-xs text-muted">{t.key}</td>
              <td className="border-b border-line px-2 py-2.5"><Link to={`/tasks/${t.id}`} className="font-medium hover:text-accent">{t.title}</Link></td>
              {showProject && (
                <td className="border-b border-line px-2 py-2.5 text-ink-2">
                  <span className="inline-flex items-center gap-1.5"><span className="h-2 w-2 rounded-sm" style={{ background: t.projectColor }} />{t.projectName}</span>
                </td>
              )}
              <td className="border-b border-line px-2 py-2.5 text-ink-2">
                <span className="inline-flex items-center gap-1.5"><StatusDot color={t.statusColor} />{t.statusName}{t.pendingApprovalStatusId && <span className="text-xs text-warn">· approval</span>}</span>
              </td>
              <td className="border-b border-line px-2 py-2.5"><PriorityBadge priority={t.priority} /></td>
              <td className="border-b border-line px-2 py-2.5 text-ink-2">
                <span className="inline-flex items-center gap-2"><Avatar user={t.assignee} size={22} />{t.assignee?.displayName ?? 'Unassigned'}</span>
              </td>
              <td className={`border-b border-line px-4 py-2.5 ${t.overdue ? 'font-semibold text-rose' : 'text-ink-2'}`}>
                {t.dueDate ? (t.overdue ? `Overdue · ${shortDate(t.dueDate)}` : shortDate(t.dueDate)) : '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
