package com.classhub.academicclass;

import com.classhub.common.api.ApiResponse;
import com.classhub.security.ClassHubUserDetails;
import com.classhub.user.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ClassMembershipController {

    private final ClassMembershipService classMembershipService;
    private final UserService userService;

    public ClassMembershipController(ClassMembershipService classMembershipService, UserService userService) {
        this.classMembershipService = classMembershipService;
        this.userService = userService;
    }

    @PostMapping("/classes/join")
    public ApiResponse<ClassMembershipResponse> join(
            @AuthenticationPrincipal ClassHubUserDetails principal, @Valid @RequestBody JoinClassRequest request) {
        return ApiResponse.of(classMembershipService.joinExistingUser(
                userService.getById(principal.getId()), request));
    }

    @GetMapping("/me/class-membership")
    public ApiResponse<ClassMembershipResponse> currentMembership(
            @AuthenticationPrincipal ClassHubUserDetails principal) {
        return ApiResponse.of(classMembershipService.currentMembership(userService.getById(principal.getId())));
    }
}
