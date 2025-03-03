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

import com.ab.jpref.cards.Card;
import com.ab.jpref.config.Config;
import com.ab.jpref.engine.GameManager;
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

        prevSuit("Previous Suit"),
        nextSuit("Next Suit"),
        lesserGame("Lesser Game"),
        greaterGame("Greater Game"),
        select("Select"),

        newGame("New Game"),
        showScores("Scores"),
        replay("Replay"),
        submitLog("Submit Log");

        final String name;

        Command(String name) {
            this.name = name;
        }
    }

    HumanPlayer currentPlayer;
    final ButtonPanel buttonPanel;
    final ButtonPanel firstBidPanel;
    final ButtonPanel bidPanel;
    final ButtonPanel declareGamePanel;
    ButtonPanel menuPanel;

    private int animDelay;
    final MainPanelLayout mainPanelLayout;

    Card selectedCard;

    public MainPanel() {
        this.mainPanelLayout = new MainPanelLayout(this);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                super.mouseDragged(e);
//                System.out.printf("%s -> %s\n", Thread.currentThread().getStackTrace()[1].getMethodName(), e);
            }

        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (!isStage(GameManager.RoundStage.discard) &&
                        !isStage(GameManager.RoundStage.play) &&
                        !isStage(GameManager.RoundStage.newTrick)
                ) {
                   return;
                }
                Logger.printf(DEBUG, "%s -> %s\n", Thread.currentThread().getStackTrace()[1].getMethodName(), e);
                if (e.getButton() == 1) {
                    menuPanel.setVisible(false);
                    // left button
                    panelMouseClicked(e.getPoint());
                }
                if (e.getButton() == 3) {
                    Rectangle bounds = menuPanel.getBounds();
                    bounds.x = e.getX();
                    bounds.y = e.getY();
                    menuPanel.setBounds(bounds);
                    menuPanel.setVisible(true);
                }
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Logger.printf(DEBUG, "MainPanel.%s -> %s\n",
                        Thread.currentThread().getStackTrace()[1].getMethodName(), e);
                update();   // repaint
            }
        });

        declareGamePanel = new ButtonPanel( 1, 1,
            new ButtonHandler[][]{
                {null, new ButtonHandler(Command.greaterGame, command -> showNotImplementedMessage()), null},
                {new ButtonHandler(Command.prevSuit, command -> showNotImplementedMessage()),
                    new ButtonHandler(Command.select, command -> showNotImplementedMessage()),
                    new ButtonHandler(Command.nextSuit, command -> showNotImplementedMessage())
                },
                {null, new ButtonHandler(Command.lesserGame, command -> showNotImplementedMessage()), null}
            });
        this.add(declareGamePanel);

        firstBidPanel = new ButtonPanel(4, 1,
            new ButtonHandler[][]{
                {new ButtonHandler(Command.minBid, command -> returnBid(GameManager.getInstance().getMinBid()))},
                {new ButtonHandler(Command.misere, command -> returnBid(Config.Bid.BID_MISERE))},
                {new ButtonHandler(Command.pass, command -> returnBid(Config.Bid.BID_PASS))}
            });
        this.add(firstBidPanel);

        bidPanel = new ButtonPanel(4, 1,
            new ButtonHandler[][]{
                {new ButtonHandler(Command.minBid, command -> returnBid(GameManager.getInstance().getMinBid()))},
                {new ButtonHandler(Command.pass, command -> returnBid(Config.Bid.BID_PASS))}
            });
        this.add(bidPanel);

        ButtonHandler[][] fullMenu = {
            {new ButtonHandler(Command.newGame, command ->
            {
                menuPanel.setVisible(false);
                GameManager.getInstance().restart(false);
            })},
            {new ButtonHandler(Command.replay, command ->
            {
                menuPanel.setVisible(false);
                GameManager.getInstance().restart(true);
            })},
            {new ButtonHandler(Command.showScores, command -> showNotImplementedMessage())},
            {new ButtonHandler(Command.submitLog, command -> submitLog())},
        };
        ButtonHandler[][] testMenu = new ButtonHandler[fullMenu.length - 1][1];
        System.arraycopy(fullMenu, 1, testMenu, 0, testMenu.length);

        ButtonHandler[][] menuItems = fullMenu;
        if (Main.testFileName != null) {
            menuItems = testMenu;
        }
        menuPanel = new ButtonPanel(3, .5, menuItems);
        this.add(menuPanel);
        menuPanel.setVisible(false);

        buttonPanel = new ButtonPanel(1, 1,
            new ButtonHandler[][]{
                {new ButtonHandler(Command.comment, command -> new NotesPopup(Main.mainFrame)),
                new ButtonHandler(Command.help, command -> showHelp())}
            });
        this.add(buttonPanel);

        update();
//        declareGamePanel.setVisible(false);
    }

    void panelMouseClicked(Point point) {
        if (currentPlayer == null) {
            return;
        }
        Card card = mainPanelLayout.getCard(point);
        if (card == null) {
            return;
        }

        if (!currentPlayer.isOK2Play(card)) {
            return;
        }

/*
        // should be clicked twice
        if (!card.equals(selectedCard)) {
            selectedCard = card;
            Logger.printf(DEBUG, "clicked, new currentHandVisualData.card %s", card);
            update();
            return;
        }
*/

        // unblock human player
        currentPlayer.accept(card);
        Logger.printf(DEBUG, "unblocked, selected %s\n", card);
        selectedCard = null;
        currentPlayer = null;
    }

    private void showNotImplementedMessage() {
        mainPanelLayout.showMessage("Not implemented yet");
    }

    private void returnBid(Config.Bid bid) {
        currentPlayer.accept(bid);
    }

    @Override
    public synchronized void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (g == null) {
            return;
        }
        mainPanelLayout.paintComponent(g);
/*
        if (GameManager.RoundStage.roundEnded.equals(GameManager.getState().getRoundState())) {
            ScorePanel.paint(g2d);
        }
//*/
        if (isStage(GameManager.RoundStage.roundEnded)) {
//            ScorePanel.paint(g2d);
            return;
        }
        BlockingQueue<GameManager.RoundStage> queue = GameManager.getQueue();
        GameManager.RoundStage q = queue.peek();
        if (q != null) {
            q = queue.remove();
            try {
                // put it back, unblock GameManager
                queue.put(q);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    boolean isStage(GameManager.RoundStage stage) {
        GameManager.RoundState state = GameManager.getState();
        if (state == null) {
            return false;
        }
         return stage.equals(state.getRoundStage());
    }

    @Override
    public void update() {
        mainPanelLayout.update();

        bidPanel.setVisible(false);
        firstBidPanel.setVisible(false);
        GameManager gm = GameManager.getInstance();
        GameManager.RoundState r = GameManager.getState();
        if (gm != null && r != null) {
            if (GameManager.RoundStage.bidding.equals(r.getRoundStage())) {
                ButtonPanel bp = bidPanel;
                if (currentPlayer != null && Config.Bid.BID_UNDEFINED.equals(currentPlayer.getBid()) &&
                        GameManager.getInstance().getMinBid().compareTo(Config.Bid.BID_MISERE) < 0) {
                    bp = firstBidPanel;
                }
                PButton button = bp.getButton(Command.minBid);
                Config.Bid minBid = gm.getMinBid();
                button.setText(minBid.getName());
                int color = minBid.getValue() % 10;
                if (color == 3 || color == 4) {
                    button.setForeground(Color.red);
                } else {
                    button.setForeground(Color.black);
                }
                bp.setVisible(true);
            }

//*
//            firstBidPanel.setVisible(GameManager.RoundStage.bidding.equals(r.getRoundStage()));
            declareGamePanel.setVisible(GameManager.RoundStage.declareRound.equals(r.getRoundStage()));
/*/
            bidPanel.setVisible(false);
            declareGamePanel.setVisible(true);
//*/

        }

        Main.mainContainer.validate();
        Main.mainContainer.repaint();
        Logger.printf(DEBUG, "mainPanel.%s -> invalidate()\n", Thread.currentThread().getStackTrace()[1].getMethodName());
        if (isStage(GameManager.RoundStage.roundEnded)) {
            popup();
        }
    }

    boolean popupShown = false;
    private void popup() {
        if (popupShown) {
            return;
        }
        popupShown = true;
        final String[] options3 = {
            "Continue",
            "Replay",
            "New game"};
        final  String[] options2 = new String[2];
        System.arraycopy(options3, 0, options2, 0, options2.length);
        String[] _options = options3;
        if (Main.testFileName != null) {
            _options = options2;
        }
        String[] options = new String[_options.length];
        for (int i = 0; i < options.length; ++i) {
            String option = I18n.m(_options[i]);
            options[i] = option;
        }

        Rectangle bounds = (Rectangle) Main.mainRectangle.clone();
        bounds.width = bounds.width / 2;
        bounds.height = 100;
        bounds.x = (Main.mainRectangle.width - bounds.width) / 2;
        bounds.y = (Main.mainRectangle.height - bounds.height) / 2;
        UIManager.put("OptionPane.minimumSize",new Dimension(bounds.width,bounds.height));
        int n = JOptionPane.showOptionDialog(Main.mainFrame,
            I18n.m("What would you like to do?"),
            I18n.m("Select"),
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);
        popupShown = false;
        Logger.printf(DEBUG, "selected %d\n", n);
        switch (n) {
            case 0:
                try {
                    GameManager.getQueue().put(GameManager.RoundStage.painted);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                break;
            case 1:
                GameManager.getInstance().restart(true);
                break;
            case 2:
                GameManager.getInstance().restart(false);
                break;
        }
    }

/*
    @Override
    public void mouseDragged(MouseEvent event) {
        System.out.printf("JPanel %s -> %s\n", Thread.currentThread().getStackTrace()[1].getMethodName(), event);
    }

    @Override
    public void mouseMoved(MouseEvent event) {
        System.out.printf("JPanel %s -> %s\n", Thread.currentThread().getStackTrace()[1].getMethodName(), event);
    }
*/

    @Override
    public void setSelectedPlayer(HumanPlayer humanPlayer) {
        this.currentPlayer = humanPlayer;
        selectedCard = null;
        update();
    }

    private void submitLog() {
        String res = Util.submitLog(Logger.getLogFileName());
        mainPanelLayout.showMessage(res);
    }

    private boolean helpShown;

    void showHelp() {
        if (helpShown) {
            return;
        }
        helpShown = true;
        Rectangle bounds = (Rectangle) Main.mainRectangle.clone();
//        bounds.width = bounds.width / 2;
//        bounds.height = bounds.height / 2;
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
