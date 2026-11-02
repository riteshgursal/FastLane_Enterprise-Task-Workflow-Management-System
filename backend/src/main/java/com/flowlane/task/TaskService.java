package com.flowlane.task;

import com.flowlane.common.event.DomainEvents.TaskAssigned;
import com.flowlane.common.exception.ApiException;
import com.flowlane.common.exception.ErrorCode;
import com.flowlane.common.persistence.Rank;
import com.flowlane.common.security.AccessGuard;
import com.flowlane.common.security.CurrentUser;
import com.flowlane.common.web.PageResponse;
import com.flowlane.common.web.Pages;
import com.flowlane.project.Project;
import com.flowlane.project.ProjectRole;
import com.flowlane.task.TaskDtos.BoardColumn;
import com.flowlane.task.TaskDtos.BoardDto;
import com.flowlane.task.TaskDtos.BulkUpdateRequest;
import com.flowlane.task.TaskDtos.CreateTaskRequest;
import com.flowlane.task.TaskDtos.DependencyDto;
import com.flowlane.task.TaskDtos.MoveTaskRequest;
import com.flowlane.task.TaskDtos.TaskDto;
import com.flowlane.task.TaskDtos.UpdateTaskRequest;
import com.flowlane.workflow.HistoryRecorder;
import com.flowlane.workflow.TaskStatus;
import com.flowlane.workflow.TaskStatusRepository;
import com.flowlane.workflow.WorkflowService;
import com.flowlane.workspace.WorkspaceMemberRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private static final Set<String> SORTABLE = Set.of("dueDate", "priority", "number", "createdAt", "updatedAt", "title");

    private final TaskRepository tasks;
    private final TaskDependencyRepository dependencies;
    private final TaskStatusRepository statuses;
    private final WorkspaceMemberRepository workspaceMembers;
    private final TaskFactory factory;
    private final TaskMapper mapper;
    private final WorkflowService workflow;
    private final HistoryRecorder history;
    private final AccessGuard guard;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TaskService(TaskRepository tasks, TaskDependencyRepository dependencies, TaskStatusRepository statuses,
                       WorkspaceMemberRepository workspaceMembers, TaskFactory factory, TaskMapper mapper,
                       WorkflowService workflow, HistoryRecorder history, AccessGuard guard,
                       ApplicationEventPublisher events, Clock clock) {
        this.tasks = tasks;
        this.dependencies = dependencies;
        this.statuses = statuses;
        this.workspaceMembers = workspaceMembers;
        this.factory = factory;
        this.mapper = mapper;
        this.workflow = workflow;
        this.history = history;
        this.guard = guard;
        this.events = events;
        this.clock = clock;
    }

    /** Loads a task the current user can see with at least the given role, or 404s. */
    public Task require(UUID taskId, ProjectRole min) {
        Task t = tasks.findByIdAndDeletedAtIsNull(taskId).orElseThrow(() -> ApiException.notFound("Task"));
        guard.requireProject(t.getProjectId(), min);
        return t;
    }

    @Transactional
    public TaskDto create(UUID projectId, CreateTaskRequest req) {
        Project p = guard.requireProject(projectId, ProjectRole.CONTRIBUTOR);
        UUID actor = CurrentUser.id();
        if (req.parentTaskId() != null) {
            Task parent = require(req.parentTaskId(), ProjectRole.VIEWER);
            if (!parent.getProjectId().equals(projectId) || parent.getParentTaskId() != null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Subtasks go one level deep, in the same project.");
            }
        }
        Task t = factory.newTask(projectId, req.statusId(), req.title(), actor);
        t.setDescription(req.description());
        t.setPriority(req.priority() != null ? req.priority() : Priority.MEDIUM);
        t.setParentTaskId(req.parentTaskId());
        t.setStartDate(req.startDate());
        t.setDueDate(req.dueDate());
        t.setEstimatePoints(req.estimatePoints());
        t.setLabelList(req.labels());
        t.setRecurrence(req.recurrence() != null ? req.recurrence() : Recurrence.NONE);
        if (req.assigneeId() != null) {
            requireAssignable(p, req.assigneeId());
            t.setAssigneeId(req.assigneeId());
        }
        tasks.save(t);
        history.record(t, actor, "CREATED", "created this task");
        if (t.getAssigneeId() != null) {
            events.publishEvent(new TaskAssigned(t.getWorkspaceId(), t.getId(), t.getTaskKey(), t.getTitle(), t.getAssigneeId(), actor));
        }
        return mapper.one(t);
    }

    @Transactional(readOnly = true)
    public TaskDto get(UUID taskId) {
        return mapper.one(require(taskId, ProjectRole.VIEWER));
    }

    @Transactional(readOnly = true)
    public TaskDto getByKey(UUID workspaceId, String key) {
        guard.requireWorkspaceMember(workspaceId);
        Task t = tasks.findByWorkspaceIdAndTaskKeyAndDeletedAtIsNull(workspaceId, key.toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Task"));
        guard.requireProject(t.getProjectId(), ProjectRole.VIEWER);
        return mapper.one(t);
    }

    @Transactional
    public TaskDto update(UUID taskId, UpdateTaskRequest req) {
        Task t = require(taskId, ProjectRole.CONTRIBUTOR);
        UUID actor = CurrentUser.id();
        ProjectRole role = guard.projectRole(guard.requireProject(t.getProjectId(), ProjectRole.CONTRIBUTOR), actor);
        boolean own = actor.equals(t.getReporterId()) || actor.equals(t.getAssigneeId());
        if (role != ProjectRole.MANAGER && !own) throw ApiException.forbidden();
        if (!Objects.equals(req.version(), t.getVersion())) throw new ApiException(ErrorCode.VERSION_CONFLICT);
        List<String> changes = new ArrayList<>();
        if (req.title() != null && !req.title().equals(t.getTitle())) { t.setTitle(req.title().trim()); changes.add("title"); }
        if (req.description() != null && !req.description().equals(t.getDescription())) { t.setDescription(req.description()); changes.add("description"); }
        if (req.priority() != null && req.priority() != t.getPriority()) {
            history.record(t, actor, "PRIORITY_CHANGED", "changed priority from " + t.getPriority() + " to " + req.priority());
            t.setPriority(req.priority());
        }
        if (Boolean.TRUE.equals(req.clearAssignee()) && t.getAssigneeId() != null) {
            t.setAssigneeId(null);
            history.record(t, actor, "UNASSIGNED", "removed the assignee");
        } else if (req.assigneeId() != null && !req.assigneeId().equals(t.getAssigneeId())) {
            if (role != ProjectRole.MANAGER && !req.assigneeId().equals(actor)) throw ApiException.forbidden();
            requireAssignable(guard.requireProject(t.getProjectId(), ProjectRole.CONTRIBUTOR), req.assigneeId());
            t.setAssigneeId(req.assigneeId());
            history.record(t, actor, "ASSIGNED", "changed the assignee");
            events.publishEvent(new TaskAssigned(t.getWorkspaceId(), t.getId(), t.getTaskKey(), t.getTitle(), req.assigneeId(), actor));
        }
        if (req.startDate() != null) t.setStartDate(req.startDate());
        if (Boolean.TRUE.equals(req.clearDueDate())) { t.setDueDate(null); changes.add("due date"); }
        else if (req.dueDate() != null && !req.dueDate().equals(t.getDueDate())) { t.setDueDate(req.dueDate()); changes.add("due date"); }
        if (req.estimatePoints() != null) t.setEstimatePoints(req.estimatePoints());
        if (req.labels() != null) { t.setLabelList(req.labels()); changes.add("labels"); }
        if (req.recurrence() != null && req.recurrence() != t.getRecurrence()) { t.setRecurrence(req.recurrence()); changes.add("repeat"); }
        if (!changes.isEmpty()) history.record(t, actor, "UPDATED", "updated " + String.join(", ", changes));
        tasks.saveAndFlush(t);
        return mapper.one(t);
    }

    @Transactional
    public void delete(UUID taskId) {
        Task t = require(taskId, ProjectRole.CONTRIBUTOR);
        UUID actor = CurrentUser.id();
        ProjectRole role = guard.projectRole(guard.requireProject(t.getProjectId(), ProjectRole.CONTRIBUTOR), actor);
        if (role != ProjectRole.MANAGER && !actor.equals(t.getReporterId())) throw ApiException.forbidden();
        t.setDeletedAt(clock.instant());
        history.record(t, actor, "DELETED", "moved this task to the trash");
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskDto> list(UUID projectId, UUID statusId, String assignee, Priority priority, LocalDate dueBefore,
                                      String q, int page, int size, String sort) {
        guard.requireProject(projectId, ProjectRole.VIEWER);
        UUID assigneeId = "me".equals(assignee) ? CurrentUser.id() : (assignee == null || assignee.isBlank() ? null : UUID.fromString(assignee));
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        var result = tasks.search(projectId, statusId, assigneeId, priority, dueBefore, like,
                Pages.of(page, size, sort, SORTABLE, "number"));
        List<TaskDto> dtos = mapper.many(result.getContent());
        return new PageResponse<>(dtos, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public BoardDto board(UUID projectId) {
        guard.requireProject(projectId, ProjectRole.VIEWER);
        List<Task> all = tasks.findByProjectIdAndDeletedAtIsNullOrderByTaskRank(projectId).stream()
                .filter(t -> t.getParentTaskId() == null).toList();
        Map<UUID, TaskDto> dto = mapper.many(all).stream().collect(Collectors.toMap(TaskDto::id, d -> d));
        List<BoardColumn> columns = new ArrayList<>();
        for (TaskStatus s : statuses.findByProjectIdOrderByPosition(projectId)) {
            List<TaskDto> col = all.stream().filter(t -> t.getStatusId().equals(s.getId())).map(t -> dto.get(t.getId())).toList();
            columns.add(new BoardColumn(s.getId(), s.getName(), s.getColor(), s.getCategory(), col));
        }
        return new BoardDto(projectId, columns);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskDto> mine(UUID workspaceId, int page, int size, String sort) {
        UUID me = CurrentUser.id();
        Set<UUID> visible = guard.visibleProjectIds(workspaceId, me);
        if (visible.isEmpty()) return new PageResponse<>(List.of(), 0, size, 0, 0);
        var result = tasks.assignedTo(workspaceId, me, visible, Pages.of(page, size, sort, SORTABLE, "dueDate"));
        return new PageResponse<>(mapper.many(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public TaskDto move(UUID taskId, MoveTaskRequest req) {
        Task t = require(taskId, ProjectRole.CONTRIBUTOR);
        if (!t.getStatusId().equals(req.statusId())) {
            workflow.transition(t, req.statusId());
            if (t.getPendingApprovalStatusId() != null) {
                return mapper.one(t);
            }
        }
        String after = neighbourRank(req.afterTaskId(), t);
        String before = neighbourRank(req.beforeTaskId(), t);
        if (after != null || before != null) {
            if (after != null && before != null && after.compareTo(before) >= 0) {
                before = null;
            }
            t.setTaskRank(Rank.between(after, before));
        }
        return mapper.one(t);
    }

    @Transactional
    public TaskDto transition(UUID taskId, UUID toStatusId) {
        Task t = require(taskId, ProjectRole.CONTRIBUTOR);
        workflow.transition(t, toStatusId);
        return mapper.one(t);
    }

    @Transactional
    public TaskDto approve(UUID taskId) {
        return mapper.one(workflow.approve(require(taskId, ProjectRole.MANAGER)));
    }

    @Transactional
    public TaskDto reject(UUID taskId, String reason) {
        return mapper.one(workflow.reject(require(taskId, ProjectRole.MANAGER), reason));
    }

    @Transactional
    public int bulkUpdate(UUID projectId, BulkUpdateRequest req) {
        Project p = guard.requireProject(projectId, ProjectRole.MANAGER);
        UUID actor = CurrentUser.id();
        if (req.assigneeId() != null) requireAssignable(p, req.assigneeId());
        int n = 0;
        for (Task t : tasks.findAllById(req.taskIds())) {
            if (!t.getProjectId().equals(projectId) || t.getDeletedAt() != null) continue;
            if (req.priority() != null && req.priority() != t.getPriority()) {
                history.record(t, actor, "PRIORITY_CHANGED", "changed priority from " + t.getPriority() + " to " + req.priority());
                t.setPriority(req.priority());
            }
            if (req.assigneeId() != null && !req.assigneeId().equals(t.getAssigneeId())) {
                t.setAssigneeId(req.assigneeId());
                history.record(t, actor, "ASSIGNED", "changed the assignee");
                events.publishEvent(new TaskAssigned(t.getWorkspaceId(), t.getId(), t.getTaskKey(), t.getTitle(), req.assigneeId(), actor));
            }
            n++;
        }
        return n;
    }

    @Transactional(readOnly = true)
    public List<TaskDto> subtasks(UUID taskId) {
        require(taskId, ProjectRole.VIEWER);
        return mapper.many(tasks.findByParentTaskIdAndDeletedAtIsNullOrderByNumber(taskId));
    }

    @Transactional(readOnly = true)
    public List<DependencyDto> dependencies(UUID taskId) {
        require(taskId, ProjectRole.VIEWER);
        List<DependencyDto> out = new ArrayList<>();
        for (TaskDependency d : dependencies.findByTaskId(taskId)) {
            tasks.findByIdAndDeletedAtIsNull(d.getBlockedById()).ifPresent(b -> out.add(new DependencyDto("BLOCKED_BY", mapper.one(b))));
        }
        for (TaskDependency d : dependencies.findByBlockedById(taskId)) {
            tasks.findByIdAndDeletedAtIsNull(d.getTaskId()).ifPresent(b -> out.add(new DependencyDto("BLOCKS", mapper.one(b))));
        }
        return out;
    }

    @Transactional
    public void addDependency(UUID taskId, UUID blockedById) {
        Task t = require(taskId, ProjectRole.CONTRIBUTOR);
        Task blocker = require(blockedById, ProjectRole.VIEWER);
        if (t.getId().equals(blocker.getId()) || !t.getWorkspaceId().equals(blocker.getWorkspaceId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "A task can't depend on itself.");
        }
        if (dependencies.findByTaskIdAndBlockedById(blocker.getId(), t.getId()).isPresent()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Those tasks already depend on each other the other way round.");
        }
        if (dependencies.findByTaskIdAndBlockedById(t.getId(), blocker.getId()).isEmpty()) {
            dependencies.save(new TaskDependency(t.getWorkspaceId(), t.getId(), blocker.getId()));
            history.record(t, CurrentUser.id(), "DEPENDENCY_ADDED", "marked this as blocked by " + blocker.getTaskKey());
        }
    }

    @Transactional
    public void removeDependency(UUID taskId, UUID blockedById) {
        Task t = require(taskId, ProjectRole.CONTRIBUTOR);
        dependencies.findByTaskIdAndBlockedById(t.getId(), blockedById).ifPresent(d -> {
            dependencies.delete(d);
            history.record(t, CurrentUser.id(), "DEPENDENCY_REMOVED", "removed a blocking task");
        });
    }

    @Transactional(readOnly = true)
    public List<TaskDto> calendar(UUID workspaceId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(62).isBefore(to)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Pick a range of up to 62 days.");
        }
        Set<UUID> visible = guard.visibleProjectIds(workspaceId, CurrentUser.id());
        if (visible.isEmpty()) return List.of();
        return mapper.many(tasks.dueBetween(workspaceId, visible, from, to));
    }

    @Transactional(readOnly = true)
    public List<TaskDto> search(UUID workspaceId, String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        Set<UUID> visible = guard.visibleProjectIds(workspaceId, CurrentUser.id());
        if (visible.isEmpty()) return List.of();
        return mapper.many(tasks.quickSearch(workspaceId, visible, "%" + q.trim().toLowerCase() + "%", PageRequest.of(0, 8)));
    }

    @Transactional(readOnly = true)
    public List<TaskDto> pendingApprovals(UUID workspaceId) {
        UUID me = CurrentUser.id();
        Set<UUID> managed = guard.visibleProjectIds(workspaceId, me).stream()
                .filter(id -> guard.requireProject(id, ProjectRole.VIEWER) != null
                        && guard.projectRole(guard.requireProject(id, ProjectRole.VIEWER), me) == ProjectRole.MANAGER)
                .collect(Collectors.toSet());
        if (managed.isEmpty()) return List.of();
        return mapper.many(tasks.pendingApproval(managed));
    }

    private String neighbourRank(UUID neighbourId, Task moving) {
        if (neighbourId == null) return null;
        return tasks.findByIdAndDeletedAtIsNull(neighbourId)
                .filter(n -> n.getProjectId().equals(moving.getProjectId()) && !n.getId().equals(moving.getId()))
                .map(Task::getTaskRank).orElse(null);
    }

    private void requireAssignable(Project p, UUID userId) {
        if (workspaceMembers.findByWorkspaceIdAndUserId(p.getWorkspaceId(), userId).isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Assignee must be a member of this workspace.");
        }
    }
}
