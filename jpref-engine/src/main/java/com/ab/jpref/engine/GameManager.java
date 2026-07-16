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
 * Created: 1/20/2025
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.jpref.config.Config.Bid;
import static com.ab.jpref.config.Config.NOP;
import static com.ab.jpref.config.Config.ROUND_SIZE;
import static com.ab.util.Logger.printf;
import static com.ab.util.Logger.println;
import com.ab.util.ScoreCalculator;
import com.ab.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class GameManager {
    public static boolean RELEASE = false;
    public static boolean DEBUG_LOG = false;

    public static final boolean[] BOTS = new boolean[NOP];
    static {
        BOTS[0] = false;
        BOTS[1] = true;
        BOTS[2] = true;
    }

    public enum RoundStage implements Config.Queueable {
        waitForBot,     // for long operations
        bidding,
        startAllPass,
        showTalon,
        drop,
        declareRound,
        whistSelection,
        selectWhistOption,
        play,
        newTrick,
        trickTaken,
        roundEnded,
        idle,
        replay,
        newGame,
        offer,
    }

    public enum RestartCommand implements Config.Queueable {
        replay,
        newRound,
        offer,
    }

    InputStream testInputStream;

    private static GameManager instance;

    private static Config config;
    private final Util util = Util.getInstance();   // needed for testing
    private final EventObserver eventObserver;
    final CardSet discarded = new CardSet();

    private Thread gameThread;
    Player[] players = new Player[NOP];
    Player[] savedPlayers;
    private CardList deck;
    private CardList talonCards = new CardList();   // todo: CardSet
    private final Trick trick = new Trick();
    private CardList lastTrickCards = new CardList();
    private boolean showDefendersCards;

    private int lineCount = 0;

    int allPassFactor = 0;
    Bid minBid = Bid.BID_6S;
    Player declarer;
    public int declarerNumber;
    CardSet declarerHand;           // with talon, as defenders know it
    CardSet initialDeclarerHand;    // with talon
    public int elderHand;
    boolean cardsRevealed;
    public boolean replayMode;

    public GameManager(Config config, EventObserver eventObserver) {
        instance = this;
        GameManager.config = config;
        this.eventObserver = eventObserver;
        if (eventObserver == null) {
            GameManager.BOTS[0] = true;
            GameManager.BOTS[1] = true;
            GameManager.BOTS[2] = true;
        }
        sleep(config.pauseBetweenTricks.get());
        players = createPlayers();
        printf(DEBUG_LOG, "GameManager constructed\n");
    }

    Player[] createPlayers() {
        Player[] players = new Player[NOP];
        for (int i = 0; i < NOP; ++i) {
            if (BOTS[i]) {
                players[i] = new Bot(i);
            } else {
                players[i] = new HumanPlayer(i, eventObserver);
            }
        }
        return players;
    }

    public static GameManager getInstance() {
        return instance;
    }

    public Player[] getPlayers() {
        return players;
    }

    public static Config getConfig() {
        return config;
    }

    // whisters do not know the declarer's drops
    public Player getDeclarerForDefender() {
        Bot fictitiousBot = new Bot(this.declarer);
        fictitiousBot.myHand = new CardSet(declarerHand);
        return fictitiousBot;
    }

    public CardList getTalonCards() {
        return talonCards;
    }

    private void sleep(int timeout) {
        if (eventObserver == null) {
            return;
        }
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Trick getTrick() {
        return trick;
    }

    public Bid getMinBid() {
        return minBid;
    }

    public void runGame(InputStream testInputStream, int skip) {
        this.testInputStream = testInputStream;
        if (testInputStream == null) {
            runGame();
        } else {
            try {
                util.getList(testInputStream,
                    (res, tokens) -> {
                        if (lineCount++ < skip) {
                            return;
                        }
                        if (!tokens.get(0).startsWith(Util.DEAL_MARK)) {
                            return;     // ignore
                        }
                        --lineCount;
                        elderHand = (Integer.parseInt(tokens.get(tokens.size() - 1))) % NOP;
                        CardList _deck = new CardList();
                        for (String token : tokens) {
                            if (token.endsWith(":")) {
                                continue;
                            }
                            _deck.addAll(util.toCardList(token));
                        }
                        _deck.verifyDeck();
                        deck = new CardList();
                        for (int j = 0; j < NOP; ++j) {
                            int k = ROUND_SIZE * ((j + NOP) % NOP);
                            deck.addAll(_deck.subList(k, k + ROUND_SIZE));
                        }
                        deck.addAll(_deck.subList(30, 32));
                        RestartCommand next = RestartCommand.replay;
                        while (RestartCommand.replay.equals(next)) {
                            minBid = Bid.BID_PASS;
                            next = playRound(deck, elderHand);
                        }
                        int totalPool = 0;
                        for (Player player: players) {
                            for (Player.RoundResults roundResults : player.getHistory()) {
                                totalPool += roundResults.getPoints(Player.PlayerPoints.poolPoints);
                            }
                        }
                        if (totalPool >= config.poolSize.get() * NOP) {
                            // emulate endgame
                            for (Player player: players) {
                                player.clearHistory();
                            }
                        }
                    });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // run random rounds
    protected void runGame() {
        int totalPool;
        elderHand = new Random().nextInt(NOP);
        do {
            deck = CardList.getDeck();
            Collections.shuffle(deck);
            RestartCommand next = RestartCommand.replay;
            while (RestartCommand.replay.equals(next)) {
                minBid = Bid.BID_PASS;
                next = playRound(deck, elderHand);
                sleep(10);     // give jPrefPanel a chance to paint
            }
            elderHand = ++elderHand % NOP;
            if (next.equals(RestartCommand.newRound)) {
                break;
            }
            totalPool = 0;
            for (Player player: players) {
                for (Player.RoundResults roundResults : player.getHistory()) {
                    totalPool += roundResults.getPoints(Player.PlayerPoints.poolPoints);
                }
            }
        } while (totalPool < config.poolSize.get() * NOP);
        printf("game ended\n");
        // now Main will continue launching games
    }

    Bid getBid(int playerNum) {
        return players[playerNum].getBid();
    }

    // for debug
    public void prepareTest(int declarerNum, Bid bid, CardList talonCards) {
        CardSet discarded = CardSet.getDeck();
        for (Player p : players) {
            if (declarerNum != p.getNumber()) {
                discarded.remove(p.myHand);
            }
            discarded.remove(p.leftHand);
            discarded.remove(p.rightHand);
        }
        this.trick.clear(elderHand);
        if (bid.equals(Bid.BID_ALL_PASS)) {
            this.declarerNumber = -1;
            this.declarer = null;
            this.minBid = bid;
            this.trick.minBid = null;
            return;
        }

        if (declarerNum >= 0) {
            this.declarerNumber = declarerNum;
            this.declarer = this.players[declarerNum];
            this.declarer.bid = bid;
            this.minBid = bid;
            this.trick.setBid(bid);
        } else {
            declarer = bidding(elderHand);
            declarerNumber = declarer.getNumber();
        }
        if (talonCards != null) {
            this.declarer.takeTalon(talonCards);
            talonCards.clear();
        }
        this.talonCards.clear();
        this.declarerHand = new CardSet(this.declarer.myHand);
        printf("declarer %s, round %s, %s\n",
            this.declarer.getName(), this.declarer.getBid(), this.declarer.toColorString());
    }

    public boolean showDefendersCards() {
        if (cardsRevealed) {
            return showDefendersCards;
        }
        return false;
    }

    public int getAllPassFactor() {
        return allPassFactor;
    }

    private void update(RoundStage roundStage) {
        if (eventObserver != null) {
            eventObserver.update(roundStage);
        }
    }

    public RestartCommand playRound(CardList deck, int elderHand) {
        if (replayMode) {
            this.players = avatars4Round();
        }
        RestartCommand next;
        try {
            gameThread = Thread.currentThread();
            trick.clear(elderHand);
            lastTrickCards.clear();
            deal(deck);
            declarer = null;
            declarerNumber = -1;
            cardsRevealed = false;

            this.elderHand = elderHand;
            declarer = bidding(elderHand);
            if (declarer == null) {
                printf("playing all-pass\n");
                playRoundAllPass();
            } else {
                printf("declarer %s: %s, %s\n",
                    declarer.getName(), declarer.getBid(), declarer.toColorString());
                declarerNumber = declarer.getNumber();
                update(RoundStage.showTalon);
                for (Player p : players) {
                    if (p instanceof HumanPlayer) {
                        p.acknowledge();
                    }
                }
                declarer.takeTalon(talonCards);
                declarerHand = new CardSet(declarer.myHand);
                initialDeclarerHand = new CardSet(declarerHand);
                if (declarer instanceof HumanPlayer) {
                    update(RoundStage.drop);
                    sleep(10);
                }
                Bid bid = declarer.drop();
                printf("%s wins bidding %s\n", declarer.getName(), bid);
                savedPlayers = this.players;    // save
                if (Bid.BID_MISERE.equals(bid)) {
                    playRoundMisere();
                } else if (!Bid.BID_WITHOUT_THREE.equals(bid)) {
                    playRoundForTricks();
                }
                updateFromAvatars();
            }

            if (!replayMode) {
                int param = 1;
                if (declarer == null) {
                    param = allPassFactor + 1;
                } else if (declarer.getBid().equals(Bid.BID_WITHOUT_THREE)) {
                    param = minBid.goal();
                }
                ScoreCalculator.getInstance().calculate(players, param);
                ++lineCount;
            }
            if (eventObserver == null) {
                next = RestartCommand.newRound;
            } else {
                next = eventObserver.showScores();
            }
            sleep(config.pauseBetweenRounds.get());
            printf("round ended\n");
            replayMode = RestartCommand.replay.equals(next);
            if (!replayMode) {
                if (minBid.equals(Bid.BID_ALL_PASS)) {
                    allPassFactor = ++allPassFactor % 3;
                } else {
                    allPassFactor = 0;
                }
            }
        } catch (Player.PrefExceptionRerun e) {
            String msg = e.getMessage();
            println("round aborted for " + msg);
            next = RestartCommand.valueOf(msg);     // a little ugly
            if (next.equals(RestartCommand.offer)) {
                updateFromAvatars();
                ScoreCalculator.getInstance().calculate(players, minBid.goal());
                if (eventObserver == null) {
                    next = RestartCommand.newRound;
                } else {
                    next = eventObserver.showScores();
                }
            }
        }
        return next;
    }

    Player bidding(int elderHand) {
        // in the future bot should be able to pass even if it can declare a round
        minBid = Bid.BID_6S;
        if (allPassFactor > 0) {
            minBid = Bid.BID_7S;
        }
        update(RoundStage.bidding);
        int passCount = 0;
        Player declarer = null;
        boolean misereDeclared = false;
        while (passCount < 3 && declarer == null || passCount < 2) {
            for (int i = 0; i < players.length; ++i) {
                int j = (i + elderHand) % players.length;
                Player bidder = players[j];
                if (bidder.equals(declarer)) {
                    continue;
                }
                if (Bid.BID_PASS.equals(bidder.getBid())) {
                    continue;
                }
                Bid savedBid = minBid;
                if (passCount == 1 && i == 0 &&
                        !(misereDeclared && Bid.BID_9S.equals(minBid))) {
                    minBid = minBid.prev();
                }
                if (bidder instanceof HumanPlayer) {
                    update(null);
                }
                Bid bid = bidder.getBid(minBid, elderHand);
                if (Bid.BID_MISERE.equals(bid)) {
                    misereDeclared = true;
                }

                if (bid.compareTo(minBid) >= 0) {
                    if (declarer != null && declarer.getBid().equals(Bid.BID_MISERE)) {
                        declarer.setBid(Bid.BID_PASS);
                        ++passCount;
                    }
                    declarer = bidder;
                    minBid = bid.next();
                } else {
                    minBid = savedBid;
                    bidder.setBid(Bid.BID_PASS);
                    ++passCount;
                }
            }
        }
        if (declarer == null) {
            minBid = Bid.BID_ALL_PASS;
        } else {
            minBid = minBid.prev();
        }
        sleep(10);
        return declarer;
    }

    void deal(CardList deck) {
        CardSet[] cardSets = new CardSet[NOP];
        for (int i = 0; i < NOP; ++i) {
            int index = i * ROUND_SIZE;
            cardSets[i] = new CardSet(deck.subList(index, index + ROUND_SIZE));
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NOP; ++i) {
            Player player = players[i];
            if (player instanceof Bot) {
                player.clear();
            }
            player.setHand(cardSets[i]);
            sb.append(player.toColorString()).append("  ");
        }

        talonCards.clear();
        talonCards = new CardList(deck.subList(30, 32));

        sb.append(talonCards.toColorString());
        if (testInputStream == null) {
            // when testing it was displayed already
            printf("%s %s  %d\n", Util.DEAL_MARK, sb, trick.getStartedBy());
        }
    }

    void playRoundAllPass() {
        Card talonCard = talonCards.last();
        for (int c = 0; c < ROUND_SIZE; ++c) {
            for (Player player : players) {
                printf("%s  ", player.toColorString());
            }
            printf("\n");
            if (talonCard != null) {
                update(RoundStage.play);
                trick.add(talonCard, true);
            }
            for (int j = 0; j < players.length; ++j) {
                Player player = players[trick.getTurn()];
                Card card = player.play(trick);
                if (card == null) {
                    throw new RuntimeException("card is null");
                }
                trick.add(card);
                update(RoundStage.play);
            }
            println(trick.toColorString());
            lastTrickCards.clear();
            players[trick.getTop()].incrementTricks();
            sleep(config.pauseBetweenTricks.get());
            printf("%s takes it, total %d\n\n", players[trick.getTop()].getName(), players[trick.getTop()].getTricks());
            lastTrickCards = trick.cards2List();
            if (talonCard != null) {
                lastTrickCards.add(0, talonCard);
            }
            trick.clear(talonCard != null);
            talonCards.removeLast();
            talonCard = talonCards.last();
            update(RoundStage.trickTaken);
            sleep(config.pauseBetweenTricks.get());
            printf(DEBUG_LOG, "trick taken\n");
        }
    }

    void revealCards() {
        cardsRevealed = true;
        int declarerNum = declarer.getNumber();
        Player left = players[(declarerNum + 1) % NOP];
        Player right = players[(declarerNum + 2) % NOP];
        declarer.leftHand = new CardSet(left.myHand);
        declarer.rightHand = new CardSet(right.myHand);
        if (Bot.targetBot != null) {
            Bot.targetBot.leftHand = declarer.leftHand;
            Bot.targetBot.rightHand = declarer.rightHand;
        }
        left.rightHand = new CardSet(declarerHand);
        left.leftHand = new CardSet(right.myHand);
        right.leftHand = new CardSet(declarerHand);
        right.rightHand = new CardSet(left.myHand);
    }

    private void updateFromAvatars() {
        if (savedPlayers == null) {
            return;
        }
        for (int i = 0; i < savedPlayers.length; ++i) {
            Player player = savedPlayers[i];
            Player avatar = this.players[i];
            if (!replayMode) {
                player.setTricks(avatar.getTricks());
            }
        }
        this.players = savedPlayers;
        savedPlayers = null;
        this.declarer = players[declarerNumber];
    }

    private void incrementTricks() {
        Player p = players[trick.getTop()];
        p.incrementTricks();
        if (Bot.targetBot != null && p == this.declarer) {
            Bot.targetBot.setTricks(p.getTricks());
        }
    }

    public Player[] avatars4Round() {
        // replace human or bot depending on whist elections
        int i = this.declarerNumber;
        Player declarer = this.getPlayers()[i];
        Player defender0 = this.getPlayers()[(i + 1) % NOP];
        Player defender1 = this.getPlayers()[(i + 2) % NOP];
        EventObserver clickable = eventObserver;
        Player[] avatars = new Player[NOP];

        if (this.replayMode) {
            avatars[declarer.getNumber()] =
                new HumanPlayer(declarer, clickable);
            avatars[defender0.getNumber()] =
                new HumanPlayer(defender0, clickable);
            avatars[defender1.getNumber()] =
                new HumanPlayer(defender1, clickable);
            return avatars;
        }

        if (declarer instanceof HumanPlayer) {
            avatars[declarer.getNumber()] =
                new HumanPlayer(declarer, clickable);
            if (defender0 instanceof HumanPlayer && defender1 instanceof HumanPlayer) {
                avatars[defender0.getNumber()] =
                    new HumanPlayer(defender0, clickable);
                avatars[defender1.getNumber()] =
                    new HumanPlayer(defender1, clickable);
            } else {
                avatars[defender0.getNumber()] = new Bot(defender0);
                avatars[defender1.getNumber()] = new Bot(defender1);
            }
        } else {
            avatars[declarer.getNumber()] = new Bot(declarer);
            if (defender0 instanceof HumanPlayer && defender0.getBid().equals(Config.Bid.BID_WHIST) ||
                defender1 instanceof HumanPlayer && defender1.getBid().equals(Config.Bid.BID_WHIST)) {
                avatars[defender0.getNumber()] =
                    new HumanPlayer(defender0, clickable);
                avatars[defender1.getNumber()] =
                    new HumanPlayer(defender1, clickable);
            } else {
                avatars[defender0.getNumber()] = new Bot(defender0);
                avatars[defender1.getNumber()] = new Bot(defender1);
            }
        }
        return avatars;
    }

    protected void playRoundForTricks() {
        if (declarer instanceof HumanPlayer) {
            update(RoundStage.declareRound);
        }
        declarer.declareRound(minBid, elderHand);
        this.minBid = declarer.getBid();
        printf("%s declares %s\n", declarer.getName(), this.minBid);
        trick.setBid(this.minBid);

        Player p1 = players[(declarer.getNumber() + 1) % NOP];
        Player p2 = players[(declarer.getNumber() + 2) % NOP];
        p2.setBid(Bid.BID_UNDEFINED);
        if ((p1 instanceof Bot) && (p2 instanceof Bot)) {
            p1.respondOnDeclaration();  // selects pass
            p2.respondOnDeclaration();  // selects whist
        } else {
            update(RoundStage.whistSelection);
            p1.respondOnDeclaration();
            p2.respondOnDeclaration();
            if (p2.getBid().equals(Bid.BID_HALF_WHIST)) {
                // 2nd chance
                p1.respondOnDeclaration();
                if (p1.getBid().equals(Bid.BID_WHIST)) {
                    p2.setBid(Bid.BID_PASS);
                }
            }
        }
        if (p1.getBid().equals(Bid.BID_PASS) && p2.getBid().equals(Bid.BID_PASS)) {
            // when 8♠ or higher
            declarer.setTricks(declarer.getBid().goal());
            return;
        }
        sleep(10);     // give jPrefPanel a chance to paint

        if (p1.getBid().equals(Bid.BID_PASS) && p2.getBid().equals(Bid.BID_WHIST)) {
            if (p2 instanceof HumanPlayer) {
                update(RoundStage.selectWhistOption);
            }
            sleep(100);     // give jPrefPanel a chance to paint
            this.showDefendersCards = p2.playWhistLaying();
        }
        if (p2.getBid().equals(Bid.BID_PASS) && p1.getBid().equals(Bid.BID_WHIST)) {
            if (p1 instanceof HumanPlayer) {
                update(RoundStage.selectWhistOption);
            }
            sleep(100);     // give jPrefPanel a chance to paint
            this.showDefendersCards = p1.playWhistLaying();
        }
        sleep(200);     // give jPrefPanel a chance to paint

        if (!replayMode) {
            this.players = avatars4Round();
        }
        this.declarer = this.players[this.declarerNumber];

        update(RoundStage.play);
        sleep(100);     // give jPrefPanel a chance to paint
        for (int c = 0; !players[0].myHand.isEmpty(); ++c) {
            StringBuilder sb = new StringBuilder();
            String sep = "";
            for (Player player : players) {
                sb.append(sep).append(player.toColorString());
                sep = "  ";
            }
            printf("\n");
            println(sb);
            update(RoundStage.play);
            for (int j = 0; j < players.length; ++j) {
                Player player = players[trick.getTurn()];
                Card card;
                if (c == 0 && j == 0 && player != declarer) {
                    revealCards();
                }
                card = player.play(trick);
                if (card == null) {
                    // sanity check
                    throw new RuntimeException(String.format("player %d, %s, trick %s", player.number, player, trick));
                }
                trick.add(card);
                if (c == 0 && j == 0 && player == declarer) {
                    revealCards();
                }
                if (player instanceof Bot) {
                    sleep(config.pauseBetweenMoves.get());
                }
                update(RoundStage.play);
            }
            update(RoundStage.trickTaken);
            sleep(config.pauseBetweenMoves.get());
            println(trick);
            println(trick.toColorString());
            lastTrickCards.clear();
            incrementTricks();
            printf("%s takes it, total %d\n", players[trick.getTop()].getName(), players[trick.getTop()].getTricks());
            lastTrickCards = trick.cards2List();
            trick.clear();  // not to repaint
            update(RoundStage.trickTaken);
            sleep(config.pauseBetweenTricks.get());
            printf(DEBUG_LOG, "trick taken\n");
        }
    }

    protected void playRoundMisere() {
        declarer.declareRound(minBid, elderHand);
        if (declarer instanceof Bot) {
            Player player = players[(declarerNumber + 1) % NOP];
            if (player instanceof Bot) {
                player = players[(declarerNumber + 2) % NOP];
            }
            player.bid = Bid.BID_WHIST;
        }
        if (!replayMode) {
            this.players = avatars4Round();
        }
        this.declarer = this.players[this.declarerNumber];
        showDefendersCards = true;
        update(RoundStage.play);
        for (int c = 0; c < ROUND_SIZE; ++c) {
            for (Player player : players) {
                printf("%s  ", player.toColorString());
            }
            printf("\n");
            trick.minBid = this.minBid;
            for (int j = 0; j < players.length; ++j) {
                Player player = players[trick.getTurn()];
                Card card;
                if (c == 0 && j == 0 && player != declarer) {
                    revealCards();
                    if (player instanceof HumanPlayer) {
                        update(RoundStage.play);
                    }
                }
                card = player.play(trick);
                if (card == null) {
                    // sanity check
                    throw new RuntimeException(String.format("player %s, trick %s", player, trick));
                }
                trick.add(card);
                declarerHand.remove(card);
                if (c == 0 && j == 0 && player == declarer) {
                    revealCards();
                }
                update(RoundStage.play);
            }
            println(trick);
            println(trick.toColorString());
            lastTrickCards.clear();
            incrementTricks();
            sleep(config.pauseBetweenTricks.get());
            printf("%s takes it, total %d\n\n", players[trick.getTop()].getName(), players[trick.getTop()].getTricks());
            lastTrickCards = trick.cards2List();
            trick.clear();  // not to repaint
            update(RoundStage.trickTaken);
            sleep(config.pauseBetweenTricks.get());
            printf(DEBUG_LOG, "trick taken\n");
        }
    }

    public CardList getLastTrickCards() {
        return lastTrickCards;
    }

    public void restart(RestartCommand command) {
        printf("this %s, game %s\n", Thread.currentThread().getName(), gameThread.getName());
        for (Player player : this.getPlayers()) {
            player.abortThread(command);
        }
    }

    public interface EventObserver {
        void setCurrentPlayer(Player player);
        void update(RoundStage roundStage);
        RestartCommand showScores();
    }
}