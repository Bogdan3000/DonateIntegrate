package com.bogdan3000.dintegrate;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ApiClient {
    private final Gson gson = new Gson();

    public String getConnectionToken(String accessToken) {
        try {
            String token = tryEndpoint("https://donatepay.ru/api/v2/socket/token", "{\"access_token\":\"" + accessToken + "\"}");
            if (token == null) {
                DonateIntegrate.LOGGER.info("Trying legacy endpoint");
                token = tryEndpoint("https://donatepay.ru/api/v1/socket/token", "{\"token\":\"" + accessToken + "\"}");
            }
            return token;
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Error getting connection token: {}", e.getMessage(), e);
            return null;
        }
    }

    public String getChannelToken(String accessToken, String clientId, String channel) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("client", clientId);
            JsonArray channels = new JsonArray();
            channels.add(channel);
            payload.add("channels", channels);

            URL url = new URL("https://donatepay.ru/api/v2/socket/token?access_token=" + accessToken);
            HttpURLConnection conn = setupConnection(url, "POST");
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            if (conn.getResponseCode() != 200) {
                logErrorResponse(conn);
                return null;
            }

            String response = readResponse(conn);
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            JsonArray channelsArray = jsonResponse.getAsJsonArray("channels");
            for (int i = 0; i < channelsArray.size(); i++) {
                JsonObject channelObj = channelsArray.get(i).getAsJsonObject();
                if (channelObj.get("channel").getAsString().equals(channel)) {
                    return channelObj.get("token").getAsString();
                }
            }
            return null;
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Error getting channel token: {}", e.getMessage(), e);
            return null;
        }
    }

    public String getUserId(String accessToken) {
        try {
            URL url = new URL("https://donatepay.ru/api/v1/user/me?access_token=" + accessToken);
            HttpURLConnection conn = setupConnection(url, "GET");
            if (conn.getResponseCode() != 200) {
                DonateIntegrate.LOGGER.error("Failed to get user ID, HTTP code: {}", conn.getResponseCode());
                return null;
            }

            String response = readResponse(conn);
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            return jsonResponse.getAsJsonObject("data").get("id").getAsString();
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Error getting user ID: {}", e.getMessage(), e);
            return null;
        }
    }

    private String tryEndpoint(String endpoint, String payload) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = setupConnection(url, "POST");
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = payload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        if (conn.getResponseCode() != 200) {
            logErrorResponse(conn);
            return null;
        }

        String response = readResponse(conn);
        JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
        return jsonResponse.get("token").getAsString();
    }

    private HttpURLConnection setupConnection(URL url, String method) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setDoOutput(true);
        return conn;
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
            return response.toString();
        }
    }

    private void logErrorResponse(HttpURLConnection conn) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder error = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                error.append(line);
            }
            DonateIntegrate.LOGGER.error("HTTP error response (code {}): {}", conn.getResponseCode(), error);
        }
    }
}