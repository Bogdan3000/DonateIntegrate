package com.bogdan3000.dintegrate.client.gui;

import com.bogdan3000.dintegrate.Config;
import com.bogdan3000.dintegrate.DonateIntegrate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TabConfig extends TabBase {

    private EditBox tokenBox;
    private EditBox userIdBox;
    private Button saveButton, startButton, stopButton, restartButton, toggleTokenButton;

    private boolean tokenVisible = false;
    private String connectionStatus = "Unknown";
    private int connectionColor = 0xFFFFAA00;
    private int connectionY;

    private final Config config;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public TabConfig(Config config) {
        super("Config");
        this.config = config;
    }

    @Override
    public void init(Minecraft mc, int width, int height) {
        super.init(mc, width, height);
        Font font = mc.font;

        int cx = width / 2;
        int y = Math.max(40, height / 6);

        // === TOKEN ===
        tokenBox = new EditBox(font, cx - 120, y, 190, 20, Component.literal("Token"));
        tokenBox.setMaxLength(2048);
        tokenBox.setValue(obfuscateToken(config.getToken()));
        tokenBox.setEditable(false);
        addWidget(tokenBox);

        toggleTokenButton = Button.builder(Component.literal("Edit"), b -> toggleTokenVisibility())
                .bounds(cx + 75, y, 60, 20).build();
        addWidget(toggleTokenButton);

        // === USER ID ===
        y += 35;
        userIdBox = new EditBox(font, cx - 120, y, 240, 20, Component.literal("User ID"));
        userIdBox.setMaxLength(16);
        userIdBox.setValue(String.valueOf(config.getUserId()));
        addWidget(userIdBox);

        // === SAVE ===
        y += 35;
        saveButton = Button.builder(Component.literal("Save Changes"), b -> saveConfig())
                .bounds(cx - 80, y, 160, 20).build();
        addWidget(saveButton);

        // === Connection status (под полями) ===
        y += 25;
        connectionY = y;

        // === Start/Stop/Restart ===
        y += 25;
        int bw = 90;
        startButton = Button.builder(Component.literal("Start"), b -> handleStart())
                .bounds(cx - bw - 60, y, bw, 20).build();
        stopButton = Button.builder(Component.literal("Stop"), b -> handleStop())
                .bounds(cx - bw / 2, y, bw, 20).build();
        restartButton = Button.builder(Component.literal("Restart"), b -> handleRestart())
                .bounds(cx + bw / 2 + 20, y, bw, 20).build();
        addWidget(startButton);
        addWidget(stopButton);
        addWidget(restartButton);

        updateConnectionStatus();
    }

    @Override
    public void tick() {
        super.tick();
        updateConnectionStatus();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        gfx.fill(0, 0, width, height, 0xAA000000);

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        gfx.drawCenteredString(font, "Connection: " + connectionStatus, centerX(), connectionY, connectionColor);

        super.render(gfx, mouseX, mouseY, partialTicks);
    }

    private void updateConnectionStatus() {
        var provider = DonateIntegrate.getDonateProvider();

        if (provider == null) {
            connectionStatus = "Disconnected";
            connectionColor = 0xFFFF5555;
            return;
        }

        if (provider.isConnected()) {
            connectionStatus = "Connected";
            connectionColor = 0xFF00FF00;
        } else {
            connectionStatus = "Disconnected";
            connectionColor = 0xFFFF5555;
        }
    }

    private void handleStart() {
        connectionStatus = "Connecting...";
        connectionColor = 0xFFFFAA00; // жёлтый
        DonateIntegrate.sendClientMessage("§eConnecting...");

        DonateIntegrate.startConnection();

        // через 1 секунду проверяем подключение ещё раз
        scheduler.schedule(() -> Minecraft.getInstance().execute(this::updateConnectionStatus),
                1, TimeUnit.SECONDS);
    }

    private void handleStop() {
        DonateIntegrate.stopConnection();
        DonateIntegrate.sendClientMessage("§cConnection stopped.");
        updateConnectionStatus();
    }

    private void handleRestart() {
        connectionStatus = "Connecting...";
        connectionColor = 0xFFFFAA00;
        DonateIntegrate.sendClientMessage("§eReconnecting...");

        DonateIntegrate.restartConnection();

        scheduler.schedule(() -> Minecraft.getInstance().execute(this::updateConnectionStatus),
                1, TimeUnit.SECONDS);
    }

    private void toggleTokenVisibility() {
        tokenVisible = !tokenVisible;
        if (tokenVisible) {
            tokenBox.setEditable(true);
            tokenBox.setValue(config.getToken());
            tokenBox.setCursorPosition(tokenBox.getValue().length());
            toggleTokenButton.setMessage(Component.literal("Hide"));
        } else {
            tokenBox.setEditable(false);
            tokenBox.setValue(obfuscateToken(config.getToken()));
            toggleTokenButton.setMessage(Component.literal("Edit"));
        }
    }

    private void saveConfig() {
        String tokenInput = tokenVisible ? tokenBox.getValue().trim() : config.getToken();
        String userStr = userIdBox.getValue().trim();

        if (tokenInput == null || tokenInput.isEmpty()) {
            DonateIntegrate.sendClientMessage("§cToken cannot be empty!");
            return;
        }

        try {
            int userId = Integer.parseInt(userStr);
            DonateIntegrate.saveToJsonConfig("token", tokenInput);
            DonateIntegrate.saveToJsonConfig("user_id", String.valueOf(userId));
            config.token = tokenInput;
            config.user_id = userId;

            if (tokenVisible) toggleTokenVisibility();

            DonateIntegrate.sendClientMessage("§aConfig saved and connection restarted!");
            connectionStatus = "Connecting...";
            connectionColor = 0xFFFFAA00;
            DonateIntegrate.restartConnection();

            scheduler.schedule(() -> Minecraft.getInstance().execute(this::updateConnectionStatus),
                    1, TimeUnit.SECONDS);

        } catch (NumberFormatException e) {
            DonateIntegrate.sendClientMessage("§cInvalid User ID format!");
        }
    }

    private String obfuscateToken(String token) {
        if (token == null || token.isBlank()) return "";
        if (token.length() <= 4) return "*".repeat(token.length());
        return token.substring(0, 2) + "*".repeat(token.length() - 4) + token.substring(token.length() - 2);
    }
}