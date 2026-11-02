package com.flowlane.attachment;

import com.flowlane.common.exception.ApiException;
import com.flowlane.common.exception.ErrorCode;
import com.flowlane.common.persistence.UuidV7;
import com.flowlane.common.security.CurrentUser;
import com.flowlane.identity.User;
import com.flowlane.identity.UserRepository;
import com.flowlane.project.ProjectRole;
import com.flowlane.task.Task;
import com.flowlane.task.TaskDtos.UserRef;
import com.flowlane.task.TaskMapper;
import com.flowlane.task.TaskService;
import com.flowlane.workflow.HistoryRecorder;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {

    public static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf", "text/plain", "text/csv",
            "application/json", "application/zip",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    public record AttachmentDto(UUID id, String fileName, String contentType, long sizeBytes, UserRef uploader, Instant createdAt) {}

    public record Download(Attachment meta, InputStream content) {}

    private final AttachmentRepository attachments;
    private final FileStorage storage;
    private final TaskService taskService;
    private final UserRepository users;
    private final HistoryRecorder history;

    public AttachmentService(AttachmentRepository attachments, FileStorage storage, TaskService taskService,
                             UserRepository users, HistoryRecorder history) {
        this.attachments = attachments;
        this.storage = storage;
        this.taskService = taskService;
        this.users = users;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> list(UUID taskId) {
        taskService.require(taskId, ProjectRole.VIEWER);
        List<Attachment> list = attachments.findByTaskIdOrderByCreatedAt(taskId);
        Map<UUID, User> u = users.findAllById(list.stream().map(Attachment::getUploaderId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return list.stream().map(a -> toDto(a, u.get(a.getUploaderId()))).toList();
    }

    @Transactional
    public AttachmentDto upload(UUID taskId, MultipartFile file) throws IOException {
        Task task = taskService.require(taskId, ProjectRole.CONTRIBUTOR);
        if (file.isEmpty() || file.getSize() > MAX_BYTES || file.getContentType() == null || !ALLOWED.contains(file.getContentType())) {
            throw new ApiException(ErrorCode.FILE_REJECTED, "Upload images, PDFs, text, CSV, JSON, ZIP or Office files up to 25 MB.");
        }
        String name = sanitize(file.getOriginalFilename());
        String key = UuidV7.next().toString();
        try (InputStream in = file.getInputStream()) {
            storage.store(key, in);
        }
        Attachment a = new Attachment();
        a.setWorkspaceId(task.getWorkspaceId());
        a.setTaskId(taskId);
        a.setUploaderId(CurrentUser.id());
        a.setFileName(name);
        a.setContentType(file.getContentType());
        a.setSizeBytes(file.getSize());
        a.setStorageKey(key);
        attachments.save(a);
        history.record(task, CurrentUser.id(), "ATTACHED", "attached " + name);
        return toDto(a, users.findById(CurrentUser.id()).orElse(null));
    }

    @Transactional(readOnly = true)
    public Download download(UUID attachmentId) throws IOException {
        Attachment a = attachments.findById(attachmentId).orElseThrow(() -> ApiException.notFound("Attachment"));
        taskService.require(a.getTaskId(), ProjectRole.VIEWER);
        return new Download(a, storage.open(a.getStorageKey()));
    }

    @Transactional
    public void delete(UUID attachmentId) throws IOException {
        Attachment a = attachments.findById(attachmentId).orElseThrow(() -> ApiException.notFound("Attachment"));
        Task task = taskService.require(a.getTaskId(), ProjectRole.CONTRIBUTOR);
        if (!a.getUploaderId().equals(CurrentUser.id())) {
            taskService.require(a.getTaskId(), ProjectRole.MANAGER);
        }
        attachments.delete(a);
        storage.delete(a.getStorageKey());
        history.record(task, CurrentUser.id(), "DETACHED", "removed " + a.getFileName());
    }

    private static String sanitize(String original) {
        String name = original == null ? "file" : original.replaceAll("[\\\\/\\r\\n\"]", "_").trim();
        if (name.isEmpty()) name = "file";
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static AttachmentDto toDto(Attachment a, User u) {
        return new AttachmentDto(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), TaskMapper.ref(u), a.getCreatedAt());
    }
}
