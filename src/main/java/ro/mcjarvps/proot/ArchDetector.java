package ro.mcjarvps.proot;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Detects host CPU architecture inside the Pterodactyl container.
 */
public class ArchDetector {

    public enum Architecture {
        ARM64,
        X86_64
    }

    private static final String PROOT_ARM64_URL =
            "https://github.com/proot-me/proot/releases/download/v5.3.0/proot-v5.3.0-aarch64-static";
    private static final String ALPINE_ARM64_URL =
            "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.0-aarch64.tar.gz";

    private static final String PROOT_X86_64_URL =
            "https://github.com/proot-me/proot/releases/download/v5.3.0/proot-v5.3.0-x86_64-static";
    private static final String ALPINE_X86_64_URL =
            "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/x86_64/alpine-minirootfs-3.20.0-x86_64.tar.gz";

    public static Architecture detect() {
        String osArch = System.getProperty("os.arch", "").toLowerCase();

        // Also attempt running uname -m if on Linux for absolute certainty
        String uname = getUnameArch().toLowerCase();
        String combined = osArch + " " + uname;

        if (combined.contains("aarch64") || combined.contains("arm64") || combined.contains("armv8")) {
            return Architecture.ARM64;
        }

        return Architecture.X86_64;
    }

    private static String getUnameArch() {
        try {
            Process process = new ProcessBuilder("uname", "-m").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                return line != null ? line.trim() : "";
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String getProotUrl(Architecture arch) {
        return arch == Architecture.ARM64 ? PROOT_ARM64_URL : PROOT_X86_64_URL;
    }

    public static String getAlpineUrl(Architecture arch) {
        return arch == Architecture.ARM64 ? ALPINE_ARM64_URL : ALPINE_X86_64_URL;
    }
}
