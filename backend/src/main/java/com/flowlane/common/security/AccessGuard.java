package com.flowlane.common.security;

import com.flowlane.common.exception.ApiException;
import com.flowlane.project.Project;
import com.flowlane.project.ProjectMember;
import com.flowlane.project.ProjectMemberRepository;
import com.flowlane.project.ProjectRepository;
import com.flowlane.project.ProjectRole;
import com.flowlane.workspace.Workspace;
import com.flowlane.workspace.WorkspaceMember;
import com.flowlane.workspace.WorkspaceMemberRepository;
import com.flowlane.workspace.WorkspaceRepository;
import com.flowlane.workspace.WorkspaceRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Every service checks access here before touching data. Resources in a workspace the user
 * doesn't belong to are reported as 404, never 403, so their existence isn't leaked.
 */
@Component
public class AccessGuard {

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository workspaceMembers;
    private final ProjectRepository projects;
    private final ProjectMemberRepository projectMembers;

    public AccessGuard(WorkspaceRepository workspaces, WorkspaceMemberRepository workspaceMembers,
                       ProjectRepository projects, ProjectMemberRepository projectMembers) {
        this.workspaces = workspaces;
        this.workspaceMembers = workspaceMembers;
        this.projects = projects;
        this.projectMembers = projectMembers;
    }

    public WorkspaceMember requireWorkspaceMember(UUID workspaceId) {
        WorkspaceMember m = workspaceMembers.findByWorkspaceIdAndUserId(workspaceId, CurrentUser.id())
                .orElseThrow(() -> ApiException.notFound("Workspace"));
        workspaceUnchecked(workspaceId);
        return m;
    }

    public WorkspaceMember requireWorkspaceRole(UUID workspaceId, WorkspaceRole min) {
        WorkspaceMember m = requireWorkspaceMember(workspaceId);
        if (!m.getRole().atLeast(min)) throw ApiException.forbidden();
        return m;
    }

    public Workspace workspace(UUID workspaceId) {
        requireWorkspaceMember(workspaceId);
        return workspaceUnchecked(workspaceId);
    }

    public Workspace workspaceUnchecked(UUID workspaceId) {
        return workspaces.findById(workspaceId).filter(w -> w.getArchivedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Workspace"));
    }

    /** The effective project role, or null if the user can't see the project. */
    public ProjectRole projectRole(Project project, UUID userId) {
        WorkspaceMember wm = workspaceMembers.findByWorkspaceIdAndUserId(project.getWorkspaceId(), userId).orElse(null);
        if (wm == null) return null;
        if (wm.getRole().atLeast(WorkspaceRole.ADMIN)) return ProjectRole.MANAGER;
        return projectMembers.findByProjectIdAndUserId(project.getId(), userId).map(ProjectMember::getRole).orElse(null);
    }

    public Project requireProject(UUID projectId, ProjectRole min) {
        Project p = projects.findById(projectId).filter(x -> x.getArchivedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Project"));
        ProjectRole role = projectRole(p, CurrentUser.id());
        if (role == null) throw ApiException.notFound("Project");
        if (!role.atLeast(min)) throw ApiException.forbidden();
        return p;
    }

    public Set<UUID> visibleProjectIds(UUID workspaceId, UUID userId) {
        WorkspaceMember wm = workspaceMembers.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.notFound("Workspace"));
        if (wm.getRole().atLeast(WorkspaceRole.ADMIN)) {
            return projects.findByWorkspaceIdAndArchivedAtIsNullOrderByName(workspaceId).stream()
                    .map(Project::getId).collect(Collectors.toSet());
        }
        return projectMembers.findByWorkspaceIdAndUserId(workspaceId, userId).stream()
                .map(ProjectMember::getProjectId).collect(Collectors.toSet());
    }

    public void removeFromAllProjects(UUID workspaceId, UUID userId) {
        List<ProjectMember> list = projectMembers.findByWorkspaceIdAndUserId(workspaceId, userId);
        projectMembers.deleteAll(list);
    }
}
