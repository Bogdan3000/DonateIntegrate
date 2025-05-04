package com.bogdan3000.dintegrate;

import com.bogdan3000.dintegrate.config.Action;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import com.bogdan3000.dintegrate.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class WebSocketClientConn extends Thread {
    private DonatepayWebSocket client;
    private boolean isRunning = false;
    private final Gson gson = new Gson();
    private final AtomicInteger messageId = new AtomicInteger(3); // Начинаем с 3, так как 1 и 2 используются для handshake

    @Override
    public void run() {
        ModConfig config = ConfigHandler.load();
        if (!config.isEnabled() || config.getDonpayToken().isEmpty()) {
            DonateIntegrate.LOGGER.info("Donation processing is disabled or token is not set, skipping");
            return;
        }

        try {
            String token = config.getDonpayToken();
            DonateIntegrate.LOGGER.info("Starting WebSocket connection with token");

            // Log token for debugging (mask most of it)
            if (token.length() > 10) {
                String maskedToken = token.substring(0, 5) + "..." + token.substring(token.length() - 5);
                DonateIntegrate.LOGGER.debug("Using token: {}", maskedToken);
            }

            // Get connection token from API
            String connectionToken = getConnectionToken(token);
            if (connectionToken == null) {
                DonateIntegrate.LOGGER.error("Failed to obtain connection token");

                // Try the legacy endpoint format as fallback
                DonateIntegrate.LOGGER.info("Trying legacy API endpoint as fallback");
                connectionToken = getLegacyConnectionToken(token);

                if (connectionToken == null) {
                    DonateIntegrate.LOGGER.error("All connection token attempts failed");
                    return;
                }
            }

            DonateIntegrate.LOGGER.info("Obtained connection token successfully");

            // Create and connect WebSocket client
            URI serverUri = new URI("wss://centrifugo.donatepay.ru:43002/connection/websocket");

            // Add HTTP headers
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36");

            // Create client
            client = new DonatepayWebSocket(serverUri, token, connectionToken);
            client.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36");

            // Connect with timeout
            client.setConnectionLostTimeout(30); // 30 seconds timeout
            client.connect();

            DonateIntegrate.LOGGER.info("WebSocket connection attempt initiated");

            // Wait for connection (with timeout)
            int connectionAttemptTimeout = 0;
            while (!client.isOpen() && connectionAttemptTimeout < 30) {
                Thread.sleep(1000);
                connectionAttemptTimeout++;
            }

            if (!client.isOpen()) {
                DonateIntegrate.LOGGER.error("Failed to connect to WebSocket within timeout period");
                return;
            }

            DonateIntegrate.LOGGER.info("WebSocket connection established successfully");
            isRunning = true;

            // Keep the thread alive and send pings
            while (isRunning && client.isOpen()) {
                try {
                    // Отправляем ping в формате Centrifugo
                    JsonObject pingMsg = new JsonObject();
                    pingMsg.addProperty("id", messageId.getAndIncrement());
                    pingMsg.addProperty("method", 7);
                    client.send(gson.toJson(pingMsg));
                    DonateIntegrate.LOGGER.debug("Sent ping: {}", pingMsg.toString());
                    Thread.sleep(15000); // Every 15 seconds
                } catch (InterruptedException e) {
                    DonateIntegrate.LOGGER.error("Interrupted during WebSocket monitoring", e);
                    isRunning = false;
                }
            }

            DonateIntegrate.LOGGER.info("WebSocket monitoring loop ended");

        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Error in WebSocket thread: {}", e.getMessage(), e);
        } finally {
            if (client != null && client.isOpen()) {
                client.close();
            }
        }
    }

    public void stopClient() {
        isRunning = false;
        if (client != null && client.isOpen()) {
            client.close();
        }
    }

    private String getConnectionToken(String accessToken) {
        try {
            URL url = new URL("https://donatepay.ru/api/v2/socket/token");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36");
            conn.setDoOutput(true);

            String jsonInputString = "{\"access_token\":\"" + accessToken + "\"}";
            DonateIntegrate.LOGGER.debug("Sending token request: {}", jsonInputString);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            if (conn.getResponseCode() != 200) {
                DonateIntegrate.LOGGER.error("Failed to get connection token, HTTP code: {}", conn.getResponseCode());
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    DonateIntegrate.LOGGER.error("Error response: {}", errorResponse.toString());
                }
                return null;
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                String responseStr = response.toString();
                DonateIntegrate.LOGGER.debug("Token response: {}", responseStr);

                JsonObject jsonResponse = new JsonParser().parse(responseStr).getAsJsonObject();
                if (jsonResponse.has("token")) {
                    return jsonResponse.get("token").getAsString();
                } else {
                    DonateIntegrate.LOGGER.error("No token found in response: {}", responseStr);
                    return null;
                }
            }
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Error getting connection token: {}", e.getMessage(), e);
            return null;
        }
    }

    private String getLegacyConnectionToken(String accessToken) {
        try {
            URL url = new URL("https://donatepay.ru/api/v1/socket/token");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36");
            conn.setDoOutput(true);

            String jsonInputString = "{\"token\":\"" + accessToken + "\"}";
            DonateIntegrate.LOGGER.debug("Sending legacy token request: {}", jsonInputString);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            if (conn.getResponseCode() != 200) {
                DonateIntegrate.LOGGER.error("Legacy endpoint failed, HTTP code: {}", conn.getResponseCode());
                return null;
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                String responseStr = response.toString();
                DonateIntegrate.LOGGER.debug("Legacy token response: {}", responseStr);

                JsonObject jsonResponse = new JsonParser().parse(responseStr).getAsJsonObject();
                if (jsonResponse.has("token")) {
                    return jsonResponse.get("token").getAsString();
                } else {
                    DonateIntegrate.LOGGER.error("No token in legacy response: {}", responseStr);
                    return null;
                }
            }
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Error with legacy token endpoint: {}", e.getMessage(), e);
            return null;
        }
    }

    private String getChannelToken(String accessToken, String clientId, String channel) {
        try {
            URL url = new URL("https://donatepay.ru/api/v2/socket/token?access_token=" + accessToken);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36");
            conn.setDoOutput(true);

            JsonObject payload = new JsonObject();
            payload.addProperty("client", clientId);
            JsonArray channels = new JsonArray();
            channels.add(channel);
            payload.add("channels", channels);
            String jsonInputString = gson.toJson(payload);
            DonateIntegrate.LOGGER.debug("Sending channel token request: {}", jsonInputString);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            if (conn.getResponseCode() != 200) {
                DonateIntegrate.LOGGER.error("Failed to get channel token, HTTP code: {}", conn.getResponseCode());
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    DonateIntegrate.LOGGER.error("Error response: {}", errorResponse.toString());
                }
                return null;
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                String responseStr = response.toString();
                DonateIntegrate.LOGGER.debug("Channel token response: {}", responseStr);

                JsonObject jsonResponse = new JsonParser().parse(responseStr).getAsJsonObject();
                if (jsonResponse.has("channels")) {
                    JsonArray channelsArray = jsonResponse.getAsJsonArray("channels");
                    for (int i = 0; i < channelsArray.size(); i++) {
                        JsonObject channelObj = channelsArray.get(i).getAsJsonObject();
                        if (channelObj.has("channel") && channelObj.get("channel").getAsString().equals(channel) && channelObj.has("token")) {
                            return channelObj.get("token").getAsString();
                        }
                    }
                    DonateIntegrate.LOGGER.error("No matching channel token found for {} in response: {}", channel, responseStr);
                    return null;
                } else {
                    DonateIntegrate.LOGGER.error("No channels array in channel token response: {}", responseStr);
                    return null;
                }
            }
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Error getting channel token: {}", e.getMessage(), e);
            return null;
        }
    }

    private class DonatepayWebSocket extends WebSocketClient {
        private final String accessToken;
        private final String connectionToken;
        private String clientId;

        public DonatepayWebSocket(URI serverUri, String accessToken, String connectionToken) {
            super(serverUri);
            this.accessToken = accessToken;
            this.connectionToken = connectionToken;
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            DonateIntegrate.LOGGER.info("WebSocket connection established");

            try {
                JsonObject handshakeMsg = new JsonObject();
                JsonObject params = new JsonObject();
                params.addProperty("token", connectionToken);
                params.addProperty("name", "java");
                handshakeMsg.add("params", params);
                handshakeMsg.addProperty("id", 1);

                send(gson.toJson(handshakeMsg));
                DonateIntegrate.LOGGER.debug("Sent handshake: {}", handshakeMsg.toString());
            } catch (Exception e) {
                DonateIntegrate.LOGGER.error("Error during WebSocket setup: {}", e.getMessage(), e);
            }
        }

        @Override
        public void onMessage(String message) {
            try {
                String logMessage = message.length() > 500 ? message.substring(0, 500) + "... [truncated]" : message;
                DonateIntegrate.LOGGER.debug("Received message: {}", logMessage);

                JsonObject jsonMsg = new JsonParser().parse(message).getAsJsonObject();

                if (jsonMsg.has("error")) {
                    DonateIntegrate.LOGGER.error("Server error: {}", jsonMsg.get("error").toString());
                    return;
                }

                if (jsonMsg.has("id") && jsonMsg.get("id").getAsString().equals("1")) {
                    if (jsonMsg.has("result")) {
                        JsonObject result = jsonMsg.getAsJsonObject("result");
                        if (result.has("client")) {
                            clientId = result.get("client").getAsString();
                            DonateIntegrate.LOGGER.info("Received client {}", clientId);
                        } else {
                            DonateIntegrate.LOGGER.error("No client ID in handshake response: {}", jsonMsg);
                            return;
                        }

                        ModConfig config = ConfigHandler.load();
                        String userId = config.getUserId();

                        if (userId == null || userId.isEmpty()) {
                            userId = getUserIdFromToken(accessToken);
                            if (userId != null && !userId.isEmpty()) {
                                DonateIntegrate.LOGGER.info("Retrieved user ID: {}", userId);
                                config.setUserId(userId);
                                ConfigHandler.save(config);
                            }
                        }

                        if (userId == null || userId.isEmpty()) {
                            DonateIntegrate.LOGGER.error("User ID not available, cannot subscribe");
                            DonateIntegrate.LOGGER.info("Set manually with /dpi set_userid <id>");
                            return;
                        }

                        String channel = "$public:" + userId;

                        // Запрашиваем channel token
                        String channelToken = getChannelToken(accessToken, clientId, channel);
                        if (channelToken == null) {
                            DonateIntegrate.LOGGER.error("Failed to obtain channel token for subscription");
                            return;
                        }

                        // Отправляем второе handshake-сообщение
                        JsonObject subscribeMsg = new JsonObject();
                        subscribeMsg.addProperty("id", 2);
                        subscribeMsg.addProperty("method", 1);
                        JsonObject subParams = new JsonObject();
                        subParams.addProperty("channel", channel);
                        subParams.addProperty("token", channelToken);
                        subscribeMsg.add("params", subParams);

                        send(gson.toJson(subscribeMsg));
                        DonateIntegrate.LOGGER.debug("Sent handshake2: {}", subscribeMsg.toString());
                        DonateIntegrate.LOGGER.info("Subscribed to donation channel for user ID: {}", userId);
                    } else {
                        DonateIntegrate.LOGGER.error("Handshake failed: {}", jsonMsg);
                    }
                    return;
                }

                if (jsonMsg.has("id") && jsonMsg.get("id").getAsString().equals("2")) {
                    if (jsonMsg.has("result")) {
                        DonateIntegrate.LOGGER.info("Successfully subscribed to donation channel");
                    } else if (jsonMsg.has("error")) {
                        DonateIntegrate.LOGGER.error("Subscription error: {}", jsonMsg.get("error").toString());
                    }
                    return;
                }

                if (jsonMsg.has("id") && jsonMsg.get("id").getAsInt() >= 3) {
                    if (jsonMsg.has("result")) {
                        DonateIntegrate.LOGGER.debug("Received ping response: {}", jsonMsg.toString());
                    } else if (jsonMsg.has("error")) {
                        DonateIntegrate.LOGGER.error("Ping error: {}", jsonMsg.get("error").toString());
                    }
                    return;
                }

                if (jsonMsg.has("result")) {
                    JsonObject result = jsonMsg.getAsJsonObject("result");
                    if (result.has("channel") && result.has("data")) {
                        String channel = result.get("channel").getAsString();
                        JsonObject data = result.getAsJsonObject("data");

                        DonateIntegrate.LOGGER.info("Received push notification on channel: {}", channel);

                        if (data.has("data")) {
                            JsonObject innerData = data.getAsJsonObject("data");
                            if (innerData.has("notification")) {
                                JsonObject notification = innerData.getAsJsonObject("notification");
                                if (notification.has("id") && notification.has("vars")) {
                                    JsonObject vars = notification.getAsJsonObject("vars");

                                    if (vars.has("sum") && vars.has("name")) {
                                        int id = notification.get("id").getAsInt();
                                        float sum = vars.get("sum").getAsFloat();
                                        String username = vars.get("name").getAsString();
                                        String donate_message = vars.get("comment").getAsString();

                                        DonateIntegrate.LOGGER.info("Processing donation from {} for {} (ID: {})", username, sum, id);
                                        processNotification(id, sum, username, donate_message);
                                    } else {
                                        DonateIntegrate.LOGGER.warn("Missing required fields in donation vars: {}", vars);
                                    }
                                } else {
                                    DonateIntegrate.LOGGER.warn("Invalid notification format: {}", notification);
                                }
                            } else {
                                DonateIntegrate.LOGGER.warn("No notification in inner data: {}", innerData);
                            }
                        } else {
                            DonateIntegrate.LOGGER.warn("No inner data in push data: {}", data);
                        }
                    } else {
                        DonateIntegrate.LOGGER.warn("Invalid result format: {}", result);
                    }
                }
            } catch (Exception e) {
                DonateIntegrate.LOGGER.error("Error processing WebSocket message: {}", e.getMessage(), e);
            }
        }

        private String getUserIdFromToken(String accessToken) {
            try {
                URL url = new URL("https://donatepay.ru/api/v1/user/me?access_token=" + accessToken);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36");

                if (conn.getResponseCode() != 200) {
                    DonateIntegrate.LOGGER.error("Failed to get user info, HTTP code: {}", conn.getResponseCode());
                    return null;
                }

                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }

                    JsonObject jsonResponse = new JsonParser().parse(response.toString()).getAsJsonObject();
                    if (jsonResponse.has("data") && jsonResponse.getAsJsonObject("data").has("id")) {
                        return jsonResponse.getAsJsonObject("data").get("id").getAsString();
                    }
                    return null;
                }
            } catch (Exception e) {
                DonateIntegrate.LOGGER.error("Error getting user ID: {}", e.getMessage(), e);
                return null;
            }
        }

        private void processNotification(int id, float sum, String username, String donate_message) {
            ModConfig config = ConfigHandler.load();
            int lastDonate = config.getLastDonate();

            // Проверяем, новый ли донат
            if (id <= lastDonate) {
                DonateIntegrate.LOGGER.info("Skipping already processed donation #{}", id);
                return;
            }

            // Проверяем действия из конфигурации
            List<Action> actions = config.getActions();
            boolean actionFound = false;

            for (Action action : actions) {
                if (Math.abs((float) action.getSum() - sum) < 0.001) { // Учитываем возможные неточности с плавающей точкой
                    actionFound = true;
                    String name = action.getCommand().replace("{username}", username);
                    String comment = action.getMessage().replace("{message}", donate_message);

                    // Проверяем, является ли action.command командой (начинается с "/")
                    if (name.startsWith("/")) {
                        // Удаляем начальный "/" и добавляем команду для выполнения от имени игрока
                        String command = name.substring(1);
                        DonateIntegrate.commands.add("/execute as @s run " + command);
                    } else {
                        // Если не команда, отправляем как сообщение в чат
                        DonateIntegrate.commands.add("/say " + name);
                    }

                    DonateIntegrate.LOGGER.info("Processed donation #{}: {} donated {}", id, username, sum);

                    // Обновляем lastDonate
                    config.setLastDonate(id);
                    ConfigHandler.save(config);
                    break;
                }
            }

            if (!actionFound) {
                DonateIntegrate.LOGGER.warn("No matching action found for donation sum: {}", sum);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            DonateIntegrate.LOGGER.info("WebSocket connection closed: {} (code: {})", reason, code);
        }

        @Override
        public void onError(Exception ex) {
            DonateIntegrate.LOGGER.error("WebSocket error: {}", ex.getMessage(), ex);
        }
    }
}