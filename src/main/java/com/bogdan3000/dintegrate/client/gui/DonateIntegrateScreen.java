package com.bogdan3000.dintegrate.client.gui;

import com.bogdan3000.dintegrate.Config;
import com.bogdan3000.dintegrate.donation.DonatePayProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DonateIntegrateScreen extends Screen {
    private final Config config;
    private EditBox tokenBox;
    private EditBox userIdBox;
    private Button saveButton;
    private Button reconnectButton;
    private Button testButton;

    private boolean connected = false;
    private final DonatePayProvider provider;

    public DonateIntegrateScreen(Config config, DonatePayProvider provider, boolean connected) {
        super(Component.literal("DonateIntegrate Control Panel"));
        this.config = config;
        this.provider = provider;
        this.connected = connected;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 4 + 40;

        tokenBox = new EditBox(this.font, centerX - 100, y, 200, 20, Component.literal("Token"));
        tokenBox.setValue(config.getToken());
        this.addRenderableWidget(tokenBox);

        y += 25;
        userIdBox = new EditBox(this.font, centerX - 100, y, 200, 20, Component.literal("User ID"));
        userIdBox.setValue(String.valueOf(config.getUserId()));
        this.addRenderableWidget(userIdBox);

        y += 30;
        saveButton = Button.builder(Component.literal("Сохранить"), btn -> {
            configSave();
        }).bounds(centerX - 100, y, 95, 20).build();
        this.addRenderableWidget(saveButton);

        reconnectButton = Button.builder(Component.literal("Переподключить"), btn -> {
            reconnect();
        }).bounds(centerX + 5, y, 95, 20).build();
        this.addRenderableWidget(reconnectButton);

        y += 30;
        testButton = Button.builder(Component.literal("Тест доната"), btn -> {
            simulateDonation();
        }).bounds(centerX - 50, y, 100, 20).build();
        this.addRenderableWidget(testButton);
    }

    private void configSave() {
        try {
            var fieldToken = config.getClass().getDeclaredField("token");
            var fieldUser = config.getClass().getDeclaredField("userId");
            fieldToken.setAccessible(true);
            fieldUser.setAccessible(true);
            fieldToken.set(config, tokenBox.getValue());
            fieldUser.set(config, Integer.parseInt(userIdBox.getValue()));
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§a[DIntegrate] Конфиг обновлён!"));
        } catch (Exception e) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§cОшибка сохранения конфига!"));
            e.printStackTrace();
        }
    }

    private void reconnect() {
        if (provider != null) {
            provider.disconnect();
            provider.connect();
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§e[DIntegrate] Переподключение к DonatePay..."));
        }
    }

    private void simulateDonation() {
        Minecraft mc = Minecraft.getInstance();
        mc.player.sendSystemMessage(Component.literal("§d[DIntegrate] Симуляция доната (50 RUB, тест)"));
        if (mc.player.connection != null) {
            mc.player.connection.sendChat("/dpi test Tester 50 Test donation");
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, "DonateIntegrate Control Panel", this.width / 2, 20, 0xFFFFFF);

        String status = connected ? "§aПодключено" : "§cОтключено";
        graphics.drawCenteredString(this.font, "Статус: " + status, this.width / 2, 40, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}