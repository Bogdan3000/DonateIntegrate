package com.bogdan3000.dintegrate.client.gui;

import net.minecraft.client.gui.GuiGraphics;

public class TabInfo extends TabBase {
    public TabInfo(DonateIntegrateScreen parent) {
        super(parent);
    }

    @Override
    public void addWidgets() {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.drawCenteredString(mc.font, "Info Tab — справка по модулю", centerX(), 45, 0x99FF99);
    }
}