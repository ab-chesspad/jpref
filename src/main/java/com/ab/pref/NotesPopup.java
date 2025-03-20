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
    Metrics metrics = Metrics.getInstance();

    NotesPopup(JFrame frame) {
        super(frame, false);
        setTitle(I18n.m("Comments"));
        setSize(Main.mainRectangle.width, Main.mainRectangle.height);

        setLocationRelativeTo(frame);
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(I18n.m("Your notes"));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setFont(metrics.font);
        panel.add(label);

        JTextArea text = new JTextArea();
        text.setAlignmentX(Component.CENTER_ALIGNMENT);
        text.setFont(metrics.font);
        panel.add(text);

        panel.add(Box.createVerticalGlue()); // Add vertical glue above button
        JButton b = new JButton(I18n.m("Record Notes"));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setFont(metrics.font);
        panel.add(b);
        b.addActionListener(evnt -> {
            Logger.printf("\ncomment: %s\n", text.getText());
            dispose();
        });
        add(panel);
        setVisible(true);
    }
}
