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
    public static boolean DEBUG_END_OF_GAME = false;
    public static boolean DEBUG_ALL_PASS = false;
    public static boolean DEBUG_STANDING_WHIST = false;
    public static boolean DEBUG_BOTH_WHIST = false;     // the bots whist on every contract
    // an input file keeps the bots' time limits: the app replays tests/freeplay at the speed of a real game,
    // the tests need no limits to be repeatable
    public static boolean REPLAY_TIME_LIMITED = false;

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
    public boolean newGame;

    // bidding:
    int biddingPassCount = 0;
    private Bid minBid = Bid.BID_6S;
    int nextBidder;

    final CardSet discarded = new CardSet();
    final CardSet declarerPlayed = new CardSet();     // cards the declarer played, not the shown voids
    private Player[] savedPlayers;
    private final Trick trick = new Trick();
    private final CardList lastTrickCards = new CardList();
    private int lastTrickStartedBy;

    Player declarer;
    public int declarerNumber;
    CardSet declarerHand;           // with talon, as defenders know it
    CardSet initialDeclarerHand;    // with talon
    public boolean cardsRevealed;
    // whist standing: every player sees only his own cards, see Bot.playStanding()
    public boolean standing;
    final CardSet talonShown = new CardSet();   // the talon the declarer took, all saw it

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
            // a bot declarer's targetBot is transient: rebuild it from the declarer's own hand,
            // otherwise the defenders guess the drop and the declarer plays from their guess
            if (declarer instanceof Bot && roundStage.compareTo(RoundStage.whistSelection) >= 0) {
                Bot bot = (Bot) declarer;
                if (Bid.BID_MISERE.equals(declarer.getBid())) {
                    bot.targetBot = MisereBot.getInstance(bot);
                } else {
                    bot.targetBot = ForTricksBot.getInstance(bot);
                }
                bot.targetBot.setTricks(bot.getTricks());
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
        fictitiousBot.myHand.set(declarerHand);
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

    public boolean isNewGame() {
        return newGame;
    }

    public void runGame(InputStream testInputStream, int skip) {
        this.testInputStream = testInputStream;
        if (roundStage == RoundStage.dealing) {
            TrickList.getInstance().initBuild(null);
            biddingPassCount = 0;
            newGame = true;
        }
        if (DEBUG_END_OF_GAME) {
            for (Player player : players) {
                if (!player.getGameHistory().isEmpty()) {
                    continue;   // restored game, keep its history
                }
                int poolPoints = config().poolSize.get();
                if (player.number == 0) {
                    --poolPoints;
                }
                for (int i = 0; i < poolPoints; ++i) {
                    Player.RoundResults roundResults = new Player.RoundResults();
                    roundResults.setPoints(Player.PlayerPoints.poolPoints, 1);
                    player.getGameHistory().add(roundResults);
                }
            }
            allPassFactor = 1;
        }
        if (testInputStream == null) {
            runGame();
        } else {
            // an input file plays its deals as the tests do, see BaseTest and TestGameManager:
            // no random drops, no time limits unless REPLAY_TIME_LIMITED, the same random deals for each deal
            Bot.DeclarerDrop savedDrop = Bot.declarerDrop;
            boolean savedTimeLimited = Bot.standingTimeLimited;
            long savedSeed = Bot.standingSeed;
            Bot.declarerDrop = Bot.DeclarerDrop.First;
            Bot.standingTimeLimited = REPLAY_TIME_LIMITED;
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
                            Bot.standingSeed = 1;
                            Bot.standingRandom = new Random(1);    // a deal does not depend on the ones before
                            allPassFactor = 0;
                            playedAllPass = false;
                            minBid = Bid.BID_6S;
                            nextBidder = elderHand;
                            next = playRound(deck);
                            roundStage = RoundStage.dealing;
                            biddingPassCount = 0;
                        } while (RestartCommand.replay.equals(next) || RestartCommand.verify.equals(next));
                        if (ScoreCalculator.isGameOver()) {
                            for (Player player: players) {
                                player.clearHistory();
                            }
                        }
                    });
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                Bot.declarerDrop = savedDrop;
                Bot.standingTimeLimited = savedTimeLimited;
                Bot.standingSeed = savedSeed;
            }
        }
    }

    // run random rounds
    protected void runGame() {
        if (roundStage == RoundStage.dealing) {
            elderHand = new Random().nextInt(NOP);
            nextBidder = elderHand;
            newGame = true;
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
            for (Player player: players) {
                player.myHand.clear();
            }
        } while (!ScoreCalculator.isGameOver());
        printf("game ended\n");
        for (Player player: players) {
            player.clearHistory();
        }
        allPassFactor = 0;
        playedAllPass = false;
        // Main will continue launching games
    }

    // the all-pass factor after another all-pass, by Config.allPassSequence: x1 x1 stays 0,
    // x1 x2 x3 x1 x2 x3 cycles, x1 x2 x3 x3 x3 stays at 2; a made game resets it to 0 anyway
    int nextAllPassFactor(int factor) {
        switch (config().allPassSequence.get().getSelectedValue()) {
            case x1_x1:
                return 0;
            case x1_x2_x3_x3_x3:
                return Math.min(factor + 1, 2);
            default:
                return (factor + 1) % 3;
        }
    }

    Bid getBid(int playerNum) {
        return players[playerNum].getBid();
    }

    // for debug
    public void prepareTest(int declarerNum, Bid bid, CardList talonCards) {
        // the round before may have left its avatars in place, playRound() alone restores the players:
        // avatars4Round() keeps them then, a defender played his partner's cards in the next deal
        updateFromAvatars();
        CardSet discarded = CardSet.getDeck();
        for (Player p : players) {
            if (declarerNum != p.getNumber()) {
                discarded.remove(p.myHand);
            }
            discarded.remove(p.leftHand);
            discarded.remove(p.rightHand);
            if (p instanceof Bot) {
                ((Bot)p).targetBot = null;
            }
        }
        declarer = null;
        standing = false;
        cardsRevealed = false;      // a misère round left it set: the next laying round revealed nothing
        talonShown.clear();
        this.trick.clear(elderHand);
        allPassFactor = 0;
        playedAllPass = false;
        if (bid.equals(Bid.BID_ALL_PASS)) {
            this.declarerNumber = -1;
            this.declarer = null;
            this.minBid = bid;
            this.trick.minBid = null;
            return;
        }
        biddingPassCount = 0;
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
                talonShown.set(new CardSet(talonCards));
                this.declarer.takeTalon(talonCards);
                this.talonCards.clear();
            }
            this.declarerHand = new CardSet(this.declarer.myHand);
            if (declarerNum >= 0) {
                this.declarer.declareRound(bid, elderHand, null);
                this.declarer.bid = bid;
                if (!bid.equals(Bid.BID_MISERE)) {
                    ForTricksBot.getInstance().bid = bid;
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
        println("*******************************");
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
                    standing = false;
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
                        talonShown.set(new CardSet(talonCards));
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
                        printf("playing all-pass*%d\n", allPassFactor * ScoreCalculator.gameFactor());
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
            ScoreCalculator.calculate(param);
            ++lineCount;
            // the round scored, the all-pass factor follows it here: a replay chosen on the scores
            // screen skipped it, a game made with a whist and then replayed kept the factor
            if (minBid.equals(Bid.BID_ALL_PASS)) {
                playedAllPass = true;
                allPassFactor = nextAllPassFactor(allPassFactor);
            } else if (declarerNumber >= 0) {
                Bid bid = declarer.getBid();
                boolean forTricks = !bid.equals(Bid.BID_MISERE) && !bid.equals(Bid.BID_WITHOUT_THREE);
                boolean whist = players[(declarerNumber + 1) % NOP].getBid().isWhist() ||
                    players[(declarerNumber + 2) % NOP].getBid().isWhist();
                if (forTricks && whist && declarer.tricks >= bid.goal()) {
                    allPassFactor = 0;
                    playedAllPass = false;
                }
            }
        }
        if (eventObserver == null) {
            next = RestartCommand.newRound;
        } else if (next == null || next == RestartCommand.offer) {
            next = eventObserver.showScores();
            sleep(config().pauseBetweenRounds.get());
        }
        replayMode = RestartCommand.verify.equals(next) ||
            RestartCommand.replay.equals(next) && trick.getNumber() == 9;
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
                if (adjustMinBid && biddingPassCount == 1 && declarer != null &&
                        (nextBidder - elderHand + NOP) % NOP < (declarer.getNumber() - elderHand + NOP) % NOP &&
                        !(misereDeclared && Bid.BID_9S.equals(minBid))) {
                    // allow 'здесь' to the earlier of the two remaining bidders
                    minBid = minBid.prev();
                    if (savedBid.equals(Bid.BID_9S)) {
                        minBid = minBid.prev();
                    }
                }
                adjustMinBid = true;
                if (bidder instanceof HumanPlayer) {
                    update(null);
                }
                long start = System.currentTimeMillis();
                if (DEBUG_ALL_PASS) {
                    if (bidder instanceof HumanPlayer) {
                        bid = bidder.getBid(minBid, elderHand);
                    } else {
                        bidder.setBid(Bid.BID_6S);
                    }
                } else {
                    bid = bidder.getBid(minBid, elderHand);
                }
                if (bidder instanceof HumanPlayer) {
                    newGame = false;
                }
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
        this.deck = new CardList(deck);
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
            println(trick);
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
        // copies, no player shares its hands with another player or a bot singleton
        declarer.leftHand.set(left.myHand);
        declarer.rightHand.set(right.myHand);
        ForTricksBot.getInstance().leftHand.set(left.myHand);
        ForTricksBot.getInstance().rightHand.set(right.myHand);
        MisereBot.getInstance().leftHand.set(left.myHand);
        MisereBot.getInstance().rightHand.set(right.myHand);
        left.rightHand.set(declarerHand);
        left.leftHand.set(right.myHand);
        right.leftHand.set(declarerHand);
        right.rightHand.set(left.myHand);
    }

    // the player shows the hand: the others and the target bots know it, the list is built anew
    public void revealHand(int number) {
        CardSet hand = players[number].myHand;
        players[(number + 1) % NOP].rightHand.set(hand);
        players[(number + 2) % NOP].leftHand.set(hand);
        if (declarer == null) {
            return;     // all-pass
        }
        if (number == declarerNumber) {
            // no drop to guess
            declarerHand.set(hand);
            ForTricksBot.getInstance().myHand.set(hand);
            MisereBot.getInstance().myHand.set(hand);
        } else if ((number - declarerNumber + NOP) % NOP == 1) {
            ForTricksBot.getInstance().leftHand.set(hand);
            MisereBot.getInstance().leftHand.set(hand);
        } else {
            ForTricksBot.getInstance().rightHand.set(hand);
            MisereBot.getInstance().rightHand.set(hand);
        }
        TrickList.getInstance().initBuild(null);
    }

    private void incrementTricks() {
        Player p = players[trick.getTop()];
        p.incrementTricks();
        if (p == this.declarer) {
            ForTricksBot.getInstance().setTricks(p.getTricks());
            MisereBot.getInstance().setTricks(p.getTricks());
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
                if (standing) {
                    // each defender plays his own cards
                    for (Player defender : new Player[] {defender0, defender1}) {
                        avatars[defender.getNumber()] = defender instanceof HumanPlayer ?
                            new HumanPlayer(defender, clickable) : newBot(defender);
                    }
                } else if (defender0 instanceof HumanPlayer && defender0.getBid().equals(Config.Bid.BID_WHIST) ||
                        defender0 instanceof HumanPlayer && defender0.getBid().equals(Bid.BID_WHIST_LAYING) ||
                        defender1 instanceof HumanPlayer && defender1.getBid().equals(Config.Bid.BID_WHIST) ||
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
                if (config().stalingrad.get() && Bid.BID_6S.equals(this.minBid)) {
                    // Stalingrad: 6♠ is whisted by both defenders, nobody is asked
                    p1 = players[(declarer.getNumber() + 1) % NOP];
                    p2 = players[(declarer.getNumber() + 2) % NOP];
                    p1.setBid(Bid.BID_WHIST);
                    p2.setBid(Bid.BID_WHIST);
                    p1.halfWhist = false;
                    p2.halfWhist = false;
                    printf("Stalingrad: %s and %s whist\n", p1.getName(), p2.getName());
                    update(null);
                    // fall through to selectWhistOption: both whist standing
                } else {
                    update(RoundStage.whistSelection);
                    if (this.minBid.goal() < 10 || config().whistTotus.get()) {
                        p1 = players[(declarer.getNumber() + 1) % NOP];
                        p1.setBid(Bid.BID_UNDEFINED);
                        p2 = players[(declarer.getNumber() + 2) % NOP];
                        p2.setBid(Bid.BID_UNDEFINED);
                        long start = System.currentTimeMillis();
                        p1.respondOnDeclaration();
                        p2.respondOnDeclaration();
                        if (p2.getBid().equals(Bid.BID_HALF_WHIST)) {
                            // 2nd chance, p1 whists alone
                            p1.respondOnDeclaration();
                            if (p1.getBid().isWhist()) {
                                p2.setBid(Bid.BID_PASS);    // halfWhist stays: no gentleman's share for him
                                printf("%s whisting: %s\n", p2.getName(), p2.getBid());
                            }
                        }
                        update(null);
                        if (!p1.getBid().isWhist() && !p2.getBid().isWhist()) {
                            // both passed, or half-whist and pass
                            declarer.setTricks(declarer.getBid().goal());
                            return;
                        }
                        sleep(10);     // give jPrefPanel a chance to paint
                    } else {
                        update(RoundStage.selectWhistOption);
                    }
                }
                // fall through

            case selectWhistOption:
                if (this.minBid.goal() < 10 || config().whistTotus.get()) {
                    p1 = players[(declarer.getNumber() + 1) % NOP];
                    p2 = players[(declarer.getNumber() + 2) % NOP];
                    Player p = null;
                    if (p1.getBid().isWhist() && p2.getBid().isWhist()) {
                        // both whist: each plays his own cards
                        p1.setBid(Bid.BID_WHIST_STANDING);
                        p2.setBid(Bid.BID_WHIST_STANDING);
                    } else if (p1.getBid().equals(Bid.BID_WHIST)) {
                        p = p1;    // the single whister, he selects
                    } else if (p2.getBid().equals(Bid.BID_WHIST)) {
                        p = p2;    // the single whister, he selects
                    }
                    if (p != null && trick.getNumber() == 0 && trick.isEmpty() &&
                            trick.getTurn() == declarerNumber) {
                        // the declarer on the 1st lead plays his card before the single whister selects
                        // laying or standing; he sees no defender's cards for it, the cards are revealed after it
                        update(RoundStage.play);
                        sleep(100);     // give jPrefPanel a chance to paint
                        Card card = declarer.play(trick);
                        if (card == null) {
                            // sanity check
                            throw new RuntimeException(String.format("declarer %d, %s, trick %s", declarerNumber, declarer, trick));
                        }
                        trick.add(card);
                        if (declarer instanceof Bot) {
                            sleep(config().pauseBetweenMoves.get());
                        }
                        update(RoundStage.selectWhistOption);
                    }
                    if (p instanceof HumanPlayer) {
                        update(RoundStage.selectWhistOption);
                        sleep(100);     // give jPrefPanel a chance to paint
                        standing = p.playWhistLaying();
                        sleep(100);     // give jPrefPanel a chance to paint
                    } else if (p != null) {
                        standing = p.playWhistLaying();    // a bot plays standing when he has something to hide
                    }
                    standing = p1.getBid().equals(Bid.BID_WHIST_STANDING) ||
                        p2.getBid().equals(Bid.BID_WHIST_STANDING);
                    if (standing && p != null && eventObserver != null && trick.isEmpty()) {
                        // one defender only, a bot whister chose it, the passer on the 1st lead;
                        // a whister knows it already; both whisters play standing anyway;
                        // the declarer on the 1st lead has played his card already, he sees the closed cards;
                        // otherwise the user sees it by the closed cards before his move
                        for (Player player : players) {
                            if (player instanceof HumanPlayer && !player.getBid().isWhist() &&
                                    player.getNumber() == elderHand) {
                                update(RoundStage.play);
                                eventObserver.showMessage("Whist", "Whist standing", "Continue");
                            }
                        }
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
                    if (c == 0 && trick.size() == 1 && trick.getStartedBy() == declarerNumber && !standing &&
                            !cardsRevealed) {
                        revealCards();      // laying selected after the declarer's 1st card
                    }
                    for (int j = trick.size(); j < players.length; ++j) {
                        Player player = players[trick.getTurn()];
                        Card card;
                        if (c == 0 && j == 0 && player != declarer && !standing) {
                            revealCards();
                        }
                        card = player.play(trick);
                        if (card == null) {
                            // sanity check
                            throw new RuntimeException(String.format("player %d, %s, trick %s", player.number, player, trick));
                        }
                        trick.add(card);
                        lastPlayer = player;
                        if (c == 0 && j == 0 && player == declarer && !standing) {
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
        // the defenders' bids and the avatars before the play starts, also when the round comes
        // declared, a fixed-bid test at whistSelection: skipped, the defenders kept the bids and
        // the avatars of the round before and played each other's cards in the next one
        if (roundStage.compareTo(RoundStage.play) < 0) {
            if (roundStage.equals(RoundStage.showTalon) ||
                    roundStage.equals(RoundStage.declareRound)) {
                declarer.declareRound(minBid, elderHand, null);
            }
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
            // leave trickTaken, otherwise a human leading the trick cannot drag a card
            update(RoundStage.play);
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
                if (player instanceof Bot) {
                    sleep(config().pauseBetweenMoves.get());
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
        // blocks until a button is clicked, returns its index
        int showMessage(String title, String text, String... buttons);
    }
}