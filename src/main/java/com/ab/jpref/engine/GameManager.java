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
 * Created: 1/25/2025
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Config;
//import com.ab.pref.HumanPlayer; // no dependencies on com.ab.pref!
import com.ab.util.Logger;
import com.ab.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class GameManager {
    public static boolean DEBUG = false;
    public static boolean SHOW_ALL = true;
    public static final int TRICK_TIMEOUT = 1000;

    public static final int ROUND_SIZE = 10;   // total tricks == initial hand size
    public static final int NUMBER_OF_PLAYERS = 3;
    public static final String DEAL_MARK = "deal:";

    public enum RoundStage implements Player.Queueable {
        resized,
        painted,
        bidding,
        discard,
        declareRound,
        startAllPass,
        roundDeclared,
        openTalon,
        play,
        newTrick,
        trickTaken,
        roundEnded,
        idle,

        goon,
        replay,
        newGame,
    }

    public enum RestartCommand implements Player.Queueable {
        goon,
        replay,
        newGame,
    }

    static RoundState roundState;
    private static GameManager instance;

    static final BlockingQueue<RoundStage> stageQueue = new LinkedBlockingQueue<>();
    private final Config config;
    private Thread gameThread;
    private final EventObserver eventObserver;

    final Player[] players = new Player[NUMBER_OF_PLAYERS];
    private final CardList talonCards = new CardList();
    private Trick trick = new Trick();
    private Trick lastTrick = new Trick();

    public String testFileName;
    private int lineCount = 0;

    private Config.Bid minBid = Config.Bid.BID_6S;
    Player.RoundData currentRound;
    int elderHand;

    // eventObserver == null for test run
    public GameManager(Config config, EventObserver eventObserver, PlayerFactory playerFactory) {
        instance = this;
        this.config = config;
        this.eventObserver = eventObserver;
        if (eventObserver == null) {
            Logger.set(System.out);
        }
        if (eventObserver != null) {
            Util.sleep(TRICK_TIMEOUT);
        }
        roundState = new RoundState();
        for (int i = 0; i < NUMBER_OF_PLAYERS; ++i) {
            players[i] = playerFactory.getPlayer(i);
        }
        Logger.printf(DEBUG, "GameManager constructed\n");
    }

    public static GameManager getInstance() {
        return instance;
    }

    public static RoundState getState() {
        return roundState;
    }

    public static BlockingQueue<RoundStage> getQueue() {
        return stageQueue;
    }

    public Player[] getPlayers() {
        return players;
    }

    public boolean showCards(int index) {
        if (SHOW_ALL) {
            return true;
        } else {
            // depending on the round!
            return index == 0;
        }
    }

    public CardList getTalonCards() {
        return talonCards;
    }

    public Trick getTrick() {
        return trick;
    }

    public Config.Bid getMinBid() {
        return minBid;
    }

    public void runGame(String testFileName, int skip) {
        this.testFileName = testFileName;
        gameThread = Thread.currentThread();
        Logger.printf(DEBUG, "runGame %s\n", gameThread.getName());
        if (testFileName == null) {
            runGame();
        } else {
            try {
                String s = new File(testFileName).getAbsolutePath();
                Util.getList(testFileName,
                    (res, tokens) -> {
                        if (lineCount++ < skip) {
                            return;
                        }
                        if (!tokens.get(0).startsWith(DEAL_MARK)) {
                            return;     // ignore
                        }
                        --lineCount;
                        elderHand = Integer.parseInt(tokens.get(tokens.size() - 1));     // 0-based
                        CardList deck = new CardList();
                        for (String token : tokens) {
                            if (token.endsWith(":")) {
                                continue;
                            }
                            deck.addAll(Util.toCardList(token));
                        }
                        playRound(deck, elderHand);
                        roundState.set(RoundStage.idle);
                        elderHand = ++elderHand % NUMBER_OF_PLAYERS;
                    });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // run random rounds
    protected void runGame() {
        elderHand = new Random().nextInt(NUMBER_OF_PLAYERS);
        for (int round = 0; round < config.poolSize.get(); ++round) {
            CardList deck = CardList.getDeck();
            Collections.shuffle(deck);

            RoundStage next;
            do {
                next = playRound(deck, elderHand);
                elderHand = ++elderHand % NUMBER_OF_PLAYERS;
            } while (RoundStage.replay.equals(next));
            Util.sleep(10);     // give jPrefPanel a chance to paint
            if (next.equals(RoundStage.newGame)) {
                break;
            }
        }
        Logger.printf("game ended\n");
        // now Main will continue launching games
    }

    RoundStage playRound(CardList deck, int elderHand) {
        RoundStage next;
        try {
            gameThread = Thread.currentThread();
            trick.clear(elderHand);
            deal(deck);
            currentRound = new Player.RoundData();

/*  debug
        roundState.set(RoundStage.bidding);
//        Logger.printf("  %s %d\n", talonCards.toString(), turn + 1);
        currentRound.declarer = bidding(turn);
        if (currentRound.declarer == null) {
            Logger.printf("playing all-pass\n");
            playRoundAllPass(turn);
        } else {
            Logger.printf("declarer %s, %s %s\n",
                    currentRound.declarer.getName(), currentRound.declarer.getBid(), currentRound.declarer);
            roundState.set(RoundStage.discard);
            currentRound.declarer.takeTalon(talonCards);
//            Util.sleep(100);
            Logger.printf("%s declareRound()\n", currentRound.declarer.toString());
            roundState.set(RoundStage.declareRound);
            currentRound = currentRound.declarer.declareRound(minBid, turn);
        }
/*/
            playRoundAllPass();
//*/
            String sep = "";
            for (int j = 0; j < players.length; ++j) {
                Player player = players[j];
                Logger.printf("%s%s: %s", sep, player.getName(), player.getTricks());
                sep = ", ";
            }
            Logger.printf("\n");
            ScoreCalculator.getInstance().calculate(currentRound.declarer, players, 1);
            ++lineCount;
            next = roundState.set(RoundStage.roundEnded);
            if (eventObserver != null) {
                Util.sleep(config.sleepBetweenRounds.get());
            }
            Logger.printf("round ended\n");
        } catch (Player.PrefExceptionRerun e) {
            String msg = e.getMessage();
            Logger.println(msg);
            next = RoundStage.valueOf(msg);     // a little ugly
        }
        return next;
    }

/*
    Player bidding(int turn) {
        Config.Bid[] bids = new Config.Bid[players.length];
        // in the future bot should be able to pass even if it can declare a round
        Config.Bid currentBid = Config.Bid.BID_PASS;    // todo: m.b ♠7
        minBid = Config.Bid.BID_6S;
        int passCount = 0;
        Player declarer = null;
        boolean misereDeclared = false;
        while (passCount < 2) {
            for (int i = 0; i < players.length; ++i) {
                int j = (i + turn) % players.length;
                Player currentBidder = players[j];
                if (currentBidder.equals(declarer)) {
                    continue;
                }
                if (Config.Bid.BID_PASS.equals(currentBidder.getBid())) {
                    continue;
                }
                Config.Bid savedBid = minBid;
                if (passCount == 1 && i == 0 &&
                        !(misereDeclared && Config.Bid.BID_9S.equals(minBid))) {
                    minBid = minBid.prev();
                }
                Config.Bid bid = currentBidder.getBid(minBid, turn);
                if (Config.Bid.BID_MISERE.equals(bid)) {
                    misereDeclared = true;
                }

                if (bid.compareTo(minBid) >= 0) {
                    currentBid = bid;
                    if (declarer != null && declarer.getBid().equals(Config.Bid.BID_MISERE)) {
                        declarer.setBid(Config.Bid.BID_PASS);
                        ++passCount;
                    }
                    declarer = currentBidder;
                    minBid = bid.next();
                } else {
                    minBid = savedBid;
                    currentBidder.setBid(Config.Bid.BID_PASS);
                    ++passCount;
                }

                roundState.set(RoundStage.bidding);
            }
        }
        roundState.set(RoundStage.bidding);
        if (declarer == null) {
            minBid = Config.Bid.BID_ALL_PASS;
        } else {
            minBid = minBid.prev();
        }
        return declarer;
    }
*/

    void deal(CardList deck) {
        int index = 0;
        StringBuilder sb = new StringBuilder();
        for (Player player : players) {
            player.setHand(deck.subList(index, index + ROUND_SIZE));
            sb.append(player).append(" ");
            index += ROUND_SIZE;
        }
        talonCards.clear();
        talonCards.addAll(deck.subList(index, index + 2));
        sb.append(talonCards);
        Logger.printf("%s %s  %d\n", DEAL_MARK, sb, trick.getStartedBy());
    }

    void playRoundAllPass() {
        for (int c = 0; c < ROUND_SIZE; ++c) {
            roundState.set(RoundStage.newTrick);
            if (!talonCards.isEmpty()) {
                Card card = talonCards.get(talonCards.size() - 1);
                Logger.printf("talon: %s, ", card.toString());
                roundState.set(RoundStage.play);
                trick.add(card, true);
            }
            String sep = "";
            for (int j = 0; j < players.length; ++j) {
                Player player = players[trick.getTurn()];
                Card card = player.play(trick);
                trick.add(card);
                roundState.set(RoundStage.play);
                Logger.printf("%s%s: %s", sep, player.getName(), card.toString());
                sep = ", ";
            }
            lastTrick.clear();
            players[trick.top].incrementTricks();
            if (eventObserver != null) {
                Util.sleep(TRICK_TIMEOUT);
            }
            Logger.printf("\n %s takes it, total %d\n\n", players[trick.top].getName(), players[trick.top].getTricks());
            for (int j = 0; j < players.length; ++j) {
                Player player = players[j];
                Logger.printf("%s  ", player.toString());
            }
            Logger.printf("\n");
            Card talonCard = null;
            if (!talonCards.isEmpty()) {
                talonCard = talonCards.remove(talonCards.size() - 1);
            }
            lastTrick.trickCards = (CardList)trick.trickCards.clone();
            lastTrick.startedBy = trick.startedBy;
            if (talonCard != null) {
                lastTrick.trickCards.add(0, talonCard);
            }
            trick.clear();  // not to repaint
            roundState.set(RoundStage.trickTaken);
            if (eventObserver != null) {
                Util.sleep(TRICK_TIMEOUT);
            }
            Logger.printf(DEBUG, "trick taken\n");
        }
    }

    public Trick getLastTrick() {
        return lastTrick;
    }

    private void clearHistory(RestartCommand command) {
        for (int i = 0; i < NUMBER_OF_PLAYERS; ++i) {
            if (players[i].getHistory().size() == 0) {
                continue;
            }
            if (command.equals(RestartCommand.replay)) {
                players[i].getHistory().remove(players[i].getHistory().size() - 1);
            } else {
                players[i].getHistory().clear();
            }
        }
    }

    private void clearQueue() {
        while (stageQueue.peek() != null) {
            stageQueue.remove();
        }
    }

    public void restart(RestartCommand command) {
        Logger.printf("this %s, game %s\n", Thread.currentThread().getName(), gameThread.getName());

        try {
            switch (command) {
                case goon:
                    clearQueue();
                    GameManager.getQueue().put(RoundStage.goon);
                    break;

                case replay:
                    clearQueue();
                    clearHistory(command);
                    GameManager.getQueue().put(RoundStage.replay);
                    break;

                case newGame:
                    clearQueue();
                    clearHistory(command);
                    GameManager.getQueue().put(RoundStage.newGame);
                    break;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static class RoundState {
        RoundStage roundStage;
        Config.Bid round;
        Player declarer;

        public void setRound(Config.Bid round, Player declarer) {
            this.round = round;
            this.declarer = declarer;
            if (round.equals(Config.Bid.BID_ALL_PASS)) {
                set(RoundStage.startAllPass);
            } else {
                set(RoundStage.openTalon);
            }
        }

        public RoundStage set(RoundStage state) {
            RoundStage q = null;
            Logger.printf(DEBUG, "%s -> %s\n", Thread.currentThread().getName(), state);
            this.roundStage = state;

            if (GameManager.instance.eventObserver == null) {
                return q;     // running in test
            }

            try {
                GameManager.getQueue().put(state);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            GameManager.instance.eventObserver.update();
            try {
                q = GameManager.getQueue().take();
                Logger.printf(DEBUG, "GameManager unblocked %s\n", q.toString());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return q;
        }

        public RoundStage getRoundStage() {
            return roundStage;
        }

        public Config.Bid getRound() {
            return round;
        }

        public Player getDeclarer() {
            return declarer;
        }

    }

    public interface EventObserver {
        void update();
    }

    public interface PlayerFactory {
        Player getPlayer(int index);
    }

}
