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
 * Created: 2/9/2025
 */
package com.ab.pref.config;

import com.ab.util.Couple;
import com.ab.util.Logger;

import java.awt.*;

public class Metrics {
    public static final boolean DEBUG_LOG = false;

    private final int
        MIN_X_MARGIN = 4,
        MIN_Y_MARGIN = 4,
        dummy_int = 0;

    public final double xVisible = .3;     // cards overlap visually
    public final double yVisible = .25;    // cards overlap visually
    public final double xSuitGap = .16;    // between suits
    public final double ySuitGap = .1;     // between suits
    public final double xHandGap = 1;      // between nands & talon
    public final double xLabel = 3;        // player info
    public final double yLabel = .6;       // player info
    public final double wHand = 4 + 6 * xVisible + 3 * xSuitGap;
    public final double hHand = 4 + 6 * yVisible + 3 * ySuitGap;

    public final double ySideHandMargin = (double) 4 / 7;     // for vertical layout
    public final double xSelected = .15;     // selected cards
    public final double ySelected = .15;     // selected cards

    double cardAspectRatio;

    public int panelWidth, panelHeight;
    public final int xMargin = MIN_X_MARGIN, yMargin = MIN_Y_MARGIN;
    public boolean horizontalLayout;
    public double cardW, cardH;
    public Font font;

    protected static Object instance;

    public static Metrics getInstance() {
        if (instance == null) {
            instance = new Metrics();
        }
        return (Metrics)instance;
    }

    private Metrics() {
    }
    
    public void setCardAspectRatio(double cardAspectRatio) {
        this.cardAspectRatio = cardAspectRatio;
    }

    // vertical to horizontal layout, the card size increases
    public void recalculateSizes() {
        double w, h;
        Rectangle r = PConfig.getInstance().mainRectangle.get();
        int panelWidth = r.width;
        int panelHeight = r.height;
        if (cardAspectRatio == 0 || this.panelWidth == panelWidth && this.panelHeight == panelHeight) {
            return;
        }

        // vertical layout:
        Couple<Double> vMetrics = new Couple<>();
        // horizontally: bottom hand + 2 * xlabel
        w = ((double) panelWidth - 4 * MIN_X_MARGIN) / (wHand + 2 * xLabel);
        // vertically: partially covered cards + 2 * yLabel;
        h = ((double) panelHeight - 4 * MIN_Y_MARGIN) / (hHand + 2 * yLabel);
        if (h < w * cardAspectRatio) {
            vMetrics.first = h / cardAspectRatio;
            vMetrics.second = h;
        } else {
            vMetrics.first = w;
            vMetrics.second = w * cardAspectRatio;
        }

        // vertical layout:
        Couple<Double> hMetrics = new Couple<>();
        // horizontally: bottom hand + 2 * xlabel
        w = ((double) panelWidth - 4 * MIN_X_MARGIN) / (wHand + 2 * xLabel);
        // vertically: partially covered cards + yLabel;
        h = ((double) panelHeight - 3 * MIN_Y_MARGIN) / (hHand + yLabel);
        if (h < w * cardAspectRatio) {
            hMetrics.first = h / cardAspectRatio;
            hMetrics.second = h;
        } else {
            hMetrics.first = w;
            hMetrics.second = w * cardAspectRatio;
        }

        // select layout that provides fitting the largest components
        if (vMetrics.first >= hMetrics.first) {
            this.cardW = vMetrics.first;
            this.cardH = vMetrics.second;
            horizontalLayout = false;
        } else {
            this.cardW = hMetrics.first;
            this.cardH = hMetrics.second;
            horizontalLayout = true;
        }

        Logger.printf(DEBUG_LOG, "horiz=%b, card %4.1fx%4.1f\n",
                this.horizontalLayout, this.cardW, this.cardH);
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;

        this.font = new Font("Serif", Font.PLAIN, (int) (this.cardW * .5));
    }
}