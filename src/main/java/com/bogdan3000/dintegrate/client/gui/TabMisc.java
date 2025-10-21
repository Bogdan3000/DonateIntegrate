package com.bogdan3000.dintegrate.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class TabMisc extends TabBase {

    public TabMisc() {
        super("Misc");
    }

    @Override
    public void init(Minecraft mc, int width, int height) {
        super.init(mc, width, height);
        // Здесь можно разместить прочие кнопки/опции (напр., hotkeys)
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.fill(0, 0, width, height, 0xAA000000);
        graphics.drawCenteredString(Minecraft.getInstance().font, "Misc Tab — доп. функции", centerX(), 45, 0xDDDDDD);
        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}