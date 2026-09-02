package com.classhub.academicclass;

import com.classhub.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/classes")
public class AdminAcademicClassController {

    private final AcademicClassService academicClassService;

    public AdminAcademicClassController(AcademicClassService academicClassService) {
        this.academicClassService = academicClassService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AcademicClassResponse> create(@Valid @RequestBody CreateAcademicClassRequest request) {
        return ApiResponse.of(academicClassService.create(request));
    }

    @GetMapping
    public ApiResponse<List<AcademicClassResponse>> list() {
        return ApiResponse.of(academicClassService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<AcademicClassResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(academicClassService.getById(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AcademicClassResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateAcademicClassRequest request) {
        return ApiResponse.of(academicClassService.update(id, request));
    }

    @PostMapping("/{classId}/class-representative/{userId}")
    public ApiResponse<Void> assignClassRepresentative(@PathVariable UUID classId, @PathVariable UUID userId) {
        academicClassService.assignClassRepresentative(classId, userId);
        return ApiResponse.of(null);
    }
}
