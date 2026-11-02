package com.flowlane.workflow;

import com.flowlane.common.security.AccessGuard;
import com.flowlane.common.security.CurrentUser;
import com.flowlane.identity.User;
import com.flowlane.identity.UserRepository;
import com.flowlane.project.ProjectRole;
import com.flowlane.task.Task;
import com.flowlane.task.TaskRepository;
import com.flowlane.task.TaskService;
import com.flowlane.workflow.WorkflowDtos.HistoryDto;
import com.flowlane.workflow.WorkflowDtos.UpdateWorkflowRequest;
import com.flowlane.workflow.WorkflowDtos.WorkflowDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WorkflowController {

    private final WorkflowService workflow;
    private final TaskService taskService;
    private final TaskHistoryRepository history;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final AccessGuard guard;

    public WorkflowController(WorkflowService workflow, TaskService taskService, TaskHistoryRepository history,
                              TaskRepository tasks, UserRepository users, AccessGuard guard) {
        this.workflow = workflow;
        this.taskService = taskService;
        this.history = history;
        this.tasks = tasks;
        this.users = users;
        this.guard = guard;
    }

    @GetMapping("/projects/{projectId}/workflow")
    public WorkflowDto get(@PathVariable UUID projectId) {
        return workflow.get(projectId);
    }

    @PutMapping("/projects/{projectId}/workflow")
    public WorkflowDto update(@PathVariable UUID projectId, @Valid @RequestBody UpdateWorkflowRequest req) {
        return workflow.update(projectId, req);
    }

    @GetMapping("/tasks/{taskId}/history")
    @Transactional(readOnly = true)
    public List<HistoryDto> taskHistory(@PathVariable UUID taskId) {
        Task t = taskService.require(taskId, ProjectRole.VIEWER);
        return toDtos(history.findByTaskIdOrderByCreatedAtDesc(taskId), Map.of(t.getId(), t));
    }

    @GetMapping("/workspaces/{workspaceId}/activity")
    @Transactional(readOnly = true)
    public List<HistoryDto> activity(@PathVariable UUID workspaceId) {
        Set<UUID> visible = guard.visibleProjectIds(workspaceId, CurrentUser.id());
        if (visible.isEmpty()) return List.of();
        List<TaskHistory> rows = history.recent(workspaceId, visible, PageRequest.of(0, 20));
        Map<UUID, Task> byId = tasks.findAllById(rows.stream().map(TaskHistory::getTaskId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));
        return toDtos(rows, byId);
    }

    private List<HistoryDto> toDtos(List<TaskHistory> rows, Map<UUID, Task> tasksById) {
        Map<UUID, User> actors = users.findAllById(rows.stream().map(TaskHistory::getActorId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream().map(h -> {
            User a = actors.get(h.getActorId());
            Task t = tasksById.get(h.getTaskId());
            return new HistoryDto(h.getId(), a == null ? "Former member" : a.getDisplayName(), a == null ? "?" : a.initials(),
                    h.getAction(), h.getDetail(), h.getCreatedAt(), t == null ? null : t.getTaskKey(), h.getTaskId());
        }).toList();
    }
}
