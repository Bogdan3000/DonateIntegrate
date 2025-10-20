package com.bogdan3000.dintegrate.client.gui;

import com.bogdan3000.dintegrate.Config;
import net.minecraft.client.gui.GuiGraphics;

public class TabCommands extends TabBase {
    private final Config config;

    public TabCommands(DonateIntegrateScreen parent, Config config) {
        super(parent);
        this.config = config;
    }

    @Override
    public void addWidgets() {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.drawCenteredString(mc.font, "Commands Tab — список триггеров", centerX(), 45, 0xFFD580);
    }
}