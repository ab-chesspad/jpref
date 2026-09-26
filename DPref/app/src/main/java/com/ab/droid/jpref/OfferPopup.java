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
 */
package com.ab.droid.jpref;

import com.ab.droid.jpref.config.DMetrics;
import com.ab.jpref.ui.TableLayout;
import static com.ab.jpref.config.Config.ROUND_SIZE;
import static com.ab.jpref.config.I18n.m;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

/**
 *  Android counterpart of the Swing OfferPopup - lets the player pick how many
 *  of the offered tricks they'll accept, restricted to [minTricks, maxTricks].
 *
 *  Built on AlertDialog.Builder rather than a bare Dialog: its title area is
 *  guaranteed to render regardless of the active dialog theme (a bare Dialog's
 *  title only shows if the theme happens to provide a title-bar region, which
 *  modern Material/AppCompat themes often don't), and its positive/negative
 *  buttons live in a footer the dialog framework lays out itself - structurally
 *  separate from the (scrollable) content view, so a tall radio list can never
 *  push them off-screen the way a hand-rolled button panel inside the same
 *  LinearLayout could in landscape.
 */
public class OfferPopup {
    interface ResultListener {
        void onResult(int tricks);
    }

    private AlertDialog dialog = null;
    private final ResultListener onResult;
    private int selectedTricks = -1;

    OfferPopup(MainActivity host, int minTricks, int maxTricks, ResultListener onResult) {
        this.onResult = onResult;
        DMetrics dMetrics = host.getMetrics();

        // A flat setTextColor(int) ignores the enabled state entirely, so disabled
        // rows would render identically to enabled ones - a ColorStateList restores
        // the dimmed-when-disabled look, matching Swing's explicit
        // jLabel.setForeground(Color.GRAY) for out-of-range trick counts.
        ColorStateList labelColors = new ColorStateList(
            new int[][] { new int[] { -android.R.attr.state_enabled }, new int[] {} },
            new int[] { Color.GRAY, Color.BLACK });

        // Centering each row individually (radioGroup.setGravity(CENTER_HORIZONTAL))
        // would center every row's own bounding box independently - since "10" is two
        // digits wider than the single-digit rows, that row's icon would then land at
        // a different x than the rest. Instead, size the group to its widest row
        // (WRAP_CONTENT) and center that one block as a whole, so every row's icon
        // lines up in the same column regardless of its own text width.
        RadioGroup radioGroup = new RadioGroup(host);
        radioGroup.setOrientation(RadioGroup.VERTICAL);
        int rowPad = (int) (dMetrics.cardW * .12);
        for (int tricks = ROUND_SIZE; tricks >= 0; --tricks) {
            RadioButton rb = new RadioButton(host);
            // programmatically-created widgets (new RadioButton(...), not inflated from
            // XML) skip AppCompat's automatic drawable backporting/tinting, which can
            // leave the theme-resolved button icon missing - use the plain framework
            // drawable explicitly instead of relying on theme resolution. Resolve it via
            // ContextCompat.getDrawable() rather than the int-resource overload: the
            // deprecated Resources.getDrawable(int) path it uses internally can return
            // an incorrectly density-scaled Drawable the very first time a given
            // resource id is resolved, then a correctly-scaled cached one afterward -
            // which is exactly why only the first radio button rendered oversized/blurry.
            rb.setButtonDrawable(ContextCompat.getDrawable(host, android.R.drawable.btn_radio));
            rb.setTextColor(labelColors);
            rb.setText(m(String.valueOf(tricks)));
            boolean enabled = tricks >= minTricks && tricks <= maxTricks;
            // draw the actual choices larger than the out-of-range ones, so the
            // selectable range stands out rather than just being a color difference
            float textSizeFactor = enabled ? .22f : .16f;
            rb.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) (dMetrics.cardW * textSizeFactor));
            // the theme's default CompoundButton minHeight reserves a full touch
            // target (~48dp) regardless of the (small) text/icon it wraps - drop it
            // so the row's actual height follows the padding set below instead.
            rb.setMinHeight(0);
            rb.setPadding(rb.getPaddingLeft(), rowPad, rb.getPaddingRight(), rowPad);
            rb.setEnabled(enabled);
            int value = tricks;
            rb.setOnClickListener(v -> {
                selectedTricks = value;
                Button acceptButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                if (acceptButton != null) {
                    acceptButton.setEnabled(true);
                }
            });
            radioGroup.addView(rb);
        }

        ScrollView scrollView = new ScrollView(host);
        ScrollView.LayoutParams groupLp = new ScrollView.LayoutParams(
            ScrollView.LayoutParams.WRAP_CONTENT, ScrollView.LayoutParams.WRAP_CONTENT);
        groupLp.gravity = Gravity.CENTER_HORIZONTAL;
        scrollView.addView(radioGroup, groupLp);
        // Rows run 10 down to 0, and the actually-selectable range is usually near
        // the low end (maxTricks is rarely close to 10) - when the list doesn't fully
        // fit, default to showing that end instead of opening scrolled to the top
        // (10, 9, 8...), which are the rows most often disabled anyway. The activity
        // declares orientation|screenSize in configChanges, so rotating doesn't
        // recreate this dialog - it just relays out the existing ScrollView at the
        // new dimensions, which resets its scroll position to the top. Reapplying
        // fullScroll on every size change (not just a one-shot post()) keeps the
        // selectable end in view across rotation too.
        scrollView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom - top != oldBottom - oldTop || right - left != oldRight - oldLeft) {
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            }
        });

        // The default constructor inherits the app's own theme, which can render
        // light-on-dark (white text) rather than the black-on-white style used
        // throughout these popups - an explicit light dialog theme guarantees
        // black text on a white background regardless of the app's base theme.
        // A plain setTitle(CharSequence) is left-aligned with no gravity setter on
        // the builder, and the only way to reach the internal title TextView to
        // center it afterward is a fragile, lint-flagged resource-name lookup
        // (its id, alertTitle, exists at runtime but isn't part of the public
        // android.R.id class). setCustomTitle(View) sidesteps that entirely by
        // letting us supply - and fully control - the title view ourselves.
        TextView title = new TextView(host);
        title.setText(m("Your Tricks"));
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) (dMetrics.cardW * .2));
        int titlePad = (int) (dMetrics.cardW * .1);
        title.setPadding(titlePad, titlePad, titlePad, titlePad);

        AlertDialog.Builder builder = new AlertDialog.Builder(host, android.R.style.Theme_Material_Light_Dialog_Alert);
        builder.setCustomTitle(title);
        builder.setCancelable(false);   // force an explicit accept/cancel choice
        builder.setView(scrollView);
        // Swapped from the framework's default positive/negative assignment so
        // Accept lands on the left and Cancel on the right (AlertDialog lays out
        // [NEGATIVE, NEUTRAL, POSITIVE] left to right) - the enable/disable
        // tracking below follows suit, keyed off BUTTON_NEGATIVE now.
        builder.setNegativeButton(m(TableLayout.ButtonCommand.accept.getName()),
            (d, which) -> finish(selectedTricks));
        builder.setPositiveButton(m(TableLayout.ButtonCommand.cancel.getName()),
            (d, which) -> finish(-1));

        dialog = builder.create();
        dialog.show();
        // the positive/negative buttons only exist once the dialog has been shown
        Button acceptButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (acceptButton != null) {
            acceptButton.setEnabled(false);
        }
        // The button bar right-aligns by default (standard Material convention),
        // typically via a weighted spacer view ahead of the buttons - setting
        // gravity alone doesn't override that, since the spacer still claims space
        // according to its weight regardless of the parent's gravity. Zeroing every
        // child's weight collapses that spacer so gravity=CENTER actually centers
        // the (now natural-width) buttons as a group.
        if (acceptButton != null && acceptButton.getParent() instanceof LinearLayout) {
            LinearLayout buttonBar = (LinearLayout) acceptButton.getParent();
            buttonBar.setGravity(Gravity.CENTER);
            for (int i = 0; i < buttonBar.getChildCount(); ++i) {
                ViewGroup.LayoutParams lp = buttonBar.getChildAt(i).getLayoutParams();
                if (lp instanceof LinearLayout.LayoutParams) {
                    ((LinearLayout.LayoutParams) lp).weight = 0;
                }
            }
        }
        Window window = dialog.getWindow();
        if (window != null) {
            // keep whatever's behind the popup at its normal color, matching the other popups
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private void finish(int tricks) {
        // AlertDialog dismisses itself once a button listener returns
        onResult.onResult(tricks);
    }
}
