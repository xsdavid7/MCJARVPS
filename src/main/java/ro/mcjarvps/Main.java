package ro.mcjarvps;

import ro.mcjarvps.config.AppConfig;
import ro.mcjarvps.console.ConsoleVPSManager;
import ro.mcjarvps.proot.ArchDetector;
import ro.mcjarvps.proot.PRootManager;
import ro.mcjarvps.ssh.SSHServer;
import ro.mcjarvps.terminal.TerminalSessionManager;
import ro.mcjarvps.web.WebSSHServer;

import java.io.File;

/**
 * Main entry point for MCJARVPS (Java 21 Linux VPS in Pterodactyl container).
 * Author: xs7david
 */
public class Main {

    public static void main(String[] args) {
        printBanner();

        // 1. Load or generate setup.yml / config.yml
        File configFile = AppConfig.findConfigFile();
        AppConfig config = AppConfig.loadOrCreate(configFile);

        ArchDetector.Architecture arch = ArchDetector.detect();

        printConfigSummary(config, arch, configFile.getName());

        // 2. Terminal multiplexer
        TerminalSessionManager terminalManager = new TerminalSessionManager();

        // 3. Initialize and boot PRoot Alpine environment
        PRootManager prootManager = new PRootManager(terminalManager);
        try {
            prootManager.initializeAndStart(config.isEnableConsoleVps());
        } catch (Exception e) {
            System.err.println("[FATAL] Failed to initialize PRoot environment: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // 4. Start Console VPS Manager (Pterodactyl Console interaction)
        ConsoleVPSManager consoleManager = new ConsoleVPSManager(terminalManager, config.isEnableConsoleVps());
        consoleManager.start();

        // 5. Start Control Service (WebSSH or real SSH) on single configured port
        String mode = config.getMode().toLowerCase();
        WebSSHServer webSSHServer = null;
        SSHServer sshServer = null;

        try {
            if ("ssh".equals(mode)) {
                sshServer = new SSHServer(config, terminalManager);
                sshServer.start();
            } else {
                // Default to WebSSH
                webSSHServer = new WebSSHServer(config, terminalManager);
                webSSHServer.start();
            }
        } catch (Exception e) {
            System.err.println("[FATAL] Failed to start control service (" + mode + "): " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Shutdown hook
        final WebSSHServer finalWeb = webSSHServer;
        final SSHServer finalSsh = sshServer;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\r\n[MCJARVPS] Shutting down services...");
            if (finalWeb != null) finalWeb.stop();
            if (finalSsh != null) finalSsh.stop();
            Process p = prootManager.getActiveProcess();
            if (p != null && p.isAlive()) {
                p.destroy();
            }
        }));

        System.out.println("[MCJARVPS] VPS System is ready and operational.");

        // Keep main thread alive
        try {
            Process p = prootManager.getActiveProcess();
            if (p != null) {
                p.waitFor();
            } else {
                Thread.currentThread().join();
            }
        } catch (InterruptedException ignored) {
        }
    }

    private static void printBanner() {
        System.out.println("""
                ===============================================================
                   __  __  ____     _   _    ____ __     ______  ____  
                  |  \\/  |/ ___|   | | / \\  |  _ \\\\ \\   / /  _ \\/ ___| 
                  | |\\/| | |   ____| |/ _ \\ | |_) |\\ \\ / /| |_) \\___ \\ 
                  | |  | | |__|____| / ___ \\|  _ <  \\ V / |  __/ ___) |
                  |_|  |_|\\____|   |_/_/   \\_\\_| \\_\\  \\_/  |_|   |____/  
                         Java 21 Pterodactyl Linux VPS | Author: xs7david
                ===============================================================
                """);
    }

    private static void printConfigSummary(AppConfig config, ArchDetector.Architecture arch, String configFileName) {
        System.out.println("---------------------------------------------------------------");
        System.out.println(" Config File       : " + configFileName);
        System.out.println(" Host Architecture : " + arch);
        System.out.println(" Control Mode      : " + config.getMode().toUpperCase());
        System.out.println(" Server Port       : " + config.getPort());
        System.out.println(" Console VPS       : " + (config.isEnableConsoleVps() ? "ENABLED (Interactive)" : "DISABLED"));
        System.out.println(" Login Username    : " + config.getUsername());
        System.out.println(" Login Password    : " + config.getPassword());
        System.out.println("---------------------------------------------------------------");
    }
}
