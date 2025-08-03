package com.bogdan3000.dintegrate.gui;

import com.bogdan3000.dintegrate.config.Action;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CommandEditGui extends GuiScreen {
    private static final int CONTENT_MARGIN = 12;
    private static final int FIELD_HEIGHT = 20;
    private final GuiScreen parent;
    private final int commandIndex;
    private final String initialCommand;
    private GuiTextField commandField;
    private GuiButton saveButton;
    private GuiButton exitButton;
    private int windowLeft, windowTop, windowWidth, windowHeight;

    public CommandEditGui(GuiScreen parent, int commandIndex, String initialCommand) {
        this.parent = parent;
        this.commandIndex = commandIndex;
        this.initialCommand = initialCommand;
    }

    @Override
    public void initGui() {
        buttonList.clear();

        windowWidth = width - 2 * CONTENT_MARGIN;
        windowHeight = height - 2 * CONTENT_MARGIN;
        windowLeft = CONTENT_MARGIN;
        windowTop = CONTENT_MARGIN;

        int contentLeft = windowLeft + CONTENT_MARGIN;
        int contentTop = windowTop + CONTENT_MARGIN + 20; // Shift down to account for title
        int contentWidth = windowWidth - 2 * CONTENT_MARGIN;
        int contentHeight = windowHeight - 2 * CONTENT_MARGIN - 40 - 20; // Adjust for title and buttons

        commandField = new GuiTextField(0, fontRenderer, contentLeft, contentTop, contentWidth, FIELD_HEIGHT);
        commandField.setMaxStringLength(200);
        commandField.setText(initialCommand);

        int buttonWidth = contentWidth / 2 - CONTENT_MARGIN;
        int buttonY = contentTop + FIELD_HEIGHT + CONTENT_MARGIN;
        saveButton = new ActionEditGui.CustomButton(1, contentLeft, buttonY, buttonWidth, 24, I18n.format("dintegrate.gui.button.save"));
        exitButton = new ActionEditGui.CustomButton(2, contentLeft + buttonWidth + CONTENT_MARGIN, buttonY, buttonWidth, 24, I18n.format("dintegrate.gui.button.exit"));

        buttonList.add(saveButton);
        buttonList.add(exitButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) { // Save
            ActionEditGui parentGui = (ActionEditGui) parent;
            if (parentGui.commandEntries.size() > commandIndex) {
                ActionEditGui.CommandEntry entry = parentGui.commandEntries.get(commandIndex);
                String newCommand = commandField.getText().trim();
                if (!newCommand.isEmpty()) {
                    entry.commandField.setText(newCommand);

                    // Сбор всех данных для обновления Action
                    try {
                        float sum = Float.parseFloat(parentGui.getSumField().getText().trim());
                        int priority = Integer.parseInt(parentGui.getPriorityField().getText().trim());
                        List<String> commands = new ArrayList<>();
                        for (ActionEditGui.CommandEntry cmdEntry : parentGui.commandEntries) {
                            String cmd = cmdEntry.commandField.getText().trim();
                            if (!cmd.isEmpty()) {
                                if (!cmd.startsWith("/")) {
                                    mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.invalid_command"), false));
                                    return;
                                }
                                commands.add(cmd);
                            }
                        }
                        Action.ExecutionMode mode = parentGui.getExecutionModeButton().getDisplayString().contains("ALL") ? Action.ExecutionMode.ALL : Action.ExecutionMode.RANDOM_ONE;
                        boolean enabled = parentGui.getEnabledButton().getDisplayString().contains(I18n.format("dintegrate.gui.value.enabled"));

                        if (sum <= 0) {
                            mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.positive_sum"), false));
                            return;
                        }
                        if (priority < 0) {
                            mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.negative_priority"), false));
                            return;
                        }
                        if (commands.isEmpty()) {
                            mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.no_commands"), false));
                            return;
                        }

                        Action newAction = new Action(sum, enabled, priority, commands, mode);
                        if (parentGui.getEditingAction() != null) {
                            int index = ConfigHandler.getConfig().getActions().indexOf(parentGui.getEditingAction());
                            if (index >= 0) {
                                ConfigHandler.getConfig().getActions().set(index, newAction);
                            } else {
                                ConfigHandler.getConfig().getActions().add(newAction);
                            }
                        } else {
                            ConfigHandler.getConfig().getActions().add(newAction);
                        }
                        ConfigHandler.save();
                        // Создание нового экземпляра ActionEditGui с перечитанными данными
                        Action updatedAction = null;
                        if (parentGui.getEditingAction() != null) {
                            int updatedIndex = ConfigHandler.getConfig().getActions().indexOf(newAction);
                            if (updatedIndex >= 0) {
                                updatedAction = ConfigHandler.getConfig().getActions().get(updatedIndex);
                            }
                        }
                        ActionEditGui newParentGui = new ActionEditGui(parentGui.getParent(), updatedAction);
                        mc.displayGuiScreen(newParentGui);
                    } catch (NumberFormatException e) {
                        mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.invalid_number"), false));
                    }
                }
            }
        } else if (button.id == 2) { // Exit
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 0.8f);
        GuiRenderUtils.drawOverlay(width, height);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GuiRenderUtils.drawRoundedRect((float) windowLeft, (float) windowTop, (float) windowWidth, (float) windowHeight, 8, 0xFF263238);

        // Draw title at the top
        drawCenteredString(fontRenderer, I18n.format("dintegrate.gui.title.edit_command"),
                windowLeft + windowWidth / 2, windowTop + CONTENT_MARGIN, 0xFFFFFFFF);

        int contentLeft = windowLeft + CONTENT_MARGIN;
        int contentTop = windowTop + CONTENT_MARGIN + 20; // Title height + margin
        int contentWidth = windowWidth - 2 * CONTENT_MARGIN;
        int contentHeight = windowHeight - 2 * CONTENT_MARGIN - 40 - 20; // Adjust for title and buttons

        // Draw only the command field background
        GuiRenderUtils.drawRoundedRect((float) contentLeft, (float) contentTop, (float) contentWidth, (float) FIELD_HEIGHT, 2, 0xFF37474F);
        GuiRenderUtils.drawRoundedRect((float) contentLeft, (float) contentTop, (float) contentWidth, (float) FIELD_HEIGHT, 2, commandField.isFocused() ? 0xFF0288D1 : 0xFF90A4AE);
        commandField.drawTextBox();

        int buttonY = contentTop + FIELD_HEIGHT + CONTENT_MARGIN;
        for (GuiButton button : buttonList) {
            if (button instanceof ActionEditGui.CustomButton) {
                ActionEditGui.CustomButton customButton = (ActionEditGui.CustomButton) button;
                boolean hovered = mouseX >= customButton.x && mouseY >= customButton.y &&
                        mouseX < customButton.x + customButton.width && mouseY < customButton.y + customButton.height;
                GlStateManager.pushMatrix();
                if (hovered) {
                    GlStateManager.translate(customButton.x + customButton.width / 2f, customButton.y + customButton.height / 2f, 0);
                    GlStateManager.scale(1.05f, 1.05f, 1.0f);
                    GlStateManager.translate(-customButton.x - customButton.width / 2f, -customButton.y - customButton.height / 2f, 0);
                }
                customButton.drawButton(mc, mouseX, mouseY, partialTicks);
                GlStateManager.popMatrix();
            }
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        commandField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        commandField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        commandField.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
    }
}