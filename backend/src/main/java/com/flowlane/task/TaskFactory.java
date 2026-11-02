package com.flowlane.task;

import com.flowlane.common.exception.ApiException;
import com.flowlane.common.persistence.Rank;
import com.flowlane.project.Project;
import com.flowlane.project.ProjectRepository;
import com.flowlane.workflow.TaskStatus;
import com.flowlane.workflow.TaskStatusRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Creates tasks with a unique per-project number (row-locked) and a rank at the end of the column. */
@Component
public class TaskFactory {

    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final TaskStatusRepository statuses;

    public TaskFactory(ProjectRepository projects, TaskRepository tasks, TaskStatusRepository statuses) {
        this.projects = projects;
        this.tasks = tasks;
        this.statuses = statuses;
    }

    public Task newTask(UUID projectId, UUID statusId, String title, UUID reporterId) {
        Project project = projects.lockById(projectId).orElseThrow(() -> ApiException.notFound("Project"));
        TaskStatus status = statusId != null
                ? statuses.findById(statusId).filter(s -> s.getProjectId().equals(projectId))
                        .orElseThrow(() -> ApiException.notFound("Status"))
                : statuses.findByProjectIdOrderByPosition(projectId).stream().filter(TaskStatus::isInitial).findFirst()
                        .orElseThrow(() -> ApiException.notFound("Initial status"));
        int number = project.takeTaskNumber();
        String last = tasks.findFirstByProjectIdAndStatusIdAndDeletedAtIsNullOrderByTaskRankDesc(projectId, status.getId())
                .map(Task::getTaskRank).orElse(null);
        Task t = new Task();
        t.setWorkspaceId(project.getWorkspaceId());
        t.setProjectId(projectId);
        t.setNumber(number);
        t.setTaskKey(project.getProjectKey() + "-" + number);
        t.setTitle(title.trim());
        t.setStatusId(status.getId());
        t.setReporterId(reporterId);
        t.setTaskRank(Rank.between(last, null));
        return t;
    }
}
