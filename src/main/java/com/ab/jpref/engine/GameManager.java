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
    public static final boolean SHOW_ALL = false;
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
    }

    static RoundState roundState;
    private static GameManager instance;

    static final BlockingQueue<RoundStage> stageQueue = new LinkedBlockingQueue<>();
    private final Config config;
    private Thread gameThread;
    private final EventObserver eventObserver;
    private final PlayerFactory playerFactory;

    final Player[] players = new Player[NUMBER_OF_PLAYERS];
    private final CardList talonCards = new CardList();
    private final Trick trick = new Trick();

    public String testFileName;
    private int lineCount = 0;

    private CardList deck;
    private int turn;
    private Config.Bid minBid = Config.Bid.BID_6S;
    Player.RoundData currentRound;

    // eventObserver == null for test run
    public GameManager(Config config, EventObserver eventObserver, PlayerFactory playerFactory) {
        instance = this;
        this.config = config;
        this.eventObserver = eventObserver;
        this.playerFactory = playerFactory;
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
                        int turn = Integer.parseInt(tokens.get(tokens.size() - 1));     // 0-based
                        CardList deck = new CardList();
                        for (String token : tokens) {
                            if (token.endsWith(":")) {
                                continue;
                            }
                            deck.addAll(Util.toCardList(token));
                        }
                        playRound(deck, turn);
                        roundState.set(RoundStage.idle);
                    });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // run random rounds
    protected void runGame() {
        int turn = new Random().nextInt(3);
        for (int round = 0; round < config.poolSize.get(); ++round) {
            CardList deck = CardList.getDeck();
            Collections.shuffle(deck);

            playRound(deck, turn);
            Util.sleep(10);     // give jPrefPanel a chance to paint
            turn = ++turn % 3;
        }
    }

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

    void deal(CardList deck, int turn) {
        roundState.discarded.clear();
        int index = 0;
        StringBuilder sb = new StringBuilder();
        for (Player player : players) {
            player.setHand(deck.subList(index, index + ROUND_SIZE));
            sb.append(player.toString()).append(" ");
            index += ROUND_SIZE;
        }
        talonCards.clear();
        talonCards.addAll(deck.subList(index, index + 2));
        sb.append(talonCards);
        Logger.printf("%s %s  %d\n", DEAL_MARK, sb, turn);
    }

    void playRound(CardList deck, int turn) {
        gameThread = Thread.currentThread();
        this.deck = (CardList)deck.clone();
        this.turn = turn;
//        Logger.printf("deal: %s  %d\n", deck.toString(), turn);
        deal(deck, turn);
/*  debug
        roundState.set(RoundStage.bidding);
//        Logger.printf("  %s %d\n", talonCards.toString(), turn + 1);
        Player declarer = bidding(turn);
        if (declarer == null) {
            Logger.printf("playing all-pass\n");
            playRoundAllPass(turn);
        } else {
            Logger.printf("declarer %s, %s %s\n", declarer.getName(), declarer.getBid(), declarer);
            roundState.set(RoundStage.discard);
            declarer.takeTalon(talonCards);
//            Util.sleep(100);
            Logger.printf("%s declareRound()\n", declarer.toString());
            roundState.set(RoundStage.declareRound);
            currentRound = declarer.declareRound(minBid, turn);
        }
/*/
        playRoundAllPass(turn);
//*/
        String sep = "";
        for (int j = 0; j < 3; ++j) {
            Player player = players[j];
            Logger.printf("%s%s: %s", sep, player.getName(), player.getTricks());
            sep = ", ";
        }
        Logger.printf("\n");
        ++lineCount;
        roundState.set(RoundStage.roundEnded);
        if (eventObserver != null) {
            Util.sleep(config.sleepBetweenRounds.get());
        }
        Logger.printf("round ended\n");
    }

    void playRoundAllPass(int turn) {
        trick.turn = turn;
        roundState.discarded.clear();
        for (int c = 0; c < ROUND_SIZE; ++c) {
            trick.clear();
            roundState.set(RoundStage.newTrick);
            if (!talonCards.isEmpty()) {
                Card card = talonCards.get(talonCards.size() - 1);
                Logger.printf("talon: %s, ", card.toString());
                roundState.set(RoundStage.play);
                roundState.discarded.add(card);
                trick.startingSuit = card.getSuit();
            }
            int top = -1;
            Card topCard = null;
            String sep = "";
            trick.started = trick.turn;
            for (int j = 0; j < 3; ++j) {
                Player player = players[trick.turn];
                Card card = player.play(trick);
                trick.trickCards.add(card);
                roundState.set(RoundStage.play);
                Logger.printf("%s%s: %s", sep, player.getName(), card.toString());
                sep = ", ";
                if (trick.startingSuit == null) {
                    trick.startingSuit = card.getSuit();
                }
                if (trick.startingSuit.equals(card.getSuit())) {
                    if (topCard == null) {
                        topCard = card;
                        top = trick.turn;
                    } else {
                        if (topCard.compareInTrick(card) < 0) {
                            topCard = card;
                            top = trick.turn;
                        }
                    }
                }
                trick.leftCard = trick.rightCard;
                trick.rightCard = card;
                trick.turn = ++trick.turn % 3;
            }
            players[top].incrementTricks();
            if (eventObserver != null) {
                Util.sleep(TRICK_TIMEOUT);
            }
            Logger.printf("\n %s takes it, total %d\n\n", players[top].getName(), players[top].getTricks());
            for (int j = 0; j < 3; ++j) {
                Player player = players[j];
                Logger.printf("%s  ", player.toString());
            }
            Logger.printf("\n");
            if (c < 2) {
                trick.turn = turn;
            } else {
                trick.turn = top;
            }
            if (!talonCards.isEmpty()) {
                talonCards.remove(talonCards.size() - 1);
            }
            trick.clear();  // not to repaint
            roundState.set(RoundStage.trickTaken);
            if (eventObserver != null) {
                Util.sleep(TRICK_TIMEOUT);
            }
            Logger.printf(DEBUG, "trick taken\n");
        }
    }

    private void clearQueue() {
        try {
            // unblock gameManager
            stageQueue.put(RoundStage.roundEnded);
            Util.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        while (stageQueue.peek() != null) {
            stageQueue.remove();
        }
    }

    public void restart(boolean replay) {
        Logger.printf("this %s, game %s\n", Thread.currentThread().getName(), gameThread.getName());
        Thread t = new Thread(() -> {
            try {
                gameThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            clearQueue();
            Logger.printf("%s ended\n", gameThread.getName());
            for (Player p : players) {
                p.clearQueue();
            }
            GameManager _gameManager = new GameManager(config, eventObserver, playerFactory);
            if (replay) {
                try {
                    _gameManager.playRound(deck, turn);
                    Logger.println("replay ended!");
                } catch (Player.PrefExceptionRerun e) {
                    // ignore
                    Logger.println("replay ended!");
                }
            }
            Logger.printf("GameManager needs to rerun %s\n", Thread.currentThread().getName());
            _gameManager.lineCount = 0;
            try {
                while (true) {
                    _gameManager.runGame(testFileName, lineCount);
                    lineCount = 0;
                }
            } catch (Player.PrefExceptionRerun e) {
                // todo: happens when there were no moves in playRound, just replay
            }
        });
        t.start();
        for (Player p : players) {
            p.abortThread();
        }
    }

    public static class Trick {
        Card.Suit startingSuit, trumpSuit;
        Config.Bid minBid;
        int started, turn;
        Card leftCard, rightCard;
        final CardList trickCards = new CardList();

        public Card.Suit getStartingSuit() {
            return startingSuit;
        }

        public int getStarted() {
            return started;
        }

        public Card.Suit getTrumpSuit() {
            return trumpSuit;
        }

        public void setTrumpSuit(Card.Suit trumpSuit) {
            this.trumpSuit = trumpSuit;
        }

        public Config.Bid getMinBid() {
            return minBid;
        }

        public int getTurn() {
            return turn;
        }

        public Card getLeftCard() {
            return leftCard;
        }

        public Card getRightCard() {
            return rightCard;
        }

        public CardList getTrickCards() {
            return trickCards;
        }

        public void clear() {
            trickCards.clear();
            leftCard = rightCard = null;
            startingSuit = null;
            minBid = null;
        }
    }

    public static class RoundState {
        RoundStage roundStage;
        Config.Bid round;
        Player declarer;
        final Set<Card> discarded = new HashSet<>();

        public void setRound(Config.Bid round, Player declarer) {
            this.round = round;
            this.declarer = declarer;
            if (round.equals(Config.Bid.BID_ALL_PASS)) {
                set(RoundStage.startAllPass);
            } else {
                set(RoundStage.openTalon);
            }
        }

        public void set(RoundStage state) {
            Logger.printf(DEBUG, "%s -> %s\n", Thread.currentThread().getName(), state);
            this.roundStage = state;
            //PQueue queue = PQueue.getInstance();

            if (GameManager.instance.eventObserver == null) {
                return;     // running in test
            }

            try {
                GameManager.getQueue().put(state);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            GameManager.instance.eventObserver.update();
            try {
                RoundStage q = GameManager.getQueue().take();
                Logger.printf(DEBUG, "GameManager unblocked %s\n", q.toString());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
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

        public Set<Card> getDiscarded() {
            return discarded;
        }
    }

    public interface EventObserver {
        void update();
    }

    public interface PlayerFactory {
        Player getPlayer(int index);
    }

}
