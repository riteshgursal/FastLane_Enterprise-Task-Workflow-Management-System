import type { ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import { WorkspaceProvider } from './context/WorkspaceContext';
import { AppShell } from './layouts/AppShell';
import { Spinner } from './components/ui';
import { LoginPage } from './pages/LoginPage';
import { AcceptInvitePage } from './pages/AcceptInvitePage';
import { DashboardPage } from './pages/DashboardPage';
import { MyTasksPage } from './pages/MyTasksPage';
import { BoardPage } from './pages/BoardPage';
import { TaskDetailPage } from './pages/TaskDetailPage';
import { CalendarPage } from './pages/CalendarPage';
import { MembersPage } from './pages/MembersPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { ReportsPage } from './pages/ReportsPage';
import { WorkflowPage } from './pages/WorkflowPage';
import { SettingsPage } from './pages/SettingsPage';
import { NotFoundPage } from './pages/NotFoundPage';

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <Spinner label="Signing you in" />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <>{children}</>;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage mode="signin" />} />
      <Route path="/register" element={<LoginPage mode="register" />} />
      <Route path="/invite/:token" element={<AcceptInvitePage />} />
      <Route element={<RequireAuth><WorkspaceProvider><AppShell /></WorkspaceProvider></RequireAuth>}>
        <Route index element={<DashboardPage />} />
        <Route path="my-tasks" element={<MyTasksPage />} />
        <Route path="calendar" element={<CalendarPage />} />
        <Route path="reports" element={<ReportsPage />} />
        <Route path="members" element={<MembersPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="projects/:projectId" element={<BoardPage />} />
        <Route path="projects/:projectId/workflow" element={<WorkflowPage />} />
        <Route path="tasks/:taskId" element={<TaskDetailPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
