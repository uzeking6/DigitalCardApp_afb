package com.afriland.approval.service;

import com.afriland.approval.model.CardRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class VCardApiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // F2 FIX: default points at the API's real port (9999), not 8767.
    @Value("${vcard.api.base:http://localhost:9999}")
    private String apiBase;

    @Value("${vcard.api.email:admin@afrilandfirstbank.com}")
    private String apiEmail;

    @Value("${vcard.api.password:admin}")
    private String apiPassword;

    @Value("${vcard.front.url:http://localhost:8766}")
    private String frontUrl;

    public String getFrontUrl() { return frontUrl; }

    public Map<String, Object> sendToVCardApp(CardRequest req) {
        try {
            // C4 FIX: connectTimeout set so an unreachable API fails fast.
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                    .build();

            // 1. Prime the CSRF cookie via a public GET. F1 FIX: real path is /api/cards.
            HttpRequest getReq = withTimeout(HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/api/cards"))
                    .GET()).build();
            client.send(getReq, HttpResponse.BodyHandlers.ofString());

            CookieManager cm = (CookieManager) client.cookieHandler().orElse(null);
            String xsrf = readXsrf(cm);

            // 2. Login. F1 FIX: real path is /api/auth/admin/login.
            //    S5 FIX: body built with Jackson, not string concatenation.
            String loginBody = MAPPER.writeValueAsString(
                    Map.of("email", apiEmail, "password", apiPassword));
            HttpRequest loginReq = withTimeout(HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/api/auth/admin/login"))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", xsrf)
                    .POST(HttpRequest.BodyPublishers.ofString(loginBody))).build();
            HttpResponse<String> loginRes = client.send(loginReq, HttpResponse.BodyHandlers.ofString());

            if (loginRes.statusCode() >= 400)
                return Map.of("success", false, "message", "vCard login failed: " + loginRes.statusCode());

            // Refresh XSRF after login (the token may have rotated).
            xsrf = readXsrf(cm);

            // 3. Build Excel
            byte[] excelBytes = buildExcel(req);

            // 4. Upload as multipart. F1 FIX: real path is /api/admin/data-import and the
            //    'scope' query parameter is REQUIRED by the controller.
            String boundary = "----FormBoundary" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = buildMultipart(boundary, "file", "card.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

            HttpRequest uploadReq = withTimeout(HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/api/admin/data-import?scope=cards&onConflict=overwrite"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("X-XSRF-TOKEN", xsrf)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))).build();
            HttpResponse<String> uploadRes = client.send(uploadReq, HttpResponse.BodyHandlers.ofString());

            boolean ok = uploadRes.statusCode() < 400;
            return Map.of("success", ok,
                    "message", ok ? "Card sent to DigitalCardApp." : "Upload failed: " + uploadRes.body(),
                    "cardUrl", frontUrl + "/#/?email=" + req.getEmail());
        } catch (Exception e) {
            return Map.of("success", false, "message", "vCard API error: " + e.getMessage());
        }
    }

    /** C4 FIX: every request carries a hard read timeout so a slow API can never hang the thread. */
    private HttpRequest.Builder withTimeout(HttpRequest.Builder builder) {
        return builder.timeout(Duration.ofSeconds(10));
    }

    private String readXsrf(CookieManager cm) {
        if (cm == null) return "";
        for (HttpCookie cookie : cm.getCookieStore().getCookies()) {
            if ("XSRF-TOKEN".equalsIgnoreCase(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return "";
    }

    public byte[] buildExcel(CardRequest req) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet ws = wb.createSheet("Cartes");
            String[] headers = {"email", "first_name", "last_name", "company", "title",
                    "phone", "fax", "mobile", "department_fr", "department_en",
                    "job_title_fr", "job_title_en"};
            Row hr = ws.createRow(0);
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(bold);
            }
            Row dr = ws.createRow(1);
            dr.createCell(0).setCellValue(req.getEmail());
            dr.createCell(1).setCellValue(req.getFirstName());
            dr.createCell(2).setCellValue(req.getLastName());
            dr.createCell(3).setCellValue("Afriland First Bank");
            dr.createCell(4).setCellValue(req.getTitle() != null && !req.getTitle().isEmpty() ? req.getTitle() : req.getJobTitleFr());
            dr.createCell(5).setCellValue(req.getPhone() != null ? req.getPhone() : "");
            dr.createCell(6).setCellValue(req.getFax() != null ? req.getFax() : "");
            dr.createCell(7).setCellValue(req.getMobile());
            dr.createCell(8).setCellValue(req.getDepartmentFr());
            dr.createCell(9).setCellValue(req.getDepartmentEn());
            dr.createCell(10).setCellValue(req.getJobTitleFr());
            dr.createCell(11).setCellValue(req.getJobTitleEn());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildMultipart(String boundary, String fieldName, String filename, String contentType, byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String nl = "\r\n";
        out.write(("--" + boundary + nl).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + filename + "\"" + nl).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + nl + nl).getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write((nl + "--" + boundary + "--" + nl).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
