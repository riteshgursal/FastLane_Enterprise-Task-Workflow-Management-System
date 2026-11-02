import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../lib/api';
import { relativeTime } from '../lib/format';
import type { Notification, NotificationType, Page } from '../lib/types';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { Button, Card, Empty, PageHeader, Spinner } from '../components/ui';

const GLYPH: Record<NotificationType, [string, string]> = {
  MENTION: ['@', 'bg-accent-soft text-accent'], APPROVAL: ['✓', 'bg-warn-soft text-warn'], ASSIGNED: ['→', 'bg-rose-soft text-rose'],
  STATUS: ['↔', 'bg-chip text-ink-2'], COMMENT: ['”', 'bg-chip text-ink-2'], DUE: ['!', 'bg-warn-soft text-warn'], OVERDUE: ['!', 'bg-rose-soft text-rose'],
};

type Filter = 'all' | 'unread' | 'MENTION' | 'APPROVAL';

export function NotificationsPage() {
  const [filter, setFilter] = useState<Filter>('all');
  const { user } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const navigate = useNavigate();
  const params = filter === 'unread' ? 'unread=true' : filter === 'all' ? '' : `type=${filter}`;
  const list = useQuery({ queryKey: ['notifications', 'list', filter], queryFn: () => api<Page<Notification>>(`/notifications?${params}`) });
  const refresh = () => qc.invalidateQueries({ queryKey: ['notifications'] });
  const readAll = useMutation({ mutationFn: () => api('/notifications/read-all', { method: 'POST' }), onSuccess: () => { toast('All notifications marked as read'); refresh(); } });

  const open = async (n: Notification) => {
    if (!n.read) await api(`/notifications/${n.id}/read`, { method: 'PATCH' }).catch(() => undefined);
    refresh();
    if (n.taskId) navigate(`/tasks/${n.taskId}`);
  };

  const prefs = user ? [['Assigned to me', user.notifyAssigned], ['Mentions', user.notifyMentions], ['Due-date reminders', user.notifyDue], ['Approval requests', user.notifyApprovals]] as const : [];

  return (
    <>
      <PageHeader title="Notifications" actions={<Button variant="ghost" onClick={() => readAll.mutate()}>Mark all as read</Button>} />
      <div className="flex flex-wrap items-start gap-5">
        <Card className="flex min-w-0 flex-[999_1_460px] flex-col gap-1.5 p-3.5">
          <div className="flex flex-wrap gap-2 px-1 pb-2.5">
            {([['all', 'All'], ['unread', 'Unread'], ['MENTION', 'Mentions'], ['APPROVAL', 'Approvals']] as [Filter, string][]).map(([k, label]) => (
              <button key={k} type="button" aria-pressed={filter === k} onClick={() => setFilter(k)}
                className={`min-h-8 rounded-full border px-3 text-[13px] font-semibold ${filter === k ? 'border-accent bg-accent-soft text-accent' : 'border-line bg-surface text-ink-2'}`}>{label}</button>
            ))}
          </div>
          {list.isLoading && <Spinner />}
          {list.data?.content.length === 0 && <Empty title="You’re all caught up" body="Assignments, mentions and approvals will show up here." />}
          {list.data?.content.map((n) => (
            <button key={n.id} type="button" onClick={() => open(n)} className={`flex items-start gap-3 rounded-[10px] p-3 text-left ${n.read ? '' : 'bg-surface-2'}`}>
              <span className={`flex h-9 w-9 flex-none items-center justify-center rounded-[10px] text-sm font-bold ${GLYPH[n.type][1]}`}>{GLYPH[n.type][0]}</span>
              <span className="flex flex-1 flex-col gap-0.5"><span className="text-sm leading-snug">{n.message}</span><span className="text-xs text-muted">{relativeTime(n.createdAt)}</span></span>
              {!n.read && <span className="mt-1.5 h-2.5 w-2.5 rounded-full bg-rose-btn" aria-label="Unread" />}
            </button>
          ))}
        </Card>
        <Card className="flex min-w-0 flex-[1_1_280px] flex-col gap-2.5 p-5">
          <h2 className="text-[15px] font-semibold">Email delivery</h2>
          {prefs.map(([label, on]) => (
            <div key={label} className="flex min-h-8 items-center justify-between text-sm"><span className="text-ink-2">{label}</span><span className={`font-semibold ${on ? 'text-ok' : 'text-muted'}`}>{on ? 'On' : 'Off'}</span></div>
          ))}
          <Link to="/settings" className="mt-1 flex min-h-10 items-center justify-center rounded-[10px] border border-line bg-surface-2 text-[13px] font-semibold">Manage email preferences</Link>
        </Card>
      </div>
    </>
  );
}
