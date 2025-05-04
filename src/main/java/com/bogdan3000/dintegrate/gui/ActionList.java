package com.bogdan3000.dintegrate.gui;

import com.bogdan3000.dintegrate.config.ConfigHandler;
import com.bogdan3000.dintegrate.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

/**
 * Custom list for displaying actions in the GUI.
 */
public class ActionList extends GuiListExtended {
    private final DonateIntegrateGui parent;
    private final List<com.bogdan3000.dintegrate.config.Action> actions;
    public boolean visible;

    public ActionList(DonateIntegrateGui parent, int x, int y, int width, int height) {
        super(Minecraft.getMinecraft(), width, height, y, y + height, 20);
        this.parent = parent;
        this.actions = ConfigHandler.getConfig().getActions();
        this.left = x;
        this.visible = true;
    }

    @Override
    public IGuiListEntry getListEntry(int index) {
        return new ActionEntry(actions.get(index));
    }

    @Override
    protected int getSize() {
        return actions.size();
    }

    @Override
    protected void drawBackground() {
        // Ensure black background
        Gui.drawRect(left, top, left + width, top + height, 0xFF000000);
    }

    public com.bogdan3000.dintegrate.config.Action getSelectedAction() {
        int selected = selectedElement;
        return selected >= 0 && selected < actions.size() ? actions.get(selected) : null;
    }

    public void refreshList() {
        actions.clear();
        actions.addAll(ConfigHandler.getConfig().getActions());
    }

    private class ActionEntry implements IGuiListEntry {
        private final com.bogdan3000.dintegrate.config.Action action;

        public ActionEntry(com.bogdan3000.dintegrate.config.Action action) {
            this.action = action;
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            String text = String.format("Sum: %.2f, Enabled: %s, Commands: %d, Mode: %s, Priority: %d",
                    action.getSum(),
                    action.isEnabled() ? TextFormatting.GREEN + "Yes" : TextFormatting.RED + "No",
                    action.getCommands().size(),
                    action.getExecutionMode(),
                    action.getPriority());
            mc.fontRenderer.drawString(text, x, y, 0xFFFFFF);
        }

        @Override
        public void updatePosition(int slotIndex, int x, int y, float partialTicks) {
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
            ActionList.this.selectedElement = slotIndex;
            return true;
        }

        @Override
        public void mouseReleased(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {
        }
    }
}