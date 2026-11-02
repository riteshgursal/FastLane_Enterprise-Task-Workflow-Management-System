import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, errorMessage } from '../lib/api';
import { relativeTime } from '../lib/format';
import type { Invitation, WorkspaceRole } from '../lib/types';
import { useMembers } from '../lib/queries';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useWorkspace } from '../context/WorkspaceContext';
import { Avatar, Button, Card, Field, Modal, PageHeader, Spinner, inputClass } from '../components/ui';

const ROLE_CLASS: Record<WorkspaceRole, string> = { OWNER: 'bg-rose-soft text-rose', ADMIN: 'bg-accent-soft text-accent', MEMBER: 'bg-chip text-ink-2' };

export function MembersPage() {
  const { workspace } = useWorkspace();
  const ws = workspace!.id;
  const isAdmin = workspace!.myRole !== 'MEMBER';
  const { user } = useAuth();
  const members = useMembers(ws);
  const invites = useQuery({ queryKey: ['invites', ws], queryFn: () => api<Invitation[]>(`/workspaces/${ws}/invitations`) });
  const [inviteOpen, setInviteOpen] = useState(false);
  const qc = useQueryClient();
  const toast = useToast();
  const refresh = () => { qc.invalidateQueries({ queryKey: ['members', ws] }); qc.invalidateQueries({ queryKey: ['invites', ws] }); };
  const act = (fn: () => Promise<unknown>, ok: string) => fn().then(() => { toast(ok); refresh(); }).catch((e) => toast(errorMessage(e)));

  return (
    <>
      <PageHeader title="Members" subtitle={`${workspace!.name} · ${members.data?.length ?? 0} members · ${invites.data?.length ?? 0} pending`}
        actions={isAdmin && <Button variant="primary" onClick={() => setInviteOpen(true)}>Invite member</Button>} />
      {members.isLoading ? <Spinner /> : (
        <div className="overflow-x-auto rounded-[14px] border border-line bg-surface">
          <table className="w-full min-w-[760px] border-collapse text-sm">
            <thead><tr className="text-left text-xs text-muted">
              <th scope="col" className="border-b border-line px-4 py-3 font-semibold">Name</th>
              <th scope="col" className="border-b border-line px-2 py-3 font-semibold">Workspace role</th>
              <th scope="col" className="border-b border-line px-2 py-3 font-semibold">Open tasks</th>
              <th scope="col" className="border-b border-line px-2 py-3 font-semibold">Joined</th>
              <th scope="col" className="border-b border-line px-4 py-3"><span className="sr-only">Actions</span></th>
            </tr></thead>
            <tbody>
              {members.data?.map((m) => (
                <tr key={m.userId}>
                  <td className="border-b border-line px-4 py-2.5">
                    <span className="flex items-center gap-2.5"><Avatar user={{ id: m.userId, initials: m.initials, displayName: m.displayName }} size={34} />
                      <span className="flex flex-col"><span className="font-semibold">{m.displayName}{m.userId === user?.id && <span className="font-normal text-muted"> (you)</span>}</span><span className="text-xs text-muted">{m.email}</span></span></span>
                  </td>
                  <td className="border-b border-line px-2 py-2.5">
                    {isAdmin && m.userId !== user?.id ? (
                      <select aria-label={`Role for ${m.displayName}`} value={m.role} className={`rounded-md px-2 py-1 text-xs font-semibold ${ROLE_CLASS[m.role]}`}
                        onChange={(e) => act(() => api(`/workspaces/${ws}/members/${m.userId}`, { method: 'PATCH', body: { role: e.target.value } }), 'Role updated')}>
                        <option value="MEMBER">Member</option><option value="ADMIN">Admin</option>{workspace!.myRole === 'OWNER' && <option value="OWNER">Owner</option>}
                      </select>
                    ) : <span className={`rounded-md px-2 py-1 text-xs font-semibold ${ROLE_CLASS[m.role]}`}>{m.role[0] + m.role.slice(1).toLowerCase()}</span>}
                  </td>
                  <td className="border-b border-line px-2 py-2.5 text-ink-2">{m.openTasks}</td>
                  <td className="border-b border-line px-2 py-2.5 text-ink-2">{relativeTime(m.joinedAt)}</td>
                  <td className="border-b border-line px-4 py-2.5 text-right">
                    {(isAdmin || m.userId === user?.id) && m.role !== 'OWNER' && (
                      <Button variant="danger" className="min-h-8 px-3 text-xs" onClick={() => confirm(`Remove ${m.displayName} from ${workspace!.name}?`) &&
                        act(() => api(`/workspaces/${ws}/members/${m.userId}`, { method: 'DELETE' }), `${m.displayName} removed`)}>
                        {m.userId === user?.id ? 'Leave' : 'Remove'}
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
              {invites.data?.map((i) => (
                <tr key={i.id}>
                  <td className="border-b border-line px-4 py-2.5"><span className="flex flex-col"><span className="font-semibold">{i.email}</span><span className="text-xs text-warn">Invitation pending · expires {relativeTime(i.expiresAt).replace(' ago', '')}</span></span></td>
                  <td className="border-b border-line px-2 py-2.5"><span className={`rounded-md px-2 py-1 text-xs font-semibold ${ROLE_CLASS[i.role]}`}>{i.role[0] + i.role.slice(1).toLowerCase()}</span></td>
                  <td className="border-b border-line px-2 py-2.5 text-muted">—</td>
                  <td className="border-b border-line px-2 py-2.5 text-muted">Invited {relativeTime(i.createdAt)}</td>
                  <td className="border-b border-line px-4 py-2.5 text-right">
                    {isAdmin && (
                      <span className="inline-flex gap-2">
                        <Button className="min-h-8 px-3 text-xs" onClick={() => act(() => api(`/workspaces/${ws}/invitations/${i.id}/resend`, { method: 'POST' }), `Invitation resent to ${i.email}`)}>Resend</Button>
                        <Button variant="danger" className="min-h-8 px-3 text-xs" onClick={() => act(() => api(`/workspaces/${ws}/invitations/${i.id}`, { method: 'DELETE' }), 'Invitation revoked')}>Revoke</Button>
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="grid grid-cols-1 gap-3.5 md:grid-cols-3">
        {[
          ['Workspace roles', 'Owner · Admin · Member', 'Admins invite people and see every project. Owners can also rename, archive and transfer the workspace.'],
          ['Project roles', 'Manager · Contributor · Viewer', 'Set per project. Managers approve transitions, edit the workflow and manage project members.'],
          ['Isolation', 'Workspace boundary', 'Nobody sees data from a workspace they don’t belong to, even with a direct link.'],
        ].map(([scope, roles, desc]) => (
          <Card key={scope} className="flex flex-col gap-1.5 p-4"><span className="text-[11px] font-semibold text-muted">{scope}</span><span className="font-semibold">{roles}</span><span className="text-[13px] leading-snug text-ink-2">{desc}</span></Card>
        ))}
      </div>
      {inviteOpen && <InviteModal workspaceId={ws} workspaceName={workspace!.name} onClose={() => setInviteOpen(false)} onDone={refresh} />}
    </>
  );
}

function InviteModal({ workspaceId, workspaceName, onClose, onDone }: { workspaceId: string; workspaceName: string; onClose: () => void; onDone: () => void }) {
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<WorkspaceRole>('MEMBER');
  const [error, setError] = useState<string | null>(null);
  const toast = useToast();
  const send = useMutation({
    mutationFn: () => api(`/workspaces/${workspaceId}/invitations`, { body: { email, role } }),
    onSuccess: () => { toast(`Invitation email sent to ${email}`); onDone(); onClose(); },
    onError: (e) => setError(errorMessage(e)),
  });
  return (
    <Modal title={`Invite to ${workspaceName}`} onClose={onClose} width="max-w-md">
      <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return setError('Enter a valid email address.'); send.mutate(); }}>
        <Field label="Email" error={error ?? undefined}><input type="email" className={inputClass} value={email} onChange={(e) => { setEmail(e.target.value); setError(null); }} placeholder="teammate@company.com" /></Field>
        <Field label="Workspace role">
          <select className={inputClass} value={role} onChange={(e) => setRole(e.target.value as WorkspaceRole)}><option value="MEMBER">Member</option><option value="ADMIN">Admin</option></select>
        </Field>
        <p className="text-[13px] text-muted">They’ll get an email with a link that expires in 7 days.</p>
        <div className="flex justify-end gap-2"><Button onClick={onClose}>Cancel</Button><Button type="submit" variant="primary" disabled={send.isPending}>Send invite</Button></div>
      </form>
    </Modal>
  );
}
