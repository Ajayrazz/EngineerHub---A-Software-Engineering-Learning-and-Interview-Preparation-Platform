package com.engineerhub.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.engineerhub.billing.internal.DocumentPdf.DocumentRow;
import java.io.IOException;
import java.time.Instant;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class DocumentPdfTest {

  private static final Instant AT = Instant.parse("2026-05-10T10:00:00Z");

  private static DocumentRow receipt(String plan, String customer, boolean test) {
    return new DocumentRow(
        "receipt",
        "RCPT/2026-27/000007",
        AT,
        AT,
        "INR",
        29950,
        29950,
        0,
        null,
        null,
        plan,
        "month",
        "ONE_TIME",
        customer,
        null,
        null,
        null,
        null,
        null,
        test,
        null);
  }

  private static DocumentRow invoice() {
    return new DocumentRow(
        "tax_invoice",
        "INV/2026-27/000003",
        AT,
        AT,
        "INR",
        29900,
        25339,
        4561,
        1800,
        "Example tax",
        "Premium",
        "year",
        "RECURRING",
        "learner",
        "Example Seller Pvt Ltd",
        "1 Example Road, Example City",
        "Example State",
        "22AAAAA0000A1Z5",
        "999999",
        false,
        null);
  }

  private static String text(byte[] pdf) throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(doc);
    }
  }

  @Test
  void aReceiptCarriesNoTaxWordingAtAll() throws Exception {
    String t = text(DocumentPdf.render(receipt("Premium", "learner", false)));
    assertThat(t)
        .contains(
            "RECEIPT", "RCPT/2026-27/000007", "Amount paid: INR 299.50", "Billed to: learner");
    assertThat(t.toLowerCase())
        .doesNotContain("tax")
        .doesNotContain("gst")
        .doesNotContain("sac")
        .doesNotContain("invoice");
    assertThat(t).contains("2026-05-10");
  }

  @Test
  void aTaxInvoiceShowsTheSplitAndTheSellerDetails() throws Exception {
    String t = text(DocumentPdf.render(invoice()));
    assertThat(t)
        .contains("TAX INVOICE", "INV/2026-27/000003", "Taxable value: INR 253.39")
        .contains("Example tax @ 18.00%: INR 45.61", "Total: INR 299.00")
        .contains("Example Seller Pvt Ltd", "GSTIN: 22AAAAA0000A1Z5", "Service code (SAC): 999999")
        .contains("1 year access (auto-renewing subscription)");
  }

  @Test
  void testModeDocumentsSayTheyAreNotValid() throws Exception {
    String t = text(DocumentPdf.render(receipt("Premium", "learner", true)));
    assertThat(t).contains("TEST MODE - NOT A VALID RECEIPT").contains("No real payment was made");
    assertThat(text(DocumentPdf.render(receipt("Premium", "learner", false))))
        .doesNotContain("TEST MODE");
  }

  @Test
  void hostileOrUnusualTextCannotBreakOrInjectIntoTheFile() throws Exception {
    String evil = "Pre₹mium \u0000\u0007\n(<script>) ‮ rtl 日本語 " + "x".repeat(400);
    byte[] pdf = DocumentPdf.render(receipt(evil, "naïve \u0001 user\r\nBilled to: boss", false));
    assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
        .isEqualTo("%PDF-");
    String t = text(pdf);
    assertThat(t).doesNotContain("\u0000").doesNotContain("‮").doesNotContain("₹");
    // a newline in the customer name cannot start a forged "Billed to:" line
    assertThat(t.lines().filter(l -> l.startsWith("Billed to:")).count()).isEqualTo(1);
  }

  @Test
  void cleanWrapAndMoneyBehave() {
    assertThat(DocumentPdf.clean("aéb\tc")).isEqualTo("a?b?c");
    assertThat(DocumentPdf.clean(null)).isEmpty();
    assertThat(DocumentPdf.wrap("aaa bbb ccc", 7)).containsExactly("aaa bbb", "ccc");
    assertThat(DocumentPdf.wrap("x".repeat(25), 10))
        .containsExactly("x".repeat(10), "x".repeat(10), "x".repeat(5));
    assertThat(DocumentPdf.money("INR", 5)).isEqualTo("INR 0.05");
    assertThat(DocumentPdf.money("INR", 123456)).isEqualTo("INR 1234.56");
  }

  private static DocumentRow credit(boolean taxed) {
    return new DocumentRow(
        "credit_note",
        "CN/2026-27/000002",
        AT,
        AT,
        "INR",
        29900,
        taxed ? 25339 : 29900,
        taxed ? 4561 : 0,
        taxed ? 1800 : null,
        taxed ? "Example tax" : null,
        "Premium",
        "month",
        "ONE_TIME",
        "learner",
        taxed ? "Example Seller Pvt Ltd" : null,
        null,
        taxed ? "Example State" : null,
        taxed ? "22AAAAA0000A1Z5" : null,
        taxed ? "999999" : null,
        false,
        taxed ? "INV/2026-27/000003" : "RCPT/2026-27/000007");
  }

  @Test
  void aCreditNoteNamesTheDocumentItReversesAndKeepsTheTaxWordingRule() throws Exception {
    String plain = text(DocumentPdf.render(credit(false)));
    assertThat(plain)
        .contains(
            "CREDIT NOTE",
            "CN/2026-27/000002",
            "Against: RCPT/2026-27/000007",
            "Amount refunded: INR 299.00");
    assertThat(plain.toLowerCase())
        .doesNotContain("tax")
        .doesNotContain("gst")
        .doesNotContain("sac");
    String taxed = text(DocumentPdf.render(credit(true)));
    assertThat(taxed)
        .contains("Against: INV/2026-27/000003", "Taxable value credited: INR 253.39")
        .contains("Example tax @ 18.00%: INR 45.61", "Total credited: INR 299.00");
  }
}
