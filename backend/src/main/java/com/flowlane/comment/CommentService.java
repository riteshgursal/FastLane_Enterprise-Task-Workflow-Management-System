package com.flowlane.comment;

import com.flowlane.common.event.DomainEvents.Commented;
import com.flowlane.common.event.DomainEvents.Mentioned;
import com.flowlane.common.exception.ApiException;
import com.flowlane.common.security.CurrentUser;
import com.flowlane.identity.User;
import com.flowlane.identity.UserRepository;
import com.flowlane.project.ProjectRole;
import com.flowlane.task.Task;
import com.flowlane.task.TaskDtos.UserRef;
import com.flowlane.task.TaskMapper;
import com.flowlane.task.TaskService;
import com.flowlane.workspace.WorkspaceMember;
import com.flowlane.workspace.WorkspaceMemberRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private static final Pattern MENTION = Pattern.compile("@([\\p{L}][\\p{L}.'-]{1,40})");

    public record CommentRequest(@NotBlank @Size(max = 10000) String body) {}

    public record CommentDto(UUID id, UserRef author, String body, Instant createdAt, Instant editedAt, boolean mine) {}

    private final CommentRepository comments;
    private final TaskService taskService;
    private final UserRepository users;
    private final WorkspaceMemberRepository members;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CommentService(CommentRepository comments, TaskService taskService, UserRepository users,
                          WorkspaceMemberRepository members, ApplicationEventPublisher events, Clock clock) {
        this.comments = comments;
        this.taskService = taskService;
        this.users = users;
        this.members = members;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CommentDto> list(UUID taskId) {
        taskService.require(taskId, ProjectRole.VIEWER);
        List<Comment> list = comments.findByTaskIdAndDeletedAtIsNullOrderByCreatedAt(taskId);
        Map<UUID, User> authors = users.findAllById(list.stream().map(Comment::getAuthorId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        UUID me = CurrentUser.id();
        return list.stream().map(c -> new CommentDto(c.getId(), TaskMapper.ref(authors.get(c.getAuthorId())), c.getBody(),
                c.getCreatedAt(), c.getEditedAt(), c.getAuthorId().equals(me))).toList();
    }

    @Transactional
    public CommentDto add(UUID taskId, String body) {
        Task task = taskService.require(taskId, ProjectRole.CONTRIBUTOR);
        UUID me = CurrentUser.id();
        Comment c = new Comment();
        c.setWorkspaceId(task.getWorkspaceId());
        c.setTaskId(taskId);
        c.setAuthorId(me);
        c.setBody(body.trim());
        comments.save(c);
        List<UUID> mentioned = resolveMentions(task.getWorkspaceId(), body, me);
        if (!mentioned.isEmpty()) {
            String excerpt = body.length() > 120 ? body.substring(0, 117) + "…" : body;
            events.publishEvent(new Mentioned(task.getWorkspaceId(), taskId, task.getTaskKey(), mentioned, me, excerpt));
        }
        events.publishEvent(new Commented(task.getWorkspaceId(), taskId, task.getTaskKey(), task.getAssigneeId(), task.getReporterId(), me));
        return new CommentDto(c.getId(), TaskMapper.ref(users.findById(me).orElse(null)), c.getBody(), c.getCreatedAt(), null, true);
    }

    @Transactional
    public CommentDto edit(UUID commentId, String body) {
        Comment c = owned(commentId);
        c.setBody(body.trim());
        c.setEditedAt(clock.instant());
        return new CommentDto(c.getId(), TaskMapper.ref(users.findById(c.getAuthorId()).orElse(null)), c.getBody(),
                c.getCreatedAt(), c.getEditedAt(), true);
    }

    @Transactional
    public void delete(UUID commentId) {
        owned(commentId).setDeletedAt(clock.instant());
    }

    private Comment owned(UUID commentId) {
        Comment c = comments.findById(commentId).filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Comment"));
        taskService.require(c.getTaskId(), ProjectRole.VIEWER);
        if (!c.getAuthorId().equals(CurrentUser.id())) throw ApiException.forbidden();
        return c;
    }

    /** Matches @Name against the first name or full name (spaces removed) of workspace members. */
    private List<UUID> resolveMentions(UUID workspaceId, String body, UUID author) {
        Matcher m = MENTION.matcher(body);
        Set<String> handles = new LinkedHashSet<>();
        while (m.find()) handles.add(m.group(1).toLowerCase(Locale.ROOT));
        if (handles.isEmpty()) return List.of();
        List<UUID> memberIds = members.findByWorkspaceId(workspaceId).stream().map(WorkspaceMember::getUserId).toList();
        return users.findAllById(memberIds).stream()
                .filter(u -> !u.getId().equals(author))
                .filter(u -> {
                    String full = u.getDisplayName().toLowerCase(Locale.ROOT);
                    String first = full.split("\\s+")[0];
                    return handles.contains(first) || handles.contains(full.replaceAll("\\s+", ""));
                })
                .map(User::getId).toList();
    }
}
