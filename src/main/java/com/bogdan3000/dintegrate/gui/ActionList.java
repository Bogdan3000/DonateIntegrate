package com.bogdan3000.dintegrate.gui;

import com.bogdan3000.dintegrate.config.Action;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.renderer.GlStateManager;

import java.util.List;

/**
 * Scrollable list of actions with a minimalist design.
 */
public class ActionList extends GuiListExtended {
    private final DonateIntegrateGui parent;
    private final List<Action> actions;
    private int selectedIndex = -1;

    public ActionList(DonateIntegrateGui parent, int x, int y, int width, int height) {
        super(parent.getMinecraft(), width, height, y, y + height, 24);
        this.parent = parent;
        this.actions = ConfigHandler.getConfig().getActions();
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

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (visible && mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom) {
            selectedIndex = getSlotIndexFromScreenCoords(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private class ActionEntry implements IGuiListEntry {
        private final int index;
        private float hoverAnimation = 0.0f;
        private boolean wasHovered = false;

        public ActionEntry(int index) {
            this.index = index;
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            Action action = actions.get(index);
            String text = String.format("Sum: %.2f, Enabled: %s, Commands: %d, Mode: %s",
                    action.getSum(), action.isEnabled() ? "Yes" : "No", action.getCommands().size(), action.getExecutionMode());

            boolean hovered = mouseX >= x && mouseX < x + listWidth && mouseY >= y && mouseY < y + slotHeight;
            if (hovered && !wasHovered) hoverAnimation = 0.0f;
            if (!hovered && wasHovered) hoverAnimation = 1.0f;
            hoverAnimation = hovered ? Math.min(1.0f, hoverAnimation + partialTicks * 0.2f) :
                    Math.max(0.0f, hoverAnimation - partialTicks * 0.2f);
            wasHovered = hovered;

            GlStateManager.pushMatrix();
            if (isSelected || hovered) {
                GuiRenderUtils.drawRoundedRect(x, y, listWidth - 4, slotHeight, 4, GuiRenderUtils.mixColors(0xFF37474F, 0xFF546E7A, hoverAnimation));
            }
            parent.getMinecraft().fontRenderer.drawString(text, x + 5, y + (slotHeight - parent.getMinecraft().fontRenderer.FONT_HEIGHT) / 2,
                    isSelected ? 0xFFFF4081 : GuiRenderUtils.getTextColor());
            GlStateManager.popMatrix();
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

    @Override
    protected int getScrollBarX() {
        return right - 6;
    }
}