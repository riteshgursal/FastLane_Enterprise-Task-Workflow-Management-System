package com.flowlane.task;

import com.flowlane.common.web.PageResponse;
import com.flowlane.task.TaskDtos.BoardDto;
import com.flowlane.task.TaskDtos.BulkUpdateRequest;
import com.flowlane.task.TaskDtos.CreateTaskRequest;
import com.flowlane.task.TaskDtos.DependencyDto;
import com.flowlane.task.TaskDtos.DependencyRequest;
import com.flowlane.task.TaskDtos.MoveTaskRequest;
import com.flowlane.task.TaskDtos.TaskDto;
import com.flowlane.task.TaskDtos.UpdateTaskRequest;
import com.flowlane.workflow.WorkflowDtos.RejectRequest;
import com.flowlane.workflow.WorkflowDtos.TransitionRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping("/projects/{projectId}/tasks")
    public PageResponse<TaskDto> list(@PathVariable UUID projectId,
                                      @RequestParam(required = false) UUID status,
                                      @RequestParam(required = false) String assignee,
                                      @RequestParam(required = false) Priority priority,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueBefore,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size,
                                      @RequestParam(required = false) String sort) {
        return service.list(projectId, status, assignee, priority, dueBefore, q, page, size, sort);
    }

    @PostMapping("/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDto create(@PathVariable UUID projectId, @Valid @RequestBody CreateTaskRequest req) {
        return service.create(projectId, req);
    }

    @GetMapping("/projects/{projectId}/board")
    public BoardDto board(@PathVariable UUID projectId) {
        return service.board(projectId);
    }

    @PostMapping("/projects/{projectId}/tasks/bulk")
    public Map<String, Integer> bulk(@PathVariable UUID projectId, @Valid @RequestBody BulkUpdateRequest req) {
        return Map.of("updated", service.bulkUpdate(projectId, req));
    }

    @GetMapping("/tasks/{id}")
    public TaskDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/workspaces/{workspaceId}/tasks/by-key/{key}")
    public TaskDto byKey(@PathVariable UUID workspaceId, @PathVariable String key) {
        return service.getByKey(workspaceId, key);
    }

    @PatchMapping("/tasks/{id}")
    public TaskDto update(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/tasks/{id}/move")
    public TaskDto move(@PathVariable UUID id, @Valid @RequestBody MoveTaskRequest req) {
        return service.move(id, req);
    }

    @PostMapping("/tasks/{id}/transitions")
    public TaskDto transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest req) {
        return service.transition(id, req.toStatusId());
    }

    @PostMapping("/tasks/{id}/approval/approve")
    public TaskDto approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PostMapping("/tasks/{id}/approval/reject")
    public TaskDto reject(@PathVariable UUID id, @Valid @RequestBody(required = false) RejectRequest req) {
        return service.reject(id, req == null ? null : req.reason());
    }

    @GetMapping("/tasks/{id}/subtasks")
    public List<TaskDto> subtasks(@PathVariable UUID id) {
        return service.subtasks(id);
    }

    @GetMapping("/tasks/{id}/dependencies")
    public List<DependencyDto> dependencies(@PathVariable UUID id) {
        return service.dependencies(id);
    }

    @PostMapping("/tasks/{id}/dependencies")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addDependency(@PathVariable UUID id, @Valid @RequestBody DependencyRequest req) {
        service.addDependency(id, req.blockedById());
    }

    @DeleteMapping("/tasks/{id}/dependencies/{blockedById}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDependency(@PathVariable UUID id, @PathVariable UUID blockedById) {
        service.removeDependency(id, blockedById);
    }

    @GetMapping("/workspaces/{workspaceId}/my-tasks")
    public PageResponse<TaskDto> mine(@PathVariable UUID workspaceId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size,
                                      @RequestParam(required = false) String sort) {
        return service.mine(workspaceId, page, size, sort);
    }

    @GetMapping("/workspaces/{workspaceId}/calendar")
    public List<TaskDto> calendar(@PathVariable UUID workspaceId,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.calendar(workspaceId, from, to);
    }

    @GetMapping("/workspaces/{workspaceId}/search")
    public List<TaskDto> search(@PathVariable UUID workspaceId, @RequestParam String q) {
        return service.search(workspaceId, q);
    }

    @GetMapping("/workspaces/{workspaceId}/approvals")
    public List<TaskDto> approvals(@PathVariable UUID workspaceId) {
        return service.pendingApprovals(workspaceId);
    }
}
