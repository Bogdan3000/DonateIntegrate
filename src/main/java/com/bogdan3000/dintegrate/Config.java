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

    public void load() throws IOException {
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println("token=YOUR_DONATEPAY_TOKEN");
                writer.println("user_id=0");
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
    }

    public String getToken() { return token; }
    public int getUserId() { return userId; }
    public Map<Double, String> getRules() { return rules; }
}