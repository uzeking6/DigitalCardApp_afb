package com.afriland.approval.controller;

import com.afriland.approval.model.CardRequest;
import com.afriland.approval.repository.CardRequestRepository;
import com.afriland.approval.service.QrService;
import com.afriland.approval.service.SettingsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Serves the digital card. The card is rendered with the design template the admin
 * selected (classic / modern), reproduced from the Angular app via a background image.
 */
@Controller
public class CardViewController {

    private final CardRequestRepository cardRepo;
    private final QrService qrService;
    private final SettingsService settingsService;

    public CardViewController(CardRequestRepository cardRepo, QrService qrService, SettingsService settingsService) {
        this.cardRepo = cardRepo;
        this.qrService = qrService;
        this.settingsService = settingsService;
    }

    @GetMapping({"/carte", "/card"})
    public String card(@RequestParam(value = "email", required = false) String email, Model model) {
        CardRequest c = findReady(email);
        if (c == null) {
            model.addAttribute("email", email == null ? "" : email);
            return "carte-introuvable";
        }
        SettingsService.CardTemplate tpl = settingsService.activeTemplate();
        model.addAttribute("c", c);
        model.addAttribute("phone", notEmpty(c.getPhone()) ? c.getPhone() : QrService.FIXED_PHONE);
        model.addAttribute("fax", notEmpty(c.getFax()) ? c.getFax() : QrService.FIXED_FAX);
        model.addAttribute("titleDisplay", notEmpty(c.getTitle()) ? c.getTitle() : c.getJobTitleFr());
        model.addAttribute("qr", qrService.qrDataUri(qrService.buildVCard(c), 220));
        model.addAttribute("vcfUrl", "/carte/vcf?email=" + urlenc(c.getEmail()));
        // design template
        model.addAttribute("bg", tpl.background());
        model.addAttribute("bgSize", tpl.backgroundSize());
        model.addAttribute("padTop", tpl.padTop());
        model.addAttribute("padRight", tpl.padRight());
        model.addAttribute("padBottom", tpl.padBottom());
        model.addAttribute("padLeft", tpl.padLeft());
        return "carte";
    }

    @GetMapping("/carte/vcf")
    @ResponseBody
    public ResponseEntity<byte[]> vcf(@RequestParam("email") String email) {
        CardRequest c = findReady(email);
        if (c == null) return ResponseEntity.notFound().build();
        byte[] body = qrService.buildVCard(c).getBytes(StandardCharsets.UTF_8);
        String fname = (safe(c.getFirstName()) + "-" + safe(c.getLastName()) + ".vcf").toLowerCase();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fname + "\"")
                .contentType(MediaType.parseMediaType("text/vcard; charset=utf-8"))
                .body(body);
    }

    private CardRequest findReady(String email) {
        if (email == null || email.isBlank()) return null;
        List<CardRequest> all = cardRepo.findByEmailIgnoreCaseOrderByIdDesc(email.trim());
        for (CardRequest r : all) {
            if ("Approved".equals(r.getStatus()) || "Sent".equals(r.getStatus())) return r;
        }
        return null;
    }

    private static boolean notEmpty(String s) { return s != null && !s.trim().isEmpty(); }
    private static String safe(String s) { return s == null ? "" : s.replaceAll("[^a-zA-Z0-9]+", ""); }
    private static String urlenc(String s) { return java.net.URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }
}
