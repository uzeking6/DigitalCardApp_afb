package com.afriland.approval.controller;

import com.afriland.approval.model.CardRequest;
import com.afriland.approval.repository.CardRequestRepository;
import com.afriland.approval.service.VCardApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller
public class PublicController {

    private final CardRequestRepository cardRepo;
    private final VCardApiService vcardService;

    @org.springframework.beans.factory.annotation.Value("${app.public.url:http://localhost:5000}")
    private String publicUrl;

    public PublicController(CardRequestRepository cardRepo, VCardApiService vcardService) {
        this.cardRepo = cardRepo;
        this.vcardService = vcardService;
    }

    @GetMapping({"/", "/demande"})
    public String index() {
        return "index";
    }

    @PostMapping("/submit")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submit(@RequestBody Map<String, String> data) {
        String[] required = {"first_name", "last_name", "email", "mobile",
                "department_fr", "department_en", "job_title_fr", "job_title_en"};
        List<String> missing = new ArrayList<>();
        for (String f : required) {
            if (data.get(f) == null || data.get(f).trim().isEmpty()) missing.add(f);
        }
        if (!missing.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Champs manquants: " + String.join(", ", missing)));

        String email = data.get("email").trim();
        Optional<CardRequest> existing = cardRepo.findFirstByEmailAndStatusNotOrderByIdDesc(email, "Rejected");
        if (existing.isPresent()) {
            CardRequest ex = existing.get();
            String label = switch (ex.getStatus()) {
                case "Pending" -> "en attente de validation";
                case "Approved" -> "approuvee";
                case "Sent" -> "active";
                default -> ex.getStatus();
            };
            return ResponseEntity.status(409).body(Map.of("success", false,
                    "error", "Cet email (" + email + ") est deja associe a une demande " + label +
                            " pour " + ex.getFullName() + ". Veuillez utiliser une autre adresse email."));
        }

        CardRequest req = new CardRequest();
        req.setFirstName(data.getOrDefault("first_name", "").trim());
        req.setLastName(data.getOrDefault("last_name", "").trim());
        req.setEmail(email);
        req.setMobile(data.getOrDefault("mobile", "").trim());
        req.setPhone(data.getOrDefault("phone", "").trim());
        req.setFax(data.getOrDefault("fax", "").trim());
        req.setDepartmentFr(data.getOrDefault("department_fr", "").trim());
        req.setDepartmentEn(data.getOrDefault("department_en", "").trim());
        req.setJobTitleFr(data.getOrDefault("job_title_fr", "").trim());
        req.setJobTitleEn(data.getOrDefault("job_title_en", "").trim());
        req.setTitle(data.getOrDefault("title", "").trim());
        cardRepo.save(req);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/check-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkStatus(@RequestBody Map<String, String> data) {
        String email = data.getOrDefault("email", "").trim();
        if (email.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Veuillez saisir votre email."));

        Optional<CardRequest> opt = cardRepo.findFirstByEmailOrderByIdDesc(email);
        if (opt.isEmpty())
            return ResponseEntity.ok(Map.of("success", true, "found", false));

        CardRequest r = opt.get();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("found", true);
        result.put("status", r.getStatus());
        result.put("first_name", r.getFirstName());
        result.put("last_name", r.getLastName());
        result.put("department_fr", r.getDepartmentFr());
        result.put("job_title_fr", r.getJobTitleFr());
        result.put("submitted_at", r.getSubmittedAt() != null ? r.getSubmittedAt().toString() : null);
        result.put("reject_reason", r.getRejectReason());
        result.put("card_url", ("Approved".equals(r.getStatus()) || "Sent".equals(r.getStatus()))
                ? publicUrl + "/carte?email=" + java.net.URLEncoder.encode(r.getEmail(), java.nio.charset.StandardCharsets.UTF_8) : null);
        return ResponseEntity.ok(result);
    }
}
