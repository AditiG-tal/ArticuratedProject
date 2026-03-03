package com.articurated.service;

import com.articurated.jobs.Messages;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class PdfInvoiceService {

    @Value("${app.invoice.storage-path:/tmp/invoices}")
    private String storagePath;

    public String generateInvoice(Messages.InvoiceGenerationMessage message) {
        try {
            Files.createDirectories(Paths.get(storagePath));
            String fileName = String.format("invoice_%s_%s.pdf",
                    message.getOrderNumber(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            String filePath = storagePath + "/" + fileName;

            PdfWriter writer = new PdfWriter(filePath);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // Header
            document.add(new Paragraph("ArtiCurated")
                    .setFontSize(24)
                    .setBold()
                    .setFontColor(ColorConstants.DARK_GRAY)
                    .setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("INVOICE")
                    .setFontSize(18)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("\n"));

            // Order Info
            document.add(new Paragraph("Order Number: " + message.getOrderNumber()).setBold());
            document.add(new Paragraph("Customer: " + message.getCustomerName()));
            document.add(new Paragraph("Email: " + message.getCustomerEmail()));
            document.add(new Paragraph("Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))));

            document.add(new Paragraph("\n"));

            // Items Table
            Table table = new Table(UnitValue.createPercentArray(new float[]{40, 20, 20, 20}))
                    .setWidth(UnitValue.createPercentValue(100));

            // Header row
            for (String header : new String[]{"Product", "SKU", "Qty", "Unit Price"}) {
                table.addHeaderCell(new Cell()
                        .add(new Paragraph(header).setBold())
                        .setBackgroundColor(ColorConstants.LIGHT_GRAY));
            }

            // Item rows
            if (message.getItems() != null) {
                for (Messages.OrderItemDetail item : message.getItems()) {
                    table.addCell(item.getProductName());
                    table.addCell(item.getProductSku());
                    table.addCell(String.valueOf(item.getQuantity()));
                    table.addCell("$" + item.getUnitPrice());
                }
            }

            document.add(table);
            document.add(new Paragraph("\n"));

            // Total
            document.add(new Paragraph("TOTAL: $" + message.getTotalAmount())
                    .setBold()
                    .setFontSize(14)
                    .setTextAlignment(TextAlignment.RIGHT));

            document.add(new Paragraph("\nThank you for shopping with ArtiCurated!")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setItalic());

            document.close();

            log.info("Generated PDF invoice at: {}", filePath);
            return filePath;

        } catch (IOException e) {
            log.error("Failed to generate PDF invoice for order {}", message.getOrderNumber(), e);
            throw new RuntimeException("Failed to generate invoice: " + e.getMessage(), e);
        }
    }

    public void simulateSendEmail(String customerEmail, String orderNumber, String invoicePath) {
        log.info("=== [EMAIL SIMULATION] ===");
        log.info("To: {}", customerEmail);
        log.info("Subject: Your ArtiCurated Invoice for Order #{}", orderNumber);
        log.info("Attachment: {}", invoicePath);
        log.info("Body: Dear customer, please find your invoice attached. Thank you for your order!");
        log.info("=== [EMAIL SENT (simulated)] ===");
    }
}
