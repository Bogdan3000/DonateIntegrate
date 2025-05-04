package com.bogdan3000.dintegrate.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiListExtended;

import java.util.List;

/**
 * Custom list for displaying donation history in the GUI.
 */
public class DonationHistoryList extends GuiListExtended {
    private final DonateIntegrateGui parent;
    private List<String> entries;
    public boolean visible;

    public DonationHistoryList(DonateIntegrateGui parent, int x, int y, int width, int height) {
        super(Minecraft.getMinecraft(), width, height, y, y + height, 20);
        this.parent = parent;
        this.left = x;
        this.entries = new java.util.ArrayList<>();
        this.visible = true;
    }

    @Override
    public IGuiListEntry getListEntry(int index) {
        return new HistoryEntry(entries.get(index));
    }

    @Override
    protected int getSize() {
        return entries.size();
    }

    @Override
    protected void drawBackground() {
        // Черный фон
        Gui.drawRect(left, top, left + width, top + height, 0xFF000000);
    }

    public void setEntries(List<String> newEntries) {
        this.entries = newEntries;
    }

    private class HistoryEntry implements IGuiListEntry {
        private final String entry;

        public HistoryEntry(String entry) {
            this.entry = entry;
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            mc.fontRenderer.drawString(entry, x, y, 0xFFFFFF);
        }

        @Override
        public void updatePosition(int slotIndex, int x, int y, float partialTicks) {
        }

        @Override
        public boolean mousePressed(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {
            return false;
        }

        @Override
        public void mouseReleased(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {
        }
    }
}