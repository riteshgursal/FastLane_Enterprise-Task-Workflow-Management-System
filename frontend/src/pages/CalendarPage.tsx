import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { toIsoDate } from '../lib/format';
import type { Task } from '../lib/types';
import { useProjects } from '../lib/queries';
import { useWorkspace } from '../context/WorkspaceContext';
import { Button, Card, PageHeader, Segmented } from '../components/ui';

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

export function CalendarPage() {
  const { workspace } = useWorkspace();
  const ws = workspace!.id;
  const { data: projects = [] } = useProjects(ws);
  const today = new Date();
  const [cursor, setCursor] = useState(new Date(today.getFullYear(), today.getMonth(), 1));
  const [mode, setMode] = useState<'month' | 'week'>('month');

  const { start, end, days } = useMemo(() => {
    if (mode === 'week') {
      const wkStart = new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate() - cursor.getDay());
      const d = Array.from({ length: 7 }, (_, i) => new Date(wkStart.getFullYear(), wkStart.getMonth(), wkStart.getDate() + i));
      return { start: d[0], end: d[6], days: d };
    }
    const first = new Date(cursor.getFullYear(), cursor.getMonth(), 1);
    const gridStart = new Date(first.getFullYear(), first.getMonth(), 1 - first.getDay());
    const last = new Date(cursor.getFullYear(), cursor.getMonth() + 1, 0);
    const cells = Math.ceil((first.getDay() + last.getDate()) / 7) * 7;
    const d = Array.from({ length: cells }, (_, i) => new Date(gridStart.getFullYear(), gridStart.getMonth(), gridStart.getDate() + i));
    return { start: d[0], end: d[d.length - 1], days: d };
  }, [cursor, mode]);

  const tasks = useQuery({
    queryKey: ['calendar', ws, toIsoDate(start), toIsoDate(end)],
    queryFn: () => api<Task[]>(`/workspaces/${ws}/calendar?from=${toIsoDate(start)}&to=${toIsoDate(end)}`),
  });
  const byDay = new Map<string, Task[]>();
  tasks.data?.forEach((t) => t.dueDate && byDay.set(t.dueDate, [...(byDay.get(t.dueDate) ?? []), t]));
  const todayIso = toIsoDate(today);
  const shift = (n: number) => setCursor(mode === 'month' ? new Date(cursor.getFullYear(), cursor.getMonth() + n, 1) : new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate() + 7 * n));
  const label = mode === 'month' ? `${MONTHS[cursor.getMonth()]} ${cursor.getFullYear()}` : `Week of ${MONTHS[start.getMonth()].slice(0, 3)} ${start.getDate()}`;
  const recurring = (tasks.data ?? []).filter((t) => t.recurrence !== 'NONE');

  return (
    <>
      <PageHeader title="Calendar" subtitle="Deadlines and recurring work"
        actions={<>
          <Button aria-label="Previous" onClick={() => shift(-1)}>‹</Button>
          <span className="min-w-[160px] text-center font-display text-lg font-bold">{label}</span>
          <Button aria-label="Next" onClick={() => shift(1)}>›</Button>
          <Button onClick={() => setCursor(mode === 'month' ? new Date(today.getFullYear(), today.getMonth(), 1) : new Date())}>Today</Button>
          <Segmented label="Calendar view" value={mode} onChange={(m) => { setMode(m); setCursor(m === 'month' ? new Date(cursor.getFullYear(), cursor.getMonth(), 1) : new Date()); }}
            options={[{ value: 'month', label: 'Month' }, { value: 'week', label: 'Week' }]} />
        </>} />
      <div className="flex flex-wrap gap-3.5 text-xs text-ink-2">
        {projects.map((p) => <span key={p.id} className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-[3px]" style={{ background: p.color }} />{p.name}</span>)}
        <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-[3px] bg-rose-soft ring-1 ring-rose" />Overdue</span>
      </div>
      <div className="overflow-x-auto rounded-[14px] border border-line bg-surface">
        <div className="grid min-w-[840px] grid-cols-7">
          {WEEKDAYS.map((d) => <div key={d} className="border-b border-line px-3 py-2.5 text-xs font-semibold text-muted">{d}</div>)}
          {days.map((d) => {
            const iso = toIsoDate(d);
            const inMonth = mode === 'week' || d.getMonth() === cursor.getMonth();
            const items = byDay.get(iso) ?? [];
            const max = mode === 'week' ? 12 : 3;
            return (
              <div key={iso} className={`flex flex-col gap-1.5 border-r border-b border-line p-2 ${mode === 'week' ? 'min-h-[360px]' : 'min-h-[118px]'} ${inMonth ? '' : 'bg-surface-2'}`}>
                <span className={`flex h-6.5 w-6.5 items-center justify-center rounded-full text-[13px] font-semibold ${iso === todayIso ? 'bg-rose-btn text-white' : inMonth ? '' : 'text-muted'}`}>{d.getDate()}</span>
                {items.slice(0, max).map((t) => (
                  <Link key={t.id} to={`/tasks/${t.id}`} title={t.title}
                    className={`flex min-h-6 items-center gap-1.5 overflow-hidden border-l-[3px] px-1.5 py-0.5 text-[11px] ${t.overdue ? 'bg-rose-soft' : 'bg-surface-2'}`}
                    style={{ borderLeftColor: t.projectColor }}>
                    <span className={`font-mono ${t.overdue ? 'text-rose' : 'text-muted'}`}>{t.key}</span>
                    <span className="truncate">{t.recurrence !== 'NONE' ? '↻ ' : ''}{t.title}</span>
                  </Link>
                ))}
                {items.length > max && <span className="text-[11px] text-muted">+{items.length - max} more</span>}
              </div>
            );
          })}
        </div>
      </div>
      <div className="grid grid-cols-1 gap-3.5 md:grid-cols-2">
        <Card className="flex flex-col gap-1.5 p-5">
          <h2 className="text-[15px] font-semibold">Recurring tasks in view</h2>
          {recurring.length === 0 && <p className="text-sm text-muted">None. Set “Repeat” on a task to create the next one automatically when it’s done.</p>}
          {recurring.map((t) => (
            <Link key={t.id} to={`/tasks/${t.id}`} className="flex min-h-10 items-center justify-between text-sm">
              <span>↻ {t.title} <span className="font-mono text-xs text-muted">{t.key}</span></span>
              <span className="text-muted">{t.recurrence === 'WEEKLY' ? 'Every week' : 'Every month'}</span>
            </Link>
          ))}
        </Card>
        <Card className="flex flex-col gap-1.5 p-5">
          <h2 className="text-[15px] font-semibold">Reminders</h2>
          <p className="text-sm leading-relaxed text-ink-2">Assignees get an in-app and email reminder the day before a due date, and a daily alert while a task is overdue. <Link to="/settings" className="font-semibold text-accent">Change in settings</Link></p>
        </Card>
      </div>
    </>
  );
}
