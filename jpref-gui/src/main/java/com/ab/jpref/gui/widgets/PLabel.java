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
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;

public class PLabel extends JLabel {
    protected final double rotation;

    public PLabel(double rotation) {
        setOpaque(false);
        this.rotation = rotation;
    }

    // edge points, not width and height!
    public void setPBounds(int _x0, int _y0, int _x1, int _y1) {
        int x0 = Math.min(_x0, _x1);
        int x1 = Math.max(_x0, _x1);
        int y0 = Math.min(_y0, _y1);
        int y1 = Math.max(_y0, _y1);
        int w = x1 - x0;
        int h = y1 - y0;
        super.setBounds(x0, y0, w, h);
    }

    public void setFontSize(int size) {
        Font f = this.getFont();
        this.setFont(new Font(f.getName(), f.getStyle(), size));
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
        FontMetrics fontMetrics = g2d.getFontMetrics();

        int x0 = bounds.width / 2;
        int y0 = bounds.height / 2;

/*  debug, axes and border
        g2d.drawLine( x0, 0, x0, bounds.height);
        g2d.drawLine( 0, y0, bounds.width, y0);
        g2d.drawRect(0, 0,  bounds.width,  bounds.height);  // without rotation
//*/
        if (BasicHTML.isHTMLString(text)) {
            View view = BasicHTML.createHTMLView(this, text);

            float viewWidth = view.getPreferredSpan(View.X_AXIS);
            float viewHeight = view.getPreferredSpan(View.Y_AXIS);
            int x = x0 - bounds.height / 2;
            if (getHorizontalAlignment() == SwingConstants.CENTER) {
                x = x0 - Math.round(viewWidth / 2);
            }
            int y = y0 - Math.round(viewHeight / 2);

            g2d.rotate(rotation, x0, y0);
            view.paint(g2d, new Rectangle(x, y, Math.round(viewWidth), Math.round(viewHeight)));
            g2d.dispose();
            return;
        }

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