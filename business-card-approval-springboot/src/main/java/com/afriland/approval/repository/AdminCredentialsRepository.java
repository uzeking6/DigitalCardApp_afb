package com.afriland.approval.repository;

import com.afriland.approval.model.AdminCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdminCredentialsRepository extends JpaRepository<AdminCredentials, Long> {
    Optional<AdminCredentials> findByUsername(String username);

    /** @deprecated legacy SHA-256 lookup; use {@link #findByUsername(String)} + PasswordEncoder. */
    @Deprecated
    Optional<AdminCredentials> findByUsernameAndPassword(String username, String password);
}
