package com.bogdan3000.dintegrate.gui;

import com.bogdan3000.dintegrate.config.Action;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI for adding or editing actions with a full-screen, minimalist form layout.
 */
public class ActionEditGui extends GuiScreen {
    private static final int CONTENT_WIDTH = 400;
    private static final int CONTENT_HEIGHT = 300;
    private final DonateIntegrateGui parent;
    private final Action action;
    private final boolean isNewAction;
    private GuiTextField sumField;
    private GuiTextField priorityField;
    private CustomButton enabledButton;
    private CustomButton modeButton;
    private CustomButton saveButton;
    private CustomButton cancelButton;
    private CustomButton addCommandButton;
    private CustomButton removeCommandButton;
    private CommandList commandList;
    private int contentLeft, contentTop;
    private float fadeAnimation = 0.0f;

    public ActionEditGui(DonateIntegrateGui parent, Action action) {
        this.parent = parent;
        this.action = action != null ? action : new Action();
        this.isNewAction = action == null;
    }

    @Override
    public void initGui() {
        contentLeft = (width - CONTENT_WIDTH) / 2;
        contentTop = (height - CONTENT_HEIGHT) / 2;
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        fadeAnimation = 0.0f;

        sumField = new GuiTextField(0, fontRenderer, contentLeft + 20, contentTop + 60, CONTENT_WIDTH - 40, 20);
        sumField.setText(action.getSum() > 0 ? String.valueOf(action.getSum()) : "");
        sumField.setValidator(s -> s.matches("\\d*\\.?\\d*")); // Allow float numbers
        priorityField = new GuiTextField(1, fontRenderer, contentLeft + 20, contentTop + 110, CONTENT_WIDTH - 40, 20);
        priorityField.setText(String.valueOf(action.getPriority()));
        priorityField.setValidator(s -> s.matches("\\d*")); // Allow integers only

        commandList = new CommandList(this, contentLeft + 20, contentTop + 150, CONTENT_WIDTH - 40, 100, action.getCommands());

        enabledButton = new CustomButton(2, contentLeft + 20, contentTop + CONTENT_HEIGHT - 60, 80, 24, "Enabled: " + (action.isEnabled() ? "Yes" : "No"));
        modeButton = new CustomButton(3, contentLeft + 110, contentTop + CONTENT_HEIGHT - 60, 80, 24, "Mode: " + action.getExecutionMode());
        saveButton = new CustomButton(4, contentLeft + 20, contentTop + CONTENT_HEIGHT - 30, 80, 24, "Save");
        cancelButton = new CustomButton(5, contentLeft + CONTENT_WIDTH - 100, contentTop + CONTENT_HEIGHT - 30, 80, 24, "Cancel");
        addCommandButton = new CustomButton(6, contentLeft + CONTENT_WIDTH - 40, contentTop + 130, 24, 24, "+");
        removeCommandButton = new CustomButton(7, contentLeft + CONTENT_WIDTH - 40, contentTop + 160, 24, 24, "−");

        buttonList.add(enabledButton);
        buttonList.add(modeButton);
        buttonList.add(saveButton);
        buttonList.add(cancelButton);
        buttonList.add(addCommandButton);
        buttonList.add(removeCommandButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 2: // Toggle Enabled
                action.setEnabled(!action.isEnabled());
                enabledButton.displayString = "Enabled: " + (action.isEnabled() ? "Yes" : "No");
                break;
            case 3: // Toggle Mode
                action.setExecutionMode(action.getExecutionMode() == Action.ExecutionMode.ALL ? Action.ExecutionMode.RANDOM_ONE : Action.ExecutionMode.ALL);
                modeButton.displayString = "Mode: " + action.getExecutionMode();
                break;
            case 4: // Save
                try {
                    String sumText = sumField.getText().trim();
                    if (sumText.isEmpty()) {
                        mc.displayGuiScreen(new MessageGui(parent, "Sum is required!", false));
                        return;
                    }
                    float sum = Float.parseFloat(sumText);
                    if (sum <= 0) {
                        mc.displayGuiScreen(new MessageGui(parent, "Sum must be positive!", false));
                        return;
                    }
                    String priorityText = priorityField.getText().trim();
                    if (priorityText.isEmpty()) {
                        mc.displayGuiScreen(new MessageGui(parent, "Priority is required!", false));
                        return;
                    }
                    int priority = Integer.parseInt(priorityText);
                    if (priority < 0) {
                        mc.displayGuiScreen(new MessageGui(parent, "Priority cannot be negative!", false));
                        return;
                    }
                    List<String> commands = commandList.getCommands();
                    if (commands.isEmpty()) {
                        mc.displayGuiScreen(new MessageGui(parent, "At least one command is required!", false));
                        return;
                    }
                    action.setSum(sum);
                    action.setPriority(priority);
                    action.setCommands(commands);
                    if (isNewAction) {
                        ConfigHandler.getConfig().getActions().add(action);
                    }
                    ConfigHandler.save();
                    parent.refreshActionList();
                    mc.displayGuiScreen(new MessageGui(parent, isNewAction ? "Action added!" : "Action updated!", true));
                } catch (NumberFormatException e) {
                    mc.displayGuiScreen(new MessageGui(parent, "Invalid sum or priority format!", false));
                }
                break;
            case 5: // Cancel
                mc.displayGuiScreen(parent);
                break;
            case 6: // Add Command
                commandList.addCommand("");
                break;
            case 7: // Remove Command
                commandList.removeSelectedCommand();
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        sumField.textboxKeyTyped(typedChar, keyCode);
        priorityField.textboxKeyTyped(typedChar, keyCode);
        commandList.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        sumField.mouseClicked(mouseX, mouseY, mouseButton);
        priorityField.mouseClicked(mouseX, mouseY, mouseButton);
        commandList.mouseClicked(mouseX, mouseY, mouseButton);
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

        fontRenderer.drawString(isNewAction ? "Add Action" : "Edit Action", contentLeft + 20, contentTop + 20, GuiRenderUtils.getTextColor());
        fontRenderer.drawString("Sum:", contentLeft + 20, contentTop + 50, GuiRenderUtils.getTextColor());
        sumField.drawTextBox();
        fontRenderer.drawString("Priority:", contentLeft + 20, contentTop + 100, GuiRenderUtils.getTextColor());
        priorityField.drawTextBox();
        fontRenderer.drawString("Commands:", contentLeft + 20, contentTop + 140, GuiRenderUtils.getTextColor());
        commandList.drawScreen(mouseX, mouseY, partialTicks);

        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private static class CommandList extends GuiListExtended {
        private final ActionEditGui parent;
        private final List<CommandEntry> commandEntries = new ArrayList<>();
        private int selectedIndex = -1;
        private int nextId = 0;

        public CommandList(ActionEditGui parent, int x, int y, int width, int height, List<String> initialCommands) {
            super(parent.mc, width, height, y, y + height, 24);
            this.parent = parent;
            this.left = x;
            this.right = x + width;
            for (String cmd : initialCommands) {
                addCommand(cmd);
            }
            if (commandEntries.isEmpty()) {
                addCommand("");
            }
        }

        public void addCommand(String command) {
            commandEntries.add(new CommandEntry(nextId++, command));
        }

        public void removeSelectedCommand() {
            if (selectedIndex >= 0 && selectedIndex < commandEntries.size()) {
                commandEntries.remove(selectedIndex);
                selectedIndex = -1;
                if (commandEntries.isEmpty()) {
                    addCommand("");
                }
            }
        }

        public List<String> getCommands() {
            List<String> commands = new ArrayList<>();
            for (CommandEntry entry : commandEntries) {
                String cmd = entry.textField.getText().trim();
                if (!cmd.isEmpty()) {
                    commands.add(cmd);
                }
            }
            return commands;
        }

        @Override
        protected int getSize() {
            return commandEntries.size();
        }

        @Override
        public IGuiListEntry getListEntry(int index) {
            return commandEntries.get(index);
        }

        @Override
        protected boolean isSelected(int slotIndex) {
            return slotIndex == selectedIndex;
        }

        public void keyTyped(char typedChar, int keyCode) {
            for (CommandEntry entry : commandEntries) {
                entry.textField.textboxKeyTyped(typedChar, keyCode);
            }
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
            boolean handled = false;
            for (int i = 0; i < commandEntries.size(); i++) {
                CommandEntry entry = commandEntries.get(i);
                if (entry.textField.mouseClicked(mouseX, mouseY, mouseButton)) {
                    selectedIndex = i;
                    handled = true;
                }
            }
            if (!handled && mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom) {
                selectedIndex = getSlotIndexFromScreenCoords(mouseX, mouseY);
            }
            return handled || super.mouseClicked(mouseX, mouseY, mouseButton);
        }

        private class CommandEntry implements IGuiListEntry {
            private final int id;
            private final GuiTextField textField;
            private float hoverAnimation = 0.0f;
            private boolean wasHovered = false;

            public CommandEntry(int id, String command) {
                this.id = id;
                this.textField = new GuiTextField(id, parent.fontRenderer, 0, 0, width - 10, 20);
                this.textField.setText(command);
            }

            @Override
            public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
                boolean hovered = mouseX >= x && mouseX < x + listWidth && mouseY >= y && mouseY < y + slotHeight;
                if (hovered && !wasHovered) hoverAnimation = 0.0f;
                if (!hovered && wasHovered) hoverAnimation = 1.0f;
                hoverAnimation = hovered ? Math.min(1.0f, hoverAnimation + partialTicks * 0.2f) :
                        Math.max(0.0f, hoverAnimation - partialTicks * 0.2f);
                wasHovered = hovered;

                if (isSelected || hovered) {
                    GuiRenderUtils.drawRoundedRect(x, y, listWidth - 4, slotHeight, 4, GuiRenderUtils.mixColors(0xFF37474F, 0xFF546E7A, hoverAnimation));
                }
                textField.x = x + 5;
                textField.y = y + 2;
                textField.drawTextBox();
            }

            @Override
            public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
                return textField.mouseClicked(mouseX, mouseY, 0);
            }

            @Override
            public void mouseReleased(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {}

            @Override
            public void updatePosition(int slotIndex, int x, int y, float partialTicks) {}
        }
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