package ro.mcjarvps.console;

import ro.mcjarvps.terminal.TerminalSessionManager;

import java.io.IOException;

/**
 * Handles bridging the Pterodactyl host server console (System.in) to the VPS terminal.
 */
public class ConsoleVPSManager {

    private final TerminalSessionManager terminalManager;
    private final boolean enabled;
    private Thread consoleInputThread;

    public ConsoleVPSManager(TerminalSessionManager terminalManager, boolean enabled) {
        this.terminalManager = terminalManager;
        this.enabled = enabled;
    }

    public void start() {
        if (!enabled) {
            System.out.println("[CONSOLE] Server Console VPS is DISABLED (console will not send commands to shell).");
            return;
        }

        System.out.println("[CONSOLE] Server Console VPS is ENABLED (you can type commands directly in this console).");

        consoleInputThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            try {
                int read;
                while (terminalManager.isRunning() && (read = System.in.read(buffer)) != -1) {
                    byte[] copy = new byte[read];
                    System.arraycopy(buffer, 0, copy, 0, read);
                    terminalManager.writeInput(copy);
                }
            } catch (IOException ignored) {
            }
        }, "Console-VPS-Input");
        consoleInputThread.setDaemon(true);
        consoleInputThread.start();
    }
}
