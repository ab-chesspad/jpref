/*
 * Copyright (c) 1995, 2008, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle or the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.ab.pref;

/* HtmlDemo.java needs no other files. */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class HtmlDemo extends JPanel
        implements ActionListener {
    JLabel theLabel;
    JTextArea htmlTextArea;

    public HtmlDemo() {
        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

/*
        String initialText = "<html>\n" +
                "Color and font test:\n" +
                "<ul>\n" +
                "<li><font color=red>red</font>\n" +
                "<li><font color=blue>blue</font>\n" +
                "<li><font color=green>green</font>\n" +
                "<li><font size=-2>small</font>\n" +
                "<li><font size=+2>large</font>\n" +
                "<li><i>italic</i>\n" +
                "<li><b>bold</b>\n" +
                "</ul>\n";
*/

        htmlTextArea = new JTextArea(10, 200);
        htmlTextArea.setText(text);
        JScrollPane scrollPane = new JScrollPane(htmlTextArea);

        JButton changeTheLabel = new JButton("Change the label");
        changeTheLabel.setMnemonic(KeyEvent.VK_C);
        changeTheLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        changeTheLabel.addActionListener(this);

        theLabel = new JLabel(text) {
            private Dimension scale() {
                return new Dimension(500, 500);
            }
            public Dimension getPreferredSize() {
                return scale();
            }
            public Dimension getMinimumSize() {
                return scale();
            }
            public Dimension getMaximumSize() {
                return scale();
            }
        };
        theLabel.setVerticalAlignment(SwingConstants.CENTER);
        theLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.PAGE_AXIS));
        leftPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        "Edit the HTML, then click the button"),
                BorderFactory.createEmptyBorder(10,10,10,10)));
        leftPanel.add(scrollPane);
        leftPanel.add(Box.createRigidArea(new Dimension(0,10)));
        leftPanel.add(changeTheLabel);

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.PAGE_AXIS));
        rightPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("A label with HTML"),
                BorderFactory.createEmptyBorder(10,10,10,10)));
        rightPanel.add(theLabel);

        setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        add(leftPanel);
        add(Box.createRigidArea(new Dimension(10,0)));
        add(rightPanel);
    }

    //React to the user pushing the Change button.
    public void actionPerformed(ActionEvent e) {
        theLabel.setText(htmlTextArea.getText());
    }

    /**
     * Create the GUI and show it.  For thread safety,
     * this method should be invoked from the
     * event dispatch thread.
     */
    private static void createAndShowGUI() {
        //Create and set up the window.
        JFrame frame = new JFrame("HtmlDemo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Add content to the window.
        frame.add(new HtmlDemo());

        //Display the window.
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        //Schedule a job for the event dispatch thread:
        //creating and showing this application's GUI.
        SwingUtilities.invokeLater(() -> {
            //Turn off metal's use of bold fonts
            UIManager.put("swing.boldMetal", Boolean.FALSE);
            createAndShowGUI();
        });
    }

    public static String text =
            "<html>\n" +
                    "<head>\n" +
                    "<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">\n" +
                    "<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>\n" +
                    "<link href=\"https://fonts.googleapis.com/css2?family=Cedarville+Cursive&display=swap\" rel=\"stylesheet\">\n" +
                    "\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "<h2><div style=\"text-align: center;\">\n" +
                    "JPref V 0.0.0.1\n" +
                    "</div></h2>\n" +
                    "<p>\n" +
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
