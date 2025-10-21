package com.bogdan3000.dintegrate.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public abstract class TabBase {
    protected int width;
    protected int height;
    protected Minecraft minecraft;
    protected final List<AbstractWidget> widgets = new ArrayList<>();
    protected final String title;

    public TabBase(String title) {
        this.title = title;
    }

    /** Вызывается экраном при переключении на вкладку */
    public void init(Minecraft mc, int width, int height) {
        this.minecraft = mc;
        this.width = width;
        this.height = height;
        this.widgets.clear();
    }

    /** Добавление контролов вкладки (кнопки, инпуты) */
    public void addWidget(AbstractWidget widget) {
        this.widgets.add(widget);
    }

    /** Отрисовка вкладки */
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        for (AbstractWidget w : widgets) {
            w.render(gfx, mouseX, mouseY, partialTicks);
        }
    }

    /** Опциональный тик вкладки (пока пустой, чтобы не требовать tick() у виджетов) */
    public void tick() {
        // Ничего не делаем. Если нужно — переопределяй во вкладке.
    }

    public List<AbstractWidget> getWidgets() {
        return widgets;
    }

    public Component getTitle() {
        return Component.literal(title);
    }

    protected int centerX() { return width / 2; }
    protected int centerY() { return height / 2; }
}