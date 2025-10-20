package com.bogdan3000.dintegrate.donation;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;

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

    private static final String TOKEN_URL = "https://donatepay.ru/api/v2/socket/token";
    private static final String WS_URL = "wss://centrifugo.donatepay.ru/connection/websocket?format=json";

    private final String accessToken;
    private final int userId;
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

    private String connectToken;
    private String clientId;
    private String subscriptionToken;

    private int msgCounter = 2; // handshake=1, subscribe=2, дальше идут пинги с 3

    public DonatePayProvider(String accessToken, int userId, Consumer<DonationEvent> handler) {
        this.accessToken = accessToken;
        this.userId = userId;
        this.donationHandler = handler;
    }

    @Override
    public void connect() {
        if (accessToken == null || accessToken.isBlank()) {
            System.err.println("[DIntegrate] Invalid DonatePay token in config!");
            return;
        }

        System.out.println("[DIntegrate] Requesting connection token...");
        getConnectionToken().thenAccept(token -> {
            if (token == null || token.isEmpty()) {
                System.err.println("[DIntegrate] Failed to get token, retrying...");
                scheduleReconnect();
                return;
            }
            this.connectToken = token;
            System.out.println("[DIntegrate] Got connection token: " + token);

            httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(WS_URL), this)
                    .thenAccept(ws -> {
                        this.socket = ws;
                        System.out.println("[DIntegrate] WebSocket connected to DonatePay.");
                        startPing(ws);
                        scheduler.schedule(() -> sendHandshake(ws), 500, TimeUnit.MILLISECONDS);
                    })
                    .exceptionally(ex -> {
                        System.err.println("[DIntegrate] WebSocket connection failed: " + ex.getMessage());
                        scheduleReconnect();
                        return null;
                    });
        });
    }

    // === HANDSHAKE ===
    private void sendHandshake(WebSocket ws) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("id", 1);
            JsonObject params = new JsonObject();
            params.addProperty("token", connectToken);
            params.addProperty("name", "js");
            root.add("params", params);
            String json = root.toString();
            System.out.println("[DIntegrate] WS -> " + json);
            ws.sendText(json, true);
            System.out.println("[DIntegrate] Sent handshake (step 1)");
        } catch (Exception e) {
            System.err.println("[DIntegrate] Error sending handshake: " + e.getMessage());
        }
    }

    // === SUBSCRIBE ===
    private void sendSubscribe(WebSocket ws) {
        try {
            String channel = "$public:" + userId;
            JsonObject root = new JsonObject();
            root.addProperty("method", 1); // не трогаем, сервер требует это значение
            root.addProperty("id", 2);
            JsonObject params = new JsonObject();
            params.addProperty("channel", channel);
            params.addProperty("token", subscriptionToken);
            root.add("params", params);

            String json = root.toString();
            System.out.println("[DIntegrate] WS -> " + json);
            ws.sendText(json, true);
            System.out.println("[DIntegrate] Sent subscribe (step 2)");
        } catch (Exception e) {
            System.err.println("[DIntegrate] Error sending subscribe: " + e.getMessage());
        }
    }

    // === STEP 1: Get connection token ===
    private CompletableFuture<String> getConnectionToken() {
        try {
            String body = "{\"access_token\":\"" + accessToken + "\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        if (resp.statusCode() != 200) {
                            System.err.println("[DIntegrate] HTTP error " + resp.statusCode());
                            return null;
                        }
                        System.out.println("[DIntegrate] HTTP <- " + resp.body());
                        try {
                            JsonReader reader = new JsonReader(new StringReader(resp.body()));
                            reader.setLenient(true);
                            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                            return json.has("token") ? json.get("token").getAsString() : null;
                        } catch (Exception e) {
                            System.err.println("[DIntegrate] Token parse error: " + e.getMessage());
                            return null;
                        }
                    });
        } catch (Exception e) {
            System.err.println("[DIntegrate] HTTP error: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    // === STEP 2.5: Get subscription token ===
    private CompletableFuture<String> getSubscriptionToken(String clientId) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("client", clientId);
            body.add("channels", JsonParser.parseString("[\"$public:" + userId + "\"]"));

            String url = TOKEN_URL + "?access_token=" + accessToken;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        if (resp.statusCode() != 200) {
                            System.err.println("[DIntegrate] Subscription token HTTP error: " + resp.statusCode());
                            return null;
                        }
                        System.out.println("[DIntegrate] HTTP (sub) <- " + resp.body());
                        try {
                            JsonReader reader = new JsonReader(new StringReader(resp.body()));
                            reader.setLenient(true);
                            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                            if (json.has("channels")) {
                                JsonObject chan = json.getAsJsonArray("channels").get(0).getAsJsonObject();
                                return chan.get("token").getAsString();
                            }
                        } catch (Exception e) {
                            System.err.println("[DIntegrate] Subscription token parse error: " + e.getMessage());
                        }
                        return null;
                    });
        } catch (Exception e) {
            System.err.println("[DIntegrate] Subscription token HTTP error: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    // === WEBSOCKET EVENTS ===
    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.println("[DIntegrate] WebSocket opened.");
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence message, boolean last) {
        String msg = message.toString();
        System.out.println("[DIntegrate] WS <- " + msg);

        try {
            JsonObject json = JsonParser.parseString(msg).getAsJsonObject();

            if (json.has("id") && json.get("id").getAsInt() == 1 && json.has("result")) {
                clientId = json.getAsJsonObject("result").get("client").getAsString();
                System.out.println("[DIntegrate] Got client ID: " + clientId);
                getSubscriptionToken(clientId).thenAccept(subToken -> {
                    if (subToken != null) {
                        this.subscriptionToken = subToken;
                        System.out.println("[DIntegrate] Got subscription token.");
                        sendSubscribe(webSocket);
                    } else {
                        System.err.println("[DIntegrate] Failed to get subscription token.");
                    }
                });
            } else if (json.has("id") && json.get("id").getAsInt() == 2 && json.has("result")) {
                System.out.println("[DIntegrate] Subscription confirmed (ttl=" +
                        json.getAsJsonObject("result").get("ttl").getAsInt() + ")");
            } else if (json.has("result") && json.getAsJsonObject("result").has("data")) {
                JsonObject vars = json.getAsJsonObject("result")
                        .getAsJsonObject("data")
                        .getAsJsonObject("data")
                        .getAsJsonObject("notification")
                        .getAsJsonObject("vars");
                handleDonationMessage(vars);
            } else if (json.has("method") &&
                    json.get("method").getAsString().equals("publication")) {
                JsonObject data = json.getAsJsonObject("params")
                        .getAsJsonObject("data")
                        .getAsJsonObject("notification")
                        .getAsJsonObject("vars");
                handleDonationMessage(data);
            }
        } catch (Exception e) {
            System.err.println("[DIntegrate] WS parse error: " + e.getMessage());
        }

        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.err.println("[DIntegrate] WebSocket error: " + error.getMessage());
        scheduleReconnect();
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        System.out.println("[DIntegrate] Disconnected (" + statusCode + "): " + reason);
        stopPing();
        scheduleReconnect();
        return CompletableFuture.completedFuture(null);
    }

    // === PING ===
    private ScheduledFuture<?> pingTask;

    private void startPing(WebSocket ws) {
        stopPing();
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                msgCounter++;
                String json = "{\"method\":7,\"id\":" + msgCounter + "}";
                ws.sendText(json, true);
                System.out.println("[DIntegrate] WS -> " + json + " (ping)");
            } catch (Exception e) {
                System.err.println("[DIntegrate] Ping send error: " + e.getMessage());
            }
        }, 25, 25, TimeUnit.SECONDS);
    }

    private void stopPing() {
        if (pingTask != null) {
            pingTask.cancel(true);
            pingTask = null;
        }
    }

    private void scheduleReconnect() {
        if (reconnecting) return;
        reconnecting = true;
        System.out.println("[DIntegrate] Reconnecting in 15s...");
        scheduler.schedule(() -> {
            reconnecting = false;
            connect();
        }, 15, TimeUnit.SECONDS);
    }

    private void handleDonationMessage(JsonObject vars) {
        try {
            System.out.println("[DIntegrate] [DONATION RAW] " + vars);
            String name = vars.has("name") ? vars.get("name").getAsString() : "Unknown";
            double sum = vars.has("sum") ? vars.get("sum").getAsDouble() : 0.0;
            String msg = vars.has("comment") ? vars.get("comment").getAsString() : "";
            donationHandler.accept(new DonationEvent(name, (float) sum, msg, -1));
            System.out.println("[DIntegrate] Donation: " + name + " -> " + sum + " (" + msg + ")");
        } catch (Exception e) {
            System.err.println("[DIntegrate] Donation parse error: " + e.getMessage());
        }
    }

    @Override
    public boolean isConnected() { return socket != null; }

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
        scheduler.shutdownNow();
    }
}