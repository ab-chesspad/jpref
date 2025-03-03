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
 * Created: 3/1/2025
 */
package com.ab.pref;

import com.ab.util.I18n;
import com.ab.util.Logger;
//import com.sun.java.util.jar.pack.Attribute;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Locale;

public class TestHtml {
    static Metrics metrics = Metrics.getInstance();
    JFrame mainFrame;
    static Rectangle mainRectangle;
    MainPanel mainPanel;

    public static void main(String[] args) {
        Logger.set(System.out);
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new TestHtml();
            }
        });
    }

    public TestHtml() {
        Locale.setDefault(new Locale("ru", "RU"));
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
            jButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    Logger.println(actionEvent.toString());
                    popup();
                }
            });
            this.add(jButton);
        }
    }

    boolean popupShown = false;
    private void popup() {
        if (popupShown) {
            return;
        }
        popupShown = true;
        String text = I18n.loadString("index.html");
        UIManager.put("OptionPane.minimumSize",new Dimension(500,300));
        JLabel label = new HtmlLabel(text);
        label.setFont(new Font("Arial", Font.PLAIN, 20));
        JScrollPane jScrollPane = new JScrollPane(label);
//        JScrollPane jScrollPane = new JScrollPane(new JLabel((text)));
        jScrollPane.setAutoscrolls(true);
        jScrollPane.setPreferredSize(new Dimension( 800,10));
//        label.add(new JLabel((text)));

        JOptionPane.showMessageDialog(null, jScrollPane,"About JPref v.0.0.0.1", JOptionPane.PLAIN_MESSAGE);
        popupShown = false;

    }

    public static String text =
            "<html>\n" +
                    "<head>" +
                    "<meta content=\"text/html; charset=utf-8\">" +
                    "</head>\n" +
                    "<body>\n" +
//                    "<font size=5>JPref V 0.0.0.1</font>\n" +
//                    "<p>\n" +
                    "Целью этого проекта является создание программы, играющей в преферанс, два бота против человека.\n" +
                    "Игра по-честному, без подглядывания и жульничества.\n" +
                    "<br/>\n" +
                    "Предлагаемая версия еще очень далека от этой цели. Она представляет собой графическую платформу для тестирования.<br/>\n" +
                    "Умеет только торговаться и играть распасы.\n" +
                    "</p>\n" +
                    "Способы посылки:<br/>\n" +
                    "<ul>\n" +
                    "    <li>Через меню. Ограничение - только текущий лог и только один раз в день.</li>\n" +
                    "    <li>Через github - open new issue.</li>\n" +
                    "    <li>Послать лог файл на <a href=\"mailto:ab.jpref@gmail.com\">ab.jpref@gmail.com</a></li>\n" +
                    "</ul>\n" +
                    "Пожалуйста, примите участие в разработке и/или тестировании.<br/>\n" +
                    "Спасибо, что дочитали до конца.<br/>\n" +
                    "\n" +
                    "\n" +
                    "</body>\n" +
                    "</html>\n"
        ;
}
