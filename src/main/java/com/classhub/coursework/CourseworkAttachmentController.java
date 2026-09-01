package com.classhub.coursework;

import com.classhub.common.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/coursework/{courseworkId}/attachments")
public class CourseworkAttachmentController {

    private final CourseworkAttachmentService attachmentService;

    public CourseworkAttachmentController(CourseworkAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CourseworkAttachmentResponse> upload(
            @PathVariable UUID courseworkId, @RequestPart("file") MultipartFile file) {
        return ApiResponse.of(attachmentService.upload(courseworkId, file));
    }

    @GetMapping
    public ApiResponse<List<CourseworkAttachmentResponse>> list(@PathVariable UUID courseworkId) {
        return ApiResponse.of(attachmentService.list(courseworkId));
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable UUID courseworkId, @PathVariable UUID attachmentId) {
        return attachmentService.download(courseworkId, attachmentId);
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID courseworkId, @PathVariable UUID attachmentId) {
        attachmentService.delete(courseworkId, attachmentId);
    }
}
