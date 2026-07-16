/*  This file is part of JPref project.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see [http://www.gnu.org/licenses/].
 *
 * Copyright (C) 2026 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 21 Jun 2026
 */
package com.ab.jpref.ui;

import static com.ab.jpref.ui.TableLayout.ButtonCommand;

public class Widget {
    public static final int BLACK_COLOR = 0;
    public static final int RED_COLOR = 1;
    static int count = -1;      // for debugging

    private final ButtonCommand command;
    private final ButtonListener buttonListener;
    private final int number;
    private String text;
    private int color = 0;
    private boolean enabled;
    protected boolean visible;
    protected int x, y;
    protected int width, height;
    Object userObject;  // Swing - Image, Android - Bitmap

    protected Widget() {
        number = ++count;
        this.command = null;
        this.buttonListener = null;
    }

    // button;
    public Widget(ButtonCommand command, ButtonListener buttonListener) {
        this.command = command;
        this.buttonListener = buttonListener;
        if (command != null) {
            text = command.getName();
        }
        number = ++count;
    }

    // label
    public Widget(int number) {
        ++count;
        this.number = number;
        this.command = null;
        this.buttonListener = null;
        this.enabled = false;
        this.visible = true;
    }

    public ButtonCommand getCommand() {
        return command;
    }

    public int getNumber() {
        return number;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.setText(text, BLACK_COLOR);
    }

    public void setText(int text) {
        this.setText("" + text, BLACK_COLOR);
    }

    public void setText(int text, int color) {
        this.setText("" + text, color);
    }

    public void setText(char text) {
        this.setText("" + text, BLACK_COLOR);
    }

    public void setText(char text, int color) {
        this.setText("" + text, color);
    }

    public void setText(String text, int color) {
        this.text = text;
        this.color = color;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public ButtonListener getButtonListener() {
        return buttonListener;
    }

    public Object getUserObject() {
        return userObject;
    }

    public void setUserObject(Object userObject) {
        this.userObject = userObject;
    }

    public void onClick() {
        if (buttonListener != null) {
            buttonListener.onClick(command);
        }
    }

    public interface ButtonListener {
        void onClick(ButtonCommand buttonCommand);
    }

}