package com.hitstudio.syncup.client.network.discovery;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ServerInfo {

    private final UUID serverId;
    private final String serverName;
    private final String serverVersion;
    private final String baseUrl;
    private final String host;
    private final List<String> capabilities;

    public ServerInfo(
            UUID serverId,
            String serverName,
            String serverVersion,
            String baseUrl,
            List<String> capabilities
    ) {
        this.serverId = Objects.requireNonNull(serverId);
        this.serverName = Objects.requireNonNull(serverName);
        this.serverVersion = serverVersion;
        this.baseUrl = Objects.requireNonNull(baseUrl);
        this.host = java.net.URI.create(baseUrl).getHost();
        this.capabilities = Collections.unmodifiableList(capabilities);
    }

    public UUID getServerId() {
        return serverId;
    }

    public String getServerName() {
        return serverName;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getHost() {
        return host;
    }

    public List<String> getCapabilities() {
        return capabilities;
    }
}
