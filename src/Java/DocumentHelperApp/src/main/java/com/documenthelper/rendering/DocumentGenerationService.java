package com.documenthelper.rendering;

import com.documenthelper.core.ConversionRequest;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DocumentGenerationService {
    public record Result(List<Path> files, List<String> diagnostics) {
        public Result {
            files = List.copyOf(files);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    /** Processes trusted local documents; document includes and diagram execution are enabled. */
    public Result generate(ConversionRequest request) throws IOException {
        Files.createDirectories(request.outputDirectory());
        Path work = Files.createTempDirectory(request.outputDirectory(), ".documenthelper-");
        List<Path> generated = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        try (Asciidoctor asciidoctor = Asciidoctor.Factory.create()) {
            asciidoctor.registerLogHandler(record -> diagnostics.add(record.getSeverity() + ": " + record.getMessage()));
            asciidoctor.requireLibrary("asciidoctor-diagram");
            if (request.pdf()) {
                asciidoctor.requireLibrary("asciidoctor-pdf");
            }
            if (request.html()) {
                convert(asciidoctor, request, work, "html5", "html", diagnostics);
                generated.add(request.outputFile("html"));
            }
            if (request.pdf()) {
                convert(asciidoctor, request, work, "pdf", "pdf", diagnostics);
                generated.add(request.outputFile("pdf"));
            }
            return new Result(generated, diagnostics);
        } finally {
            try (var paths = Files.walk(work)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            } catch (IOException cleanupFailure) {
                // Retain the original conversion result/error if temporary files are locked.
                work.toFile().deleteOnExit();
            }
        }
    }

    private void convert(Asciidoctor asciidoctor, ConversionRequest request, Path work,
                         String backend, String extension, List<String> diagnostics) throws IOException {
        var attributes = Attributes.builder()
                .attribute("imagesoutdir", work.toString())
                .attribute("outdir", work.toString())
                .attribute("diagram-cachedir", work.resolve("cache").toString())
                .attribute("webfonts", false)
                .attribute("icons", false);
        if (backend.equals("html5")) {
            attributes.attribute("data-uri", "");
            attributes.attribute("linkcss", false);
        }
        Path staged = work.resolve("document." + extension);
        int diagnosticStart = diagnostics.size();
        asciidoctor.convertFile(request.document().toFile(), Options.builder()
                .backend(backend)
                .safe(SafeMode.UNSAFE)
                .baseDir(request.document().getParent().toFile())
                .toFile(staged.toFile())
                .attributes(attributes.build())
                .build());
        boolean errors = diagnostics.subList(diagnosticStart, diagnostics.size()).stream()
                .anyMatch(message -> message.startsWith("ERROR:") || message.startsWith("FATAL:"));
        if (errors || !Files.isRegularFile(staged)) {
            throw new IOException("Failed to generate " + extension.toUpperCase() + ".\n"
                    + String.join("\n", diagnostics));
        }
        Files.move(staged, request.outputFile(extension), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
