package com.hms.finance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionPdfService {

    private final MinioService minioService;

    private static final int PRESIGNED_URL_TTL_MINUTES = 15;

    /**
     * Generates a simple text-based prescription PDF, uploads to MinIO,
     * and returns a pre-signed download URL.
     *
     * In a production system this would call an external Clinical Service
     * to fetch prescription data. For now we build a stub PDF.
     */
    public Map<String, Object> generateAndUpload(UUID sessionId, String doctorName, String hospitalName) {
        log.info("Generating prescription PDF for session: {}", sessionId);

        String objectName = "rx-" + sessionId + ".pdf";
        byte[] pdfBytes = buildStubPdf(sessionId, doctorName, hospitalName);

        minioService.uploadPdf(objectName, pdfBytes);

        String pdfUrl = minioService.generatePresignedUrl(objectName, PRESIGNED_URL_TTL_MINUTES);
        ZonedDateTime expiresAt = ZonedDateTime.now().plusMinutes(PRESIGNED_URL_TTL_MINUTES);

        log.info("Prescription PDF ready for session {}. URL expires at {}", sessionId, expiresAt);

        return Map.of(
                "pdf_url", pdfUrl,
                "expires_at", expiresAt
        );
    }

    private byte[] buildStubPdf(UUID sessionId, String doctorName, String hospitalName) {
        // Minimal PDF structure (plain text placeholder)
        // In production, use iText or Apache PDFBox with real prescription data
        String content = """
                %%PDF-1.4
                1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
                2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
                3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792]
                /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
                endobj
                4 0 obj << /Length 200 >>
                stream
                BT /F1 12 Tf 72 720 Td
                (%s) Tj 0 -20 Td
                (Doctor: %s) Tj 0 -20 Td
                (Session ID: %s) Tj 0 -20 Td
                (Generated: %s) Tj
                ET
                endstream endobj
                5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj
                xref
                0 6
                trailer << /Size 6 /Root 1 0 R >>
                startxref 0
                %%%%EOF
                """.formatted(
                hospitalName != null ? hospitalName : "Narammala Channeling Hospital",
                doctorName != null ? doctorName : "N/A",
                sessionId.toString(),
                ZonedDateTime.now().toString()
        );
        return content.getBytes();
    }
}
