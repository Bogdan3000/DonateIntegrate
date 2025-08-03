package com.bogdan3000.dintegrate.config;

import com.bogdan3000.dintegrate.DonateIntegrate;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.List;

public class ConfigHandler {
    private static File configFile;
    private static ModConfig cachedConfig;
    private static long lastModified;
    private static final Yaml YAML = new Yaml(new Constructor(ModConfig.class));

    public static void register(File file) {
        configFile = new File(file.getParentFile(), "dintegrate.yml");
        if (!configFile.exists()) {
            cachedConfig = new ModConfig();
            save();
        } else {
            load(); // Изменено: просто вызываем load(), так как возвращаемое значение не используется
        }
        lastModified = configFile.lastModified();
    }

    public static void load() { // Изменено: возвращаемый тип изменен на void
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            ModConfig config = YAML.loadAs(reader, ModConfig.class);
            if (config == null || !isValid(config)) {
                DonateIntegrate.LOGGER.warn("Некорректная конфигурация, создание стандартной");
                config = new ModConfig();
            }
            cachedConfig = config;
            lastModified = configFile.lastModified();
            DonateIntegrate.LOGGER.debug("Загружена конфигурация: token={}, userId={}",
                    maskToken(config.getDonpayToken()), config.getUserId());
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Ошибка загрузки конфигурации: {}", e.getMessage());
            cachedConfig = new ModConfig();
            save();
        }
    }

    public static void save() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            YAML.dump(cachedConfig, writer);
            lastModified = configFile.lastModified();
            DonateIntegrate.LOGGER.debug("Сохранена конфигурация: token={}, userId={}",
                    maskToken(cachedConfig.getDonpayToken()), cachedConfig.getUserId());
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Ошибка сохранения конфигурации: {}", e.getMessage());
        }
    }

    public static void checkAndReloadConfig() {
        try {
            if (configFile.lastModified() > lastModified) {
                DonateIntegrate.LOGGER.info("Конфигурация изменена, перезагрузка...");
                load();
            }
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Ошибка проверки конфигурации: {}", e.getMessage());
        }
    }

    public static ModConfig getConfig() {
        if (cachedConfig == null) {
            load();
        }
        return cachedConfig;
    }

    private static boolean isValid(ModConfig config) {
        try {
            List<Action> actions = config.getActions();
            if (actions == null || actions.isEmpty()) {
                DonateIntegrate.LOGGER.warn("Действия не настроены");
                return false;
            }
            for (Action action : actions) {
                if (action.getSum() <= 0) {
                    DonateIntegrate.LOGGER.warn("Некорректная сумма: {}", action.getSum());
                    return false;
                }
                if (action.getCommands().isEmpty()) {
                    DonateIntegrate.LOGGER.warn("Нет команд для суммы: {}", action.getSum());
                    return false;
                }
                for (String command : action.getCommands()) {
                    if (command == null || command.trim().isEmpty()) {
                        DonateIntegrate.LOGGER.warn("Некорректная команда для суммы: {}", action.getSum());
                        return false;
                    }
                }
                if (action.getPriority() < 0) {
                    DonateIntegrate.LOGGER.warn("Некорректный приоритет для суммы: {}", action.getSum());
                    return false;
                }
            }
            if (config.getDonpayToken() == null || config.getDonpayToken().isEmpty()) {
                DonateIntegrate.LOGGER.warn("Токен DonatePay не установлен");
                return false;
            }
            if (config.getUserId() == null || config.getUserId().isEmpty()) {
                DonateIntegrate.LOGGER.warn("User ID не установлен");
                return false;
            }
            return true;
        } catch (Exception e) {
            DonateIntegrate.LOGGER.error("Ошибка валидации конфигурации: {}", e.getMessage());
            return false;
        }
    }

    private static String maskToken(String token) {
        if (token == null || token.length() <= 10) return token != null ? token : "<null>";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}