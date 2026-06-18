package com.afriland.approval.controller;

import com.afriland.approval.config.AppConfig;
import com.afriland.approval.model.*;
import com.afriland.approval.repository.*;
import com.afriland.approval.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
public class AdminController {

    private final CardRequestRepository cardRepo;
    private final AdminCredentialsRepository adminRepo;
    private final SmtpConfigRepository smtpRepo;
    private final EmailService emailService;
    private final VCardApiService vcardService;
    private final PasswordEncoder passwordEncoder;
    private final AsyncNotificationService asyncNotifier;
    private final SettingsService settingsService;

    @org.springframework.beans.factory.annotation.Value("${app.public.url:http://localhost:5000}")
    private String publicUrl;

    /** When false (default for standalone), the card is served by THIS app and no external
     *  vCard backend is contacted. Set VCARD_PUSH_ENABLED=true to also push to a vcard-api. */
    @org.springframework.beans.factory.annotation.Value("${vcard.push.enabled:false}")
    private boolean vcardPushEnabled;

    private String selfCardUrl(String email) {
        return publicUrl + "/carte?email="
                + java.net.URLEncoder.encode(email == null ? "" : email, java.nio.charset.StandardCharsets.UTF_8);
    }

    public AdminController(CardRequestRepository cardRepo, AdminCredentialsRepository adminRepo,
            SmtpConfigRepository smtpRepo, EmailService emailService, VCardApiService vcardService,
            PasswordEncoder passwordEncoder, AsyncNotificationService asyncNotifier, SettingsService settingsService) {
        this.cardRepo = cardRepo;
        this.adminRepo = adminRepo;
        this.smtpRepo = smtpRepo;
        this.emailService = emailService;
        this.vcardService = vcardService;
        this.passwordEncoder = passwordEncoder;
        this.asyncNotifier = asyncNotifier;
        this.settingsService = settingsService;
    }

    private boolean isAdmin(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute("admin"));
    }

    /**
     * S2 FIX: verify with BCrypt. If the stored value is a legacy SHA-256 hash that matches,
     * accept it once and transparently re-hash with BCrypt (seamless migration).
     */
    private boolean verifyAndMaybeMigrate(AdminCredentials admin, String rawPassword) {
        String stored = admin.getPassword();
        if (stored == null) return false;
        if (passwordEncoder.matches(rawPassword, stored)) {
            return true;
        }
        if (AppConfig.isLegacySha256(stored) && stored.equals(AppConfig.sha256(rawPassword))) {
            admin.setPassword(passwordEncoder.encode(rawPassword));
            adminRepo.save(admin);
            return true;
        }
        return false;
    }

    @GetMapping("/login")
    public String loginAlias() {
        return "login";
    }

    @GetMapping("/admin/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/admin/authenticate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> authenticate(@RequestBody Map<String, String> data,
            HttpSession session) {
        String user = data.getOrDefault("username", "").trim();
        String pass = data.getOrDefault("password", "").trim();
        if (user.isEmpty() || pass.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Identifiants requis."));

        Optional<AdminCredentials> found = adminRepo.findByUsername(user);
        if (found.isEmpty() || !verifyAndMaybeMigrate(found.get(), pass))
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Identifiants invalides."));

        session.setAttribute("admin", true);
        session.setAttribute("admin_user", user);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/admin/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/admin/login";
    }

    @GetMapping("/admin")
    public String adminPage(@RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "approvals") String tab,
            HttpSession session, Model model) {
        if (!isAdmin(session))
            return "redirect:/admin/login";

        List<CardRequest> requests = "all".equals(status)
                ? cardRepo.findAllByOrderByIdDesc()
                : cardRepo.findByStatusOrderByIdDesc(status);

        Map<String, Long> stats = new HashMap<>();
        for (Object[] row : cardRepo.countByStatus()) {
            stats.put((String) row[0], (Long) row[1]);
        }
        long total = stats.values().stream().mapToLong(v -> v).sum();

        List<CardRequest> activeCards = cardRepo.findByStatusInOrderByIdDesc(List.of("Approved", "Sent"));
        SmtpConfig smtp = emailService.getConfig();

        model.addAttribute("requests", requests);
        model.addAttribute("stats", stats);
        model.addAttribute("total", total);
        model.addAttribute("currentFilter", status);
        model.addAttribute("currentTab", tab);
        model.addAttribute("adminUser", session.getAttribute("admin_user"));
        model.addAttribute("smtpConfigured",
                smtp.isEnabled() && !smtp.getUsername().isEmpty() && !smtp.getPassword().isEmpty());
        model.addAttribute("activeCards", activeCards);
        model.addAttribute("vcardFrontUrl", vcardService.getFrontUrl());
        model.addAttribute("activeDesign", settingsService.getActiveDesign());
        model.addAttribute("designs", settingsService.templates());
        return "admin";
    }

    @PostMapping("/approve/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session))
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Non autorise"));
        Optional<CardRequest> opt = cardRepo.findById(id);
        if (opt.isEmpty())
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Demande introuvable."));

        CardRequest req = opt.get();
        // The card is created and immediately viewable in THIS app.
        req.setStatus("Sent");
        req.setRejectReason(null);
        cardRepo.save(req);

        String cardUrl = selfCardUrl(req.getEmail());

        // Optional best-effort push to an external vcard-api (off by default).
        Map<String, Object> vcardResult = vcardPushEnabled
                ? vcardService.sendToVCardApp(req)
                : Map.of("success", true, "message", "Carte servie localement.");
        boolean vcardOk = (boolean) vcardResult.getOrDefault("success", false);

        // Email the employee the link to their card — independent of any external push.
        asyncNotifier.sendApprovalEmailAsync(req.getEmail(), req.getFullName(), cardUrl);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("card_url", cardUrl);
        response.put("vcard_sent", vcardOk);
        response.put("vcard_message", vcardResult.get("message"));
        response.put("email_queued", true);
        response.put("email_msg", "Email envoye (si SMTP active).");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reject/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long id, @RequestBody Map<String, String> data,
            HttpSession session) {
        if (!isAdmin(session))
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Non autorise"));
        Optional<CardRequest> opt = cardRepo.findById(id);
        if (opt.isEmpty())
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Demande introuvable."));

        String reason = data.getOrDefault("reason", "").trim();
        if (reason.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Motif de refus requis."));

        CardRequest req = opt.get();
        req.setStatus("Rejected");
        req.setRejectReason(reason);
        cardRepo.save(req);

        String adminEmail = data.getOrDefault("admin_email", "");
        asyncNotifier.sendRejectionEmailAsync(req.getEmail(), req.getFullName(), reason, adminEmail);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("email_queued", true);
        response.put("email_msg", "Email de refus en file d'attente.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/update-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateStatus(@RequestBody Map<String, Object> data,
            HttpSession session) {
        if (!isAdmin(session))
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Non autorise"));
        Long id = Long.valueOf(data.get("id").toString());
        String newStatus = data.getOrDefault("status", "Pending").toString();
        Optional<CardRequest> opt = cardRepo.findById(id);
        if (opt.isEmpty())
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Introuvable."));
        CardRequest req = opt.get();
        req.setStatus(newStatus);
        if ("Pending".equals(newStatus))
            req.setRejectReason(null);
        cardRepo.save(req);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/admin/create-card")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createCard(@RequestBody Map<String, String> data, HttpSession session) {
        if (!isAdmin(session))
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Non autorise"));

        String[] required = { "first_name", "last_name", "email", "mobile",
                "department_fr", "department_en", "job_title_fr", "job_title_en" };
        List<String> missing = new ArrayList<>();
        for (String f : required) {
            if (data.get(f) == null || data.get(f).trim().isEmpty())
                missing.add(f);
        }
        if (!missing.isEmpty())
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Champs manquants: " + String.join(", ", missing)));

        String email = data.get("email").trim();
        Optional<CardRequest> existing = cardRepo.findFirstByEmailOrderByIdDesc(email);

        CardRequest req;
        if (existing.isPresent()) {
            req = existing.get();
        } else {
            req = new CardRequest();
            req.setEmail(email);
        }
        req.setFirstName(data.getOrDefault("first_name", "").trim());
        req.setLastName(data.getOrDefault("last_name", "").trim());
        req.setMobile(data.getOrDefault("mobile", "").trim());
        req.setPhone(data.getOrDefault("phone", "").trim());
        req.setFax(data.getOrDefault("fax", "").trim());
        req.setDepartmentFr(data.getOrDefault("department_fr", "").trim());
        req.setDepartmentEn(data.getOrDefault("department_en", "").trim());
        req.setJobTitleFr(data.getOrDefault("job_title_fr", "").trim());
        req.setJobTitleEn(data.getOrDefault("job_title_en", "").trim());
        req.setTitle(data.getOrDefault("title", "").trim());
        req.setStatus("Sent");
        req.setRejectReason(null);
        cardRepo.save(req);

        String cardUrl = selfCardUrl(req.getEmail());

        Map<String, Object> vcardResult = vcardPushEnabled
                ? vcardService.sendToVCardApp(req)
                : Map.of("success", true, "message", "Carte servie localement.");
        boolean vcardOk = (boolean) vcardResult.getOrDefault("success", false);

        asyncNotifier.sendApprovalEmailAsync(req.getEmail(), req.getFullName(), cardUrl);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("card_url", cardUrl);
        response.put("vcard_sent", vcardOk);
        response.put("vcard_message", vcardResult.get("message"));
        response.put("email_queued", true);
        response.put("email_msg", "Email envoye (si SMTP active).");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin/design")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setDesign(@RequestBody Map<String, String> data, HttpSession session) {
        if (!isAdmin(session))
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Non autorise"));
        settingsService.setActiveDesign(data.getOrDefault("design", SettingsService.DEFAULT_DESIGN));
        return ResponseEntity.ok(Map.of("success", true, "design", settingsService.getActiveDesign(),
                "message", "Design enregistre."));
    }

    @PostMapping("/admin/clear-all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearAll(HttpSession session) {
        if (!isAdmin(session))
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Non autorise"));
        long count = cardRepo.count();
        cardRepo.deleteAll();
        return ResponseEntity
                .ok(Map.of("success", true, "deleted", count, "message", count + " demande(s) supprimee(s)."));
    }

    @PostMapping("/admin/change-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> data,
            HttpSession session) {
        if (!isAdmin(session))
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Non autorise"));
        String current = data.getOrDefault("current_password", "");
        String newPw = data.getOrDefault("new_password", "");
        String confirm = data.getOrDefault("confirm_password", "");
        if (current.isEmpty() || newPw.isEmpty() || confirm.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Tous les champs sont requis."));
        if (newPw.length() < 6)
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Minimum 6 caracteres."));
        if (!newPw.equals(confirm))
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Les mots de passe ne correspondent pas."));
        String user = (String) session.getAttribute("admin_user");
        Optional<AdminCredentials> found = adminRepo.findByUsername(user);
        if (found.isEmpty() || !verifyAndMaybeMigrate(found.get(), current))
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Mot de passe actuel incorrect."));
        AdminCredentials admin = found.get();
        admin.setPassword(passwordEncoder.encode(newPw));
        adminRepo.save(admin);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/admin/smtp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSmtp(HttpSession session) {
        if (!isAdmin(session))
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Non autorise"));
        SmtpConfig cfg = emailService.getConfig();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", cfg.isEnabled());
        config.put("host", cfg.getHost());
        config.put("port", cfg.getPort());
        config.put("protocol", cfg.getProtocol());
        config.put("username", cfg.getUsername());
        config.put("password_set", !cfg.getPassword().isEmpty());
        config.put("from_email", cfg.getFromEmail());
        config.put("from_name", cfg.getFromName());
        return ResponseEntity.ok(Map.of("success", true, "config", config));
    }

    @PostMapping("/admin/smtp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveSmtp(@RequestBody Map<String, Object> data, HttpSession session) {
        if (!isAdmin(session))
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Non autorise"));
        SmtpConfig cfg = emailService.getConfig();
        cfg.setEnabled(Boolean.TRUE.equals(data.get("enabled")));
        cfg.setHost(data.getOrDefault("host", "smtp.gmail.com").toString().trim());
        cfg.setPort(data.get("port") != null ? Integer.parseInt(data.get("port").toString()) : 587);
        cfg.setProtocol(data.getOrDefault("protocol", "STARTTLS").toString());
        cfg.setUsername(data.getOrDefault("username", "").toString().trim());
        cfg.setFromEmail(data.getOrDefault("from_email", "").toString().trim());
        cfg.setFromName(data.getOrDefault("from_name", "").toString().trim());
        String pw = data.getOrDefault("password", "").toString();
        if (!pw.isEmpty())
            cfg.setPassword(pw);
        if (Boolean.TRUE.equals(data.get("clear_password")))
            cfg.setPassword("");
        emailService.saveConfig(cfg);
        return ResponseEntity.ok(Map.of("success", true, "message", "Configuration SMTP enregistree."));
    }

    @PostMapping("/admin/smtp/test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testSmtp(@RequestBody Map<String, String> data, HttpSession session) {
        if (!isAdmin(session))
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Non autorise"));
        String testTo = data.getOrDefault("test_email", "").trim();
        if (testTo.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Email de test requis."));
        Map<String, Object> result = emailService.sendTestEmail(testTo);
        boolean ok = (boolean) result.getOrDefault("sent", false);
        return ok ? ResponseEntity.ok(Map.of("success", true, "message", result.get("message")))
                : ResponseEntity.status(500).body(Map.of("success", false, "error", result.get("message")));
    }

    @GetMapping("/excel/{id}")
    public ResponseEntity<byte[]> downloadExcel(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session))
            return ResponseEntity.status(401).build();
        Optional<CardRequest> opt = cardRepo.findById(id);
        if (opt.isEmpty())
            return ResponseEntity.notFound().build();
        try {
            byte[] data = vcardService.buildExcel(opt.get());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=card_" + id + ".xlsx")
                    .contentType(MediaType
                            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
