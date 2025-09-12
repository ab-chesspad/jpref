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
import com.ab.jpref.cards.CardSet;
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
    public static boolean RELEASE = false;
    public static boolean DEBUG_LOG = false;
    public static int TRICK_TIMEOUT = 500;
    public static boolean SKIP_BIDDING = false;

    public static final int ROUND_SIZE = 10;   // total tricks == initial hand size
    public static final int NUMBER_OF_PLAYERS = 3;

    public enum RoundStage implements Config.Queueable {
//        resized,
//        painted,
        bidding,
        drop,
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

    public enum RestartCommand implements Config.Queueable {
        goon,
        replay,
        newGame,
    }

    public static String testFileName;
    public static final int DECLARER_NUM = 0;

    static RoundState roundState;
    private static GameManager instance;

    static final BlockingQueue<RoundStage> stageQueue = new LinkedBlockingQueue<>();
    private final Config config;
    private Thread gameThread;
    private final EventObserver eventObserver;

    final Player[] players = new Player[NUMBER_OF_PLAYERS];
    private CardList deck;
    private CardList talonCards = new CardList();
    private final Trick trick = new Trick();
    private final Trick lastTrick = new Trick();

    private int lineCount = 0;

    Config.Bid minBid = Config.Bid.BID_6S;
    Player declarer;
    CardSet declarerHand, initialDeclarerHand;   // with talon
    int initElderHand;
    int elderHand;
    CardSet discarded;

    // eventObserver == null for test run
    public GameManager(Config config, EventObserver eventObserver, PlayerFactory playerFactory) {
        instance = this;
        this.config = config;
        this.eventObserver = eventObserver;
        if (eventObserver != null) {
            Util.sleep(TRICK_TIMEOUT);
        }
        roundState = new RoundState();
        for (int i = 0; i < NUMBER_OF_PLAYERS; ++i) {
            players[i] = playerFactory.getPlayer(i);
        }
        Logger.printf(DEBUG_LOG, "GameManager constructed\n");
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

    public Player getDeclarer() {
        return declarer;
    }

    // player does not know declarer's drops
    public Player getDeclarerFor() {
        Bot fictitiousBot = new Bot(this.declarer);
        fictitiousBot.myHand = declarerHand.clone();
        return fictitiousBot;
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

    public void runGame(final String testFileName, int skip) {
        GameManager.testFileName = testFileName;
        gameThread = Thread.currentThread();
        Logger.printf(DEBUG_LOG, "runGame %s\n", gameThread.getName());
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
                        if (!tokens.get(0).startsWith(Util.DEAL_MARK)) {
                            return;     // ignore
                        }
                        --lineCount;
                        elderHand = (DECLARER_NUM + Integer.parseInt(tokens.get(tokens.size() - 1))) % NUMBER_OF_PLAYERS;
                        CardList _deck = new CardList();
                        for (String token : tokens) {
                            if (token.endsWith(":")) {
                                continue;
                            }
                            _deck.addAll(Util.toCardList(token));
                        }
                        _deck.verifyDeck();
                        deck = new CardList();
                        for (int j = 0; j < NUMBER_OF_PLAYERS; ++j) {
                            int k = 10 * ((j - DECLARER_NUM + NUMBER_OF_PLAYERS) % NUMBER_OF_PLAYERS);
                            deck.addAll(_deck.subList(k, k + 10));
                        }
                        deck.addAll(_deck.subList(30, 32));
                        MisereBot.debugDrop = new CardList(deck.subList(30, 32));

                        RoundStage next = RoundStage.replay;
                        while (RoundStage.replay.equals(next)) {
                            next = playRound(deck, elderHand);
                            roundState.set(RoundStage.idle);
                            Util.sleep(10);     // give jPrefPanel a chance to paint
                        }
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
            deck = CardList.getDeck();
            Collections.shuffle(deck);
            RoundStage next = RoundStage.replay;
            while (RoundStage.replay.equals(next)) {
                next = playRound(deck, elderHand);
                roundState.set(RoundStage.idle);
                Util.sleep(10);     // give jPrefPanel a chance to paint
            }
            elderHand = ++elderHand % NUMBER_OF_PLAYERS;
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
            lastTrick.clear();
            deal(deck);
            declarer = null;

            initElderHand = elderHand;
            if (testFileName != null && testFileName.contains("misere") && declarer instanceof Bot) {
                declarer = players[DECLARER_NUM];
//                    trick.startedBy = elderHand;
                declarer.bid = Config.Bid.BID_MISERE;
                minBid = Config.Bid.BID_MISERE;

                declarer.takeTalon(talonCards);
                declarerHand = declarer.myHand.clone();
                initialDeclarerHand = declarer.myHand.clone();
                declarer.declareRound(minBid, elderHand);
                trick.clear(elderHand);
                playRoundMisere();
            } else {
                if (SKIP_BIDDING) {
                    // todo!
//            roundState.set(RoundStage.declareRound);
//            declarer = players[0];
//            declarer.setBid(Config.Bid.BID_7D);
//            declarer.declareRound(minBid, true);
                    playRoundAllPass();
                } else {
                    roundState.set(RoundStage.bidding);
                    declarer = bidding(elderHand);
                    if (declarer == null) {
                        Logger.printf("playing all-pass\n");
                        playRoundAllPass();
                    } else {
                        Logger.printf("declarer %s, %s %s\n",
                            declarer.getName(), declarer.getBid(), declarer.toColorString());
                        declarer.takeTalon(talonCards);
                        declarerHand = declarer.myHand.clone();
                        initialDeclarerHand = declarer.myHand.clone();
                        roundState.set(RoundStage.drop);
                        Config.Bid bid = declarer.drop();
                        if (!Config.Bid.BID_WITHOUT_THREE.equals(bid)) {
                            roundState.set(RoundStage.roundDeclared);
                            Util.sleep(100);
                            Logger.printf("%s declares %s\n", declarer.getName(), bid);
                            if (Config.Bid.BID_MISERE.equals(bid)) {
                                playRoundMisere();
                            } else {
                                roundState.set(RoundStage.declareRound);
                                declarer.declareRound(minBid, elderHand);
                                // todo: whist/pass and play round for tricks
                                playRoundAllPass(); // just a placeholder
                            }
                        }
                    }
                }
            }

            String sep = "";
            for (Player player : players) {
                Logger.printf("%s%s: %s", sep, player.getName(), player.getTricks());
                sep = ", ";
            }
            Logger.printf("\n");
            ScoreCalculator.getInstance().calculate(declarer, players, 1);
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

    Player bidding(int elderHand) {
        // in the future bot should be able to pass even if it can declare a round
        minBid = Config.Bid.BID_6S;
        int passCount = 0;
        Player declarer = null;
        boolean misereDeclared = false;
        while (passCount < 2) {
            for (int i = 0; i < players.length; ++i) {
                int j = (i + elderHand) % players.length;
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
                Config.Bid bid = currentBidder.getBid(minBid, elderHand);
                if (Config.Bid.BID_MISERE.equals(bid)) {
                    misereDeclared = true;
                }

                if (bid.compareTo(minBid) >= 0) {
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

    void deal(CardList deck) {
        CardSet[] cardSets = new CardSet[NUMBER_OF_PLAYERS];
        for (int i = 0; i < NUMBER_OF_PLAYERS; ++i) {
            int index = i * ROUND_SIZE;
            cardSets[i] = new CardSet(deck.subList(index, index + ROUND_SIZE));
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NUMBER_OF_PLAYERS; ++i) {
            Player player = players[i];
            player.setHand(cardSets[i]);
            sb.append(player.toColorString()).append(" ");
        }

        talonCards.clear();
        talonCards = new CardList(deck.subList(30, 32));

        sb.append(talonCards.toColorString());
        if (testFileName == null) {
            // when testing it was displayed already
            Logger.printf("%s %s  %d\n", Util.DEAL_MARK, sb, trick.getStartedBy());
        }
    }

    void playRoundAllPass() {
        for (int c = 0; c < ROUND_SIZE; ++c) {
            for (Player player : players) {
                Logger.printf("%s  ", player.toColorString());
            }
            Logger.printf("\n");
            if (!talonCards.isEmpty()) {
                Card card = talonCards.get(talonCards.size() - 1);
                roundState.set(RoundStage.play);
                trick.add(card, true);
            }
            for (int j = 0; j < players.length; ++j) {
                Player player = players[trick.getTurn()];
                Card card = player.play(trick);
                trick.add(card);
                roundState.set(RoundStage.play);
            }
            Logger.println(trick.toColorString());
            lastTrick.clear();
            players[trick.top].incrementTricks();
            if (eventObserver != null) {
                Util.sleep(TRICK_TIMEOUT);
            }
            Logger.printf("%s takes it, total %d\n\n", players[trick.top].getName(), players[trick.top].getTricks());
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
            Logger.printf(DEBUG_LOG, "trick taken\n");
        }
    }

    void revealCards() {
        int declarerNum = declarer.getNumber();
        Player left = players[(declarerNum + 1) % NUMBER_OF_PLAYERS];
        Player right = players[(declarerNum + 2) % NUMBER_OF_PLAYERS];
        declarer.leftHand = left.myHand.clone();
        declarer.rightHand = right.myHand.clone();
        left.rightHand = declarerHand.clone();
        left.leftHand = right.myHand.clone();
        right.leftHand = declarerHand.clone();
        right.rightHand = left.myHand.clone();
    }

    void playRoundMisere() {
//        trick.declarerNum = declarer.getNumber();
        roundState.set(RoundStage.play);
        for (int c = 0; c < ROUND_SIZE; ++c) {
            for (Player player : players) {
                Logger.printf("%s  ", player.toColorString());
            }
            Logger.printf("\n");
            trick.minBid = this.minBid;
            roundState.set(RoundStage.play);
            StringBuilder sb = new StringBuilder();
//            Logger.printf("trick %d: ", trick.number);
            sb.append(String.format("trick %d: ", trick.number));
            String sep = "";
            for (int j = 0; j < players.length; ++j) {
                Player player = players[trick.getTurn()];
                Card card;
                if (c == 0 && j == 0 && player != declarer) {
                    revealCards();
                }
                card = player.play(trick);
                if (card == null) {
                    throw new RuntimeException(String.format("player %s, trick %s", player, trick.getTrickCards().toString()));
                }
                trick.add(card);
                declarerHand.remove(card);
                if (c == 0 && j == 0 && player == declarer) {
                    revealCards();
                }
//                Logger.printf("%s%s: %s", sep, player.getName(), card.toColorString());
                sb.append(String.format("%s%s: %s", sep, player.getName(), card.toColorString()));
                sep = ", ";
            }
            Logger.println(sb.toString());
            lastTrick.clear();
            players[trick.top].incrementTricks();
            if (eventObserver != null) {
                Util.sleep(TRICK_TIMEOUT);
            }
            Logger.printf("%s takes it, total %d\n\n", players[trick.top].getName(), players[trick.top].getTricks());
            lastTrick.trickCards = (CardList)trick.trickCards.clone();
            lastTrick.startedBy = trick.startedBy;
            trick.clear();  // not to repaint
            roundState.set(RoundStage.trickTaken);
            if (eventObserver != null) {
                Util.sleep(TRICK_TIMEOUT);
            }
            Logger.printf(DEBUG_LOG, "trick taken\n");
        }
    }

    public Trick getLastTrick() {
        return lastTrick;
    }

    private void clearHistory(RestartCommand command) {
        for (int i = 0; i < NUMBER_OF_PLAYERS; ++i) {
            if (players[i].getHistory().isEmpty()) {
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
            Logger.printf(DEBUG_LOG, "%s -> %s\n", Thread.currentThread().getName(), state);
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
                Logger.printf(DEBUG_LOG,"GameManager blocked %s\n", state.toString());
                q = GameManager.getQueue().take();
                Logger.printf(DEBUG_LOG,"GameManager unblocked %s\n", q.toString());
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
