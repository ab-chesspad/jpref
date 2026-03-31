/*  This file is part of JPref.
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
 * Copyright (C) 2025-2026 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 2/16/2025
 */

package com.ab.pref.widgets;

import com.ab.jpref.cards.Card;
import com.ab.pref.MainPanel;
import com.ab.pref.PUtil;
import com.ab.pref.config.Metrics;
import com.ab.jpref.config.I18n;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PButton extends JButton {
    private static final Metrics metrics = Metrics.getInstance();
    private final PUtil pUtil = PUtil.getInstance();

    final ButtonHandler command;
    final private double scaleW;
    final private double scaleH;
    final private BufferedImage image;
    int width, height;
    String text;

    PButton(double scaleW, double scaleH, ButtonHandler command) {
        this.setHorizontalTextPosition(JButton.CENTER);
        this.setVerticalTextPosition(JButton.CENTER);
        setMargin(new Insets(0, 0, 0, 0));
        this.scaleW = scaleW;
        this.scaleH = scaleH;
        this.command = command;
        this.addActionListener(actionEvent -> {
            if (command != null) {
                command.buttonListener.onClick(command.buttonCommand);
            }
        });
        image = pUtil.loadImage(String.format("buttons/%s.png", command.buttonCommand.toString()));
        if (image == null) {
            text = command.buttonCommand.getName();
        }
        rescale();
    }

    public MainPanel.ButtonCommand getCommand() {
        return command.buttonCommand;
    }

    Dimension rescale() {
        int width = (int) (metrics.cardW * scaleW) - 2 * metrics.xMargin;
        int height = (int) (metrics.cardW * scaleH) - 2 * metrics.yMargin;
        if (this.width != width || this.height != height) {
            this.width = width;
            this.height = height;
            if (image != null) {
                BufferedImage scaledImage = pUtil.scale(image, width, height);
                this.setIcon(new ImageIcon(scaledImage));
            }
            Font f = this.getFont();
            f = new Font(f.getFontName(), f.getStyle(), height / 3);
            this.setFont(f);
        }
        return new Dimension(width, height);
    }

    @Override
    public Dimension getPreferredSize() {
        return rescale();
    }

    @Override
    public Dimension getMinimumSize() {
        return rescale();
    }

    @Override
    public Dimension getMaximumSize() {
        return rescale();
    }

    @Override
    public Dimension getSize() {
        return rescale();
    }

    public Rectangle getBounds() {
        Rectangle r = super.getBounds();
        Dimension d = rescale();
        r.width = d.width;
        r.height = d.height;
        return r;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (text != null && !text.isEmpty()) {
            Pattern p0 = Pattern.compile("([^*]+)");
            Matcher m0 = p0.matcher(text);
            int start = 0;
            StringBuilder sb = new StringBuilder();
            while (m0.find()) {
                String chunk = m0.group();
                sb.append(text, start, m0.start());
                if (chunk.startsWith(" ")) {
                    sb.append(" ");
                }
                sb.append(I18n.m(chunk.trim()));
                start = m0.end();
            }
            sb.append(text.substring(start));
            super.setText(sb.toString());
        }
        super.paintComponent(g);
    }

    public void setText(String text, Color fgColor) {
//        super.setText(text);
        this.text = text;
        super.setForeground(fgColor);
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setText(char text) {
        Color fgColor = Color.black;
        if (text == Card.Suit.DIAMOND.getCode() || text == Card.Suit.HEART.getCode()) {
            fgColor = Color.red;
        }
        this.text = "" + text;
//        super.setText("" + text);
        super.setForeground(fgColor);
    }

    public void setText(int text, Color fgColor) {
        this.text = "" + text;
//        super.setText("" + text);
        super.setForeground(fgColor);
    }

    public interface ButtonListener {
        void onClick(MainPanel.ButtonCommand buttonCommand);
    }

    public static class ButtonHandler {
        public final MainPanel.ButtonCommand buttonCommand;
        public final ButtonListener buttonListener;

        public ButtonHandler(MainPanel.ButtonCommand buttonCommand, ButtonListener buttonListener) {
            this.buttonCommand = buttonCommand;
            this.buttonListener = buttonListener;
        }
    }
}