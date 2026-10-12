package ro.mcjarvps.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages configuration loaded from and saved to setup.yml / config.yml.
 * Author: xs7david
 */
public class AppConfig {

    private String username = "admin";
    private String password = "";
    private String mode = "webssh"; // "webssh" or "ssh"
    private int port = 25565; // Single port used for both webssh and ssh modes
    private boolean enableConsoleVps = true;

    public AppConfig() {
    }

    public static File findConfigFile() {
        File setupFile = new File("setup.yml");
        File configFile = new File("config.yml");
        if (configFile.exists()) {
            return configFile;
        }
        return setupFile;
    }

    public static AppConfig loadOrCreate(File configFile) {
        AppConfig config = new AppConfig();

        if (!configFile.exists()) {
            config.password = generateRandomPassword(12);
            config.save(configFile);
            System.out.println("[CONFIG] Generated new configuration file: " + configFile.getAbsolutePath());
            return config;
        }

        try (InputStream input = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(input);

            if (data != null) {
                // auth section
                Object authObj = data.get("auth");
                if (authObj instanceof Map<?, ?> authMap) {
                    if (authMap.containsKey("username")) {
                        config.username = String.valueOf(authMap.get("username")).trim();
                    }
                    if (authMap.containsKey("password")) {
                        config.password = String.valueOf(authMap.get("password")).trim();
                    }
                }

                // control section
                Object controlObj = data.get("control");
                if (controlObj instanceof Map<?, ?> controlMap) {
                    if (controlMap.containsKey("mode")) {
                        config.mode = String.valueOf(controlMap.get("mode")).trim().toLowerCase();
                    }
                    // Support single port configuration
                    if (controlMap.containsKey("port")) {
                        config.port = Integer.parseInt(String.valueOf(controlMap.get("port")));
                    } else if ("ssh".equalsIgnoreCase(config.mode) && controlMap.containsKey("ssh_port")) {
                        config.port = Integer.parseInt(String.valueOf(controlMap.get("ssh_port")));
                    } else if (controlMap.containsKey("web_port")) {
                        config.port = Integer.parseInt(String.valueOf(controlMap.get("web_port")));
                    }
                }

                // console section
                Object consoleObj = data.get("console");
                if (consoleObj instanceof Map<?, ?> consoleMap) {
                    if (consoleMap.containsKey("enable_console_vps")) {
                        config.enableConsoleVps = Boolean.parseBoolean(String.valueOf(consoleMap.get("enable_console_vps")));
                    }
                }
            }

            if (config.password.isEmpty()) {
                config.password = generateRandomPassword(12);
                config.save(configFile);
            }

        } catch (Exception e) {
            System.err.println("[CONFIG] Error reading " + configFile.getName() + ": " + e.getMessage());
            if (config.password.isEmpty()) {
                config.password = generateRandomPassword(12);
            }
        }

        return config;
    }

    public void save(File configFile) {
        try (FileWriter writer = new FileWriter(configFile)) {
            String yamlContent = String.format("""
                    # ==============================================================================
                    #  MCJARVPS - Pterodactyl Java 21 Linux VPS Configuration
                    #  Author: xs7david
                    # ==============================================================================
                    
                    # ------------------------------------------------------------------------------
                    # [AUTH] Access Credentials
                    # Used to authenticate when connecting via WebSSH or native SSH.
                    # ------------------------------------------------------------------------------
                    auth:
                      username: %s
                      password: %s
                    
                    # ------------------------------------------------------------------------------
                    # [CONTROL] Access Modes & Single Port Configuration
                    #
                    # Available modes:
                    #   - 'webssh' : Web-based interactive terminal with login screen.
                    #                Access via your browser at http://<server-ip>:%d/
                    #                Features a clean login page and a fullscreen, black PuTTY-style
                    #                xterm console. Supports Ctrl+C, Ctrl+V, F1-F12 and all keys.
                    #
                    #   - 'ssh'    : Real native SSH server powered by Apache MINA SSHD.
                    #                Connect using standard tools like PuTTY, Termius, or OpenSSH:
                    #                ssh %s@<server-ip> -p %d
                    #
                    # Single Port Design:
                    #   Whichever mode you choose ('webssh' or 'ssh'), the service will bind to
                    #   the single 'port' configured below. Set this to your Pterodactyl allocated port!
                    # ------------------------------------------------------------------------------
                    control:
                      mode: %s
                      port: %d
                    
                    # ------------------------------------------------------------------------------
                    # [CONSOLE] Pterodactyl Host Console Integration
                    #
                    # Settings:
                    #   - true  : Direct terminal access. You can type Linux commands directly
                    #             into the Pterodactyl panel's Console tab without needing SSH/Web.
                    #   - false : Status/Read-only mode. The panel console will only display server
                    #             logs and startup status, ignoring interactive input.
                    # ------------------------------------------------------------------------------
                    console:
                      enable_console_vps: %s
                    """,
                    username,
                    password,
                    port,
                    username,
                    port,
                    mode,
                    port,
                    enableConsoleVps
            );

            writer.write(yamlContent);
        } catch (Exception e) {
            System.err.println("[CONFIG] Failed to save " + configFile.getName() + ": " + e.getMessage());
        }
    }

    private static String generateRandomPassword(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isEnableConsoleVps() {
        return enableConsoleVps;
    }

    public void setEnableConsoleVps(boolean enableConsoleVps) {
        this.enableConsoleVps = enableConsoleVps;
    }
}
