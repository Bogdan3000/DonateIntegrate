package com.bogdan3000.dintegrate.gui;

import com.bogdan3000.dintegrate.config.Action;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.renderer.GlStateManager;

import static net.minecraft.client.gui.Gui.drawRect;

public class ActionList extends GuiListExtended {
    private final DonateIntegrateGui parent;
    private final java.util.List<Action> actions;
    private int selectedIndex = -1;

    public ActionList(DonateIntegrateGui parent, int x, int y, int width, int height) {
        super(parent.getMinecraft(), width, height, y, y + height, 20);
        this.parent = parent;
        this.actions = ConfigHandler.getConfig().getActions();
        this.setHasListHeader(false, 0);
        this.left = x;
        this.right = x + width;
    }
    @Override
    protected int getSize() {
        return actions.size();
    }

    @Override
    public IGuiListEntry getListEntry(int index) {
        return new ActionEntry(index);
    }

    @Override
    protected boolean isSelected(int slotIndex) {
        return slotIndex == selectedIndex;
    }

    public Action getSelectedAction() {
        return selectedIndex >= 0 && selectedIndex < actions.size() ? actions.get(selectedIndex) : null;
    }

    public void refreshList() {
        selectedIndex = -1;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    private class ActionEntry implements IGuiListEntry {
        private final int index;

        public ActionEntry(int index) {
            this.index = index;
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            Action action = actions.get(index);
            String text = String.format("Sum: %.2f, Enabled: %s, Commands: %d, Mode: %s",
                    action.getSum(), action.isEnabled() ? "Yes" : "No", action.getCommands().size(), action.getExecutionMode());

            // Draw background for selected entry
            if (isSelected) {
                GlStateManager.pushMatrix();
                drawRect(x, y, x + listWidth - 4, y + slotHeight, 0xFF555555);
                GlStateManager.popMatrix();
            }

            // Draw the text with proper alignment
            parent.getMinecraft().fontRenderer.drawString(text, x + 5, y + (slotHeight - parent.getMinecraft().fontRenderer.FONT_HEIGHT) / 2, isSelected ? 0xFFFF00 : 0xFFFFFF);
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

    @Override
    protected int getScrollBarX() {
        return right - 6;
    }


}