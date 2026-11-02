package com.flowlane.task;

import com.flowlane.workflow.StatusCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TaskDtos {

    private TaskDtos() {}

    public record UserRef(UUID id, String displayName, String initials) {}

    public record TaskDto(UUID id, String key, UUID projectId, String projectKey, String projectName, String projectColor,
                          String title, String description, UUID statusId, String statusName, String statusColor,
                          StatusCategory category, Priority priority, UserRef assignee, UserRef reporter,
                          UUID parentId, LocalDate startDate, LocalDate dueDate, Integer estimatePoints, List<String> labels,
                          Recurrence recurrence, String rank, Instant completedAt, boolean overdue,
                          UUID pendingApprovalStatusId, String pendingApprovalStatusName,
                          long subtaskCount, long subtaskDone, long version, Instant createdAt, Instant updatedAt) {}

    public record CreateTaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20000) String description,
            Priority priority,
            UUID assigneeId,
            UUID parentTaskId,
            UUID statusId,
            LocalDate startDate,
            LocalDate dueDate,
            @Min(0) @Max(100) Integer estimatePoints,
            @Size(max = 10) List<@Size(max = 30) String> labels,
            Recurrence recurrence) {}

    /** Fields left null are unchanged. Use the clear flags to remove an assignee or due date. */
    public record UpdateTaskRequest(
            @NotNull Long version,
            @Size(min = 1, max = 200) String title,
            @Size(max = 20000) String description,
            Priority priority,
            UUID assigneeId,
            Boolean clearAssignee,
            LocalDate startDate,
            LocalDate dueDate,
            Boolean clearDueDate,
            @Min(0) @Max(100) Integer estimatePoints,
            @Size(max = 10) List<@Size(max = 30) String> labels,
            Recurrence recurrence) {}

    public record MoveTaskRequest(@NotNull UUID statusId, UUID afterTaskId, UUID beforeTaskId) {}

    public record BulkUpdateRequest(@NotNull @Size(min = 1, max = 100) List<UUID> taskIds, UUID assigneeId, Priority priority) {}

    public record DependencyRequest(@NotNull UUID blockedById) {}

    public record DependencyDto(String relation, TaskDto task) {}

    public record BoardColumn(UUID statusId, String name, String color, StatusCategory category, List<TaskDto> tasks) {}

    public record BoardDto(UUID projectId, List<BoardColumn> columns) {}
}
