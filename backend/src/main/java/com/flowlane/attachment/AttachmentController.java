package com.flowlane.attachment;

import com.flowlane.attachment.AttachmentService.AttachmentDto;
import com.flowlane.attachment.AttachmentService.Download;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class AttachmentController {

    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    @GetMapping("/tasks/{taskId}/attachments")
    public List<AttachmentDto> list(@PathVariable UUID taskId) {
        return service.list(taskId);
    }

    @PostMapping(value = "/tasks/{taskId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentDto upload(@PathVariable UUID taskId, @RequestParam("file") MultipartFile file) throws IOException {
        return service.upload(taskId, file);
    }

    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) throws IOException {
        Download d = service.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.meta().getFileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(d.meta().getContentType()))
                .contentLength(d.meta().getSizeBytes())
                .body(new InputStreamResource(d.content()));
    }

    @DeleteMapping("/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) throws IOException {
        service.delete(id);
    }
}
