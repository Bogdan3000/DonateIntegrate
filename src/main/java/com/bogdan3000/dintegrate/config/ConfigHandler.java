package com.bogdan3000.dintegrate.config;

import com.bogdan3000.dintegrate.DonateIntegrate;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.List;

public class ConfigHandler {
    private static File configFile;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void register(File file) {
        configFile = new File(file.getParentFile(), "dintegrate.json");
        if (!configFile.exists()) {
            save(new ModConfig());
        }
    }

    public static ModConfig load() {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            ModConfig config = GSON.fromJson(reader, ModConfig.class);
            if (config == null || !isValid(config)) {
                DonateIntegrate.LOGGER.warn("Invalid or missing config, using default");
                config = new ModConfig();
                save(config);
            }
            return config;
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Failed to load config: {}", e.getMessage(), e);
            ModConfig config = new ModConfig();
            save(config);
            return config;
        }
    }

    public static void save(ModConfig config) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            GSON.toJson(config, writer);
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Failed to save config: {}", e.getMessage(), e);
        }
    }

    private static boolean isValid(ModConfig config) {
        if (config.getDonpayToken() != null && config.getDonpayToken().length() < 10) {
            DonateIntegrate.LOGGER.warn("Invalid DonatePay token length");
            return false;
        }
        if (config.getUserId() != null && !config.getUserId().matches("\\d+")) {
            DonateIntegrate.LOGGER.warn("Invalid user ID format");
            return false;
        }
        List<Action> actions = config.getActions();
        if (actions == null || actions.isEmpty()) {
            DonateIntegrate.LOGGER.warn("No actions configured");
            return false;
        }
        for (Action action : actions) {
            if (action.getSum() <= 0 || action.getCommand() == null || action.getMessage() == null) {
                DonateIntegrate.LOGGER.warn("Invalid action: sum={}, command={}, message={}",
                        action.getSum(), action.getCommand(), action.getMessage());
                return false;
            }
        }
        return true;
    }
}