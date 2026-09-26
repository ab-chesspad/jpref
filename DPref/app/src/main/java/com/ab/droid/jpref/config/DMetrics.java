/*  This file is part of DPref project.
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
 * Created: 2/1/26
 */
package com.ab.droid.jpref.config;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowInsets;
import android.view.WindowManager;

import com.ab.droid.jpref.MainActivity;
import com.ab.jpref.config.Metrics;
import com.ab.util.Logger;
import com.ab.util.Util;

public class DMetrics extends Metrics {
//    private final static Context context = MainActivity.context();
//    protected static DMetrics instance;
    protected MainActivity context;

    private boolean recalculated = false;

/*
    public final double talonOverlap = .4;  // cards overlap visually
    public final double xOverlap = .6;      // cards overlap visually
    public final double yOverlap = .25;    // cards overlap visually
    public final double xSuitOverlap = .1;    // between suits
    public final double ySuitOverlap = .1;     // between suits
    public final double xHandGap = 1;      // between nands & talon
    public final double wButton = .5;
    public final double hButton = .75;
    public final double wHand = 4 * (1 - xSuitOverlap) + 6 * (1 - xOverlap);
    public final double hHand = 4 * (1 - ySuitOverlap) + 6 * (1 - yOverlap);
    public final double xSelected = .15;     // selected cards
    public final double ySelected = .15;     // selected cards

    double cardAspectRatio = 1.3;
*/
//    public int panelWidth, panelHeight;
    public final Rect mainRect = new Rect();
//    public final int xMargin = MIN_X_MARGIN, yMargin = MIN_Y_MARGIN;
//    public boolean horizontalLayout;
//    public double cardW, cardH;
//    public Font font;

//    public float fontSize;
/*

    public static DMetrics getInstance() {
//        if (instance == null) {
//            instance = new DMetrics();
//        }
        return instance;
    }
*/

    public DMetrics(MainActivity context) {
        this.context = context;
//        instance = this;
//        recalculateSizes();
    }

/*
    private DMetrics() {
        recalculateSizes();
    }
*/

    // return true on changed layout
    public synchronized boolean recalculateSizes() {
        Logger.printf(DEBUG_LOG, "DMetrics.recalculateSizes ENTERED, thread %s\n", Thread.currentThread().getName());
        DConfig config = context.config();
        int w, h;
        if (Build.VERSION.SDK_INT >= 35) {
            // must match the threshold in MainView.onSizeChanged() - see the
            // comment there for why R (30) is the wrong cutoff for "is edge-to-edge"
            WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
            android.view.WindowMetrics wm_metrics = wm.getCurrentWindowMetrics();
            android.graphics.Insets insets = wm_metrics.getWindowInsets()
                    .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
            w = wm_metrics.getBounds().width() - insets.left - insets.right;
            h = wm_metrics.getBounds().height() - insets.top - insets.bottom;
            panelX = insets.left;
            panelY = insets.top;
        } else if (panelWidth > 0) {
            // pre-R: Resources.getDisplayMetrics() is unreliable here (confirmed: it
            // disagrees with MainView.onSizeChanged's real measured size by exactly
            // the nav bar height). Once onSizeChanged has given us that real size via
            // recalculateSizes(w,h), keep using it instead of re-deriving a wrong
            // value from DisplayMetrics on every call (this method runs on every
            // TableLayout.update(), which would otherwise clobber the correct size).
            w = panelWidth;
            h = panelHeight;
        } else {
            // no real size known yet (very first call, before onSizeChanged has ever
            // fired) - best-effort guess just to get something on screen until then.
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            w = dm.widthPixels;
            h = dm.heightPixels;
        }
        Logger.printf(DEBUG_LOG, "%s: screen size %dx%d, old %dx%d\n", Util.currMethodName(),
                w, h, panelWidth, panelHeight);
        if (w == 0) {
            return false;
        }
        // keep config.mainSize in sync even when w/h match the already-known
        // panelWidth/panelHeight (e.g. the pre-R reuse branch) - TableLayout.update()
        // reads config.mainSize (not metrics.panelWidth/panelHeight directly) to place
        // the label/menuBtn widgets, so if this were skipped on the early-return below,
        // those widgets would keep using whatever stale size was last synced here.
        config.mainSize.first = w;
        config.mainSize.second = h;
        if (w == panelWidth && h == panelHeight) {
            return false;
        }
        recalculateSizes(w, h);
        setRecalculated();
        return true;
    }

    public synchronized boolean isRecalculated() {
        return recalculated;
    }

    private synchronized void setRecalculated() {
        recalculated = true;
    }

/*
    public boolean recalculateSizes(int width, int height) {
        if (width == panelWidth && height == panelHeight) {
            return false;
        }
        panelWidth = width;
        panelHeight = height;

        double w, h;
        if (panelWidth < panelHeight) {
            // vertical layout:
            horizontalLayout = false;
            // horizontally: 2 * vertical hand + label + bottom hand
//            w = ((double) panelWidth - 3 * MIN_X_MARGIN) / (wButton + wHand);
            // maximize width
            w = ((double) panelWidth - 3 * MIN_X_MARGIN) / (wHand);
            // vertically: partially covered cards + yLabel;
            h = ((double) panelHeight - 3 * MIN_Y_MARGIN) / (hButton + hHand);
            if (h < w * cardAspectRatio) {
                this.cardW = h / cardAspectRatio;
                this.cardH = h;
            } else {
                this.cardW = w;
                this.cardH = w * cardAspectRatio;
            }
//            this.panelWidth = panelWidth;
//            this.panelHeight = panelHeight;
//            this.fontSize = this.panelHeight / 20;
        } else {
            // horizontal layout:
            horizontalLayout = true;
            // horizontally: 2 * xLabel + 2 * wHand + talon + 2 * x-margins + 2 * xHandGap
            w = ((double) panelWidth - 2 * MIN_X_MARGIN) /
                    (2 * wButton + 2 * wHand + (1 + xOverlap) + 2 * xHandGap);
            // vertically: 2 * yLabel + 2 * hand + talon + 2 * handGap
            h = (double) (panelHeight - 3 * MIN_Y_MARGIN) / (hButton + hHand);

            if (h < w * cardAspectRatio) {
                this.cardW = h / cardAspectRatio;
                this.cardH = h;
            } else {
                this.cardW = w;
                this.cardH = w * cardAspectRatio;
            }
//            this.panelWidth = panelWidth;
//            this.panelHeight = panelHeight;
//            this.fontSize = this.panelHeight / 10;
        }
//        mainRect.right = panelWidth;
//        mainRect.top = statusBarHeight;
//        mainRect.top = 0;
//        mainRect.bottom = panelHeight;
        return true;
    }
*/

}
