package ro.mcjarvps.terminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/**
 * Manages multiplexing terminal I/O between the PRoot shell process,
 * WebSSH clients, SSH clients, and the Pterodactyl console.
 * Author: xs7david
 */
public class TerminalSessionManager {

    private final Object lock = new Object();
    private OutputStream processInput;
    private final StringBuilder historyBuffer = new StringBuilder();
    private static final int MAX_BUFFER_CHARS = 50_000;

    private final Set<Consumer<String>> outputListeners = new CopyOnWriteArraySet<>();
    private boolean consoleVpsEnabled = true;
    private volatile boolean running = false;

    public void setProcessStreams(InputStream processOutput, OutputStream processInput, boolean consoleVps) {
        this.processInput = processInput;
        this.consoleVpsEnabled = consoleVps;
        this.running = true;

        // Background reader thread for process stdout/stderr
        Thread readerThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            try {
                int bytesRead;
                while (running && (bytesRead = processOutput.read(buffer)) != -1) {
                    String rawText = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                    // Fix staircase effect: ensure all \n are preceded by \r (CRLF line discipline)
                    String normalized = normalizeNewlines(rawText);
                    broadcastOutput(normalized);
                }
            } catch (IOException e) {
                if (running) {
                    broadcastOutput("\r\n[!] Terminal process stream ended: " + e.getMessage() + "\r\n");
                }
            } finally {
                running = false;
                broadcastOutput("\r\n[!] Terminal session closed.\r\n");
            }
        }, "Terminal-Output-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Converts any standalone LF (\n) to CRLF (\r\n) to eliminate the terminal staircase effect in PuTTY and WebSSH.
     */
    public static String normalizeNewlines(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.replace("\r\n", "\n").replace("\n", "\r\n");
    }

    public void broadcastOutput(String text) {
        synchronized (lock) {
            historyBuffer.append(text);
            if (historyBuffer.length() > MAX_BUFFER_CHARS) {
                historyBuffer.delete(0, historyBuffer.length() - MAX_BUFFER_CHARS);
            }
        }

        // Pipe to Pterodactyl server console if enabled
        if (consoleVpsEnabled) {
            System.out.print(text);
            System.out.flush();
        }

        // Broadcast to WebSSH and SSH listeners
        for (Consumer<String> listener : outputListeners) {
            try {
                listener.accept(text);
            } catch (Exception ignored) {
            }
        }
    }

    public void writeInput(byte[] data) {
        if (processInput == null || data == null) return;
        try {
            // Normalize CR (\r) to LF (\n) for processes reading from piped stdin
            byte[] normalized = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                if (data[i] == '\r') {
                    normalized[i] = '\n';
                } else {
                    normalized[i] = data[i];
                }
            }
            processInput.write(normalized);
            processInput.flush();
        } catch (IOException e) {
            System.err.println("[TERMINAL] Error writing to process stdin: " + e.getMessage());
        }
    }

    public void writeInput(String text) {
        if (text == null) return;
        writeInput(text.getBytes(StandardCharsets.UTF_8));
    }

    public void addOutputListener(Consumer<String> listener) {
        outputListeners.add(listener);
        // Send initial buffer history
        synchronized (lock) {
            if (!historyBuffer.isEmpty()) {
                try {
                    listener.accept(historyBuffer.toString());
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void removeOutputListener(Consumer<String> listener) {
        outputListeners.remove(listener);
    }

    public String getBufferHistory() {
        synchronized (lock) {
            return historyBuffer.toString();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void setConsoleVpsEnabled(boolean enabled) {
        this.consoleVpsEnabled = enabled;
    }
}
