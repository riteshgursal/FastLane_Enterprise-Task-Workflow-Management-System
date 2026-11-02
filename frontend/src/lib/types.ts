export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type StatusCategory = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED';
export type Recurrence = 'NONE' | 'WEEKLY' | 'MONTHLY';
export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'MEMBER';
export type ProjectRole = 'MANAGER' | 'CONTRIBUTOR' | 'VIEWER';
export type Theme = 'dark' | 'light' | 'system';
export type NotificationType = 'ASSIGNED' | 'MENTION' | 'COMMENT' | 'STATUS' | 'APPROVAL' | 'DUE' | 'OVERDUE';

export interface User {
  id: string; email: string; displayName: string; initials: string; timezone: string; theme: Theme;
  notifyAssigned: boolean; notifyMentions: boolean; notifyDue: boolean; notifyApprovals: boolean; dailyDigest: boolean;
  activeSessions: number;
}
export interface AuthResponse { accessToken: string; expiresIn: number; user: User }
export interface UserRef { id: string; displayName: string; initials: string }

export interface Workspace { id: string; name: string; slug: string; myRole: WorkspaceRole; projectCount: number; memberCount: number }
export interface Member { userId: string; displayName: string; initials: string; email: string; role: WorkspaceRole; openTasks: number; joinedAt: string }
export interface Invitation { id: string; email: string; role: WorkspaceRole; expiresAt: string; createdAt: string }
export interface InvitationPreview { workspaceName: string; email: string; role: WorkspaceRole; valid: boolean }

export interface Project {
  id: string; workspaceId: string; key: string; name: string; description: string | null; color: string;
  startDate: string | null; endDate: string | null; myRole: ProjectRole; totalTasks: number; doneTasks: number; overdueTasks: number;
}

export interface Task {
  id: string; key: string; projectId: string; projectKey: string; projectName: string; projectColor: string;
  title: string; description: string | null; statusId: string; statusName: string; statusColor: string; category: StatusCategory;
  priority: Priority; assignee: UserRef | null; reporter: UserRef; parentId: string | null;
  startDate: string | null; dueDate: string | null; estimatePoints: number | null; labels: string[]; recurrence: Recurrence;
  rank: string; completedAt: string | null; overdue: boolean; pendingApprovalStatusId: string | null; pendingApprovalStatusName: string | null;
  subtaskCount: number; subtaskDone: number; version: number; createdAt: string; updatedAt: string;
}
export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface BoardColumn { statusId: string; name: string; color: string; category: StatusCategory; tasks: Task[] }
export interface Board { projectId: string; columns: BoardColumn[] }

export interface Status { id: string; name: string; category: StatusCategory; color: string; position: number; initial: boolean; taskCount: number }
export interface Transition { fromStatusId: string; toStatusId: string; requiresApproval: boolean }
export interface Workflow { projectId: string; statuses: Status[]; transitions: Transition[] }

export interface HistoryEntry { id: string; actorName: string; actorInitials: string; action: string; detail: string; at: string; taskKey: string | null; taskId: string }
export interface Comment { id: string; author: UserRef | null; body: string; createdAt: string; editedAt: string | null; mine: boolean }
export interface Attachment { id: string; fileName: string; contentType: string; sizeBytes: number; uploader: UserRef | null; createdAt: string }
export interface Dependency { relation: 'BLOCKED_BY' | 'BLOCKS'; task: Task }
export interface Notification { id: string; type: NotificationType; message: string; taskId: string | null; taskKey: string | null; actor: UserRef | null; read: boolean; createdAt: string }

export interface Overview {
  byCategory: Record<StatusCategory, number>; overdue: number; completedThisWeek: number; completedLastWeek: number;
  projects: { id: string; key: string; name: string; color: string; total: number; done: number; overdue: number }[];
  workload: { userId: string; displayName: string; openTasks: number }[];
  completedPerWeek: { weekStart: string; completed: number }[];
}
