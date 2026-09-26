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
import com.ab.jpref.engine.GameManager;
import static com.ab.jpref.engine.GameManager.RestartCommand;

import com.ab.jpref.engine.Player;
import com.ab.jpref.ui.Scoresheet;
import com.ab.jpref.ui.TableLayout;
import com.ab.util.Couple;
import com.ab.util.Logger;
import com.ab.util.Pair;
import com.ab.util.Point;
import static com.ab.jpref.config.I18n.m;
import static com.ab.util.Util.currMethodName;

import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 *  for description look at etc/doc/scores.jpg - same pinwheel layout as the desktop StatusPopup
 */
public class StatusPopup {
    static final boolean DEBUG_LOG = false;

    static final int lineColor = Color.BLACK;
    static final int poolSizeColor = Color.parseColor("#008000");
    static final int strokeWidth = 1;

    static Scoresheet scoresheet;

    private final MainActivity host;
    private final DMetrics dMetrics;
    private final DConfig dConfig;
    private final ResultListener onResult;

    boolean withButtons;

    private final Dialog dialog;
    private final ScoresView scoresView;

    int historySize;

    StatusPopup(MainActivity host, boolean withButtons, ResultListener onResult) {
        this.host = host;
        this.withButtons = withButtons;
        this.dMetrics = host.getMetrics();
        this.dConfig = host.config();
        this.onResult = onResult;
        historySize = GameManager.getInstance().getPlayers()[0].getGameHistory().size();
        if (!withButtons) {
            --historySize;
        }
        if (scoresheet == null) {
            scoresheet = new Scoresheet();
        }

        int size = dConfig.mainSize.first;
        if (size > dConfig.mainSize.second) {
            size = dConfig.mainSize.second;
        }
        int popupWidth = size;
        int popupHeight = (int)(popupWidth * 0.9);
        int buttonPanelHeight = withButtons ? (int) (dMetrics.cardW * .5) : 0;
        int scoresHeight = popupHeight - buttonPanelHeight;

        dialog = new Dialog(host);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(!withButtons);   // force an explicit choice at round end

        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        RelativeLayout scoresContainer = new RelativeLayout(host);
        scoresView = new ScoresView(host, scoresContainer, popupWidth, scoresHeight);
        scoresContainer.addView(scoresView, new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        root.addView(scoresContainer, new LinearLayout.LayoutParams(popupWidth, scoresHeight));

        if (withButtons) {
            root.addView(createButtonPanel(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, buttonPanelHeight));
        }

        FrameLayout frame = new FrameLayout(host);
        frame.addView(root, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        if (scoresheet.isGameOver()) {
            TextView gameOverLabel = new TextView(host);
            gameOverLabel.setText("Game Over");
            gameOverLabel.setTextColor(Color.RED);
            gameOverLabel.setAlpha(0.3f);
            gameOverLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) (popupWidth * 0.12));
            gameOverLabel.setGravity(Gravity.CENTER);
            gameOverLabel.setRotation(-25f);
            gameOverLabel.setClickable(false);
            gameOverLabel.setFocusable(false);
            frame.addView(gameOverLabel, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        }

        dialog.setContentView(frame);
        Window window = dialog.getWindow();
        if (window != null) {
            // the theme's default dialog window background is a 9-patch with its own
            // built-in padding (for the shadow/rounded frame) - that padding shrinks the
            // actual visible content area below the popupWidth/popupHeight requested here,
            // throwing off every point computed from those dimensions (recalc()'s p0 and
            // the diagonals' corner targets end up short of the real, visible corners).
            // A plain, paddingless background keeps the requested size == the visible size.
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.WHITE));
            window.setLayout(popupWidth, popupHeight);
            window.setGravity(Gravity.CENTER);
            // keep whatever's behind the popup at its normal color instead of the
            // theme's default dimmed scrim
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dialog.show();
    }

    private LinearLayout createButtonPanel() {
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(0xFFCCCCCC);
        int pad = (int) (dMetrics.cardW * .1);

        Button goonButton = styledButton(m(TableLayout.ButtonCommand.goon.getName()));
        goonButton.setOnClickListener(v -> finish(RestartCommand.newRound));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = pad;
        panel.addView(goonButton, lp);

        Button replayButton = styledButton(m(TableLayout.ButtonCommand.replay.getName()));
        replayButton.setOnClickListener(v -> finish(RestartCommand.replay));
        panel.addView(replayButton, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return panel;
    }

    private Button styledButton(String text) {
        Button button = new Button(host);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) (dMetrics.cardW * .18));
        return button;
    }

    private void finish(RestartCommand command) {
        dialog.dismiss();
        onResult.onResult(command);
    }

    private class ScoresView extends View {
        final List<Pair<DLabel, Scoresheet.Widget>> labels = new ArrayList<>();
        final RelativeLayout container;

        ScoresView(MainActivity context, RelativeLayout container, int width, int height) {
            super(context);
            this.container = container;

            for (Scoresheet.Widget widget : Scoresheet.widgets) {
                DLabel lbl = new DLabel(context, widget.getRotation());
                lbl.setEnabled(false);
                placeView(lbl, 0, 0, 0, 0);
                labels.add(new Pair<>(lbl, widget));
            }
            recalc(width, height);
            postInvalidate();
        }

        public void recalc(int width, int height) {
            Logger.printf(DEBUG_LOG, "ScoresPanel.%s -> %dx%d\n", currMethodName(), width, height);
            scoresheet.recalc(width, height, dConfig.poolSize.get(), withButtons);
            StringBuilder sb = new StringBuilder();

            for (Pair<DLabel, Scoresheet.Widget> pair : labels) {
                DLabel lbl = pair.first;
                Scoresheet.Widget widget = pair.second;
                lbl.setTextColor(Color.BLACK);
                placeView(lbl, widget.getX(), widget.getY(),
                        widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight());
                lbl.setCentered(widget.getLabel().equals(Player.PlayerPoints.status));
                if (widget.getRotation() == 0) {
                    lbl.setTextSizePx(widget.getHeight() / 2);
                } else {
                    lbl.setTextSizePx(widget.getWidth() / 2);
                }
                String text = widget.getText();
                int textWidth = (int)lbl.measureTextWidth(text);
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
                        textWidth = (int)lbl.measureTextWidth(sb + chunk + sep);
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
                        text = String.format("<html>%s<span style=\"color: #0080FF\">%s</span></html>",
                                text.substring(0, index + 1), text.substring(index + 1));
                    }
                }
                lbl.setText(text);
            }
        }

        private void placeView(View view, int left, int top, int right, int bottom) {
            int width = right - left;
            int height = bottom - top;
            if (view.getParent() != null) {
                // setLayoutParams() unconditionally calls requestLayout(), which schedules
                // another layout+draw traversal - since onDraw() (via _update()) calls this
                // every frame, applying it unconditionally turns a single postInvalidate()
                // into a self-perpetuating layout/draw loop that never settles and starves
                // input dispatch, presenting as a UI freeze. Only re-apply when bounds
                // actually changed so the loop can terminate once the layout stabilizes.
                RelativeLayout.LayoutParams existing = (RelativeLayout.LayoutParams) view.getLayoutParams();
                if (existing != null && existing.width == width && existing.height == height
                        && existing.leftMargin == left && existing.topMargin == top) {
                    return;
                }
            }
            Logger.printf(DEBUG_LOG, "placeView caller=[%d,%d,%d,%d] -> %dx%d\n",
                left, top, right, bottom, width, height);
            RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(width, height);
            rlp.setMargins(left, top, 0, 0);
            if (view.getParent() == null) {
                view.setPadding(0, 0, 0, 0);
                container.addView(view, rlp);
            } else {
                view.setLayoutParams(rlp);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            if (width == 0 || height == 0) {
                return;
            }
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
            paint.setColor(lineColor);
            for (Scoresheet.Line line : scoresheet.getLines()) {
                Couple<Point> points = line.points;
                canvas.drawLine(points.first.getX(), points.first.getY(), points.second.getX(), points.second.getY(), paint);
            }

            // center circle:
            Point center = scoresheet.getCenter();
            int circleRadius = scoresheet.getCircleRadius();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(lineColor);
            canvas.drawCircle(center.getX(), center.getY(), circleRadius, paint);
            int innerRadius = circleRadius - strokeWidth;
            paint.setColor(Color.WHITE);
            canvas.drawCircle(center.getX(), center.getY(), innerRadius, paint);

            //  pool size:
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(innerRadius);
            textPaint.setColor(poolSizeColor);
            String text = "" + dConfig.poolSize.get();
            float textWidth = textPaint.measureText(text);
            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            float x = center.getX() - textWidth / 2 - dMetrics.xMargin;
            float y = center.getY() + fontMetrics.descent + dMetrics.yMargin;
            canvas.drawText(text, x, y, textPaint);
        }
    }

    interface ResultListener {
        void onResult(RestartCommand command);
    }
}
