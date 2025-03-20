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
 * Copyright 2025 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 2/16/2025
 */

package com.ab.pref;

import com.ab.util.I18n;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

public class PButton extends JButton {
    private static final Metrics metrics = Metrics.getInstance();

    final MainPanel.ButtonHandler command;
    final private double scaleW;
    final private double scaleH;
    final private BufferedImage image;
    int width, height;

    PButton(double scaleW, double scaleH, MainPanel.ButtonHandler command) {
        this.scaleW = scaleW;
        this.scaleH = scaleH;
        this.command = command;
        this.addActionListener(actionEvent -> {
//            System.out.printf("clicked %s\n", command.command.name());
            if (command != null) {
                command.buttonListener.onClick(command.command);
            }
        });
        image = Util.loadImage(String.format("buttons/%s.png", command.command.toString()));
        if (image == null) {
            this.setText(I18n.m(command.command.name));
        }
        rescale();
    }

    public MainPanel.Command getCommand() {
        return command.command;
    }

    Dimension rescale() {
        int width = (int)(metrics.cardW * scaleW);
        int height = (int)(metrics.cardW * scaleH);
        if (this.width != width || this.height != height) {
            this.width = width;
            this.height = height;
            if (image != null) {
                BufferedImage scaledImage = Util.scale(image, width, height);
                this.setIcon(new ImageIcon(scaledImage));
            }
            Font f = this.getFont();
            f = new Font(f.getFontName(), f.getStyle(), height / 2);
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
        super.paintComponent(g);
    }
}
