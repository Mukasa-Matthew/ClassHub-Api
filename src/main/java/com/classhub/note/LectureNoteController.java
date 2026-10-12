package com.classhub.note;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notes")
public class LectureNoteController {

    private final LectureNoteService lectureNoteService;

    public LectureNoteController(LectureNoteService lectureNoteService) {
        this.lectureNoteService = lectureNoteService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LectureNoteResponse> create(@Valid @RequestBody CreateLectureNoteRequest request) {
        return ApiResponse.of(lectureNoteService.create(request));
    }

    @GetMapping
    public ApiResponse<List<LectureNoteResponse>> list(
            @RequestParam(required = false) UUID courseUnitId,
            @RequestParam(required = false) LectureNoteStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        LectureNoteService.ApiPage result = lectureNoteService.list(courseUnitId, status, page, size);
        return ApiResponse.of(result.data(), result.pagination());
    }

    @GetMapping("/{id}")
    public ApiResponse<LectureNoteResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(lectureNoteService.getById(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<LectureNoteResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateLectureNoteRequest request) {
        return ApiResponse.of(lectureNoteService.update(id, request));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<LectureNoteResponse> complete(@PathVariable UUID id) {
        return ApiResponse.of(lectureNoteService.complete(id));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<LectureNoteResponse> archive(@PathVariable UUID id) {
        return ApiResponse.of(lectureNoteService.archive(id));
    }

    @PostMapping("/{id}/ai/process")
    public ApiResponse<LectureNoteAiOutputResponse> processAi(
            @PathVariable UUID id, @Valid @RequestBody ProcessLectureNoteRequest request) {
        return ApiResponse.of(lectureNoteService.processAi(id, request));
    }

    @GetMapping("/{id}/ai/outputs")
    public ApiResponse<List<LectureNoteAiOutputResponse>> listAiOutputs(@PathVariable UUID id) {
        return ApiResponse.of(lectureNoteService.listAiOutputs(id));
    }
}
