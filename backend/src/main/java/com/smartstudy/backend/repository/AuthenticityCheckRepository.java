package com.smartstudy.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartstudy.backend.model.AuthenticityCheck;

public interface AuthenticityCheckRepository extends JpaRepository<AuthenticityCheck, Long> {
    List<AuthenticityCheck> findTop20ByUserIdOrderByIdDesc(Long userId);
}
