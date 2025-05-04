package com.bogdan3000.dintegrate.gui;

import com.bogdan3000.dintegrate.config.Action;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiListExtended.IGuiListEntry;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ActionEditGui extends GuiScreen {
    private final DonateIntegrateGui parent;
    private final Action action;
    private final boolean isNewAction;
    private GuiTextField sumField;
    private GuiTextField priorityField;
    private GuiButton enabledButton;
    private GuiButton modeButton;
    private GuiButton saveButton;
    private GuiButton cancelButton;
    private CommandList commandList;

    public ActionEditGui(DonateIntegrateGui parent, Action action) {
        this.parent = parent;
        this.action = action != null ? action : new Action();
        this.isNewAction = action == null;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();

        int centerX = width / 2;
        int centerY = height / 2;

        sumField = new GuiTextField(0, fontRenderer, centerX - 100, centerY - 80, 200, 20);
        sumField.setText(action.getSum() > 0 ? String.valueOf(action.getSum()) : "");
        priorityField = new GuiTextField(1, fontRenderer, centerX - 100, centerY - 40, 200, 20);
        priorityField.setText(String.valueOf(action.getPriority()));

        commandList = new CommandList(this, centerX - 110, centerY - 10, 220, 80, action.getCommands());

        enabledButton = new GuiButton(2, centerX - 100, centerY + 80, 98, 20, "Enabled: " + (action.isEnabled() ? "Yes" : "No"));
        modeButton = new GuiButton(3, centerX + 2, centerY + 80, 98, 20, "Mode: " + action.getExecutionMode());
        saveButton = new GuiButton(4, centerX - 100, centerY + 110, 98, 20, "Save");
        cancelButton = new GuiButton(5, centerX + 2, centerY + 110, 98, 20, "Cancel");

        buttonList.add(enabledButton);
        buttonList.add(modeButton);
        buttonList.add(saveButton);
        buttonList.add(cancelButton);

        buttonList.add(new GuiButton(6, centerX + 110, centerY - 10, 20, 20, "+"));
        buttonList.add(new GuiButton(7, centerX + 110, centerY + 10, 20, 20, "−"));
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
                    float sum = Float.parseFloat(sumField.getText().trim());
                    int priority = Integer.parseInt(priorityField.getText().trim());
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
                    mc.displayGuiScreen(new MessageGui(parent, "Invalid sum or priority!", false));
                } catch (IllegalArgumentException e) {
                    mc.displayGuiScreen(new MessageGui(parent, e.getMessage(), false));
                }
                break;
            case 5: // Cancel
                mc.displayGuiScreen(parent);
                break;
            case 6: // Add Command
                commandList.addCommand("");
                break;
            case 7: // Remove Command
                commandList.removeLastCommand();
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
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawGuiBackground();

        drawCenteredString(fontRenderer, isNewAction ? "Add Action" : "Edit Action", width / 2, height / 2 - 100, 0xFFFFFF);
        drawString(fontRenderer, "Sum:", width / 2 - 100, height / 2 - 90, 0xFFFFFF);
        sumField.drawTextBox();
        drawString(fontRenderer, "Priority:", width / 2 - 100, height / 2 - 50, 0xFFFFFF);
        priorityField.drawTextBox();
        drawString(fontRenderer, "Commands:", width / 2 - 100, height / 2 - 20, 0xFFFFFF);
        commandList.drawScreen(mouseX, mouseY, partialTicks);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawGuiBackground() {
        int centerX = width / 2;
        int centerY = height / 2;
        drawRect(centerX - 150, centerY - 120, centerX + 150, centerY + 150, 0xFF2A2A2A);
        drawRect(centerX - 148, centerY - 118, centerX + 148, centerY + 148, 0xFF1E1E1E);
        drawRect(centerX - 146, centerY - 116, centerX + 146, centerY + 146, 0xFF333333);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private static class CommandList extends GuiListExtended {
        private final ActionEditGui parent;
        private final List<CommandEntry> commandEntries = new ArrayList<>();
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

        public void removeLastCommand() {
            if (!commandEntries.isEmpty()) {
                commandEntries.remove(commandEntries.size() - 1);
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
            return false;
        }

        public void keyTyped(char typedChar, int keyCode) {
            for (CommandEntry entry : commandEntries) {
                entry.textField.textboxKeyTyped(typedChar, keyCode);
            }
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
            boolean handled = false;
            for (CommandEntry entry : commandEntries) {
                if (entry.textField.mouseClicked(mouseX, mouseY, mouseButton)) {
                    handled = true;
                }
            }
            return handled || super.mouseClicked(mouseX, mouseY, mouseButton);
        }

        private class CommandEntry implements IGuiListEntry {
            private final int id;
            private final GuiTextField textField;

            public CommandEntry(int id, String command) {
                this.id = id;
                this.textField = new GuiTextField(id, parent.fontRenderer, 0, 0, width - 10, 20);
                this.textField.setText(command);
            }

            @Override
            public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
                textField.x = x + 5;
                textField.y = y + 2;
                textField.drawTextBox();
            }

            @Override
            public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
                return textField.mouseClicked(mouseX, mouseY, 0);
            }

            @Override
            public void mouseReleased(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {
            }

            @Override
            public void updatePosition(int slotIndex, int x, int y, float partialTicks) {
            }
        }
    }
}