/*
     Copyright (C) 2025-2026	Alexander Bootman, alexbootman@gmail.com
 *
 * Randomized crash-hunting for the game engine. These tests don't check trick counts or
 * bidding outcomes (unlike TestGameManager's fixed-deal tests) - they only check that a full
 * round can be played to completion without throwing. Every iteration uses a seed derived from
 * a fixed base seed, so any failure prints everything needed to replay that exact deal.
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Config;
import com.ab.util.Logger;

import static com.ab.util.Logger.printf;
import static com.ab.util.Logger.println;
import static com.ab.util.Util.currMethodName;

import org.junit.Assert;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class TestStress extends BaseTest {
    private static final int NOP = Config.NOP;

    // every non-misere/non-all-pass declare bid, deliberately including levels far beyond what
    // any real hand would justify - the point is to stress the search on hand shapes it would
    // never naturally see, not to model plausible bidding
    private static final Config.Bid[] FOR_TRICKS_BIDS = {
        Config.Bid.BID_6S, Config.Bid.BID_6C, Config.Bid.BID_6D, Config.Bid.BID_6H, Config.Bid.BID_6N,
        Config.Bid.BID_7S, Config.Bid.BID_7C, Config.Bid.BID_7D, Config.Bid.BID_7H, Config.Bid.BID_7N,
        Config.Bid.BID_8S, Config.Bid.BID_8C, Config.Bid.BID_8D, Config.Bid.BID_8H, Config.Bid.BID_8N,
        Config.Bid.BID_9S, Config.Bid.BID_9C, Config.Bid.BID_9D, Config.Bid.BID_9H, Config.Bid.BID_9N,
        Config.Bid.BID_XS, Config.Bid.BID_XC, Config.Bid.BID_XD, Config.Bid.BID_XH, Config.Bid.BID_XN,
    };

    private List<String> failures;

    private GameManager freshGameManager() {
        GameManager gameManager = new GameManager();
        gameManager.init(host);
        GameManager.DEBUG_LOG = false;
        config.pauseBetweenRounds.set(0);
        Bot.targetBot = null;
        Bot.debugDrop = null;
        new TrickList();
        return gameManager;
    }

    private CardList shuffledDeck(long seed) {
        CardList deck = CardList.getDeck();
        Collections.shuffle(deck, new Random(seed));
        return deck;
    }

    // runs `action`, and on any Throwable records a full repro (label + seed + deck + stack
    // trace) instead of failing immediately, so one bad seed doesn't cut the sweep short and
    // hide other, independent failures later in the run
    private void safeRun(String label, long seed, CardList deck, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String repro = String.format(
                "%s%nseed=%d%ndeck=%s%n%s",
                label, seed, deck == null ? "?" : deck.toString(), sw);
            Logger.println("STRESS FAILURE: " + repro);
            failures.add(repro);
        }
    }

    private void reportAndClear(String suiteName, int ran) {
        printf("%s: ran %d, failures %d\n", suiteName, ran, failures.size());
        if (!failures.isEmpty()) {
            Assert.fail(String.format("%s: %d/%d iterations failed:%n%s",
                suiteName, failures.size(), ran, String.join("\n----\n", failures)));
        }
    }

    // --- natural play: real bidding, real (non-forced) drop guessing, full random hands -----

    @Test
    public void stressNaturalBidding() {
        println("running: " + currMethodName());
        failures = new ArrayList<>();
        Random seeds = new Random(1);
        int iterations = 1500;
        for (int i = 0; i < iterations; ++i) {
            long seed = seeds.nextLong();
            int elderHand = i % NOP;
            // vary the deadline: mostly the default, sometimes tight enough to truncate a
            // search, sometimes generous - both ends of the range matter for crash-hunting
            int maxMoveTime = new int[]{1, 1, 2, 3, 5}[i % 5];
            CardList deck = shuffledDeck(seed);
            safeRun("stressNaturalBidding#" + i + " elderHand=" + elderHand + " maxMoveTime=" + maxMoveTime,
                seed, deck, () -> {
                    GameManager gameManager = freshGameManager();
                    config.maxMoveTime.set(maxMoveTime);
                    gameManager.elderHand = elderHand;
                    gameManager.deal(deck);
                    gameManager.prepareTest(-1, Config.Bid.BID_6S, null);
                    gameManager.playRound(deck);
                    checkInvariants(gameManager);
                });
        }
        reportAndClear("stressNaturalBidding", iterations);
    }

    // --- forced bids: every suit/level, random hands, playRoundForTricks ---------------------

    @Test
    public void stressForcedForTricksBids() {
        println("running: " + currMethodName());
        failures = new ArrayList<>();
        Random seeds = new Random(2);
        int dealsPerBid = 8;
        int ran = 0;
        for (Config.Bid bid : FOR_TRICKS_BIDS) {
            for (int d = 0; d < dealsPerBid; ++d) {
                long seed = seeds.nextLong();
                CardList deck = shuffledDeck(seed);
                for (int declarerNum = 0; declarerNum < NOP; ++declarerNum) {
                    ++ran;
                    final int declarerNumF = declarerNum;
                    int elderHand = (declarerNum + d) % NOP;
                    int maxMoveTime = 1;    // keep this sweep fast; deadline behavior itself is covered elsewhere
                    safeRun("stressForcedForTricksBids bid=" + bid + " declarer=" + declarerNum
                            + " elderHand=" + elderHand, seed, deck, () -> {
                        GameManager gameManager = freshGameManager();
                        config.maxMoveTime.set(maxMoveTime);
                        CardList talon = new CardList(deck.subList(30, 32));
                        Bot.debugDrop = new CardList(talon);
                        gameManager.elderHand = elderHand;
                        gameManager.deal(deck);
                        gameManager.prepareTest(declarerNumF, bid, talon);
                        gameManager.playRoundForTricks();
                        checkInvariants(gameManager);
                    });
                }
            }
        }
        reportAndClear("stressForcedForTricksBids", ran);
    }

    // --- forced misere: random hands, both declarerDrop strategies --------------------------

    @Test
    public void stressForcedMisere() {
        println("running: " + currMethodName());
        failures = new ArrayList<>();
        Random seeds = new Random(3);
        int deals = 120;
        int ran = 0;
        for (int d = 0; d < deals; ++d) {
            long seed = seeds.nextLong();
            CardList deck = shuffledDeck(seed);
            for (int declarerNum = 0; declarerNum < NOP; ++declarerNum) {
                final int declarerNumF = declarerNum;
                for (MisereBot.DeclarerDrop dropStrategy : MisereBot.DeclarerDrop.values()) {
                    ++ran;
                    int elderHand = (declarerNum + d) % NOP;
                    safeRun("stressForcedMisere declarer=" + declarerNum + " elderHand=" + elderHand
                            + " drop=" + dropStrategy, seed, deck, () -> {
                        GameManager gameManager = freshGameManager();
                        config.maxMoveTime.set(20);
                        MisereBot.declarerDrop = dropStrategy;
                        CardList talon = new CardList(deck.subList(30, 32));
                        Bot.debugDrop = new CardList(talon);
                        gameManager.elderHand = elderHand;
                        gameManager.deal(deck);
                        gameManager.prepareTest(declarerNumF, Config.Bid.BID_MISERE, talon);
                        gameManager.playRoundMisere();
                        checkInvariants(gameManager);
                    });
                }
            }
        }
        reportAndClear("stressForcedMisere", ran);
    }

    // --- forced all-pass: random hands --------------------------------------------------------

    @Test
    public void stressAllPass() {
        println("running: " + currMethodName());
        failures = new ArrayList<>();
        Random seeds = new Random(4);
        int iterations = 500;
        for (int i = 0; i < iterations; ++i) {
            long seed = seeds.nextLong();
            int turn = i % NOP;
            CardList deck = shuffledDeck(seed);
            safeRun("stressAllPass#" + i + " turn=" + turn, seed, deck, () -> {
                GameManager gameManager = freshGameManager();
                gameManager.getTrick().clear(turn);
                gameManager.elderHand = turn;
                gameManager.deal(deck);
                gameManager.playRoundAllPass();
                checkInvariants(gameManager);
            });
        }
        reportAndClear("stressAllPass", iterations);
    }

    // --- maxMoveTime edge values --------------------------------------------------------------

    @Test
    public void stressMaxMoveTimeEdgeValues() {
        println("running: " + currMethodName());
        failures = new ArrayList<>();
        int[] values = {0, -1, 1, 2};
        Random seeds = new Random(5);
        int ran = 0;
        for (int maxMoveTime : values) {
            for (int d = 0; d < 5; ++d) {
                ++ran;
                long seed = seeds.nextLong();
                CardList deck = shuffledDeck(seed);
                int elderHand = d % NOP;
                safeRun("stressMaxMoveTimeEdgeValues maxMoveTime=" + maxMoveTime, seed, deck, () -> {
                    GameManager gameManager = freshGameManager();
                    config.maxMoveTime.set(maxMoveTime);
                    gameManager.elderHand = elderHand;
                    gameManager.deal(deck);
                    gameManager.prepareTest(-1, Config.Bid.BID_6S, null);
                    gameManager.playRound(deck);
                    checkInvariants(gameManager);
                });
            }
        }
        reportAndClear("stressMaxMoveTimeEdgeValues", ran);
    }

    // sanity checks beyond "didn't throw": catches silent corruption (e.g. a truncated search
    // leaving a player holding the wrong number of cards, or tricks not summing to a full round)
    private void checkInvariants(GameManager gameManager) {
        int totalTricks = 0;
        for (Player p : gameManager.getPlayers()) {
            Assert.assertEquals("player " + p.getNumber() + " ended with cards left: " + p.toColorString(),
                0, p.myHand.size());
            totalTricks += p.getTricks();
        }
        if (gameManager.getDeclarer() != null && !Config.Bid.BID_ALL_PASS.equals(gameManager.getMinBid())) {
            Assert.assertEquals("tricks don't add up to a full round", Config.ROUND_SIZE, totalTricks);
        }
    }
}
