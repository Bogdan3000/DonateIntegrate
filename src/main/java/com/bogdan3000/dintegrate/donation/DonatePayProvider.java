package com.bogdan3000.dintegrate.donation;

import com.bogdan3000.dintegrate.DonateIntegrate;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.socket.client.IO;
import io.socket.client.Socket;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class DonatePayProvider implements DonationProvider {
    private static final String WS_URL = "wss://centrifugo.donatepay.ru:43002/connection/websocket";
    private static final String API_URL = "https://donatepay.ru/api/v2/socket/token";
    private static final Gson GSON = new Gson();

    private Socket socket;
    private Consumer<DonationEvent> donationHandler;
    private boolean isConnected;
    private String clientId;
    private int messageId = 3;

    @Override
    public void connect() {
        try {
            String token = ConfigHandler.getConfig().getDonpayToken();
            String userId = ConfigHandler.getConfig().getUserId();
            if (token == null || token.isEmpty() || userId == null || userId.isEmpty()) {
                DonateIntegrate.LOGGER.error("Токен или User ID не установлены");
                return;
            }

            String connectionToken = getConnectionToken(token);
            if (connectionToken == null) {
                DonateIntegrate.LOGGER.error("Не удалось получить токен подключения");
                return;
            }

            IO.Options options = new IO.Options();
            options.forceNew = true;
            options.reconnection = true;
            options.reconnectionAttempts = 5;
            options.reconnectionDelay = 1000;
            options.query = "token=" + connectionToken;

            socket = IO.socket(WS_URL, options);

            socket.on(Socket.EVENT_CONNECT, args -> {
                isConnected = true;
                DonateIntegrate.LOGGER.info("Подключено к DonatePay WebSocket");
                sendHandshake(connectionToken);
            });

            socket.on("message", args -> {
                try {
                    String message = args[0].toString();
                    JsonObject jsonMsg = GSON.fromJson(message, JsonObject.class);
                    handleMessage(jsonMsg);
                } catch (Exception e) {
                    DonateIntegrate.LOGGER.error("Ошибка обработки сообщения: {}", e.getMessage());
                }
            });

            socket.on(Socket.EVENT_DISCONNECT, args -> {
                isConnected = false;
                DonateIntegrate.LOGGER.warn("Отключено от DonatePay: {}", args[0]);
            });

            socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                isConnected = false;
                DonateIntegrate.LOGGER.error("Ошибка подключения к DonatePay: {}", args[0]);
            });

            socket.connect();
            startPingScheduler();
        } catch (Exception e) {
            isConnected = false;
            DonateIntegrate.LOGGER.error("Ошибка подключения к DonatePay: {}", e.getMessage());
        }
    }

    @Override
    public void disconnect() {
        try {
            if (socket != null) {
                socket.disconnect();
                socket = null;
                isConnected = false;
                DonateIntegrate.LOGGER.info("Отключено от DonatePay");
            }
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Ошибка отключения от DonatePay: {}", e.getMessage());
        }
    }

    @Override
    public boolean isConnected() {
        return isConnected;
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
            conn.setRequestProperty("User-Agent", "Minecraft-DonateIntegrate/2.0.0");
            conn.setDoOutput(true);

            JsonObject payload = new JsonObject();
            payload.addProperty("client", "java");
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            if (conn.getResponseCode() != 200) {
                DonateIntegrate.LOGGER.error("Ошибка получения токена, код HTTP: {}", conn.getResponseCode());
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
            DonateIntegrate.LOGGER.error("Ошибка получения токена подключения: {}", e.getMessage());
            return null;
        }
    }

    private void sendHandshake(String connectionToken) {
        try {
            JsonObject handshakeMsg = new JsonObject();
            JsonObject params = new JsonObject();
            params.addProperty("token", connectionToken);
            params.addProperty("name", "java");
            handshakeMsg.add("params", params);
            handshakeMsg.addProperty("id", 1);
            socket.emit("message", GSON.toJson(handshakeMsg));
            DonateIntegrate.LOGGER.debug("Отправлен handshake: {}", handshakeMsg);
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Ошибка отправки handshake: {}", e.getMessage());
        }
    }

    private void handleMessage(JsonObject jsonMsg) {
        try {
            if (jsonMsg.has("error")) {
                DonateIntegrate.LOGGER.error("Ошибка сервера: {}", jsonMsg.get("error"));
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
            DonateIntegrate.LOGGER.error("Ошибка обработки сообщения: {}", e.getMessage());
        }
    }

    private void handleHandshakeResponse(JsonObject jsonMsg) {
        try {
            if (!jsonMsg.has("result")) {
                DonateIntegrate.LOGGER.error("Ошибка handshake: {}", jsonMsg);
                return;
            }

            JsonObject result = jsonMsg.getAsJsonObject("result");
            if (!result.has("client")) {
                DonateIntegrate.LOGGER.error("Отсутствует client ID: {}", jsonMsg);
                return;
            }

            clientId = result.get("client").getAsString();
            DonateIntegrate.LOGGER.info("Получен client ID: {}", clientId);

            String channel = "$public:" + ConfigHandler.getConfig().getUserId();
            String channelToken = getChannelToken(ConfigHandler.getConfig().getDonpayToken(), clientId, channel);
            if (channelToken == null) {
                DonateIntegrate.LOGGER.error("Не удалось получить токен канала");
                return;
            }

            JsonObject subscribeMsg = new JsonObject();
            subscribeMsg.addProperty("id", 2);
            subscribeMsg.addProperty("method", 1);
            JsonObject params = new JsonObject();
            params.addProperty("channel", channel);
            params.addProperty("token", channelToken);
            subscribeMsg.add("params", params);

            socket.emit("message", GSON.toJson(subscribeMsg));
            DonateIntegrate.LOGGER.debug("Отправлена подписка: {}", subscribeMsg);
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Ошибка обработки handshake: {}", e.getMessage());
        }
    }

    private String getChannelToken(String accessToken, String clientId, String channel) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("client", clientId);
            com.google.gson.JsonArray channels = new com.google.gson.JsonArray();
            channels.add(channel);
            payload.add("channels", channels);

            URL url = new URL(API_URL + "?access_token=" + accessToken);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Minecraft-DonateIntegrate/2.0.0");
            conn.setDoOutput(true);

            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            if (conn.getResponseCode() != 200) {
                DonateIntegrate.LOGGER.error("Ошибка получения токена канала, код HTTP: {}", conn.getResponseCode());
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
                com.google.gson.JsonArray channelsArray = jsonResponse.getAsJsonArray("channels");
                for (int i = 0; i < channelsArray.size(); i++) {
                    JsonObject channelObj = channelsArray.get(i).getAsJsonObject();
                    if (channelObj.get("channel").getAsString().equals(channel)) {
                        return channelObj.get("token").getAsString();
                    }
                }
                return null;
            }
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Ошибка получения токена канала: {}", e.getMessage());
            return null;
        }
    }

    private void handleSubscriptionResponse(JsonObject jsonMsg) {
        try {
            if (jsonMsg.has("result")) {
                DonateIntegrate.LOGGER.info("Успешно подписан на канал донатов");
            } else if (jsonMsg.has("error")) {
                DonateIntegrate.LOGGER.error("Ошибка подписки: {}", jsonMsg.get("error"));
            }
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Ошибка обработки ответа подписки: {}", e.getMessage());
        }
    }

    private void handlePingResponse(JsonObject jsonMsg) {
        try {
            if (jsonMsg.has("result")) {
                DonateIntegrate.LOGGER.debug("Получен ответ на пинг: {}", jsonMsg);
            } else if (jsonMsg.has("error")) {
                DonateIntegrate.LOGGER.error("Ошибка пинга: {}", jsonMsg.get("error"));
            }
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Ошибка обработки пинга: {}", e.getMessage());
        }
    }

    private void handlePushNotification(JsonObject jsonMsg) {
        try {
            JsonObject result = jsonMsg.getAsJsonObject("result");
            if (!result.has("channel") || !result.has("data")) {
                DonateIntegrate.LOGGER.warn("Некорректный формат уведомления: {}", result);
                return;
            }

            JsonObject data = result.getAsJsonObject("data");
            if (!data.has("data") || !data.getAsJsonObject("data").has("notification")) {
                DonateIntegrate.LOGGER.warn("Отсутствует уведомление: {}", data);
                return;
            }

            JsonObject notification = data.getAsJsonObject("data").getAsJsonObject("notification");
            if (!notification.has("id") || !notification.has("vars")) {
                DonateIntegrate.LOGGER.warn("Некорректное уведомление: {}", notification);
                return;
            }

            JsonObject vars = notification.getAsJsonObject("vars");
            if (!vars.has("sum") || !vars.has("name")) {
                DonateIntegrate.LOGGER.warn("Отсутствуют переменные доната: {}", vars);
                return;
            }

            int id = notification.get("id").getAsInt();
            float sum = vars.get("sum").getAsFloat();
            String username = vars.get("name").getAsString();
            String comment = vars.has("comment") ? vars.get("comment").getAsString() : "";

            if (id <= ConfigHandler.getConfig().getLastDonate()) {
                DonateIntegrate.LOGGER.info("Пропущен обработанный донат #{}", id);
                return;
            }

            if (donationHandler != null) {
                donationHandler.accept(new DonationEvent(username, sum, comment, id));
            }
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Ошибка обработки уведомления: {}", e.getMessage());
        }
    }

    private void startPingScheduler() {
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
            try {
                if (isConnected && socket != null) {
                    JsonObject pingMsg = new JsonObject();
                    pingMsg.addProperty("id", messageId++);
                    pingMsg.addProperty("method", 7);
                    socket.emit("message", GSON.toJson(pingMsg));
                    DonateIntegrate.LOGGER.debug("Отправлен пинг: {}", pingMsg);
                }
            } catch (Exception e) {
                DonateIntegrate.LOGGER.error("Ошибка отправки пинга: {}", e.getMessage());
            }
        }, 0, 15, java.util.concurrent.TimeUnit.SECONDS);
    }
}