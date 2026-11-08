package com.documenthelper;

import com.documenthelper.gui.DocumentHelperFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class DocumentHelper {
    private DocumentHelper() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // The default Swing look and feel remains available.
            }
            new DocumentHelperFrame().setVisible(true);
        });
    }
}
