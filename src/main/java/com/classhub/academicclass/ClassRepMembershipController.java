package com.classhub.academicclass;

import com.classhub.common.api.ApiResponse;
import com.classhub.security.ClassHubUserDetails;
import com.classhub.user.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/class-rep")
public class ClassRepMembershipController {

    private final ClassRepMembershipService classRepMembershipService;
    private final SemesterTimelineService semesterTimelineService;

    public ClassRepMembershipController(
            ClassRepMembershipService classRepMembershipService,
            SemesterTimelineService semesterTimelineService) {
        this.classRepMembershipService = classRepMembershipService;
        this.semesterTimelineService = semesterTimelineService;
    }

    @GetMapping("/members")
    public ApiResponse<List<ClassMemberDirectoryResponse>> listMembers(
            @AuthenticationPrincipal ClassHubUserDetails principal,
            @RequestParam(required = false) MembershipStatus status,
            @RequestParam(required = false) String search) {
        return ApiResponse.of(classRepMembershipService.listMembers(principal.getId(), status, search));
    }

    @PostMapping("/members/{membershipId}/approve")
    public ApiResponse<ClassMemberDirectoryResponse> approve(
            @AuthenticationPrincipal ClassHubUserDetails principal, @PathVariable UUID membershipId) {
        return ApiResponse.of(classRepMembershipService.approve(principal.getId(), membershipId));
    }

    @PostMapping("/members/{membershipId}/reject")
    public ApiResponse<ClassMemberDirectoryResponse> reject(
            @AuthenticationPrincipal ClassHubUserDetails principal, @PathVariable UUID membershipId) {
        return ApiResponse.of(classRepMembershipService.reject(principal.getId(), membershipId));
    }

    @PostMapping("/members/{membershipId}/deactivate")
    public ApiResponse<ClassMemberDirectoryResponse> deactivate(
            @AuthenticationPrincipal ClassHubUserDetails principal, @PathVariable UUID membershipId) {
        return ApiResponse.of(classRepMembershipService.deactivate(principal.getId(), membershipId));
    }

    @PostMapping("/members/{membershipId}/reactivate")
    public ApiResponse<ClassMemberDirectoryResponse> reactivate(
            @AuthenticationPrincipal ClassHubUserDetails principal, @PathVariable UUID membershipId) {
        return ApiResponse.of(classRepMembershipService.reactivate(principal.getId(), membershipId));
    }

    @GetMapping("/class-list")
    public ApiResponse<ClassListResponse> classList(@AuthenticationPrincipal ClassHubUserDetails principal) {
        return ApiResponse.of(classRepMembershipService.classList(principal.getId()));
    }

    @GetMapping("/class")
    public ApiResponse<AcademicClassResponse> ownClass(@AuthenticationPrincipal ClassHubUserDetails principal) {
        return ApiResponse.of(classRepMembershipService.ownClass(principal.getId()));
    }

    @PostMapping("/class/join-code/regenerate")
    public ApiResponse<AcademicClassResponse> regenerateJoinCode(
            @AuthenticationPrincipal ClassHubUserDetails principal) {
        return ApiResponse.of(classRepMembershipService.regenerateJoinCode(principal.getId()));
    }

    @PutMapping("/class/semester")
    public ApiResponse<SemesterTimelineResponse> updateSemesterTimeline(
            @AuthenticationPrincipal ClassHubUserDetails principal,
            @Valid @RequestBody UpdateSemesterTimelineRequest request) {
        return ApiResponse.of(semesterTimelineService.updateForClassRep(principal.getId(), request));
    }
}
