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
 * Created: 3/4/25
 */

package com.ab.pref;

import com.ab.jpref.engine.GameManager;
import com.ab.jpref.engine.Player;
import com.ab.util.I18n;
import com.ab.util.Logger;
import com.ab.util.Point;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
//import java.awt.geom.Line2D;
import java.util.LinkedList;
import java.util.List;

/**
 * description in etc/doc/scores.jpg
 */
public class StatusPopup extends JDialog {
    static final boolean DEBUG = false;

    static final int leftPoints = Player.PlayerPoints.leftPoints.ordinal();
    static final int rightPoints = Player.PlayerPoints.rightPoints.ordinal();
    static final int poolPoints = Player.PlayerPoints.poolPoints.ordinal();
    static final int dumpPoints = Player.PlayerPoints.dumpPoints.ordinal();
    static final int statusPoints = Player.PlayerPoints.status.ordinal();

    static final Color lineColor = Color.black;
    static final Color poolSizeColor = Color.decode("#008000");

    static final double centerYOffset = .4;         // relative to cardW size
    static final int strokeWidth = 2;
    static final double scoreFontSize = .4;         // relative to cardW size
    static final double panelHeight = .7;           // left, right, pool, dump points, relative to cardW

    static final double centerCircleRadius = 1;     // relative to cardW size

    static final int South = MainPanel.Alignment.South.ordinal(),
        West = MainPanel.Alignment.West.ordinal(),
        East = MainPanel.Alignment.East.ordinal();

    public static final PConfig pConfig = PConfig.getInstance();

    static StatusPopup instance;
    Rectangle popupRectangle;
    ScoresPanel scoresPanel;
    ButtonPanel buttonPanel;
    ScoresMetrics scoresMetrics = new ScoresMetrics();
//    boolean abort;

    StatusPopup(JFrame frame, boolean withButtons) {
        super(frame, true);
        instance = this;
        setTitle(I18n.m("Scores"));
        setLayout(new BorderLayout(1, 4));
        popupRectangle = pConfig.scoresPopupRectangle.get();
        if (popupRectangle.width == 0) {
            popupRectangle = (Rectangle) Main.mainRectangle.clone();
        }
        this.setBounds(popupRectangle);
        this.setLocation(popupRectangle.x, popupRectangle.y);
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Logger.printf(DEBUG, "ScoresPanel.%s -> %s\n", Thread.currentThread().getStackTrace()[1].getMethodName(), e);
                popupRectangle = StatusPopup.instance.getBounds();
                pConfig.scoresPopupRectangle.set(popupRectangle);
                scoresPanel.recalc();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                Logger.printf(DEBUG, "%s -> %s\n", Thread.currentThread().getStackTrace()[1].getMethodName(), e);
                popupRectangle = StatusPopup.instance.getBounds();
                pConfig.scoresPopupRectangle.set(popupRectangle);
            }

        });

        buttonPanel = createButtonPanel();
        if (withButtons) {
            add(buttonPanel, BorderLayout.SOUTH);
        }

        scoresPanel = new ScoresPanel();
        add(scoresPanel, BorderLayout.NORTH);

        setVisible(true);   // blocks until dialog ends
    }

    Rectangle getScoresRectangle() {
        Rectangle r = (Rectangle) this.getBounds().clone();
        Insets insets = getInsets();
        r.height -= insets.top + buttonPanel.getBounds().height + 2;
        return r;
    }

    private class ScoresPanel extends JPanel {
        final private PlayerArea[] playerAreas = new PlayerArea[MainPanel.Alignment.values().length];
        private final Metrics metrics = Metrics.getInstance();

        ScoresPanel() {
            setLayout(null);
            this.setBackground(Color.white);
            for (int i = 0; i < playerAreas.length; ++i) {
                PlayerArea playerArea = new PlayerArea(i);
                playerAreas[i] = playerArea;
                for (SLabel sLabel : playerArea.pLabels) {
                    add(sLabel);
                }
            }
            recalc();
        }

        public void recalc() {
            Rectangle scoresRectangle = StatusPopup.instance.getScoresRectangle();
            Logger.printf(DEBUG, "ScoresPanel.%s -> %s\n", Thread.currentThread().getStackTrace()[1].getMethodName(), scoresRectangle);

            // center:
            scoresMetrics.p0 = new Point(scoresRectangle.width / 2, (int) (scoresRectangle.height * centerYOffset));
            scoresMetrics.tan = scoresRectangle.height * (1 - centerYOffset) / scoresMetrics.p0.getX();
            int h = (int) (metrics.cardW * panelHeight);
            int x = (int) (h / scoresMetrics.tan);
            if (scoresMetrics.tan > 1) {
                x = (int) (metrics.cardW * panelHeight);
                h = (int) (x * scoresMetrics.tan);
            }
            scoresMetrics.p1 = new Point(x, scoresRectangle.height - h);
            scoresMetrics.p2 = new Point(2 * x, scoresRectangle.height - 2 * h);
            scoresMetrics.p3 = new Point(scoresRectangle.width - 2 * x, scoresRectangle.height - 2 * h);
            scoresMetrics.p4 = new Point(scoresRectangle.width - x, scoresRectangle.height - h);
            scoresMetrics.p5 = new Point(scoresRectangle.width / 2, scoresRectangle.height - h);
            scoresMetrics.p6 = new Point(x, scoresMetrics.p0.getY());
            scoresMetrics.p7 = new Point(scoresRectangle.width - x, scoresMetrics.p0.getY());
            int circleRadius = (int) (metrics.cardW * centerCircleRadius) + 2;  // 2 pixels y-margin
            int y = scoresMetrics.p0.getY() + circleRadius;
            int _x = (int) (circleRadius / scoresMetrics.tan);
            scoresMetrics.p8 = new Point(scoresMetrics.p0.getX() - _x, y);
            scoresMetrics.p9 = new Point(scoresMetrics.p0.getX() + _x, y);

/* verification
            double d = Line2D.ptSegDist(0, scoresRectangle.height,
                    scoresMetrics.p0.getX(), scoresMetrics.p0.getY(),
                    scoresMetrics.p1.getX(), scoresMetrics.p1.getY());
            Logger.println("recalc: " + String.valueOf(d));
//*/

            PLabel pLabel;
            PlayerArea playerArea;

            // south labels:
            playerArea = playerAreas[South];
            pLabel = playerArea.pLabels[leftPoints];
            pLabel.setPBounds(
                scoresMetrics.p1.getX(),
                scoresMetrics.p1.getY(),
                scoresMetrics.p5.getX(),
                scoresRectangle.height);
            pLabel = playerArea.pLabels[rightPoints];
            pLabel.setPBounds(
                scoresMetrics.p5.getX(),
                scoresMetrics.p5.getY(),
                scoresMetrics.p4.getX(),
                scoresRectangle.height);
            pLabel = playerArea.pLabels[poolPoints];
            pLabel.setPBounds(
                scoresMetrics.p2.getX(),
                scoresMetrics.p2.getY(),
                scoresMetrics.p3.getX(),
                scoresMetrics.p4.getY());
            pLabel = playerArea.pLabels[dumpPoints];
            pLabel.setPBounds(
                scoresMetrics.p2.getX() + x,
                scoresMetrics.p2.getY() - h,
                scoresMetrics.p3.getX() - x,
                scoresMetrics.p4.getY() - h);

            int w = scoresMetrics.p9.getX() - scoresMetrics.p8.getX();
            pLabel = playerArea.pLabels[statusPoints];
            pLabel.setPBounds(
                scoresMetrics.p8.getX(),
                scoresMetrics.p8.getY(),
                scoresMetrics.p9.getX(),
                scoresMetrics.p9.getY() + h);
            pLabel.setHorizontalAlignment(SwingConstants.CENTER);

            // west labels:
            playerArea = playerAreas[West];
            pLabel = playerArea.pLabels[leftPoints];
            pLabel.setPBounds(0, 0,
                scoresMetrics.p6.getX(),
                scoresMetrics.p6.getY());
            pLabel = playerArea.pLabels[rightPoints];
            pLabel.setPBounds(0, scoresMetrics.p6.getY(),
                scoresMetrics.p1.getX(), scoresMetrics.p1.getY());
            pLabel = playerArea.pLabels[poolPoints];
            pLabel.setPBounds(scoresMetrics.p1.getX(), 0,
                scoresMetrics.p2.getX(),
                scoresMetrics.p2.getY());
            pLabel = playerArea.pLabels[dumpPoints];
            pLabel.setPBounds(
                scoresMetrics.p2.getX(), 0,
                scoresMetrics.p2.getX() + x,
                scoresMetrics.p2.getY() - h);

            pLabel = playerArea.pLabels[statusPoints];
            pLabel.setPBounds(
                scoresMetrics.p8.getX() - h,
                scoresMetrics.p8.getY() - w,
                scoresMetrics.p8.getX(),
                scoresMetrics.p8.getY());
            pLabel.setHorizontalAlignment(SwingConstants.CENTER);

            // east labels:
            playerArea = playerAreas[East];
            pLabel = playerArea.pLabels[leftPoints];
            pLabel.setPBounds(
                scoresMetrics.p7.getX(),
                scoresMetrics.p7.getY(),
                scoresRectangle.width,
                scoresMetrics.p4.getY());
            pLabel = playerArea.pLabels[rightPoints];
            pLabel.setPBounds(
                scoresMetrics.p7.getX(), 0,
                scoresRectangle.width,
                scoresMetrics.p7.getY());
            pLabel = playerArea.pLabels[poolPoints];
            pLabel.setPBounds(scoresMetrics.p3.getX(), 0,
                scoresMetrics.p4.getX(),
                scoresMetrics.p3.getY());
            pLabel = playerArea.pLabels[dumpPoints];
            pLabel.setPBounds(
                scoresMetrics.p3.getX() - x, 0,
                scoresMetrics.p3.getX(),
                scoresMetrics.p3.getY() - h);

            pLabel = playerArea.pLabels[statusPoints];
            pLabel.setPBounds(
                scoresMetrics.p9.getX(),
                scoresMetrics.p9.getY() - w,
                scoresMetrics.p9.getX() + h,
                scoresMetrics.p9.getY());
            pLabel.setHorizontalAlignment(SwingConstants.CENTER);

            // refresh labels
            for (int i = 0; i < playerAreas.length; ++i) {
                playerArea = playerAreas[i];
                for (SLabel sLabel : playerArea.pLabels) {
                    sLabel.refresh();
                }
            }

            Rectangle popupRectangle = (Rectangle) StatusPopup.instance.popupRectangle.clone();
            Rectangle buttonPanelBounds = StatusPopup.instance.buttonPanel.getBounds();
            buttonPanelBounds.x = (popupRectangle.width - buttonPanelBounds.width) / 2;
            buttonPanelBounds.y = popupRectangle.height - buttonPanelBounds.height;
            StatusPopup.instance.buttonPanel.setBounds(buttonPanelBounds);

            this.invalidate();
            this.validate();
            this.repaint();

            StatusPopup.instance.invalidate();
            StatusPopup.instance.validate();
            StatusPopup.instance.repaint();
        }

        @Override
        public Dimension getSize() {
            return new Dimension(
                StatusPopup.instance.getScoresRectangle().width,
                StatusPopup.instance.getScoresRectangle().height);
        }

        @Override
        public Dimension getPreferredSize() {
            return getSize();
        }

        @Override
        public Dimension getMinimumSize() {
            return getSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getSize();
        }

        @Override
        public Rectangle getBounds() {
            return StatusPopup.instance.getScoresRectangle();
        }

        protected void paintComponent(Graphics g) {
            Logger.printf(DEBUG, "ScoresPanel.%s -> %s\n",
                Thread.currentThread().getStackTrace()[1].getMethodName(),
                StatusPopup.instance.getScoresRectangle());
            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(Color.white);
            g2d.fillRect(0, 0,
                StatusPopup.instance.getScoresRectangle().width,
                StatusPopup.instance.getScoresRectangle().height);
            paintLines(g2d);
        }

        private void paintLines(Graphics2D g2d) {
            Rectangle scoresRectangle = StatusPopup.instance.getScoresRectangle();
            g2d.setStroke(new BasicStroke(strokeWidth));
            g2d.setColor(lineColor);

            // vertical line
            g2d.drawLine(scoresMetrics.p0.getX(), 0, scoresMetrics.p0.getX(), scoresMetrics.p0.getY());

            // draw diagonals to center and paint center circle over
            g2d.drawLine(0, scoresRectangle.height, scoresMetrics.p0.getX(), scoresMetrics.p0.getY());
            g2d.drawLine(scoresRectangle.width, scoresRectangle.height, scoresMetrics.p0.getX(), scoresMetrics.p0.getY());

/* verification
            double d = Line2D.ptSegDist(0, scoresRectangle.height,
                    scoresMetrics.p0.getX(), scoresMetrics.p0.getY(),
                    scoresMetrics.p1.getX(), scoresMetrics.p1.getY());
            Logger.println("recalc: " + String.valueOf(d));
//*/

            // center circle and pool size:
            int circleRadius = (int) (metrics.cardW * centerCircleRadius);
            g2d.fillOval(scoresMetrics.p0.getX() - circleRadius,
                scoresMetrics.p0.getY() - circleRadius,
                2 * circleRadius, 2 * circleRadius);
            int innerRadius = (int) (metrics.cardW * centerCircleRadius - strokeWidth);
            g2d.setColor(Color.white);
            g2d.fillOval(scoresMetrics.p0.getX() - innerRadius,
                scoresMetrics.p0.getY() - innerRadius,
                2 * innerRadius, 2 * innerRadius);

            // pool size:
            Font font = new Font("Serif", Font.PLAIN, (int) (metrics.cardW));
            g2d.setFont(font);
            String text = "" + pConfig.poolSize.get();
            FontMetrics fontMetrics = g2d.getFontMetrics(font);
            int _width = fontMetrics.stringWidth(text);
            int _height = fontMetrics.getHeight();
            int x = scoresMetrics.p0.getX() - _width / 2 - metrics.xMargin;
            int y = scoresMetrics.p0.getY() + fontMetrics.getDescent() + metrics.yMargin;
            g2d.setColor(poolSizeColor);
            g2d.drawString(text, x, y);

            g2d.setColor(lineColor);

            // South panel:
            g2d.drawLine(scoresMetrics.p1.getX(), scoresMetrics.p1.getY(),
                scoresMetrics.p4.getX(), scoresMetrics.p4.getY());
            g2d.drawLine(scoresMetrics.p2.getX(), scoresMetrics.p2.getY(),
                scoresMetrics.p3.getX(), scoresMetrics.p3.getY());
            g2d.drawLine(scoresMetrics.p5.getX(), scoresMetrics.p5.getY(),
                scoresMetrics.p5.getX(), scoresRectangle.height);

            // West panel:
            g2d.drawLine(scoresMetrics.p1.getX(), 0,
                scoresMetrics.p1.getX(), scoresMetrics.p1.getY());
            g2d.drawLine(scoresMetrics.p2.getX(), 0,
                scoresMetrics.p2.getX(), scoresMetrics.p2.getY());
            g2d.drawLine(0, scoresMetrics.p6.getY(),
                scoresMetrics.p1.getX(), scoresMetrics.p6.getY());

            // East panel:
            g2d.drawLine(scoresMetrics.p4.getX(), 0,
                scoresMetrics.p4.getX(), scoresMetrics.p4.getY());
            g2d.drawLine(scoresMetrics.p3.getX(), 0,
                scoresMetrics.p3.getX(), scoresMetrics.p3.getY());
            g2d.drawLine(scoresRectangle.width, scoresMetrics.p7.getY(),
                scoresMetrics.p4.getX(), scoresMetrics.p7.getY());
        }
    }

    private ButtonPanel createButtonPanel() {
        MainPanel.ButtonHandler[][] fullList = {{
            new MainPanel.ButtonHandler(MainPanel.Command.goon, command -> {
                GameManager.getInstance().restart(GameManager.RestartCommand.goon);
                StatusPopup.instance.dispose();
            }),
            new MainPanel.ButtonHandler(MainPanel.Command.replay, command -> {
                GameManager.getInstance().restart(GameManager.RestartCommand.replay);
                StatusPopup.instance.dispose();
            }),
            new MainPanel.ButtonHandler(MainPanel.Command.newGame, command -> {
                GameManager.getInstance().restart(GameManager.RestartCommand.newGame);
                StatusPopup.instance.dispose();
            }),
        }};
        MainPanel.ButtonHandler[][] buttonList = fullList;
        if (Main.testFileName != null) {
            MainPanel.ButtonHandler[][] testList =
                new MainPanel.ButtonHandler[1][fullList[0].length - 1];
            System.arraycopy(fullList[0], 0, testList[0], 0, testList[0].length);
            buttonList = testList;
        }
        return new ButtonPanel(3, .7, buttonList);
    }

    private static class PlayerArea {
        // debug:
        Color[] bgColors = {Color.green, Color.magenta, Color.red, Color.cyan, Color.yellow};
        final SLabel[] pLabels = new SLabel[Player.PlayerPoints.values().length];
        final Player player;

        PlayerArea(int playerNum) {
            player = GameManager.getInstance().getPlayers()[playerNum];
            MainPanel.Alignment alignment = MainPanel.Alignment.values()[playerNum];

            double rotation = 0;
            switch (alignment) {
                case South:
                    break;
                case West:
                    rotation = Math.PI / 2;
                    break;
                case East:
                    rotation = -Math.PI / 2;
                    break;
            }
            for (int i = 0; i < pLabels.length; ++i) {
                pLabels[i] = new SLabel(rotation, player, Player.PlayerPoints.values()[i]);
                pLabels[i].setBackground(Color.white);
//                pLabels[i].setBackground(bgColors[i]);
                pLabels[i].setOpaque(true);
            }
        }
    }

    //  description is in etc/doc/scores.jpg
    static class ScoresMetrics {
        double tan;     // diagonal line slope tangent
        Point p0, p1, p2, p3, p4, p5, p6, p7, p8, p9;
    }

    static class SLabel extends PLabel {
        final Player player;
        final Player.PlayerPoints label;

        public SLabel(double rotation, Player player, Player.PlayerPoints label) {
            super(rotation);
            this.player = player;
            this.label = label;
        }

        @Override
        public void setPBounds(int x0, int y0, int x1, int y1) {
            super.setPBounds(x0 + strokeWidth, y0 + strokeWidth,
                x1 - strokeWidth, y1 - strokeWidth);
        }

        public void refresh() {
            List<Player.RoundResults> history = player.getHistory();
            if (label.equals(Player.PlayerPoints.status)) {
                String text = "";
                if (!history.isEmpty()) {
                    Player.RoundResults roundResults = history.get(history.size() - 1);
                    int curr = roundResults.getPoints(label);
                    int prev = 0;
                    if (history.size() > 1) {
                        roundResults = history.get(history.size() - 2);
                        prev = roundResults.getPoints(label);
                    }
                    text = String.format("%d (%d)", curr, curr - prev);
                }
                this.setText(text);
                return;
            }

            // todo: indicate the latest changes
            String sep = "";
            StringBuilder sb = new StringBuilder();
            List<Integer> results = new LinkedList<>();
            int total = 0;
            for (Player.RoundResults roundResults : history) {
                int res = roundResults.getPoints(label);
                if (label.equals(Player.PlayerPoints.status)) {
                    // todo:
                    continue;
                }
                if (res == 0) {
                    continue;
                }
                total += res;
                results.add(total);
                sb.append(sep).append(total);
                sep = ".";
            }
            String trailing = "";
            if (label.equals(Player.PlayerPoints.poolPoints) && total >= pConfig.poolSize.get()) {
                trailing = ">>";
                sb.append(trailing);
            }
            Font f = this.getFont();
            Canvas c = new Canvas();
            FontMetrics fontMetrics = c.getFontMetrics(f);
            int textWidth = fontMetrics.stringWidth(sb.toString());
            int labelW = this.getBounds().width;
            if (rotation != 0) {
                labelW = this.getBounds().height;
            }
            if (textWidth <= labelW) {
                this.setText(sb.toString());
                return;
            }

            sb.delete(0, sb.length());
            final String front = "...";
            sb.append(front);
            sep = trailing;
            for (int i = results.size() - 1; i >= 0; --i) {
                int res = results.get(i);
                String cur = res + sep;
                sb.insert(front.length(), cur);
                textWidth = fontMetrics.stringWidth(sb.toString());
                if (textWidth > labelW) {
                    sb.delete(front.length(), front.length() + cur.length());
                    break;
                }
                sep = ".";
            }
            this.setText(sb.toString());
        }
    }
}