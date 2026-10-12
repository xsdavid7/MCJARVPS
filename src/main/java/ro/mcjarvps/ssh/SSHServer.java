package ro.mcjarvps.ssh;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.ShellFactory;
import ro.mcjarvps.config.AppConfig;
import ro.mcjarvps.terminal.TerminalSessionManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Embedded real SSH server powered by Apache MINA SSHD.
 * Allows direct connection from PuTTY, OpenSSH, Termius, etc.
 * Author: xs7david
 */
public class SSHServer {

    private final AppConfig config;
    private final TerminalSessionManager terminalManager;
    private SshServer sshd;

    public SSHServer(AppConfig config, TerminalSessionManager terminalManager) {
        this.config = config;
        this.terminalManager = terminalManager;
    }

    public void start() throws IOException {
        int port = config.getPort();
        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(port);

        // Host key persistence
        File hostKeyFile = new File("hostkey.ser");
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKeyFile.toPath()));

        // Password authentication against setup.yml / config.yml
        sshd.setPasswordAuthenticator((username, password, session) -> {
            boolean matches = config.getUsername().equals(username) && config.getPassword().equals(password);
            if (matches) {
                System.out.println("[SSH] Successful login for user '" + username + "' from " + session.getRemoteAddress());
            } else {
                System.out.println("[SSH] Failed login attempt for user '" + username + "' from " + session.getRemoteAddress());
            }
            return matches;
        });

        // Interactive Shell Factory
        sshd.setShellFactory(new ShellFactory() {
            @Override
            public Command createShell(ChannelSession channel) {
                return new ProotShellCommand(terminalManager);
            }
        });

        sshd.start();
        System.out.println("[SSH] Real SSH Server listening on port " + port);
        System.out.println("[SSH] Connect using: ssh " + config.getUsername() + "@<server-ip> -p " + port);
    }

    public void stop() {
        if (sshd != null) {
            try {
                sshd.stop(true);
            } catch (IOException ignored) {
            }
        }
    }

    private static class ProotShellCommand implements Command {
        private final TerminalSessionManager terminalManager;
        private InputStream in;
        private OutputStream out;
        private OutputStream err;
        private ExitCallback callback;
        private Consumer<String> outputListener;
        private Thread inputThread;
        private volatile boolean active = true;

        public ProotShellCommand(TerminalSessionManager terminalManager) {
            this.terminalManager = terminalManager;
        }

        @Override
        public void setInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public void setOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void setErrorStream(OutputStream err) {
            this.err = err;
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
            this.callback = callback;
        }

        @Override
        public void start(ChannelSession channel, Environment env) throws IOException {
            // Forward terminal output to SSH client with CRLF normalization (prevents PuTTY staircase)
            outputListener = text -> {
                if (!active || out == null) return;
                try {
                    String normalized = TerminalSessionManager.normalizeNewlines(text);
                    out.write(normalized.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (IOException ignored) {
                }
            };
            terminalManager.addOutputListener(outputListener);

            // Read client input and send to terminalManager
            inputThread = new Thread(() -> {
                byte[] buffer = new byte[1024];
                try {
                    int read;
                    while (active && (read = in.read(buffer)) != -1) {
                        byte[] copy = new byte[read];
                        System.arraycopy(buffer, 0, copy, 0, read);
                        terminalManager.writeInput(copy);
                    }
                } catch (IOException ignored) {
                } finally {
                    destroy(channel);
                }
            }, "SSH-Client-Input");
            inputThread.setDaemon(true);
            inputThread.start();
        }

        @Override
        public void destroy(ChannelSession channel) {
            active = false;
            if (outputListener != null) {
                terminalManager.removeOutputListener(outputListener);
                outputListener = null;
            }
            if (callback != null) {
                callback.onExit(0);
            }
        }
    }
}
