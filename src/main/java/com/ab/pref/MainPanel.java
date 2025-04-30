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
 * Copyright 2025 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 1/15/25
 */
package com.ab.pref;

import com.ab.jpref.config.Config;
import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.engine.GameManager;
import com.ab.jpref.engine.Player;
import com.ab.jpref.engine.Bot;
import com.ab.util.I18n;
import com.ab.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.BlockingQueue;

public class MainPanel extends JPanel implements GameManager.EventObserver, HumanPlayer.Clickable {
    public static final boolean DEBUG = false;

    public enum Alignment {
        South,
        West,
        East,
    }

    public enum Command {
        comment("Comment"),
        help("Help"),

        minBid("Min Bid"),
        misere("Misère"),
        pass("Pass"),
        discard("Discard"),
        without3("Without 3"),
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

        recordAndClose("Record and close"),

        ok("OK"),
        ;

        final String name;

        Command(String name) {
            this.name = name;
        }
    }

    HumanPlayer currentPlayer;
    Config.Bid currentBid;
    ButtonPanel buttonPanel;
    ButtonPanel bidPanel;
    ButtonPanel discardPanel;
    ButtonPanel declareRoundPanel;
    ButtonPanel menuPanel;
    final JPanel trickPanel;

    private int animDelay;
    final MainPanelLayout mainPanelLayout;

    final CardList selectedCards = new CardList();

    public MainPanel() {
        this.mainPanelLayout = new MainPanelLayout(this);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                super.mouseDragged(e);
//                System.out.printf("%s -> %s\n", com.ab.util.Util.currMethodName(), e);
            }

        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (trickPanel.isVisible()) {
                    trickPanel.setVisible(false);
                    update();
                }
                if (!isStage(GameManager.RoundStage.discard) &&
                        !isStage(GameManager.RoundStage.play) &&
                        !isStage(GameManager.RoundStage.newTrick)
                ) {
                   return;
                }
                Logger.printf(DEBUG, "%s -> %s\n", com.ab.util.Util.currMethodName(), e);
                if (e.getButton() == 1) {
                    menuPanel.setVisible(false);
                    // left button
                    MainPanel.this.mouseClicked(e.getPoint());
                }
                if (e.getButton() == 3) {
                    Rectangle bounds = menuPanel.getBounds();
                    bounds.x = e.getX();
                    bounds.y = e.getY();
                    menuPanel.setBounds(bounds);
                    menuPanel.setVisible(true);
                    PButton b = menuPanel.getButton(Command.lastTrick);
                    b.setEnabled(!GameManager.getInstance().getLastTrick().getTrickCards().isEmpty());
                    b = menuPanel.getButton(Command.newGame);
                    b.setEnabled(Main.testFileName == null);
                    b = menuPanel.getButton(Command.submitLog);
                    b.setEnabled(Logger.isToFile());
                }
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Logger.printf(DEBUG, "MainPanel.%s -> %s\n",
                    com.ab.util.Util.currMethodName(), e);
                update();   // repaint
            }
        });

        createButtonPanels();

        trickPanel = createTrickPanel();
        add(trickPanel);
        trickPanel.setVisible(false);

        update();
    }

    private JPanel createTrickPanel() {
        return new JPanel() {
            @Override
            public Dimension getSize() {
                return new Dimension((int)(2 * Metrics.getInstance().cardW),
                    (int)(2 * Metrics.getInstance().cardH));
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
                    GameManager.getInstance().getLastTrick());
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

        if (GameManager.getState().getRoundStage().equals(GameManager.RoundStage.discard)) {
            if (selectedCards.contains(card)) {
                selectedCards.remove(card);
            } else if (selectedCards.size() < 2){
                selectedCards.add(card);
            }
            update();
            return;
        }

        if (!currentPlayer.isOK2Play(card)) {
            return;
        }

/* testing, should be confirmed by clicking twice
        if (selectedCards.size() == 0 || !card.equals(selectedCards.get(0))) {
            selectedCards.clear();
            selectedCards.add(card);
            Logger.printf(DEBUG, "clicked, new currentHandVisualData.card %s", card);
            update();
            return;
        }
//*/

        // unblock human player
        currentPlayer.accept(card);
        Logger.printf(DEBUG, "unblocked, selected %s\n", card);
        selectedCards.clear();
        currentPlayer = null;
    }

    private void showNotImplementedMessage() {
        mainPanelLayout.showMessage(I18n.m("Not implemented yet"));
    }

    private void returnBid(Config.Bid bid) {
        currentPlayer.accept(bid);
    }

    private void unblockGameManager() {
        BlockingQueue<GameManager.RoundStage> queue = GameManager.getQueue();
        GameManager.RoundStage q = queue.peek();
        if (q != null) {
            q = queue.remove();
            try {
                Logger.printf(DEBUG, "paintComponent, %s unblock\n", q.toString());
                // put it back, unblock GameManager
                queue.put(q);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public synchronized void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (g == null) {
            return;
        }
        mainPanelLayout.paintComponent(g);
        unblockGameManager();
    }

    boolean isStage(GameManager.RoundStage stage) {
        GameManager.RoundState state = GameManager.getState();
        if (state == null) {
            return false;
        }
         return stage.equals(state.getRoundStage());
    }

    boolean showCards(int index) {
        if (Main.SHOW_ALL) {
            return true;
        } else {
            Logger.printf(DEBUG, "showCards %s\n", GameManager.getState().getRoundStage().toString());
            // depending on the round!
            if (isStage(GameManager.RoundStage.roundDeclared)) {
                if (GameManager.getInstance().getDeclarer() instanceof HumanPlayer &&
                    Config.Bid.BID_MISERE.equals(GameManager.getInstance().getDeclarer().getBid())) {
                    return true;
                }
            }
            return index == 0;
        }
    }

    @Override
    public void update() {
        mainPanelLayout.update();

        bidPanel.setVisible(false);
        GameManager gm = GameManager.getInstance();
        GameManager.RoundState r = GameManager.getState();
        if (currentPlayer != null && gm != null && r != null) {
            if (GameManager.RoundStage.bidding.equals(r.getRoundStage())) {
                bidPanel.getButton(Command.misere).setEnabled(false);
                if (Config.Bid.BID_UNDEFINED.equals(currentPlayer.getBid()) &&
                    GameManager.getInstance().getMinBid().compareTo(Config.Bid.BID_MISERE) < 0) {
                    bidPanel.getButton(Command.misere).setEnabled(true);
                }
                PButton button = bidPanel.getButton(Command.minBid);
                Config.Bid minBid = gm.getMinBid();
                button.setText(minBid.getName());
                int color = minBid.getValue() % 10;
                if (color == 3 || color == 4) {
                    button.setForeground(Color.red);
                } else {
                    button.setForeground(Color.black);
                }
                bidPanel.setVisible(true);
            } else if (GameManager.RoundStage.declareRound.equals(r.getRoundStage())) {
                setDeclareRoundPanel(null);
            }
            declareRoundPanel.setVisible(GameManager.RoundStage.declareRound.equals(r.getRoundStage()));
            discardPanel.setVisible(GameManager.RoundStage.discard.equals(r.getRoundStage()));
            discardPanel.getButton(Command.discard).setEnabled(selectedCards.size() == 2);
            discardPanel.getButton(Command.without3)
                .setEnabled(!Config.Bid.BID_MISERE.equals(currentPlayer.getBid()));
        }

        Main.mainContainer.validate();
        Main.mainContainer.repaint();
        if (GameManager.getState() != null) {
            Logger.printf(DEBUG, "mainPanel.%s, %s -> invalidate()\n",
                com.ab.util.Util.currMethodName(),
                GameManager.getState().getRoundStage());
        }
        if (isStage(GameManager.RoundStage.roundEnded)) {
            new StatusPopup(Main.mainFrame, true);
        }
    }

    private void setDeclareRoundPanel(Command command) {
        if (command == null) {
            if (currentBid == null) {
                currentBid = currentPlayer.getBid();
            }
            command = Command.ok;
        }
        int minRound = currentPlayer.getBid().getValue() / 10;
        int minSuit = currentPlayer.getBid().getValue() % 10;
        int roundValue = currentBid.getValue() / 10;
        int suitValue = currentBid.getValue() % 10;
        Logger.printf(DEBUG, "setDeclareRoundPanel curr %s, %d, %d\n", currentBid, roundValue, suitValue);

        switch (command) {
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

        currentBid = Config.Bid.fromValue(roundValue * 10 + suitValue);
        Logger.printf(DEBUG, "setDeclareRoundPanel next %s, %d, %d\n", currentBid, roundValue, suitValue);
        declareRoundPanel.getButton(Command.select).setText(currentBid.getName());

        if (suitValue <= 1 || roundValue == minRound && suitValue <= minSuit) {
            declareRoundPanel.getButton(Command.prevSuit).setText("");
            declareRoundPanel.getButton(Command.prevSuit).setEnabled(false);
        } else {
            declareRoundPanel.getButton(Command.prevSuit).setText("" + Card.Suit.values()[suitValue - 2].getCode());
            declareRoundPanel.getButton(Command.prevSuit).setEnabled(true);
        }

        if (roundValue <= minRound || roundValue == minRound + 1 && suitValue < minSuit) {
            declareRoundPanel.getButton(Command.lesserGame).setText("");
            declareRoundPanel.getButton(Command.lesserGame).setEnabled(false);
        } else {
            declareRoundPanel.getButton(Command.lesserGame).setText("" + (roundValue - 1));
            declareRoundPanel.getButton(Command.lesserGame).setEnabled(true);
        }

        if (suitValue >= 5) {
            declareRoundPanel.getButton(Command.nextSuit).setText("");
        } else {
            declareRoundPanel.getButton(Command.nextSuit).setText("" + Card.Suit.values()[suitValue].getCode());
        }
        declareRoundPanel.getButton(Command.nextSuit).setEnabled(suitValue < 5);

        if (roundValue >= 10) {
            declareRoundPanel.getButton(Command.greaterGame).setText("");
        } else {
            declareRoundPanel.getButton(Command.greaterGame).setText("" + (roundValue + 1));
        }
        declareRoundPanel.getButton(Command.greaterGame).setEnabled(roundValue < 10);
    }

    @Override
    public void setSelectedPlayer(HumanPlayer humanPlayer) {
        this.currentPlayer = humanPlayer;
        selectedCards.clear();
        update();
    }

    private void submitLog() {
        String res = Util.submitLog(Logger.getLogFileName());
        String text = String.format(I18n.m("The file %s has been uploaded"), res);
        mainPanelLayout.showMessage(text);
    }

    private boolean helpShown;
    void showHelp() {
        if (helpShown) {
            return;
        }
        helpShown = true;
        Rectangle bounds = (Rectangle) Main.mainRectangle.clone();
        bounds.x = (Main.mainRectangle.width - bounds.width) / 2;
        bounds.y = (Main.mainRectangle.height - bounds.height) / 2;

        String text = I18n.loadString("index.html");
        UIManager.put("OptionPane.minimumSize",new Dimension(bounds.width,bounds.height));
        JLabel label = new HtmlLabel(text);
        label.setFont(new Font("Arial", Font.PLAIN, 20));
        JScrollPane jScrollPane = new JScrollPane(label);
        jScrollPane.setAutoscrolls(true);
        jScrollPane.setPreferredSize(new Dimension( 800,10));

        JOptionPane.showMessageDialog(null, jScrollPane,"About JPref v.0.0.0.1", JOptionPane.PLAIN_MESSAGE);
        helpShown = false;
    }

    private void createButtonPanels() {
        discardPanel = new ButtonPanel( 4, 1,
            new ButtonHandler[][] {
                {new ButtonHandler(Command.discard, command -> currentPlayer.discard(selectedCards))},
                {new ButtonHandler(Command.without3, command -> returnBid(Config.Bid.BID_WITHOUT_THREE))},
            });
        discardPanel.setVisible(false);
        this.add(discardPanel);

        declareRoundPanel = new ButtonPanel( 1.5, 1.5,
            new ButtonHandler[][] {
                {null, new ButtonHandler(Command.greaterGame, command -> setDeclareRoundPanel(command)), null},
                {new ButtonHandler(Command.prevSuit, command -> setDeclareRoundPanel(command)),
                    new ButtonHandler(Command.select, command -> returnBid(currentBid)),
                    new ButtonHandler(Command.nextSuit, command -> setDeclareRoundPanel(command))
                },
                {null, new ButtonHandler(Command.lesserGame, command -> setDeclareRoundPanel(command)), null}
            });
        declareRoundPanel.setVisible(false);
        this.add(declareRoundPanel);

        bidPanel = new ButtonPanel(4, 1,
            new ButtonHandler[][] {
                {new ButtonHandler(Command.minBid, command -> returnBid(GameManager.getInstance().getMinBid()))},
                {new ButtonHandler(Command.misere, command -> returnBid(Config.Bid.BID_MISERE))},
                {new ButtonHandler(Command.pass, command -> returnBid(Config.Bid.BID_PASS))}
            });
        bidPanel.setVisible(false);
        this.add(bidPanel);

        buttonPanel = new ButtonPanel(1, 1,
            new ButtonHandler[][] {
                {new ButtonHandler(Command.comment, command -> new NotesPopup(Main.mainFrame)),
                    new ButtonHandler(Command.help, command -> showHelp())}
            });
        buttonPanel.setVisible(true);
        this.add(buttonPanel);

        menuPanel = new ButtonPanel(3.5, .5,
            new ButtonHandler[][] {
                {new ButtonHandler(Command.showScores, command -> {
                    menuPanel.setVisible(false);
                    new StatusPopup(Main.mainFrame, false);
                })},
                {new ButtonHandler(Command.lastTrick, command -> {
                    menuPanel.setVisible(false);
                    trickPanel.setVisible(true);
                    update();
                })},
                {new ButtonHandler(Command.replay, command -> {
                    menuPanel.setVisible(false);
                    for (Player player : GameManager.getInstance().getPlayers()) {
                        player.abortThread(GameManager.RestartCommand.replay);
                    }
                })},
                {new ButtonHandler(Command.submitLog, command -> {
                    menuPanel.setVisible(false);
                    submitLog();
                })},
                {new ButtonHandler(Command.newGame, command -> {
                    menuPanel.setVisible(false);
                    for (Player player : GameManager.getInstance().getPlayers()) {
                        player.abortThread(GameManager.RestartCommand.newGame);
                    }
                })},
            });
        menuPanel.setVisible(false);
        this.add(menuPanel);
    }

    public interface ButtonListener {
        void onClick(MainPanel.Command command);
    }

    public static class ButtonHandler {
        final MainPanel.Command command;
        final ButtonListener buttonListener;

        public ButtonHandler(MainPanel.Command command, ButtonListener buttonListener) {
            this.command = command;
            this.buttonListener = buttonListener;
        }
    }
}
