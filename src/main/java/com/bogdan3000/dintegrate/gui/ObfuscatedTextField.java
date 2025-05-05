package com.bogdan3000.dintegrate.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

public class ObfuscatedTextField extends GuiTextField {
    private String actualText;

    public ObfuscatedTextField(int id, FontRenderer fontRenderer, int x, int y, int width, int height) {
        super(id, fontRenderer, x, y, width, height);
        this.actualText = "";
    }

    @Override
    public void setText(String text) {
        this.actualText = text != null ? text : "";
        super.setText(obfuscateText(this.actualText));
    }

    @Override
    public String getText() {
        return this.actualText;
    }

    @Override
    public void writeText(String textToWrite) {
        // Update actualText when user types
        String before = this.actualText.substring(0, getCursorPosition());
        String after = this.actualText.substring(getCursorPosition());
        this.actualText = before + textToWrite + after;
        super.setText(obfuscateText(this.actualText));
        // Move cursor to end of new text
        setCursorPosition(before.length() + textToWrite.length());
    }

    @Override
    public boolean textboxKeyTyped(char typedChar, int keyCode) {
        if (!isFocused()) {
            return false;
        }

        // Handle backspace, delete, and Ctrl+Backspace
        if (keyCode == Keyboard.KEY_BACK) { // Backspace
            if (getCursorPosition() > 0) {
                this.actualText = this.actualText.substring(0, getCursorPosition() - 1) +
                        this.actualText.substring(getCursorPosition());
                super.setText(obfuscateText(this.actualText));
                setCursorPosition(getCursorPosition() - 1);
            }
            return true;
        } else if (keyCode == Keyboard.KEY_DELETE) { // Delete
            if (getCursorPosition() < this.actualText.length()) {
                this.actualText = this.actualText.substring(0, getCursorPosition()) +
                        this.actualText.substring(getCursorPosition() + 1);
                super.setText(obfuscateText(this.actualText));
            }
            return true;
        } else if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) && keyCode == Keyboard.KEY_BACK) { // Ctrl+Backspace
            this.actualText = "";
            super.setText(obfuscateText(this.actualText));
            setCursorPositionZero();
            return true;
        }

        return super.textboxKeyTyped(typedChar, keyCode);
    }

    private String obfuscateText(String text) {
        if (text.length() <= 4) {
            return text; // If 4 or fewer characters, show as is
        }
        // Show first 2 and last 2 characters, replace rest with asterisks
        StringBuilder obfuscated = new StringBuilder();
        obfuscated.append(text.substring(0, 2));
        for (int i = 2; i < text.length() - 2; i++) {
            obfuscated.append('*');
        }
        obfuscated.append(text.substring(text.length() - 2));
        return obfuscated.toString();
    }
}