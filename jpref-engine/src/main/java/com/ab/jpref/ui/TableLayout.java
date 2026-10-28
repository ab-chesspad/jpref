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
import com.ab.util.*;

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

    // set by the game thread, cleared by the UI thread
    private volatile HumanPlayer currentPlayer;
    // a half-transparent stamp across the table until the round ends, null for none
    private volatile String banner;
    static final String
        BANNER_DECLINED = "Declined",
        BANNER_DECLINED_OPEN = "Declined, your cards are open",
        BANNER_FIT = BANNER_DECLINED_OPEN;  // the longest, all banners get the font that fits it
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
                    if (RoundStage.bidding.equals(roundStage)) {
                        banner = null;      // a new round, or the round replayed
                    }
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
                w = (int) (metrics.cardW * metrics.wLabel * 0.95);
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
                    // right of a system bar on the left, Android in landscape
                    x += metrics.panelX;
                    if (i == gameManager.elderHand) {
                        elderHandLocation.first += metrics.panelX;
                    }
                    label.setBounds(x, y, w, h);
                }
                placeDeclarerHand(panelWidth, panelHeight, w, h);

                // menu
                w = (int)(metrics.cardW * metrics.wButton);
                h = (int)(metrics.cardW * metrics.hButton);
                x = metrics.panelX + panelWidth - w - metrics.xMargin;
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
                    boolean enabled = roundStage.equals(RoundStage.trickTaken) ||
                        roundStage.equals(RoundStage.play);
                    if (BID_ALL_PASS.equals(gameManager.getMinBid()) && !gameManager.getTalonCards().isEmpty()) {
                        enabled = false;    // all-pass tricks with the talon: a talon card is removed after its trick
                    }
                    if (gameManager.replayMode) {
                        enabled = false;
                    }
                    widget.setEnabled(enabled);
                    break;
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

        int x = metrics.panelX + panelWidth - metrics.xMargin - wPanel;
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

        x = metrics.panelX + (metrics.panelWidth - wPanel) / 2;
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
                    text += " *" + (gameManager.getAllPassFactor() + 1) * ScoreCalculator.gameFactor();
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
            String banner = this.banner;
            if (banner != null) {
                gui.paintBanner(graphics, banner, BANNER_FIT, metrics.panelX, metrics.panelY, metrics.panelWidth, metrics.panelHeight);
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
        int x = metrics.panelX + (metrics.panelWidth - w) / 2;
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
            // between the side hands in the horizontal layout; a hand wider than that, 12 cards,
            // overlaps its cards more instead of covering the left hand, centered either way
            int room = metrics.panelWidth - 2 * metrics.xMargin;
            if (metrics.horizontalLayout) {
                room -= 2 * ((int) cardW + metrics.xMargin);
            }
            if (actualW > room) {
                double k = (room - cardW) / (actualW - cardW);
                dx *= k;
                dxSuit *= k;
                actualW = room;
            }
            x = metrics.panelX + (metrics.panelWidth - actualW) / 2;
            Logger.printf(DEBUG_LOG, "panel %d, hand %d, x %d\n", metrics.panelWidth, actualW, x);
            y = metrics.panelY + metrics.panelHeight - actualH - metrics.yMargin;
        } else {
            actualW = (int) metrics.cardW;
            dx = 0;
            dxSuit = dx;
            dy = metrics.yVisible * cardH;
            dySuit = metrics.ySuitVisible * cardH;
            if (alignment.equals(Alignment.West)) {
                x = metrics.panelX + metrics.xMargin;
            } else {
                x = metrics.panelX + metrics.panelWidth - metrics.xMargin - (int) (metrics.cardW);
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
                        if (_x < metrics.panelX) {
                            draggingStart.first += _x - metrics.panelX;
                            _x = metrics.panelX;
                        }
                        int maxX = metrics.panelX + metrics.panelWidth - (int)metrics.cardW;
                        if (_x > maxX) {
                            draggingStart.first += _x - maxX;
                            _x = maxX;
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
        if (gameManager.getMinBid().goal() == 10 && !config().whistTotus.get()) {
            return true;
        }

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
        int declarerNumber = gameManager.declarerNumber;
        boolean visible = !gameManager.replayMode && declarerNumber != 0 &&
            BID_MISERE.equals(gameManager.getMinBid()) &&
            (isStage(RoundStage.play) || isStage(RoundStage.trickTaken) || isStage(RoundStage.confirmMove));
        for (Widget line : declarerHandLines) {
            line.setVisible(visible);
        }
        if (!visible) {
            return;
        }
        CardSet declarerHand = gameManager.getDeclarerHand();
        int x;
        if (Alignment.values()[declarerNumber].equals(Alignment.West)) {
            x = metrics.panelX + metrics.xMargin;
        } else {
            x = metrics.panelX + panelWidth - w - metrics.xMargin;
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
                    unblock(card);
                    refresh = false;
                }
            }
        }
        if (refresh) {
            update(null);   // refresh screen
        }
    }

    // a drag interrupted by the system (ACTION_CANCEL): drop the card back, nothing is played
    public void onMouseDragCanceled() {
        draggingStart.first = -1;
        if (!selectedCards.isEmpty() && !isStage(RoundStage.drop)) {
            selectedCards.clear();
            update(null);
        }
    }

    // Cleared before accept(): after it the game thread may already ask the human again,
    // e.g. the next hand is the human's too (misère defense), and clearing it afterwards
    // would wipe the new currentPlayer, no card click would work anymore
    private void unblock(Config.Queueable q) {
        HumanPlayer player = currentPlayer;
        if (player == null) {
            return;
        }
        currentPlayer = null;
        selectedCards.clear();
        player.accept(q);
        Logger.printf(DEBUG_LOG, "unblocked, selected %s\n", q);
    }

    public void onMouseClick(int x, int y) {
        if (isStage(RoundStage.showTalon)) {
            unblock(BID_XN);   // fake value
            return;
        }
        if (isStage(RoundStage.confirmMove)) {
            unblock(BID_XN);   // fake value, a stray click must not queue a card
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
        unblock(card);
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
        banner = null;      // the round is over
        return gui.showScores(true);
    }

    private void execCommand(ButtonCommand buttonCommand) {
        Logger.printf("%s %s\n", Util.currMethodName(), buttonCommand.getName());
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
            case submitLog:
                String userComments = gui.getUserComments();
                if (userComments == null) {
                    break;      // canceled
                }
                if (!userComments.trim().isEmpty()) {
                    Logger.printf("*** comment start ***\n%s\n*** comment end ***\n", userComments);
                }
                submitLog(gui);
                break;
            case settings:
                host.updateSettings();
                break;
            case help:
                showHelp();
                break;

            // declare round panel:
            case greaterGame:
            case prevSuit:
            case nextSuit:
            case lesserGame:
                setDeclareRoundPanel(buttonCommand);
                gui.update();
                return;

            default:
                // a button stays visible while bots move, a tap then or a double tap must not
                // reach the human player: no currentPlayer -> ignored.
                // Cleared before accept(), after it the game thread may already ask the human again
                HumanPlayer player = currentPlayer;
                if (player != null) {
                    currentPlayer = null;
                    execCommand4currentPlayer(player, buttonCommand);
                }
                break;
        }
        update(null);
    }

    private void execCommand4currentPlayer(HumanPlayer currentPlayer, ButtonCommand buttonCommand) {
        switch (buttonCommand) {
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
    }

    public synchronized void submitLog(GUI gui) {
        while (!host.getUtil().isConnected()) {
            if (this.gui.showMessage("No Internet Connection", "Please correct",
                    "Retry", "Cancel") != 0) {
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
        int i = fn.lastIndexOf(".");
        if (i < 0) {
            i = fn.length();
        }
        String msg = res[0];
        if (msg.startsWith(fn.substring(0, i))) {
            msg = m(msg.substring(fn.length() + 1));
        } else {
            fn = "";
        }
        String text = String.format("%s %s\n%s", fn, msg, m("Restart JPref") + "?");
        if (gui != null) {
            if (gui.showMessage("Confirmation", text, "Yes", "No") == 0) {
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
        gui.showMessage("Description", sb.toString(), "Continue");
    }

    // select trump suit and tricks
    private void setDeclareRoundPanel(ButtonCommand buttonCommand) {
        if (buttonCommand == null) {
            currentBid = gameManager.getMinBid();
        }
        int minRound = currentPlayer.getBid().goal();
        int minSuit = currentPlayer.getBid().getValue() % 10;
        int roundValue = currentBid.goal();
        int suitNum = currentBid.getValue() % 10;
        Logger.printf(DEBUG_LOG, "setDeclareRoundPanel curr %s, %d, %d\n", currentBid, roundValue, suitNum);

        if (buttonCommand != null) {
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

    // offer always for player[0]
    private void getOffer() {
        Player[] players = gameManager.getPlayers();
        int minTricks, maxTricks;

        boolean forMaxTricks;
        minTricks = players[0].getTricks();
        maxTricks = ROUND_SIZE - players[1].getTricks() - players[2].getTricks();
        if (players[0].getBid().equals(BID_WHIST) ||
                players[0].getBid().equals(BID_WHIST_LAYING) ||
                players[0].getBid().equals(BID_WHIST_STANDING)) {
            forMaxTricks = !gameManager.getMinBid().equals(BID_MISERE);
            if (players[1].getBid() == Bid.BID_PASS) {
                minTricks += players[1].getTricks();
                maxTricks = ROUND_SIZE - players[2].getTricks();
            } else {
                minTricks += players[2].getTricks();
                maxTricks = ROUND_SIZE - players[1].getTricks();
            }
        } else {
            // all-pass or players[0] misère: the fewer, the better
            forMaxTricks = !gameManager.getMinBid().equals(BID_ALL_PASS) &&
                !gameManager.getMinBid().equals(BID_MISERE);
        }
        Logger.printf("offer: forMaxTricks:%b min: %d, max: %d\n", forMaxTricks, minTricks, maxTricks);
        // players[0] cards are open already: a defender's laid out for the round (cardsRevealed opens
        // the defenders' cards only), or after a declined offer, banner; nothing more to show
        boolean cardsOpen = gameManager.cardsRevealed && gameManager.declarerNumber != 0 || banner != null;
        int acceptedTricks = gui.showOffer(minTricks, maxTricks, cardsOpen);
        if (acceptedTricks < 0) {
            return;     // declined
        }
        // the rest of the round played with open cards: players[1] and players[2] tricks
        int[] dryRun = dryRun();
        if (gameManager.getDeclarer() == null || !BID_PASS.equals(players[0].getBid())) {
            // the opponents check the offer: accepted when it gives them no less than the open cards
            int dryRunTricks = dryRunTricks(dryRun);
            Logger.printf("offer forMaxTricks=%b: accepted: %d, dry run: %d\n", forMaxTricks, acceptedTricks, dryRunTricks);
            if (forMaxTricks ? acceptedTricks > dryRunTricks : acceptedTricks < dryRunTricks) {
                if (!cardsOpen) {
                    // only the user saw them, the offer has shown them to the others
                    gameManager.revealHand(0);
                }
                // the declarer's or all-pass cards are open by the offer, even the second time;
                // a defender's laid out for the round were open anyway
                if (gameManager.declarerNumber > 0 && gameManager.cardsRevealed) {
                    banner = BANNER_DECLINED;
                } else {
                    banner = BANNER_DECLINED_OPEN;
                }
                update(null);
                return;     // the game goes on, players[0] cards are known
            }
        }
        players[0].setTricks(acceptedTricks);
        int declarerNumber = gameManager.declarerNumber;
        if (gameManager.getDeclarer() != null && declarerNumber != 0) {
            // players[0] defends, the offer counts the partner's tricks for players[0]
            for (int i = 1; i < NOP; ++i) {
                players[i].setTricks(i == declarerNumber ? ROUND_SIZE - acceptedTricks : 0);
            }
        } else {
            // the offer may give the others more than the open cards or, all-pass, fewer:
            // the difference from the one with the most tricks on
            int diff = acceptedTricks - dryRun[0];
            int[] tricks = dryRun.clone();
            while (diff != 0) {
                int most = tricks[1] >= tricks[2] ? 1 : 2;
                if (diff > 0 && tricks[most] == 0) {
                    break;
                }
                tricks[most] -= Integer.signum(diff);
                diff -= Integer.signum(diff);
            }
            for (int i = 1; i < NOP; ++i) {
                players[i].setTricks(tricks[i]);
            }
        }
        GameManager.getInstance().restart(GameManager.RestartCommand.offer);
    }

    // every player's tricks at the end of the round, the rest of it played with open cards,
    // players[0] side against the others: the least gain or the max loss
    private int[] dryRun() {
        Player[] players = gameManager.getPlayers();
        int declarerNumber = gameManager.declarerNumber;
        int counted;
        int maximizers;
        if (gameManager.getDeclarer() == null || declarerNumber == 0) {
            counted = 1;
            if (gameManager.getDeclarer() == null || BID_MISERE.equals(gameManager.getMinBid())) {
                maximizers = 1 << 1 | 1 << 2;   // all-pass or players[0] misère: the others give all they can
            } else {
                maximizers = 1;
            }
        } else {
            int defenders = 1 | 1 << (NOP - declarerNumber);   // players 0, 1, 2
            maximizers = defenders;
            if (BID_MISERE.equals(gameManager.getMinBid())) {
                counted = 1 << declarerNumber;  // the defenders catch the declarer
            } else {
                counted = defenders;
            }
        }
        int[] res = OpenPlay.futureTricksByPlayer(counted, maximizers);
        for (int i = 0; i < NOP; ++i) {
            res[i] += players[i].getTricks();
        }
        return res;
    }

    // players[0] tricks as the offer counts them
    private int dryRunTricks(int[] tricks) {
        int declarerNumber = gameManager.declarerNumber;
        if (gameManager.getDeclarer() == null || declarerNumber == 0) {
            return tricks[0];
        }
        if (BID_MISERE.equals(gameManager.getMinBid())) {
            return ROUND_SIZE - tricks[declarerNumber];     // the declarer gets the rest
        }
        return tricks[0] + tricks[NOP - declarerNumber];    // with the partner
    }

    public interface GUI {
        void update();
        <T> void paint(T graphics, Card card, int x, int y);
        <T> void paintBack(T graphics, int x, int y);
        // half-transparent text across the area, as Game Over in StatusPopup, translated with m();
        // the font is sized so that fitText fits the width, the same for all banners
        <T> void paintBanner(T graphics, String text, String fitText, int x, int y, int width, int height);
        void add(Widget widget);
        // the comments to write into the log before it is submitted, null on Cancel
        String getUserComments();
        // the dialog translates title, text and buttons with m()
        // returns the index of the clicked button; the last one is the default, also on close
        int showMessage(String title, String text, String... buttons);
        void showLastTrick(CardList cards, int startedBy);
        GameManager.RestartCommand showScores(boolean showButtons);
        // cardsOpen: players[0] cards are open, a defender's laid out for the round or after a rejected offer
        int showOffer(int minTricks, int maxTricks, boolean cardsOpen);
    }
}