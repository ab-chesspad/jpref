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

import com.ab.droid.jpref.config.DConfig;
import com.ab.droid.jpref.config.DMetrics;
import com.ab.jpref.ui.TableLayout;
import static com.ab.jpref.config.I18n.m;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 *  Android counterpart of the Swing MainPanel.getUserComments() - lets the
 *  player type free-form notes before the log is submitted, delivered on Submit,
 *  null on Cancel.
 */
public class CommentsPopup {
    interface ResultListener {
        void onResult(String comments);     // null on Cancel
    }

    private final MainActivity host;
    private final Dialog dialog;
    private final ResultListener onResult;
    private final EditText textArea;

    CommentsPopup(MainActivity host, ResultListener onResult) {
        this.host = host;
        this.onResult = onResult;
        DMetrics dMetrics = host.getMetrics();
        DConfig dConfig = host.config();

        int popupWidth = dConfig.mainSize.first * 2 / 3;
        int popupHeight = dConfig.mainSize.second * 2/ 3;

        dialog = new Dialog(host);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);

        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(host);
        title.setText(m("Comments"));
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) (dMetrics.cardW * .2));
        root.addView(title, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        textArea = new EditText(host);
        textArea.setGravity(Gravity.TOP | Gravity.START);
        // multi-line free text, matching Swing's JTextArea - EditText scrolls its own
        // content internally once it exceeds its bounds, no extra ScrollView needed
        textArea.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        textArea.setTextColor(Color.BLACK);
        textArea.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) (dMetrics.cardW * .16));
        root.addView(textArea, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout buttonPanel = new LinearLayout(host);
        buttonPanel.setOrientation(LinearLayout.HORIZONTAL);
        buttonPanel.setGravity(Gravity.CENTER);
        buttonPanel.setBackgroundColor(0xFFCCCCCC);

        Button submitButton = new DButton(host);
        submitButton.setText(m("Submit"));
        submitButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) (dMetrics.cardW * .18));
        submitButton.setOnClickListener(v -> finish(textArea.getText().toString()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = (int) (dMetrics.cardW * .1);
        buttonPanel.addView(submitButton, lp);

        Button cancelButton = new DButton(host);
        cancelButton.setText(m("Cancel"));
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) (dMetrics.cardW * .18));
        cancelButton.setOnClickListener(v -> finish(null));
        buttonPanel.addView(cancelButton, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(buttonPanel, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            window.setLayout(popupWidth, popupHeight);
            window.setGravity(Gravity.CENTER);
            // keep whatever's behind the popup at its normal color, matching the other popups
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dialog.show();
    }

    private void finish(String text) {
        dialog.dismiss();
        onResult.onResult(text);
    }
}
