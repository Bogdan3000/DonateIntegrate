package com.bogdan3000.dintegrate;

import java.io.*;
import java.util.*;

public class Config {
    private final File file = new File("config/dintegrate.cfg");
    private final Map<Double, String> rules = new HashMap<>();
    private String token = "";
    private int userId = 0;

    public void load() throws IOException {
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

                try {
                    if (line.startsWith("token=")) {
                        token = line.substring(6).trim();
                    } else if (line.startsWith("user_id=")) {
                        userId = Integer.parseInt(line.substring(8).trim());
                    } else if (line.startsWith("rule_")) {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2 && parts[1].contains(":")) {
                            String[] vals = parts[1].split(":", 2);
                            double amount = Double.parseDouble(vals[0]);
                            rules.put(amount, vals[1]);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[DIntegrate] Error parsing config line: " + line);
                }
            });
        }
    }

    public String getToken() { return token; }
    public int getUserId() { return userId; }
    public Map<Double, String> getRules() { return Collections.unmodifiableMap(rules); }
}