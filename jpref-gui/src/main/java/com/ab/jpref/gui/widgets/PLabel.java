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
 * Copyright (C) 2025-2026 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 2/11/2025
 */
package com.ab.jpref.gui.widgets;

import javax.swing.*;
import java.awt.*;

public class PLabel extends JLabel {
    public static final double fontFactor = .4;    // relative to height
    protected final double rotation;

    private static Font font;
    private static int minSize = Integer.MAX_VALUE;

    public PLabel(double rotation) {
        setOpaque(false);
        this.rotation = rotation;
    }

    public static void initRecalc() {
        PLabel.font = null;
        minSize = Integer.MAX_VALUE;
    }

    // not width and height but edge points
    public void setPBounds(int x0, int y0, int x1, int y1) {
        int w = x1 - x0;
        int h = y1 - y0;
        super.setBounds(x0, y0, w, h);
        int size = w;
        if (rotation == 0) {
            size = h;
        }
        if (minSize > size) {
            minSize = size;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (font == null && minSize != Integer.MAX_VALUE) {
            font = new Font("Serif", Font.PLAIN, (int)(minSize * fontFactor));
        }
        if (font != null) {
            g.setFont(font);
        }
        if (rotation == 0) {
            super.paintComponent(g);
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();
        Rectangle bounds = super.getBounds();
        Color bgColor;
        if ((bgColor = getBackground()) != null) {
            g2d.setColor(bgColor);
            g2d.fillRect(0, 0,  bounds.width,  bounds.height);
        }
        g2d.setColor(getForeground());
        String text = getText();
        g2d.setFont(font);
        FontMetrics fontMetrics = g2d.getFontMetrics();

        int x0 = bounds.width / 2;
        int y0 = bounds.height / 2;

/*  debug, axes and border
        g2d.drawLine( x0, 0, x0, bounds.height);
        g2d.drawLine( 0, y0, bounds.width, y0);
        g2d.drawRect(0, 0,  bounds.width,  bounds.height);  // without rotation
//*/
        int x = x0 - bounds.height / 2;

        if (getHorizontalAlignment() == SwingConstants.CENTER) {
            int textWidth = fontMetrics.stringWidth(text);
            x = x0 - textWidth / 2;
        }
        g2d.rotate(rotation, x0, y0);
        g2d.drawString(text, x, y0 + fontMetrics.getDescent());
        g2d.dispose();
    }
}