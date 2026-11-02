package com.flowlane.project;

import com.flowlane.common.exception.ApiException;
import com.flowlane.common.exception.ErrorCode;
import com.flowlane.common.security.AccessGuard;
import com.flowlane.common.security.CurrentUser;
import com.flowlane.identity.User;
import com.flowlane.identity.UserRepository;
import com.flowlane.project.ProjectDtos.CreateProjectRequest;
import com.flowlane.project.ProjectDtos.ProjectDto;
import com.flowlane.project.ProjectDtos.ProjectMemberDto;
import com.flowlane.project.ProjectDtos.UpdateProjectRequest;
import com.flowlane.task.TaskRepository;
import com.flowlane.workflow.WorkflowService;
import com.flowlane.workspace.WorkspaceMemberRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private static final List<String> PALETTE = List.of("#1DA1D8", "#FF2E7E", "#F5B544", "#4AD08A", "#A78BFA", "#007BA7");

    private final ProjectRepository projects;
    private final ProjectMemberRepository projectMembers;
    private final WorkspaceMemberRepository workspaceMembers;
    private final UserRepository users;
    private final TaskRepository tasks;
    private final WorkflowService workflow;
    private final AccessGuard guard;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository projectMembers,
                          WorkspaceMemberRepository workspaceMembers, UserRepository users, TaskRepository tasks,
                          WorkflowService workflow, AccessGuard guard, Clock clock) {
        this.projects = projects;
        this.projectMembers = projectMembers;
        this.workspaceMembers = workspaceMembers;
        this.users = users;
        this.tasks = tasks;
        this.workflow = workflow;
        this.guard = guard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> list(UUID workspaceId) {
        guard.requireWorkspaceMember(workspaceId);
        UUID me = CurrentUser.id();
        Set<UUID> visible = guard.visibleProjectIds(workspaceId, me);
        return projects.findByWorkspaceIdAndArchivedAtIsNullOrderByName(workspaceId).stream()
                .filter(p -> visible.contains(p.getId()))
                .map(p -> toDto(p, guard.projectRole(p, me)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDto get(UUID projectId) {
        Project p = guard.requireProject(projectId, ProjectRole.VIEWER);
        return toDto(p, guard.projectRole(p, CurrentUser.id()));
    }

    @Transactional
    public ProjectDto create(UUID workspaceId, CreateProjectRequest req) {
        guard.requireWorkspaceMember(workspaceId);
        if (projects.existsByWorkspaceIdAndProjectKey(workspaceId, req.key())) {
            throw new ApiException(ErrorCode.PROJECT_KEY_TAKEN);
        }
        Project p = new Project();
        p.setWorkspaceId(workspaceId);
        p.setProjectKey(req.key());
        p.setName(req.name().trim());
        p.setDescription(req.description());
        long count = projects.countByWorkspaceIdAndArchivedAtIsNull(workspaceId);
        p.setColor(req.color() != null ? req.color() : PALETTE.get((int) (count % PALETTE.size())));
        p.setStartDate(req.startDate());
        p.setEndDate(req.endDate());
        projects.save(p);
        projectMembers.save(new ProjectMember(workspaceId, p.getId(), CurrentUser.id(), ProjectRole.MANAGER));
        workflow.createDefaultWorkflow(p);
        return toDto(p, ProjectRole.MANAGER);
    }

    @Transactional
    public ProjectDto update(UUID projectId, UpdateProjectRequest req) {
        Project p = guard.requireProject(projectId, ProjectRole.MANAGER);
        if (req.name() != null) p.setName(req.name().trim());
        if (req.description() != null) p.setDescription(req.description());
        if (req.color() != null) p.setColor(req.color());
        if (req.startDate() != null) p.setStartDate(req.startDate());
        if (req.endDate() != null) p.setEndDate(req.endDate());
        return toDto(p, ProjectRole.MANAGER);
    }

    @Transactional
    public void archive(UUID projectId) {
        guard.requireProject(projectId, ProjectRole.MANAGER).setArchivedAt(clock.instant());
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberDto> members(UUID projectId) {
        guard.requireProject(projectId, ProjectRole.VIEWER);
        List<ProjectMember> list = projectMembers.findByProjectId(projectId);
        Map<UUID, User> byId = users.findAllById(list.stream().map(ProjectMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return list.stream().filter(m -> byId.containsKey(m.getUserId())).map(m -> {
            User u = byId.get(m.getUserId());
            return new ProjectMemberDto(u.getId(), u.getDisplayName(), u.initials(), u.getEmail(), m.getRole());
        }).toList();
    }

    @Transactional
    public void addMember(UUID projectId, UUID userId, ProjectRole role) {
        Project p = guard.requireProject(projectId, ProjectRole.MANAGER);
        if (workspaceMembers.findByWorkspaceIdAndUserId(p.getWorkspaceId(), userId).isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Add the person to the workspace first.");
        }
        ProjectMember m = projectMembers.findByProjectIdAndUserId(projectId, userId)
                .orElseGet(() -> new ProjectMember(p.getWorkspaceId(), projectId, userId, role));
        m.setRole(role);
        projectMembers.save(m);
    }

    @Transactional
    public void removeMember(UUID projectId, UUID userId) {
        guard.requireProject(projectId, ProjectRole.MANAGER);
        projectMembers.findByProjectIdAndUserId(projectId, userId).ifPresent(projectMembers::delete);
    }

    private ProjectDto toDto(Project p, ProjectRole role) {
        LocalDate today = LocalDate.now(clock);
        return new ProjectDto(p.getId(), p.getWorkspaceId(), p.getProjectKey(), p.getName(), p.getDescription(), p.getColor(),
                p.getStartDate(), p.getEndDate(), role, tasks.countByProject(p.getId()), tasks.countDoneByProject(p.getId()),
                tasks.countOverdueByProject(p.getId(), today));
    }
}
