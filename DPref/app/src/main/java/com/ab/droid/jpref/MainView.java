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
package com.ab.droid.jpref;

import com.ab.droid.jpref.config.DMetrics;
import com.ab.droid.jpref.util.DUtil;

import com.ab.jpref.cards.Card;
import static com.ab.jpref.cards.Card.TOTAL_RANKS;
import static com.ab.jpref.cards.Card.TOTAL_SUITS;

import static com.ab.jpref.config.I18n.m;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Config;
import com.ab.jpref.engine.GameManager;
import com.ab.jpref.engine.HumanPlayer;
import com.ab.jpref.engine.Player;
import com.ab.jpref.ui.TableLayout;
import com.ab.jpref.ui.Widget;
import com.ab.util.Couple;
import com.ab.util.Logger;
import com.ab.util.Pair;
import com.ab.util.Point;
import com.ab.util.Util;

import static com.ab.jpref.ui.TableLayout.getInstance;
import static com.ab.util.Util.currMethodName;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

public class MainView extends View implements TableLayout.GUI {
    public static boolean DEBUG_LOG = false;

    private final int LBL_BG_COLOR = Color.YELLOW;
    private final int LBL_SELECTED_BG_COLOR = Color.GREEN;
    private final String CARDS_DECK_FILENAME = "/cards/deck.png";
    private final String CARD_BACK_FILENAME = "/cards/b5.jpg";
    private static final PorterDuffColorFilter DISABLED_FILTER =
            new PorterDuffColorFilter(Color.argb(160, 128, 128, 128), PorterDuff.Mode.SRC_ATOP);

    final MainActivity context;
    private final DMetrics dMetrics;

    final Bitmap[] suitImages = new Bitmap[TOTAL_SUITS];
    final Bitmap[][] cardImages = new Bitmap[Card.TOTAL_SUITS][Card.TOTAL_RANKS];

    private Bitmap sourceBackImage;
    private Bitmap backImage;

    Bitmap sourceElderHandImage;
    Bitmap elderHandImage;
    Bitmap buttonImage;

    final DUtil dUtil;

    final Point draggingStart = new Point(-1, -1);
//private final GestureDetectorCompat mDetector;

Canvas g2d;

    int panelWidth = -1, panelHeight = -1;

    final List<Pair<Widget, View>> widgets = new ArrayList<>();

    // _update() runs on every onDraw() - i.e. on every touch-move-triggered
    // frame while dragging - and previously re-scaled/re-tinted every visible
    // button's background Bitmap unconditionally, even though a button's size,
    // enabled state and source image essentially never change frame to frame.
    // Replacing the background Drawable every frame forced Android to
    // recomposite those buttons every frame too, which - since the round-stage
    // action buttons sit at the bottom of the screen, right by the hand being
    // dragged - showed up as jank concentrated exactly there. Caching the last
    // applied state per button (mirroring placeView()'s own change-guard below)
    // skips that work whenever nothing actually changed.
    private static class ButtonVisual {
        int w, h;
        boolean enabled;
        Bitmap sourceImage;
    }
    private final Map<View, ButtonVisual> buttonVisuals = new IdentityHashMap<>();

    GameManager gameManager;
    GameManager.RoundStage roundStage;
    HumanPlayer currentPlayer;
    Config.Bid currentBid;
    final CardList selectedCards = new CardList();
    boolean reportReady;

    TextView textView;
    private ImageButton btnBottom;
    private Bitmap bitmap;

    // mainLayout draws MainView (this: the table/cards) and every Widget's own
    // View (buttons, labels) as siblings, in add order - so a card dragged
    // under one of those widgets would otherwise paint behind it, the same way
    // it would in a plain z-order stack. This overlay draws nothing except the
    // one card currently being dragged (see TableLayout.getDraggedCard()), and
    // is kept as mainLayout's last child (see _update()) so that one card stays
    // visually on top of every widget while it's being held, without changing
    // the stacking of anything else.
    private View dragOverlay;

    // shown whenever TableLayout.getCurrentPlayer() is null - i.e. no
    // HumanPlayer is currently waiting on input, so the game is doing
    // something (bots thinking, a trick resolving, ...) that the user just
    // has to wait out. update() (below) is the single place that toggles it,
    // since it already runs on every table refresh.
    private ProgressBar waitBar;

    public MainView(MainActivity context) {
        super(context);
        this.context = context;
        Logger.printf(DEBUG_LOG, "register, thread %s\n", Thread.currentThread().getName());
        dMetrics = context.getMetrics();
        dUtil = (DUtil)context.getUtil();
        loadImages();
        placeControls(context);
        addDragOverlay(context);
        addWaitBar(context);
        dMetrics.recalculateSizes();
        this.setBackgroundColor(context.config().bgColor.getColor());
/*
        mDetector = new GestureDetectorCompat(context, new GestureDetector.OnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent motionEvent) {
                return false;
            }

            @Override
            public boolean onFling(MotionEvent event1, MotionEvent event2,
                                   float velocityX, float velocityY) {
//                Log.d(DEBUG_TAG, "onFling: " + event1.toString() + event2.toString());
                Logger.println(String.format("%s, %s, %s", Util.currMethodName(), event1, event2));
                int x = (int)event1.getX();
                int y = (int)event2.getY();
                TableLayout<?> tableLayout = TableLayout.getInstance();
                return true;
            }

            @Override
            public void onLongPress(@NonNull MotionEvent motionEvent) {

            }

            @Override
            public boolean onScroll(@Nullable MotionEvent event, @NonNull MotionEvent event1, float v, float v1) {
                return false;
            }

            @Override
            public void onShowPress(@NonNull MotionEvent event) {
                Logger.println(String.format("%s, %s", Util.currMethodName(), event));
                int x = (int)event.getX();
                int y = (int)event.getY();
                TableLayout<?> tableLayout = TableLayout.getInstance();
            }

            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent event) {
                Logger.println(String.format("%s, %s", Util.currMethodName(), event));
                int x = (int)event.getX();
                int y = (int)event.getY();
                TableLayout<?> tableLayout = TableLayout.getInstance();
                return false;
            }
        });
*/

        this.setOnTouchListener(new OnTouchListener() {
            // Real touchscreens keep delivering ACTION_MOVE samples - with a
            // pixel or two of sensor jitter - even while a finger is held
            // essentially still, not just while it's actually being dragged.
            // Forwarding every single one to onMouseDragged() triggers a full
            // table repaint (TableLayout.paint() redraws all four hands, the
            // trick and the talon) each time, which is wasted work when
            // nothing visually needs to move. Skipping samples that haven't
            // moved at least this many pixels since the last one we acted on
            // cuts that out without making the drag itself feel less
            // responsive - a couple of pixels is imperceptible.
            private static final int MOVE_THRESHOLD_PX = 3;
            private int lastMoveX = Integer.MIN_VALUE;
            private int lastMoveY = Integer.MIN_VALUE;

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                Logger.println(DEBUG_LOG, String.format("%s, %s", Util.currMethodName(), event));
                int x = (int)event.getX();
                int y = (int)event.getY();
                TableLayout tableLayout = TableLayout.getInstance();
                switch(event.getAction()) {
                    case (MotionEvent.ACTION_DOWN):
                        Logger.println(DEBUG_LOG, String.format("%s, ACTION_DOWN %s", Util.currMethodName(), event));
                        lastMoveX = Integer.MIN_VALUE;
                        lastMoveY = Integer.MIN_VALUE;
                        tableLayout.onMouseClick(x, y);
                        return true;
                    case (MotionEvent.ACTION_MOVE) :
                        Logger.println(DEBUG_LOG, String.format("%s, ACTION_MOVE %s", Util.currMethodName(), event));
                        if (Math.abs(x - lastMoveX) >= MOVE_THRESHOLD_PX || Math.abs(y - lastMoveY) >= MOVE_THRESHOLD_PX) {
                            lastMoveX = x;
                            lastMoveY = y;
                            tableLayout.onMouseDragged(x, y, false);
                        }
                        return true;
                    case (MotionEvent.ACTION_UP) :
                        Logger.println(DEBUG_LOG, String.format("%s, ACTION_UP %s", Util.currMethodName(), event));
                        tableLayout.onMouseDragged(x, y, true);
                        return true;
                }
                return true;
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Logger.printf("MainView.onSizeChanged: real view size %dx%d (was %dx%d)\n", w, h, oldw, oldh);
        if (Build.VERSION.SDK_INT >= 35) {
            // this app never opts into edge-to-edge itself (no
            // WindowCompat.setDecorFitsSystemWindows(window, false) call) - the OS
            // only forces it automatically for a targetSdk>=35 app once the device
            // itself runs Android 15+. Below that (including API 30-34, despite
            // being "R+"), the window is NOT edge-to-edge: MainView's raw size
            // already excludes the system-bar area, same as pre-R. Getting this
            // threshold wrong double-counts the inset (subtracted from the window
            // bounds here, but ALSO already excluded from MainView's own bounds),
            // shifting content down and clipping the bottom by the inset amount.
            dMetrics.recalculateSizes();
        } else {
            // not edge-to-edge: Android already reserves the system-bar space
            // outside MainView - this raw size IS the safe area, more reliably
            // than guessing at a "navigation_bar_height" resource.
            dMetrics.recalculateSizes(w, h);
        }
        recalculateSizes();
        TableLayout tableLayout = TableLayout.getInstance();
        if (tableLayout != null) {
            // re-run widget layout (incl. menuBtn) against the now-final size;
            // TableLayout.update() is otherwise only triggered by round-stage changes,
            // so a late/changed size would leave widgets positioned with stale DMetrics
            tableLayout.update(null);
        }
    }

    private void loadImages() {
        final int cardHeight = 204;
        final int[] suitStarts = {0, 215, 429, 644};
        Bitmap sourceDeckImage = dUtil.loadBitmap(CARDS_DECK_FILENAME);
        int suitWidth = sourceDeckImage.getWidth();
        for (int i = 0; i < suitImages.length; ++i) {
            // descending order
            int j = suitImages.length - 1 - i;
            suitImages[j] = Bitmap.createBitmap(sourceDeckImage,
                    0, suitStarts[i], suitWidth, cardHeight);
        }
        dMetrics.setCardAspectRatio((double) cardHeight * 8 / suitWidth);
        sourceBackImage = dUtil.loadBitmap(CARD_BACK_FILENAME);
        sourceElderHandImage = dUtil.loadBitmap("/buttons/hand.png");
        buttonImage = dUtil.loadBitmap("/buttons/button.jpg");
    }

    private Bitmap getCardImage(Card card) {
        return cardImages[card.getSuit().getValue()][card.getRank().ordinal() - 1];
    }

    private void recalculateSizes() {
        if (dMetrics.cardW <= 0) {
            return;
        }
        if (panelWidth == dMetrics.panelWidth && panelHeight == dMetrics.panelHeight) {
            return;
        }
        panelWidth = dMetrics.panelWidth;
        panelHeight = dMetrics.panelHeight;

        int cardW = (int) dMetrics.cardW;
        int cardH = (int) dMetrics.cardH;
        for (int j = 0; j < TOTAL_SUITS; ++j) {
            Bitmap scaledSuitImage = Bitmap.createScaledBitmap(suitImages[j], cardW * 8, cardH, true);
            for (int i = 0; i < TOTAL_RANKS; ++i) {
                int col = Card.Rank.values()[i].getValue() - Card.Rank.SIX.getValue();
                int xS = col * cardW;
                cardImages[j][i] = Bitmap.createBitmap(scaledSuitImage, xS, 0, cardW, cardH);
            }
        }
        backImage = Bitmap.createScaledBitmap(sourceBackImage, cardW, cardH, true);
        int size = (int)(cardW * dMetrics.wElderHand);
        elderHandImage = Bitmap.createScaledBitmap(sourceElderHandImage, size, size, true);
    }

    @Override
    public void add(Widget widget) {
        Logger.printf(DEBUG_LOG, "register, thread %s\n", Thread.currentThread().getName());
        View view;
        if (widget.getCommand() == null) {
            TextView lbl = new TextView(context);
            lbl.setBackgroundColor(Color.YELLOW);
            lbl.setEnabled(false);
            lbl.setMaxLines(1);
            lbl.setGravity(Gravity.CENTER);
            view = lbl;
        } else {
            Bitmap image = dUtil.loadBitmap(String.format("/buttons/%s.png", widget.getCommand().toString()));
            if (image == null) {
                Button b = new Button(context);
                b.setBackgroundColor(Color.WHITE);
                b.setTransformationMethod(null);
                b.setText(widget.getCommand().getName());
                b.setOnClickListener(v -> onButtonClick(widget));
                view = b;
            } else {
                widget.setUserObject(image);
                Button b = new Button(context);
                b.setOnClickListener(v -> onButtonClick(widget));
                view = b;
            }
        }
        placeView(view, 0, 0, 0, 0);
        widgets.add(new Pair<>(widget, view));
    }

    // Button clicks land on the UI thread by construction. That's fine for the
    // vast majority of commands (execCommand() just mutates widgets directly),
    // but yourOffer ends up in TableLayout.getOffer() -> gui.showOffer(), and
    // comments ends up in gui.getUserComments() - both block waiting for a popup's
    // result, and blocking the UI thread there would prevent that same thread from
    // ever laying out/drawing the dialog it's waiting on (deadlock: the popup never
    // appears, the app hangs). submitLog similarly calls gui.showMessage(...,flags),
    // which blocks the same way, and its retry loop also calls Util.isConnected()
    // directly (not on a worker thread the way the actual upload is), which does
    // real network I/O - NetworkOnMainThreadException on the UI thread. Route these
    // commands through a background thread so they can safely wait/do I/O; every
    // other command keeps running synchronously on the UI thread as before.
    private void onButtonClick(Widget widget) {
        if (widget.getCommand() == TableLayout.ButtonCommand.yourOffer
                || widget.getCommand() == TableLayout.ButtonCommand.comments
                || widget.getCommand() == TableLayout.ButtonCommand.submitLog) {
            new Thread(widget::onClick).start();
        } else {
            widget.onClick();
        }
    }

    @Override
    public void update() {
        // android specific issue, update must be in Thread-2
        Logger.printf(DEBUG_LOG, "%s, %s %s\n", Thread.currentThread().getName(),
                Util.currMethodName(), roundStage);
        postInvalidate();
        // update() runs on the game-logic thread as well as the UI thread (see
        // the comment above) - setVisibility() isn't thread-safe, so hop onto
        // the UI thread via post() rather than calling it directly here.
        boolean waiting = getInstance().getCurrentPlayer() == null;
        post(() -> {
            if (waitBar != null) {
                waitBar.setVisibility(waiting ? VISIBLE : GONE);
            }
        });
    }

    public void _update() {
        Logger.printf(DEBUG_LOG, "%s, %s %s\n", Thread.currentThread().getName(),
                Util.currMethodName(), roundStage);
        int fontSize = (int)(dMetrics.cardW * .3);
        for (Pair<Widget, View> pair : widgets) {
            Widget widget = pair.first;
            View jComponent = pair.second;
            if (!widget.isVisible()) {
                jComponent.setVisibility(INVISIBLE);
                continue;
            }
            jComponent.setVisibility(VISIBLE);
            int x = widget.getX();
            int y = widget.getY();
            int w = widget.getWidth();
            int h = widget.getHeight();
            int r = x + w;
            int b = y + h;
            placeView(jComponent, x, y, r, b);
//            int fontSize = h / 2;
            String text = m(widget.getText());
            int textColor = widget.getColor() == Widget.RED_COLOR ? Color.RED : Color.BLACK;
            if (jComponent instanceof Button) {
                Bitmap image = (Bitmap)widget.getUserObject();
                Bitmap sourceImage = image != null ? image : buttonImage;
                boolean enabled = widget.isEnabled();
                ButtonVisual last = buttonVisuals.get(jComponent);
                if (last == null || last.w != w || last.h != h
                        || last.enabled != enabled || last.sourceImage != sourceImage) {
                    Bitmap scaled = Bitmap.createScaledBitmap(sourceImage, w, h, true);
                    if (!enabled) {
                        scaled = tintDisabled(scaled);
                    }
                    jComponent.setBackground(new BitmapDrawable(getResources(), scaled));
                    // theme/AppCompat button styles can carry a default backgroundTint
                    // ColorStateList with a translucent disabled-state color, which gets
                    // composited over whatever background Drawable is set - clear it so
                    // our own (fully opaque) disabled tint isn't washed out further.
                    jComponent.setBackgroundTintList(null);
                    if (last == null) {
                        last = new ButtonVisual();
                        buttonVisuals.put(jComponent, last);
                    }
                    last.w = w;
                    last.h = h;
                    last.enabled = enabled;
                    last.sourceImage = sourceImage;
                }
                if (image == null || widget.getTextFace()) {
                    ((Button)jComponent).setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
                    ((Button)jComponent).setText(text);
                    ((Button)jComponent).setTextColor(textColor);
                }
                jComponent.setEnabled(widget.isEnabled());
                ((Button)jComponent).setTextColor(widget.isEnabled() ? textColor : 0xFFCCCCCC);
            } else if (jComponent instanceof TextView) {
                ((TextView)jComponent).setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
                ((TextView)jComponent).setText(text);
                ((TextView)jComponent).setTextColor(textColor);
                Player p = getInstance().getCurrentPlayer();
                if (p == null || p.getNumber() != widget.getNumber()) {
                    jComponent.setBackgroundColor(LBL_BG_COLOR);
                } else {
                    jComponent.setBackgroundColor(LBL_SELECTED_BG_COLOR);
                }
            }
            jComponent.setElevation(widget.getZOrder());
            // elevation alone doesn't reliably reorder overlapping siblings on all
            // Android versions - explicitly reassert draw order for anything meant
            // to sit above the round's main buttons (the menu button, the menu panel).
            if (widget.getZOrder() >= 2) {
                jComponent.bringToFront();
            }
        }
        // the zOrder>=2 bringToFront() calls above can push a widget back above
        // dragOverlay - reassert it as mainLayout's last child so the dragged
        // card (drawn there) stays on top of every widget, not just most of
        // them. Guarded the same way placeView() guards setLayoutParams(): only
        // reorder (which requests a new layout+draw pass) when actually needed,
        // so this doesn't turn into a self-perpetuating redraw loop.
        if (dragOverlay != null) {
            int lastIndex = MainActivity.mainLayout.getChildCount() - 1;
            if (MainActivity.mainLayout.getChildAt(lastIndex) != dragOverlay) {
                dragOverlay.bringToFront();
            }
        }
        // same reasoning as dragOverlay just above - reassert waitBar as the
        // very last child (on top of dragOverlay too) so it's actually visible
        // over everything else whenever update() shows it, guarded the same way.
        if (waitBar != null) {
            int lastIndex = MainActivity.mainLayout.getChildCount() - 1;
            if (MainActivity.mainLayout.getChildAt(lastIndex) != waitBar) {
                waitBar.bringToFront();
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onDraw(Canvas canvas) {
        Logger.printf(DEBUG_LOG, "%s, %s %s\n", Thread.currentThread().getName(),
                Util.currMethodName(), roundStage);
        super.onDraw(canvas);
        _update();
        TableLayout.getInstance().paint(canvas);
        Couple<Integer> elderHandLocation = getInstance().elderHandLocation;
        if (elderHandLocation.first != null) {
            canvas.drawBitmap(elderHandImage, elderHandLocation.first, elderHandLocation.second, null);
        }
        // TableLayout.paint() (above) just recomputed the dragged card's
        // position (if any) - refresh the overlay now, on the same UI-thread
        // pass, so it never lags a frame behind the card's own redraw.
        if (dragOverlay != null) {
            dragOverlay.invalidate();
        }
    }

    // Draws only the card currently being dragged (if any), positioned exactly
    // like TableLayout.paint() draws it - see the dragOverlay field comment for
    // why this needs to be a separate View on top of the widgets rather than
    // just part of MainView's own onDraw().
    private void addDragOverlay(Context context) {
        dragOverlay = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                TableLayout tableLayout = getInstance();
                if (tableLayout == null) {
                    return;
                }
                Card draggedCard = tableLayout.getDraggedCard();
                if (draggedCard != null) {
                    paint(canvas, draggedCard,
                        tableLayout.getDraggedCardPosition().getX(),
                        tableLayout.getDraggedCardPosition().getY());
                }
            }
        };
        LayoutParams rlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        MainActivity.mainLayout.addView(dragOverlay, rlp);
    }

    private void addWaitBar(Context context) {
        waitBar = new ProgressBar(context);
        waitBar.setVisibility(GONE);
        LayoutParams rlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        rlp.addRule(RelativeLayout.CENTER_IN_PARENT);
        MainActivity.mainLayout.addView(waitBar, rlp);
    }

    public void placeControls(Context context) {
        LayoutParams rlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        if (getParent() == null) {
            setPadding(0, 0, 0, 0);
            MainActivity.mainLayout.addView(this, rlp);
        } else {
            setLayoutParams(rlp);
        }
    }

    // Bakes the gray disabled tint into the bitmap's own pixels instead of using
    // Drawable.setColorFilter(): a colorFilter on a hardware-accelerated background
    // isn't reliably composited on some pre-API 26 devices (it either silently
    // drops, or - forced onto a software layer - breaks the elevation-based
    // z-ordering between overlapping siblings, letting views underneath show
    // through). Baking the tint into real pixels sidesteps both failure modes.
    private Bitmap tintDisabled(Bitmap src) {
        Bitmap tinted = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tinted);
        Paint paint = new Paint();
        paint.setColorFilter(DISABLED_FILTER);
        canvas.drawBitmap(src, 0, 0, paint);
        return tinted;
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
            LayoutParams existing = (LayoutParams) view.getLayoutParams();
            if (existing != null && existing.width == width && existing.height == height
                    && existing.leftMargin == left && existing.topMargin == top) {
                return;
            }
        }
        Logger.printf(DEBUG_LOG, "placeView, thread %s\n", Thread.currentThread().getName());
        LayoutParams rlp = new LayoutParams(width, height);
        rlp.setMargins(left, top, 0, 0);
        if (view.getParent() == null) {
            view.setPadding(0, 0, 0, 0);
            MainActivity.mainLayout.addView(view, rlp);
        } else {
            view.setLayoutParams(rlp);
        }
    }

    // Holds a showMessage() dialog's content (title + scrollable body) before
    // its button row - which differs between the plain and flags overloads
    // below - is attached and it's actually shown.
    private static class MessageDialog {
        final Dialog dialog;
        final LinearLayout root;
        final int width, height, buttonPanelHeight;

        MessageDialog(Dialog dialog, LinearLayout root, int width, int height, int buttonPanelHeight) {
            this.dialog = dialog;
            this.root = root;
            this.width = width;
            this.height = height;
            this.buttonPanelHeight = buttonPanelHeight;
        }
    }

    private MessageDialog buildMessageDialog(String title, String text) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(Color.BLACK);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) (dMetrics.cardW * .2));
        int titlePad = (int) (dMetrics.cardW * .1);
        titleView.setPadding(titlePad, titlePad, titlePad, titlePad);

        TextView body = new TextView(context);
        body.setText(DLabel.fromHtml(text));
        body.setTextColor(Color.BLACK);
        body.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) (dMetrics.cardW * .16));
        int bodyPad = (int) (dMetrics.cardW * .15);
        body.setPadding(bodyPad, 0, bodyPad, bodyPad);

        // Measure the actual content, at a width capped to the main window, before
        // it's attached to the dialog's own window - mirrors the desktop
        // MainPanel.showMessage's dialog.pack(): a short message (e.g. the
        // submitLog result) ends up sized to its own small extent, while a long
        // one (e.g. showHelp()'s HTML) hits the cap and scrolls instead, once the
        // window below is fixed at the clamped size.
        int maxWidth = context.config().mainSize.first;
        int maxHeight = context.config().mainSize.second;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST);
        titleView.measure(widthSpec, View.MeasureSpec.UNSPECIFIED);
        body.measure(widthSpec, View.MeasureSpec.UNSPECIFIED);
        int buttonPanelHeight = (int) (dMetrics.cardW * .5);
        int minWidth = (int) (dMetrics.cardW * 3);
        int minHeight = (int) (dMetrics.cardW * 2);
        int contentWidth = Math.max(titleView.getMeasuredWidth(), body.getMeasuredWidth());
        int contentHeight = titleView.getMeasuredHeight() + body.getMeasuredHeight() + buttonPanelHeight;
        int popupWidth = Math.min(Math.max(contentWidth, minWidth), maxWidth);
        int popupHeight = Math.min(Math.max(contentHeight, minHeight), maxHeight);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.addView(titleView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(body, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        return new MessageDialog(dialog, root, popupWidth, popupHeight, buttonPanelHeight);
    }

    private void showMessageDialog(MessageDialog md, LinearLayout buttonPanel) {
        md.root.addView(buttonPanel, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, md.buttonPanelHeight));
        md.dialog.setContentView(md.root);
        Window window = md.dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            window.setLayout(md.width, md.height);
            window.setGravity(Gravity.CENTER);
            // keep whatever's behind the popup at its normal color, matching the other popups
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        md.dialog.show();
    }

    @Override
    public void showMessage(String title, String text) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            // submitLog's network call currently runs on the caller's own thread
            // (see onButtonClick()), so this can land here off the UI thread -
            // building/showing a Dialog requires the UI thread.
            context.runOnUiThread(() -> showMessage(title, text));
            return;
        }

        MessageDialog md = buildMessageDialog(title, text);
        LinearLayout buttonPanel = new LinearLayout(context);
        buttonPanel.setOrientation(LinearLayout.HORIZONTAL);
        buttonPanel.setGravity(Gravity.CENTER);
        buttonPanel.setBackgroundColor(0xFFCCCCCC);
        Button continueButton = new Button(context);
        continueButton.setText(m("Continue"));
        continueButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) (dMetrics.cardW * .18));
        continueButton.setOnClickListener(v -> md.dialog.dismiss());
        buttonPanel.addView(continueButton, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        showMessageDialog(md, buttonPanel);
    }

    @Override
    public int showMessage(String title, String text, int flags) {
        // The caller needs the clicked button back synchronously (the int
        // return), so - like showOffer() - this must be called off the UI
        // thread: it blocks here on a queue the popup's buttons fill in once
        // tapped, and building/showing the popup itself has to happen on the
        // UI thread, so blocking on the UI thread here would deadlock (the
        // popup could never be shown to unblock it).
        ArrayBlockingQueue<Integer> resultQueue = new ArrayBlockingQueue<>(1);
        context.runOnUiThread(() -> {
            MessageDialog md = buildMessageDialog(title, text);
            LinearLayout buttonPanel = new LinearLayout(context);
            buttonPanel.setOrientation(LinearLayout.HORIZONTAL);
            buttonPanel.setGravity(Gravity.CENTER);
            buttonPanel.setBackgroundColor(0xFFCCCCCC);
            int pad = (int) (dMetrics.cardW * .1);
            if ((flags & msgFlagOK) != 0) {
                Button okButton = new Button(context);
                okButton.setText(m(TableLayout.ButtonCommand.ok.getName()));
                okButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) (dMetrics.cardW * .18));
                okButton.setOnClickListener(v -> {
                    md.dialog.dismiss();
                    resultQueue.offer(msgFlagOK);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = pad;
                buttonPanel.addView(okButton, lp);
            }
            if ((flags & msgFlagCancel) != 0) {
                Button cancelButton = new Button(context);
                cancelButton.setText(m(TableLayout.ButtonCommand.cancel.getName()));
                cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) (dMetrics.cardW * .18));
                cancelButton.setOnClickListener(v -> {
                    md.dialog.dismiss();
                    resultQueue.offer(msgFlagCancel);
                });
                buttonPanel.addView(cancelButton, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            showMessageDialog(md, buttonPanel);
        });
        try {
            return resultQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }

    @Override
    public String getUserComments() {
        // see onButtonClick()/showOffer() for why this must always block off the UI
        // thread - the "comments" button click is routed through a background thread
        // specifically so this can safely wait here.
        ArrayBlockingQueue<String> resultQueue = new ArrayBlockingQueue<>(1);
        context.runOnUiThread(() -> new CommentsPopup(context, resultQueue::offer));
        try {
            return resultQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    @Override
    public void showLastTrick(CardList cards) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setDimAmount(0f);

        int pw = (int) (dMetrics.cardW * 2);
        int ph = (int) (dMetrics.cardH * 2);
        View trickView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                getInstance().paintTrick(canvas, cards, getWidth() / 2, getHeight() / 2);
            }
        };
        trickView.setBackgroundColor(Color.GREEN);
        trickView.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(trickView, new LinearLayout.LayoutParams(pw, ph));
        dialog.show();
    }

    @Override
    public GameManager.RestartCommand showScores(boolean showButtons) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // called from a button click, already on the UI thread - no caller is
            // waiting on the result (see the withButtons==false call site), so just
            // show it and let the user dismiss it (tap outside / back) whenever
            new StatusPopup(context, showButtons, command -> { });
            return GameManager.RestartCommand.newRound;
        }
        // called from the game-logic thread, which needs the user's choice before
        // it can continue - build/show the dialog on the UI thread and block here
        // until one of its buttons delivers a result, mirroring how the Swing
        // build's showScores() uses SwingUtilities.invokeAndWait for the same reason
        ArrayBlockingQueue<GameManager.RestartCommand> resultQueue = new ArrayBlockingQueue<>(1);
        context.runOnUiThread(() -> new StatusPopup(context, showButtons, resultQueue::offer));
        try {
            return resultQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return GameManager.RestartCommand.newRound;
        }
    }

    @Override
    public int showOffer(int minTricks, int maxTricks) {
        // TableLayout.getOffer() always needs the real answer from here - unlike
        // showScores(), there's no legitimate fire-and-forget case, so this always
        // blocks on the queue rather than special-casing "already on the UI thread"
        // the way showScores() does. That's only safe because every caller has been
        // arranged to reach this off the UI thread: the game-logic thread calls it
        // directly, and the "yourOffer" button click (which would otherwise run on
        // the UI thread, per Android's click-listener contract) gets routed through
        // a background thread in onButtonClick() specifically so it can land here
        // safely too - see the comment there for why blocking the UI thread itself
        // would deadlock (it would never be free to lay out/draw the dialog it's
        // waiting on).
        ArrayBlockingQueue<Integer> resultQueue = new ArrayBlockingQueue<>(1);
        context.runOnUiThread(() -> new OfferPopup(context, minTricks, maxTricks, resultQueue::offer));
        try {
            return resultQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    @Override
    public <T> void paint(T canvas, Card card, int x, int y) {
        Logger.println(DEBUG_LOG, "running " + currMethodName());
        Bitmap bitmap = getCardImage(card);
        ((Canvas) canvas).drawBitmap(bitmap, x, y, null);
    }

    @Override
    public <T> void paintBack(T canvas, int x, int y) {
        Logger.println(DEBUG_LOG, "running " + currMethodName());
        ((Canvas) canvas).drawBitmap(backImage, x, y, null);
    }

}
