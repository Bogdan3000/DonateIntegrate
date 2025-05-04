package com.bogdan3000.dintegrate.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;

import java.io.IOException;

/**
 * Minimalist popup GUI for success or error messages.
 */
public class MessageGui extends GuiScreen {
    private static final int CONTENT_WIDTH = 200;
    private static final int CONTENT_HEIGHT = 100;
    private final GuiScreen parent;
    private final String message;
    private final boolean success;
    private int contentLeft, contentTop;
    private float fadeAnimation = 0.0f;

    public MessageGui(GuiScreen parent, String message, boolean success) {
        this.parent = parent;
        this.message = message;
        this.success = success;
    }

    @Override
    public void initGui() {
        contentLeft = (width - CONTENT_WIDTH) / 2;
        contentTop = (height - CONTENT_HEIGHT) / 2;
        buttonList.clear();
        buttonList.add(new CustomButton(0, contentLeft + CONTENT_WIDTH / 2 - 50, contentTop + CONTENT_HEIGHT - 40, 100, 24, "OK"));
        fadeAnimation = 0.0f;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (fadeAnimation < 1.0f) {
            fadeAnimation = Math.min(1.0f, fadeAnimation + 0.05f);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, fadeAnimation);
        GuiRenderUtils.drawOverlay(width, height);
        GuiRenderUtils.drawRoundedRect(contentLeft, contentTop, CONTENT_WIDTH, CONTENT_HEIGHT, 8, 0xFF263238);
        drawCenteredString(fontRenderer, message, width / 2, contentTop + CONTENT_HEIGHT / 2 - 10,
                success ? 0xFF4CAF50 : GuiRenderUtils.getErrorColor());
        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static class CustomButton extends GuiButton {
        private float hoverAnimation = 0.0f;
        private boolean wasHovered = false;

        public CustomButton(int buttonId, int x, int y, int width, int height, String buttonText) {
            super(buttonId, x, y, width, height, buttonText);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!visible) return;

            boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            if (hovered && !wasHovered) hoverAnimation = 0.0f;
            if (!hovered && wasHovered) hoverAnimation = 1.0f;
            hoverAnimation = hovered ? Math.min(1.0f, hoverAnimation + partialTicks * 0.2f) :
                    Math.max(0.0f, hoverAnimation - partialTicks * 0.2f);
            wasHovered = hovered;

            GlStateManager.pushMatrix();
            GuiRenderUtils.drawButton(x, y, width, height, hovered, enabled, hoverAnimation);
            drawCenteredString(mc.fontRenderer, displayString, x + width / 2, y + (height - 8) / 2, GuiRenderUtils.getTextColor());
            GlStateManager.popMatrix();
        }
    }
}