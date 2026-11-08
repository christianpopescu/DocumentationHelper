package com.documenthelper.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public record ConversionRequest(Path document, Path outputDirectory, boolean html, boolean pdf) {
    public ConversionRequest {
        document = Objects.requireNonNull(document).toAbsolutePath().normalize();
        outputDirectory = Objects.requireNonNull(outputDirectory).toAbsolutePath().normalize();
        if (!Files.isRegularFile(document) || !Files.isReadable(document)) {
            throw new IllegalArgumentException("Select a readable AsciiDoc document.");
        }
        String name = document.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".adoc") || name.endsWith(".asciidoc"))) {
            throw new IllegalArgumentException("The input document must have an .adoc or .asciidoc extension.");
        }
        if (!html && !pdf) {
            throw new IllegalArgumentException("Select HTML, PDF, or both.");
        }
        if (Files.exists(outputDirectory) && !Files.isDirectory(outputDirectory)) {
            throw new IllegalArgumentException("The output path must be a directory.");
        }
    }

    public Path outputFile(String extension) {
        String name = document.getFileName().toString();
        return outputDirectory.resolve(name.substring(0, name.lastIndexOf('.')) + "." + extension);
    }
}
