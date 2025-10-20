package com.bogdan3000.dintegrate.client.gui;

import com.bogdan3000.dintegrate.Config;
import com.bogdan3000.dintegrate.donation.DonatePayProvider;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class TabConfig extends TabBase {
    private final Config config;
    private final DonatePayProvider provider;

    public TabConfig(DonateIntegrateScreen parent, Config config, DonatePayProvider provider) {
        super(parent);
        this.config = config;
        this.provider = provider;
    }

    @Override
    public void addWidgets() {
        int y = 60;
        int x = centerX() - 60;

        Button start = Button.builder(Component.literal("Start"), b -> {
            parent.getMinecraft().player.displayClientMessage(Component.literal("§aStarted connection"), false);
        }).bounds(x - 70, y, 60, 20).build();

        Button stop = Button.builder(Component.literal("Stop"), b -> {
            parent.getMinecraft().player.displayClientMessage(Component.literal("§cStopped connection"), false);
        }).bounds(x, y, 60, 20).build();

        Button restart = Button.builder(Component.literal("Restart"), b -> {
            parent.getMinecraft().player.displayClientMessage(Component.literal("§bRestarted connection"), false);
        }).bounds(x + 70, y, 80, 20).build();

        parent.addChildWidget(start);
        parent.addChildWidget(stop);
        parent.addChildWidget(restart);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.drawCenteredString(mc.font, "Config Tab — токен, юзер, кнопки", centerX(), 45, 0xAAAAFF);
    }
}