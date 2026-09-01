package com.classhub.courseunit;

import com.classhub.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/course-units")
public class CourseUnitController {

    private final CourseUnitService courseUnitService;
    private final CourseUnitCoverImageService coverImageService;

    public CourseUnitController(
            CourseUnitService courseUnitService, CourseUnitCoverImageService coverImageService) {
        this.courseUnitService = courseUnitService;
        this.coverImageService = coverImageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> create(@Valid @RequestBody CreateCourseUnitRequest request) {
        return ApiResponse.of(courseUnitService.create(request));
    }

    @GetMapping
    public ApiResponse<List<?>> list(@RequestParam(required = false) Boolean active) {
        return ApiResponse.of(courseUnitService.list(active));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable UUID id) {
        return ApiResponse.of(courseUnitService.getById(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<?> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseUnitRequest request) {
        return ApiResponse.of(courseUnitService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<?> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseUnitStatusRequest request) {
        return ApiResponse.of(courseUnitService.updateStatus(id, request));
    }

    @PostMapping(value = "/{id}/cover-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CourseUnitResponse> uploadCoverImage(
            @PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return ApiResponse.of(coverImageService.upload(id, file));
    }

    @GetMapping("/{id}/cover-image")
    public ResponseEntity<InputStreamResource> downloadCoverImage(@PathVariable UUID id) {
        return coverImageService.download(id);
    }

    @DeleteMapping("/{id}/cover-image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCoverImage(@PathVariable UUID id) {
        coverImageService.remove(id);
    }
}
