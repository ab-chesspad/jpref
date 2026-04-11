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
 * Created: 3/1/2025
 */
package com.ab.pref;

import com.ab.jpref.config.I18n;
import com.ab.pref.config.Metrics;
import com.ab.pref.config.PConfig;
import com.ab.pref.widgets.HtmlLabel;
import com.ab.util.Logger;
import org.junit.Ignore;

import javax.swing.*;
import java.awt.*;

@Ignore
public class TestHtml {
    static final Metrics metrics = Metrics.getInstance();
    final JFrame mainFrame;
    static Rectangle mainRectangle;
    MainPanel mainPanel;

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> new TestHtml());
    }

    public TestHtml() {
        mainFrame = new JFrame();
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setBounds(1000, 1000, 400, 600);
        metrics.cardW = 50;
        metrics.cardH = 80;

        Container mainContainer = mainFrame.getContentPane();
        mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.X_AXIS));
        MainPanel mainPanel = new MainPanel();
        mainContainer.add(mainPanel);
        mainFrame.setState(Frame.NORMAL);
        mainFrame.setVisible(true);
        popup();
    }

    class MainPanel extends JPanel {
        GridBagConstraints gbc = new GridBagConstraints();
        LayoutManager layout = new GridBagLayout();

        public MainPanel() {
            setMainPanel();
        }

        private void setMainPanel() {
            this.setOpaque(true);
            this.setBackground(Color.red);
            JButton jButton = new JButton("pop up");
            jButton.addActionListener(actionEvent -> {
                Logger.println(actionEvent);
                popup();
            });
            this.add(jButton);
        }
    }

    boolean popupShown = false;
    static final String versionVar = "<!-- *** VERVION ***-->";
    static final String remoteMark = "<!--*** REMOTE ***-->";
    private void popup() {
        if (popupShown) {
            return;
        }
        popupShown = true;
        String src = I18n.loadString("index.html").replace(versionVar, PConfig.VERSION);
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
        UIManager.put("OptionPane.minimumSize",new Dimension(500,300));
        JLabel label = new HtmlLabel(text);
        label.setFont(new Font("Arial", Font.PLAIN, 20));
        JScrollPane jScrollPane = new JScrollPane(label);
        jScrollPane.setAutoscrolls(true);
        jScrollPane.setPreferredSize(new Dimension( 800,10));

        JOptionPane.showMessageDialog(null, jScrollPane,"About JPref v.0.0.0.1", JOptionPane.PLAIN_MESSAGE);
        popupShown = false;

    }
}