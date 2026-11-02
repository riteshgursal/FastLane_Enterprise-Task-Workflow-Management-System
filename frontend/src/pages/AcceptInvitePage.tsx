import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, errorMessage } from '../lib/api';
import type { InvitationPreview, Workspace } from '../lib/types';
import { useAuth } from '../context/AuthContext';
import { Logo } from '../components/Logo';
import { Button, Spinner } from '../components/ui';

export function AcceptInvitePage() {
  const { token = '' } = useParams();
  const { user } = useAuth();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const preview = useQuery({ queryKey: ['invite', token], queryFn: () => api<InvitationPreview>(`/invitations/${token}`) });
  const accept = useMutation({
    mutationFn: () => api<Workspace>(`/invitations/${token}/accept`, { method: 'POST' }),
    onSuccess: async (w) => {
      await qc.invalidateQueries({ queryKey: ['workspaces'] });
      localStorage.setItem('fl_ws', w.id);
      navigate('/');
    },
  });

  return (
    <div className="flex min-h-full items-center justify-center p-6">
      <div className="flex w-full max-w-md flex-col gap-6">
        <Logo />
        {preview.isLoading && <Spinner />}
        {preview.data && !preview.data.valid && <p className="text-ink-2">This invitation is invalid or has expired. Ask for a new one.</p>}
        {preview.data?.valid && (
          <>
            <h1 className="font-display text-3xl font-bold tracking-tight">Join {preview.data.workspaceName}</h1>
            <p className="text-sm text-ink-2">The invitation was sent to <b>{preview.data.email}</b>. Sign in or create an account with that email to accept it.</p>
            {user ? (
              <Button variant="primary" onClick={() => accept.mutate()} disabled={accept.isPending}>Accept invitation</Button>
            ) : (
              <div className="flex gap-2">
                <Link to="/login" state={{ from: `/invite/${token}` }} className="flex min-h-10 flex-1 items-center justify-center rounded-[10px] bg-cerulean text-sm font-semibold text-white">Sign in</Link>
                <Link to="/register" state={{ from: `/invite/${token}` }} className="flex min-h-10 flex-1 items-center justify-center rounded-[10px] border border-line text-sm font-semibold">Create account</Link>
              </div>
            )}
            {accept.error && <p className="text-sm text-rose">{errorMessage(accept.error)}</p>}
          </>
        )}
      </div>
    </div>
  );
}
