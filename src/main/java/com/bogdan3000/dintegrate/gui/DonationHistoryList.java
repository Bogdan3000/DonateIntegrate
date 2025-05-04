package com.bogdan3000.dintegrate.gui;

import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.renderer.GlStateManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable donation history list with a minimalist design.
 */
public class DonationHistoryList extends GuiListExtended {
    private final DonateIntegrateGui parent;
    private final List<String> entries = new ArrayList<>();
    private int selectedIndex = -1;

    public DonationHistoryList(DonateIntegrateGui parent, int x, int y, int width, int height) {
        super(parent.getMinecraft(), width, height, y, y + height, 24);
        this.parent = parent;
        this.left = x;
        this.right = x + width;
    }

    public void setEntries(List<String> entries) {
        this.entries.clear();
        this.entries.addAll(entries);
    }

    @Override
    protected int getSize() {
        return entries.size();
    }

    @Override
    public IGuiListEntry getListEntry(int index) {
        return new HistoryEntry(index);
    }

    @Override
    protected boolean isSelected(int slotIndex) {
        return slotIndex == selectedIndex;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (visible && mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom) {
            selectedIndex = getSlotIndexFromScreenCoords(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private class HistoryEntry implements IGuiListEntry {
        private final int index;
        private float hoverAnimation = 0.0f;
        private boolean wasHovered = false;

        public HistoryEntry(int index) {
            this.index = index;
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            String text = entries.get(index);

            boolean hovered = mouseX >= x && mouseX < x + listWidth && mouseY >= y && mouseY < y + slotHeight;
            if (hovered && !wasHovered) hoverAnimation = 0.0f;
            if (!hovered && wasHovered) hoverAnimation = 1.0f;
            hoverAnimation = hovered ? Math.min(1.0f, hoverAnimation + partialTicks * 0.2f) :
                    Math.max(0.0f, hoverAnimation - partialTicks * 0.2f);
            wasHovered = hovered;

            if (isSelected || hovered) {
                GuiRenderUtils.drawRoundedRect(x, y, listWidth - 4, slotHeight, 4, GuiRenderUtils.mixColors(0xFF37474F, 0xFF546E7A, hoverAnimation));
            }
            parent.getMinecraft().fontRenderer.drawString(text, x + 5, y + (slotHeight - parent.getMinecraft().fontRenderer.FONT_HEIGHT) / 2,
                    isSelected ? 0xFFFF4081 : GuiRenderUtils.getTextColor());
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
            selectedIndex = slotIndex;
            return true;
        }

        @Override
        public void mouseReleased(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {}

        @Override
        public void updatePosition(int slotIndex, int x, int y, float partialTicks) {}
    }
}