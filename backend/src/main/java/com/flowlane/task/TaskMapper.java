package com.flowlane.task;

import com.flowlane.identity.User;
import com.flowlane.identity.UserRepository;
import com.flowlane.project.Project;
import com.flowlane.project.ProjectRepository;
import com.flowlane.task.TaskDtos.TaskDto;
import com.flowlane.task.TaskDtos.UserRef;
import com.flowlane.workflow.TaskStatus;
import com.flowlane.workflow.TaskStatusRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Converts tasks to DTOs, loading related users, statuses and projects in bulk. */
@Component
public class TaskMapper {

    private final UserRepository users;
    private final TaskStatusRepository statuses;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final Clock clock;

    public TaskMapper(UserRepository users, TaskStatusRepository statuses, ProjectRepository projects,
                      TaskRepository tasks, Clock clock) {
        this.users = users;
        this.statuses = statuses;
        this.projects = projects;
        this.tasks = tasks;
        this.clock = clock;
    }

    public TaskDto one(Task t) {
        return many(List.of(t)).getFirst();
    }

    public List<TaskDto> many(Collection<Task> list) {
        if (list.isEmpty()) return List.of();
        Set<UUID> userIds = new HashSet<>();
        Set<UUID> statusIds = new HashSet<>();
        Set<UUID> projectIds = new HashSet<>();
        for (Task t : list) {
            if (t.getAssigneeId() != null) userIds.add(t.getAssigneeId());
            userIds.add(t.getReporterId());
            statusIds.add(t.getStatusId());
            if (t.getPendingApprovalStatusId() != null) statusIds.add(t.getPendingApprovalStatusId());
            projectIds.add(t.getProjectId());
        }
        Map<UUID, User> u = users.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, TaskStatus> s = statuses.findAllById(statusIds).stream().collect(Collectors.toMap(TaskStatus::getId, Function.identity()));
        Map<UUID, Project> p = projects.findAllById(projectIds).stream().collect(Collectors.toMap(Project::getId, Function.identity()));
        LocalDate today = LocalDate.now(clock);
        return list.stream().map(t -> {
            TaskStatus st = s.get(t.getStatusId());
            TaskStatus pending = t.getPendingApprovalStatusId() == null ? null : s.get(t.getPendingApprovalStatusId());
            Project pr = p.get(t.getProjectId());
            boolean closed = st != null && st.getCategory().isClosed();
            boolean overdue = !closed && t.getDueDate() != null && t.getDueDate().isBefore(today);
            List<Task> subs = tasks.findByParentTaskIdAndDeletedAtIsNullOrderByNumber(t.getId());
            long subDone = subs.stream().map(x -> s.computeIfAbsent(x.getStatusId(), id -> statuses.findById(id).orElse(null)))
                    .filter(Objects::nonNull).filter(x -> x.getCategory().isClosed()).count();
            return new TaskDto(t.getId(), t.getTaskKey(), t.getProjectId(), pr.getProjectKey(), pr.getName(), pr.getColor(),
                    t.getTitle(), t.getDescription(), t.getStatusId(), st.getName(), st.getColor(), st.getCategory(),
                    t.getPriority(), ref(u.get(t.getAssigneeId())), ref(u.get(t.getReporterId())), t.getParentTaskId(),
                    t.getStartDate(), t.getDueDate(), t.getEstimatePoints(), t.labelList(), t.getRecurrence(), t.getTaskRank(),
                    t.getCompletedAt(), overdue, t.getPendingApprovalStatusId(), pending == null ? null : pending.getName(),
                    subs.size(), subDone, t.getVersion() == null ? 0 : t.getVersion(), t.getCreatedAt(), t.getUpdatedAt());
        }).toList();
    }

    public static UserRef ref(User user) {
        return user == null ? null : new UserRef(user.getId(), user.getDisplayName(), user.initials());
    }
}
