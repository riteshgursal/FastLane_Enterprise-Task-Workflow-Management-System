package com.flowlane.workflow;

import com.flowlane.common.event.DomainEvents.ApprovalRequested;
import com.flowlane.common.event.DomainEvents.TaskTransitioned;
import com.flowlane.common.exception.ApiException;
import com.flowlane.common.exception.ErrorCode;
import com.flowlane.common.security.AccessGuard;
import com.flowlane.common.security.CurrentUser;
import com.flowlane.project.Project;
import com.flowlane.project.ProjectRole;
import com.flowlane.task.Task;
import com.flowlane.task.TaskDependency;
import com.flowlane.task.TaskDependencyRepository;
import com.flowlane.task.TaskFactory;
import com.flowlane.task.TaskRepository;
import com.flowlane.task.Recurrence;
import com.flowlane.workflow.WorkflowDtos.StatusDto;
import com.flowlane.workflow.WorkflowDtos.StatusInput;
import com.flowlane.workflow.WorkflowDtos.TransitionDto;
import com.flowlane.workflow.WorkflowDtos.TransitionInput;
import com.flowlane.workflow.WorkflowDtos.UpdateWorkflowRequest;
import com.flowlane.workflow.WorkflowDtos.WorkflowDto;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns statuses, transitions and every status change. The server is the only authority:
 * any move that isn't a configured transition is rejected.
 */
@Service
public class WorkflowService {

    private final TaskStatusRepository statuses;
    private final TaskTransitionRepository transitions;
    private final TaskRepository tasks;
    private final TaskDependencyRepository dependencies;
    private final TaskFactory factory;
    private final HistoryRecorder history;
    private final AccessGuard guard;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public WorkflowService(TaskStatusRepository statuses, TaskTransitionRepository transitions, TaskRepository tasks,
                           TaskDependencyRepository dependencies, TaskFactory factory, HistoryRecorder history,
                           AccessGuard guard, ApplicationEventPublisher events, Clock clock) {
        this.statuses = statuses;
        this.transitions = transitions;
        this.tasks = tasks;
        this.dependencies = dependencies;
        this.factory = factory;
        this.history = history;
        this.guard = guard;
        this.events = events;
        this.clock = clock;
    }

    /** To do → In progress → In review → Done, plus Cancelled. In review → Done needs a manager's approval. */
    public void createDefaultWorkflow(Project p) {
        UUID ws = p.getWorkspaceId();
        TaskStatus todo = statuses.save(new TaskStatus(ws, p.getId(), "To do", StatusCategory.TODO, "#8D929E", 0, true));
        TaskStatus doing = statuses.save(new TaskStatus(ws, p.getId(), "In progress", StatusCategory.IN_PROGRESS, "#1DA1D8", 1, false));
        TaskStatus review = statuses.save(new TaskStatus(ws, p.getId(), "In review", StatusCategory.IN_PROGRESS, "#A78BFA", 2, false));
        TaskStatus done = statuses.save(new TaskStatus(ws, p.getId(), "Done", StatusCategory.DONE, "#4AD08A", 3, false));
        TaskStatus cancelled = statuses.save(new TaskStatus(ws, p.getId(), "Cancelled", StatusCategory.CANCELLED, "#5F6573", 4, false));
        link(p, todo, doing, false);
        link(p, todo, cancelled, false);
        link(p, doing, review, false);
        link(p, doing, todo, false);
        link(p, review, done, true);
        link(p, review, doing, false);
        link(p, done, doing, false);
        link(p, cancelled, todo, false);
    }

    private void link(Project p, TaskStatus from, TaskStatus to, boolean approval) {
        transitions.save(new TaskTransition(p.getWorkspaceId(), p.getId(), from.getId(), to.getId(), approval));
    }

    @Transactional(readOnly = true)
    public WorkflowDto get(UUID projectId) {
        guard.requireProject(projectId, ProjectRole.VIEWER);
        return toDto(projectId);
    }

    @Transactional
    public WorkflowDto update(UUID projectId, UpdateWorkflowRequest req) {
        Project p = guard.requireProject(projectId, ProjectRole.MANAGER);
        if (req.statuses().stream().filter(StatusInput::initial).count() != 1) {
            throw new ApiException(ErrorCode.INVALID_WORKFLOW, "Exactly one status must be the initial status.");
        }
        Set<String> names = new HashSet<>();
        for (StatusInput s : req.statuses()) {
            if (!names.add(s.name().trim().toLowerCase())) {
                throw new ApiException(ErrorCode.INVALID_WORKFLOW, "Status names must be unique.");
            }
        }
        List<TaskStatus> existing = statuses.findByProjectIdOrderByPosition(projectId);
        Map<String, TaskStatus> byRef = new HashMap<>();
        Set<UUID> kept = new HashSet<>();
        for (TaskStatus s : existing) {
            req.statuses().stream().filter(i -> i.ref().equals(s.getId().toString())).findFirst().ifPresent(i -> kept.add(s.getId()));
        }
        for (TaskStatus s : existing) {
            if (!kept.contains(s.getId())) {
                if (tasks.countByStatusIdAndDeletedAtIsNull(s.getId()) > 0) {
                    throw new ApiException(ErrorCode.STATUS_IN_USE, "Move the tasks out of “" + s.getName() + "” before removing it.");
                }
            }
        }
        transitions.deleteByProjectId(projectId);
        transitions.flush();
        for (TaskStatus s : existing) {
            if (!kept.contains(s.getId())) statuses.delete(s);
        }
        statuses.flush();
        // Rename to temporary names first so a swap of two names doesn't hit the unique constraint.
        for (TaskStatus s : existing) {
            if (kept.contains(s.getId())) s.setName("~" + s.getId());
        }
        statuses.flush();
        int position = 0;
        for (StatusInput in : req.statuses()) {
            TaskStatus s = existing.stream().filter(e -> e.getId().toString().equals(in.ref())).findFirst()
                    .orElseGet(() -> new TaskStatus(p.getWorkspaceId(), projectId, in.name(), in.category(), "#8D929E", 0, false));
            s.setName(in.name().trim());
            s.setCategory(in.category());
            if (in.color() != null) s.setColor(in.color());
            s.setPosition(position++);
            s.setInitial(in.initial());
            byRef.put(in.ref(), statuses.save(s));
        }
        if (req.transitions() != null) {
            Set<String> seen = new HashSet<>();
            for (TransitionInput t : req.transitions()) {
                TaskStatus from = byRef.get(t.fromRef());
                TaskStatus to = byRef.get(t.toRef());
                if (from == null || to == null || from.equals(to)) {
                    throw new ApiException(ErrorCode.INVALID_WORKFLOW, "A transition points at an unknown status.");
                }
                if (seen.add(t.fromRef() + ">" + t.toRef())) {
                    transitions.save(new TaskTransition(p.getWorkspaceId(), projectId, from.getId(), to.getId(), t.requiresApproval()));
                }
            }
        }
        return toDto(projectId);
    }

    /** Moves a task to another status, enforcing transitions, subtasks, dependencies and approvals. */
    @Transactional
    public Task transition(Task task, UUID toStatusId) {
        guard.requireProject(task.getProjectId(), ProjectRole.CONTRIBUTOR);
        UUID actor = CurrentUser.id();
        if (task.getStatusId().equals(toStatusId)) return task;
        if (task.getPendingApprovalStatusId() != null) {
            throw new ApiException(ErrorCode.APPROVAL_PENDING, "This task is waiting for approval to move to another status.");
        }
        TaskStatus from = statuses.findById(task.getStatusId()).orElseThrow();
        TaskStatus to = statuses.findById(toStatusId).filter(s -> s.getProjectId().equals(task.getProjectId()))
                .orElseThrow(() -> ApiException.notFound("Status"));
        TaskTransition rule = transitions.findByFromStatusIdAndToStatusId(from.getId(), to.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TRANSITION,
                        "A task can't move from “" + from.getName() + "” to “" + to.getName() + "”."));
        if (to.getCategory() == StatusCategory.DONE) {
            checkReadyForDone(task);
        }
        if (rule.isRequiresApproval()) {
            task.setPendingApprovalStatusId(to.getId());
            history.record(task, actor, "APPROVAL_REQUESTED", "requested approval to move to " + to.getName());
            events.publishEvent(new ApprovalRequested(task.getWorkspaceId(), task.getProjectId(), task.getId(),
                    task.getTaskKey(), task.getTitle(), to.getName(), actor));
            return task;
        }
        apply(task, from, to, actor);
        return task;
    }

    @Transactional
    public Task approve(Task task) {
        guard.requireProject(task.getProjectId(), ProjectRole.MANAGER);
        if (task.getPendingApprovalStatusId() == null) throw new ApiException(ErrorCode.NO_PENDING_APPROVAL);
        TaskStatus from = statuses.findById(task.getStatusId()).orElseThrow();
        TaskStatus to = statuses.findById(task.getPendingApprovalStatusId()).orElseThrow();
        if (to.getCategory() == StatusCategory.DONE) checkReadyForDone(task);
        task.setPendingApprovalStatusId(null);
        UUID actor = CurrentUser.id();
        history.record(task, actor, "APPROVED", "approved the move to " + to.getName());
        apply(task, from, to, actor);
        return task;
    }

    @Transactional
    public Task reject(Task task, String reason) {
        guard.requireProject(task.getProjectId(), ProjectRole.MANAGER);
        if (task.getPendingApprovalStatusId() == null) throw new ApiException(ErrorCode.NO_PENDING_APPROVAL);
        TaskStatus to = statuses.findById(task.getPendingApprovalStatusId()).orElseThrow();
        task.setPendingApprovalStatusId(null);
        String detail = "declined the move to " + to.getName() + (reason == null || reason.isBlank() ? "" : ": " + reason.trim());
        history.record(task, CurrentUser.id(), "REJECTED", detail);
        return task;
    }

    private void checkReadyForDone(Task task) {
        for (Task sub : tasks.findByParentTaskIdAndDeletedAtIsNullOrderByNumber(task.getId())) {
            if (!statuses.findById(sub.getStatusId()).orElseThrow().getCategory().isClosed()) {
                throw new ApiException(ErrorCode.OPEN_SUBTASKS, "Finish or cancel " + sub.getTaskKey() + " first.");
            }
        }
        for (TaskDependency d : dependencies.findByTaskId(task.getId())) {
            Task blocker = tasks.findByIdAndDeletedAtIsNull(d.getBlockedById()).orElse(null);
            if (blocker != null && statuses.findById(blocker.getStatusId()).orElseThrow().getCategory() != StatusCategory.DONE) {
                throw new ApiException(ErrorCode.BLOCKED_BY_DEPENDENCY, blocker.getTaskKey() + " has to be done first.");
            }
        }
    }

    private void apply(Task task, TaskStatus from, TaskStatus to, UUID actor) {
        task.setStatusId(to.getId());
        task.setCompletedAt(to.getCategory() == StatusCategory.DONE ? clock.instant() : null);
        task.setTaskRank(tasks.findFirstByProjectIdAndStatusIdAndDeletedAtIsNullOrderByTaskRankDesc(task.getProjectId(), to.getId())
                .map(t -> com.flowlane.common.persistence.Rank.between(t.getTaskRank(), null))
                .orElse(com.flowlane.common.persistence.Rank.first()));
        history.record(task, actor, "STATUS_CHANGED", "moved this from " + from.getName() + " to " + to.getName());
        events.publishEvent(new TaskTransitioned(task.getWorkspaceId(), task.getId(), task.getTaskKey(), task.getTitle(),
                from.getName(), to.getName(), task.getAssigneeId(), actor));
        if (to.getCategory() == StatusCategory.DONE && task.getRecurrence() != Recurrence.NONE && task.getDueDate() != null) {
            Task next = factory.newTask(task.getProjectId(), null, task.getTitle(), actor);
            next.setDescription(task.getDescription());
            next.setPriority(task.getPriority());
            next.setAssigneeId(task.getAssigneeId());
            next.setLabels(task.getLabels());
            next.setEstimatePoints(task.getEstimatePoints());
            next.setRecurrence(task.getRecurrence());
            next.setDueDate(task.getRecurrence().next(task.getDueDate()));
            tasks.save(next);
            history.record(next, actor, "CREATED", "created this as the next occurrence of " + task.getTaskKey());
        }
    }

    private WorkflowDto toDto(UUID projectId) {
        List<StatusDto> s = new ArrayList<>();
        for (TaskStatus st : statuses.findByProjectIdOrderByPosition(projectId)) {
            s.add(new StatusDto(st.getId(), st.getName(), st.getCategory(), st.getColor(), st.getPosition(), st.isInitial(),
                    tasks.countByStatusIdAndDeletedAtIsNull(st.getId())));
        }
        List<TransitionDto> t = transitions.findByProjectId(projectId).stream()
                .map(x -> new TransitionDto(x.getFromStatusId(), x.getToStatusId(), x.isRequiresApproval())).toList();
        return new WorkflowDto(projectId, s, t);
    }
}
