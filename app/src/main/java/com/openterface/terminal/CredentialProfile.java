package com.openterface.terminal;

import java.util.UUID;

/**
 * Represents a saved SSH credential profile.
 */
public class CredentialProfile {

    private String id;
    private String name;
    private String host;
    private int port;
    private String username;
    private String password;
    private boolean isActive;
    private long createdAt;
    private long updatedAt;

    public CredentialProfile() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public CredentialProfile(String id, String name, String host, int port,
                              String username, String password, boolean isActive,
                              long createdAt, long updatedAt) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Returns a display label: profile name if present, otherwise "user@host -p port".
     */
    public String getDisplayLabel() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return getShortDescription();
    }

    /**
     * Returns "user@host -p port" for compact display.
     */
    public String getShortDescription() {
        String user = username != null ? username : "";
        String h = host != null ? host : "";
        return user + "@" + h + " -p " + port;
    }
}
