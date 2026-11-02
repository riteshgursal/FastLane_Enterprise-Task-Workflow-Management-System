package com.flowlane.workflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class WorkflowDtos {

    private WorkflowDtos() {}

    public record StatusDto(UUID id, String name, StatusCategory category, String color, int position, boolean initial, long taskCount) {}

    public record TransitionDto(UUID fromStatusId, UUID toStatusId, boolean requiresApproval) {}

    public record WorkflowDto(UUID projectId, List<StatusDto> statuses, List<TransitionDto> transitions) {}

    /** {@code ref} is the status id for existing statuses, or any temporary key for new ones. */
    public record StatusInput(@NotBlank String ref, @NotBlank @Size(max = 40) String name, @NotNull StatusCategory category,
                              @Pattern(regexp = "#[0-9A-Fa-f]{6}") String color, boolean initial) {}

    public record TransitionInput(@NotBlank String fromRef, @NotBlank String toRef, boolean requiresApproval) {}

    public record UpdateWorkflowRequest(@NotEmpty @Valid List<StatusInput> statuses, @Valid List<TransitionInput> transitions) {}

    public record TransitionRequest(@NotNull UUID toStatusId) {}

    public record RejectRequest(@Size(max = 500) String reason) {}

    public record HistoryDto(UUID id, String actorName, String actorInitials, String action, String detail, java.time.Instant at,
                             String taskKey, UUID taskId) {}
}
