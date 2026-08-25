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

                // Host key checking: use "no" to avoid Cipher.isCBC() NPE in BLE-Eth
                // tunnel scenario (JSch Issue #760). SshKeyPusher is a one-shot operation
                // (push public key to server), so host key verification is less critical.
                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                config.put("compression.s2c", "none");
                config.put("compression.c2s", "none");
                config.put("PreferredAuthentications", "keyboard-interactive,password");
                config.put("PubkeyAuthentication", "no");

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
                // Double newline guard:
                // 1) Pre-check: if file exists but lacks trailing \n, add one first
                // 2) Post-append: add trailing \n so next key won't stack
                String appendCmd =
                    "if [ -s ~/.ssh/authorized_keys ] && [ \"$(tail -c1 ~/.ssh/authorized_keys | wc -l)\" -eq 0 ]; then echo '' >> ~/.ssh/authorized_keys; fi; " +
                    "echo '" + escapedKey + "' >> ~/.ssh/authorized_keys; " +
                    "echo '' >> ~/.ssh/authorized_keys";
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
     * Execute a pre-built command on the remote server via a new SSH session.
     * Unlike {@link #pushKey}, this does NOT wrap the command in echo — it executes
     * the command directly via ChannelExec. Use this when the caller has already
     * built the full shell command (e.g. with mkdir, chmod, base64 decode, etc.).
     *
     * The command is wrapped in bash -c to ensure multi-line snippets
     * (if/then/fi, etc.) are executed as a shell script.
     *
     * @param host          Server hostname/IP
     * @param port          SSH port (usually 22)
     * @param username      Username for authentication
     * @param password      Password for authentication
     * @param command       Shell command to execute
     * @param socketFactory SocketFactory for transport (null for direct TCP, BleEthSocketFactory for BLE-Eth tunnel)
     * @param listener      Progress callback
     */
    public static void executeCommand(String host, int port, String username,
                                      String password, String command,
                                      com.jcraft.jsch.SocketFactory socketFactory,
                                      ProgressListener listener) {

        new Thread(() -> {
            try {
                listener.onProgress("Connecting to server…");

                JSch jsch = new JSch();

                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                config.put("compression.s2c", "none");
                config.put("compression.c2s", "none");
                config.put("PreferredAuthentications", "keyboard-interactive,password");
                config.put("PubkeyAuthentication", "no");

                Session session = jsch.getSession(username, host, port);
                session.setConfig(config);
                session.setUserInfo(new AutoAcceptUserInfo());
                session.setPassword(password);

                if (socketFactory != null) {
                    session.setSocketFactory(socketFactory);
                }

                session.connect(CONNECT_TIMEOUT);

                listener.onProgress("Executing command…");

                // Wrap in bash -c to ensure multi-line snippets are interpreted as shell script
                String escapedCommand = command.replace("'", "'\\''");
                String bashCommand = "bash -c '" + escapedCommand + "'";

                ChannelExec channel = (ChannelExec) session.openChannel("exec");
                channel.setCommand(bashCommand);
                channel.connect(CONNECT_TIMEOUT);

                // Wait for command to complete
                while (!channel.isClosed()) {
                    Thread.sleep(100);
                }

                int exitStatus = channel.getExitStatus();
                channel.disconnect();
                session.disconnect();

                if (exitStatus == 0) {
                    Log.i(TAG, "Command executed successfully on " + host);
                    listener.onSuccess();
                } else {
                    Log.w(TAG, "Command exited with status " + exitStatus + " on " + host);
                    listener.onError("Command exited with status " + exitStatus);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to execute command: " + e.getMessage());
                listener.onError(e.getMessage());
            }
        }, "SshKeyPusher-Exec").start();
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
     * UserInfo implementation that auto-accepts prompts.
     * Required by JSch for some authentication code paths.
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
            // Auto-accept prompts (host key verification is skipped with "no" mode)
            Log.d(TAG, "Auto-accepting prompt: " + message);
            return true;
        }

        @Override
        public void showMessage(String message) {
            Log.d(TAG, "JSch message: " + message);
        }
    }
}
