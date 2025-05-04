package com.bogdan3000.dintegrate.gui;

import net.minecraft.client.gui.GuiListExtended;

import java.util.ArrayList;
import java.util.List;

public class DonationHistoryList extends GuiListExtended {
    private final DonateIntegrateGui parent;
    private final List<String> entries = new ArrayList<>();
    private int selectedIndex = -1;

    public DonationHistoryList(DonateIntegrateGui parent, int x, int y, int width, int height) {
        super(parent.getMinecraft(), width, height, y, y + height, 20);
        this.parent = parent;
        this.setHasListHeader(false, 0);
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

    private class HistoryEntry implements IGuiListEntry {
        private final int index;

        public HistoryEntry(int index) {
            this.index = index;
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            String text = entries.get(index);
            parent.getMinecraft().fontRenderer.drawString(text, x, y + 5, isSelected ? 0xFFFF00 : 0xFFFFFF);
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
            selectedIndex = slotIndex;
            return true;
        }

        @Override
        public void mouseReleased(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {
        }

        @Override
        public void updatePosition(int slotIndex, int x, int y, float partialTicks) {
        }
    }
}