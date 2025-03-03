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
 * Created: 2/28/2025
 *
 * This is my clumsy attempt to improve Swing JLabel HTML handling which is even clumsier
 *
 */

package com.ab.pref;

import com.ab.util.Logger;

import javax.swing.*;
import java.awt.*;

public class HtmlLabel extends JLabel {
    private final Rectangle parentBounds = new Rectangle();

    public HtmlLabel(String text) {
        super(text);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        Rectangle parentBounds = this.getParent().getBounds();
        if (this.parentBounds.width != parentBounds.getWidth() || this.parentBounds.height != parentBounds.getHeight()) {
            reformat();
            this.parentBounds.width = parentBounds.width;
            this.parentBounds.height = parentBounds.height;
        }
    }

    @Override
    public synchronized void paintComponent(Graphics g) {
        super.paintComponent(g);
    }

    private void reformat() {
        final String lb = "<br/>";
        String text = getText();
        int lineWidth = getParent().getBounds().width;
        Graphics g = this.getGraphics();
        FontMetrics fontMetrics = g.getFontMetrics();

        text = text.replaceAll("<head.*?head>|\r", "");    // remove <head>
        String[] lines = text.split("\n|<p>|</p>|<br>|<br/>");
//        int emptyLines = 0;
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
/*
                if (++emptyLines != 2) {
                    continue;   // only single empty lines
                }
                sb.append(lb);
*/
                continue;
            }
//            emptyLines = 0;
            line = line.replaceAll("<a.*?>|</a>", "");    // remove all unprintable tags
            int textWidth = fontMetrics.stringWidth(line);
            if (textWidth > lineWidth) {
                String[] words = line.split("\\s+");
                StringBuilder _sb = new StringBuilder();
                String sep = "";
                for (String word : words) {
                    String soFar = _sb.toString();
                    String test = soFar + sep + word;
                    if (fontMetrics.stringWidth(test) > lineWidth) {
                        sb.append(soFar).append(lb);
                        sep = "";
                        _sb.delete(0, _sb.length());
                    }
                    _sb.append(sep).append(word);
                    sep = " ";
                }
                line = _sb.toString();
            }
            if (!line.isEmpty()) {
                sb.append(line).append(lb);
            }
        }
        text = sb.toString();
        setText(text);
    }
}
