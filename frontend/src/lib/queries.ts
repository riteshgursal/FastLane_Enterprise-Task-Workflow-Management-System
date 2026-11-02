import { useQuery } from '@tanstack/react-query';
import { api } from './api';
import type { Member, Project, Workspace } from './types';

export function useWorkspaces() {
  return useQuery({ queryKey: ['workspaces'], queryFn: () => api<Workspace[]>('/workspaces') });
}

export function useProjects(workspaceId: string | undefined) {
  return useQuery({
    queryKey: ['projects', workspaceId],
    queryFn: () => api<Project[]>(`/workspaces/${workspaceId}/projects`),
    enabled: !!workspaceId,
  });
}

export function useMembers(workspaceId: string | undefined) {
  return useQuery({
    queryKey: ['members', workspaceId],
    queryFn: () => api<Member[]>(`/workspaces/${workspaceId}/members`),
    enabled: !!workspaceId,
  });
}

export function useUnreadCount() {
  return useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => api<{ count: number }>('/notifications/unread-count'),
    refetchInterval: 30_000,
  });
}
