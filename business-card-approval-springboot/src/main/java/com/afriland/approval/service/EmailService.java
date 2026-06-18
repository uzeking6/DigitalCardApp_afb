package com.afriland.approval.service;

import com.afriland.approval.model.SmtpConfig;
import com.afriland.approval.repository.SmtpConfigRepository;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class EmailService {

    private final SmtpConfigRepository smtpRepo;

    public EmailService(SmtpConfigRepository smtpRepo) {
        this.smtpRepo = smtpRepo;
    }

    public SmtpConfig getConfig() {
        return smtpRepo.findById(1L).orElseGet(() -> {
            SmtpConfig c = new SmtpConfig();
            return smtpRepo.save(c);
        });
    }

    public void saveConfig(SmtpConfig config) {
        config.setId(1L);
        smtpRepo.save(config);
    }

    private Session buildSession(SmtpConfig cfg) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", cfg.getHost());
        props.put("mail.smtp.port", String.valueOf(cfg.getPort()));
        if ("SSL/TLS".equals(cfg.getProtocol())) {
            props.put("mail.smtp.ssl.enable", "true");
        } else if ("STARTTLS".equals(cfg.getProtocol())) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");

        return Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(cfg.getUsername(), cfg.getPassword());
            }
        });
    }

    private String fromAddress(SmtpConfig cfg) {
        if (cfg.getFromName() == null || cfg.getFromName().isEmpty()) return cfg.getFromEmail();
        return cfg.getFromName() + " <" + cfg.getFromEmail() + ">";
    }

    public Map<String, Object> sendApprovalEmail(String to, String name, String cardUrl) {
        SmtpConfig cfg = getConfig();
        if (!cfg.isEnabled() || cfg.getUsername().isEmpty() || cfg.getPassword().isEmpty())
            return Map.of("sent", false, "message", "SMTP disabled or not configured.");
        try {
            Session session = buildSession(cfg);
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(fromAddress(cfg)));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            msg.setSubject("Votre carte de visite digitale est prete !");
            String sn = (cfg.getFromName() == null || cfg.getFromName().isEmpty()) ? "Administration RH" : cfg.getFromName();
            String html = "<div style='font-family:Arial;max-width:600px;margin:0 auto'>"
                + "<div style='background:#a8232f;padding:20px 28px'>"
                + "<img src='https://upload.wikimedia.org/wikipedia/fr/e/eb/Logo_Afriland.png' style='height:32px;filter:brightness(0) invert(1)'>"
                + "</div><div style='padding:28px;border:1px solid #e5e1d8;border-top:none'>"
                + "<p>Bonjour <strong>" + name + "</strong>,</p>"
                + "<p>Votre carte de visite digitale a ete creee et est maintenant disponible.</p>"
                + "<div style='text-align:center;margin:28px 0'><a href='" + cardUrl + "' "
                + "style='background:#a8232f;color:#fff;padding:14px 32px;border-radius:4px;text-decoration:none;font-weight:600'>Voir ma carte de visite</a></div>"
                + "<p style='font-size:13px;color:#666'>Ou copiez ce lien :<br><a href='" + cardUrl + "' style='color:#a8232f'>" + cardUrl + "</a></p>"
                + "<p style='color:#888;margin-top:24px'>Cordialement,<br><strong>" + sn + "</strong></p></div></div>";
            msg.setContent(html, "text/html; charset=UTF-8");
            Transport.send(msg);
            return Map.of("sent", true, "message", "Email sent.");
        } catch (Exception e) {
            return Map.of("sent", false, "message", e.getMessage());
        }
    }

    public Map<String, Object> sendRejectionEmail(String to, String name, String reason, String adminEmail) {
        SmtpConfig cfg = getConfig();
        if (!cfg.isEnabled() || cfg.getUsername().isEmpty() || cfg.getPassword().isEmpty())
            return Map.of("sent", false, "message", "SMTP disabled or not configured.");
        try {
            Session session = buildSession(cfg);
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(fromAddress(cfg)));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            msg.setSubject("Demande de carte de visite - Non approuvee");
            if (adminEmail != null && !adminEmail.isEmpty())
                msg.setReplyTo(new Address[]{new InternetAddress(adminEmail)});
            String contact = (adminEmail != null && !adminEmail.isEmpty()) ? adminEmail : cfg.getFromEmail();
            String sn = (cfg.getFromName() == null || cfg.getFromName().isEmpty()) ? "Administration RH" : cfg.getFromName();
            String html = "<div style='font-family:Arial;max-width:600px;margin:0 auto'>"
                + "<div style='background:#a8232f;padding:20px 28px'>"
                + "<img src='https://upload.wikimedia.org/wikipedia/fr/e/eb/Logo_Afriland.png' style='height:32px;filter:brightness(0) invert(1)'>"
                + "</div><div style='padding:28px;border:1px solid #e5e1d8;border-top:none'>"
                + "<p>Bonjour <strong>" + name + "</strong>,</p>"
                + "<p>Votre demande de carte de visite n'a pas pu etre approuvee :</p>"
                + "<div style='background:#fdecea;border-left:4px solid #a8232f;padding:14px 18px;margin:18px 0;border-radius:4px'>"
                + "<p style='color:#a8232f;margin:0'><strong>Motif :</strong> " + reason + "</p></div>"
                + "<p>Contact : <a href='mailto:" + contact + "' style='color:#a8232f'>" + contact + "</a></p>"
                + "<p style='color:#888;margin-top:24px'>Cordialement,<br><strong>" + sn + "</strong></p></div></div>";
            msg.setContent(html, "text/html; charset=UTF-8");
            Transport.send(msg);
            return Map.of("sent", true, "message", "Email sent.");
        } catch (Exception e) {
            return Map.of("sent", false, "message", e.getMessage());
        }
    }

    public Map<String, Object> sendTestEmail(String to) {
        SmtpConfig cfg = getConfig();
        if (cfg.getUsername().isEmpty() || cfg.getPassword().isEmpty())
            return Map.of("sent", false, "message", "SMTP not configured.");
        try {
            Session session = buildSession(cfg);
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(fromAddress(cfg)));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            msg.setSubject("Test SMTP - Digital Card App");
            msg.setContent("<div style='font-family:Arial;max-width:500px;margin:auto;padding:24px'>"
                + "<h2 style='color:#a8232f'>Test SMTP reussi !</h2>"
                + "<p>La configuration SMTP fonctionne correctement.</p>"
                + "<p style='color:#888;margin-top:20px'>- Afriland First Bank</p></div>", "text/html; charset=UTF-8");
            Transport.send(msg);
            return Map.of("sent", true, "message", "Email de test envoye a " + to + ".");
        } catch (Exception e) {
            return Map.of("sent", false, "message", e.getMessage());
        }
    }
}
