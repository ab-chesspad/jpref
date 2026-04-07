/*  This file is part of JPref.
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
 * Created: 1/15/2025
 */
package com.ab.pref;

import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.jpref.config.Config.Bid;
import static com.ab.jpref.config.Config.NOP;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.Card.Suit;

import com.ab.jpref.cards.CardList;
import com.ab.jpref.engine.Bot;
import com.ab.jpref.engine.GameManager;
import com.ab.jpref.engine.GameManager.RoundStage;

import com.ab.jpref.engine.MisereBot;
import com.ab.jpref.engine.Player;
import com.ab.pref.config.Metrics;
import com.ab.pref.config.PConfig;
import static com.ab.pref.config.PConfig.Host;
import static com.ab.pref.config.PConfig.Host.SPECIAL_OPTION_SHOW_CARDS;
import static com.ab.pref.config.PConfig.Host.SPECIAL_OPTION_MANUAL;
import com.ab.pref.config.SettingsPopup;
import com.ab.pref.widgets.ButtonPanel;
import com.ab.pref.widgets.PButton;
import com.ab.util.Logger;
import static com.ab.jpref.config.I18n.m;
import static com.ab.util.Util.currMethodName;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class MainPanel extends JPanel implements GameManager.EventObserver {
    public static final boolean DEBUG_LOG = false;

    public enum Alignment {
        South,
        West,
        East,
    }

    public enum ButtonCommand {
        settings("Settings"),
        comment("Comment"),
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
        newGame("New Game"),
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

    private final PUtil pUtil = PUtil.getInstance();
    final Host host;
    final MainPanelLayout mainPanelLayout;
    final CardList selectedCards = new CardList();

    ButtonPanel buttonPanel;
    ButtonPanel bidPanel;
    ButtonPanel dropPanel;
    ButtonPanel declareRoundPanel;
    ButtonPanel menuPanel;
    ButtonPanel whistSelectionPanel;
    ButtonPanel whistOptionPanel;
    final JPanel trickPanel;

    GameManager gameManager;
    RoundStage roundStage;
    boolean reportReady;
    HumanPlayer currentPlayer;
    Bid currentBid;

    private int animDelay;

    public MainPanel(Host host) {
        this.host = host;
        this.mainPanelLayout = new MainPanelLayout(this);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // for the future, play by dragging
                super.mouseDragged(e);
//                System.out.printf("%s -> %s\n", currMethodName(), e);
            }

        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                menuPanel.setVisible(false);
                if (isStage(RoundStage.showTalon)) {
                    gameManager.unblockGameManager(RoundStage.showTalon);
                    return;
                }
                if (trickPanel.isVisible()) {
                    trickPanel.setVisible(false);
                    update();
                }
                if (e.getButton() == 3) {
                    Rectangle bounds = menuPanel.getBounds();
                    bounds.x = e.getX();
                    if (bounds.x + bounds.width > mainRectangle().width) {
                        bounds.x = mainRectangle().width - bounds.width;
                    }
                    bounds.y = e.getY();
                    if (bounds.y + bounds.height > mainRectangle().height) {
                        bounds.y = mainRectangle().height - bounds.height;
                    }
                    menuPanel.setBounds(bounds);
                    menuPanel.setVisible(true);
                    PButton b = menuPanel.getButton(ButtonCommand.lastTrick);
                    b.setEnabled(!gameManager.getLastTrickCards().isEmpty());
                    b = menuPanel.getButton(ButtonCommand.yourOffer);
                    boolean enable = Bot.trickList != null || Bot.targetBot instanceof MisereBot;
                    b.setEnabled(enable);
                    b = menuPanel.getButton(ButtonCommand.newGame);
                    b.setEnabled(host.release());
                    b = menuPanel.getButton(ButtonCommand.submitLog);
                    b.setEnabled(host.getLogFileName() != null);
                }
                if (!isStage(RoundStage.drop) &&
                    !isStage(RoundStage.play) &&
                    !isStage(RoundStage.trickTaken) &&
                    !isStage(RoundStage.newTrick)
                ) {
                    return;
                }
                Logger.printf(DEBUG_LOG, "%s -> %s\n", currMethodName(), e);
                if (e.getButton() == 1) {
                    menuPanel.setVisible(false);
                    // left button
                    MainPanel.this.mouseClicked(e.getPoint());
                }
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Logger.printf(DEBUG_LOG, "MainPanel.%s -> %s\n",
                    currMethodName(), e);
                update();   // repaint
            }
        });

        createButtonPanels();

        trickPanel = createTrickPanel();
        add(trickPanel);
        trickPanel.setVisible(false);

        update();
    }

    Rectangle mainRectangle() {
        return PConfig.getInstance().mainRectangle.get();
    }

    private JPanel createTrickPanel() {
        return new JPanel() {
            @Override
            public Dimension getSize() {
                return new Dimension((int) (2 * Metrics.getInstance().cardW),
                    (int) (2 * Metrics.getInstance().cardH));
            }

            @Override
            public Dimension getPreferredSize() {
                return getSize();
            }

            @Override
            public Dimension getMinimumSize() {
                return getSize();
            }

            @Override
            public Dimension getMaximumSize() {
                return getSize();
            }

            @Override
            public Rectangle getBounds() {
                Rectangle r = (Rectangle) super.getBounds().clone();
                Dimension d = getSize();
                r.width = d.width;
                r.height = d.height;
                return r;
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                mainPanelLayout.paintTrick((Graphics2D) g,
                    gameManager.getLastTrickCards());
            }
        };
    }

    void mouseClicked(Point point) {
        if (currentPlayer == null) {
            return;
        }
        Card card = mainPanelLayout.getCard(point);
        if (card == null) {
            return;
        }

        if (GameManager.getState().getRoundStage().equals(RoundStage.drop)) {
            if (selectedCards.contains(card)) {
                selectedCards.remove(card);
            } else if (selectedCards.size() < 2) {
                selectedCards.add(card);
            }
            update();
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
    }

    private void returnBid(Bid bid) {
        currentPlayer.accept(bid);
    }

    @Override
    public synchronized void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (g == null || mainPanelLayout == null || gameManager == null) {
            return;
        }
        mainPanelLayout.paintComponent(g);
        if (reportReady) {
            reportReady = false;
            if (isStage(RoundStage.showTalon)) {
                return;     // wait for mouse click
            }
            host.mainFrame().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            gameManager.unblockGameManager(this.roundStage);
        }
    }

    boolean isStage(RoundStage stage) {
        if (stage == null) {
            return false;
        }
        return stage.equals(this.roundStage);
    }

    boolean showCards(int index) {
        if (gameManager.replayMode || (host.specialOption() & SPECIAL_OPTION_SHOW_CARDS) != 0) {
            return true;
        } else {
            Logger.printf(DEBUG_LOG, "showCards %s\n", GameManager.getState().getRoundStage());
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
    public void update(RoundStage roundStage) {
        this.roundStage = roundStage;
        reportReady = true;
        update();
    }

    private void update() {
        gameManager = GameManager.getInstance();
        host.mainFrame().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        mainPanelLayout.update();

        bidPanel.setVisible(false);
        whistSelectionPanel.setVisible(false);
        whistOptionPanel.setVisible(false);
        if (currentPlayer != null && gameManager != null) {
            if (isStage(RoundStage.bidding)) {
                bidPanel.getButton(ButtonCommand.misere).setEnabled(false);
                if (Bid.BID_UNDEFINED.equals(currentPlayer.getBid()) &&
                        gameManager.getMinBid().compareTo(Bid.BID_MISERE) < 0) {
                    bidPanel.getButton(ButtonCommand.misere).setEnabled(true);
                }
                PButton button = bidPanel.getButton(ButtonCommand.minBid);
                Bid minBid = gameManager.getMinBid();
                button.setText(minBid.getName());
                int color = minBid.getValue() % 10;
                if (color == 3 || color == 4) {
                    button.setForeground(Color.red);
                } else {
                    button.setForeground(Color.black);
                }
                button = bidPanel.getButton(ButtonCommand.pass);
                String text = "Pass";
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
                button.setText(text);
                bidPanel.setVisible(true);
            } else if (isStage(RoundStage.declareRound)) {
                setDeclareRoundPanel(null);
            } else if (isStage(RoundStage.responseOnDeclaration)) {
                setOnDeclarationPanel();
                whistSelectionPanel.setVisible(true);
            }
            setTitle();
            whistOptionPanel.setVisible(isStage(RoundStage.selectWhistOption));
            declareRoundPanel.setVisible(isStage(RoundStage.declareRound));
            dropPanel.setVisible(isStage(RoundStage.drop));
            dropPanel.getButton(ButtonCommand.drop).setEnabled(selectedCards.size() == 2);
            dropPanel.getButton(ButtonCommand.without3)
                .setEnabled(currentPlayer != null && !Bid.BID_MISERE.equals(currentPlayer.getBid()));
        }

        host.repaint();
        if (GameManager.getState() != null) {
            Logger.printf(DEBUG_LOG, "mainPanel.%s, %s -> invalidate()\n",
                currMethodName(),
                GameManager.getState().getRoundStage());
        }
        if (isStage(RoundStage.roundEnded) || isStage(RoundStage.offer)) {
            new StatusPopup(host, true);
        }
    }

    private void setTitle() {
        String title = Config.PROJECT_NAME;
        if (gameManager.declarerNumber >= 0) {
            title += " - " + m(gameManager.getMinBid());
        } else if (gameManager.getMinBid().equals(Bid.BID_ALL_PASS)) {
            title += " " + Bid.BID_ALL_PASS + " *" + (gameManager.getAllPassFactor() + 1);
        }
        if ((host.specialOption() & SPECIAL_OPTION_MANUAL) != 0) {
            title += ", All open";
        }
        host.mainFrame().setTitle(title);
    }

    // select whist/pass/half-whist
    private void setOnDeclarationPanel() {
        int player1 = (gameManager.declarerNumber + 1) % NOP;
        int player2 = (gameManager.declarerNumber + 2) % NOP;
        boolean enable = currentPlayer.getNumber() == player2 &&
            gameManager.getMinBid().goal() < 8 &&
            gameManager.getPlayers()[player1].getBid().equals(Bid.BID_PASS);
        whistSelectionPanel.getButton(ButtonCommand.halfWhist).setEnabled(enable);
        whistSelectionPanel.getButton(ButtonCommand.pass).setEnabled(!enable);
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
        int suitValue = currentBid.getValue() % 10;
        Logger.printf(DEBUG_LOG, "setDeclareRoundPanel curr %s, %d, %d\n", currentBid, roundValue, suitValue);

        switch (buttonCommand) {
            case prevSuit:
                --suitValue;
                break;
            case nextSuit:
                ++suitValue;
                break;
            case lesserGame:
                --roundValue;
                break;
            case greaterGame:
                ++roundValue;
                break;
        }

        currentBid = Bid.fromValue(roundValue * 10 + suitValue);
        Logger.printf(DEBUG_LOG, "setDeclareRoundPanel next %s, %d, %d\n", currentBid, roundValue, suitValue);
        Color fgColor = Color.black;
        if (suitValue == 3 || suitValue == 4) {
            fgColor = Color.red;
        }
        declareRoundPanel.getButton(ButtonCommand.select).setText(currentBid.getName(), fgColor);

        if (suitValue <= 1 || roundValue == minRound && suitValue <= minSuit) {
            declareRoundPanel.getButton(ButtonCommand.prevSuit).setText("");
            declareRoundPanel.getButton(ButtonCommand.prevSuit).setEnabled(false);
        } else {
            declareRoundPanel.getButton(ButtonCommand.prevSuit).setText(Suit.values()[suitValue - 2].getCode());
            declareRoundPanel.getButton(ButtonCommand.prevSuit).setEnabled(true);
        }

        if (roundValue <= minRound || roundValue == minRound + 1 && suitValue < minSuit) {
            declareRoundPanel.getButton(ButtonCommand.lesserGame).setText("");
            declareRoundPanel.getButton(ButtonCommand.lesserGame).setEnabled(false);
        } else {
            declareRoundPanel.getButton(ButtonCommand.lesserGame).setText(roundValue - 1, fgColor);
            declareRoundPanel.getButton(ButtonCommand.lesserGame).setEnabled(true);
        }

        if (suitValue >= 5) {
            declareRoundPanel.getButton(ButtonCommand.nextSuit).setText("");
        } else if (suitValue == 4) {
            declareRoundPanel.getButton(ButtonCommand.nextSuit).setText(Config.NO_TRUMP);
        } else {
            declareRoundPanel.getButton(ButtonCommand.nextSuit).setText(Suit.values()[suitValue].getCode());
        }
        declareRoundPanel.getButton(ButtonCommand.nextSuit).setEnabled(suitValue < 5);

        if (roundValue >= 10) {
            declareRoundPanel.getButton(ButtonCommand.greaterGame).setText("");
        } else {
            declareRoundPanel.getButton(ButtonCommand.greaterGame).setText(roundValue + 1, fgColor);
        }
        declareRoundPanel.getButton(ButtonCommand.greaterGame).setEnabled(roundValue < 10);

        host.repaint();
    }

    @Override
    public void setSelectedPlayer(Player player) {
        if (player instanceof HumanPlayer) {
            this.currentPlayer = (HumanPlayer) player;
        } else {
            this.currentPlayer = null;
        }
        selectedCards.clear();
        update();
    }

    private void submitLog() {
        String res = pUtil.submitLog(host.getLogFileName());
        String text = String.format(m("The file %s has been uploaded"), res);
        mainPanelLayout.showMessage(text);
    }

    private void createButtonPanels() {
        dropPanel = new ButtonPanel( 4, 1,
            new PButton.ButtonHandler[][] {
                {new PButton.ButtonHandler(ButtonCommand.drop, buttonCommand -> currentPlayer.drop(new CardSet(selectedCards)))},
                {new PButton.ButtonHandler(ButtonCommand.without3, buttonCommand -> returnBid(Bid.BID_WITHOUT_THREE))},
            });
        dropPanel.setVisible(false);
        this.add(dropPanel);

        declareRoundPanel = new ButtonPanel( 1.5, 1.5,
            new PButton.ButtonHandler[][] {
                {null, new PButton.ButtonHandler(ButtonCommand.greaterGame, buttonCommand -> setDeclareRoundPanel(buttonCommand)), null},
                {new PButton.ButtonHandler(ButtonCommand.prevSuit, buttonCommand -> setDeclareRoundPanel(buttonCommand)),
                    new PButton.ButtonHandler(ButtonCommand.select, buttonCommand -> returnBid(currentBid)),
                    new PButton.ButtonHandler(ButtonCommand.nextSuit, buttonCommand -> setDeclareRoundPanel(buttonCommand))
                },
                {null, new PButton.ButtonHandler(ButtonCommand.lesserGame, buttonCommand -> setDeclareRoundPanel(buttonCommand)), null}
            });
        declareRoundPanel.setVisible(false);
        this.add(declareRoundPanel);

        bidPanel = new ButtonPanel(4, 1,
            new PButton.ButtonHandler[][] {
                {new PButton.ButtonHandler(ButtonCommand.minBid, buttonCommand -> returnBid(gameManager.getMinBid()))},
                {new PButton.ButtonHandler(ButtonCommand.misere, buttonCommand -> returnBid(Bid.BID_MISERE))},
                {new PButton.ButtonHandler(ButtonCommand.pass, buttonCommand -> returnBid(Bid.BID_PASS))}
            });
        bidPanel.setVisible(false);
        this.add(bidPanel);

        double scale = 0.9;
        buttonPanel = new ButtonPanel(scale, scale,
            new PButton.ButtonHandler[][] {{
                new PButton.ButtonHandler(ButtonCommand.settings, buttonCommand -> new SettingsPopup(host)),
                new PButton.ButtonHandler(ButtonCommand.comment, buttonCommand -> new NotesPopup(host)),
                new PButton.ButtonHandler(ButtonCommand.help, buttonCommand -> new HelpPopup(host))
            }});
        buttonPanel.setVisible(true);
        this.add(buttonPanel);

        menuPanel = new ButtonPanel(3.5, .6,
            new PButton.ButtonHandler[][] {
                {new PButton.ButtonHandler(ButtonCommand.showScores, buttonCommand -> {
                    menuPanel.setVisible(false);
                    new StatusPopup(host, false);
                })},
                {new PButton.ButtonHandler(ButtonCommand.lastTrick, buttonCommand -> {
                    menuPanel.setVisible(false);
                    trickPanel.setVisible(true);
                    update();
                })},
                {new PButton.ButtonHandler(ButtonCommand.yourOffer, buttonCommand -> {
                    menuPanel.setVisible(false);
                    new OfferPopup(host);
                })},
                {new PButton.ButtonHandler(ButtonCommand.replay, buttonCommand -> {
                    menuPanel.setVisible(false);
                    for (Player player : gameManager.getPlayers()) {
                        player.abortThread(GameManager.RestartCommand.replay);
                    }
                })},
                {new PButton.ButtonHandler(ButtonCommand.submitLog, buttonCommand -> {
                    menuPanel.setVisible(false);
                    submitLog();
                })},
                {new PButton.ButtonHandler(ButtonCommand.newGame, buttonCommand -> {
                    menuPanel.setVisible(false);
                    for (Player player : gameManager.getPlayers()) {
                        player.abortThread(GameManager.RestartCommand.newGame);
                    }
                })},
            });
        menuPanel.setVisible(false);
        this.add(menuPanel);

        whistSelectionPanel = new ButtonPanel(4, 1,
            new PButton.ButtonHandler[][] {
                {new PButton.ButtonHandler(ButtonCommand.whist, buttonCommand -> returnBid(Bid.BID_WHIST))},
                {new PButton.ButtonHandler(ButtonCommand.halfWhist, buttonCommand -> returnBid(Bid.BID_HALF_WHIST))},
                {new PButton.ButtonHandler(ButtonCommand.pass, buttonCommand -> returnBid(Bid.BID_PASS))}
            });
        whistSelectionPanel.setVisible(false);
        this.add(whistSelectionPanel);

        whistOptionPanel = new ButtonPanel(4, 1,
            new PButton.ButtonHandler[][] {
                {new PButton.ButtonHandler(ButtonCommand.lying, buttonCommand -> returnBid(Bid.BID_WHIST_LAYING))},
                {new PButton.ButtonHandler(ButtonCommand.standing, buttonCommand -> returnBid(Bid.BID_WHIST_STANDING))}
            });
        whistOptionPanel.setVisible(false);
        this.add(whistOptionPanel);

        // todo: remove to allow play whist standing
        whistOptionPanel.getButton(ButtonCommand.standing).setEnabled(false);
    }

}