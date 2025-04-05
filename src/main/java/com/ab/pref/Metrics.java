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
 * Created: 2/9/2025
 */
package com.ab.pref;

import com.ab.util.Couple;
import com.ab.util.Logger;

import java.awt.*;

public class Metrics {
    public static final boolean DEBUG = false;

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
    public final double yLabel = .75;      // player info
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

    // todo: there must be a bug in this algorithm because when switching from
    // vertical to horizontal layout, the card size increases
    void recalculateSizes() {
            double w, h;

            int panelWidth = Main.mainRectangle.width;
            int panelHeight = Main.mainRectangle.height;
            if (Main.mainPanel != null) {
                panelWidth = Main.mainPanel.getBounds().width;
                panelHeight = Main.mainPanel.getBounds().height;
            }
            if (cardAspectRatio == 0 || this.panelWidth == panelWidth && this.panelHeight == panelHeight) {
                return;
            }

            // vertical layout:
            Couple<Double> vMetrics = new Couple<>();
            // horizontally: 2 * vertical hand + label + bottom hand
            w = ((double) panelWidth - 3 * MIN_X_MARGIN) / (wHand + 2 * xLabel);
            // vertically: partially covered cards + yLabel;
            h = ((double) panelHeight - 3 * MIN_Y_MARGIN) / (yLabel + hHand);
            if (h < w * cardAspectRatio) {
                vMetrics.first = h / cardAspectRatio;
                vMetrics.second = h;
            } else {
                vMetrics.first = w;
                vMetrics.second = w * cardAspectRatio;
            }
            horizontalLayout = false;

            int hV = (int) (vMetrics.second * (hHand + yLabel + 1) + 4 * yMargin);
            this.cardW = vMetrics.first;
            this.cardH = vMetrics.second;

            // select layout that provides fitting all the components
            if (hV > panelHeight) {
                // horizontal layout:
                Couple<Double> hMetrics = new Couple<>();
                // horizontally: 2 * xLabel + 2 * wHand + talon + 2 * x-margins + 2 * xHandGap
                w = ((double) panelWidth - 2 * MIN_X_MARGIN) /
                        (2 * xLabel + 2 * wHand + (1 + xVisible) + 2 * xHandGap);
                // vertically: 2 * yLabel + 2 * hand + talon + 2 * handGap
                h = (double) (panelHeight - 3 * MIN_Y_MARGIN) / (yLabel + hHand);

                if (h < w * cardAspectRatio) {
                    hMetrics.first = h / cardAspectRatio;
                    hMetrics.second = h;
                } else {
                    hMetrics.first = w;
                    hMetrics.second = w * cardAspectRatio;
                }

                this.cardW = hMetrics.first;
                this.cardH = hMetrics.second;
                horizontalLayout = true;
            }

            Logger.printf(DEBUG, "horiz=%b, card %4.1fx%4.1f\n",
                    this.horizontalLayout, this.cardW, this.cardH);
            this.panelWidth = panelWidth;
            this.panelHeight = panelHeight;

            this.font = new Font("Serif", Font.PLAIN, (int) (this.cardW * .5));
    }
}
