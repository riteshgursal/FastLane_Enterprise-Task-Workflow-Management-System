package com.flowlane.comment;

import com.flowlane.comment.CommentService.CommentDto;
import com.flowlane.comment.CommentService.CommentRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentService service;

    public CommentController(CommentService service) {
        this.service = service;
    }

    @GetMapping("/tasks/{taskId}/comments")
    public List<CommentDto> list(@PathVariable UUID taskId) {
        return service.list(taskId);
    }

    @PostMapping("/tasks/{taskId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto add(@PathVariable UUID taskId, @Valid @RequestBody CommentRequest req) {
        return service.add(taskId, req.body());
    }

    @PatchMapping("/comments/{id}")
    public CommentDto edit(@PathVariable UUID id, @Valid @RequestBody CommentRequest req) {
        return service.edit(id, req.body());
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
