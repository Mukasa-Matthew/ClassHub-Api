package com.classhub.announcement;

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
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AnnouncementResponse> create(@Valid @RequestBody CreateAnnouncementRequest request) {
        return ApiResponse.of(announcementService.create(request));
    }

    @GetMapping
    public ApiResponse<List<AnnouncementResponse>> list() {
        return ApiResponse.of(announcementService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<AnnouncementResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(announcementService.getById(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AnnouncementResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateAnnouncementRequest request) {
        return ApiResponse.of(announcementService.update(id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<AnnouncementResponse> publish(@PathVariable UUID id) {
        return ApiResponse.of(announcementService.publish(id));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<AnnouncementResponse> archive(@PathVariable UUID id) {
        return ApiResponse.of(announcementService.archive(id));
    }
}
