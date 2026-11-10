package com.ankit.bustracking.repository;

import com.ankit.bustracking.model.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin,Long> {
    public Optional<Admin> findByAdminId(String adminId);
}
