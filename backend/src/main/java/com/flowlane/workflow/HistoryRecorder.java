package com.flowlane.workflow;

import com.flowlane.task.Task;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class HistoryRecorder {

    private final TaskHistoryRepository history;

    public HistoryRecorder(TaskHistoryRepository history) {
        this.history = history;
    }

    public void record(Task task, UUID actorId, String action, String detail) {
        history.save(new TaskHistory(task.getWorkspaceId(), task.getProjectId(), task.getId(), actorId, action, detail));
    }
}
