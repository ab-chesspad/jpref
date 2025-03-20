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
 * Created: 2/11/2025
 */
package com.ab.pref;

import javax.swing.*;
import java.awt.*;

public class PLabel extends javax.swing.JLabel {
    public static final double fontFactor = .4;    // relative to cardW
    protected final double rotation;

    public PLabel(double rotation) {
        setOpaque(false);
        this.rotation = rotation;
    }

    // not width and height but edge points
    public void setPBounds(int x0, int y0, int x1, int y1) {
        super.setBounds(x0, y0, x1 - x0, y1 - y0);
        Font font = new Font("Serif", Font.PLAIN, (int) (Metrics.getInstance().cardW * fontFactor));
        this.setFont(font );
    }

    @Override
    protected void paintComponent(Graphics g) {
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
        g2d.setFont(getFont());
        FontMetrics fontMetrics = g2d.getFontMetrics();

        int x0 =  bounds.width / 2;
        int y0 =  bounds.height / 2;

/*  // debug, axes and border
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
