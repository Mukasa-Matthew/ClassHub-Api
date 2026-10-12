package com.classhub.dashboard;

import com.classhub.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/student")
    public ApiResponse<StudentDashboardResponse> student() {
        return ApiResponse.of(dashboardService.studentDashboard());
    }

    @GetMapping("/class-rep")
    public ApiResponse<ClassRepDashboardResponse> classRep() {
        return ApiResponse.of(dashboardService.classRepDashboard());
    }

    @GetMapping("/admin")
    public ApiResponse<AdminDashboardResponse> admin() {
        return ApiResponse.of(dashboardService.adminDashboard());
    }
}
