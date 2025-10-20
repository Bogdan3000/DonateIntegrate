package com.bogdan3000.dintegrate;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.*;
import java.util.*;

public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final File file = new File("config/dintegrate.cfg");

    private String token = "";
    private int userId = 0;
    private String tokenUrl = "https://donatepay.ru/api/v2/socket/token";
    private String socketUrl = "wss://centrifugo.donatepay.ru/connection/websocket?format=json";
    private final Map<Double, DonationRule> rules = new HashMap<>();

    public void load() throws IOException {
        if (!file.exists()) createDefault();

        Map<String, String> raw = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            reader.lines().forEach(line -> {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) return;
                int eq = line.indexOf('=');
                if (eq > 0) raw.put(line.substring(0, eq), line.substring(eq + 1));
            });
        }

        token = raw.getOrDefault("token", "");
        userId = parseIntSafe(raw.get("user_id"), 0);
        tokenUrl = raw.getOrDefault("token_url", tokenUrl);
        socketUrl = raw.getOrDefault("socket_url", socketUrl);

        rules.clear();

        // Чтение новой структуры правил
        Map<String, DonationRule> tmp = new HashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String key = e.getKey();
            String val = e.getValue();

            if (key.startsWith("rule_") && key.contains(".")) {
                String prefix = key.substring(0, key.indexOf('.')); // rule_1
                DonationRule rule = tmp.computeIfAbsent(prefix, k -> new DonationRule());
                if (key.endsWith(".amount")) rule.amount = parseDoubleSafe(val, 0);
                else if (key.endsWith(".mode")) rule.mode = val.trim().toLowerCase();
                else if (key.matches(prefix + "\\.cmd\\d+")) rule.commands.add(val.trim());
            }
            // Поддержка старого формата rule_X=50:command
            else if (key.startsWith("rule_") && val.contains(":")) {
                try {
                    String[] parts = val.split(":", 2);
                    double amount = Double.parseDouble(parts[0]);
                    String command = parts[1];
                    DonationRule rule = new DonationRule();
                    rule.amount = amount;
                    rule.mode = "all";
                    rule.commands.add(command);
                    rules.put(amount, rule);
                } catch (Exception ignored) {}
            }
        }

        // Добавляем новые правила
        for (DonationRule r : tmp.values()) {
            if (r.amount > 0 && !r.commands.isEmpty()) {
                rules.put(r.amount, r);
            }
        }

        LOGGER.info("[DIntegrate] Config loaded: {} rules, user_id={}, URLs loaded.", rules.size(), userId);
    }

    private void createDefault() throws IOException {
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
            writer.println("rule_1.amount=10");
            writer.println("rule_1.mode=all");
            writer.println("rule_1.cmd1=say Спасибо за донат!");
            writer.println("rule_1.cmd2=give @a diamond 1");
        }
        LOGGER.info("[DIntegrate] Default config created.");
    }

    public String getToken() { return token; }
    public int getUserId() { return userId; }
    public String getTokenUrl() { return tokenUrl; }
    public String getSocketUrl() { return socketUrl; }
    public Map<Double, DonationRule> getRules() { return rules; }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    private double parseDoubleSafe(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }

    public static class DonationRule {
        public double amount;
        public String mode = "all";
        public List<String> commands = new ArrayList<>();
    }
}