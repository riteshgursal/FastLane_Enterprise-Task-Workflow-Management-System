package com.flowlane.notification;

import com.flowlane.common.exception.ApiException;
import com.flowlane.common.security.CurrentUser;
import com.flowlane.common.web.PageResponse;
import com.flowlane.identity.User;
import com.flowlane.identity.UserRepository;
import com.flowlane.task.Task;
import com.flowlane.task.TaskDtos.UserRef;
import com.flowlane.task.TaskMapper;
import com.flowlane.task.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    public record NotificationDto(UUID id, NotificationType type, String message, UUID taskId, String taskKey,
                                  UserRef actor, boolean read, Instant createdAt) {}

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final TaskRepository tasks;
    private final Clock clock;

    public NotificationController(NotificationRepository notifications, UserRepository users, TaskRepository tasks, Clock clock) {
        this.notifications = notifications;
        this.users = users;
        this.tasks = tasks;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(@RequestParam(defaultValue = "false") boolean unread,
                                              @RequestParam(required = false) NotificationType type,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "30") int size) {
        Page<Notification> p = notifications.forUser(CurrentUser.id(), unread, type, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        Map<UUID, User> actors = users.findAllById(p.getContent().stream().map(Notification::getActorId).filter(Objects::nonNull)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Task> taskMap = tasks.findAllById(p.getContent().stream().map(Notification::getTaskId).filter(Objects::nonNull)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Task::getId, Function.identity()));
        return PageResponse.of(p, n -> new NotificationDto(n.getId(), n.getType(), n.getMessage(), n.getTaskId(),
                n.getTaskId() == null || !taskMap.containsKey(n.getTaskId()) ? null : taskMap.get(n.getTaskId()).getTaskKey(),
                TaskMapper.ref(actors.get(n.getActorId())), n.getReadAt() != null, n.getCreatedAt()));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notifications.countByRecipientIdAndReadAtIsNull(CurrentUser.id()));
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void markRead(@PathVariable UUID id) {
        Notification n = notifications.findById(id).filter(x -> x.getRecipientId().equals(CurrentUser.id()))
                .orElseThrow(() -> ApiException.notFound("Notification"));
        if (n.getReadAt() == null) n.setReadAt(clock.instant());
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void readAll() {
        notifications.markAllRead(CurrentUser.id(), clock.instant());
    }
}
