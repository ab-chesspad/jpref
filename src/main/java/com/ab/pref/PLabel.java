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

import java.awt.*;

public class PLabel extends javax.swing.JLabel {
    private final double rotation;
    final Rectangle bounds = new Rectangle(); // sometimes bounds get wiped out

    public PLabel(String text) {
        super((text));
        rotation = 0;
    }

    public PLabel(double rotation) {
        this.rotation = rotation;
    }

//*
    public void setPBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        this.bounds.x = x;
        this.bounds.y = y;
        this.bounds.width = width;
        this.bounds.height = height;
    }

    public void setPBounds(Rectangle r) {
//        super.setBounds(r);
        this.setPBounds(r.x, r.y, r.width, r.height);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
//        paintComponent(g);
    }

//*/

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();

        Rectangle bounds = this.bounds;
        Color bgColor;
        if ((bgColor = getBackground()) != null) {
            g2d.setColor(bgColor);
            g2d.fillRect(0, 0,  bounds.width,  bounds.height);
        }
        g2d.setColor(getForeground());
        String text = getText();
        g2d.setFont(getFont());
        FontMetrics fontMetrics = g2d.getFontMetrics();
        int textHeight = fontMetrics.getHeight();
        int textWidth = fontMetrics.stringWidth(text);

        int x0 =  bounds.width / 2;
        int y0 =  bounds.height / 2;

/*  // debug, axes and border
        g2d.drawLine( x0, 0, x0, bounds.height);
        g2d.drawLine( 0, y0, bounds.width, y0);
        g2d.drawRect(0, 0,  bounds.width,  bounds.height);  // without rotation
//*/

        if (rotation == 0) {
            g2d.drawString(text, 0, y0 + fontMetrics.getDescent());
        } else {
            g2d.rotate(rotation, x0, y0);
            g2d.drawString(text, x0 - bounds.height / 2, y0 + fontMetrics.getDescent());
        }
        g2d.dispose();
    }
}
