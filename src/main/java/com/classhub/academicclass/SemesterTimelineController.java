package com.classhub.academicclass;

import com.classhub.common.api.ApiResponse;
import com.classhub.security.ClassHubUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class SemesterTimelineController {

    private final SemesterTimelineService semesterTimelineService;

    public SemesterTimelineController(SemesterTimelineService semesterTimelineService) {
        this.semesterTimelineService = semesterTimelineService;
    }

    @GetMapping("/semester")
    public ApiResponse<SemesterTimelineResponse> currentSemester(
            @AuthenticationPrincipal ClassHubUserDetails principal) {
        return ApiResponse.of(semesterTimelineService.getForActiveMember(principal.getId()));
    }
}
