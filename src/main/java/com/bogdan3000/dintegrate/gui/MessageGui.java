package com.bogdan3000.dintegrate.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

public class MessageGui extends GuiScreen {
    private final GuiScreen parent;
    private final String message;
    private final boolean success;

    public MessageGui(GuiScreen parent, String message, boolean success) {
        this.parent = parent;
        this.message = message;
        this.success = success;
    }

    @Override
    public void initGui() {
        buttonList.add(new GuiButton(0, width / 2 - 50, height / 2 + 20, 100, 20, "OK"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, message, width / 2, height / 2 - 10, success ? 0x00FF00 : 0xFF0000);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}