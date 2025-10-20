package com.bogdan3000.dintegrate.donation;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class DonatePayProvider implements DonationProvider, WebSocket.Listener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final String accessToken;
    private final int userId;
    private final String tokenUrl;
    private final String socketUrl;
    private final Consumer<DonationEvent> donationHandler;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("DonatePayScheduler");
                return t;
            });

    private WebSocket socket;
    private volatile boolean reconnecting = false;
    private ScheduledFuture<?> pingTask;

    private String connectToken;
    private String clientId;
    private String subscriptionToken;
    private int msgCounter = 2; // handshake=1, subscribe=2, дальше пинги

    public DonatePayProvider(String accessToken, int userId, String tokenUrl, String socketUrl, Consumer<DonationEvent> handler) {
        this.accessToken = accessToken;
        this.userId = userId;
        this.tokenUrl = tokenUrl;
        this.socketUrl = socketUrl;
        this.donationHandler = handler;
    }

    @Override
    public void connect() {
        if (accessToken == null || accessToken.isBlank()) {
            LOGGER.error("[DIntegrate] Invalid DonatePay token in config!");
            return;
        }

        LOGGER.info("[DIntegrate] Requesting connection token from {}", tokenUrl);
        getConnectionToken().thenAccept(token -> {
            if (token == null || token.isEmpty()) {
                LOGGER.error("[DIntegrate] Failed to get connection token, retrying...");
                scheduleReconnect();
                return;
            }
            this.connectToken = token;
            LOGGER.info("[DIntegrate] Got connection token.");

            httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(socketUrl), this)
                    .thenAccept(ws -> {
                        this.socket = ws;
                        LOGGER.info("[DIntegrate] WebSocket connected to {}", socketUrl);
                        startPing(ws);
                        scheduler.schedule(() -> sendHandshake(ws), 500, TimeUnit.MILLISECONDS);
                    })
                    .exceptionally(ex -> {
                        LOGGER.error("[DIntegrate] WebSocket connection failed: {}", ex.getMessage());
                        scheduleReconnect();
                        return null;
                    });
        });
    }

    private void sendHandshake(WebSocket ws) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("id", 1);
            JsonObject params = new JsonObject();
            params.addProperty("token", connectToken);
            params.addProperty("name", "js");
            root.add("params", params);
            ws.sendText(root.toString(), true);
            LOGGER.info("[DIntegrate] Sent handshake (step 1)");
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] Error sending handshake", e);
        }
    }

    private void sendSubscribe(WebSocket ws) {
        try {
            String channel = "$public:" + userId;
            JsonObject root = new JsonObject();
            root.addProperty("method", 1);
            root.addProperty("id", 2);
            JsonObject params = new JsonObject();
            params.addProperty("channel", channel);
            params.addProperty("token", subscriptionToken);
            root.add("params", params);
            ws.sendText(root.toString(), true);
            LOGGER.info("[DIntegrate] Sent subscribe (step 2)");
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] Error sending subscribe", e);
        }
    }

    private CompletableFuture<String> getConnectionToken() {
        try {
            String body = "{\"access_token\":\"" + accessToken + "\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        if (resp.statusCode() != 200) {
                            LOGGER.error("[DIntegrate] HTTP error {} when requesting {}", resp.statusCode(), tokenUrl);
                            return null;
                        }
                        try {
                            JsonReader reader = new JsonReader(new StringReader(resp.body()));
                            reader.setLenient(true);
                            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                            return json.has("token") ? json.get("token").getAsString() : null;
                        } catch (Exception e) {
                            LOGGER.error("[DIntegrate] Token parse error", e);
                            return null;
                        }
                    });
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] HTTP error", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<String> getSubscriptionToken(String clientId) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("client", clientId);
            body.add("channels", JsonParser.parseString("[\"$public:" + userId + "\"]"));

            String url = tokenUrl + "?access_token=" + accessToken;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        if (resp.statusCode() != 200) {
                            LOGGER.error("[DIntegrate] Subscription token HTTP error {}", resp.statusCode());
                            return null;
                        }
                        try {
                            JsonReader reader = new JsonReader(new StringReader(resp.body()));
                            reader.setLenient(true);
                            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                            if (json.has("channels")) {
                                JsonObject chan = json.getAsJsonArray("channels").get(0).getAsJsonObject();
                                return chan.get("token").getAsString();
                            }
                        } catch (Exception e) {
                            LOGGER.error("[DIntegrate] Subscription token parse error", e);
                        }
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] Subscription token HTTP error", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        LOGGER.info("[DIntegrate] WebSocket opened.");
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence message, boolean last) {
        String msg = message.toString();
        LOGGER.info("[DIntegrate] WS <- {}", msg);
        try {
            JsonObject json = JsonParser.parseString(msg).getAsJsonObject();
            if (json.has("id") && json.get("id").getAsInt() == 1 && json.has("result")) {
                clientId = json.getAsJsonObject("result").get("client").getAsString();
                getSubscriptionToken(clientId).thenAccept(subToken -> {
                    if (subToken != null) {
                        this.subscriptionToken = subToken;
                        sendSubscribe(webSocket);
                    } else {
                        LOGGER.error("[DIntegrate] Failed to get subscription token.");
                    }
                });
            } else if (json.has("result") && json.getAsJsonObject("result").has("data")) {
                JsonObject vars = json.getAsJsonObject("result")
                        .getAsJsonObject("data")
                        .getAsJsonObject("data")
                        .getAsJsonObject("notification")
                        .getAsJsonObject("vars");
                handleDonationMessage(vars);
            }
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] WS parse error", e);
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    private void startPing(WebSocket ws) {
        stopPing();
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (ws != null && !ws.isOutputClosed()) {
                    msgCounter++;
                    String json = "{\"method\":7,\"id\":" + msgCounter + "}";
                    ws.sendText(json, true);
                }
            } catch (Exception e) {
                LOGGER.error("[DIntegrate] Ping send error", e);
            }
        }, 25, 25, TimeUnit.SECONDS);
    }

    private void stopPing() {
        if (pingTask != null) {
            pingTask.cancel(true);
            pingTask = null;
        }
    }

    private void handleDonationMessage(JsonObject vars) {
        try {
            String name = vars.has("name") ? vars.get("name").getAsString() : "Unknown";
            double sum = vars.has("sum") ? vars.get("sum").getAsDouble() : 0.0;
            String msg = vars.has("comment") ? vars.get("comment").getAsString() : "";
            donationHandler.accept(new DonationEvent(name, sum, msg, -1));
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] Donation parse error", e);
        }
    }

    private void scheduleReconnect() {
        if (reconnecting) return;
        reconnecting = true;
        LOGGER.warn("[DIntegrate] Reconnecting in 15s...");
        scheduler.schedule(() -> {
            reconnecting = false;
            connect();
        }, 15, TimeUnit.SECONDS);
    }

    @Override
    public boolean isConnected() {
        return socket != null;
    }

    @Override
    public void onDonation(Consumer<DonationEvent> handler) { }

    @Override
    public void disconnect() {
        stopPing();
        if (socket != null) {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "Manual disconnect");
            } catch (Exception ignored) {}
            socket = null;
        }
    }
}