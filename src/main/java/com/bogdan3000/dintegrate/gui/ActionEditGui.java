package com.bogdan3000.dintegrate.gui;

import com.bogdan3000.dintegrate.config.Action;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI for adding or editing an action, allowing configuration of sum, commands, priority, and execution mode.
 */
public class ActionEditGui extends GuiScreen {
    private final DonateIntegrateGui parent;
    private final Action action;
    private GuiTextField sumField;
    private GuiTextField priorityField;
    private GuiButton enabledButton;
    private GuiButton modeButton;
    private GuiButton saveButton;
    private GuiButton cancelButton;
    private CommandSlot commandSlot;
    private boolean isNewAction;

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

        commandSlot = new CommandSlot(this, mc, centerX - 100, centerY, 200, 100, 20);
        // Передаем команды напрямую
        commandSlot.setCommands(action.getCommands());

        enabledButton = new GuiButton(2, centerX - 100, centerY + 110, 98, 20, "Enabled: " + (action.isEnabled() ? "Yes" : "No"));
        modeButton = new GuiButton(3, centerX + 2, centerY + 110, 98, 20, "Mode: " + action.getExecutionMode());
        saveButton = new GuiButton(4, centerX - 100, centerY + 140, 98, 20, "Save");
        cancelButton = new GuiButton(5, centerX + 2, centerY + 140, 98, 20, "Cancel");

        buttonList.add(enabledButton);
        buttonList.add(modeButton);
        buttonList.add(saveButton);
        buttonList.add(cancelButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 200) { // Add command
            commandSlot.addCommandField();
        } else if (button.id == 300) { // Remove command
            commandSlot.removeCommandField();
        } else {
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
                        if (sum <= 0) throw new IllegalArgumentException("Sum must be positive");
                        int priority = Integer.parseInt(priorityField.getText().trim());
                        if (priority < 0) throw new IllegalArgumentException("Priority cannot be negative");
                        List<String> commands = commandSlot.getCommands();
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
            }
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
        commandSlot.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        sumField.mouseClicked(mouseX, mouseY, mouseButton);
        priorityField.mouseClicked(mouseX, mouseY, mouseButton);
        commandSlot.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0xFF000000);
        drawCenteredString(fontRenderer, isNewAction ? "Add Action" : "Edit Action", width / 2, height / 2 - 100, 0xFFFFFF);
        drawString(fontRenderer, "Sum:", width / 2 - 100, height / 2 - 90, 0xFFFFFF);
        sumField.drawTextBox();
        drawString(fontRenderer, "Priority:", width / 2 - 100, height / 2 - 50, 0xFFFFFF);
        priorityField.drawTextBox();
        drawString(fontRenderer, "Commands:", width / 2 - 100, height / 2 - 10, 0xFFFFFF);
        commandSlot.drawScreen(mouseX, mouseY, partialTicks);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private class CommandSlot extends GuiSlot {
        private final List<GuiTextField> commandFields = new ArrayList<>();
        private final List<GuiButton> addButtons = new ArrayList<>();
        private final List<GuiButton> removeButtons = new ArrayList<>();

        public CommandSlot(GuiScreen parent, Minecraft mcIn, int x, int y, int width, int height, int slotHeight) {
            super(mcIn, width, height, y, y + height, slotHeight);
            this.left = x;
            this.top = y;
            this.width = width;
            this.height = height;
            List<String> commands = new ArrayList<>();
            if (commands.isEmpty()) {
                commands.add("");
            }
            for (String cmd : commands) {
                addCommandField(cmd);
            }
            if (commandFields.size() == 1) {
                removeButtons.get(0).enabled = false;
            }
        }

        public void setCommands(List<String> commands) {
            commandFields.clear();
            addButtons.clear();
            removeButtons.clear();
            buttonList.removeIf(button -> button.id >= 200 && button.id < 400);
            if (commands.isEmpty()) {
                commands.add("");
            }
            for (String cmd : commands) {
                addCommandField(cmd);
            }
            if (commandFields.size() == 1) {
                removeButtons.get(0).enabled = false;
            }
        }

        @Override
        protected int getSize() {
            return commandFields.size();
        }

        @Override
        protected void elementClicked(int slotIndex, boolean isDoubleClick, int mouseX, int mouseY) {
        }

        @Override
        protected boolean isSelected(int slotIndex) {
            return false;
        }

        @Override
        protected void drawBackground() {
        }

        @Override
        protected void drawSlot(int slotIndex, int x, int y, int heightIn, int mouseXIn, int mouseYIn, float partialTicks) {
            GuiTextField field = commandFields.get(slotIndex);
            field.x = x;
            field.y = y;
            field.width = width - 40;
            field.height = this.slotHeight;
            field.drawTextBox();

            GuiButton addButton = addButtons.get(slotIndex);
            addButton.x = x + width - 40;
            addButton.y = y;
            addButton.drawButton(mc, mouseXIn, mouseYIn, partialTicks);

            GuiButton removeButton = removeButtons.get(slotIndex);
            removeButton.x = x + width - 20;
            removeButton.y = y;
            removeButton.drawButton(mc, mouseXIn, mouseYIn, partialTicks);
        }

        public void addCommandField() {
            GuiTextField newField = new GuiTextField(100 + commandFields.size(), fontRenderer, 0, 0, width - 40, this.slotHeight);
            commandFields.add(newField);
            GuiButton addButton = new GuiButton(200 + addButtons.size(), 0, 0, 20, 20, "+");
            GuiButton removeButton = new GuiButton(300 + removeButtons.size(), 0, 0, 20, 20, "−");
            addButtons.add(addButton);
            removeButtons.add(removeButton);
            buttonList.add(addButton);
            buttonList.add(removeButton);
            if (commandFields.size() > 1) {
                removeButtons.get(removeButtons.size() - 2).enabled = true;
            }
        }

        public void addCommandField(String command) {
            GuiTextField newField = new GuiTextField(100 + commandFields.size(), fontRenderer, 0, 0, width - 40, this.slotHeight);
            newField.setText(command);
            commandFields.add(newField);
            GuiButton addButton = new GuiButton(200 + addButtons.size(), 0, 0, 20, 20, "+");
            GuiButton removeButton = new GuiButton(300 + removeButtons.size(), 0, 0, 20, 20, "−");
            addButtons.add(addButton);
            removeButtons.add(removeButton);
            buttonList.add(addButton);
            buttonList.add(removeButton);
        }

        public void removeCommandField() {
            if (commandFields.size() <= 1) return;
            int lastIndex = commandFields.size() - 1;
            commandFields.remove(lastIndex);
            buttonList.remove(addButtons.remove(lastIndex));
            buttonList.remove(removeButtons.remove(lastIndex));
            if (commandFields.size() == 1) {
                removeButtons.get(0).enabled = false;
            }
        }

        public List<String> getCommands() {
            List<String> commands = new ArrayList<>();
            for (GuiTextField field : commandFields) {
                String cmd = field.getText().trim();
                if (!cmd.isEmpty()) {
                    commands.add(cmd);
                }
            }
            return commands;
        }

        public void keyTyped(char typedChar, int keyCode) {
            for (GuiTextField field : commandFields) {
                field.textboxKeyTyped(typedChar, keyCode);
            }
        }

        public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            for (GuiTextField field : commandFields) {
                field.mouseClicked(mouseX, mouseY, mouseButton);
            }
        }
    }
}