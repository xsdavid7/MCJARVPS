package ro.mcjarvps.proot;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import ro.mcjarvps.terminal.TerminalSessionManager;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Manages PRoot environment setup, downloading Alpine RootFS and starting the VPS guest session.
 */
public class PRootManager {

    private final TerminalSessionManager terminalManager;
    private final File workDir;
    private final File systemDir;
    private final File binDir;
    private final File rootfsDir;
    private final File prootBinFile;

    private Process activeProcess;

    public PRootManager(TerminalSessionManager terminalManager) {
        this.terminalManager = terminalManager;
        this.workDir = new File(System.getProperty("user.dir", "."));
        this.systemDir = new File(workDir, "system");
        this.binDir = new File(systemDir, "bin");
        this.rootfsDir = new File(systemDir, "rootfs");
        this.prootBinFile = new File(binDir, "proot");
    }

    public void initializeAndStart(boolean consoleVpsEnabled) throws Exception {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        if (isWindows) {
            System.out.println("[VPS] Detected Windows host (development/testing mode).");
            startWindowsSession(consoleVpsEnabled);
            return;
        }

        // On Linux / Docker container in Pterodactyl
        ArchDetector.Architecture arch = ArchDetector.detect();
        System.out.println("=================================================");
        System.out.println("[VPS] Host Architecture detected: " + arch);
        System.out.println("=================================================");

        prepareEnvironment(arch);
        startLinuxProotSession(consoleVpsEnabled);
    }

    private void prepareEnvironment(ArchDetector.Architecture arch) throws Exception {
        if (!systemDir.exists()) systemDir.mkdirs();
        if (!binDir.exists()) binDir.mkdirs();
        if (!rootfsDir.exists()) rootfsDir.mkdirs();

        // 1. Check or download PRoot binary
        if (!prootBinFile.exists() || prootBinFile.length() == 0) {
            String prootUrl = ArchDetector.getProotUrl(arch);
            System.out.println("[VPS] Downloading PRoot binary for " + arch + " from: " + prootUrl);
            downloadFile(prootUrl, prootBinFile);
            makeExecutable(prootBinFile);
            System.out.println("[VPS] PRoot binary ready.");
        } else {
            makeExecutable(prootBinFile);
        }

        // 2. Check or download Alpine RootFS
        File guestSh = new File(rootfsDir, "bin/sh");
        if (!guestSh.exists()) {
            String alpineUrl = ArchDetector.getAlpineUrl(arch);
            File tarFile = new File(systemDir, "alpine-rootfs.tar.gz");
            System.out.println("[VPS] Downloading Alpine RootFS for " + arch + " from: " + alpineUrl);
            downloadFile(alpineUrl, tarFile);

            System.out.println("[VPS] Extracting RootFS...");
            extractTarGz(tarFile, rootfsDir);
            System.out.println("[VPS] RootFS extracted successfully.");
            tarFile.delete();
        }

        // 3. DNS configuration inside RootFS
        File etcDir = new File(rootfsDir, "etc");
        if (!etcDir.exists()) etcDir.mkdirs();
        File resolvConf = new File(etcDir, "resolv.conf");
        Files.writeString(resolvConf.toPath(), "nameserver 1.1.1.1\nnameserver 8.8.8.8\n", StandardCharsets.UTF_8);

        // 4. Create or update guest entrypoint.sh
        File entrypointFile = new File(rootfsDir, "entrypoint.sh");
        String entrypointContent = """
                #!/bin/sh
                set -e
                
                # DNS check
                if [ ! -f /etc/resolv.conf ] || [ ! -s /etc/resolv.conf ]; then
                    echo "nameserver 1.1.1.1" > /etc/resolv.conf
                    echo "nameserver 8.8.8.8" >> /etc/resolv.conf
                fi
                
                # First boot package installation
                if [ ! -f /installed.flag ]; then
                    echo "[*] Initializing Alpine packages (apk update & add bash nano curl)..."
                    apk update
                    apk add --no-cache bash curl ca-certificates nano htop procps
                    echo "[*] Base system initialized successfully."
                    touch /installed.flag
                fi
                
                echo ""
                echo "=========================================="
                echo "    ALPINE LINUX VPS ENVIRONMENT READY!   "
                echo "    Architecture: $(uname -m)             "
                echo "    Package Manager: apk                  "
                echo "=========================================="
                echo ""
                
                export TERM=xterm-256color
                export HOME=/root
                export USER=root
                export PS1='\\u@alpine:\\w\\$ '
                
                # Configure terminal line discipline for proper CRLF newlines
                if command -v stty >/dev/null 2>&1; then
                    stty sane onlcr icrnl 2>/dev/null || true
                fi
                
                if command -v bash >/dev/null 2>&1; then
                    exec /bin/bash -i
                else
                    exec /bin/sh -i
                fi
                """;
        Files.writeString(entrypointFile.toPath(), entrypointContent, StandardCharsets.UTF_8);
        makeExecutable(entrypointFile);
    }

    private void startLinuxProotSession(boolean consoleVpsEnabled) throws Exception {
        System.out.println("[VPS] Booting PRoot Alpine guest session...");

        ProcessBuilder pb = new ProcessBuilder(
                prootBinFile.getAbsolutePath(),
                "-r", rootfsDir.getAbsolutePath(),
                "-0",
                "-b", "/dev",
                "-b", "/sys",
                "-b", "/proc",
                "-b", workDir.getAbsolutePath() + ":/workspace",
                "-w", "/root",
                "/entrypoint.sh"
        );

        pb.environment().put("TERM", "xterm-256color");
        pb.environment().put("HOME", "/root");
        pb.environment().put("USER", "root");
        pb.environment().put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");

        pb.redirectErrorStream(true);

        this.activeProcess = pb.start();
        terminalManager.setProcessStreams(activeProcess.getInputStream(), activeProcess.getOutputStream(), consoleVpsEnabled);
        System.out.println("[VPS] PRoot session running successfully.");
    }

    private void startWindowsSession(boolean consoleVpsEnabled) throws Exception {
        System.out.println("[VPS] Starting Windows local terminal session for testing...");
        ProcessBuilder pb = new ProcessBuilder("cmd.exe");
        pb.redirectErrorStream(true);
        this.activeProcess = pb.start();
        terminalManager.setProcessStreams(activeProcess.getInputStream(), activeProcess.getOutputStream(), consoleVpsEnabled);
        terminalManager.broadcastOutput("\r\n[MCJARVPS] Windows Dev Shell Started. (Deploy to Pterodactyl Linux container to run Alpine PRoot)\r\n");
    }

    private void makeExecutable(File file) {
        file.setExecutable(true, false);
        file.setReadable(true, false);
        try {
            new ProcessBuilder("chmod", "+x", file.getAbsolutePath()).start().waitFor();
        } catch (Exception ignored) {
        }
    }

    private void downloadFile(String sourceUrl, File destination) throws Exception {
        URL url = URI.create(sourceUrl).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 MCJARVPS");
        connection.setInstanceFollowRedirects(true);

        int status = connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM
                || status == 307 || status == 308) {
            String newUrl = connection.getHeaderField("Location");
            downloadFile(newUrl, destination);
            return;
        }

        if (status != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP download failed: " + status + " on " + sourceUrl);
        }

        long contentLength = connection.getContentLengthLong();
        try (InputStream in = connection.getInputStream();
             OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            long totalRead = 0;
            long lastPrint = System.currentTimeMillis();

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalRead += read;
                if (System.currentTimeMillis() - lastPrint > 1000) {
                    lastPrint = System.currentTimeMillis();
                    if (contentLength > 0) {
                        int percent = (int) ((totalRead * 100) / contentLength);
                        System.out.printf("[VPS] Download progress: %d%% (%d MB / %d MB)\n",
                                percent, totalRead / (1024 * 1024), contentLength / (1024 * 1024));
                    } else {
                        System.out.printf("[VPS] Downloaded %d MB...\n", totalRead / (1024 * 1024));
                    }
                }
            }
        }
    }

    private void extractTarGz(File tarGzFile, File targetDir) throws Exception {
        // First try system tar if available
        try {
            Process process = new ProcessBuilder("tar", "-xzf", tarGzFile.getAbsolutePath(), "-C", targetDir.getAbsolutePath()).start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return;
            }
        } catch (Exception ignored) {
        }

        // Fallback to pure Java extraction
        try (InputStream fi = new FileInputStream(tarGzFile);
             InputStream bi = new BufferedInputStream(fi);
             InputStream gzi = new GzipCompressorInputStream(bi);
             TarArchiveInputStream tarInput = new TarArchiveInputStream(gzi)) {

            TarArchiveEntry entry;
            while ((entry = tarInput.getNextEntry()) != null) {
                File outputFile = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    if (!outputFile.exists()) {
                        outputFile.mkdirs();
                    }
                } else if (entry.isSymbolicLink()) {
                    try {
                        Path link = outputFile.toPath();
                        Path target = Paths.get(entry.getLinkName());
                        Files.createDirectories(link.getParent());
                        Files.deleteIfExists(link);
                        Files.createSymbolicLink(link, target);
                    } catch (Exception ignored) {
                    }
                } else {
                    File parent = outputFile.getParentFile();
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = tarInput.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                        }
                    }
                    if ((entry.getMode() & 0111) != 0) {
                        outputFile.setExecutable(true, false);
                    }
                }
            }
        }
    }

    public Process getActiveProcess() {
        return activeProcess;
    }
}
