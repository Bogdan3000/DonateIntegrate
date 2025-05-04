package com.bogdan3000.dintegrate.config;

import com.bogdan3000.dintegrate.DonateIntegrate;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;

public class ConfigHandler {
    private static File configFile;

    public static void register(File file) {
        configFile = new File(file.getParentFile(), "dintegrate.json");
        if (!configFile.exists()) {
            save(new ModConfig());
        }
    }

    public static ModConfig load() {
        try {
            Gson gson = new Gson();
            BufferedReader reader = new BufferedReader(new FileReader(configFile));
            ModConfig config = gson.fromJson(reader, ModConfig.class);
            reader.close();
            return config != null ? config : new ModConfig();
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Failed to load config: {}", e.getMessage());
            return new ModConfig();
        }
    }

    public static void save(ModConfig config) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            BufferedWriter writer = new BufferedWriter(new FileWriter(configFile));
            gson.toJson(config, writer);
            writer.close();
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }
}