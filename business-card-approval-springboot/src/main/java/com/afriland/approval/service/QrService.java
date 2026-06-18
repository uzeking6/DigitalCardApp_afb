package com.afriland.approval.service;

import com.afriland.approval.model.CardRequest;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the contact vCard text and a scannable QR code (as a base64 PNG data URI),
 * so the card page can show a "scan" code and a downloadable .vcf — all rendered by
 * this app, no external service required.
 */
@Service
public class QrService {

    public static final String FIXED_PHONE = "222 233 068";
    public static final String FIXED_FAX   = "222 221 785";
    public static final String COMPANY     = "Afriland First Bank";
    public static final String WEBSITE     = "https://www.afrilandfirstbank.com";

    /** Standard vCard 3.0 so phones can save the contact directly from the QR or the .vcf. */
    public String buildVCard(CardRequest c) {
        String first = nz(c.getFirstName());
        String last  = nz(c.getLastName());
        String title = !nz(c.getTitle()).isEmpty() ? c.getTitle() : nz(c.getJobTitleFr());
        String phone = !nz(c.getPhone()).isEmpty() ? c.getPhone() : FIXED_PHONE;
        String fax   = !nz(c.getFax()).isEmpty()   ? c.getFax()   : FIXED_FAX;
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCARD\r\n");
        sb.append("VERSION:3.0\r\n");
        sb.append("N:").append(last).append(';').append(first).append(";;;\r\n");
        sb.append("FN:").append(first).append(' ').append(last).append("\r\n");
        sb.append("ORG:").append(COMPANY).append("\r\n");
        if (!title.isEmpty()) sb.append("TITLE:").append(title).append("\r\n");
        if (!nz(c.getEmail()).isEmpty()) sb.append("EMAIL;TYPE=WORK:").append(c.getEmail()).append("\r\n");
        sb.append("TEL;TYPE=WORK,VOICE:").append(phone).append("\r\n");
        sb.append("TEL;TYPE=WORK,FAX:").append(fax).append("\r\n");
        if (!nz(c.getMobile()).isEmpty()) sb.append("TEL;TYPE=CELL:").append(c.getMobile()).append("\r\n");
        sb.append("URL:").append(WEBSITE).append("\r\n");
        sb.append("ADR;TYPE=WORK:;;Place de l'Independance;Yaounde;;BP 11834;Cameroun\r\n");
        sb.append("END:VCARD\r\n");
        return sb.toString();
    }

    /** Returns a data: URI (base64 PNG) of a QR encoding the vCard, ready for an &lt;img src&gt;. */
    public String qrDataUri(String content, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private static String nz(String s) { return s == null ? "" : s.trim(); }
}
