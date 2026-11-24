package com.engineerhub.billing.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Draws a receipt or tax invoice from an immutable {@link DocumentRow} snapshot. Only text we draw
 * ourselves goes into the file (no HTML, no templates, no images, no links), and every string is
 * reduced to printable ASCII first so user-chosen text can neither break the encoding nor smuggle
 * in control characters. A RECEIPT never contains any tax wording (docs/21 §5).
 */
final class DocumentPdf {

  static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(IST);

  private DocumentPdf() {}

  record DocumentRow(
      String docType,
      String number,
      Instant issuedAt,
      Instant paidAt,
      String currency,
      long totalMinor,
      long netMinor,
      long taxMinor,
      Integer taxRateBps,
      String taxRuleName,
      String planName,
      String periodUnit,
      String billingMode,
      String customerName,
      String sellerLegalName,
      String sellerAddress,
      String sellerState,
      String sellerGstin,
      String sacCode,
      boolean testMode,
      String againstNumber) {}

  /** Printable ASCII only; everything else becomes '?'. The built-in fonts cannot draw more. */
  static String clean(String s) {
    if (s == null) {
      return "";
    }
    StringBuilder b = new StringBuilder(s.length());
    for (char c : s.toCharArray()) {
      b.append(c >= 0x20 && c <= 0x7E ? c : '?');
    }
    return b.toString().strip();
  }

  static String money(String currency, long minor) {
    return "%s %d.%02d".formatted(clean(currency), minor / 100, minor % 100);
  }

  static List<String> wrap(String text, int max) {
    List<String> out = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    for (String word : clean(text).split("\\s+")) {
      if (word.isEmpty()) {
        continue;
      }
      if (line.length() > 0 && line.length() + 1 + word.length() > max) {
        out.add(line.toString());
        line.setLength(0);
      }
      while (word.length() > max) { // a single very long word
        out.add(word.substring(0, max));
        word = word.substring(max);
      }
      if (line.length() > 0) {
        line.append(' ');
      }
      line.append(word);
    }
    if (line.length() > 0) {
      out.add(line.toString());
    }
    return out;
  }

  /** The text lines of the document, in order (also what the tests read back). */
  static List<String> lines(DocumentRow d) {
    boolean credit = d.docType().equals("credit_note");
    boolean taxed = d.taxRateBps() != null; // tax invoice, or a credit note against one
    String title = credit ? "CREDIT NOTE" : taxed ? "TAX INVOICE" : "RECEIPT";
    List<String> l = new ArrayList<>();
    l.add(title);
    if (d.testMode()) {
      l.add("TEST MODE - NOT A VALID " + title + ". No real payment was made.");
    }
    l.add("");
    l.add("Number: " + clean(d.number()));
    if (credit) {
      l.add("Against: " + clean(d.againstNumber()));
    }
    l.add("Issued: " + DATE.format(d.issuedAt()));
    l.add("Payment date: " + DATE.format(d.paidAt()));
    l.add("");
    l.add("From:");
    l.add(
        d.sellerLegalName() != null && !d.sellerLegalName().isBlank()
            ? clean(d.sellerLegalName())
            : "EngineerHub");
    if (d.sellerAddress() != null && !d.sellerAddress().isBlank()) {
      l.addAll(wrap(d.sellerAddress(), 80));
    }
    if (taxed) {
      l.add("State: " + clean(d.sellerState()));
      l.add("GSTIN: " + clean(d.sellerGstin()));
    }
    l.add("");
    l.add("Billed to: " + clean(d.customerName()));
    l.add("");
    String period = "year".equals(d.periodUnit()) ? "1 year" : "1 month";
    String kind =
        "RECURRING".equals(d.billingMode()) ? "auto-renewing subscription" : "one-time pass";
    l.addAll(wrap("%s - %s access (%s)".formatted(d.planName(), period, kind), 80));
    if (taxed) {
      l.add("Service code (SAC): " + clean(d.sacCode()));
    }
    l.add("");
    if (taxed) {
      l.add(
          (credit ? "Taxable value credited: " : "Taxable value: ")
              + money(d.currency(), d.netMinor()));
      l.add(
          "%s @ %d.%02d%%: %s"
              .formatted(
                  clean(d.taxRuleName()),
                  d.taxRateBps() / 100,
                  d.taxRateBps() % 100,
                  money(d.currency(), d.taxMinor())));
      l.add((credit ? "Total credited: " : "Total: ") + money(d.currency(), d.totalMinor()));
    } else {
      l.add((credit ? "Amount refunded: " : "Amount paid: ") + money(d.currency(), d.totalMinor()));
    }
    return l;
  }

  static byte[] render(DocumentRow d) {
    try (PDDocument doc = new PDDocument()) {
      PDDocumentInformation info = new PDDocumentInformation();
      info.setTitle(clean(d.number()));
      info.setProducer("EngineerHub");
      doc.setDocumentInformation(info);
      PDPage page = new PDPage(PDRectangle.A4);
      doc.addPage(page);
      PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        float y = 790;
        boolean first = true;
        for (String line : lines(d)) {
          cs.beginText();
          cs.setFont(first ? bold : regular, first ? 20 : 11);
          cs.newLineAtOffset(50, y);
          cs.showText(line);
          cs.endText();
          y -= first ? 32 : 17;
          first = false;
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
