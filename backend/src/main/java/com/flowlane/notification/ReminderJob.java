package com.flowlane.notification;

import com.flowlane.common.event.DomainEvents.DueReminder;
import com.flowlane.task.Task;
import com.flowlane.task.TaskRepository;
import com.flowlane.workflow.StatusCategory;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Daily due-tomorrow reminders and overdue alerts. Single-instance; use ShedLock or Quartz when scaling out. */
@Component
public class ReminderJob {

    private final TaskRepository tasks;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ReminderJob(TaskRepository tasks, ApplicationEventPublisher events, Clock clock) {
        this.tasks = tasks;
        this.events = events;
        this.clock = clock;
    }

    @Scheduled(cron = "${flowlane.reminders.cron}", zone = "UTC")
    @Transactional
    public void run() {
        LocalDate today = LocalDate.now(clock);
        for (Task t : tasks.openDueOn(today.plusDays(1), StatusCategory.OPEN)) {
            events.publishEvent(new DueReminder(t.getWorkspaceId(), t.getId(), t.getTaskKey(), t.getTitle(), t.getAssigneeId(), false));
        }
        for (Task t : tasks.openOverdue(today, StatusCategory.OPEN)) {
            events.publishEvent(new DueReminder(t.getWorkspaceId(), t.getId(), t.getTaskKey(), t.getTitle(), t.getAssigneeId(), true));
        }
    }
}
