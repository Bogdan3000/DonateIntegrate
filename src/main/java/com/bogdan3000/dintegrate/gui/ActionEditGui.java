package com.bogdan3000.dintegrate.gui;

import com.bogdan3000.dintegrate.config.Action;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ActionEditGui extends GuiScreen {
    private final DonateIntegrateGui parent;
    private final Action action;
    private GuiTextField sumField;
    private GuiTextField commandsField;
    private GuiTextField priorityField;
    private GuiButton enabledButton;
    private GuiButton modeButton;
    private GuiButton saveButton;
    private GuiButton cancelButton;
    private boolean isNewAction;

    public ActionEditGui(DonateIntegrateGui parent, Action action) {
        this.parent = parent;
        this.action = action != null ? action : new Action();
        this.isNewAction = action == null;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        int centerX = width / 2;
        int centerY = height / 2;

        sumField = new GuiTextField(0, fontRenderer, centerX - 100, centerY - 60, 200, 20);
        sumField.setText(action.getSum() > 0 ? String.valueOf(action.getSum()) : "");
        commandsField = new GuiTextField(1, fontRenderer, centerX - 100, centerY - 20, 200, 20);
        commandsField.setText(String.join(";", action.getCommands()));
        priorityField = new GuiTextField(2, fontRenderer, centerX - 100, centerY + 20, 200, 20);
        priorityField.setText(String.valueOf(action.getPriority()));

        enabledButton = new GuiButton(3, centerX - 100, centerY + 60, 98, 20, "Enabled: " + (action.isEnabled() ? "Yes" : "No"));
        modeButton = new GuiButton(4, centerX + 2, centerY + 60, 98, 20, "Mode: " + action.getExecutionMode());
        saveButton = new GuiButton(5, centerX - 100, centerY + 90, 98, 20, "Save");
        cancelButton = new GuiButton(6, centerX + 2, centerY + 90, 98, 20, "Cancel");

        buttonList.add(enabledButton);
        buttonList.add(modeButton);
        buttonList.add(saveButton);
        buttonList.add(cancelButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 3: // Toggle Enabled
                action.setEnabled(!action.isEnabled());
                enabledButton.displayString = "Enabled: " + (action.isEnabled() ? "Yes" : "No");
                break;
            case 4: // Toggle Mode
                action.setExecutionMode(action.getExecutionMode() == Action.ExecutionMode.ALL ? Action.ExecutionMode.RANDOM_ONE : Action.ExecutionMode.ALL);
                modeButton.displayString = "Mode: " + action.getExecutionMode();
                break;
            case 5: // Save
                try {
                    float sum = Float.parseFloat(sumField.getText().trim());
                    int priority = Integer.parseInt(priorityField.getText().trim());
                    List<String> commands = new ArrayList<>();
                    String commandsText = commandsField.getText().trim();
                    if (!commandsText.isEmpty()) {
                        for (String cmd : commandsText.split(";")) {
                            if (!cmd.trim().isEmpty()) {
                                commands.add(cmd.trim());
                            }
                        }
                    }
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
            case 6: // Cancel
                mc.displayGuiScreen(parent);
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
        commandsField.textboxKeyTyped(typedChar, keyCode);
        priorityField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        sumField.mouseClicked(mouseX, mouseY, mouseButton);
        commandsField.mouseClicked(mouseX, mouseY, mouseButton);
        priorityField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, isNewAction ? "Add Action" : "Edit Action", width / 2, height / 2 - 80, 0xFFFFFF);
        drawString(fontRenderer, "Sum:", width / 2 - 100, height / 2 - 70, 0xFFFFFF);
        sumField.drawTextBox();
        drawString(fontRenderer, "Commands (semicolon-separated):", width / 2 - 100, height / 2 - 30, 0xFFFFFF);
        commandsField.drawTextBox();
        drawString(fontRenderer, "Priority:", width / 2 - 100, height / 2 + 10, 0xFFFFFF);
        priorityField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }
}