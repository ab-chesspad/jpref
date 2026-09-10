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
 * Created: 3/4/2025
 */

package com.ab.jpref.gui;

import com.ab.jpref.engine.GameManager;
import static com.ab.jpref.engine.GameManager.RestartCommand;
import com.ab.jpref.engine.Player;
import com.ab.jpref.config.Metrics;
import com.ab.jpref.gui.config.PConfig;
import com.ab.jpref.gui.widgets.PLabel;
import static com.ab.jpref.config.I18n.m;

import com.ab.jpref.ui.Host;
import com.ab.jpref.ui.Scoresheet;
import com.ab.jpref.ui.TableLayout;
import static com.ab.jpref.ui.Scoresheet.Widget;
import static com.ab.jpref.ui.Scoresheet.Line;

import com.ab.util.*;
import com.ab.util.Point;

import static com.ab.util.Util.currMethodName;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

/**
 *  for description look at etc/doc/scores.jpg
 */
public class StatusPopup extends JDialog {
    static final boolean DEBUG_LOG = false;

    public static final double MAGIC_ASPECT_RATIO = 434d / 619d;
    public static final double MAGIC_HEIGHT_FACTOR = 0.5d;

    static final Color lineColor = Color.black;
    static final Color poolSizeColor = Color.decode("#008000");
    static final int strokeWidth = 2;

    static Scoresheet scoresheet;

    private final Metrics metrics;
    private final PConfig pConfig;

    boolean withButtons;
    final JPanel buttonPanel;
    Rectangle popupRectangle;
    final ScoresPanel scoresPanel;
    RestartCommand result = RestartCommand.newRound;
    int historySize;

    StatusPopup(Host host, boolean withButtons) {
        super(Main.mainFrame, true);
        this.withButtons = withButtons;
        setTitle(m("Scores"));
        setLayout(new BorderLayout(1, 4));
        metrics = host.getMetrics();
        pConfig = (PConfig)host.config();
        popupRectangle = pConfig.scoresPopupRectangle.get();
        if (popupRectangle.width == 0) {
            Rectangle mainRectangle = new Rectangle();
            mainRectangle.width = pConfig.mainSize.first;
            mainRectangle.height = pConfig.mainSize.second;
            mainRectangle.x = pConfig.mainPosition.getX();
            mainRectangle.y = pConfig.mainPosition.getY();
            popupRectangle.height = (int)(mainRectangle.width * MAGIC_HEIGHT_FACTOR);
            popupRectangle.width = (int)(popupRectangle.height / MAGIC_ASPECT_RATIO);
            popupRectangle.x = mainRectangle.x +
                (mainRectangle.width - popupRectangle.width) / 2;
            popupRectangle.y = mainRectangle.y +
                (mainRectangle.height - popupRectangle.height) / 2;
            Logger.printf(DEBUG_LOG, "popup %s\n", popupRectangle);
        }
        if (popupRectangle.y < 10) {
            popupRectangle.y = 10;
        }
        this.setBounds(popupRectangle);
        this.setLocation(popupRectangle.x, popupRectangle.y);
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Logger.printf(DEBUG_LOG, "ScoresPanel.%s -> %s\n", currMethodName(), e);
                popupRectangle = StatusPopup.this.getBounds();
                pConfig.scoresPopupRectangle.set(popupRectangle);
                scoresPanel.recalc();
                repaint();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                Logger.printf(DEBUG_LOG, "%s -> %s\n", currMethodName(), e);
                popupRectangle = StatusPopup.this.getBounds();
                pConfig.scoresPopupRectangle.set(popupRectangle);
            }
        });

        buttonPanel = createButtonPanel();
        if (withButtons) {
            add(buttonPanel, BorderLayout.SOUTH);
        }

        if (scoresheet == null) {
            scoresheet = new Scoresheet();
        }

        scoresPanel = new ScoresPanel();
        add(scoresPanel, BorderLayout.NORTH);

        historySize = GameManager.getInstance().getPlayers()[0].getGameHistory().size();
        if (!withButtons) {
            --historySize;
        }

        setVisible(true);   // blocks until dialog ends
        Logger.println(DEBUG_LOG, "StatusPopup done");
    }

    Rectangle getScoresRectangle() {
        Rectangle r = new Rectangle(this.getBounds());
        Insets insets = getInsets();
        r.height -= insets.top + buttonPanel.getBounds().height + 2;
        return r;
    }

    private class ScoresPanel extends JPanel {
        final List<Pair<PLabel, Widget>> labels = new ArrayList<>();

        ScoresPanel() {
            setLayout(null);
            this.setBackground(Color.white);
            for (Widget widget : Scoresheet.widgets) {
                PLabel pLabel = new PLabel(widget.getRotation());
                pLabel.setBackground(Color.white);
                labels.add(new Pair<>(pLabel, widget));
                add(pLabel);
            }
            recalc();
        }

        public void recalc() {
            Rectangle scoresRectangle = getScoresRectangle();
            Logger.printf(DEBUG_LOG, "ScoresPanel.%s -> %s\n", currMethodName(), scoresRectangle);
            scoresheet.recalc(scoresRectangle.width, scoresRectangle.height, pConfig.poolSize.get(), withButtons);
            StringBuilder sb = new StringBuilder();
            for (Pair<PLabel, Widget> pair : labels) {
                PLabel pLabel = pair.first;
                Widget widget = pair.second;
                pLabel.setBounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
                pLabel.setFontSize(scoresheet.getFontSize());

                String text = widget.getText();
                Font f = pLabel.getFont();
                Canvas c = new Canvas();
                FontMetrics fontMetrics = c.getFontMetrics(f);
                int textWidth = fontMetrics.stringWidth(text);
                int labelW = widget.getWidth();
                if (widget.getRotation() != 0) {
                    labelW = widget.getHeight();
                }
                if (textWidth >= labelW - 10) {
                    sb.delete(0, sb.length());
                    final String front = "…";
                    sb.append(front);
                    String sep = "";
                    if (text.endsWith(Scoresheet.POOL_COMPLETE)) {
                        sep = Scoresheet.POOL_COMPLETE;
                        text = text.substring(0, text.length() - sep.length());
                    }
                    String[] parts = text.split("\\.");
                    for (int i = parts.length - 1; i >= 0; --i) {
                        String chunk = parts[i];
                        textWidth = fontMetrics.stringWidth(sb + chunk + sep);
                        if (textWidth > labelW) {
                            break;
                        }
                        sb.insert(front.length(), sep).insert(front.length(), chunk);
                        sep = ".";
                    }
                    text = new String(sb);
                }
                if (widget.getChange() != 0) {
                    if (widget.getLabel().equals(Player.PlayerPoints.status)) {
                        int change = widget.getChange();
                        String sym = "&#9650;"; // ▲
                        String color = "#00CC00";
                        if (change < 0) {
                            change = -change;
                            sym = "&#9660;"; // ▼
                            color = "#FF0000";
                        }
                        text = String.format("<html>%s <span style=\"color: %s\">%s%d</span></html>",
                            text, color, sym, change);
                    } else {
                        int index = text.lastIndexOf(".");
                        text = String.format("<html>%s<span style=\"color: #0000EE\">%s</span></html>",
                            text.substring(0, index + 1), text.substring(index + 1));
                    }
                }
                pLabel.setText(text);
            }
            Rectangle popupRectangle = new Rectangle(StatusPopup.this.popupRectangle);
            Rectangle buttonPanelBounds = StatusPopup.this.buttonPanel.getBounds();
            buttonPanelBounds.x = (popupRectangle.width - buttonPanelBounds.width) / 2;
            buttonPanelBounds.y = popupRectangle.height - buttonPanelBounds.height;
            StatusPopup.this.buttonPanel.setBounds(buttonPanelBounds);
        }

        @Override
        public void repaint() {
            this.invalidate();
            this.validate();
            super.repaint();
            StatusPopup.this.invalidate();
            StatusPopup.this.validate();
            StatusPopup.this.repaint();
        }

        @Override
        public Dimension getSize() {
            return new Dimension(
                StatusPopup.this.getScoresRectangle().width,
                StatusPopup.this.getScoresRectangle().height);
        }

        @Override
        public Dimension getPreferredSize() {
            return getSize();
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Logger.printf(DEBUG_LOG, "ScoresPanel.%s -> %s\n", currMethodName(),
                StatusPopup.this.getScoresRectangle());
            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(Color.white);
            g2d.fillRect(0, 0,
                StatusPopup.this.getScoresRectangle().width,
                StatusPopup.this.getScoresRectangle().height);
            g2d.setStroke(new BasicStroke(strokeWidth));
            g2d.setColor(lineColor);
            for (Line line : scoresheet.getLines()) {
                Couple<Point> points = line.points;
                g2d.drawLine(points.first.getX(), points.first.getY(), points.second.getX(), points.second.getY());
            }

            // center circle:
            int circleRadius = scoresheet.getCircleRadius();
            Point center = scoresheet.getCenter();
            g2d.fillOval(center.getX() - circleRadius,
                center.getY() - circleRadius,
                2 * circleRadius, 2 * circleRadius);
            int innerRadius = circleRadius - strokeWidth;
            g2d.setColor(Color.white);
            g2d.fillOval(center.getX() - innerRadius,
                center.getY() - innerRadius,
                2 * innerRadius, 2 * innerRadius);

            // pool size:
            Font font = new Font("Serif", Font.PLAIN, innerRadius);
            g2d.setFont(font);
            String text = "" + pConfig.poolSize.get();
            FontMetrics fontMetrics = g2d.getFontMetrics(font);
            int _width = fontMetrics.stringWidth(text);
            int x = center.getX() - _width / 2 - metrics.xMargin;
            int y = center.getY() + fontMetrics.getDescent() + metrics.yMargin;
            g2d.setColor(poolSizeColor);
            g2d.drawString(text, x, y);
        }
    }

    private JPanel createButtonPanel() {
        JPanel jPanel = new JPanel();
        JButton goonButton = new JButton(TableLayout.ButtonCommand.goon.getName());
        goonButton.addActionListener(actionEvent -> {
            StatusPopup.this.dispose();
            result = RestartCommand.newRound;
        });
        jPanel.add(goonButton);
        JButton replayButton = new JButton(TableLayout.ButtonCommand.replay.getName());
        replayButton.addActionListener(actionEvent -> {
            StatusPopup.this.dispose();
            result = RestartCommand.replay;
        });
        jPanel.add(replayButton);
        return  jPanel;
    }
}