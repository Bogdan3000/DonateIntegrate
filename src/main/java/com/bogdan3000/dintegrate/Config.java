package com.bogdan3000.dintegrate;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final File file = new File("config/dintegrate.cfg");
    private final Map<Double, String> rules = new HashMap<>();

    private String token = "";
    private int userId = 0;
    private String tokenUrl = "https://donatepay.ru/api/v2/socket/token";
    private String socketUrl = "wss://centrifugo.donatepay.ru/connection/websocket?format=json";

    public void load() throws IOException {
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println("# === DonatePay Configuration ===");
                writer.println("token=YOUR_DONATEPAY_TOKEN");
                writer.println("user_id=0");
                writer.println();
                writer.println("# API endpoints (you can change these if DonatePay updates URLs)");
                writer.println("token_url=https://donatepay.ru/api/v2/socket/token");
                writer.println("socket_url=wss://centrifugo.donatepay.ru/connection/websocket?format=json");
                writer.println();
                writer.println("# === Donation Rules ===");
                writer.println("rule_1=50:say Спасибо за донат!");
                writer.println("rule_2=100:give @a minecraft:diamond 1");
            }
            LOGGER.info("[DIntegrate] Default config created.");
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            reader.lines().forEach(line -> {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) return;

                try {
                    if (line.startsWith("token=")) {
                        token = line.substring(6).trim();
                    } else if (line.startsWith("user_id=")) {
                        userId = Integer.parseInt(line.substring(8).trim());
                    } else if (line.startsWith("token_url=")) {
                        tokenUrl = line.substring(10).trim();
                    } else if (line.startsWith("socket_url=")) {
                        socketUrl = line.substring(11).trim();
                    } else if (line.startsWith("rule_")) {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2 && parts[1].contains(":")) {
                            String[] vals = parts[1].split(":", 2);
                            double amount = Double.parseDouble(vals[0]);
                            rules.put(amount, vals[1]);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("[DIntegrate] Error parsing config line: {}", line);
                }
            });
        }

        LOGGER.info("[DIntegrate] Config loaded: {} rules, user_id={}, URLs loaded.", rules.size(), userId);
    }

    public String getToken() { return token; }
    public int getUserId() { return userId; }
    public Map<Double, String> getRules() { return rules; }
    public String getTokenUrl() { return tokenUrl; }
    public String getSocketUrl() { return socketUrl; }
}