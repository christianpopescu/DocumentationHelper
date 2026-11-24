package com.documenthelper.rendering;

import com.documenthelper.core.ConversionRequest;
import com.documenthelper.core.ConversionRequest.PdfPageSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class DocumentGenerationServiceTest {
    @TempDir Path directory;

    @Test
    void plantUmlOptionIsAvailableOnApplicationClasspath() throws Exception {
        assertNotNull(Class.forName("net.sourceforge.plantuml.Option"));
    }

    @Test
    void generatesHtmlAndPdfWithRelativeResourcesAndEmbeddedImages() throws Exception {
        Path source = Files.createDirectories(directory.resolve("source with spaces"));
        Path images = Files.createDirectories(source.resolve("images"));
        Files.write(images.resolve("pixel.png"), Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a3foAAAAASUVORK5CYII="));
        Files.writeString(source.resolve("section.adoc"), "Included content.\n");
        Files.writeString(source.resolve("sequence.puml"), "@startuml\nAlice -> Bob: Hello\n@enduml\n");
        Path document = source.resolve("example.adoc");
        Files.writeString(document, """
                = Example document

                include::section.adoc[]

                image::images/pixel.png[]

                plantuml::sequence.puml[format=svg]
                """);
        Path output = directory.resolve("output with spaces");
        var result = new DocumentGenerationService().generate(new ConversionRequest(document, output, true, true));

        assertEquals(2, result.files().size());
        assertTrue(result.diagnostics().isEmpty(),
                () -> "Successful conversion must not report debug lookups as warnings: " + result.diagnostics());
        String html = Files.readString(output.resolve("example.html"));
        assertTrue(html.contains("Included content."));
        assertTrue(html.contains("data:image/png;base64,"));
        assertTrue(html.contains("data:image/svg+xml"));
        // The bundled CSS documents an optional Google Fonts import inside a comment.
        // Only active references would cause a network dependency.
        String htmlWithoutCssComments = html.replaceAll("(?s)/\\*.*?\\*/", "");
        assertFalse(htmlWithoutCssComments.contains("fonts.googleapis.com"),
                "HTML must not contain active Google Fonts references.");
        assertTrue(html.contains("<style>"));
        byte[] pdf = Files.readAllBytes(output.resolve("example.pdf"));
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.US_ASCII));
        try (var files = Files.list(output)) {
            assertEquals(2, files.count(), "Temporary rendering files should be removed.");
        }
        assertFalse(Files.exists(source.resolve(".asciidoctor")), "Diagram cache must stay outside sources.");
    }

    @Test
    void writesSvgTextSpacingAsPdfDecimalsForAcrobat() throws Exception {
        Files.writeString(directory.resolve("labels.svg"), """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 80">
                  <text x="5" y="20" font-family="sans-serif" font-size="14" font-weight="bold"
                        lengthAdjust="spacing" textLength="60.6758">PlantUml</text>
                  <rect x="5" y="30" width="120" height="30" fill="#eeeeee" stroke="black"/>
                  <text x="10" y="50" font-family="sans-serif" font-size="14">Visible file</text>
                </svg>
                """);
        Path document = Files.writeString(directory.resolve("labels.adoc"),
                "= SVG labels\n\nimage::labels.svg[]\n");
        Path output = directory.resolve("output");
        new DocumentGenerationService().generate(new ConversionRequest(document, output, false, true));

        // Prawn's page content streams are uncompressed by default. Inspect the actual
        // emitted operator: tolerant viewers can conceal invalid exponential numbers.
        String pdf = Files.readString(output.resolve("labels.pdf"), StandardCharsets.ISO_8859_1);
        assertTrue(Pattern.compile("(?m)^-?[0-9.]+ Tc$").matcher(pdf).find());
        assertFalse(Pattern.compile("(?m)^[-+0-9.]+[eE][-+]?[0-9]+ T[cz]$").matcher(pdf).find(),
                "PDF text spacing/scaling must not use scientific notation; Acrobat stops rendering the diagram.");
    }

    @Test
    void usesSelectedPdfPageSize() throws Exception {
        Path document = Files.writeString(directory.resolve("example.adoc"),
                "= Example\n:pdf-page-size: Letter\n\nPage size test.\n");
        var service = new DocumentGenerationService();
        var a4 = new ConversionRequest(document, directory.resolve("a4"), false, true, PdfPageSize.A4);
        var a3 = new ConversionRequest(document, directory.resolve("a3"), false, true, PdfPageSize.A3);

        service.generate(a4);
        service.generate(a3);

        String a4Pdf = Files.readString(a4.outputFile("pdf"), StandardCharsets.ISO_8859_1);
        String a3Pdf = Files.readString(a3.outputFile("pdf"), StandardCharsets.ISO_8859_1);
        assertTrue(a4Pdf.contains("/MediaBox [0 0 595.28 841.89]"));
        assertTrue(a3Pdf.contains("/MediaBox [0 0 841.89 1190.55]"));
    }

    @Test
    void rejectsMissingInputAndNoSelectedFormats() throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
                new ConversionRequest(directory.resolve("missing.adoc"), directory, true, true));
        Path input = Files.writeString(directory.resolve("example.adoc"), "= Example\n");
        assertThrows(IllegalArgumentException.class, () ->
                new ConversionRequest(input, directory, false, false));
    }
}
