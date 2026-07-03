package com.hitstudio.syncup.network.discovery;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class ServerDiscoveryRepository {

    public interface Callback {
        void onConnected(ServerInfo server);

        void onUnavailable(Throwable error);
    }

    private static final int DISCOVERY_PORT = 9999;
    private static final int DISCOVERY_TIMEOUT_MILLIS = 2_500;
    private static final int MAX_DATAGRAM_BYTES = 2_048;
    private static final String API_VERSION = "v1";
    private static volatile ServerDiscoveryRepository instance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger requestGeneration = new AtomicInteger();
    private final ServerCache cache;
    private final OkHttpClient httpClient;

    private ServerDiscoveryRepository(Context context) {
        cache = new ServerCache(context.getApplicationContext());
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    public static ServerDiscoveryRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (ServerDiscoveryRepository.class) {
                if (instance == null) {
                    instance = new ServerDiscoveryRepository(context);
                }
            }
        }
        return instance;
    }

    public void discover(Callback callback) {
        int generation = requestGeneration.incrementAndGet();
        executor.execute(() -> {
            try {
                ServerInfo cached = cache.get();
                if (cached != null) {
                    try {
                        ServerInfo verified = verify(cached.getBaseUrl(), cached.getServerId());
                        cache.put(verified);
                        postConnected(generation, callback, verified);
                        return;
                    } catch (IOException | JSONException ignored) {
                        cache.clear();
                    }
                }

                DiscoveryCandidate candidate = discoverUdp();
                ServerInfo verified = verify(candidate.baseUrl, candidate.serverId);
                cache.put(verified);
                postConnected(generation, callback, verified);
            } catch (Throwable error) {
                postUnavailable(generation, callback, error);
            }
        });
    }

    public void connectManually(String address, Callback callback) {
        int generation = requestGeneration.incrementAndGet();
        executor.execute(() -> {
            try {
                String baseUrl = normalizeManualAddress(address);
                ServerInfo verified = verify(baseUrl, null);
                cache.put(verified);
                postConnected(generation, callback, verified);
            } catch (Throwable error) {
                postUnavailable(generation, callback, error);
            }
        });
    }

    private DiscoveryCandidate discoverUdp() throws IOException, JSONException {
        UUID requestId = UUID.randomUUID();
        JSONObject requestJson = new JSONObject()
                .put("type", "SYNCUP_DISCOVER")
                .put("apiVersion", API_VERSION)
                .put("requestId", requestId.toString());
        byte[] requestBytes = requestJson.toString().getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(400);
            for (InetAddress address : broadcastAddresses()) {
                DatagramPacket request = new DatagramPacket(
                        requestBytes,
                        requestBytes.length,
                        address,
                        DISCOVERY_PORT
                );
                socket.send(request);
            }

            long deadline = System.currentTimeMillis() + DISCOVERY_TIMEOUT_MILLIS;
            byte[] responseBytes = new byte[MAX_DATAGRAM_BYTES];
            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket response =
                            new DatagramPacket(responseBytes, responseBytes.length);
                    socket.receive(response);
                    JSONObject json = new JSONObject(new String(
                            response.getData(),
                            response.getOffset(),
                            response.getLength(),
                            StandardCharsets.UTF_8
                    ));
                    if (!"SYNCUP_SERVER".equals(json.optString("type"))
                            || !API_VERSION.equals(json.optString("apiVersion"))
                            || !requestId.toString().equals(json.optString("requestId"))) {
                        continue;
                    }
                    UUID serverId = UUID.fromString(json.getString("serverId"));
                    String baseUrl = normalizeBaseUrl(json.getString("baseUrl"));
                    return new DiscoveryCandidate(serverId, baseUrl);
                } catch (SocketTimeoutException ignored) {
                    // Continue until the complete discovery window has elapsed.
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed responses and continue listening.
                }
            }
        }
        throw new SocketTimeoutException("No SyncUp server responded on this network");
    }

    private Set<InetAddress> broadcastAddresses() throws IOException {
        Set<InetAddress> addresses = new LinkedHashSet<>();
        addresses.add(InetAddress.getByName("255.255.255.255"));
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) {
            return addresses;
        }
        while (interfaces.hasMoreElements()) {
            NetworkInterface network = interfaces.nextElement();
            if (!network.isUp() || network.isLoopback()) {
                continue;
            }
            for (InterfaceAddress interfaceAddress : network.getInterfaceAddresses()) {
                if (interfaceAddress.getBroadcast() != null) {
                    addresses.add(interfaceAddress.getBroadcast());
                }
            }
        }
        return addresses;
    }

    private ServerInfo verify(String baseUrl, UUID expectedServerId)
            throws IOException, JSONException {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        Request request = new Request.Builder()
                .url(normalizedBaseUrl + "/server")
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Server identity request returned HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Server identity response was empty");
            }
            JSONObject json = new JSONObject(body.string());
            if (!API_VERSION.equals(json.optString("apiVersion"))) {
                throw new IOException("Server API version is not supported");
            }
            UUID serverId = UUID.fromString(json.getString("serverId"));
            if (expectedServerId != null && !expectedServerId.equals(serverId)) {
                throw new IOException("Cached server identity changed");
            }
            String serverName = json.getString("serverName").trim();
            if (serverName.isEmpty()) {
                throw new IOException("Server name is empty");
            }
            List<String> capabilities = jsonArray(json.optJSONArray("capabilities"));
            return new ServerInfo(serverId, serverName, normalizedBaseUrl, capabilities);
        } catch (IllegalArgumentException error) {
            throw new IOException("Server identity was invalid", error);
        }
    }

    private List<String> jsonArray(JSONArray array) throws JSONException {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int index = 0; index < array.length(); index++) {
            values.add(array.getString(index));
        }
        return values;
    }

    private String normalizeManualAddress(String address) throws IOException {
        String value = address == null ? "" : address.trim();
        if (value.isEmpty()) {
            throw new IOException("Server address is empty");
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        try {
            URI source = new URI(value);
            String scheme = source.getScheme();
            if (source.getHost() == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IOException("Server address is invalid");
            }
            int port = source.getPort() < 0 ? 8500 : source.getPort();
            URI base = new URI(
                    scheme.toLowerCase(Locale.ROOT),
                    null,
                    source.getHost(),
                    port,
                    "/api/v1",
                    null,
                    null
            );
            return normalizeBaseUrl(base.toString());
        } catch (URISyntaxException error) {
            throw new IOException("Server address is invalid", error);
        }
    }

    private String normalizeBaseUrl(String baseUrl) throws IOException {
        String value = baseUrl == null ? "" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        try {
            URI uri = new URI(value);
            if (uri.getHost() == null
                    || (!"http".equalsIgnoreCase(uri.getScheme())
                    && !"https".equalsIgnoreCase(uri.getScheme()))
                    || !"/api/v1".equals(uri.getPath())) {
                throw new IOException("SyncUp base URL is invalid");
            }
            return uri.toString();
        } catch (URISyntaxException error) {
            throw new IOException("SyncUp base URL is invalid", error);
        }
    }

    private void postConnected(int generation, Callback callback, ServerInfo server) {
        mainHandler.post(() -> {
            if (requestGeneration.get() == generation) {
                callback.onConnected(server);
            }
        });
    }

    private void postUnavailable(int generation, Callback callback, Throwable error) {
        mainHandler.post(() -> {
            if (requestGeneration.get() == generation) {
                callback.onUnavailable(error);
            }
        });
    }

    private static final class DiscoveryCandidate {
        private final UUID serverId;
        private final String baseUrl;

        private DiscoveryCandidate(UUID serverId, String baseUrl) {
            this.serverId = serverId;
            this.baseUrl = baseUrl;
        }
    }
}
