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

        Map<String, DonationRule> tmp = new HashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String key = e.getKey();
            String val = e.getValue();

            if (key.startsWith("rule_") && key.contains(".")) {
                String prefix = key.substring(0, key.indexOf('.'));
                DonationRule rule = tmp.computeIfAbsent(prefix, k -> new DonationRule());
                if (key.endsWith(".amount")) rule.amount = parseDoubleSafe(val, 0);
                else if (key.endsWith(".mode")) rule.mode = val.trim().toLowerCase();
                else if (key.endsWith(".startdelay")) rule.startDelay = parseDoubleSafe(val, 0.0);
                else if (key.matches(prefix + "\\.cmd\\d+")) rule.commands.add(val.trim());
            }
            // поддержка старого формата rule_X=50:command
            else if (key.startsWith("rule_") && val.contains(":")) {
                try {
                    String[] parts = val.split(":", 2);
                    double amount = Double.parseDouble(parts[0]);
                    String command = parts[1];
                    DonationRule rule = new DonationRule();
                    rule.amount = amount;
                    rule.commands.add(command);
                    rules.put(amount, rule);
                } catch (Exception ignored) {}
            }
        }

        for (DonationRule r : tmp.values()) {
            if (r.amount > 0 && !r.commands.isEmpty()) rules.put(r.amount, r);
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
            writer.println("# API endpoints");
            writer.println("token_url=https://donatepay.ru/api/v2/socket/token");
            writer.println("socket_url=wss://centrifugo.donatepay.ru/connection/websocket?format=json");
            writer.println();
            writer.println("# === Donation Rules — Full Demo ===");
            writer.println();

            // === Rule 1: simple sequential execution with delay ===
            writer.println("# 1️⃣ Mode: all — sequential commands with delay");
            writer.println("rule_1.amount=10");
            writer.println("rule_1.mode=all");
            writer.println("rule_1.startdelay=1");
            writer.println("rule_1.cmd1=say Спасибо, {name}, за {sum} рублей!");
            writer.println("rule_1.cmd2=delay 1.5");
            writer.println("rule_1.cmd3=say Вот тебе подарок!");
            writer.println("rule_1.cmd4=/give @p minecraft:iron_ingot 3");
            writer.println();

            // === Rule 2: random — pick one random command ===
            writer.println("# 2️⃣ Mode: random — one random command");
            writer.println("rule_2.amount=20");
            writer.println("rule_2.mode=random");
            writer.println("rule_2.cmd1=say {name} получил изумруд!");
            writer.println("rule_2.cmd2=say {name} получил железо!");
            writer.println("rule_2.cmd3=say {name} получил уголь!");
            writer.println("rule_2.cmd4=/give @p minecraft:diamond 1");
            writer.println("rule_2.cmd5=/give @p minecraft:emerald 1");
            writer.println();

            // === Rule 3: random_all — shuffle all commands ===
            writer.println("# 3️⃣ Mode: random_all — all commands, random order");
            writer.println("rule_3.amount=30");
            writer.println("rule_3.mode=random_all");
            writer.println("rule_3.cmd1=say Случайный порядок активирован!");
            writer.println("rule_3.cmd2=/give @p minecraft:apple 1");
            writer.println("rule_3.cmd3=/give @p minecraft:bread 1");
            writer.println("rule_3.cmd4=delay 1");
            writer.println("rule_3.cmd5=say Все команды выполнились в случайном порядке!");
            writer.println();

            // === Rule 4: random3 — choose 3 random commands ===
            writer.println("# 4️⃣ Mode: random3 — 3 случайные команды без повторов");
            writer.println("rule_4.amount=40");
            writer.println("rule_4.mode=random3");
            writer.println("rule_4.cmd1=say Комбо-донат от {name}!");
            writer.println("rule_4.cmd2=randomdelay 0.5-1.5");
            writer.println("rule_4.cmd3=/effect give @p minecraft:speed 5 2");
            writer.println("rule_4.cmd4=/effect give @p minecraft:jump_boost 5 2");
            writer.println("rule_4.cmd5=/effect give @p minecraft:regeneration 5 1");
            writer.println("rule_4.cmd6=say Бонусный предмет!");
            writer.println("rule_4.cmd7=/give @p minecraft:diamond 1");
            writer.println();

            // === Rule 5: mixed delays and randoms ===
            writer.println("# 5️⃣ Mode: all — delay + randomdelay + rand demo");
            writer.println("rule_5.amount=50");
            writer.println("rule_5.mode=all");
            writer.println("rule_5.startdelay=0.5");
            writer.println("rule_5.cmd1=say {name}, готовься!");
            writer.println("rule_5.cmd2=randomdelay 0.5-1.5");
            writer.println("rule_5.cmd3=/playsound minecraft:entity.experience_orb.pickup master @p");
            writer.println("rule_5.cmd4=delay 1");
            writer.println("rule_5.cmd5=say Второй этап...");
            writer.println("rule_5.cmd6=rand 0.3-1");
            writer.println("rule_5.cmd7=say Финалочка!");
            writer.println("rule_5.cmd8=/title @p actionbar {\"text\":\"Спасибо за поддержку, {name}!\"}");
            writer.println();

            // === Rule 6: multiple delay types ===
            writer.println("# 6️⃣ Mode: all — mixed delay chain");
            writer.println("rule_6.amount=60");
            writer.println("rule_6.mode=all");
            writer.println("rule_6.cmd1=say Многоступенчатый донат!");
            writer.println("rule_6.cmd2=delay 0.5");
            writer.println("rule_6.cmd3=say Первая пауза прошла!");
            writer.println("rule_6.cmd4=randomdelay 1-2");
            writer.println("rule_6.cmd5=say Вторая пауза рандомная!");
            writer.println("rule_6.cmd6=rand 0.5-1");
            writer.println("rule_6.cmd7=say Финал! Все задержки сработали!");
        }

        LOGGER.info("[DIntegrate] Default config created — full feature demo loaded (clean).");
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
        public double startDelay = 0.0;
        public List<String> commands = new ArrayList<>();
    }
}