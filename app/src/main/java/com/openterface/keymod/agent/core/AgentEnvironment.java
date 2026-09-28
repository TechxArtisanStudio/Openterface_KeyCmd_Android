package com.openterface.keymod.agent.core;

import androidx.annotation.Nullable;

import com.openterface.keymod.BluetoothService;
import com.openterface.keymod.ConnectionManager;
import com.openterface.terminal.CredentialProfile;
import com.openterface.terminal.SshClient;

/**
 * Abstraction for the host Activity environment that the Agent core layer depends on.
 *
 * <p>Decouples {@code AgentController} and tool executors from the concrete
 * {@code MainActivity}, enabling unit testing and future Activity replacements.</p>
 *
 * <p>Implementation: {@code MainActivity implements AgentEnvironment}.</p>
 */
public interface AgentEnvironment {

    /**
     * Get the currently selected SSH credential profile (from Target Settings).
     *
     * @return active profile, or null if none selected
     */
    @Nullable
    CredentialProfile getActiveSshProfile();

    /**
     * Set the active SSH credential profile.
     *
     * @param profile profile to select, or null to clear
     */
    void setActiveSshProfile(@Nullable CredentialProfile profile);

    /**
     * Get the shared SshClient instance (may be null if SSH is not connected).
     */
    @Nullable
    SshClient getSshClient();

    /**
     * Store a newly connected SshClient so other components can access it.
     */
    void setSshClient(@Nullable SshClient client);

    /**
     * Get the BLE BluetoothService for HID fallback in tool executors.
     */
    @Nullable
    BluetoothService getBluetoothService();

    /**
     * Get the HID ConnectionManager for keyboard/mouse control.
     */
    @Nullable
    ConnectionManager getConnectionManager();
}
