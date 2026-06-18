package com.afriland.approval.repository;

import com.afriland.approval.model.SmtpConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmtpConfigRepository extends JpaRepository<SmtpConfig, Long> {
}
