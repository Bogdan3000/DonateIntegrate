package com.bogdan3000.dintegrate.donation;

import com.bogdan3000.dintegrate.DonateIntegrate;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Implementation of DonationProvider for DonatePay service using WebSocket.
 */
public class DonatePayProvider implements DonationProvider {
    private static final String WS_URL = "wss://centrifugo.donatepay.ru:43002/connection/websocket";
    private static final String API_URL = "https://donatepay.ru/api/v2/socket/token";
    private static final Gson GSON = new Gson();

    private WebSocketClient socket;
    private Consumer<DonationEvent> donationHandler;
    private volatile boolean isConnected;
    private String clientId;
    private final AtomicInteger messageId = new AtomicInteger(3);
    private ScheduledExecutorService pingScheduler;

    @Override
    public void connect() {
        try {
            if (isConnected && socket != null && socket.isOpen()) {
                DonateIntegrate.LOGGER.info("WebSocket already connected, skipping reconnect");
                return;
            }

            disconnect();

            String token = ConfigHandler.getConfig().getDonpayToken();
            String userId = ConfigHandler.getConfig().getUserId();
            if (token == null || token.trim().isEmpty() || userId == null || !userId.matches("\\d+")) {
                DonateIntegrate.LOGGER.error("Invalid token or User ID");
                isConnected = false;
                return;
            }

            String connectionToken = getConnectionToken(token);
            if (connectionToken == null) {
                DonateIntegrate.LOGGER.error("Failed to obtain connection token");
                isConnected = false;
                return;
            }

            URI serverUri = new URI(WS_URL);
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Minecraft-DonateIntegrate/2.0.3");

            socket = new DonatePayWebSocket(serverUri, token, connectionToken, headers);
            socket.setConnectionLostTimeout(30);
            socket.connect();

            int connectionTimeout = 0;
            while (!socket.isOpen() && connectionTimeout < 30) {
                Thread.sleep(1000);
                connectionTimeout++;
            }

            if (!socket.isOpen()) {
                DonateIntegrate.LOGGER.error("WebSocket connection timeout");
                isConnected = false;
                return;
            }

            isConnected = true;
            startPingScheduler();
            DonateIntegrate.LOGGER.info("Connected to DonatePay WebSocket");
        } catch (Exception e) {
            isConnected = false;
            DonateIntegrate.LOGGER.error("Error connecting to DonatePay: {}", e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        try {
            isConnected = false;
            if (pingScheduler != null) {
                pingScheduler.shutdownNow();
                pingScheduler = null;
            }
            if (socket != null) {
                if (socket.isOpen()) {
                    socket.close();
                }
                socket = null;
                DonateIntegrate.LOGGER.info("Disconnected from DonatePay");
            }
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Error disconnecting from DonatePay: {}", e.getMessage());
        }
    }

    @Override
    public boolean isConnected() {
        return isConnected && socket != null && socket.isOpen();
    }

    @Override
    public void onDonation(Consumer<DonationEvent> handler) {
        this.donationHandler = handler;
    }

    private String getConnectionToken(String accessToken) {
        try {
            URL url = new URL(API_URL + "?access_token=" + accessToken);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Minecraft-DonateIntegrate/2.0.3");
            conn.setDoOutput(true);

            JsonObject payload = new JsonObject();
            payload.addProperty("client", "java");
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            if (conn.getResponseCode() != 200) {
                DonateIntegrate.LOGGER.error("Failed to get token, HTTP code: {}", conn.getResponseCode());
                return null;
            }

            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                JsonObject jsonResponse = GSON.fromJson(response.toString(), JsonObject.class);
                return jsonResponse.get("token").getAsString();
            }
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Error getting connection token: {}", e.getMessage());
            return null;
        }
    }

    private void startPingScheduler() {
        pingScheduler = Executors.newScheduledThreadPool(1);
        pingScheduler.scheduleAtFixedRate(() -> {
            try {
                if (socket != null && socket.isOpen()) {
                    JsonObject pingMsg = new JsonObject();
                    pingMsg.addProperty("id", messageId.getAndIncrement());
                    pingMsg.addProperty("method", 7);
                    socket.send(GSON.toJson(pingMsg));
                    DonateIntegrate.LOGGER.debug("Sent ping: {}", pingMsg);
                    // Проверка подключения с каждым пингом
                    if (!isValidConnection()) {
                        isConnected = false;
                        DonateIntegrate.LOGGER.warn("Connection validation failed");
                    }
                } else {
                    isConnected = false;
                }
            } catch (Exception e) {
                isConnected = false;
                DonateIntegrate.LOGGER.error("Error sending ping: {}", e.getMessage());
            }
        }, 0, 15, TimeUnit.SECONDS);
    }

    private boolean isValidConnection() {
        try {
            if (socket == null || !socket.isOpen()) return false;
            // Простая проверка: если токен или userId пустые или некорректные, считаем соединение недействительным
            String token = ConfigHandler.getConfig().getDonpayToken();
            String userId = ConfigHandler.getConfig().getUserId();
            if (token == null || token.trim().isEmpty() || userId == null || !userId.matches("\\d+")) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private class DonatePayWebSocket extends WebSocketClient {
        private final String accessToken;
        private final String connectionToken;

        public DonatePayWebSocket(URI serverUri, String accessToken, String connectionToken, Map<String, String> headers) {
            super(serverUri, headers);
            this.accessToken = accessToken;
            this.connectionToken = connectionToken;
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            DonateIntegrate.LOGGER.info("WebSocket connection opened");
            sendHandshake();
        }

        @Override
        public void onMessage(String message) {
            try {
                JsonObject jsonMsg = GSON.fromJson(message, JsonObject.class);
                handleMessage(jsonMsg);
            } catch (Exception e) {
                DonateIntegrate.LOGGER.error("Error processing message: {}", e.getMessage());
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            isConnected = false;
            DonateIntegrate.LOGGER.warn("WebSocket closed: {} (code: {})", reason, code);
        }

        @Override
        public void onError(Exception ex) {
            isConnected = false;
            DonateIntegrate.LOGGER.error("WebSocket error: {}", ex.getMessage());
        }

        private void sendHandshake() {
            try {
                JsonObject handshakeMsg = new JsonObject();
                JsonObject params = new JsonObject();
                params.addProperty("token", connectionToken);
                params.addProperty("name", "java");
                handshakeMsg.add("params", params);
                handshakeMsg.addProperty("id", 1);
                send(GSON.toJson(handshakeMsg));
                DonateIntegrate.LOGGER.debug("Sent handshake: {}", handshakeMsg);
            } catch (Exception e) {
                isConnected = false;
                DonateIntegrate.LOGGER.error("Error sending handshake: {}", e.getMessage());
            }
        }

        private void handleMessage(JsonObject jsonMsg) {
            try {
                if (jsonMsg.has("error")) {
                    isConnected = false;
                    DonateIntegrate.LOGGER.error("Server error: {}", jsonMsg.get("error"));
                    return;
                }

                if (jsonMsg.has("id") && jsonMsg.get("id").getAsInt() == 1) {
                    handleHandshakeResponse(jsonMsg);
                } else if (jsonMsg.has("id") && jsonMsg.get("id").getAsInt() == 2) {
                    handleSubscriptionResponse(jsonMsg);
                } else if (jsonMsg.has("id") && jsonMsg.get("id").getAsInt() >= 3) {
                    handlePingResponse(jsonMsg);
                } else if (jsonMsg.has("result")) {
                    handlePushNotification(jsonMsg);
                }
            } catch (Exception e) {
                isConnected = false;
                DonateIntegrate.LOGGER.error("Error processing message: {}", e.getMessage());
            }
        }

        private void handleHandshakeResponse(JsonObject jsonMsg) {
            try {
                if (!jsonMsg.has("result")) {
                    isConnected = false;
                    DonateIntegrate.LOGGER.error("Handshake failed: {}", jsonMsg);
                    return;
                }

                JsonObject result = jsonMsg.getAsJsonObject("result");
                if (!result.has("client")) {
                    isConnected = false;
                    DonateIntegrate.LOGGER.error("No client ID: {}", jsonMsg);
                    return;
                }

                clientId = result.get("client").getAsString();
                DonateIntegrate.LOGGER.info("Received client ID: {}", clientId);

                String channel = "$public:" + ConfigHandler.getConfig().getUserId();
                String channelToken = getChannelToken(accessToken, clientId, channel);
                if (channelToken == null) {
                    isConnected = false;
                    DonateIntegrate.LOGGER.error("Failed to obtain channel token");
                    return;
                }

                JsonObject subscribeMsg = new JsonObject();
                subscribeMsg.addProperty("id", 2);
                subscribeMsg.addProperty("method", 1);
                JsonObject params = new JsonObject();
                params.addProperty("channel", channel);
                params.addProperty("token", channelToken);
                subscribeMsg.add("params", params);

                send(GSON.toJson(subscribeMsg));
                DonateIntegrate.LOGGER.debug("Sent subscription: {}", subscribeMsg);
            } catch (Exception e) {
                isConnected = false;
                DonateIntegrate.LOGGER.error("Error processing handshake: {}", e.getMessage());
            }
        }

        private String getChannelToken(String accessToken, String clientId, String channel) {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("client", clientId);
                JsonArray channels = new JsonArray();
                channels.add(channel);
                payload.add("channels", channels);

                URL url = new URL(API_URL + "?access_token=" + accessToken);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "Minecraft-DonateIntegrate/2.0.3");
                conn.setDoOutput(true);

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                if (conn.getResponseCode() != 200) {
                    isConnected = false;
                    DonateIntegrate.LOGGER.error("Failed to get channel token, HTTP code: {}", conn.getResponseCode());
                    return null;
                }

                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    JsonObject jsonResponse = GSON.fromJson(response.toString(), JsonObject.class);
                    JsonArray channelsArray = jsonResponse.getAsJsonArray("channels");
                    for (int i = 0; i < channelsArray.size(); i++) {
                        JsonObject channelObj = channelsArray.get(i).getAsJsonObject();
                        if (channelObj.get("channel").getAsString().equals(channel)) {
                            return channelObj.get("token").getAsString();
                        }
                    }
                    return null;
                }
            } catch (Exception e) {
                isConnected = false;
                DonateIntegrate.LOGGER.error("Error getting channel token: {}", e.getMessage());
                return null;
            }
        }

        private void handleSubscriptionResponse(JsonObject jsonMsg) {
            try {
                if (jsonMsg.has("result")) {
                    isConnected = true;
                    DonateIntegrate.LOGGER.info("Successfully subscribed to donation channel");
                } else if (jsonMsg.has("error")) {
                    isConnected = false;
                    DonateIntegrate.LOGGER.error("Subscription error: {}", jsonMsg.get("error"));
                }
            } catch (Exception e) {
                isConnected = false;
                DonateIntegrate.LOGGER.error("Error processing subscription response: {}", e.getMessage());
            }
        }

        private void handlePingResponse(JsonObject jsonMsg) {
            try {
                if (jsonMsg.has("result")) {
                    DonateIntegrate.LOGGER.debug("Received ping response: {}", jsonMsg);
                } else if (jsonMsg.has("error")) {
                    isConnected = false;
                    DonateIntegrate.LOGGER.error("Ping error: {}", jsonMsg.get("error"));
                }
            } catch (Exception e) {
                isConnected = false;
                DonateIntegrate.LOGGER.error("Error processing ping: {}", e.getMessage());
            }
        }

        private void handlePushNotification(JsonObject jsonMsg) {
            try {
                JsonObject result = jsonMsg.getAsJsonObject("result");
                if (!result.has("channel") || !result.has("data")) {
                    DonateIntegrate.LOGGER.warn("Invalid notification format: {}", result);
                    return;
                }

                JsonObject data = result.getAsJsonObject("data");
                if (!data.has("data") || !data.getAsJsonObject("data").has("notification")) {
                    DonateIntegrate.LOGGER.warn("Missing notification: {}", data);
                    return;
                }

                JsonObject notification = data.getAsJsonObject("data").getAsJsonObject("notification");
                if (!notification.has("id") || !notification.has("vars")) {
                    DonateIntegrate.LOGGER.warn("Invalid notification: {}", notification);
                    return;
                }

                JsonObject vars = notification.getAsJsonObject("vars");
                if (!vars.has("sum") || !vars.has("name")) {
                    DonateIntegrate.LOGGER.warn("Missing donation variables: {}", vars);
                    return;
                }

                int id = notification.get("id").getAsInt();
                float sum = vars.get("sum").getAsFloat();
                String username = vars.get("name").getAsString();
                String comment = vars.has("comment") ? vars.get("comment").getAsString() : "";

                if (id <= ConfigHandler.getConfig().getLastDonate()) {
                    DonateIntegrate.LOGGER.info("Skipped processed donation #{}", id);
                    return;
                }

                if (donationHandler != null) {
                    donationHandler.accept(new DonationEvent(username, sum, comment, id));
                }
            } catch (Exception e) {
                DonateIntegrate.LOGGER.error("Error processing notification: {}", e.getMessage());
            }
        }
    }
}