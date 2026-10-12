package com.classhub.coursework;

import com.classhub.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coursework")
public class CourseworkController {

    private final CourseworkService courseworkService;

    public CourseworkController(CourseworkService courseworkService) {
        this.courseworkService = courseworkService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CourseworkResponse> create(@Valid @RequestBody CreateCourseworkRequest request) {
        return ApiResponse.of(courseworkService.create(request));
    }

    @GetMapping
    public ApiResponse<java.util.List<CourseworkResponse>> list(
            @RequestParam(required = false) UUID courseUnitId,
            @RequestParam(required = false) CourseworkStatus status,
            @RequestParam(required = false) Boolean upcoming,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        CourseworkService.ApiPage result =
                courseworkService.list(courseUnitId, status, upcoming, overdue, page, size);
        return ApiResponse.of(result.data(), result.pagination());
    }

    @GetMapping("/{id}")
    public ApiResponse<CourseworkResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(courseworkService.getById(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CourseworkResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateCourseworkRequest request) {
        return ApiResponse.of(courseworkService.update(id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<CourseworkResponse> publish(@PathVariable UUID id) {
        return ApiResponse.of(courseworkService.publish(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<CourseworkResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.of(courseworkService.cancel(id));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<CourseworkResponse> archive(@PathVariable UUID id) {
        return ApiResponse.of(courseworkService.archive(id));
    }
}
