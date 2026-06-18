package com.afriland.approval.config;

import com.afriland.approval.model.AdminCredentials;
import com.afriland.approval.model.SmtpConfig;
import com.afriland.approval.repository.AdminCredentialsRepository;
import com.afriland.approval.repository.SmtpConfigRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Configuration
public class AppConfig {

    /** S2 FIX: salted, adaptive BCrypt hashing replaces the old unsalted SHA-256. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CommandLineRunner initData(AdminCredentialsRepository adminRepo,
                               SmtpConfigRepository smtpRepo,
                               PasswordEncoder passwordEncoder,
                               Environment env) {
        return args -> {
            if (adminRepo.count() == 0) {
                AdminCredentials admin = new AdminCredentials();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode(
                        env.getProperty("APPROVAL_ADMIN_PASSWORD", "afriland2024")));
                adminRepo.save(admin);
                System.out.println("[INIT] Approval admin created: admin / "
                        + env.getProperty("APPROVAL_ADMIN_PASSWORD", "afriland2024")
                        + " (CHANGE THIS PASSWORD)");
            }
            if (smtpRepo.count() == 0) {
                // SMTP is configured entirely from .env so the app is ready on first run.
                SmtpConfig smtp = new SmtpConfig();
                smtp.setHost(env.getProperty("SMTP_HOST", "smtp.gmail.com"));
                smtp.setPort(Integer.parseInt(env.getProperty("SMTP_PORT", "587")));
                smtp.setProtocol(env.getProperty("SMTP_PROTOCOL", "STARTTLS"));
                smtp.setUsername(env.getProperty("SMTP_USER", ""));
                smtp.setPassword(env.getProperty("SMTP_PASS", ""));
                smtp.setFromEmail(env.getProperty("SMTP_FROM", env.getProperty("SMTP_USER", "")));
                smtp.setFromName(env.getProperty("SMTP_FROM_NAME", "Afriland First Bank RH"));
                smtp.setEnabled(Boolean.parseBoolean(env.getProperty("SMTP_ENABLED", "false")));
                smtpRepo.save(smtp);
                System.out.println("[INIT] SMTP config seeded from environment (enabled="
                        + smtp.isEnabled() + ", host=" + smtp.getHost() + ")");
            }
        };
    }

    /**
     * Legacy hash, kept ONLY to recognise and transparently migrate pre-existing
     * SHA-256 password rows on the user's next successful login. Do not use for new hashes.
     *
     * @deprecated replaced by {@link #passwordEncoder()} (BCrypt).
     */
    @Deprecated
    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** True for a 64-char lowercase-hex string, i.e. a legacy SHA-256 hash. */
    public static boolean isLegacySha256(String stored) {
        return stored != null && stored.matches("[0-9a-f]{64}");
    }
}
