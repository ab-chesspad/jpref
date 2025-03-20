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
 * Created: 02/19/2025
 */
 package com.ab.pref;

import javax.swing.*;
import java.awt.*;

public class ButtonPanel extends JPanel {
    static final int xGap = 3;
    static final int yGap = 3;

    private final Metrics metrics;

    private final int columns;
    private final int rows;
    private final double scaleW, scaleH;

    public ButtonPanel(double scaleW, double scaleH, MainPanel.ButtonHandler[][] commands) {
        this.scaleW = scaleW;
        this.scaleH = scaleH;
        metrics = Metrics.getInstance();
        this.setOpaque(false);      // transparent
        this.setBackground(Color.magenta);
        rows = commands.length;
        columns = commands[0].length;
        this.setLayout(new GridLayout(rows, columns, xGap, xGap));
        for (MainPanel.ButtonHandler[] buttonHandlers : commands) {
            for (MainPanel.ButtonHandler command : buttonHandlers) {
                if (command == null) {
                    this.add(new JLabel());
                } else {
                    this.add(new PButton(scaleW, scaleH, command));
                }
            }
        }
    }

    Dimension rescale() {
        int width = (int) (metrics.cardW * scaleW * columns + xGap * (columns - 1));
        int height = (int) (metrics.cardW * scaleH * rows + yGap * (rows - 1));
        for (Component c : getComponents()) {
            if (c instanceof PButton) {
                ((PButton) c).rescale();
            }
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
    public Rectangle getBounds() {
        Rectangle r = (Rectangle) super.getBounds().clone();
        Dimension d = rescale();
        r.width = d.width;
        r.height = d.height;
        return r;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
    }

    public PButton getButton(MainPanel.Command command) {
        for (Component c : getComponents()) {
            if (c instanceof PButton) {
                if (command.equals(((PButton) c).command.command)) {
                    return (PButton) c;
                }
            }
        }
        return null;
    }
}
