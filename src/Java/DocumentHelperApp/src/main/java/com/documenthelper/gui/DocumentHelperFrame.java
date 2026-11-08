package com.documenthelper.gui;

import com.documenthelper.core.ConversionRequest;
import com.documenthelper.rendering.DocumentGenerationService;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public final class DocumentHelperFrame extends JFrame {
    private final JTextField document = new JTextField(42);
    private final JTextField output = new JTextField(42);
    private final JCheckBox html = new JCheckBox("HTML", true);
    private final JCheckBox pdf = new JCheckBox("PDF", true);
    private final JButton generate = new JButton("Generate Document");
    private final JLabel status = new JLabel("Select an AsciiDoc document and an output folder.");
    private final List<JComponent> inputs = new ArrayList<>();

    public DocumentHelperFrame() {
        super("DocumentHelper");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        addPathRow(form, 0, "Document path:", document, this::chooseDocument);
        addPathRow(form, 1, "Output path:", output, this::chooseOutput);
        JPanel formats = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        formats.add(html);
        formats.add(Box.createHorizontalStrut(18));
        formats.add(pdf);
        GridBagConstraints c = constraints(1, 2);
        form.add(formats, c);
        c = constraints(1, 3);
        c.insets = new Insets(15, 6, 10, 6);
        form.add(generate, c);
        c = constraints(0, 4);
        c.gridwidth = 3;
        form.add(status, c);
        inputs.add(html);
        inputs.add(pdf);
        inputs.add(generate);
        generate.addActionListener(event -> generate());
        getRootPane().setDefaultButton(generate);
        setContentPane(form);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
    }

    private static GridBagConstraints constraints(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(6, 6, 6, 6);
        return c;
    }

    private void addPathRow(JPanel form, int row, String label, JTextField field, Runnable browse) {
        JLabel pathLabel = new JLabel(label);
        pathLabel.setLabelFor(field);
        form.add(pathLabel, constraints(0, row));
        GridBagConstraints c = constraints(1, row);
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
        JButton button = new JButton("Browse...");
        button.addActionListener(event -> browse.run());
        form.add(button, constraints(2, row));
        inputs.add(field);
        inputs.add(button);
    }

    private void chooseDocument() {
        JFileChooser chooser = new JFileChooser();
        setInitialDirectory(chooser, document.getText());
        chooser.setDialogTitle("Select input document");
        chooser.setFileFilter(new FileNameExtensionFilter("AsciiDoc documents (*.adoc, *.asciidoc)", "adoc", "asciidoc"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path selected = chooser.getSelectedFile().toPath().toAbsolutePath();
            document.setText(selected.toString());
            if (output.getText().isBlank()) {
                output.setText(selected.getParent().resolve("output").toString());
            }
        }
    }

    private void chooseOutput() {
        JFileChooser chooser = new JFileChooser();
        setInitialDirectory(chooser, output.getText());
        chooser.setDialogTitle("Select output folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            output.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private static void setInitialDirectory(JFileChooser chooser, String currentPath) {
        if (currentPath.isBlank()) return;
        try {
            Path directory = Path.of(currentPath.strip()).toAbsolutePath().normalize();
            // For an input file or a not-yet-created output folder, use its nearest existing parent.
            while (directory != null && !Files.isDirectory(directory)) {
                directory = directory.getParent();
            }
            if (directory != null) chooser.setCurrentDirectory(directory.toFile());
        } catch (java.nio.file.InvalidPathException ignored) {
            // A partially typed path should not prevent browsing from the default folder.
        }
    }

    private void generate() {
        final ConversionRequest request;
        try {
            if (document.getText().isBlank() || output.getText().isBlank()) {
                throw new IllegalArgumentException("Select both an input document and an output folder.");
            }
            request = new ConversionRequest(Path.of(document.getText().strip()),
                    Path.of(output.getText().strip()), html.isSelected(), pdf.isSelected());
        } catch (RuntimeException failure) {
            JOptionPane.showMessageDialog(this, failure.getMessage(), "Invalid selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<Path> existing = new ArrayList<>();
        if (request.html() && Files.exists(request.outputFile("html"))) existing.add(request.outputFile("html"));
        if (request.pdf() && Files.exists(request.outputFile("pdf"))) existing.add(request.outputFile("pdf"));
        if (!existing.isEmpty() && JOptionPane.showConfirmDialog(this,
                "Replace existing output files?\n" + String.join("\n", existing.stream().map(Path::toString).toList()),
                "Replace output", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

        inputs.forEach(input -> input.setEnabled(false));
        status.setText("Generating document...");
        new SwingWorker<DocumentGenerationService.Result, Void>() {
            @Override
            protected DocumentGenerationService.Result doInBackground() throws Exception {
                return new DocumentGenerationService().generate(request);
            }

            @Override
            protected void done() {
                inputs.forEach(input -> input.setEnabled(true));
                try {
                    var result = get();
                    status.setText(result.diagnostics().isEmpty() ? "Document generated." : "Document generated with diagnostics.");
                    showDetails("Generation result", String.join("\n", result.files().stream().map(Path::toString).toList())
                            + (result.diagnostics().isEmpty() ? "" : "\n\n" + String.join("\n", result.diagnostics())),
                            result.diagnostics().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    status.setText("Generation interrupted.");
                } catch (ExecutionException failure) {
                    status.setText("Generation failed. Check the output folder for any completed format.");
                    Throwable cause = failure.getCause();
                    showDetails("Generation failed", cause.getMessage() == null ? cause.toString() : cause.getMessage(),
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showDetails(String title, String message, int type) {
        JTextArea details = new JTextArea(message, 12, 65);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new JScrollPane(details), title, type);
    }
}
