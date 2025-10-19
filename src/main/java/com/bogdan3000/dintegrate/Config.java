package com.bogdan3000.dintegrate;

import java.io.*;
import java.util.*;

public class Config {
    private final File file = new File("config/dintegrate.cfg");
    private final Map<Double, String> rules = new HashMap<>();
    private String token = "";
    private int userId = 0;

    public void load() {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                try (PrintWriter writer = new PrintWriter(file)) {
                    writer.println("token=YOUR_DONATEPAY_TOKEN");
                    writer.println("user_id=0");
                    writer.println("rule_1=50:say Спасибо за донат!");
                    writer.println("rule_2=100:give @a minecraft:diamond 1");
                }
                System.out.println("[DIntegrate] Default config created.");
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.lines().forEach(line -> {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) return;

                    if (line.startsWith("token=")) {
                        token = line.substring(6).trim();
                    } else if (line.startsWith("user_id=")) {
                        try {
                            userId = Integer.parseInt(line.substring(8).trim());
                        } catch (NumberFormatException ignored) {}
                    } else if (line.startsWith("rule_")) {
                        String[] parts = line.split("=");
                        if (parts.length == 2 && parts[1].contains(":")) {
                            String[] vals = parts[1].split(":", 2);
                            try {
                                double amount = Double.parseDouble(vals[0]);
                                rules.put(amount, vals[1]);
                            } catch (Exception ignored) {}
                        }
                    }
                });
            }

            System.out.println("[DIntegrate] Config loaded. Rules: " + rules.size());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getToken() {
        return token;
    }

    public int getUserId() {
        return userId;
    }

    public Map<Double, String> getRules() {
        return rules;
    }
}