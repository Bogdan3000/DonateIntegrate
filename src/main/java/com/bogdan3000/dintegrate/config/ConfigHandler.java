package com.bogdan3000.dintegrate.config;

import com.bogdan3000.dintegrate.DonateIntegrate;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.List;

public class ConfigHandler {
    private static File configFile;
    private static ModConfig cachedConfig;
    private static long lastModified;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void register(File file) {
        configFile = new File(file.getParentFile(), "dintegrate.json");
        if (!configFile.exists()) {
            cachedConfig = new ModConfig();
            save();
        } else {
            load();
        }
        lastModified = configFile.lastModified();
    }

    public static ModConfig load() {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            ModConfig config = GSON.fromJson(reader, ModConfig.class);
            if (config == null || !isValid(config)) {
                DonateIntegrate.LOGGER.warn("Invalid config, creating new default");
                config = new ModConfig();
            }
            cachedConfig = config;
            lastModified = configFile.lastModified();
            DonateIntegrate.LOGGER.debug("Loaded config: token={}, userId={}", maskToken(config.getDonpayToken()), config.getUserId());
            return config;
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Failed to load config: {}", e.getMessage(), e);
            cachedConfig = new ModConfig();
            save();
            return cachedConfig;
        }
    }

    public static void save() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            GSON.toJson(cachedConfig, writer);
            lastModified = configFile.lastModified();
            DonateIntegrate.LOGGER.debug("Saved config: token={}, userId={}",
                    maskToken(cachedConfig.getDonpayToken()), cachedConfig.getUserId());
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Failed to save config: {}", e.getMessage(), e);
        }
    }

    public static void checkAndReloadConfig() {
        if (configFile.lastModified() > lastModified) {
            DonateIntegrate.LOGGER.info("Configuration file changed, reloading...");
            load();
        }
    }

    public static ModConfig getConfig() {
        if (cachedConfig == null) {
            load();
        }
        return cachedConfig;
    }

    private static boolean isValid(ModConfig config) {
        List<Action> actions = config.getActions();
        if (actions == null || actions.isEmpty()) {
            DonateIntegrate.LOGGER.warn("No actions configured");
            return false;
        }
        for (Action action : actions) {
            if (action.getSum() <= 0) {
                DonateIntegrate.LOGGER.warn("Invalid action sum: {}", action.getSum());
                return false;
            }
            if (action.getCommands().isEmpty()) {
                DonateIntegrate.LOGGER.warn("Action has no commands for sum: {}", action.getSum());
                return false;
            }
            for (String command : action.getCommands()) {
                if (command == null || command.trim().isEmpty()) {
                    DonateIntegrate.LOGGER.warn("Invalid command in action for sum: {}", action.getSum());
                    return false;
                }
            }
        }
        return true;
    }

    private static String maskToken(String token) {
        if (token == null || token.length() <= 10) return token;
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}