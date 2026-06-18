package com.afriland.approval.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Fire-and-forget e-mail wrappers so the approve/reject/create flows return immediately
 * instead of blocking the request thread on SMTP. Failures are swallowed (best-effort);
 * EmailService already logs/returns its own status.
 */
@Service
public class AsyncNotificationService {

    private final EmailService emailService;

    public AsyncNotificationService(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async("approvalExecutor")
    public void sendApprovalEmailAsync(String email, String fullName, String cardUrl) {
        try {
            emailService.sendApprovalEmail(email, fullName, cardUrl);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    @Async("approvalExecutor")
    public void sendRejectionEmailAsync(String email, String fullName, String reason, String adminEmail) {
        try {
            emailService.sendRejectionEmail(email, fullName, reason, adminEmail);
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
