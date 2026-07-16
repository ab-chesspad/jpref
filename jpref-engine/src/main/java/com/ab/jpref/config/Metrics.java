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
 * Created: 2/9/2025
 */
package com.ab.jpref.config;

import static com.ab.jpref.cards.Card.Suit;
import com.ab.jpref.cards.CardSet;
import com.ab.util.Couple;
import com.ab.util.Logger;

import java.awt.*;

public class Metrics {
    public static final boolean DEBUG_LOG = false;

    public final int
        MIN_X_MARGIN = 2,
        MIN_Y_MARGIN = 2,
        dummy_int = 0;

    public final double
        xVisible = .3,      // cards overlap visually
        yVisible = .25,     // cards overlap visually
        xSuitVisible = .6,  // between suits
        ySuitVisible = .5,  // between suits
        xHandGap = 1,       // between nands & talon
        wButton = .5,
        hButton = wButton,
        wLabel = 1.7,
        hLabel = .5,
        wElderHand = .5,

        wHand = 4 * xSuitVisible + 8 * xVisible + wButton,  // for 12 cards
        hHand = 4 * ySuitVisible + 6 * yVisible,            // for 10 cards

        ySideHandMargin = (double) 4 / 7,     // for vertical layout
        xSelected = .15,     // selected cards
        ySelected = .15,     // selected cards
        dummy_double = 0;

    public double cardAspectRatio;

    public int panelWidth, panelHeight;
    public final int xMargin = MIN_X_MARGIN, yMargin = MIN_Y_MARGIN;
    public boolean horizontalLayout;
    public double cardW, cardH;
    public double fontSize;

    protected static Object instance;

    public static Metrics getInstance() {
        if (instance == null) {
            instance = new Metrics();
        }
        return (Metrics)instance;
    }

    public Metrics() {
    }
    
    public void setCardAspectRatio(double cardAspectRatio) {
        this.cardAspectRatio = cardAspectRatio;
    }

    public boolean recalculateSizes() {
        int panelWidth = Config.getInstance().mainSize.first;
        int panelHeight = Config.getInstance().mainSize.second;
        if (this.panelWidth == panelWidth && this.panelHeight == panelHeight) {
            return false;
        }
        recalculateSizes(panelWidth, panelHeight);
        return true;
    }

    public void recalculateSizes(int panelWidth, int panelHeight) {
        double w, h;
        // vertical layout:
        Couple<Double> vMetrics = new Couple<>();
//        // horizontally: partially covered cards + 2 vertical columns
//        w = ((double) panelWidth - 4 * MIN_X_MARGIN) / (wHand + 2);
//        // vertically: partially covered cards + 2 * yLabel;
//        h = ((double) panelHeight - 4 * MIN_Y_MARGIN) / (hHand + 2 * hButton);
        // horizontally: partially covered cards
        w = ((double) panelWidth - 2 * MIN_X_MARGIN) / wHand;
        // vertically: partially covered cards + yLabel + horizontal line;
        h = ((double) panelHeight - 4 * MIN_Y_MARGIN) / (hHand + cardAspectRatio + hButton);
        if (h < w * cardAspectRatio) {
            vMetrics.first = h / cardAspectRatio;
            vMetrics.second = h;
        } else {
            vMetrics.first = w;
            vMetrics.second = w * cardAspectRatio;
        }

        // horizontal layout:
        Couple<Double> hMetrics = new Couple<>();
        // horizontally: bottom hand + 2 vertical columns
        w = ((double) panelWidth - 4 * MIN_X_MARGIN) / (wHand + 2);
        // vertically: partially covered cards + button;
        h = ((double) panelHeight - 3 * MIN_Y_MARGIN) / (hHand + hButton);
        if (h < w * cardAspectRatio) {
            hMetrics.first = h / cardAspectRatio;
            hMetrics.second = h;
        } else {
            hMetrics.first = w;
            hMetrics.second = w * cardAspectRatio;
        }

        // select layout that provides the largest cards
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

        fontSize = this.cardW * .5;
    }

    public int actualWidth(CardSet cardSet) {
        double w = 0;
        for (Suit suit : Suit.values()) {
            int size = cardSet.size(suit);
            if (size > 0) {
                Logger.printf(DEBUG_LOG, "%s - %d\n", suit, (int)w);
                w += cardW * (size - 1) * xVisible + cardW * xSuitVisible;
            }
        }
        w += (1 - xSuitVisible) * cardW;
        Logger.printf(DEBUG_LOG, "total width - %d\n", (int)w);
        return (int)w;
    }

    public int actualHeight(CardSet cardSet) {
        double h = 0;
        for (Suit suit : Suit.values()) {
            int size = cardSet.size(suit);
            if (size > 0) {
                Logger.printf(DEBUG_LOG, "%s - %d\n", suit, (int)h);
                h += cardH * (size - 1) * yVisible + cardH * ySuitVisible;
            }
        }
        h += (1 - ySuitVisible) * cardH;
        Logger.printf(DEBUG_LOG, "total height - %d\n", (int)h);
        return (int)h;
    }

}