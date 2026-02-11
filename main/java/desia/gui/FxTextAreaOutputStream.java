package desia.gui;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Redirects System.out/System.err to a JavaFX TextArea.
 *
 * Thread-safe: all UI updates are marshalled onto the JavaFX Application thread.
 */
public final class FxTextAreaOutputStream extends OutputStream {
    private final TextArea area;

    // Soft cap to avoid unbounded memory growth.
    private static final int MAX_CHARS = 250_000;

    public FxTextAreaOutputStream(TextArea area) {
        this.area = area;
    }

    @Override
    public void write(int b) {
        // Single-byte writes happen a lot; delegate to bulk to keep behavior consistent.
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) {
        if (len <= 0) return;
        String s = new String(b, off, len, StandardCharsets.UTF_8);

        Platform.runLater(() -> {
            area.appendText(s);

            // Trim from the start if too large.
            int over = area.getLength() - MAX_CHARS;
            if (over > 0) {
                // Delete a bit more than needed to reduce frequent trims.
                int trimTo = Math.min(area.getLength(), over + 10_000);
                area.deleteText(0, trimTo);
            }
        });
    }
}
