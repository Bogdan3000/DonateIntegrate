package com.bogdan3000.dintegrate.client.gui;

import net.minecraft.client.gui.GuiGraphics;

public class TabMisc extends TabBase {
    public TabMisc(DonateIntegrateScreen parent) {
        super(parent);
    }

    @Override
    public void addWidgets() {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.drawCenteredString(mc.font, "Misc Tab — доп. функции", centerX(), 45, 0xDDDDDD);
    }
}