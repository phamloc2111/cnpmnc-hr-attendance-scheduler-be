package com.company.hr.attendance.identity.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {
    public record Workspace(String title, String description) {}

    @GetMapping("/employee")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public Workspace employee() {
        return new Workspace("Không gian nhân viên", "Theo dõi ca làm và giờ công của bạn.");
    }

    @GetMapping("/management")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public Workspace management() {
        return new Workspace("Không gian quản lý", "Điều phối lịch làm việc và theo dõi chấm công của đội ngũ.");
    }

    @GetMapping("/administration")
    @PreAuthorize("hasRole('ADMIN')")
    public Workspace administration() {
        return new Workspace("Không gian quản trị", "Quản trị tài khoản và cấu hình hệ thống ShiftSync.");
    }
}
