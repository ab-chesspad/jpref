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

import com.ab.jpref.ui.Host;
import com.ab.util.ScoreCalculator;
import com.ab.util.Util;
import static com.ab.util.Util.DEAL_MARK;

import java.io.*;
import java.util.*;

public class GameManager implements Serializable {
    public static boolean DEBUG_LOG = false;
    public static final boolean DEBUG_END_OF_GAME = false;

    public static final boolean[] BOTS = new boolean[NOP];
    static {
        BOTS[0] = false;
        BOTS[1] = true;
        BOTS[2] = true;
    }

    private static final long serialVersionUID = Config.projectSerialVersionUID;

    public enum RoundStage implements Config.Queueable {
        dealing,
        bidding,
        showTalon,
        drop,
        declareRound,
        whistSelection,
        selectWhistOption,
        play,
        confirmMove,
        trickTaken,
    }

    public enum RestartCommand implements Config.Queueable {
        replay,
        newRound,
        offer,
        verify,
        reset,      // aborts runGame() with PrefExceptionReset
    }

    transient InputStream testInputStream;

    private static GameManager instance;

    private transient Host host;
    private transient EventObserver eventObserver;

    private transient Util util;   // needed for testing

    RoundStage roundStage = RoundStage.dealing;

    private int lineCount = -1;
    private int allPassFactor = 0;
    public boolean playedAllPass;
    public boolean replayMode;

    private CardList deck;
    private CardList talonCards = new CardList();
    private Player[] players = new Player[NOP];
    public int elderHand;

    // bidding:
    int biddingPassCount = 0;
    private Bid minBid = Bid.BID_6S;
    int nextBidder;

    final CardSet discarded = new CardSet();
    private Player[] savedPlayers;
    private final Trick trick = new Trick();
    private final CardList lastTrickCards = new CardList();
    private int lastTrickStartedBy;

    private Player declarer;
    public int declarerNumber;
    CardSet declarerHand;           // with talon, as defenders know it
    CardSet initialDeclarerHand;    // with talon
    public boolean cardsRevealed;

    public GameManager() {}

    public void init(Host host) {
        instance = this;
        this.host = host;
        this.eventObserver = config().eventObserver;
        util = host.getUtil();
        if (players[0] == null) {
            if (eventObserver == null) {
                GameManager.BOTS[0] = true;
                GameManager.BOTS[1] = true;
                GameManager.BOTS[2] = true;
            }
            sleep(config().pauseBetweenTricks.get());
            for (int i = 0; i < NOP; ++i) {
                if (BOTS[i]) {
                    players[i] = newBot(i);
                } else {
                    players[i] = new HumanPlayer(i, eventObserver);
                }
            }
        } else {
            // initiate unserialized:
            for (int i = 0; i < NOP; ++i) {
                Player p = players[i];
                if (p instanceof HumanPlayer) {
                    ((HumanPlayer)p).initTransient(eventObserver);
                }
                if (savedPlayers != null) {
                    p = savedPlayers[i];
                    if (p instanceof HumanPlayer) {
                        ((HumanPlayer)p).initTransient(eventObserver);
                    }
                }
            }
        }
        printf(DEBUG_LOG, "GameManager initialized\n");
    }

    public static GameManager getInstance() {
        return instance;
    }

    public Config config() {
        return host.config();
    }
 
    public Player[] getPlayers() {
        return players;
    }

    public RoundStage getRoundStage() {
        return roundStage;
    }

    // tests override to play with a Bot subclass
    protected Bot newBot(int number) {
        return new Bot(number);
    }

    protected Bot newBot(Player realPlayer) {
        return new Bot(realPlayer);
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

    // with moveConfirmation, keep the completed trick on the table until the user clicks;
    // no wait when the user played the last card. Returns true if it waited.
    private boolean confirmTrick(Player lastPlayer) {
        if (eventObserver == null || !config().trickConfirmation.get() || lastPlayer instanceof HumanPlayer) {
            return false;
        }
        for (Player p : players) {
            if (p instanceof HumanPlayer) {
                update(RoundStage.confirmMove);
                p.acknowledge();
                return true;
            }
        }
        return false;
    }

    public Trick getTrick() {
        return trick;
    }

    public Bid getMinBid() {
        return minBid;
    }

    public void setMinBid(Bid minBid) {
        this.minBid = minBid;
    }

    public Player getDeclarer() {
        return declarer;
    }

    public CardSet getDeclarerHand() {
        return declarerHand;
    }

    public void setRoundStage(RoundStage roundStage) {
        this.roundStage = roundStage;
    }

    public CardSet getDiscarded() {
        return discarded;
    }

    public void runGame(InputStream testInputStream, int skip) {
        this.testInputStream = testInputStream;
        if (roundStage == RoundStage.dealing) {
            TrickList.getInstance().initBuild(null);
            biddingPassCount = 0;
        }
        if (DEBUG_END_OF_GAME) {
            int poolPoints = config().poolSize.get();
            for (Player player : players) {
                Player.RoundResults roundResults = new Player.RoundResults();
                if (player.number == 0) {
                    roundResults.setPoints(Player.PlayerPoints.poolPoints, poolPoints - 1);
                } else {
                    roundResults.setPoints(Player.PlayerPoints.poolPoints, poolPoints);
                }
                player.getGameHistory().add(roundResults);
            }
        }
        if (testInputStream == null) {
            runGame();
        } else {
            try {
                util.getList(testInputStream,
                    (res, tokens) -> {
                        if (++lineCount < skip) {
                            return;
                        }
                        if (!tokens.get(0).startsWith(DEAL_MARK)) {
                            return;     // ignore
                        }
                        elderHand = (Integer.parseInt(tokens.get(tokens.size() - 1))) % NOP;
                        deck = new CardList();
                        for (String token : tokens) {
                            if (token.endsWith(":")) {
                                continue;
                            }
                            deck.addAll(util.toCardList(token));
                        }
                        deck.verifyDeck();
                        RestartCommand next;
                        do {
                            allPassFactor = 0;
                            playedAllPass = false;
                            minBid = Bid.BID_6S;
                            nextBidder = elderHand;
                            next = playRound(deck);
                            roundStage = RoundStage.dealing;
                            biddingPassCount = 0;
                        } while (RestartCommand.replay.equals(next) || RestartCommand.verify.equals(next));
                        int totalPool = 0;
                        for (Player player: players) {
                            for (Player.RoundResults roundResults : player.getGameHistory()) {
                                totalPool += roundResults.getPoints(Player.PlayerPoints.poolPoints);
                            }
                        }
                        if (totalPool >= config().poolSize.get() * NOP) {
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
        if (roundStage == RoundStage.dealing) {
            elderHand = new Random().nextInt(NOP);
            nextBidder = elderHand;
        }
        do {
            if (roundStage == RoundStage.dealing) {
                TrickList.getInstance().initBuild(null);
                biddingPassCount = 0;
                deck = CardList.getDeck();
                Collections.shuffle(deck);
                minBid = Bid.BID_6S;
            }
            RestartCommand next = RestartCommand.replay;
            while (RestartCommand.replay.equals(next) || RestartCommand.verify.equals(next)) {
                next = playRound(deck);
                sleep(10);     // give jPrefPanel a chance to paint
                roundStage = RoundStage.dealing;
                minBid = Bid.BID_6S;
                biddingPassCount = 0;
                nextBidder = elderHand;
            }
            elderHand = ++elderHand % NOP;
            nextBidder = elderHand;
            totalPool = 0;
            for (Player player: players) {
                for (Player.RoundResults roundResults : player.getGameHistory()) {
                    totalPool += roundResults.getPoints(Player.PlayerPoints.poolPoints);
                }
                player.myHand.clear();
            }
        } while (totalPool < config().poolSize.get() * NOP);
        printf("game ended\n");
        for (Player player: players) {
            player.clearHistory();
        }
        allPassFactor = 0;
        // Main will continue launching games
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
        declarer = null;
        this.trick.clear(elderHand);
        if (bid.equals(Bid.BID_ALL_PASS)) {
            this.declarerNumber = -1;
            this.declarer = null;
            this.minBid = bid;
            this.trick.minBid = null;
            return;
        }
        allPassFactor = 0;
        playedAllPass = false;
        biddingPassCount = 0;
        Bot.targetBot = null;
        if (declarerNum >= 0) {
            this.declarerNumber = declarerNum;
            this.declarer = this.players[declarerNum];
            this.declarer.bid = bid;
            this.minBid = bid;
            this.trick.setBid(bid);
        } else {
            nextBidder = elderHand;
            declarer = bidding();
            if (declarer != null) {
                declarerNumber = declarer.getNumber();
            }
        }
        if (this.declarer != null) {
            if (talonCards != null) {
                this.declarer.takeTalon(talonCards);
                this.talonCards.clear();
            }
            this.declarerHand = new CardSet(this.declarer.myHand);
            if (declarerNum >= 0) {
                // roundStage is still at its default (dealing) here, so playRoundForTricks()/
                // playRoundMisere() would skip their own declareRound() call entirely; without
                // this, Bot.play()'s targetBot==null fallback runs declareRound() on a throwaway
                // proxy (getDeclarerForDefender(), meant only for a human declarer) instead of
                // the real declarer, so the drop never actually reduces this.declarer.myHand -
                // it silently drops from a discarded copy instead, leaving phantom cards in
                // this.declarer's hand for the rest of the round. Call it directly on the real
                // declarer here, then restore the forced bid on both this.declarer and
                // targetBot: declareRound() (via ForTricksBot/MisereBot.getDrop()) always
                // recomputes bid from the bot's own judgement of the hand - normally fine, since
                // real bidding only ever reaches declareRound() with a bid the declarer judged
                // achievable, but a bid forced here for testing can be far beyond what the dealt
                // hand supports, in which case that judgement can come back null. targetBot (not
                // this.declarer) is what declarerPlay()/the search actually reads afterward, so
                // it needs the restored bid too, or it NPEs the first time it's consulted.
                this.declarer.declareRound(bid, elderHand, null);
                this.declarer.bid = bid;
                if (Bot.targetBot != null) {
                    Bot.targetBot.bid = bid;
                }
            }
            printf("declarer %s, round %s, %s\n",
                this.declarer.getName(), this.declarer.getBid(), this.declarer.toColorString());
            this.initialDeclarerHand = new CardSet(declarerHand);
        }
    }

    public int getAllPassFactor() {
        return allPassFactor;
    }

    private void update(RoundStage roundStage) {
        if (roundStage != null) {
            this.roundStage = roundStage;
        }
        if (eventObserver != null) {
            eventObserver.update(roundStage);
        }
    }

    public RestartCommand playRound(CardList deck) {
        printDeck(false);
        if (replayMode) {
            avatars4Round();
        }
        RestartCommand next = null;
        try {
            switch (roundStage) {
                case dealing:
                    trick.clear(elderHand);
                    lastTrickCards.clear();
                    deal(deck);
                    declarer = null;
                    // fall through

                case bidding:
                    declarerNumber = -1;
                    cardsRevealed = false;
                    declarer = bidding();
                    // fall through

                case showTalon:
                    if (declarer != null) {
                        printf("declarer %s: %s, %s\n",
                                declarer.getName(), declarer.getBid(), declarer.toColorString());
                        declarerNumber = declarer.getNumber();
                        update(RoundStage.showTalon);
                        for (Player p : players) {
                            if (p instanceof HumanPlayer) {
                                p.acknowledge();
                                break;
                            }
                        }
                        declarer.takeTalon(talonCards);
                        declarerHand = new CardSet(declarer.myHand);
                        initialDeclarerHand = new CardSet(declarerHand);
                    }
                    // fall through

                case drop:
                    if (declarer != null) {
                        if (declarer instanceof HumanPlayer) {
                            update(RoundStage.drop);
                            sleep(10);
                        }
                        Bid bid = declarer.drop();
                        if (Bid.BID_WITHOUT_THREE.equals(bid)) {
                            break;
                        }
                        minBid = bid;
                        this.roundStage = RoundStage.declareRound;
                    }
                    // fall through

                default:
                    if (declarer == null) {
                        printf("playing all-pass*%d\n", allPassFactor);
                        playRoundAllPass();
                    } else {
                        if (Bid.BID_MISERE.equals(minBid)) {
                            playRoundMisere();
                        } else if (!Bid.BID_WITHOUT_THREE.equals(minBid)) {
                            playRoundForTricks();
                        }
                    }
            }
            printf("round ended\n");
        } catch (Config.PrefExceptionRerun e) {
            for (Player p : players) {
                if (p instanceof HumanPlayer) {
                    ((HumanPlayer)p).clearQueue();
                }
            }
            String msg = e.getMessage();
            println("round aborted for " + msg);
            next = RestartCommand.valueOf(msg);     // a little ugly
            trick.setNumber(9);
        }

        updateFromAvatars();
        printDeck(true);
        if (!replayMode && (next == null || next == RestartCommand.offer)) {
            int param = 1;
            if (declarer == null) {
                param = allPassFactor + 1;
            } else if (declarer.getBid().equals(Bid.BID_WITHOUT_THREE)) {
                param = minBid.goal();
            }
            ScoreCalculator.getInstance(config()).calculate(players, param);
            ++lineCount;
        }
        if (eventObserver == null) {
            next = RestartCommand.newRound;
        } else if (next == null || next == RestartCommand.offer) {
            next = eventObserver.showScores();
            sleep(config().pauseBetweenRounds.get());
        }
        replayMode = (RestartCommand.verify.equals(next) || RestartCommand.replay.equals(next))
            && trick.getNumber() == 9;
        if (!replayMode) {
            if (minBid.equals(Bid.BID_ALL_PASS)) {
                playedAllPass = true;
                allPassFactor = ++allPassFactor % 3;
            } else if (declarerNumber >= 0) {
                boolean whist = players[(declarerNumber + 1) % NOP].getBid().equals(Bid.BID_PASS) ||
                    players[(declarerNumber + 2) % NOP].getBid().equals(Bid.BID_PASS);
                int defenderTricks = players[(declarerNumber + 1) % NOP].getTricks() +
                    players[(declarerNumber + 2) % NOP].getTricks();
                if (declarer.tricks >= declarer.getBid().goal() && whist &&
                        defenderTricks >= declarer.getBid().defenderGoal()) {
                    allPassFactor = 0;
                    playedAllPass = false;
                }
            }
        }
        TrickList.getInstance().initBuild(null);
        return next;
    }

    Player bidding() {
        // in the future bot should be able to pass even if it can declare a round
        if (playedAllPass) {
            minBid = Bid.BID_7S;
        }
        if (minBid.compareTo(Bid.BID_6S) < 0) {
            minBid = Bid.BID_6S;
        }
        update(RoundStage.bidding);
        boolean misereDeclared = false;
        boolean adjustMinBid = true;    // to allow 'здесь'
        for (Player p : players) {
            Bid bid = p.getBid();
            if (Bid.BID_MISERE.equals(bid)) {
                misereDeclared = true;
            }
            if (!Bid.BID_UNDEFINED.equals(bid)) {
                adjustMinBid = false;
            }
        }

        nextBidder = (nextBidder + 2) % NOP;
loop:
        while (declarer == null && biddingPassCount < NOP || biddingPassCount < NOP - 1) {
            for (int i = 0; i < players.length; ++i) {
                nextBidder = (nextBidder + 1) % NOP;
                Player bidder = players[nextBidder];
                if (bidder.equals(declarer)) {
                    continue;
                }
                Bid bid = bidder.getBid();
                if (Bid.BID_PASS.equals(bid)) {
                    continue;
                }
                Bid savedBid = minBid;
                if (adjustMinBid && biddingPassCount == 1 && nextBidder == elderHand &&
                        !(misereDeclared && Bid.BID_9S.equals(minBid))) {
                    // allow 'здесь'
                    minBid = minBid.prev();
                    if (savedBid.equals(Bid.BID_9S)) {
                        minBid = minBid.prev();
                    }
                }
                adjustMinBid = true;
                if (bidder instanceof HumanPlayer) {
                    update(null);
                }
                bid = bidder.getBid(minBid, elderHand);
                printf("%s bid: %s\n", bidder.getName(), bid);
                if (Bid.BID_MISERE.equals(bid)) {
                    misereDeclared = true;
                }

                if (bid.compareTo(minBid) >= 0) {
                    if (declarer != null && declarer.getBid().equals(Bid.BID_MISERE)) {
                        declarer.setBid(Bid.BID_PASS);
                        ++biddingPassCount;
                    }
                    declarer = bidder;
                    if (bid.equals(Bid.BID_XN)) {
                        break loop;
                    }
                    minBid = bid.next();
                    if (bid.equals(Bid.BID_8N)) {
                        minBid = minBid.next();     // skip Misère
                    }
                } else {
                    minBid = savedBid;
                    bidder.setBid(Bid.BID_PASS);
                    ++biddingPassCount;
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

    private void printDeck(boolean roundEnd) {
        if (deck == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (roundEnd) {
            sb.append(DEAL_MARK);
        } else {
            sb.append(DEAL_MARK, 0, DEAL_MARK.length() - 1).append(" start");
        }
        sb.append(" ").append(new CardSet(deck.subList(0, 10)).toColorString()).append("  ")
            .append(new CardSet(deck.subList(10, 20)).toColorString()).append("  ")
            .append(new CardSet(deck.subList(20, 30)).toColorString()).append("  ")
            .append(new CardList(deck.subList(30, 32)).toColorString()).append("  ")
            .append(elderHand);
        if (roundEnd) {
            sb.append(" -> ");
            if (declarerNumber > 0) {
                sb.append(declarerNumber);
            }
            int num = declarerNumber;
            if (num < 0) {
                num = 0;
            }
            sb.append(String.format(" %s %d", minBid, players[num].getTricks()));
        } else {
            sb.append(String.format(", replay %b, all-pass factor %d", replayMode, allPassFactor + 1));
        }
        println(sb);
    }

    void deal(CardList deck) {
        CardSet[] cardSets = new CardSet[NOP];
        for (int i = 0; i < NOP; ++i) {
            int index = i * ROUND_SIZE;
            cardSets[i] = new CardSet(deck.subList(index, index + ROUND_SIZE));
        }

        for (int i = 0; i < NOP; ++i) {
            Player player = players[i];
            if (player instanceof Bot) {
                player.clear();
            }
            player.setHand(cardSets[i]);
        }
        talonCards.clear();
        talonCards = new CardList(deck.subList(30, 32));
    }

    void playRoundAllPass() {
        if (!replayMode) {
            avatars4Round();
        }
        Card talonCard = talonCards.last();
        for (int c = trick.number; c < ROUND_SIZE; ++c) {
            for (Player player : players) {
                printf("%s  ", player.toColorString());
            }
            printf("\n");
            if (talonCard != null) {
                trick.add(talonCard, true);
            }
            update(RoundStage.play);
            Player lastPlayer = null;
            for (int j = trick.size(); j < players.length; ++j) {
                Player player = players[trick.getTurn()];
                Card card = player.play(trick);
                if (card == null) {
                    throw new RuntimeException("card is null");
                }
                trick.add(card);
                lastPlayer = player;
                update(RoundStage.play);
                if (trick.size() >= NOP) {
                    break;
                }
            }
            println(trick.toColorString());
            lastTrickCards.clear();
            players[trick.getTop()].incrementTricks();
            if (!confirmTrick(lastPlayer)) {
                sleep(config().pauseBetweenTricks.get());
            }
            printf("%s takes it, tricks %d\n\n", players[trick.getTop()].getName(), players[trick.getTop()].getTricks());
            lastTrickCards.addAll(trick.cards2List());
            lastTrickStartedBy = trick.getStartedBy();
            if (talonCard != null) {
                lastTrickCards.add(0, talonCard);
            }
            trick.clear(talonCard != null);
            talonCards.removeLast();
            talonCard = talonCards.last();
            update(RoundStage.trickTaken);
            sleep(config().pauseBetweenTricks.get());
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

    private void incrementTricks() {
        Player p = players[trick.getTop()];
        p.incrementTricks();
        if (Bot.targetBot != null && p == this.declarer) {
            Bot.targetBot.setTricks(p.getTricks());
        }
    }

    public void avatars4Round() {
        if (savedPlayers != null) {
            return;
        }
        // replace human or bot depending on whist elections
        int i = this.declarerNumber;
        if (i < 0) {
            i = 0;
        }
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
        } else {
            if (declarer instanceof HumanPlayer) {
                avatars[declarer.getNumber()] =
                        new HumanPlayer(declarer, clickable);
                if (defender0 instanceof HumanPlayer && defender1 instanceof HumanPlayer) {
                    avatars[defender0.getNumber()] =
                            new HumanPlayer(defender0, clickable);
                    avatars[defender1.getNumber()] =
                            new HumanPlayer(defender1, clickable);
                } else {
                    avatars[defender0.getNumber()] = newBot(defender0);
                    avatars[defender1.getNumber()] = newBot(defender1);
                }
            } else {
                avatars[declarer.getNumber()] = newBot(declarer);
                if (defender0 instanceof HumanPlayer && defender0.getBid().equals(Config.Bid.BID_WHIST) ||
                        defender0 instanceof HumanPlayer && defender0.getBid().equals(Bid.BID_WHIST_LAYING) ||
                        defender1 instanceof HumanPlayer && defender0.getBid().equals(Config.Bid.BID_WHIST) ||
                    defender1 instanceof HumanPlayer && defender1.getBid().equals(Bid.BID_WHIST_LAYING)) {
                    avatars[defender0.getNumber()] =
                            new HumanPlayer(defender0, clickable);
                    avatars[defender1.getNumber()] =
                            new HumanPlayer(defender1, clickable);
                } else {
                    avatars[defender0.getNumber()] = newBot(defender0);
                    avatars[defender1.getNumber()] = newBot(defender1);
                }
            }
        }
        savedPlayers = players;
        players = avatars;
        if (declarerNumber >= 0) {
            this.declarer = players[declarerNumber];
        }
    }

    private void updateFromAvatars() {
        if (savedPlayers == null) {
            return;
        }
        if (!replayMode) {
            for (int i = 0; i < NOP; ++i) {
                Player player = savedPlayers[i];
                Player avatar = this.players[i];
                player.setTricks(avatar.getTricks());
                player.setBid(avatar.getBid());
                // the round was actually played out on the avatar (a separate Bot/HumanPlayer
                // copy made by avatars4Round()), so its hand fields are the ones that reflect
                // the finished round; without this, the real player object keeps whatever
                // myHand/leftHand/rightHand it had going in - stale for anyone reading it
                // between now and the next deal() (which overwrites it unconditionally, hiding
                // the staleness in the common case of immediately starting a new round).
                player.myHand.set(avatar.myHand);
                player.leftHand.set(avatar.leftHand);
                player.rightHand.set(avatar.rightHand);
            }
        }
        this.players = savedPlayers;
        savedPlayers = null;
        if (declarerNumber >= 0) {
            this.declarer = players[declarerNumber];
        }
    }

    protected void playRoundForTricks() {
        Player p1, p2;
        switch (roundStage) {
            case showTalon:
            case declareRound:
                if (declarer instanceof HumanPlayer) {
                    update(RoundStage.declareRound);
                }
                declarer.declareRound(minBid, elderHand, null);
                this.minBid = declarer.getBid();
                printf("%s declares %s\n", declarer.getName(), this.minBid);
                trick.setBid(this.minBid);
                // fall through

            case whistSelection:
                update(RoundStage.whistSelection);
                if (this.minBid.goal() < 10 || config().whistTotus.get()) {
                    p1 = players[(declarer.getNumber() + 1) % NOP];
                    p1.setBid(Bid.BID_UNDEFINED);
                    p2 = players[(declarer.getNumber() + 2) % NOP];
                    p2.setBid(Bid.BID_UNDEFINED);
                    if ((p1 instanceof Bot) && (p2 instanceof Bot)) {
                        p1.respondOnDeclaration();  // selects pass
                        printf("%s whist: %s\n", p1.getName(), p1.getBid());
                        p2.respondOnDeclaration();  // selects whist
                        printf("%s whist: %s\n", p2.getName(), p2.getBid());
                    } else {
                        update(RoundStage.whistSelection);
                        p1.respondOnDeclaration();
                        printf("%s whist: %s\n", p1.getName(), p1.getBid());
                        p2.respondOnDeclaration();
                        printf("%s whist: %s\n", p2.getName(), p2.getBid());
                        if (p2.getBid().equals(Bid.BID_HALF_WHIST)) {
                            // 2nd chance
                            p1.respondOnDeclaration();
                            printf("%s whist: %s\n", p1.getName(), p1.getBid());
                            if (p1.getBid().equals(Bid.BID_WHIST_LAYING)) {
                                p2.setBid(Bid.BID_PASS);
                                printf("%s whist: %s\n", p2.getName(), p2.getBid());
                            }
                        }
                    }
                    if (p1.getBid().equals(Bid.BID_PASS) && p2.getBid().equals(Bid.BID_PASS)) {
                        // when 8♠ or higher
                        declarer.setTricks(declarer.getBid().goal());
                        return;
                    }
                    sleep(10);     // give jPrefPanel a chance to paint
                } else {
                    update(RoundStage.selectWhistOption);
                }
                // fall through

            case selectWhistOption:
                if (this.minBid.goal() < 10 || config().whistTotus.get()) {
                    p1 = players[(declarer.getNumber() + 1) % NOP];
                    p2 = players[(declarer.getNumber() + 2) % NOP];
                    Player p = null;
                    if (p1 instanceof HumanPlayer && p1.getBid().equals(Bid.BID_WHIST)) {
                        p = p1;
                    }
                    if (p2 instanceof HumanPlayer && p2.getBid().equals(Bid.BID_WHIST)) {
                        p = p2;
                    }
                    if (p != null) {
                        update(RoundStage.selectWhistOption);
                        sleep(100);     // give jPrefPanel a chance to paint
                        p.playWhistLaying();
                        sleep(100);     // give jPrefPanel a chance to paint
                    }
                } else {
                    if (declarer instanceof HumanPlayer) {
                        players[(declarer.getNumber() + 1) % NOP].setBid(Bid.BID_WHIST_LAYING);
                        players[(declarer.getNumber() + 2) % NOP].setBid(Bid.BID_PASS);
                    } else if (players[(declarer.getNumber() + 1) % NOP] instanceof HumanPlayer) {
                        players[(declarer.getNumber() + 1) % NOP].setBid(Bid.BID_WHIST_LAYING);
                        players[(declarer.getNumber() + 2) % NOP].setBid(Bid.BID_PASS);
                    } else {
                        players[(declarer.getNumber() + 1) % NOP].setBid(Bid.BID_PASS);
                        players[(declarer.getNumber() + 2) % NOP].setBid(Bid.BID_WHIST_LAYING);
                    }
                    revealCards();
                }
                // fall through

            default:
                if (!replayMode) {
                    avatars4Round();
                }
                this.declarer = this.players[this.declarerNumber];

                update(RoundStage.play);
                sleep(100);     // give jPrefPanel a chance to paint
                for (int c = trick.number; c < ROUND_SIZE; ++c) {
                    StringBuilder sb = new StringBuilder();
                    String sep = "";
                    for (Player player : players) {
                        sb.append(sep).append(player.toColorString());
                        sep = "  ";
                    }
                    printf("\n");
                    println(sb);
                    update(RoundStage.play);
                    Player lastPlayer = null;
                    for (int j = trick.size(); j < players.length; ++j) {
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
                        lastPlayer = player;
                        if (c == 0 && j == 0 && player == declarer) {
                            revealCards();
                        }
                        if (player instanceof Bot) {
                            sleep(config().pauseBetweenMoves.get());
                        }
                        update(RoundStage.play);
                    }
                    if (!confirmTrick(lastPlayer)) {
                        update(RoundStage.trickTaken);
                        sleep(config().pauseBetweenMoves.get());
                    }
                    println(trick);
                    println(trick.toColorString());
                    lastTrickCards.clear();
                    incrementTricks();
                    printf("%s takes it, tricks %d\n", players[trick.getTop()].getName(), players[trick.getTop()].getTricks());
                    lastTrickCards.addAll(trick.cards2List());
                    lastTrickStartedBy = trick.getStartedBy();
                    trick.clear();  // not to repaint
                    update(RoundStage.trickTaken);
                    sleep(config().pauseBetweenTricks.get());
                    printf(DEBUG_LOG, "trick taken\n");
                }
        }
    }

    protected void playRoundMisere() {
        if (roundStage.equals(RoundStage.showTalon) ||
                roundStage.equals(RoundStage.declareRound)) {
            declarer.declareRound(minBid, elderHand, null);
            Player player1 = players[(this.declarerNumber + 1) % NOP];
            Player player2 = players[(this.declarerNumber + 2) % NOP];
            if (declarer instanceof HumanPlayer) {
                player1.setBid(Bid.BID_WHIST_LAYING);
                player2.setBid(Bid.BID_PASS);
            } else if (player1 instanceof HumanPlayer) {
                player1.setBid(Bid.BID_WHIST_LAYING);
                player2.setBid(Bid.BID_PASS);
            } else {
                player1.setBid(Bid.BID_PASS);
                player2.setBid(Bid.BID_WHIST_LAYING);
            }
            update(RoundStage.play);
            if (!replayMode) {
                avatars4Round();
            }
            this.declarer = this.players[this.declarerNumber];
        }
        update(RoundStage.play);
        for (int c = trick.number; c < ROUND_SIZE; ++c) {
            for (Player player : players) {
                printf("%s  ", player.toColorString());
            }
            printf("\n");
            trick.minBid = this.minBid;
            Player lastPlayer = null;
            for (int j = trick.size(); j < players.length; ++j) {
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
                lastPlayer = player;
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
            if (!confirmTrick(lastPlayer)) {
                sleep(config().pauseBetweenTricks.get());
            }
            printf("%s takes it, tricks %d\n\n", players[trick.getTop()].getName(), players[trick.getTop()].getTricks());
            lastTrickCards.addAll(trick.cards2List());
            lastTrickStartedBy = trick.getStartedBy();
            trick.clear();  // not to repaint
            update(RoundStage.trickTaken);
            sleep(config().pauseBetweenTricks.get());
            printf(DEBUG_LOG, "trick taken\n");
        }
    }

    public void restart(RestartCommand command) {
        printf(DEBUG_LOG, "this %s, game %s\n", Thread.currentThread().getName());
        for (Player player : this.getPlayers()) {
            player.abortThread(command);
        }
    }

    public CardList getLastTrickCards() {
        return lastTrickCards;
    }

    public int getLastTrickStartedBy() {
        return lastTrickStartedBy;
    }

    public interface EventObserver {
        void setCurrentPlayer(Player player);
        void update(RoundStage roundStage);
        RestartCommand showScores();
    }
}