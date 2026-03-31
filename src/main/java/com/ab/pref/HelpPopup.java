/*  This file is part of jpref.
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
 * Copyright (C) 2026 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 2/16/26
 *
 */

package com.ab.pref;

import com.ab.pref.MainPanel.Host;
import com.ab.pref.config.Metrics;
import com.ab.jpref.config.I18n;
import com.ab.pref.config.PConfig;

import javax.swing.*;
import java.awt.*;

public class HelpPopup extends JDialog {
    final Metrics metrics = Metrics.getInstance();

    static final String versionVar = "<!-- *** VERSION ***-->";
    static final String remoteMark = "<!--*** REMOTE ***-->";

    HelpPopup(Host host) {
        super(host.mainFrame(), false);
        setTitle(I18n.m("Help"));
        Rectangle mainRectangle = (Rectangle)PConfig.getInstance().mainRectangle.get().clone();
        setSize(mainRectangle.width, mainRectangle.height);
        setLocationRelativeTo(host.mainFrame());
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        String src = I18n.loadString("index.html")
            .replace(versionVar, PConfig.VERSION);
        StringBuilder sb = new StringBuilder();
        int start = 0;
        int end;
        while ((end = src.indexOf(remoteMark, start)) >= 0) {
            sb.append(src, start, end);
            start = src.indexOf(remoteMark, end + 1);
            if (start < 0) {
                start = src.length();
                break;
            }
            start += remoteMark.length();
        }
        sb.append(src, start, src.length());
        String text = sb.toString();

        JTextPane textPane = new JTextPane();
        textPane.setContentType("text/html");
        textPane.setText(text);
        textPane.setEditable(false);

        JScrollPane jScrollPane = new JScrollPane(textPane);
        panel.add(jScrollPane);

        panel.add(Box.createVerticalGlue()); // Add vertical glue above button
        JButton b = new JButton(I18n.m("Continue"));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setFont(metrics.font);
        panel.add(b);
        b.addActionListener(e -> {
//            Logger.printf("helpPopup: %s\n", e.toString());
            dispose();
        });
        add(panel);
        setVisible(true);
    }
}
