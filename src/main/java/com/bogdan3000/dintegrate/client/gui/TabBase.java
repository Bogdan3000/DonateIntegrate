package com.bogdan3000.dintegrate.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Базовый класс вкладки.
 */
public abstract class TabBase {

    protected final String title;
    public final List<AbstractWidget> widgets = new ArrayList<>();

    protected Minecraft mc;
    protected int width, height;

    public TabBase(String title) {
        this.title = title;
    }

    public void init(Minecraft mc, int width, int height) {
        this.mc = mc;
        this.width = width;
        this.height = height;
        this.widgets.clear();
    }

    protected void addWidget(AbstractWidget w) {
        this.widgets.add(w);
    }

    protected int centerX() { return width / 2; }
    protected int centerY() { return height / 2; }

    public void tick() {
        for (AbstractWidget w : widgets) {
            if (w instanceof EditBox eb) {
                eb.tick();
            }
        }
    }

    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        for (AbstractWidget w : widgets) {
            w.render(gfx, mouseX, mouseY, partialTicks);
        }
    }

    public void onClose() { }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }
}