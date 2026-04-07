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
 * Created: 2/21/2025
 */
package com.ab.pref;

import com.ab.pref.config.Metrics;
import com.ab.pref.config.PConfig;
import com.ab.pref.config.PConfig.Host;
import com.ab.util.Logger;
import static com.ab.jpref.config.I18n.m;

import javax.swing.*;
import java.awt.*;

public class NotesPopup extends JDialog {
    final Metrics metrics = Metrics.getInstance();

    NotesPopup(Host host) {
        super(host.mainFrame(), false);
        setTitle(m("Comments"));
        Rectangle mainRectangle = (Rectangle)PConfig.getInstance().mainRectangle.get().clone();
        setSize(mainRectangle.width, mainRectangle.height);

        setLocationRelativeTo(host.mainFrame());
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(m("Your notes"));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setFont(metrics.font);
        panel.add(label);

        JTextArea text = new JTextArea();
        text.setAlignmentX(Component.CENTER_ALIGNMENT);
        text.setFont(metrics.font);
        panel.add(text);

        panel.add(Box.createVerticalGlue()); // Add vertical glue above button
        JButton b = new JButton(m("Record Notes"));
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