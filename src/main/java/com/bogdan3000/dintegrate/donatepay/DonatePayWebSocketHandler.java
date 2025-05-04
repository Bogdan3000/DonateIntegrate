package com.bogdan3000.dintegrate.donatepay;

import com.bogdan3000.dintegrate.DonateIntegrate;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import com.bogdan3000.dintegrate.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DonatePayWebSocketHandler {
    private final Gson gson = new Gson();
    private final AtomicInteger messageId = new AtomicInteger(3);
    private DonatepayWebSocket client;
    private final DonatePayApiClient apiClient;
    private boolean isRunning = false;
    private ScheduledExecutorService pingScheduler;

    public DonatePayWebSocketHandler(DonatePayApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void start() {
        ModConfig config = ConfigHandler.getConfig();
        if (!config.isEnabled() || config.getDonpayToken().isEmpty()) {
            DonateIntegrate.LOGGER.info("Donation processing disabled or token not set");
            return;
        }

        try {
            String token = config.getDonpayToken();
            DonateIntegrate.LOGGER.info("Starting WebSocket with token: {}", maskToken(token));

            String connectionToken = apiClient.getConnectionToken(token);
            if (connectionToken == null) {
                DonateIntegrate.LOGGER.error("Failed to obtain connection token");
                return;
            }

            URI serverUri = new URI("wss://centrifugo.donatepay.ru:43002/connection/websocket");
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            client = new DonatepayWebSocket(serverUri, token, connectionToken);
            client.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            client.setConnectionLostTimeout(30);
            client.connect();

            int connectionTimeout = 0;
            while (!client.isOpen() && connectionTimeout < 30) {
                Thread.sleep(1000);
                connectionTimeout++;
            }

            if (!client.isOpen()) {
                DonateIntegrate.LOGGER.error("WebSocket connection timeout");
                return;
            }

            isRunning = true;
            startPingScheduler();
            DonateIntegrate.LOGGER.info("WebSocket connection established");

        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("WebSocket startup error: {}", e.getMessage(), e);
        }
    }

    public void stop() {
        isRunning = false;
        if (pingScheduler != null) {
            pingScheduler.shutdown();
        }
        if (client != null && client.isOpen()) {
            client.close();
        }
    }

    private void startPingScheduler() {
        pingScheduler = Executors.newScheduledThreadPool(1);
        pingScheduler.scheduleAtFixedRate(() -> {
            if (isRunning && client != null && client.isOpen()) {
                JsonObject pingMsg = new JsonObject();
                pingMsg.addProperty("id", messageId.getAndIncrement());
                pingMsg.addProperty("method", 7);
                client.send(gson.toJson(pingMsg));
                DonateIntegrate.LOGGER.debug("Sent ping: {}", pingMsg);
            }
        }, 0, 15, TimeUnit.SECONDS);
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 10) return token;
        return token.substring(0, 5) + "..." + token.substring(token.length() - 5);
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
            DonateIntegrate.LOGGER.info("WebSocket connection opened");
            sendHandshake();
        }

        @Override
        public void onMessage(String message) {
            try {
                DonateIntegrate.LOGGER.debug("Received: {}", message.length() > 500 ? message.substring(0, 500) + "..." : message);
                JsonObject jsonMsg = gson.fromJson(message, JsonObject.class);
                handleMessage(jsonMsg);
            } catch (Exception e) {
                DonateIntegrate.LOGGER.error("Error processing message: {}", e.getMessage(), e);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            DonateIntegrate.LOGGER.info("WebSocket closed: {} (code: {})", reason, code);
            isRunning = false;
        }

        @Override
        public void onError(Exception ex) {
            DonateIntegrate.LOGGER.error("WebSocket error: {}", ex.getMessage(), ex);
        }

        private void sendHandshake() {
            JsonObject handshakeMsg = new JsonObject();
            JsonObject params = new JsonObject();
            params.addProperty("token", connectionToken);
            params.addProperty("name", "java");
            handshakeMsg.add("params", params);
            handshakeMsg.addProperty("id", 1);
            send(gson.toJson(handshakeMsg));
            DonateIntegrate.LOGGER.debug("Sent handshake: {}", handshakeMsg);
        }

        private void handleMessage(JsonObject jsonMsg) {
            if (jsonMsg.has("error")) {
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
        }

        private void handleHandshakeResponse(JsonObject jsonMsg) {
            if (!jsonMsg.has("result")) {
                DonateIntegrate.LOGGER.error("Handshake failed: {}", jsonMsg);
                return;
            }

            JsonObject result = jsonMsg.getAsJsonObject("result");
            if (!result.has("client")) {
                DonateIntegrate.LOGGER.error("No client ID in handshake: {}", jsonMsg);
                return;
            }

            clientId = result.get("client").getAsString();
            DonateIntegrate.LOGGER.info("Client ID: {}", clientId);

            ModConfig config = ConfigHandler.getConfig();
            String userId = config.getUserId();
            if (userId == null || userId.isEmpty()) {
                userId = apiClient.getUserId(accessToken);
                if (userId != null) {
                    config.setUserId(userId);
                    ConfigHandler.save();
                } else {
                    DonateIntegrate.LOGGER.error("User ID unavailable. Set manually with /dpi set_userid <id>");
                    return;
                }
            }

            String channel = "$public:" + userId;
            String channelToken = apiClient.getChannelToken(accessToken, clientId, channel);
            if (channelToken == null) {
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

            send(gson.toJson(subscribeMsg));
            DonateIntegrate.LOGGER.debug("Sent subscription: {}", subscribeMsg);
        }

        private void handleSubscriptionResponse(JsonObject jsonMsg) {
            if (jsonMsg.has("result")) {
                DonateIntegrate.LOGGER.info("Subscribed to donation channel");
            } else if (jsonMsg.has("error")) {
                DonateIntegrate.LOGGER.error("Subscription error: {}", jsonMsg.get("error"));
            }
        }

        private void handlePingResponse(JsonObject jsonMsg) {
            if (jsonMsg.has("result")) {
                DonateIntegrate.LOGGER.debug("Ping response: {}", jsonMsg);
            } else if (jsonMsg.has("error")) {
                DonateIntegrate.LOGGER.error("Ping error: {}", jsonMsg.get("error"));
            }
        }

        private void handlePushNotification(JsonObject jsonMsg) {
            JsonObject result = jsonMsg.getAsJsonObject("result");
            if (!result.has("channel") || !result.has("data")) {
                DonateIntegrate.LOGGER.warn("Invalid push format: {}", result);
                return;
            }

            String channel = result.get("channel").getAsString();
            JsonObject data = result.getAsJsonObject("data");
            if (!data.has("data") || !data.getAsJsonObject("data").has("notification")) {
                DonateIntegrate.LOGGER.warn("No notification in data: {}", data);
                return;
            }

            JsonObject notification = data.getAsJsonObject("data").getAsJsonObject("notification");
            if (!notification.has("id") || !notification.has("vars")) {
                DonateIntegrate.LOGGER.warn("Invalid notification: {}", notification);
                return;
            }

            JsonObject vars = notification.getAsJsonObject("vars");
            if (!vars.has("sum") || !vars.has("name")) {
                DonateIntegrate.LOGGER.warn("Missing donation vars: {}", vars);
                return;
            }

            int id = notification.get("id").getAsInt();
            float sum = vars.get("sum").getAsFloat();
            String username = vars.get("name").getAsString();
            String comment = vars.has("comment") ? vars.get("comment").getAsString() : "";

            DonateIntegrate.LOGGER.info("Donation from {} for {} (ID: {}, comment: {})", username, sum, id, comment);
            processDonation(id, sum, username, comment);
        }

        private void processDonation(int id, float sum, String username, String comment) {
            ModConfig config = ConfigHandler.getConfig();
            if (id <= config.getLastDonate()) {
                DonateIntegrate.LOGGER.info("Skipping processed donation #{}", id);
                return;
            }

            boolean actionFound = false;
            Random random = new Random();
            for (com.bogdan3000.dintegrate.config.Action action : config.getActions()) {
                if (Math.abs((float) action.getSum() - sum) < 0.001) {
                    actionFound = true;
                    List<String> commandsToExecute = new ArrayList<>();
                    String title = action.getMessage()
                            .replace("{username}", username)
                            .replace("{message}", comment);

                    List<String> availableCommands = action.getCommands();
                    switch (action.getExecutionMode()) {
                        case SEQUENTIAL:
                            commandsToExecute.addAll(availableCommands);
                            break;
                        case RANDOM_ONE:
                            if (!availableCommands.isEmpty()) {
                                commandsToExecute.add(availableCommands.get(random.nextInt(availableCommands.size())));
                            }
                            break;
                        case RANDOM_MULTIPLE:
                            if (!availableCommands.isEmpty()) {
                                int count = random.nextInt(availableCommands.size()) + 1;
                                List<String> shuffled = new ArrayList<>(availableCommands);
                                Collections.shuffle(shuffled, random);
                                commandsToExecute.addAll(shuffled.subList(0, Math.min(count, shuffled.size())));
                            }
                            break;
                        case ALL:
                            commandsToExecute.addAll(availableCommands);
                            break;
                    }

                    for (String cmd : commandsToExecute) {
                        String command = cmd.replace("{username}", username).replace("{message}", comment);
                        DonateIntegrate.commands.add(new DonateIntegrate.CommandToExecute(command, username));
                    }

                    if (!title.isEmpty()) {
                        DonateIntegrate.commands.add(new DonateIntegrate.CommandToExecute("title @a title \"" + title + "\"", username));
                    }

                    config.setLastDonate(id);
                    ConfigHandler.save();
                    DonateIntegrate.LOGGER.info("Processed donation #{}: {} donated {}, executed {} commands", id, username, sum, commandsToExecute.size());
                    break;
                }
            }

            if (!actionFound) {
                DonateIntegrate.LOGGER.warn("No action for donation sum: {}", sum);
            }
        }
    }
}