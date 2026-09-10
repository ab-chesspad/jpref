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
 * Copyright (C) 2026 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 9/4/2026
 */

package com.ab.jpref.ui;

import com.ab.jpref.engine.GameManager;
import com.ab.jpref.engine.Player;
import com.ab.util.Couple;
import com.ab.util.Logger;
import com.ab.util.Point;
import static com.ab.jpref.config.Config.NOP;
import static com.ab.util.Util.currMethodName;

import java.util.ArrayList;
import java.util.List;

public class Scoresheet {
    static final boolean DEBUG_LOG = false;

    static final int X_MARGIN = 2;
    static final int Y_MARGIN = 2;
    public static final double centerYOffset = .4;          // relative to scoreRectangle height
    public static final double centerCircleRadius = .5;     // relative to paneH
    public static final String POOL_COMPLETE = ">>";

    static final int leftPoints = Player.PlayerPoints.leftPoints.ordinal();
    static final int rightPoints = Player.PlayerPoints.rightPoints.ordinal();
    static final int poolPoints = Player.PlayerPoints.poolPoints.ordinal();
    static final int dumpPoints = Player.PlayerPoints.dumpPoints.ordinal();
    static final int statusPoints = Player.PlayerPoints.status.ordinal();

    private static final Line[] lines = new Line[3 + 3 * NOP];
    private static final PlayerArea[] allAreas = new PlayerArea[NOP];
    public static final List<Widget> widgets = new ArrayList<>();
    static {
        for (int i = 0; i < NOP; ++ i) {
            Scoresheet.PlayerArea playerArea = new PlayerArea(i);
            allAreas[i] = playerArea;
            for (Widget label : playerArea.labels) {
                widgets.add(label);
            }
        }
        for (int i = 0; i < lines.length; ++i) {
            lines[i] = new Line();
        }
    }

    public static Scoresheet instance;

    //  for description look at etc/doc/scores.jpg
    private final Point p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11;
    private final Point topLeft, topRight, bottomLeft, bottomRight;

    private int width, height;
    private int circleRadius;
    private int fontSize;
    private int poolSize;
    private boolean fullHistory;

    public Scoresheet() {
        instance = this;
        topLeft = new Point();
        topRight = new Point();
        bottomLeft = new Point();
        bottomRight = new Point();
        p0 = new Point();
        p1 = new Point();
        p2 = new Point();
        p3 = new Point();
        p4 = new Point();
        p5 = new Point();
        p6 = new Point();
        p7 = new Point();
        p8 = new Point();
        p9 = new Point();
        p10 = new Point();
        p11 = new Point();
    }

    public void recalc(int width, int height, int poolSize, boolean fullHistory) {
        Logger.printf(DEBUG_LOG, "Scoresheet.%s -> %dx%d\n", currMethodName(), width, height);

        this.width = width;
        this.height = height;
        this.poolSize = poolSize;
        this.fullHistory = fullHistory;
        topLeft.set(0, 0);
        topRight.set(width, 0);
        bottomLeft.set(0, height);
        bottomRight.set(width, height);

        // center:
        p0.set(width / 2, (int)(height * centerYOffset));
        // diagonal line slope tangent
        double tan = height * (1 - centerYOffset) / this.p0.getX();

        int paneH = (int) (height * (1 - centerYOffset) / 5);
        int paneX = (int) (paneH / tan);
        fontSize = paneH / 2;
        if (tan > 1) {
            fontSize = paneX / 2;
        }
        circleRadius = (int) (paneH * centerCircleRadius) + 2;  // 2 pixels y-margin
        this.p1.set(paneX, height - paneH);
        this.p2.set(2 * paneX, height - 2 * paneH);
        this.p3.set(width - 2 * paneX, height - 2 * paneH);
        this.p4.set(width - paneX, height - paneH);
        this.p5.set(width / 2, height - paneH);
        this.p6.set(paneX, this.p0.getY());
        this.p7.set(width - paneX, this.p0.getY());
        this.p8.set(3 * paneX, height - 3 * paneH);
        this.p9.set(width - 3 * paneX, height - 3 * paneH);
        this.p10.set(4 * paneX, height - 4 * paneH);
        this.p11.set(width - 4 * paneX, height - 4 * paneH);

        updateLines();
        allAreas[0].setPBounds(leftPoints, p1, new Point(p0.getX(), height));
        allAreas[0].setPBounds(rightPoints, p4, new Point(p0.getX(), height));
        allAreas[0].setPBounds(poolPoints, p2, new Point(p3.getX(), p1.getY()));
        allAreas[0].setPBounds(dumpPoints, p8, new Point(p9.getX(), p2.getY()));
        allAreas[0].setPBounds(statusPoints, p10, new Point(p11.getX(), p8.getY()));

        allAreas[1].setPBounds(leftPoints, topLeft, p6);
        allAreas[1].setPBounds(rightPoints, p6, new Point(0, p5.getY()));
        allAreas[1].setPBounds(poolPoints, new Point(p1.getX(), 0), p2);
        allAreas[1].setPBounds(dumpPoints, new Point(p2.getX(), 0), p8);
        allAreas[1].setPBounds(statusPoints, new Point(p8.getX(), 0), p10);

        allAreas[2].setPBounds(leftPoints, p4, new Point(width, p7.getY()));
        allAreas[2].setPBounds(rightPoints, p7, new Point(width, 0));
        allAreas[2].setPBounds(poolPoints, p3, new Point(p4.getX(), 0));
        allAreas[2].setPBounds(dumpPoints, new Point(p3.getX(), 0), p9);
        allAreas[2].setPBounds(statusPoints, new Point(p9.getX(), 0), p11);
    }

    private void updateLines() {
        int i = -1;
        // lines separating player areas
        lines[++i].set(p0.getX(), 0, p0.getX(), p0.getY());     // vertical
        lines[++i].set(0, height, p0.getX(), p0.getY());        // left diagonal
        lines[++i].set(width, height, p0.getX(), p0.getY());        // right diagonal

        // South area:
        lines[++i].set(p1.getX(), p1.getY(), p4.getX(), p4.getY());
        lines[++i].set(p2.getX(), p2.getY(), p3.getX(), p3.getY());
        lines[++i].set(p5.getX(), p5.getY(), p5.getX(), height);

        // West area:
        lines[++i].set(p1.getX(), 0, p1.getX(), p1.getY());
        lines[++i].set(p2.getX(), 0, p2.getX(), p2.getY());
        lines[++i].set(0, p6.getY(), p1.getX(), p6.getY());

        // East areal:
        lines[++i].set(p4.getX(), 0, p4.getX(), p4.getY());
        lines[++i].set(p3.getX(), 0, p3.getX(), p3.getY());
        lines[++i].set(width, p7.getY(), p4.getX(), p7.getY());
    }

    public Point getCenter() {
        return p0;
    }

    public int getCircleRadius() {
        return circleRadius;
    }

    public int getFontSize() {
        return fontSize;
    }

    public Line[] getLines() {
        return lines;
    }

    public static class Line {
        public final Couple<Point> points = new Couple<>(new Point(0,0), new Point(0,0));

        void set(int x0, int y0, int x1, int y1) {
            points.first.first = x0;
            points.first.second = y0;
            points.second.first = x1;
            points.second.second = y1;
        }
    }

    private static class PlayerArea {
        final int playerNum;
        final TableLayout.Alignment alignment;
        final Widget[] labels = new Widget[Player.PlayerPoints.values().length];

        public PlayerArea(int playerNum) {
            this.playerNum = playerNum;
            alignment = TableLayout.Alignment.values()[playerNum];
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

            for (int i = 0; i < labels.length; ++i) {
                labels[i] = new Widget();
                labels[i].rotation = rotation;
                labels[i].label = Player.PlayerPoints.values()[i];
            }
        }

        private void setPBounds(int labelIndex, Point p0, Point p1) {
            update(labelIndex, p0.getX(), p0.getY(), p1.getX(), p1.getY());
        }

        private void update(int labelIndex, int _x0, int _y0, int _x1, int _y1) {
            Widget widget = labels[labelIndex];
            widget.setText("");
            int x0 = Math.min(_x0, _x1);
            int x1 = Math.max(_x0, _x1);
            int y0 = Math.min(_y0, _y1);
            int y1 = Math.max(_y0, _y1);
            widget.setPBounds(x0 + X_MARGIN, y0 + Y_MARGIN, x1 - X_MARGIN, y1 - Y_MARGIN);
            List<Player.RoundResults> history = GameManager.getInstance().getPlayers()[playerNum].getGameHistory();
            int historySize = history.size();
            if (!instance.fullHistory) {
                --historySize;
            }
            if (historySize == 0) {
                return;
            }
            if (labelIndex == statusPoints) {
                Player.RoundResults roundResults = history.get(historySize - 1);
                int curr = roundResults.getPoints(statusPoints);
                int prev = 0;
                if (historySize > 1) {
                    roundResults = history.get(historySize - 2);
                    prev = roundResults.getPoints(statusPoints);
                }
                widget.setText(curr);
                widget.change = curr - prev;
                return;
            }
            String sep = "";
            StringBuilder sb = new StringBuilder();
            int total = 0;
            widget.change = 0;
            for (int j = 0; j < historySize; ++j) {
                Player.RoundResults roundResults = history.get(j);
                widget.change = roundResults.getPoints(labelIndex);
                if (widget.change == 0) {
                    continue;
                }
                total += widget.change;
                sb.append(sep).append(total);
                sep = ".";
            }
            if (labelIndex == poolPoints && total >= Scoresheet.instance.poolSize) {
                sb.append(POOL_COMPLETE);
            }
            widget.setText(new String(sb));
        }
    }

    public static class Widget extends com.ab.jpref.ui.Widget {
        double rotation;
        int change;
        Player.PlayerPoints label;

        public int getChange() {
            return change;
        }

        public double getRotation() {
            return rotation;
        }

        public Player.PlayerPoints getLabel() {
            return label;
        }
    }
}