package com.bogdan3000.dintegrate.gui;

import com.bogdan3000.dintegrate.config.Action;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import com.bogdan3000.dintegrate.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ActionEditGui extends GuiScreen {
    private static final int CONTENT_WIDTH = 300;
    private static final int CONTENT_HEIGHT = 300;
    private static final int FIELD_WIDTH = 260;
    private static final int COMMAND_FIELD_HEIGHT = 20;
    private static final int COMMAND_LIST_HEIGHT = 80;
    private static final int MAX_VISIBLE_COMMANDS = 3;
    private static final int SCROLL_SPEED = 20; // Pixels to scroll per mouse wheel tick

    private final GuiScreen parent;
    private final Action editingAction;
    private int contentLeft, contentTop;
    private float fadeAnimation = 0.0f;
    private float scrollOffset = 0.0f;
    private boolean isDraggingScrollbar = false;
    private int scrollbarHeight = 20;
    private int scrollbarTop;

    private GuiTextField sumField;
    private GuiTextField priorityField;
    private final List<GuiTextField> commandFields = new ArrayList<>();
    private CustomButton addCommandButton;
    private CustomButton removeCommandButton;
    private CustomButton executionModeButton;
    private CustomButton enabledButton;
    private CustomButton saveButton;
    private CustomButton cancelButton;

    public ActionEditGui(GuiScreen parent, Action editingAction) {
        this.parent = parent;
        this.editingAction = editingAction;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        commandFields.clear();
        Keyboard.enableRepeatEvents(true);
        fadeAnimation = 0.0f;
        scrollOffset = 0.0f;

        contentLeft = (width - CONTENT_WIDTH) / 2;
        contentTop = (height - CONTENT_HEIGHT) / 2;

        sumField = new GuiTextField(0, fontRenderer, contentLeft + 20, contentTop + 40, FIELD_WIDTH, 20);
        sumField.setMaxStringLength(10);
        sumField.setText(editingAction != null ? String.valueOf(editingAction.getSum()) : "0.0");

        priorityField = new GuiTextField(1, fontRenderer, contentLeft + 20, contentTop + 80, FIELD_WIDTH, 20);
        priorityField.setMaxStringLength(5);
        priorityField.setText(editingAction != null ? String.valueOf(editingAction.getPriority()) : "1");

        List<String> initialCommands = editingAction != null ? editingAction.getCommands() : new ArrayList<>();
        if (initialCommands.isEmpty()) {
            initialCommands.add("");
        }
        for (int i = 0; i < initialCommands.size(); i++) {
            GuiTextField commandField = new GuiTextField(100 + i, fontRenderer, contentLeft + 20,
                    contentTop + 120 + i * (COMMAND_FIELD_HEIGHT + 4), FIELD_WIDTH, COMMAND_FIELD_HEIGHT);
            commandField.setMaxStringLength(200);
            commandField.setText(initialCommands.get(i));
            commandFields.add(commandField);
        }

        addCommandButton = new CustomButton(2, contentLeft + FIELD_WIDTH + 24, contentTop + 120, 20, 20, "+");
        removeCommandButton = new CustomButton(3, contentLeft + FIELD_WIDTH + 24, contentTop + 144, 20, 20, "−");
        removeCommandButton.enabled = commandFields.size() > 1;

        Action.ExecutionMode mode = editingAction != null ? editingAction.getExecutionMode() : Action.ExecutionMode.ALL;
        executionModeButton = new CustomButton(4, contentLeft + 20, contentTop + 220, 120, 24, I18n.format("dintegrate.gui.label.mode") + mode.name());
        enabledButton = new CustomButton(5, contentLeft + CONTENT_WIDTH - 140, contentTop + 220, 120, 24,
                editingAction != null ? (editingAction.isEnabled() ? I18n.format("dintegrate.gui.value.enabled") : I18n.format("dintegrate.gui.value.disabled")) : I18n.format("dintegrate.gui.value.enabled"));

        saveButton = new CustomButton(6, contentLeft + 20, contentTop + CONTENT_HEIGHT - 40, 100, 24, I18n.format("dintegrate.gui.button.save"));
        cancelButton = new CustomButton(7, contentLeft + CONTENT_WIDTH - 120, contentTop + CONTENT_HEIGHT - 40, 100, 24, I18n.format("dintegrate.gui.button.cancel"));

        buttonList.add(addCommandButton);
        buttonList.add(removeCommandButton);
        buttonList.add(executionModeButton);
        buttonList.add(enabledButton);
        buttonList.add(saveButton);
        buttonList.add(cancelButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 2:
                GuiTextField newField = new GuiTextField(100 + commandFields.size(), fontRenderer, contentLeft + 20,
                        contentTop + 120 + commandFields.size() * (COMMAND_FIELD_HEIGHT + 4), FIELD_WIDTH, COMMAND_FIELD_HEIGHT);
                newField.setMaxStringLength(200);
                newField.setText("");
                commandFields.add(newField);
                removeCommandButton.enabled = true;
                break;
            case 3:
                if (commandFields.size() > 1) {
                    commandFields.remove(commandFields.size() - 1);
                    removeCommandButton.enabled = commandFields.size() > 1;
                }
                break;
            case 4:
                Action.ExecutionMode currentMode = executionModeButton.displayString.contains("ALL") ?
                        Action.ExecutionMode.ALL : Action.ExecutionMode.RANDOM_ONE;
                Action.ExecutionMode newMode = currentMode == Action.ExecutionMode.ALL ?
                        Action.ExecutionMode.RANDOM_ONE : Action.ExecutionMode.ALL;
                executionModeButton.displayString = I18n.format("dintegrate.gui.label.mode") + newMode.name();
                break;
            case 5:
                enabledButton.displayString = enabledButton.displayString.contains(I18n.format("dintegrate.gui.value.enabled")) ? I18n.format("dintegrate.gui.value.disabled") : I18n.format("dintegrate.gui.value.enabled");
                break;
            case 6:
                try {
                    float sum = Float.parseFloat(sumField.getText().trim());
                    int priority = Integer.parseInt(priorityField.getText().trim());
                    List<String> commands = new ArrayList<>();
                    for (GuiTextField field : commandFields) {
                        String cmd = field.getText().trim();
                        if (!cmd.isEmpty()) {
                            commands.add(cmd);
                        }
                    }
                    Action.ExecutionMode mode = executionModeButton.displayString.contains("ALL") ?
                            Action.ExecutionMode.ALL : Action.ExecutionMode.RANDOM_ONE;
                    boolean enabled = enabledButton.displayString.contains(I18n.format("dintegrate.gui.value.enabled"));

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

                    ModConfig config = ConfigHandler.getConfig();
                    Action newAction = new Action(sum, enabled, priority, commands, mode);
                    if (editingAction != null) {
                        int index = config.getActions().indexOf(editingAction);
                        if (index >= 0) {
                            config.getActions().set(index, newAction);
                        }
                    } else {
                        config.getActions().add(newAction);
                    }
                    ConfigHandler.save();
                    mc.displayGuiScreen(new MessageGui(parent, I18n.format(editingAction != null ? "dintegrate.gui.message.action_updated" : "dintegrate.gui.message.action_added"), true));
                } catch (NumberFormatException e) {
                    mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.invalid_number"), false));
                } catch (Exception e) {
                    mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.save_error") + e.getMessage(), false));
                }
                break;
            case 7:
                mc.displayGuiScreen(parent);
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, fadeAnimation);
        GuiRenderUtils.drawOverlay(width, height);
        GuiRenderUtils.drawRoundedRect(contentLeft, contentTop, CONTENT_WIDTH, CONTENT_HEIGHT, 8, 0xFF263238);

        fontRenderer.drawString(I18n.format(editingAction != null ? "dintegrate.gui.title.edit_action" : "dintegrate.gui.title.add_action"), contentLeft + 20, contentTop + 20, GuiRenderUtils.getTextColor());
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.sum"), contentLeft + 20, contentTop + 30, GuiRenderUtils.getTextColor());
        sumField.drawTextBox();
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.priority"), contentLeft + 20, contentTop + 70, GuiRenderUtils.getTextColor());
        priorityField.drawTextBox();
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.commands"), contentLeft + 20, contentTop + 110, GuiRenderUtils.getTextColor());

        // Отрисовка списка команд
        int listTop = contentTop + 120;
        int listLeft = contentLeft + 20;
        int listWidth = FIELD_WIDTH;

        // Фон для списка команд
        GuiRenderUtils.drawRoundedRect(listLeft, listTop, listWidth, COMMAND_LIST_HEIGHT, 4, 0xFF37474F);

        // Включение обрезки для списка команд
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        int scaleFactor = new ScaledResolution(mc).getScaleFactor();
        GL11.glScissor((listLeft * scaleFactor), (height - listTop - COMMAND_LIST_HEIGHT) * scaleFactor,
                (listWidth * scaleFactor), (COMMAND_LIST_HEIGHT * scaleFactor));

        // Отрисовка полей команд
        for (int i = 0; i < commandFields.size(); i++) {
            GuiTextField field = commandFields.get(i);
            int y = listTop + i * (COMMAND_FIELD_HEIGHT + 4) - (int) scrollOffset;
            if (y + COMMAND_FIELD_HEIGHT < listTop || y > listTop + COMMAND_LIST_HEIGHT) continue;
            field.y = y;
            field.drawTextBox();
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Отрисовка полосы прокрутки
        if (commandFields.size() > MAX_VISIBLE_COMMANDS) {
            int maxScroll = (commandFields.size() - MAX_VISIBLE_COMMANDS) * (COMMAND_FIELD_HEIGHT + 4);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll)); // Ensure scrollOffset is within bounds
            float scrollRatio = scrollOffset / (maxScroll > 0 ? maxScroll : 1);
            scrollbarHeight = Math.max(20, COMMAND_LIST_HEIGHT * MAX_VISIBLE_COMMANDS / commandFields.size());
            scrollbarTop = listTop + (int) ((COMMAND_LIST_HEIGHT - scrollbarHeight) * scrollRatio);
            int scrollbarLeft = contentLeft + CONTENT_WIDTH - 30;
            GuiRenderUtils.drawRoundedRect(scrollbarLeft, scrollbarTop, 10, scrollbarHeight, 4, 0xFF546E7A);
        } else {
            scrollOffset = 0; // Reset scroll if no scrollbar is needed
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        int listTop = contentTop + 120; // Calculate listTop (same as in drawScreen)
        int listLeft = contentLeft + 20;
        int listWidth = FIELD_WIDTH;
        int listHeight = COMMAND_LIST_HEIGHT;

        if (commandFields.size() > MAX_VISIBLE_COMMANDS) {
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0) {
                // Check if mouse is over the command list area
                if (mouseX >= listLeft && mouseX <= listLeft + listWidth &&
                        mouseY >= listTop && mouseY <= listTop + listHeight) {
                    int maxScroll = (commandFields.size() - MAX_VISIBLE_COMMANDS) * (COMMAND_FIELD_HEIGHT + 4);
                    scrollOffset -= wheel > 0 ? SCROLL_SPEED : -SCROLL_SPEED;
                    scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
                }
            }
        }

        // Optional: Handle scrollbar dragging
        if (Mouse.isButtonDown(0)) {
            int scrollbarLeft = contentLeft + CONTENT_WIDTH - 30;
            if (!isDraggingScrollbar && mouseX >= scrollbarLeft && mouseX <= scrollbarLeft + 10 &&
                    mouseY >= scrollbarTop && mouseY <= scrollbarTop + scrollbarHeight) {
                isDraggingScrollbar = true;
            }
        } else {
            isDraggingScrollbar = false;
        }

        if (isDraggingScrollbar) {
            int maxScroll = (commandFields.size() - MAX_VISIBLE_COMMANDS) * (COMMAND_FIELD_HEIGHT + 4);
            float scrollRatio = (mouseY - listTop - scrollbarHeight / 2.0f) / (listHeight - scrollbarHeight);
            scrollOffset = scrollRatio * maxScroll;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        sumField.mouseClicked(mouseX, mouseY, mouseButton);
        priorityField.mouseClicked(mouseX, mouseY, mouseButton);
        for (GuiTextField commandField : commandFields) {
            commandField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        sumField.textboxKeyTyped(typedChar, keyCode);
        priorityField.textboxKeyTyped(typedChar, keyCode);
        for (GuiTextField commandField : commandFields) {
            commandField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        sumField.updateCursorCounter();
        priorityField.updateCursorCounter();
        for (GuiTextField commandField : commandFields) {
            commandField.updateCursorCounter();
        }
        if (fadeAnimation < 1.0f) {
            fadeAnimation = Math.min(1.0f, fadeAnimation + 0.05f);
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