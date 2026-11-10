package com.ankit.bustracking.controller;

import com.ankit.bustracking.dto.AdminRequestDTO;
import com.ankit.bustracking.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/register")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    public void createAdmin(@RequestBody @Valid AdminRequestDTO adminRequestDTO) {
        adminService.createAdmin(adminRequestDTO);
    }

    @PutMapping
    public void updateAdminPassword(@RequestBody @Valid AdminRequestDTO adminRequestDTO) {
        adminService.updateAdminPassword(adminRequestDTO);
    }
}
