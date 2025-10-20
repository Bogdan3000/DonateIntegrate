package com.bogdan3000.dintegrate.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public abstract class TabBase {
    protected final DonateIntegrateScreen parent;
    protected Minecraft mc;
    protected int width, height;

    public TabBase(DonateIntegrateScreen parent) {
        this.parent = parent;
    }

    public void init(Minecraft mc, int width, int height) {
        this.mc = mc;
        this.width = width;
        this.height = height;
    }

    public abstract void addWidgets();

    public abstract void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks);

    protected int centerX() {
        return width / 2;
    }
}