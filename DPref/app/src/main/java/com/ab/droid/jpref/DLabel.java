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
 * Created: 9/10/2026 by claude.ai
 *
 */
package com.ab.droid.jpref;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.text.Html;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

// Draws its text rotated about its own center, while its own box (background,
// layout position/size) stays axis-aligned - mirroring the Swing PLabel, which
// rotates the Graphics2D just for the text draw rather than the whole component.
// View.setRotation() rotates the ENTIRE view (background included) about its
// center but keeps measuring/laying it out at its declared (unrotated) size, so
// a +-90 box wide/tall enough to read sideways after rotating routinely overflows
// RelativeLayout's remaining space near an edge - RelativeLayout then clamps the
// measured width to fit, shifting the rotation pivot and bunching adjacent
// labels together. Keeping each label's own box at its real (narrow) bounds and
// rotating only the drawn text avoids that entirely.
class DLabel extends View {
    private final double rotation;    // radians
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private CharSequence text = "";
    // rebuilt lazily in onDraw() - StaticLayout (unlike Canvas.drawText) understands
    // the Spanned markup Html.fromHtml() produces, so callers can pass either a plain
    // String or a Spanned and get the same colored-span rendering a real TextView would.
    private StaticLayout layout;
    private boolean centered = false;   // matches Swing PLabel's default (non-centered/"left")

    DLabel(MainActivity context, double rotation) {
        super(context);
        this.rotation = rotation;
        paint.setColor(Color.BLACK);
        paint.setTextSize(64f);
    }

    // Accepts either a plain string or one containing HTML markup (e.g. a
    // <span style="color: ..."> run) - converting is done here, once, so callers
    // never need to remember to run text through Html.fromHtml() themselves before
    // calling this (Canvas/StaticLayout draw literal "<span ...>" characters if fed
    // the raw HTML string directly, since only Html.fromHtml() parses that markup
    // into the Spanned that colors/styles actually come from).
    void setText(String text) {
        this.text = fromHtml(text);
        layout = null;
        invalidate();
    }

    @SuppressWarnings("deprecation")   // single-arg overload is required below API 24 (minSdk 23)
    static CharSequence fromHtml(String html) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        }
        return Html.fromHtml(html);
    }

    void setTextColor(int color) {
        paint.setColor(color);
        layout = null;
        invalidate();
    }

    void setTextSizePx(float size) {
        paint.setTextSize(size);
        layout = null;
        invalidate();
    }

    // Mirrors Swing PLabel.setHorizontalAlignment(SwingConstants.CENTER / LEFT).
    void setCentered(boolean centered) {
        this.centered = centered;
        invalidate();
    }

    // Width in pixels the given text would render at with this label's current
    // font/size - use this (not a separately-constructed Paint) so a truncation
    // decision made from it matches what actually gets drawn.
    float measureTextWidth(CharSequence text) {
        return Layout.getDesiredWidth(text, paint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (text.length() == 0) {
            return;
        }
        if (layout == null) {
            int width = (int) Math.ceil(Layout.getDesiredWidth(text, paint));
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.max(1, width))
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build();
        }
        canvas.save();
        canvas.rotate((float) Math.toDegrees(rotation), getWidth() / 2f, getHeight() / 2f);
        float tx;
        if (centered) {
            // Centering along the text's own reading direction is rotation-agnostic:
            // the text's own center always lands on the box's center regardless of
            // rotation, so the box's WIDTH is the right reference for both cases.
            tx = getWidth() / 2f - layout.getWidth() / 2f;
        } else if (rotation == 0) {
            tx = 0;   // left-align at the box's own true left edge
        } else {
            // Left-align at the box's own true starting edge. Rotation swaps which
            // screen axis a local offset lands on - a local-x offset ends up moving
            // the text along the screen's rotated reading direction, not sideways -
            // so "start of reading" has to be expressed via the box's HEIGHT
            // (matching the Swing PLabel's own non-centered case:
            // x = x0 - bounds.height / 2), not width or a flat 0. Getting this wrong
            // doesn't just misalign the text, it also leaves less room before the
            // view's own edge clips the tail, which looks like truncation.
            tx = getWidth() / 2f - getHeight() / 2f;
        }
        canvas.translate(tx, getHeight() / 2f - layout.getHeight() / 2f);
        layout.draw(canvas);
        canvas.restore();
    }
}
