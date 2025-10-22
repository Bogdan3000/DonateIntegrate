package com.bogdan3000.dintegrate.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class LabelWidget extends AbstractWidget {
    private final String text;

    public LabelWidget(int centerX, int y, String text) {
        super(centerX - 100, y, 200, 20, Component.literal(text));
        this.text = text;
        this.active = false; // надпись не интерактивна
    }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        gfx.drawCenteredString(Minecraft.getInstance().font, text, this.getX() + this.width / 2, this.getY(), 0xFFFFFF);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        // без озвучки — надпись не интерактивна
    }
}