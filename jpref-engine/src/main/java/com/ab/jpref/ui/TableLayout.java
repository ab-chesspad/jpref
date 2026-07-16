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
import static com.ab.jpref.config.Config.Bid.BID_XN;
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

public class TableLayout<T> implements GameManager.EventObserver {
    public static final boolean DEBUG_LOG = false;

    public enum ButtonCommand {
        menu("Menu"),
        settings("Settings"),
        comments("Comments"),
        help("Help"),

        minBid("Min Bid"),
        misere("Misère"),
        whist("Whist"),
        halfWhist("½ Whist"),
        pass("Pass"),
        drop("Drop"),
        without3("Without Three"),

        lying("Lying"),
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
        submitLog("Submit Log"),
        yourOffer("Your Offer"),

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
    final CardList selectedCards = new CardList();

    final GUI<T> gui;
    final Host host;
    final Config config;
    final Metrics metrics;

    int panelWidth = -1;
    int panelHeight = -1;

    // widgets:
    private final List<ButtonPanel> buttonPanels = new ArrayList<>();
    public final Widget[] labels = new Widget[NOP];
    public final Widget menuBtn;
    public final ButtonPanel menuPanel;
    public final ButtonPanel declareRoundPanel;
    public final ButtonPanel whistSelectionPanel;

    private Bid currentBid;
    public final Couple<Integer> elderHandLocation = new Couple<>();
    final CardList currentUserCards = new CardList();
    RoundStage roundStage;

    private static TableLayout<?> instance;
    public static TableLayout<?> getInstance() {
        return instance;
    }

    public TableLayout(Host host, GUI<T> gui) {
        this.host = host;
        this.gui = gui;
        instance = this;
        metrics = host.getMetrics();
        config = host.getConfig();

        create(RoundStage.bidding, 4, 1,
            new ButtonCommand[][]{
                {ButtonCommand.minBid},
                {ButtonCommand.misere},
                {ButtonCommand.pass},
            });
        create(RoundStage.drop, 4, 1,
            new ButtonCommand[][]{
                {ButtonCommand.drop},
                {ButtonCommand.without3},
            });
        declareRoundPanel = create(RoundStage.declareRound, 1.5, 1.5,
            new ButtonCommand[][]{
                {null, ButtonCommand.greaterGame, null},
                {ButtonCommand.prevSuit, ButtonCommand.select, ButtonCommand.nextSuit},
                {null, ButtonCommand.lesserGame, null},
            });
        whistSelectionPanel = create(RoundStage.whistSelection, 4, 1,
            new ButtonCommand[][]{
                {ButtonCommand.whist},
                {ButtonCommand.halfWhist},
                {ButtonCommand.pass},
            });
        ButtonPanel whistOptionPanel = create(RoundStage.selectWhistOption, 4, 1,
            new ButtonCommand[][]{
                {ButtonCommand.lying},
                {ButtonCommand.standing},
            });
        // no standing whist!
        Widget standing = whistOptionPanel.getWidget(1, 0);
        standing.setEnabled(false);

        // labels
        for (int i = 0; i < NOP; ++i) {
            labels[i] = new Widget(i);
            gui.add(labels[i]);
        }

        // menu
        menuBtn = new Widget(ButtonCommand.menu, buttonCommand -> execCommand(buttonCommand));
        gui.add(menuBtn);

        menuPanel = create(null, 3.5, .6,
            new ButtonCommand[][]{
                {ButtonCommand.showScores},
                {ButtonCommand.lastTrick},
                {ButtonCommand.yourOffer},
                {ButtonCommand.replay},
                {ButtonCommand.newRound},
                {ButtonCommand.comments},
                {ButtonCommand.submitLog},
                {ButtonCommand.settings},
                {ButtonCommand.help},
            });
    }

    private ButtonPanel create(RoundStage roundStage, double scaleW, double scaleH, ButtonCommand[][] commands) {
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
        ButtonPanel res = new ButtonPanel(roundStage, scaleW, scaleH, handlers);
        for (Widget w : res) {
            gui.add(w);
        }
        Logger.printf(DEBUG_LOG, "%s, widget end # %d\n", roundStageName, Widget.count);
        buttonPanels.add(res);
        return res;
    }

    @Override
    public void update(RoundStage roundStage) {
        Logger.printf(DEBUG_LOG, "%s %s\n", Util.currMethodName(), roundStage);
        gameManager = GameManager.getInstance();
        metrics.recalculateSizes();
        if (metrics.cardW <= 0) {
            return;
        }

        int panelWidth = config.mainSize.first;
        int panelHeight = config.mainSize.second;
        if (this.panelWidth == panelWidth && this.panelHeight == panelHeight && roundStage == null) {
            gui.update();
            return;
        }

        if (roundStage != null) {
            this.roundStage = roundStage;
        }

        int x, y, w, h;

        if (this.roundStage != null) {
            if (isStage(RoundStage.declareRound)) {
                if (currentPlayer != null && !declareRoundPanel.isVisible()) {
                    setDeclareRoundPanel(null);
                }
            }
            placeButtonPanels();
        }

        // labels:
        x = y = 0;
        w = (int)(metrics.cardW * metrics.wLabel);
        h = (int)(metrics.cardW * metrics.hLabel);
        for (int i = 0; i < gameManager.getPlayers().length; ++i) {
            Widget label = labels[i];
            switch (Alignment.values()[i]) {
                case South:
                    x = (int)((panelWidth - metrics.cardW * metrics.wLabel) / 2 - metrics.xMargin);
                    y = (int)(panelHeight - metrics.cardH -
                        h - 2 * metrics.yMargin);
                    if (i == gameManager.elderHand) {
                        elderHandLocation.first = x + w + metrics.xMargin;
                        elderHandLocation.second = y + h - (int)(metrics.cardW * metrics.wElderHand);
                    }
                    break;

                case West:
                    y = metrics.yMargin;
                    if (i == gameManager.elderHand) {
                        elderHandLocation.first = (int)(metrics.cardW) + 2* metrics.xMargin;
                        elderHandLocation.second = y + metrics.yMargin + h;
                    }
                    if (metrics.horizontalLayout) {
                        x = (int)(2 * metrics.xMargin + metrics.cardW);
                    } else {
                        x = metrics.xMargin;
                    }
                    break;

                case East:
                    y = metrics.yMargin;
                    x = panelWidth - w - metrics.xMargin;
                    if (i == gameManager.elderHand) {
                        elderHandLocation.first = panelWidth - (int) (metrics.cardW * (1 + metrics.wElderHand));
                        elderHandLocation.second = y + metrics.yMargin + h;
                    }
                    if (metrics.horizontalLayout) {
                        x -= (int)(metrics.xMargin + metrics.cardW);
                    }
                    break;
            }
            label.setBounds(x, y, w, h);
        }

        // menu
        w = (int)(metrics.cardW * metrics.wButton);
        h = (int)(metrics.cardW * metrics.hButton);
        x = panelWidth - w - metrics.xMargin;
        y = panelHeight - h - metrics.yMargin;
        menuBtn.setBounds(x, y, w, h);
        menuBtn.setVisible(true);
        menuBtn.setEnabled(true);

        if (this.roundStage != null) {
            // labels:
            for (int i = 0; i < gameManager.getPlayers().length; ++i) {
                Player player = gameManager.getPlayers()[i];
                Widget label = labels[i];
                String text;

                switch (this.roundStage) {
                    case bidding:
                    case showTalon:
                    case drop:
                    case declareRound:
                    case whistSelection:
                    case selectWhistOption:
                        text = player.getBid().toString();
                        break;
                    default:
                        text = "" + player.getTricks();
                        break;
                }

                label.setText(text);
            }
        }
        placeMenuPanel();
        gui.update();
        sleep(10);  // let GUI thread a chance to repaint
    }

    private void placeMenuPanel() {
        if (!menuPanel.isVisible()) {
            return;
        }
        int panelWidth = config.mainSize.first;
        int panelHeight = config.mainSize.second;

        double wButton = metrics.cardW * menuPanel.getScaleW();
        double hButton = metrics.cardW * menuPanel.getScaleH();

        double aspectRatio = menuPanel.getScaleH() / menuPanel.getScaleW();
        if (wButton * aspectRatio > hButton) {
            wButton = hButton / aspectRatio;
        } else {
            hButton = wButton * aspectRatio;
        }

        int hPanel = (int) (hButton * menuPanel.getRowCount());
        if (hPanel > panelHeight) {
            hPanel = panelHeight;
        }

        int wPanel = (int) (wButton * menuPanel.getColumnCount());

        int x = panelWidth - metrics.xMargin - wPanel;
        int y = panelHeight - metrics.yMargin - hPanel;
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
        if (menuPanel.isVisible()) {
            Widget widget;
            widget = menuPanel.getWidget(1, 0);  // lastTrick, speed vs. convenience
            widget.setEnabled(!gameManager.getLastTrickCards().isEmpty());
            widget = menuPanel.getWidget(2, 0);  // yourOffer, speed vs. convenience
            widget.setEnabled(Bot.trickList != null || Bot.targetBot instanceof MisereBot);

            widget = menuPanel.getWidget(5, 0);  // comments, speed vs. convenience
            widget.setEnabled(host.getLogFileName() != null);
            widget = menuPanel.getWidget(6, 0);  // submit log, speed vs. convenience
            widget.setEnabled(host.getLogFileName() != null);
        }
    }

    private void placeButtonPanel(ButtonPanel buttonPanel) {
        int x, y, space;

        double wButton = metrics.cardW * buttonPanel.getScaleW();
        space = metrics.panelWidth - (int) (2 * metrics.cardW) - 4 * metrics.xMargin;
        if (wButton * buttonPanel.getColumnCount() > space) {
            wButton = (double)space / buttonPanel.getColumnCount();
        }

        double hButton = metrics.cardW * buttonPanel.getScaleH();
        space = metrics.panelHeight -
            2 * (2 * metrics.yMargin + (int) metrics.cardH) - (int) (metrics.cardW * metrics.hLabel);
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
        y = 2 * metrics.yMargin + (int) metrics.cardH + (space - hPanel) / 2;
        buttonPanel.setBounds(x, y, wPanel, hPanel);
        buttonPanel.setEnabled(true);
        buttonPanel.setVisible(true);

        Widget widget;
        switch (buttonPanel.getRoundStage()) {
            case bidding:
                widget = buttonPanel.getWidget(0, 0);  // min bid, speed vs. convenience
                widget.setText(gameManager.getMinBid().toString());
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
                    if (p.getBid().compareTo(Config.Bid.BID_PASS) > 0) {
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
                int player1 = (gameManager.declarerNumber + 1) % NOP;
                int player2 = (gameManager.declarerNumber + 2) % NOP;
                boolean enable = currentPlayer.getNumber() == player2 &&
                    gameManager.getMinBid().goal() < 8 &&
                    gameManager.getPlayers()[player1].getBid().equals(Bid.BID_PASS);
                whistSelectionPanel.getWidget(ButtonCommand.halfWhist).setEnabled(enable);
                whistSelectionPanel.getWidget(ButtonCommand.pass).setEnabled(!enable);
                break;
        }
    }

    public void paint(T graphics) {
        GameManager gameManager = GameManager.getInstance();
        if (metrics.panelWidth == 0 || gameManager == null) {
            return;
        }

        cardPositions.clear();
        currentUserCards.clear();
        for (int i = 0; i < gameManager.getPlayers().length; ++i) {
            paintHand(graphics, gameManager.getPlayers()[i]);
        }
        paintTalon(graphics);
        paintTrick(graphics, gameManager.getTrick().cards2List(), metrics.panelWidth / 2, metrics.panelHeight / 2);
    }

    private void paintTalon(T graphics) {
        GameManager gameManager = GameManager.getInstance();
        CardList talonCards = gameManager.getTalonCards();
        if (talonCards.isEmpty()) {
            return;
        }
        int dx = (int) (metrics.cardW * metrics.xSuitVisible);
        int w = (int) metrics.cardW + dx;    // adjust for single card?
        int x = (metrics.panelWidth - w) / 2;
        int y = metrics.yMargin;

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

    private void paintHand(T graphics, Player player) {
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
            y = metrics.panelHeight - actualH - metrics.yMargin;
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

        int mask = 0;
        for (Suit suit : suits) {
            int bit = 1 << suit.getValue();
            if ((mask & bit) != 0) {
                continue;   // already painted
            }
            mask |= bit;
            int bm = myHand.list(suit).getBitmap();
            bit = 0;
            while ((bit = CardSet.next(bm, bit)) != 0) {
                Card card = Card.get(bit);
                int _x = x;
                int _y = y;
                if (selectedCards.contains(card)) {
                    switch (alignment) {
                        case South:
                            _y -= (int)(metrics.ySelected * metrics.cardW);
                            break;
                        case West:
                            _x += (int)(metrics.xSelected * metrics.cardW);
                            break;
                        case East:
                            _x -= (int)(metrics.xSelected * metrics.cardW);
                            break;
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
            if (showCards) {
                x += (int)(dxSuit - dx);
                y += (int)(dySuit - dy);
            }
        }

    }

    public void paintTrick(T graphics, CardList trickCards, int centerX, int centerY) {
        Point[] positions = {
            new Point(-(int)(metrics.cardW * .5), -(int)(metrics.cardH * .9)),
            new Point(-(int)(metrics.cardW * .5), -(int)(metrics.cardH * .25)),
            new Point(-(int)(metrics.cardW * .75), -(int)(metrics.cardH * .75)),
            new Point(-(int)(metrics.cardW * .25), -(int)(metrics.cardH * .7)),
        };

        if (trickCards.isEmpty()) {
            return;
        }

        int turn = 0;
        for (int j = 0; j < trickCards.size(); ++j) {
            Card card = trickCards.get(j);
            int i = turn + 1;
            if (j == 0 && trickCards.size() > NOP) {
                i = 0;
                --turn;
            }
            int x = positions[i].getX() + centerX;
            int y = positions[i].getY() + centerY;
            Logger.printf(DEBUG_LOG, "trick %s %d\n", card, i);
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
        } else {
//            Logger.printf(DEBUG_LOG, "showCards %s\n", GameManager.getState().getRoundStage());
            if (isStage(RoundStage.play)
                || isStage(RoundStage.waitForBot)
                || isStage(RoundStage.trickTaken)) {
                if (gameManager.declarerNumber != index) {
                    if (gameManager.showDefendersCards()) {
                        return true;
                    }
                }
            }
            return index == 0;
        }
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

    public HumanPlayer getCurrentPlayer() {
        return currentPlayer;
    }

    public void onMouseClick(int x, int y) {
        if (isStage(RoundStage.showTalon)) {
            currentPlayer.accept(BID_XN);   // fake value
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

        if (!currentPlayer.isOK2Play(card)) {
            return;
        }

/* testing, should be confirmed by double click
        if (selectedCards.size() == 0 || !card.equals(selectedCards.get(0))) {
            selectedCards.clear();
            selectedCards.add(card);
            Logger.printf(DEBUG, "clicked, new currentHandVisualData.card %s", card);
            refresh();
            return;
        }
//*/

        // unblock human player
        currentPlayer.accept(card);
        Logger.printf(DEBUG_LOG, "unblocked, selected %s\n", card);
        selectedCards.clear();
        currentPlayer = null;
        // todo: set wait cursor
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
        menuPanel.setVisible(false);
        String text;
        switch (buttonCommand) {
            case menu:
                menuPanel.setVisible(true);
                break;

            // menu panel:
            case showScores:
                gui.showScores(false);
                host.repaint();
                break;
            case lastTrick:
                gui.update();
                gui.showLastTrick(gameManager.getLastTrickCards());
                break;
            case yourOffer:
                getOffer();
                break;
            case replay:
                GameManager.getInstance().restart(GameManager.RestartCommand.replay);
                break;
            case newRound:
                GameManager.getInstance().restart(GameManager.RestartCommand.newRound);
                break;
            case comments:
                String userComments = gui.getUserComments();
                if (!userComments.isEmpty()) {
                    Logger.printf("*** comment start ***\n%s\n*** comment end ***\n", userComments);
                }
                break;
            case submitLog:
                String logFilePath = host.getLogFileName();
                File f = new File(logFilePath);
                String fn = f.getName();
                String res = Util.getInstance().submitLog(logFilePath);
                String msg = res;
                if (res.startsWith(fn)) {
                    msg = m(msg.substring(fn.length() + 1));
                } else {
                    fn = "";
                }
                text = String.format("%s %s", fn, msg);
                gui.showMessage(text);
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
                currentPlayer.drop(new CardSet(selectedCards));
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
            case lying:
                currentPlayer.accept(Bid.BID_WHIST_LAYING);
                break;
            case standing:
                // disabled
                currentPlayer.accept(Bid.BID_WHIST_STANDING);
                break;
        }
        update(null);
    }

    private void showHelp() {
        final String versionVar = "<!-- *** VERSION ***-->";
        final String remoteMark = "<!--*** REMOTE ***-->";
        String version = Config.VERSION + " built " + new SimpleDateFormat("yyyy-MM-dd").format(host.buildDate());
        String src = I18n.loadString("index.html")
            .replace(versionVar, version);
        StringBuilder sb = new StringBuilder();
        int start = 0;
        int end;
        while ((end = src.indexOf(remoteMark, start)) >= 0) {
            sb.append(src, start, end);
            start = src.indexOf(remoteMark, end + 1);
            if (start < 0) {
                start = src.length();
                break;
            }
            start += remoteMark.length();
        }
        sb.append(src, start, src.length());
        gui.showMessage(sb.toString());
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
        int fgColor = getFGColor(suitNum);
        String text = currentBid.getName();
        if (roundValue == 10) {
            text = "" + roundValue + Suit.values()[suitNum - 1].getCode();
        }
        declareRoundPanel.getWidget(ButtonCommand.select).setText(text, fgColor);

        if (suitNum == 1 || roundValue == minRound && suitNum <= minSuit) {
            declareRoundPanel.getWidget(ButtonCommand.prevSuit).setText("");
            declareRoundPanel.getWidget(ButtonCommand.prevSuit).setEnabled(false);
        } else {
            int _suitNum = suitNum - 1;
            char _text = Suit.values()[_suitNum - 1].getCode();
            declareRoundPanel.getWidget(ButtonCommand.prevSuit).setText(_text, getFGColor(_suitNum));
            declareRoundPanel.getWidget(ButtonCommand.prevSuit).setEnabled(true);
        }

        if (suitNum >= 5) {
            declareRoundPanel.getWidget(ButtonCommand.nextSuit).setText("");
        } else if (suitNum == 4) {
            declareRoundPanel.getWidget(ButtonCommand.nextSuit).setText(Config.NO_TRUMP);
        } else {
            int _suitNum = suitNum + 1;
            char _text = Suit.values()[_suitNum - 1].getCode();
            declareRoundPanel.getWidget(ButtonCommand.nextSuit).setText(_text, getFGColor(_suitNum));
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

    private int getFGColor(int suitNum) {
        if (suitNum == 3 || suitNum == 4) {
            return Widget.RED_COLOR;
        }
        return Widget.BLACK_COLOR;
    }

    private void getOffer() {
        int minTricks, maxTricks;
        Player[] players = gameManager.getPlayers();
        Player player0 = players[0];
        int theirTricks = players[1].getTricks() + players[2].getTricks();
        int tricksEstimate;
        if (Bot.trickList == null) {
            if (gameManager.getMinBid().equals(Bid.BID_MISERE)) {
                tricksEstimate = ROUND_SIZE - players[gameManager.declarerNumber].getTricks();
                if (Bot.targetBot instanceof MisereBot) {
                    ((MisereBot)Bot.targetBot).getHoles(gameManager.declarerNumber);
                    tricksEstimate -= ((MisereBot)Bot.targetBot).holes.size();
                }
            } else {
                tricksEstimate = 0;
            }
        } else {
            tricksEstimate = Bot.trickList.getEstimate();
        }

        if (player0.getBid().equals(Bid.BID_MISERE)) {
            minTricks = tricksEstimate;
            maxTricks = ROUND_SIZE - theirTricks;
        } else if (player0.getBid().equals(Bid.BID_WHIST)) {
            int _minTricks = players[0].getTricks();
            if (players[1].getBid() == Bid.BID_PASS) {
                _minTricks += players[1].getTricks();
            } else {
                _minTricks += players[2].getTricks();
            }
            if (gameManager.getMinBid().equals(Bid.BID_MISERE)) {
                minTricks = tricksEstimate;
                maxTricks = ROUND_SIZE - players[gameManager.declarerNumber].getTricks();
            } else {
                minTricks = _minTricks;
                maxTricks = ROUND_SIZE - tricksEstimate;
            }
        } else {
            // for tricks play
            minTricks = Math.max(player0.getTricks(), 0);
            maxTricks = Math.min(ROUND_SIZE - theirTricks, tricksEstimate);
        }
        int acceptedTricks = gui.showOffer(minTricks, maxTricks);
        if (acceptedTricks < 0) {
            return;     // rejected
        }
        int others = ROUND_SIZE - acceptedTricks;
        players[0].setTricks(acceptedTricks);
        if (players[2].getBid() == Bid.BID_PASS) {
            players[1].setTricks(others);
        } else {
            players[2].setTricks(others);
        }
        GameManager.getInstance().restart(GameManager.RestartCommand.offer);
    }

    public interface GUI<T> {
        void update();
        void paint(T graphics, Card card, int x, int y);
        void paintBack(T graphics, int x, int y);
        void add(Widget widget);
        String getUserComments();
        void showMessage(String text);
        void showLastTrick(CardList cards);
        GameManager.RestartCommand showScores(boolean showButtons);
        int showOffer(int minTricks, int maxTricks);
    }

}