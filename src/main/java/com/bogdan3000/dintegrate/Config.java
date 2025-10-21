package com.bogdan3000.dintegrate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path CONFIG_PATH = Paths.get("config", "dintegrate.json");

    public String token = "YOUR_DONATEPAY_TOKEN";
    public int user_id = 0;
    public String token_url = "https://donatepay.ru/api/v2/socket/token";
    public String socket_url = "wss://centrifugo.donatepay.ru/connection/websocket?format=json";
    public List<DonationRule> rules = new ArrayList<>();

    public static class DonationRule {
        public double amount;
        public String mode;
        public List<String> commands = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public void load() throws IOException {
        if (!Files.exists(CONFIG_PATH)) {
            LOGGER.warn("[DIntegrate] JSON config not found — creating default one");
            saveDefault();
        }

        try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
            Config loaded = GSON.fromJson(reader, Config.class);
            if (loaded != null) {
                this.token = loaded.token;
                this.user_id = loaded.user_id;
                this.token_url = loaded.token_url;
                this.socket_url = loaded.socket_url;
                this.rules = loaded.rules != null ? loaded.rules : new ArrayList<>();
            }
            LOGGER.info("[DIntegrate] JSON config loaded — rules: {}", rules.size());
            for (DonationRule r : rules) {
                LOGGER.info("[DIntegrate] Rule {}₽ → {} commands (mode={})",
                        r.amount, r.commands.size(), r.mode);
            }
        } catch (JsonSyntaxException e) {
            LOGGER.error("[DIntegrate] Failed to parse JSON config: {}", e.toString());
            throw new IOException("Invalid JSON syntax in dintegrate.json", e);
        }
    }

    private void saveDefault() throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());

        Config def = new Config();
        DonationRule r1 = new DonationRule();
        r1.amount = 10;
        r1.mode = "all";
        r1.commands = List.of(
                "say Спасибо, {name}, за {sum} рублей!",
                "delay 1.5",
                "say Вот тебе подарок!",
                "/give @p minecraft:iron_ingot 3"
        );
        def.rules = List.of(r1);

        try (FileWriter w = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(def, w);
        }

        LOGGER.info("[DIntegrate] Default JSON config created at {}", CONFIG_PATH.toAbsolutePath());
    }

    public Map<Double, DonationRule> getRules() {
        Map<Double, DonationRule> map = new HashMap<>();
        for (DonationRule r : rules) {
            map.put(r.amount, r);
        }
        return map;
    }

    public String getToken() { return token; }
    public int getUserId() { return user_id; }
    public String getTokenUrl() { return token_url; }
    public String getSocketUrl() { return socket_url; }
}