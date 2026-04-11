package com.croh.reporting;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Generates local export file artifacts (CSV and PDF) and stores them on disk.
 * Returns the relative file path for retrieval.
 */
@Service
public class ExportService {

    private final String basePath;

    public ExportService(@Value("${croh.storage.base-path}") String basePath) {
        this.basePath = basePath;
    }

    /**
     * Writes CSV content to a local file and returns the relative path.
     */
    public String exportCsv(String csvContent, String filenamePrefix) {
        String relativePath = "exports/" + filenamePrefix + "-" + UUID.randomUUID() + ".csv";
        writeToFile(relativePath, csvContent.getBytes());
        return relativePath;
    }

    /**
     * Generates a PDF from a title and rows of data, writes to local file, returns relative path.
     */
    public String exportPdf(String title, List<String> headers, List<List<String>> rows, String filenamePrefix) {
        byte[] pdfBytes = generatePdf(title, headers, rows);
        String relativePath = "exports/" + filenamePrefix + "-" + UUID.randomUUID() + ".pdf";
        writeToFile(relativePath, pdfBytes);
        return relativePath;
    }

    /**
     * Reads an export file from disk and returns its bytes.
     */
    public byte[] readExportFile(String relativePath) {
        Path fullPath = Path.of(basePath).resolve(relativePath);
        try {
            return Files.readAllBytes(fullPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read export file: " + relativePath, e);
        }
    }

    /**
     * Returns the content type for a file path based on extension.
     */
    public String getContentType(String relativePath) {
        if (relativePath.endsWith(".pdf")) return "application/pdf";
        return "text/csv";
    }

    private byte[] generatePdf(String title, List<String> headers, List<List<String>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            doc.add(new Paragraph(title, titleFont));
            doc.add(new Paragraph(" "));

            // Header row
            doc.add(new Paragraph(String.join(" | ", headers), headerFont));
            doc.add(new Paragraph("---", bodyFont));

            // Data rows
            for (List<String> row : rows) {
                doc.add(new Paragraph(String.join(" | ", row), bodyFont));
            }
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    private void writeToFile(String relativePath, byte[] content) {
        Path fullPath = Path.of(basePath).resolve(relativePath);
        try {
            Files.createDirectories(fullPath.getParent());
            Files.write(fullPath, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write export file", e);
        }
    }
}
