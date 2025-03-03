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
 * Created: 2/21/2025
 */
package com.ab.pref;

import com.ab.util.I18n;
import com.ab.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class NotesPopup extends JDialog {
    public static Logger logger;

    NotesPopup(JFrame frame) {
        super(frame, false);
        setTitle(I18n.m("Комментарий"));
        setSize(Main.mainRectangle.width / 2, Main.mainRectangle.height / 2);
//        this.setBounds(bounds);

        setLocationRelativeTo(frame);
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(I18n.m("Ваши замечания"));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(label);

        JTextArea text = new JTextArea();
        text.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(text);

        panel.add(Box.createVerticalGlue()); // Add vertical glue above button
        JButton b = new JButton(I18n.m("Запомнить и закрыть"));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(b);
        b.addActionListener(evnt -> {
            Logger.println(text.getText());
            dispose();
        });
        add(panel);
        setVisible(true);
    }

/*
    Dimension rescale() {
        int width = metrics.panelWidth / 2;
        int height = metrics.panelHeight / 2;
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

    @Override
    public Rectangle getBounds() {
        Rectangle r = super.getBounds();
        Dimension d = rescale();
        r.width = d.width;
        r.height = d.height;
        return r;
    }
*/

}
