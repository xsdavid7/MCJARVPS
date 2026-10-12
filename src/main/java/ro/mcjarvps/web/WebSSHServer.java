package ro.mcjarvps.web;

import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import ro.mcjarvps.config.AppConfig;
import ro.mcjarvps.terminal.TerminalSessionManager;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Embedded WebSSH server providing web login and real-time terminal streaming via WebSockets.
 * Author: xs7david
 */
public class WebSSHServer {

    private final AppConfig config;
    private final TerminalSessionManager terminalManager;
    private final Set<String> validTokens = ConcurrentHashMap.newKeySet();
    private final Set<WsContext> activeWsClients = ConcurrentHashMap.newKeySet();
    private Javalin app;
    private Consumer<String> terminalListener;

    public WebSSHServer(AppConfig config, TerminalSessionManager terminalManager) {
        this.config = config;
        this.terminalManager = terminalManager;
    }

    public void start() {
        int port = config.getPort();

        app = Javalin.create(javalinConfig -> {
            javalinConfig.showJavalinBanner = false;
        });

        // Serve main terminal HTML page
        app.get("/", ctx -> {
            try (InputStream in = getClass().getResourceAsStream("/web/index.html")) {
                if (in != null) {
                    ctx.contentType("text/html; charset=UTF-8");
                    ctx.result(in.readAllBytes());
                } else {
                    ctx.status(404).result("index.html not found");
                }
            }
        });

        // Login API endpoint
        app.post("/api/login", ctx -> {
            try {
                String body = ctx.body();
                Yaml yaml = new Yaml(new LoaderOptions());
                Map<String, Object> map = yaml.load(body);

                String user = map != null ? String.valueOf(map.get("username")) : "";
                String pass = map != null ? String.valueOf(map.get("password")) : "";

                if (config.getUsername().equals(user) && config.getPassword().equals(pass)) {
                    String token = UUID.randomUUID().toString();
                    validTokens.add(token);
                    ctx.json(Map.of("success", true, "token", token));
                    System.out.println("[WebSSH] User '" + user + "' authenticated successfully from " + ctx.ip());
                } else {
                    ctx.status(401).json(Map.of("success", false, "message", "Invalid username or password!"));
                }
            } catch (Exception e) {
                ctx.status(400).json(Map.of("success", false, "message", "Error: " + e.getMessage()));
            }
        });

        // WebSocket endpoint
        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> {
                String token = ctx.queryParam("token");
                if (token == null || !validTokens.contains(token)) {
                    ctx.session.close(1008, "Unauthorized");
                    return;
                }

                activeWsClients.add(ctx);

                // Send current terminal buffer history
                String history = terminalManager.getBufferHistory();
                if (!history.isEmpty()) {
                    ctx.send(serializeOutput(TerminalSessionManager.normalizeNewlines(history)));
                }
            });

            ws.onClose(ctx -> {
                activeWsClients.remove(ctx);
            });

            ws.onError(ctx -> {
                activeWsClients.remove(ctx);
            });

            ws.onMessage(ctx -> {
                String msg = ctx.message();
                try {
                    Yaml yaml = new Yaml(new LoaderOptions());
                    Map<String, Object> map = yaml.load(msg);
                    if (map != null && "input".equals(map.get("type"))) {
                        Object data = map.get("data");
                        if (data != null) {
                            terminalManager.writeInput(String.valueOf(data));
                        }
                    }
                } catch (Exception ignored) {
                    terminalManager.writeInput(msg);
                }
            });
        });

        // Register listener for broadcasting terminal output to WebSocket clients
        terminalListener = output -> {
            if (activeWsClients.isEmpty()) return;
            String payload = serializeOutput(TerminalSessionManager.normalizeNewlines(output));
            for (WsContext client : activeWsClients) {
                try {
                    client.send(payload);
                } catch (Exception ignored) {
                }
            }
        };
        terminalManager.addOutputListener(terminalListener);

        app.start(port);
        System.out.println("[WebSSH] Web terminal listening on http://0.0.0.0:" + port);
    }

    private String serializeOutput(String data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"output\",\"data\":");
        sb.append(escapeJson(data));
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    public void stop() {
        if (terminalListener != null) {
            terminalManager.removeOutputListener(terminalListener);
        }
        if (app != null) {
            app.stop();
        }
    }
}
