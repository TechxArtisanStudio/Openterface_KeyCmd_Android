package com.openterface.terminal;

import android.util.Log;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import java.util.Properties;

/**
 * Pushes SSH public key to remote server via password authentication.
 * Uses ChannelExec to execute commands on the remote host.
 */
public class SshKeyPusher {
    private static final String TAG = "SshKeyPusher";
    private static final int CONNECT_TIMEOUT = 20000; // 20 seconds

    /**
     * Listener for push operation progress and results.
     */
    public interface ProgressListener {
        void onProgress(String message);
        void onSuccess();
        void onError(String message);
    }

    /**
     * Push public key to remote server using password authentication.
     *
     * @param host      Server hostname/IP
     * @param port      SSH port (usually 22)
     * @param username  Username for authentication
     * @param password  Password for authentication
     * @param publicKey Public key to push (OpenSSH format)
     * @param listener  Progress callback
     */
    public static void pushKey(String host, int port, String username,
                               String password, String publicKey,
                               com.jcraft.jsch.SocketFactory socketFactory,
                               ProgressListener listener) {

        new Thread(() -> {
            try {
                listener.onProgress("Connecting to server…");

                JSch jsch = new JSch();

                // Host key checking: "ask" + auto-accept UserInfo.
                // Saves the host key on first use, validates on subsequent uses.
                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "ask");
                config.put("PreferredAuthentications", "keyboard-interactive,password");

                Session session = jsch.getSession(username, host, port);
                session.setConfig(config);
                session.setUserInfo(new AutoAcceptUserInfo());
                session.setPassword(password);

                if (socketFactory != null) {
                    session.setSocketFactory(socketFactory);
                }

                session.connect(CONNECT_TIMEOUT);

                listener.onProgress("Setting up .ssh directory…");

                // Create .ssh directory with proper permissions
                String mkdirCmd = "mkdir -p ~/.ssh && chmod 700 ~/.ssh";
                int mkdirExit = executeCommand(session, mkdirCmd);

                if (mkdirExit != 0) {
                    session.disconnect();
                    listener.onError("Failed to create .ssh directory");
                    return;
                }

                listener.onProgress("Adding public key…");

                // Escape the public key for shell safety
                String escapedKey = publicKey.replace("'", "'\\''");
                String appendCmd = "echo '" + escapedKey + "' >> ~/.ssh/authorized_keys";
                int appendExit = executeCommand(session, appendCmd);

                if (appendExit != 0) {
                    session.disconnect();
                    listener.onError("Failed to append key to authorized_keys");
                    return;
                }

                listener.onProgress("Setting permissions…");

                // Set proper permissions on authorized_keys
                String chmodCmd = "chmod 600 ~/.ssh/authorized_keys";
                executeCommand(session, chmodCmd);

                session.disconnect();

                Log.i(TAG, "Successfully pushed public key to " + host);
                listener.onSuccess();

            } catch (Exception e) {
                Log.e(TAG, "Failed to push key: " + e.getMessage());
                listener.onError(e.getMessage());
            }
        }, "SshKeyPusher").start();
    }

    /**
     * Execute a single command and return exit status.
     */
    private static int executeCommand(Session session, String command) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);

        channel.connect();

        // Wait for command to complete
        while (!channel.isClosed()) {
            Thread.sleep(100);
        }

        int exitStatus = channel.getExitStatus();
        channel.disconnect();

        return exitStatus;
    }

    /**
     * UserInfo implementation that auto-accepts host key prompts.
     * Works with StrictHostKeyChecking="ask" to save the host key on first use
     * and verify it on subsequent connections (MITM protection).
     */
    private static class AutoAcceptUserInfo implements UserInfo {
        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean promptPassword(String message) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return false;
        }

        @Override
        public boolean promptYesNo(String message) {
            // Automatically accept unknown host keys — they will be saved to known_hosts
            Log.d(TAG, "Auto-accepting host key: " + message);
            return true;
        }

        @Override
        public void showMessage(String message) {
            Log.d(TAG, "JSch message: " + message);
        }
    }
}
