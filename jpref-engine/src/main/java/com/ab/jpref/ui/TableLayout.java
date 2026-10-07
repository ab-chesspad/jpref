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
 * Created: 2/23/2025
 */
package com.ab.jpref.ui;

import static com.ab.jpref.config.Config.*;
import static com.ab.jpref.config.Config.Bid.*;
import static com.ab.jpref.config.Config.ROUND_SIZE;
import static com.ab.jpref.config.I18n.m;
import static com.ab.jpref.ui.ButtonPanel.ButtonHandler;

import com.ab.jpref.cards.Card;
import static com.ab.jpref.cards.Card.Suit;
import static com.ab.jpref.cards.Card.Suit.SPADE;
import static com.ab.jpref.cards.Card.Suit.CLUB;
import static com.ab.jpref.cards.Card.Suit.DIAMOND;
import static com.ab.jpref.cards.Card.Suit.HEART;

import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;

import com.ab.jpref.config.I18n;
import com.ab.jpref.engine.*;

import static com.ab.jpref.engine.GameManager.RoundStage;
import com.ab.jpref.config.Metrics;
import com.ab.util.Couple;
import com.ab.util.Logger;
import com.ab.util.Point;
import com.ab.util.Util;
import static com.ab.util.Util.sleep;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class TableLayout implements GameManager.EventObserver {
    public static final boolean DEBUG_LOG = false;

    public enum ButtonCommand {
        menu("Menu"),
        settings("Settings"),
        comments("Comments"),
        help("Description"),

        minBid("Min Bid"),
        misere("Misère"),
        whist("Whist"),
        halfWhist("½ Whist"),
        pass("Pass"),
        drop("Drop"),
        without3("Without Three"),

        laying("Laying"),
        standing("Standing"),

        prevSuit("Previous Suit"),
        nextSuit("Next Suit"),
        lesserGame("Lesser Game"),
        greaterGame("Greater Game"),
        select("Select"),

        goon("Continue"),
        newRound("New Round"),
        showScores("Scores"),
        lastTrick("Last Trick"),
        replay("Replay"),
        verify("Verify"),
        submitLog("Submit Log"),
        yourOffer("Your Offer"),
        backToGame("Back to Game"),

        ok("OK"),
        accept("Accept"),
        cancel("Cancel"),
        ;
        final String name;

        ButtonCommand(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public enum Alignment {
        South,
        West,
        East,
    }

    GameManager gameManager;

    private HumanPlayer currentPlayer;
    final List<Point> cardPositions = new ArrayList<>();
    final CardSet selectedCards = new CardSet();
    final Point draggingStart = new Point(-1, -1);
    final Point dragging = new Point(-1, -1);
    // the card currently being dragged (if any) and where paint() last drew it -
    // exposed so a GUI can re-draw just this card on top of its own overlaid
    // widgets, since the rest of the table paints underneath those widgets in
    // z-order.
    private Card draggedCard;
    private final Point draggedCardPosition = new Point();

    final GUI gui;
    final Host host;
    final Metrics metrics;

    int panelWidth = -1;
    int panelHeight = -1;

    // widgets:
    private final List<ButtonPanel> buttonPanels = new ArrayList<>();
    public final Widget[] labels = new Widget[NOP];
    // bot declarer's misère hand as defenders know it, one line per suit
    private final Widget[] declarerHandLines = new Widget[Suit.values().length];
    public final Widget menuBtn;
    public final ButtonPanel menuPanel;
    public final ButtonPanel declareRoundPanel;
    public final ButtonPanel whistSelectionPanel;

    private Bid currentBid;
    public final Couple<Integer> elderHandLocation = new Couple<>();
    final CardList currentUserCards = new CardList();
    RoundStage roundStage;

    Config config() {
        return host.config();
    }

    private static TableLayout instance;
    public static TableLayout getInstance() {
        return instance;
    }

    public TableLayout(Host host, GUI gui) {
        this.host = host;
        this.gui = gui;
        instance = this;
        metrics = host.getMetrics();

        create(RoundStage.bidding, 4, 1, 1,
            new ButtonCommand[][] {
                {ButtonCommand.minBid},
                {ButtonCommand.misere},
                {ButtonCommand.pass},
            });
        create(RoundStage.drop, 4, 1, 1,
            new ButtonCommand[][] {
                {ButtonCommand.drop},
                {ButtonCommand.without3},
            });
        declareRoundPanel = create(RoundStage.declareRound, 1.5, 1.5, 1, true,
            new ButtonCommand[][] {
                {null, ButtonCommand.greaterGame, null},
                {ButtonCommand.prevSuit, ButtonCommand.select, ButtonCommand.nextSuit},
                {null, ButtonCommand.lesserGame, null},
            });
        whistSelectionPanel = create(RoundStage.whistSelection, 4, 1, 1,
            new ButtonCommand[][] {
                {ButtonCommand.whist},
                {ButtonCommand.halfWhist},
                {ButtonCommand.pass},
            });
        ButtonPanel whistOptionPanel = create(RoundStage.selectWhistOption, 4, 1, 1,
            new ButtonCommand[][] {
                {ButtonCommand.laying},
                {ButtonCommand.standing},
            });

        // labels
        for (int i = 0; i < NOP; ++i) {
            labels[i] = new Widget(i);
            gui.add(labels[i]);
        }
        for (int i = 0; i < declarerHandLines.length; ++i) {
            declarerHandLines[i] = new Widget(-1);     // never highlighted as the current player
            declarerHandLines[i].setVisible(false);
            declarerHandLines[i].setLeftAligned(true);
            gui.add(declarerHandLines[i]);
        }

        // menu
        menuBtn = new Widget(ButtonCommand.menu, buttonCommand -> execCommand(buttonCommand), 2, false);
        gui.add(menuBtn);

        menuPanel = create(null, 3.5, .6, 3,
            new ButtonCommand[][] {
                {ButtonCommand.showScores},
                {ButtonCommand.lastTrick},
                {ButtonCommand.yourOffer},
                {ButtonCommand.comments},
                {ButtonCommand.submitLog},
                {ButtonCommand.settings},
                {ButtonCommand.help},
                {ButtonCommand.replay},
                {ButtonCommand.newRound},
                {ButtonCommand.backToGame},
            });
    }

    private ButtonPanel create(RoundStage roundStage, double scaleW, double scaleH, int zOrder, ButtonCommand[][] commands) {
        return create(roundStage, scaleW, scaleH, zOrder, false, commands);
    }

    private ButtonPanel create(RoundStage roundStage, double scaleW, double scaleH, int zOrder, boolean textFace, ButtonCommand[][] commands) {
        ButtonHandler[][] handlers = new ButtonHandler[commands.length][commands[0].length];
        for (int j = 0; j < commands.length; ++j) {
            ButtonHandler[] row = new ButtonHandler[commands[0].length];
            handlers[j] = row;
            final int r = j;
            for (int i = 0; i < commands[0].length; ++i) {
                if (commands[j][i] == null) {
                    continue;
                }
                final int c = i;
                row[i] = new ButtonHandler(commands[j][i], buttonCommand -> execCommand(commands[r][c]));
            }
        }
        String roundStageName = null;
        if (roundStage != null) {
            roundStageName = roundStage.toString();
        }
        Logger.printf(DEBUG_LOG, "%s, widget start # %d\n", roundStageName, Widget.count + 1);
        ButtonPanel res = new ButtonPanel(roundStage, scaleW, scaleH, zOrder, textFace, handlers);
        for (Widget w : res) {
            gui.add(w);
        }
        Logger.printf(DEBUG_LOG, "%s, widget end # %d\n", roundStageName, Widget.count);
        buttonPanels.add(res);
        return res;
    }

    @Override
    public void update(RoundStage roundStage) {
        boolean fullUpdate;
        synchronized (metrics) {
            Logger.printf(DEBUG_LOG, "%s, %s %s\n", Thread.currentThread().getName(),
                    Util.currMethodName(), roundStage);
            gameManager = GameManager.getInstance();
            metrics.recalculateSizes();
            if (metrics.cardW <= 0) {
                return;
            }

            int panelWidth = config().mainSize.first;
            int panelHeight = config().mainSize.second;
            if (this.panelWidth == panelWidth && this.panelHeight == panelHeight && roundStage == null) {
                fullUpdate = false;
            } else {
                fullUpdate = true;

                if (roundStage != null) {
                    this.roundStage = roundStage;
                }

                int x, y, w, h;

                if (this.roundStage != null) {
                    // only reset on an actual transition INTO declareRound (a non-null
                    // roundStage param) - isStage() checks the persisted this.roundStage,
                    // which stays declareRound across later resize-only refreshes
                    // (roundStage == null), and would otherwise wipe out currentBid
                    // every time the user resizes the window mid-declaration
                    if (RoundStage.declareRound.equals(roundStage)) {
                        if (currentPlayer == null) {
                            currentPlayer = (HumanPlayer) gameManager.getDeclarer();
                        }
                        setDeclareRoundPanel(null);
                    }
                    placeButtonPanels();
                }

                // labels:
                x = y = 0;
                w = (int) (metrics.cardW * metrics.wLabel);
                h = (int) (metrics.cardW * metrics.hLabel);
                for (int i = 0; i < gameManager.getPlayers().length; ++i) {
                    Widget label = labels[i];
                    switch (Alignment.values()[i]) {
                        case South:
                            x = (int) ((panelWidth - metrics.cardW * metrics.wLabel) / 2 - metrics.xMargin);
                            y = metrics.panelY + (int) (panelHeight - metrics.cardH -
                                h - 2 * metrics.yMargin) - (int)(metrics.ySelected * metrics.cardW);
                            if (i == gameManager.elderHand) {
                                elderHandLocation.first = x + w + metrics.xMargin;
                                elderHandLocation.second = y + h - (int) (metrics.cardW * metrics.wElderHand);
                            }
                            break;

                        case West:
                            y = metrics.panelY + metrics.yMargin;
                            if (i == gameManager.elderHand) {
                                elderHandLocation.first = (int) (metrics.cardW) + 2 * metrics.xMargin;
                                elderHandLocation.second = y + metrics.yMargin + h;
                            }
                            if (metrics.horizontalLayout) {
                                x = (int) (2 * metrics.xMargin + metrics.cardW);
                            } else {
                                x = metrics.xMargin;
                            }
                            break;

                        case East:
                            y = metrics.panelY + metrics.yMargin;
                            x = panelWidth - w - metrics.xMargin;
                            if (i == gameManager.elderHand) {
                                elderHandLocation.first = panelWidth - (int) (metrics.cardW * (1 + metrics.wElderHand));
                                elderHandLocation.second = y + metrics.yMargin + h;
                            }
                            if (metrics.horizontalLayout) {
                                x -= (int) (metrics.xMargin + metrics.cardW);
                            }
                            break;
                    }
                    label.setBounds(x, y, w, h);
                }
                placeDeclarerHand(panelWidth, panelHeight, w, h);

                // menu
                w = (int)(metrics.cardW * metrics.wButton);
                h = (int)(metrics.cardW * metrics.hButton);
                x = panelWidth - w - metrics.xMargin;
                y = metrics.panelY + panelHeight - h - metrics.yMargin;
                menuBtn.setBounds(x, y, w, h);
                Logger.printf(DEBUG_LOG, "%s: menuBtn: %dx%d, %dx%d\n", Util.currMethodName(), x, y, w, h);
                menuBtn.setVisible(true);
                menuBtn.setEnabled(true);

                if (this.roundStage != null) {
                    // labels:
                    for (int i = 0; i < gameManager.getPlayers().length; ++i) {
                        Player player = gameManager.getPlayers()[i];
                        Widget label = labels[i];
                        String text = m(player.getBid().toString());
                        if (text.startsWith("X")) {
                            text = 10 + text.substring(1);
                        }

                        switch (this.roundStage) {
                            case bidding:
                            case showTalon:
                            case drop:
                            case declareRound:
                            case whistSelection:
                            case selectWhistOption:
                                break;
                            default:
                                text += ", " + player.getTricks();
                                break;
                        }
                        Suit trump = player.getBid().getTrump();
                        int color;
                        if (Suit.DIAMOND.equals(trump) || HEART.equals(trump)) {
                            color = Widget.RED_COLOR;
                        } else {
                            color = Widget.BLACK_COLOR;
                        }
                        label.setText(text, color);
                    }
                }
                placeMenuPanel();
            }
        }
        // gui.update() and sleep() touch Swing/Android UI components and must run
        // outside the metrics lock, otherwise a UI-thread paint() waiting on the same
        // lock can deadlock against this thread waiting on the UI toolkit's own lock.
        gui.update();
        if (fullUpdate) {
            sleep(10);  // let GUI thread a chance to repaint
        }
    }

    private void placeMenuPanel() {
        if (!menuPanel.isVisible()) {
            return;
        }
        int panelWidth = config().mainSize.first;
        int panelHeight = config().mainSize.second;

        double wButton = metrics.cardW * menuPanel.getScaleW();
        double hButton = metrics.cardW * menuPanel.getScaleH();

        double aspectRatio = menuPanel.getScaleH() / menuPanel.getScaleW();
        if (wButton * aspectRatio > hButton) {
            wButton = hButton / aspectRatio;
        } else {
            hButton = wButton * aspectRatio;
        }

        int rowCount = 0;
        for (int i = 0; i < menuPanel.getRowCount(); ++i) {
            Widget widget = menuPanel.getWidget(i, 0);
            switch (widget.getCommand()) {
                case showScores:
                    widget.setVisible(menuPanel.isVisible());
                    break;
                case lastTrick:
                    widget.setEnabled(!gameManager.getLastTrickCards().isEmpty());
                    break;
                case yourOffer:
                    widget.setEnabled(getEstimate() >= 0);
                    break;
                case comments:
                case submitLog:
                    widget.setEnabled(host.getLogFileName() != null);
                    break;
                case settings:
                case help:
                    widget.setVisible(menuPanel.isVisible());
                    break;
                case replay:
                case newRound:
                    widget.setVisible(menuPanel.isVisible() && !config().release.get());
                    break;
                case backToGame:
                    widget.setVisible(menuPanel.isVisible() && gameManager.replayMode);
                    break;
            }
            if (widget.isVisible()) {
                ++rowCount;
            }
        }

        if (rowCount == 0) {
            return;
        }

        int hPanel = (int) (hButton * rowCount);
        if (hPanel > panelHeight) {
            hPanel = panelHeight;
        }

        int wPanel = (int) (wButton * menuPanel.getColumnCount());

        int x = panelWidth - metrics.xMargin - wPanel;
        int y = panelHeight + metrics.panelY - metrics.yMargin - hPanel;
        menuPanel.setBounds(x, y, wPanel, hPanel);
    }

    private void placeButtonPanels() {
        for (ButtonPanel buttonPanel : buttonPanels) {
            if (this.roundStage.equals(buttonPanel.getRoundStage())) {
                placeButtonPanel(buttonPanel);
            } else if (buttonPanel != menuPanel) {
                buttonPanel.setVisible(false);
            }
        }
    }

    private void placeButtonPanel(ButtonPanel buttonPanel) {
        int x, y, space;

        double wButton = metrics.cardW * buttonPanel.getScaleW();
        space = metrics.panelWidth - (int) (2 * metrics.cardW) - 4 * metrics.xMargin;
        if (wButton * buttonPanel.getColumnCount() > space) {
            wButton = (double) space / buttonPanel.getColumnCount();
        }

        double hButton = metrics.cardW * buttonPanel.getScaleH();
        space = metrics.panelHeight - (int)(metrics.ySelected * metrics.cardW) -
            2 * (3 * metrics.yMargin + (int)metrics.cardH) - (int) (metrics.cardW * metrics.hLabel);
        if (hButton * buttonPanel.getRowCount() > space) {
            hButton = (double)space / buttonPanel.getRowCount();
        }

        double aspectRatio = buttonPanel.getScaleH() / buttonPanel.getScaleW();
        if (wButton * aspectRatio > hButton) {
            wButton = hButton / aspectRatio;
        } else {
            hButton = wButton * aspectRatio;
        }

        int wPanel = (int) (wButton * buttonPanel.getColumnCount());
        int hPanel = (int) (hButton * buttonPanel.getRowCount());

        x = (metrics.panelWidth - wPanel) / 2;
        y = 2 * metrics.yMargin + metrics.panelY + (int) metrics.cardH + (space - hPanel) / 2;
        buttonPanel.setBounds(x, y, wPanel, hPanel);
        buttonPanel.setVisible(true);

        Widget widget;
        switch (buttonPanel.getRoundStage()) {
            case bidding:
                widget = buttonPanel.getWidget(0, 0);  // min bid, speed vs. convenience
                widget.setText(gameManager.getMinBid().toString());
                Suit trump = gameManager.getMinBid().getTrump();
                if (Suit.DIAMOND.equals(trump) || HEART.equals(trump)) {
                    widget.setColor(Widget.RED_COLOR);
                } else {
                    widget.setColor(Widget.BLACK_COLOR);
                }
                if (currentPlayer != null) {
                    widget = buttonPanel.getWidget(1, 0);  // misere, speed vs. convenience
                    widget.setEnabled(Bid.BID_UNDEFINED.equals(currentPlayer.getBid()) &&
                        gameManager.getMinBid().compareTo(Bid.BID_MISERE) < 0);
                }
                widget = buttonPanel.getWidget(2, 0);   // pass, speed vs. convenience
                // pass, speed vs. convenience
                String text = m("Pass");
                boolean pass = true;
                for (Player p : gameManager.getPlayers()) {
                    if (p.getBid().compareTo(Bid.BID_PASS) > 0) {
                        pass = false;
                        break;
                    }
                }
                if (pass) {
                    text += " *" + (gameManager.getAllPassFactor() + 1);
                }
                widget.setText(text);
                break;

            case drop:
                widget = buttonPanel.getWidget(0, 0);  // drop, speed vs. convenience
                widget.setEnabled(selectedCards.size() == 2);
                widget = buttonPanel.getWidget(1, 0);  // without 3, speed vs. convenience
                widget.setEnabled(currentPlayer != null && !Bid.BID_MISERE.equals(currentPlayer.getBid()));
                break;

            case whistSelection:
                Player p1 = gameManager.getPlayers()[(gameManager.getDeclarer().getNumber() + 1) % NOP];
                Player p2 = gameManager.getPlayers()[(gameManager.getDeclarer().getNumber() + 2) % NOP];
                boolean enable = gameManager.getMinBid().goal() < 8 &&
                    p2 instanceof HumanPlayer &&
                    p2.getBid().equals(BID_UNDEFINED) &&
                    p1.getBid().equals(BID_PASS);
                whistSelectionPanel.getWidget(ButtonCommand.halfWhist).setEnabled(enable);
                whistSelectionPanel.getWidget(ButtonCommand.pass).setEnabled(!enable);
                break;

            case selectWhistOption:
                widget = buttonPanel.getWidget(1, 0);  // standing, speed vs. convenience
                widget.setEnabled(false);        // no standing whist!
                break;
        }
    }

    // the card currently being dragged, if any, and the position paint() last
    // drew it at - null/unset when nothing is being dragged. A GUI can use
    // these to re-draw just this card above its own overlaid widgets, since
    // paint() otherwise draws the whole table beneath those widgets in z-order.
    public Card getDraggedCard() {
        return draggedCard;
    }

    public Point getDraggedCardPosition() {
        return draggedCardPosition;
    }

    public <T> void paint(T graphics) {
        synchronized (metrics) {
            GameManager gameManager = GameManager.getInstance();
            if (metrics.panelWidth == 0 || gameManager == null) {
                return;
            }

            cardPositions.clear();
            currentUserCards.clear();
            draggedCard = null;
            paintTalon(graphics);
            int centerX = metrics.panelX + metrics.panelWidth / 2;
            int centerY = (labels[0].y + labels[0].height - labels[1].y) / 2;
            paintTrick(graphics, gameManager.getTrick().cards2List(), gameManager.getTrick().getStartedBy(), centerX, centerY);
            int index = 1;
            if (currentPlayer != null) {
                index = currentPlayer.getNumber() + 1;
            }
            for (int i = 0; i < gameManager.getPlayers().length; ++i) {
                // paint currentPlayer the last, so her dragging cards be on the top
                paintHand(graphics, gameManager.getPlayers()[(index + i) % NOP]);
            }
        }
    }

    private <T> void paintTalon(T graphics) {
        GameManager gameManager = GameManager.getInstance();
        // getTalonCards() returns the game-logic thread's live, mutable list -
        // it clears/reassigns/removes from it (see GameManager) with no
        // synchronization against this (UI-thread) paint path, so iterating it
        // directly below can throw ConcurrentModificationException if a mutation
        // lands mid-frame. CardList's copy constructor uses ArrayList's
        // Collection constructor internally, which snapshots via toArray()
        // rather than an iterator, so this copy itself can't throw even if the
        // source is being mutated concurrently.
        CardList talonCards = new CardList(gameManager.getTalonCards());
        if (talonCards.isEmpty()) {
            return;
        }
        int dx = (int) (metrics.cardW * metrics.xSuitVisible);
        int w = (int) metrics.cardW + dx;    // adjust for single card?
        int x = (metrics.panelWidth - w) / 2;
        int y = metrics.panelY + metrics.yMargin;

        boolean showCards = gameManager.replayMode ||
            (host.specialOption() & Host.SPECIAL_OPTION_SHOW_CARDS) != 0 ||
            isStage(RoundStage.showTalon);

        if (gameManager.getMinBid().equals(Bid.BID_ALL_PASS)) {
            if (talonCards.size() == 1) {
                showCards = true;
            }
        }
        for (Card card : talonCards) {
            if (showCards) {
                gui.paint(graphics, card, x, y);
            } else {
                gui.paintBack(graphics, x, y);
            }
            if (gameManager.getMinBid().equals(Bid.BID_ALL_PASS)) {
                showCards = true;
            }
            x += dx;
        }
    }

    private <T> void paintHand(T graphics, Player player) {
        int actualW, actualH;
        double cardW, cardH, dx, dy, dxSuit, dySuit;
        int x, y;

        cardW = metrics.cardW;
        cardH = metrics.cardH;
        Alignment alignment = Alignment.values()[player.getNumber()];
        if (alignment.equals(Alignment.South)) {
            actualW = metrics.actualWidth(player.getMyHand());
            actualH = (int) metrics.cardH;
            dx = metrics.xVisible * cardW;
            dxSuit = metrics.xSuitVisible * cardW;
            dy = 0;
            dySuit = dy;
            x = metrics.xMargin;
            int w;
            if (metrics.horizontalLayout) {
                w = actualW + 2 * (int) cardW + 4 * metrics.xMargin;
            } else {
                w = actualW + 2 * metrics.xMargin;
            }
            if (w < metrics.panelWidth) {
                x = (metrics.panelWidth - actualW) / 2;
            }
            Logger.printf(DEBUG_LOG, "panel %d, hand %d, x %d\n", metrics.panelWidth, actualW, x);
            y = metrics.panelY + metrics.panelHeight - actualH - metrics.yMargin;
        } else {
            actualW = (int) metrics.cardW;
            dx = 0;
            dxSuit = dx;
            dy = metrics.yVisible * cardH;
            dySuit = metrics.ySuitVisible * cardH;
            if (alignment.equals(Alignment.West)) {
                x = metrics.xMargin;
            } else {
                x = metrics.panelWidth - metrics.xMargin - (int) (metrics.cardW);
            }
            if (metrics.horizontalLayout) {
                y = metrics.yMargin;
            } else {
                y = 2 * metrics.yMargin + (int) (metrics.cardW * metrics.hLabel);
            }
            y += metrics.panelY;
            Logger.printf(DEBUG_LOG, "panel %d, hand %d, x %d\n", metrics.panelWidth, actualW, x);
        }

        CardSet myHand = player.getMyHand();
        final Suit[] suits = {SPADE, DIAMOND, CLUB, HEART};

        // todo: 2 suits m.b. absent
        if (myHand.list(DIAMOND).isEmpty()) {
            suits[1] = HEART;
        }
        if (myHand.list(CLUB).isEmpty()) {
            suits[0] = DIAMOND;
            suits[2] = SPADE;
        }
        boolean showCards = showCards(player.getNumber());

        Card handDraggedCard = null;
        Point handDraggedPoint = new Point();
        int mask = 0;
        for (Suit suit : suits) {
            int bit = 1 << suit.getValue();
            if ((mask & bit) != 0) {
                continue;   // already painted
            }
            mask |= bit;
            int bm = myHand.list(suit).getBitmap();
            bit = 0;
            boolean nonEmpty = false;
            while ((bit = CardSet.next(bm, bit)) != 0) {
                nonEmpty = true;
                Card card = Card.get(bit);
                int _x = x;
                int _y = y;
                if (selectedCards.contains(card)) {
                    Logger.printf(DEBUG_LOG,"%s, selected %s\n", Util.currMethodName(), selectedCards.toColorString());
                    if (draggingStart.first >= 0) {
                        _x += dragging.first - draggingStart.first;
                        // Pull the reference point (draggingStart) back by the overshoot
                        // instead of overwriting dragging with the clamped, screen-space
                        // position - dragging must stay the raw touch position (only
                        // onMouseDragged() should ever set it) or the delta computed here
                        // on the very next repaint mixes touch-space and screen-space
                        // numbers, snapping the card to a bogus position on any repaint
                        // that isn't immediately preceded by a fresh touch event (visible
                        // as the card jerking/snapping once it's dragged to a screen
                        // border). Adjusting draggingStart instead keeps dragging correct
                        // while still pinning the card at the edge and responding
                        // immediately the instant the drag reverses direction.
                        if (_x < 0) {
                            draggingStart.first += _x;
                            _x = 0;
                        }
                        if (_x > metrics.panelWidth - (int)metrics.cardW) {
                            draggingStart.first += _x - (metrics.panelWidth - (int)metrics.cardW);
                            _x = metrics.panelWidth - (int)metrics.cardW;
                        }
                        _y += dragging.second - draggingStart.second;
                        if (_y < 0) {
                            draggingStart.second += _y;
                            _y = 0;
                        }
                        if (_y > metrics.panelHeight - (int)metrics.cardH) {
                            draggingStart.second += _y - (metrics.panelHeight - (int)metrics.cardH);
                            _y = metrics.panelHeight - (int)metrics.cardH;
                        }
                        handDraggedCard = card;
                        handDraggedPoint.setX(_x);
                        handDraggedPoint.setY(_y);
                        continue;
                    } else {
                        switch (alignment) {
                            case South:
                                _y -= (int)(metrics.ySelected * metrics.cardW);
                                break;
                            case West:
                                _x += (int) (metrics.xSelected * metrics.cardW);
                                break;
                            case East:
                                _x -= (int) (metrics.xSelected * metrics.cardW);
                                break;
                        }
                    }
                }
                if (showCards) {
                    gui.paint(graphics, card, _x, _y);
                } else {
                    gui.paintBack(graphics, x, y);
                }
                if (currentPlayer != null && player.getNumber() == currentPlayer.getNumber()) {
                    cardPositions.add(new Point(x, y));
                    currentUserCards.add(card);
                }
                x += (int)dx;
                y += (int)dy;
            }
            if (showCards && nonEmpty) {
                x += (int)(dxSuit - dx);
                y += (int)(dySuit - dy);
            }
        }
        if (handDraggedCard != null) {
            gui.paint(graphics, handDraggedCard, handDraggedPoint.getX(), handDraggedPoint.getY());
            draggedCard = handDraggedCard;
            draggedCardPosition.set(handDraggedPoint.getX(), handDraggedPoint.getY());
        }

    }

    public <T> void paintTrick(T graphics, CardList trickCards, int startedBy, int centerX, int centerY) {
        Point[] positions = {
            new Point(-(int)(metrics.cardW * .5), -(int)(metrics.cardH * .9)),      // top
            new Point(-(int)(metrics.cardW * .5), -(int)(metrics.cardH * .25)),     // left
            new Point(-(int)(metrics.cardW * .75), -(int)(metrics.cardH * .75)),    // right
            new Point(-(int)(metrics.cardW * .25), -(int)(metrics.cardH * .7)),     // bottom
        };

        if (trickCards.isEmpty()) {
            return;
        }

        int turn = startedBy;
        for (int j = 0; j < trickCards.size(); ++j) {
            Card card = trickCards.get(j);
            int i = turn + 1;
            if (j == 0 && trickCards.size() > NOP) {
                i = 0;
                --turn;
            }
            int x = positions[i].getX() + centerX;
            int y = positions[i].getY() + centerY;
            Logger.printf(DEBUG_LOG, "trick card %s %dx%d\n", card, x, y);
            gui.paint(graphics, card, x, y);
            turn = ++turn % NOP;
        }
    }

    boolean isStage(RoundStage stage) {
        if (stage == null) {
            return false;
        }
        return stage.equals(this.roundStage);
    }

    boolean showCards(int index) {
        GameManager gameManager = GameManager.getInstance();
        if (gameManager.replayMode || (host.specialOption() & Host.SPECIAL_OPTION_SHOW_CARDS) != 0) {
            return true;
        }
        Player player = gameManager.getPlayers()[index];
        if (player instanceof HumanPlayer) {
            return true;
        }
        if (player.getBid().compareTo(BID_6S) >= 0) {
            return false;
        }
        if (!gameManager.cardsRevealed) {
            return false;
        }
        if (player.getBid().equals(BID_WHIST_LAYING)) {
            return true;
        }
        for (Player p : gameManager.getPlayers()) {
            if (p.getBid().equals(BID_WHIST_LAYING)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setCurrentPlayer(Player player) {
        if (player instanceof HumanPlayer) {
            this.currentPlayer = (HumanPlayer) player;
        } else {
            this.currentPlayer = null;
        }
        selectedCards.clear();
        update(null);
    }

    // when a bot plays misère, show its hand as defenders know it (with talon),
    // centered vertically at the screen border on the bot's side
    private void placeDeclarerHand(int panelWidth, int panelHeight, int w, int h) {
        Player declarer = gameManager.getDeclarer();
        CardSet declarerHand = gameManager.getDeclarerHand();
        boolean visible = declarer != null && !(declarer instanceof HumanPlayer) && declarerHand != null &&
            BID_MISERE.equals(gameManager.getMinBid()) &&
            (isStage(RoundStage.play) || isStage(RoundStage.trickTaken) || isStage(RoundStage.confirmMove));
        for (Widget line : declarerHandLines) {
            line.setVisible(visible);
        }
        if (!visible) {
            return;
        }
        int x;
        if (Alignment.values()[declarer.getNumber()].equals(Alignment.West)) {
            x = metrics.xMargin;
        } else {
            x = panelWidth - w - metrics.xMargin;
        }
        int y = metrics.panelY + (panelHeight - declarerHandLines.length * h) / 2;
        for (Suit suit : Suit.values()) {
            Widget line = declarerHandLines[suit.getValue()];
            CardSet cards = declarerHand.list(suit);
            String text = cards.isEmpty() ? "" + suit.getCode() : cards.toString();
            int color = Suit.DIAMOND.equals(suit) || HEART.equals(suit) ? Widget.RED_COLOR : Widget.BLACK_COLOR;
            line.setText(text, color);
            line.setBounds(x, y, w, h);
            y += h;
        }
    }

    public HumanPlayer getCurrentPlayer() {
        return currentPlayer;
    }

    public void onMouseDragged(int x, int y, boolean draggingEnded) {
        if (!config().moveMethod.get().getSelectedValue().equals(MoveMethod.Dragging)) {
            return;
        }
        if (!isStage(RoundStage.play) || currentPlayer == null) {
            return;
        }
        Logger.printf(DEBUG_LOG, "%s, card %s, (%d,%d) draggingEnded=%b, %s\n",
            Util.currMethodName(), selectedCards.toColorString(), x, y, draggingEnded, config().moveMethod.get().getSelectedValue());
        boolean refresh = true;
        if (selectedCards.isEmpty()) {
            if (draggingEnded) {
                return;     // mouse released without dragging
            }
            Card card = getCard(x, y);
            if (!currentPlayer.isOK2Play(card)) {
                return;
            }
            selectedCards.clear();
            selectedCards.add(card);
            dragging.first =
            draggingStart.first = x;
            dragging.second =
            draggingStart.second = y;
        } else {
            dragging.first = x;
            dragging.second = y;
            if (draggingEnded) {
                Card card = selectedCards.first();
                selectedCards.clear();
                int dx = dragging.first - draggingStart.first;
                int dy = dragging.second - draggingStart.second;
                draggingStart.first = -1;
                if (Math.abs(dx) > (int)metrics.cardW / 2 ||
                        Math.abs(dy) > (int)metrics.cardH / 2) {
                    // unblock human player
                    currentPlayer.accept(card);
                    Logger.printf(DEBUG_LOG, "unblocked, selected %s\n", card);
                    currentPlayer = null;
                    refresh = false;
                }
            }
        }
        if (refresh) {
            update(null);   // refresh screen
        }
    }

    public void onMouseClick(int x, int y) {
        if (isStage(RoundStage.showTalon)) {
            currentPlayer.accept(BID_XN);   // fake value
            return;
        }
        if (isStage(RoundStage.confirmMove)) {
            if (currentPlayer != null) {
                currentPlayer.accept(BID_XN);   // fake value
                currentPlayer = null;           // a stray click must not queue a card
            }
            return;
        }
        menuPanel.setVisible(false);

        if (!isStage(RoundStage.drop) && !isStage(RoundStage.play) && !isStage(RoundStage.trickTaken)) {
            update(null);   // refresh menu
            return;
        }

        if (currentPlayer == null) {
            update(null);   // refresh menu
            return;
        }

        Card card = getCard(x, y);
        if (card == null) {
            update(null);   // refresh menu
            return;
        }

        if (RoundStage.drop.equals(roundStage)) {
            if (selectedCards.contains(card)) {
                selectedCards.remove(card);
            } else if (selectedCards.size() < 2) {
                selectedCards.add(card);
            }
            update(null);
            return;
        }

        if (!config().moveMethod.get().getSelectedValue().equals(MoveMethod.SingleClick) &&
            !config().moveMethod.get().getSelectedValue().equals(MoveMethod.DoubleClick)) {
            return;
        }
        draggingStart.first = -1;

        if (!currentPlayer.isOK2Play(card)) {
            return;
        }

        Logger.printf(DEBUG_LOG, "%s, card=%s, (%d,%d), %s\n",
            Util.currMethodName(), selectedCards.toColorString(), x, y, config().moveMethod.get().getSelectedValue());
        if (MoveMethod.DoubleClick.equals(config().moveMethod.get().getSelectedValue())) {
            if (!selectedCards.contains(card)) {
                selectedCards.clear();
                selectedCards.add(card);
                Logger.printf(DEBUG_LOG, "clicked, new currentHandVisualData.card %s", card);
                update(null);
                return;
            }
        }

        // unblock human player
        currentPlayer.accept(card);
        Logger.printf(DEBUG_LOG, "unblocked, selected %s\n", card);
        selectedCards.clear();
        currentPlayer = null;
    }

    // convert click point to card
    Card getCard(int x, int y) {
        int width = (int)metrics.cardW;
        int height = (int)metrics.cardH;

        for (int j = cardPositions.size() - 1; j >= 0; --j) {
            Point p = cardPositions.get(j);
            if (x < p.getX() || x >= p.getX() + width ||
                    y < p.getY() || y >= p.getY() + height) {
                continue;
            }
            return currentUserCards.get(j);
        }
        return null;
    }

    public GameManager.RestartCommand showScores() {
        return gui.showScores(true);
    }

    private void execCommand(ButtonCommand buttonCommand) {
        Logger.printf(DEBUG_LOG, "%s for %s\n", Util.currMethodName(), buttonCommand.getName());
        boolean menuVisible = menuPanel.isVisible();
        menuPanel.setVisible(false);
        if (menuVisible) {
            update(null);   // hide menu
        }
        switch (buttonCommand) {
            case menu:
                menuPanel.setVisible(true);
                break;

            // menu panel:
            case showScores:
                gui.showScores(false);
                host.repaintAll();
                break;
            case lastTrick:
                gui.update();
                gui.showLastTrick(gameManager.getLastTrickCards(), gameManager.getLastTrickStartedBy());
                break;
            case yourOffer:
                getOffer();
                break;
            case replay:
                GameManager.getInstance().restart(GameManager.RestartCommand.replay);
                break;
            case newRound:
            case backToGame:
                GameManager.getInstance().restart(GameManager.RestartCommand.newRound);
                break;
            case comments:
                String userComments = gui.getUserComments();
                if (!userComments.trim().isEmpty()) {
                    Logger.printf("*** comment start ***\n%s\n*** comment end ***\n", userComments);
                }
                break;
            case submitLog:
                submitLog(gui);
                break;
            case settings:
                host.updateSettings();
                break;
            case help:
                showHelp();
                break;

            // bidding panel:
            case minBid:
                currentPlayer.accept(gameManager.getMinBid());
                break;
            case pass:
                // for both bidding and whisting
                currentPlayer.accept(Bid.BID_PASS);
                break;
            case misere:
                currentPlayer.accept(Bid.BID_MISERE);
                break;

            // drop panel:
            case drop:
                currentPlayer.drop(selectedCards);
                break;

            case without3:
                currentPlayer.accept(Bid.BID_WITHOUT_THREE);
                break;

            // declare round panel:
            case greaterGame:
            case prevSuit:
            case nextSuit:
            case lesserGame:
                setDeclareRoundPanel(buttonCommand);
                gui.update();
                return;
            case select:
                currentPlayer.accept(currentBid);
                break;

            // whist selection panel:
            case whist:
                currentPlayer.accept(Bid.BID_WHIST);
                break;
            case halfWhist:
                currentPlayer.accept(Bid.BID_HALF_WHIST);
                break;

            // whist option panel:
            case laying:
                currentPlayer.accept(Bid.BID_WHIST_LAYING);
                break;
            case standing:
                // disabled
                currentPlayer.accept(Bid.BID_WHIST_STANDING);
                break;
        }
        update(null);
    }

    public synchronized void submitLog(GUI gui) {
        while (!host.getUtil().isConnected()) {
            if (this.gui.showMessage(m("No Internet Connection"), m("Please correct"),
                    GUI.msgFlagOK | GUI.msgFlagCancel) != GUI.msgFlagOK) {
                return;
            }
        }
        Logger.flush();     // submit the log up to this moment
        String logFilePath = host.getLogFileName();
        final String[] res = new String[1];
        Thread worker = new Thread(() -> {
            res[0] = host.getUtil().submitLog(logFilePath);
            System.out.println(res[0]);
        });
        worker.start();
        try {
            worker.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        File f = new File(logFilePath);
        String fn = f.getName();
        String msg = res[0];
        if (msg.startsWith(fn)) {
            msg = m(msg.substring(fn.length() + 1));
        } else {
            fn = "";
        }
        String text = String.format("%s %s\n%s", fn, msg, m("Restart JPref") + "?");
        if (gui != null) {
            if (gui.showMessage(m("Confirmation"), text,
                TableLayout.GUI.msgFlagYes | TableLayout.GUI.msgFlagNo) == TableLayout.GUI.msgFlagYes) {
                // runs on the button handler's thread; the game thread picks it up
                // and throws PrefExceptionReset out of runGame() to the main loop
                GameManager.getInstance().restart(GameManager.RestartCommand.reset);
            }
        }
    }

    private void showHelp() {
        final String versionVar = "<!-- *** VERSION ***-->";
        final String remoteMark = "<!--*** REMOTE ***-->";
        final String androidMark = "<!--*** ANDROID_SKIP ***-->";

        String skipMark = remoteMark;
        if (host.getOS().equals(OS.android)) {
            skipMark = androidMark;
        }

        String version = Config.VERSION + " built " + new SimpleDateFormat("yyyy-MM-dd").format(host.buildDate());
        String src = I18n.loadString("index.html").replace(versionVar, version);
        StringBuilder sb = new StringBuilder();
        int start = 0;
        int end;
        while ((end = src.indexOf(skipMark, start)) >= 0) {
            sb.append(src, start, end);
            start = src.indexOf(skipMark, end + 1);
            if (start < 0) {
                start = src.length();
                break;
            }
            start += skipMark.length();
        }
        sb.append(src, start, src.length());
        gui.showMessage(m("Description"), sb.toString());
    }

    // select trump suit and tricks
    private void setDeclareRoundPanel(ButtonCommand buttonCommand) {
        if (buttonCommand == null) {
            currentBid = gameManager.getMinBid();
            buttonCommand = ButtonCommand.ok;
        }
        int minRound = currentPlayer.getBid().goal();
        int minSuit = currentPlayer.getBid().getValue() % 10;
        int roundValue = currentBid.goal();
        int suitNum = currentBid.getValue() % 10;
        Logger.printf(DEBUG_LOG, "setDeclareRoundPanel curr %s, %d, %d\n", currentBid, roundValue, suitNum);

        switch (buttonCommand) {
            case prevSuit:
                --suitNum;
                break;
            case nextSuit:
                ++suitNum;
                break;
            case lesserGame:
                --roundValue;
                break;
            case greaterGame:
                ++roundValue;
                break;
        }

        currentBid = Bid.fromValue(roundValue * 10 + suitNum);  // new current bid
        Logger.printf(DEBUG_LOG, "setDeclareRoundPanel new %s, %d, %d\n", currentBid, roundValue, suitNum);
        int fgColor = getSuitColor(suitNum);
        String text = currentBid.getName();
        if (roundValue == 10) {
            text = roundValue + text.substring(1);
        }
        declareRoundPanel.getWidget(ButtonCommand.select).setText(text, fgColor);

        if (suitNum == 1 || roundValue == minRound && suitNum <= minSuit) {
            declareRoundPanel.getWidget(ButtonCommand.prevSuit).setText("");
            declareRoundPanel.getWidget(ButtonCommand.prevSuit).setEnabled(false);
        } else {
            int _suitNum = suitNum - 1;
            char _text = Suit.values()[_suitNum - 1].getCode();
            declareRoundPanel.getWidget(ButtonCommand.prevSuit).setText(_text, getSuitColor(_suitNum));
            declareRoundPanel.getWidget(ButtonCommand.prevSuit).setEnabled(true);
        }

        if (suitNum >= 5) {
            declareRoundPanel.getWidget(ButtonCommand.nextSuit).setText("");
        } else if (suitNum == 4) {
            declareRoundPanel.getWidget(ButtonCommand.nextSuit).setText(Config.NO_TRUMP);
        } else {
            int _suitNum = suitNum + 1;
            char _text = Suit.values()[_suitNum - 1].getCode();
            declareRoundPanel.getWidget(ButtonCommand.nextSuit).setText(_text, getSuitColor(_suitNum));
        }
        declareRoundPanel.getWidget(ButtonCommand.nextSuit).setEnabled(suitNum < 5);

        if (roundValue <= minRound || roundValue == minRound + 1 && suitNum < minSuit) {
            declareRoundPanel.getWidget(ButtonCommand.lesserGame).setText("");
            declareRoundPanel.getWidget(ButtonCommand.lesserGame).setEnabled(false);
        } else {
            declareRoundPanel.getWidget(ButtonCommand.lesserGame).setText(roundValue - 1, fgColor);
            declareRoundPanel.getWidget(ButtonCommand.lesserGame).setEnabled(true);
        }

        if (roundValue >= 10) {
            declareRoundPanel.getWidget(ButtonCommand.greaterGame).setText("");
        } else {
            declareRoundPanel.getWidget(ButtonCommand.greaterGame).setText(roundValue + 1, fgColor);
        }
        declareRoundPanel.getWidget(ButtonCommand.greaterGame).setEnabled(roundValue < 10);
    }

    private int getSuitColor(int suitNum) {
        if (suitNum == 3 || suitNum == 4) {
            return Widget.RED_COLOR;
        }
        return Widget.BLACK_COLOR;
    }

    int getEstimate() {
        if (!gameManager.getRoundStage().equals(RoundStage.play) &&
                !gameManager.getRoundStage().equals(RoundStage.trickTaken) &&
                !gameManager.getRoundStage().equals(RoundStage.confirmMove)) {
            return -1;
        }
        Player player = gameManager.getDeclarer();
        if (player == null) {
            player = gameManager.getPlayers()[0];   // all-pass, player is user
        }
        int tricksEstimate;
        if (gameManager.getMinBid().equals(Bid.BID_MISERE) || gameManager.getMinBid().equals(BID_ALL_PASS)) {
            boolean myTurn = gameManager.getTrick().getTurn() == gameManager.declarerNumber &&
                gameManager.getTrick().isEmpty();
            tricksEstimate = player.getTricks() +
                CardSet.holes(player.getMyHand(), gameManager.getDiscarded(), myTurn);
            if (myTurn) {
                if (tricksEstimate < 10) {
                    ++tricksEstimate;   // this is a pessimistic estimate
                }
            }
        } else {
            tricksEstimate = TrickList.getInstance().getEstimate();
        }
        return tricksEstimate;
    }

    // offer always for player[0]
    private void getOffer() {
        int minTricks, maxTricks;
        Player[] players = gameManager.getPlayers();
        int tricksEstimate = getEstimate();
        int _minTricks;

        switch (players[0].getBid()) {
            case BID_MISERE:
                minTricks = tricksEstimate;
                maxTricks = ROUND_SIZE - players[1].getTricks() - players[2].getTricks();
                minTricks = Math.min(minTricks, maxTricks);
                break;

            case BID_WHIST:
            case BID_WHIST_LAYING:
            case BID_WHIST_STANDING:
                minTricks = players[0].getTricks();
                if (players[1].getBid() == Bid.BID_PASS) {
                    minTricks += players[1].getTricks();
                } else {
                    minTricks += players[2].getTricks();
                }
                if (gameManager.getMinBid().equals(Bid.BID_MISERE)) {
                    minTricks =
                    maxTricks = ROUND_SIZE - gameManager.getDeclarer().getTricks();
                } else {
                    maxTricks = ROUND_SIZE - tricksEstimate;
                }
                break;

            case BID_ALL_PASS:
                minTricks = tricksEstimate;
                maxTricks = ROUND_SIZE - players[1].getTricks() - players[2].getTricks();
                break;


            default:
                // game for tricks, declarer
                minTricks = players[0].getTricks();
                maxTricks = Math.min(ROUND_SIZE - players[1].getTricks() - players[2].getTricks(), tricksEstimate);
                break;
        }
        int acceptedTricks = gui.showOffer(minTricks, maxTricks);
        if (acceptedTricks < 0) {
            return;     // rejected
        }
        players[0].setTricks(acceptedTricks);          // human
        if (gameManager.getDeclarer() == null) {
            // all-pass
            int others = ROUND_SIZE - acceptedTricks - players[2].getTricks();
            players[1].setTricks(others);              // whist/declarer
        } else {
            int others = ROUND_SIZE - acceptedTricks;
            if (players[2].getBid() == Bid.BID_PASS) {
                players[2].setTricks(0);
                players[1].setTricks(others);              // whist/declarer
            } else {
                players[1].setTricks(0);
                players[2].setTricks(others);
            }
        }
        GameManager.getInstance().restart(GameManager.RestartCommand.offer);
    }

    public interface GUI {
        int msgFlagOK = 0x1;
        int msgFlagCancel = 0x2;
        int msgFlagYes = 0x4;
        int msgFlagNo = 0x8;
        void update();
        <T> void paint(T graphics, Card card, int x, int y);
        <T> void paintBack(T graphics, int x, int y);
        void add(Widget widget);
        String getUserComments();
        void showMessage(String title, String text);
        // shows a button for each msgFlagXXX set in flags, returns the flag of the clicked one
        int showMessage(String title, String text, int flags);
        void showLastTrick(CardList cards, int startedBy);
        GameManager.RestartCommand showScores(boolean showButtons);
        int showOffer(int minTricks, int maxTricks);
    }
}