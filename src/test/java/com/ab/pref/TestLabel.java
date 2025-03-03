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

import com.ab.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class TestLabel {
    static final boolean USE_PLabel = false;
    JFrame mainFrame;

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new TestLabel(args);
            }
        });
    }

    public TestLabel(String[] args) {
        Logger.set(System.out);
        mainFrame = new JFrame();
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setBounds(30, 200, 600, 900);
        JPrefPanel jPanel = new JPrefPanel("test");
        Container mainContainer = mainFrame.getContentPane();
        mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.X_AXIS));
/*
        mainFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Logger.printf("main.%s -> %s\n", Thread.currentThread().getStackTrace()[1].getMethodName(), e);
//            mainRectangle = ((JFrame)e.getSource()).getBounds();
                jPanel.resized();
            }
        });
*/
        mainFrame.add(jPanel);
        mainFrame.setState(Frame.NORMAL);
        mainFrame.setVisible(true);
    }

    public class JPrefPanel extends JPanel {
        PLabel westLabel, eastLabel, southLabel;

        public JPrefPanel(String text) {
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
//                    Logger.printf("jPrefPanel.%s -> %s\n", Thread.currentThread().getStackTrace()[1].getMethodName(), e);
                    resized();   // repaint
                }
            });

            if (USE_PLabel) {
                this.setOpaque(true);
                this.setBackground(Color.green);
                this.setLayout(null);
                westLabel = new PLabel(Math.PI / 2); // west
                westLabel.setText("West");
                this.add(westLabel);

                eastLabel = new PLabel(-Math.PI / 2);  // east
                eastLabel.setText("East");
                this.add(eastLabel);

                southLabel = new PLabel(0);  // south
                southLabel.setText("South");
                this.add(southLabel);
                resized();
            }
        }

        public void resized() {
            if (!USE_PLabel) {
                return;
            }
            Rectangle bounds = this.getBounds();
            Logger.printf("jPrefPanel.%s -> %s\n", Thread.currentThread().getStackTrace()[1].getMethodName(), bounds);
            int fontSizeW = 40, fontSizeE = 40, fontSizeS = 40;
            int heightW = bounds.height / 4;
            int heightE = bounds.height / 4;
            int widthS = bounds.width / 2;
            westLabel.setFont(new Font("Serif", Font.PLAIN, fontSizeW));
            westLabel.setBounds(bounds.x, bounds.y + bounds.height - heightW, fontSizeW, heightW);

            eastLabel.setFont(new Font("Serif", Font.PLAIN, fontSizeE));
            eastLabel.setBounds(bounds.x + bounds.width - fontSizeE,
                    bounds.y + bounds.height - heightE, fontSizeE, heightE);

            southLabel.setFont(new Font("Serif", Font.PLAIN, fontSizeS));
            southLabel.setBounds(bounds.x + (bounds.width - widthS) / 2,
                    bounds.y + bounds.height - fontSizeS, widthS, fontSizeS);

            mainFrame.getContentPane().validate();
            mainFrame.getContentPane().repaint();
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (USE_PLabel) {
                return;
            }

            Graphics2D g2d = (Graphics2D) g;
            Rectangle r;
//            r = new Rectangle(100, 40, 40, 300);
//            r = new Rectangle(100, 40, 40, 400);
            r = new Rectangle(100, 40, 60, 400);
//            r = new Rectangle(100, 40, 80, 400);
//            r = new Rectangle(100, 40, 80, 300);
//            r = new Rectangle(100, 40, 20, 400);
//            r = new Rectangle(100, 40, 40, 300);

            drawLabel(g2d, "a string", -Math.PI / 2, r);
            r.x += 200;
            drawLabel(g2d, "a string", Math.PI / 2, r);
        }

        private void drawLabel(Graphics g, String text, double rotation, Rectangle r) {

            Graphics2D g2d = (Graphics2D)g.create();
            Rectangle bounds = this.getBounds();
            g2d.setFont(new Font("Serif", Font.PLAIN, 40));
            FontMetrics fontMetrics = g2d.getFontMetrics();
            int textHeight = fontMetrics.getHeight();
            int textWidth = fontMetrics.stringWidth(text);

            int x0 = r.x + r.width / 2;
            int y0 = r.y + r.height / 2;

            // axes
            g2d.drawLine( x0, bounds.y, x0, bounds.y + bounds.width);
            g2d.drawLine( bounds.x, y0, bounds.x + bounds.height, y0);

            g2d.drawRect(r.x, r.y, r.width, r.height);  // vertical

//            g2d.drawString(text, x0 - textWidth / 2, y0 + fontMetrics.getDescent());       // not rotated!

            g2d.rotate(rotation, x0, y0);
//            g2d.drawString(text, r.x, y0 + fontMetrics.getDescent());
//            g2d.drawString(text, x0 - textWidth / 2, y0 + fontMetrics.getDescent());
            g2d.drawString(text, x0 - r.height / 2, y0 + fontMetrics.getDescent());
            g2d.dispose();
        }
    }
}