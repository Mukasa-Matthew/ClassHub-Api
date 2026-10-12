package com.classhub.coursework;

import com.classhub.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coursework/{courseworkId}/progress")
public class CourseworkProgressController {

    private final CourseworkProgressService progressService;

    public CourseworkProgressController(CourseworkProgressService progressService) {
        this.progressService = progressService;
    }

    @GetMapping
    public ApiResponse<CourseworkProgressResponse> get(@PathVariable UUID courseworkId) {
        return ApiResponse.of(progressService.getOwnProgress(courseworkId));
    }

    @PutMapping
    public ApiResponse<CourseworkProgressResponse> upsert(
            @PathVariable UUID courseworkId,
            @Valid @RequestBody UpdateCourseworkProgressRequest request) {
        return ApiResponse.of(progressService.upsertOwnProgress(courseworkId, request));
    }
}
