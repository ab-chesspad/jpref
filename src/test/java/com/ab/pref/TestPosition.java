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
 * Created: 1/26/2026.
 */
package com.ab.pref;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardSet;
import static com.ab.jpref.config.Config.Bid;
import static com.ab.jpref.config.Config.ROUND_SIZE;
import static com.ab.jpref.config.Config.NOP;
import com.ab.jpref.engine.*;
import com.ab.jpref.engine.GameManager.PlayerFactory;
import com.ab.pref.config.PConfig.Host;
import com.ab.pref.config.Metrics;
import com.ab.util.BidData;
import com.ab.util.Logger;
import com.ab.pref.config.PConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Date;

// convenience GUI program to test intermediate positions for TrickList search
// add/modify sources using hands from either a breakpoint in buildSubList(CardList cards)
// or log
public class TestPosition implements Host {
    public static final boolean[] BOTS = new boolean[NOP];
    static {
        BOTS[0] = false;
        BOTS[1] = false;
        BOTS[2] = false;
    }
    final PConfig pConfig = PConfig.getInstance();
    final PUtil pUtil = PUtil.getInstance();
    final TestGameManager gameManager;
    final CardSet[] hands = new CardSet[NOP];
    final Container mainContainer;
    final JFrame mainFrame;
    final MainPanel mainPanel;
    Rectangle mainRectangle;

    ForTricksBot forTricksBot;

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(TestPosition::new);
    }

    public TestPosition() {
        mainFrame = new JFrame();
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Insets insets = mainFrame.getInsets();
                mainRectangle = ((JFrame)e.getSource()).getBounds();
                mainRectangle.height -= insets.top;
                PConfig.getInstance().mainRectangle.set(mainRectangle);
                Metrics.getInstance().recalculateSizes();
                repaint();
            }
        });

        JFrame.setDefaultLookAndFeelDecorated(true);
        Rectangle mainRectangle = pConfig.mainRectangle.get();
        mainFrame.setBounds(mainRectangle);
        mainContainer = mainFrame.getContentPane();
        mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.X_AXIS));

        mainFrame.setState(Frame.NORMAL);
        mainFrame.setTitle(PConfig.PROJECT_NAME + "-test position");
        mainFrame.setVisible(true);
        mainPanel = new MainPanel(this);
        gameManager = new TestGameManager();
        mainContainer.add(mainPanel);
        Thread gameThread = new Thread(() -> {
            runTests();
            Logger.println("tests ended!");
            mainFrame.dispose();
        });
        gameThread.start();
    }

    @Override
    public void repaint() {
        mainContainer.validate();
        mainContainer.repaint();
    }

    @Override
    public JFrame mainFrame() {
        return mainFrame;
    }

    @Override
    public int specialOption() {
        return Host.SPECIAL_OPTION_SHOW_CARDS;
    }

    @Override
    public long buildDate() {
        return new Date().getTime();
    }

    class TestGameManager extends GameManager {
        TestGameManager() {
            super(PConfig.getInstance(), mainPanel);
        }

        @Override
        public void playRoundForTricks() {
            super.playRoundForTricks();
        }
        @Override
        public void playRoundMisere() {
            super.playRoundMisere();
        }

        public RoundState getRoundState() {
            return GameManager.roundState;
        }
    }

    void runTests() {
        final String[] sources = {
            // hands, bid, [elderHand]
            "♠JQA ♣89JA ♥7XQ  ♠8XK ♣7XK ♦XK ♥8A  ♠9 ♦789JQA ♥9JK : 6♣",
            "♠7 ♣9QK ♦7JQK ♥8X ♦X8  ♠89XA ♣8XA ♥7JA  ♠JQK ♣7J ♦9A ♥9QK : 6♦ : 1",
            "♣9K ♦QK  ♣XA ♥7A  ♣7 ♥9QK : 6♦",
            "♣9QK ♦78XJQK  ♠9XA ♣8XA ♥7JA  ♠QK ♣7J ♦9A ♥9QK : 6♦",
            "♣J ♦Q ♥8A  ♥XQ  ♥9K : 7♣",
            "♠79 ♥XJ  ♠JQ ♦A ♥K  ♠K ♦K ♥QA : 6♥",
            "♦XA ♥7JQK  ♠K ♦78QK ♥X  ♠9 ♣K ♦9 ♥89A : 6♣",
        };

        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split("  | : ");
            for (int j = 0; j < NOP; ++j) {
                hands[j] = new CardSet(pUtil.toCardList(parts[j]));
            }
            int j = NOP;
            Bid bid = Bid.fromName(parts[j]);
            int elderHand = 0;
            if (++j < parts.length) {
                elderHand = Integer.parseInt(parts[j]);    // todo: use elderHand
            }
            boolean keepWorking = true;
            while (keepWorking) {
                keepWorking = false;
                try {
                    for (int i = 0; i < gameManager.getPlayers().length; ++i) {
                        Player player = gameManager.getPlayers()[i];
                        player.setHand(hands[i]);
                    }
                    gameManager.getPlayers()[0].setTricks(ROUND_SIZE - hands[1].size());
                    gameManager.prepareTest(0, bid, null);
                    Trick trick = gameManager.getTrick();
                    trick.setStartedBy(elderHand);
                    forTricksBot = new ForTricksBot(hands);
                    forTricksBot.setBid(bid);
                    if (hands[0].size() > hands[1].size()) {
                        // assuming needed drop
                        gameManager.getRoundState().set(GameManager.RoundStage.drop);
                        Bid _bid = gameManager.getPlayers()[0].drop();
                        Logger.println(_bid);
//                        hands[0] = gameManager.getPlayers()[0].getMyHand();
                        BidData.PlayerBid playerBid = forTricksBot.getDrop(elderHand);
                        forTricksBot.drop(playerBid.drops);
                        hands[0] = forTricksBot.getMyHand();
                    }
//                    forTricksBot = new ForTricksBot(gameManager.getPlayers()[0]);
                    trick.setNumber(ROUND_SIZE - hands[1].size());
                    Bot.targetBot = forTricksBot;
                    Bot.trickList = new TrickList(forTricksBot, trick, hands);

                    if (bid.equals(Bid.BID_MISERE)) {
                        gameManager.playRoundMisere();
                    } else {
                        gameManager.playRoundForTricks();
                    }
                } catch (Player.PrefExceptionRerun e) {
                    Logger.println("replay");
                    keepWorking = true;
                }
            }
        }
    }
}