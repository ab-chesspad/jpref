/*
     Copyright (C) 2025-2026	Alexander Bootman, alexbootman@gmail.com
 *
 * Created by Alexander Bootman on 1/25/2025.
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Config;

import static com.ab.util.Logger.printf;
import static com.ab.util.Logger.println;

import com.ab.util.Logger;
import com.ab.util.SimpleLongIntMap;

import static com.ab.util.Util.DEAL_MARK;
import static com.ab.util.Util.currMethodName;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;

public class TestGameManager extends BaseTest {
    public static final int NOP = Config.NOP;

    static GameManager gameManager;
    static TrickList trickList;

    @Before
    public void init() {
        trickList = new TrickList();
        gameManager = new GameManager();
        gameManager.init(host);
        GameManager.DEBUG_LOG = false;      // suppress thread status logginga
        config.pauseBetweenRounds.set(0);
    }

    private void printStatistics(int count) {
        SimpleLongIntMap.printStatistics();
        printf("done %d tests, maxTreeBuildTime %d msec, maxSimilar %,d, maxPoolCount %,d\n", count,
            TrickList.maxListBuildTime, TrickList.maxSimilar, TrickList.maxPoolCount);
    }

    private void printTricks() {
        String sep = "results: ";
        for (Player p : gameManager.getPlayers()) {
            printf("%s%s: %d", sep, p.getName(), p.getTricks());
            sep = ", ";
        }
        println();
    }

    private InputStream getInputStream(String testFileName) {
        String path = "../etc/tests/" + testFileName;
        File f = new File(path);
        Logger.println(f.getAbsolutePath());
        try {
            return new FileInputStream(path);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    // enforce bid but not drop
    public void testFixedBid() throws IOException {
        println("running: " + currMethodName());
        final int[] count = {0};
        final InputStream testInputStream = getInputStream("fixedbid");
        gameManager.testInputStream = testInputStream;    // just to avoid duplicate line print

        util.getList(testInputStream,
            (res, tokens) -> {
                if (!tokens.get(0).startsWith(DEAL_MARK)) {
                    return;     // ignore
                }
                String[] resParts = res.split("\\s+");
                int _declarerNum = 0;
                int i = 0;
                try {
                    _declarerNum = Integer.parseInt(resParts[i]);
                    ++i;
                } catch (NumberFormatException e) {
                    // ignore
                }
                int declarerTricks = 0;
                Config.Bid expectedBid = Config.Bid.fromName(resParts[i]);
                if (!Config.Bid.BID_ALL_PASS.equals(expectedBid)) {
                    declarerTricks = Integer.parseInt(resParts[i + 1]);
                }
                int _elderHand = Integer.parseInt(tokens.get(tokens.size() - 1));
                CardList _deck = new CardList();
                for (String token : tokens) {
                    if (token.endsWith(":")) {
                        continue;
                    }
                    _deck.addAll(util.toCardList(token));
                }
                _deck.verifyDeck();
                i = 0;
                int declarerNum = (_declarerNum + i) % NOP;
                printf("declarer #%d\n", declarerNum);
                int elderHand = (_elderHand + declarerNum) % NOP;
                CardList deck = new CardList(_deck);
                CardList talon = new CardList(deck.subList(30, 32));
                gameManager.elderHand = elderHand;
                gameManager.deal(deck);
                gameManager.prepareTest(declarerNum, expectedBid, talon);
                if (Config.Bid.BID_ALL_PASS.equals(expectedBid)) {
                    gameManager.playRoundAllPass();
                } else if (gameManager.getMinBid().equals(Config.Bid.BID_MISERE)) {
                    gameManager.playRoundMisere();
                } else {
                    gameManager.playRoundForTricks();
                }
                printTricks();
                if (!Config.Bid.BID_ALL_PASS.equals(expectedBid)) {
                    Assert.assertEquals("wrong bid", expectedBid, gameManager.getDeclarer().bid);
                    if (!gameManager.getMinBid().equals(Config.Bid.BID_MISERE)) {
                        Assert.assertEquals("wrong tricks", declarerTricks, gameManager.getDeclarer().getTricks());
                    }
                }
            });
        printStatistics(count[0]);
    }

    @Test
    // enforce bid and drop
    public void testFixedPlay() throws IOException {
        println("running: " + currMethodName());
        playFixed("fixedplay");
    }

    @Test
    // like testFixedPlay, deals with the most expensive TrickList builds, run with -Dslow=true
    public void testStressPlay() throws IOException {
        println("running: " + currMethodName());
        Assume.assumeTrue(Boolean.getBoolean("slow"));
        playFixed("stressplay");
    }

    private void playFixed(String testFileName) throws IOException {
        final int[] count = {0};
        final InputStream testInputStream = getInputStream(testFileName);
        gameManager.testInputStream = testInputStream;    // just to avoid duplicate line print

        util.getList(testInputStream,
            (res, tokens) -> {
                if (!tokens.get(0).startsWith(DEAL_MARK)) {
                    return;     // ignore
                }
                String[] resParts = res.split("\\s+|#");
                int declarerNum = 0;
                int i = 0;
                try {
                    declarerNum = Integer.parseInt(resParts[i]);
                    ++i;
                } catch (NumberFormatException e) {
                    // ignore
                }
                int declarerTricks = 0;
                Config.Bid expectedBid = Config.Bid.fromName(resParts[i]);
                if (!Config.Bid.BID_ALL_PASS.equals(expectedBid)) {
                    declarerTricks = Integer.parseInt(resParts[i + 1]);
                }
                int _elderHand = Integer.parseInt(tokens.get(tokens.size() - 1));
                CardList deck = new CardList();
                for (String token : tokens) {
                    if (token.endsWith(":")) {
                        continue;
                    }
                    deck.addAll(util.toCardList(token));
                }
                deck.verifyDeck();
                CardList _talonCards = new CardList(deck.subList(30, 32));
                Bot.debugDrop = new CardList(_talonCards);
                printf("declarer #%d\n", declarerNum);
                int elderHand = _elderHand;
                CardList talon = new CardList(deck.subList(30, 32));
                gameManager.elderHand = elderHand;
                gameManager.deal(deck);
                gameManager.prepareTest(declarerNum, expectedBid, talon);
                if (Config.Bid.BID_ALL_PASS.equals(expectedBid)) {
                    gameManager.playRoundAllPass();
                } else if (gameManager.getMinBid().equals(Config.Bid.BID_MISERE)) {
                    gameManager.playRoundMisere();
                } else {
                    gameManager.playRoundForTricks();
                }
                printTricks();
                if (!Config.Bid.BID_ALL_PASS.equals(expectedBid)) {
                    Assert.assertEquals("wrong bid", expectedBid, gameManager.getDeclarer().bid);
                    if (gameManager.getMinBid().equals(Config.Bid.BID_MISERE)) {
                        Assert.assertEquals("wrong misere res", declarerTricks > 0, gameManager.getDeclarer().getTricks() > 0);
                    } else {
                        Assert.assertEquals("wrong tricks", declarerTricks, gameManager.getDeclarer().getTricks());
                    }
                }

                for (Player player : gameManager.getPlayers()) {
                    player.clearHistory();
                }
                ++count[0];
            });
        printStatistics(count[0]);
    }


    @Test
    // let players bid and play the highest bid
    public void testFreePlay() throws IOException {
        println("running: " + currMethodName());
        final int[] count = {0};
        final InputStream testInputStream = getInputStream("freeplay");
        gameManager.testInputStream = testInputStream;    // just to avoid duplicate line print

        util.getList(testInputStream,
            (res, tokens) -> {
                if (!tokens.get(0).startsWith(DEAL_MARK)) {
                    return;     // ignore
                }
                int _elderHand = Integer.parseInt(tokens.get(tokens.size() - 1));     // 0-based
                CardList deck = new CardList();
                for (String token : tokens) {
                    if (token.endsWith(":")) {
                        continue;
                    }
                    deck.addAll(util.toCardList(token));
                }
                deck.verifyDeck();
//                CardList _talonCards = new CardList(deck.subList(30, 32));
                String[] resParts = res.split("\\s+|#");
                int i = 0;
                int declarerNum = 0;
                if (resParts[0].length() == 1 && Character.isDigit(resParts[0].charAt(0))) {
                    declarerNum = Integer.parseInt(resParts[0]);
                    i = 1;
                }
                int declarerTricks = 0;
                Config.Bid expectedBid = Config.Bid.fromName(resParts[i]);
                if (!Config.Bid.BID_ALL_PASS.equals(expectedBid)) {
                    declarerTricks = Integer.parseInt(resParts[i + 1]);
                }
                printf("declarer #%d\n", declarerNum);
                int elderHand = _elderHand;
                gameManager.deal(deck);
                gameManager.elderHand = elderHand;
                gameManager.prepareTest(-1, Config.Bid.BID_6S, null);
                gameManager.playRound(deck);
                if (expectedBid.equals(Config.Bid.BID_ALL_PASS)) {
                    Assert.assertEquals("wrong bid all-pass", expectedBid, gameManager.getMinBid());
                } else {
                    Assert.assertNotNull("wrong bidding, All-pass", gameManager.getDeclarer());
                    Assert.assertEquals("wrong bid", expectedBid, gameManager.getDeclarer().bid);
                    if (gameManager.getMinBid().equals(Config.Bid.BID_MISERE)) {
                        Assert.assertEquals("wrong misere res", declarerTricks > 0, gameManager.getDeclarer().getTricks() > 0);
                    } else {
                        Assert.assertEquals("wrong tricks", declarerTricks, gameManager.getDeclarer().getTricks());
                    }
                }
                gameManager.roundStage = GameManager.RoundStage.dealing;
                gameManager.setMinBid(Config.Bid.BID_6S);
                ++count[0];
            });
        printStatistics(count[0]);
    }

    @Test
    public void testMisere() throws IOException {
        println("running: " + currMethodName());
        final int[] count = {0};
        final InputStream testInputStream = getInputStream("misereplay");
        gameManager.testInputStream = testInputStream;    // just to avoid duplicate line print

        util.getList(testInputStream,
            (res, tokens) -> {
                if (!tokens.get(0).startsWith(DEAL_MARK)) {
                    return;     // ignore
                }
                String[] resParts0 = res.split("\\s+:\\s+|\\s+#\\s+");
                String[] resParts = resParts0[0].split("\\s+|#");
                int tot = 2;    // delayed drop, drop 1st, last
                boolean clean = false;
                int _elderHand = Integer.parseInt(tokens.get(tokens.size() - 1));     // 0-based
                CardList _deck = new CardList();
                for (String token : tokens) {
                    if (token.endsWith(":")) {
                        continue;
                    }
                    _deck.addAll(util.toCardList(token));
                }
                _deck.verifyDeck();
                CardList _talonCards = new CardList(_deck.subList(30, 32));
                for (int declarerNum = 0; declarerNum < NOP; ++declarerNum) {
                    printf("declarer #%d\n", declarerNum);
                    int elderHand = (_elderHand + declarerNum) % NOP;
                    CardList deck = new CardList();
                    for (int j = 0; j < NOP; ++j) {
                        int k = 10 * ((j - declarerNum + NOP) % NOP);
                        deck.addAll(_deck.subList(k, k + 10));
                    }
                    deck.addAll(new CardList(_talonCards));
                    for (int i = 0; i < tot; ++i) {
                        CardList talonCards = new CardList(_talonCards);
                        MisereBot.declarerDrop = MisereBot.DeclarerDrop.values()[i];
                        gameManager.getTrick().clear(elderHand);
                        gameManager.elderHand = elderHand;
                        gameManager.deal(deck);
                        if (resParts0.length >= 3) {
                            CardList cardList = util.toCardList(resParts0[1]);
                            Bot.debugDrop = new CardList(cardList.subList(0, 2));
                        } else {
                            Bot.debugDrop = new CardList(deck.subList(30, 32));
                        }
                        gameManager.prepareTest(declarerNum, Config.Bid.BID_MISERE, talonCards);
                        gameManager.playRoundMisere();
                        printTricks();
                        Assert.assertEquals("wrong result", clean, gameManager.getPlayers()[declarerNum].getTricks() == 0);
                    }
                    for (Player player: gameManager.getPlayers()) {
                        player.clearHistory();
                    }
                }
                ++count[0];
            });
        printStatistics(count[0]);
    }

    @Test
    public void testAllPass() throws IOException {
        println("running: " + currMethodName());
        final int[] count = {0};

        final InputStream testInputStream = getInputStream("allpassplay");
        gameManager.testInputStream = testInputStream;    // just to avoid duplicate line print
        util.getList(testInputStream,
            (res, tokens) -> {
                if (!tokens.get(0).startsWith(DEAL_MARK)) {
                    return;     // ignore
                }
                int turn = Integer.parseInt(tokens.get(tokens.size() - 1));     // 0-based
                CardList deck = new CardList();
                for (String token : tokens) {
                    if (token.endsWith(":")) {
                        continue;
                    }
                    deck.addAll(util.toCardList(token));
                }
                gameManager.getTrick().clear(turn);
                gameManager.deal(deck);
                gameManager.playRoundAllPass();
                int i = res.indexOf("#");
                if (i > 0) {
                    res = res.substring(0, i);
                }
                res = res.trim();
                String actualRes = String.format("%d, %d, %d"
                    , gameManager.getPlayers()[0].getTricks()
                    , gameManager.getPlayers()[1].getTricks()
                    , gameManager.getPlayers()[2].getTricks());
                Assert.assertEquals(String.format("err: \"%s\" != \"%s\"", res, actualRes), res, actualRes);
                ++count[0];
            });
        printf("passed %d tests\n", count[0]);
    }

    @Test
    @Ignore("postpone for android code")
    public void testAndroidGame() {
        println("running: " + currMethodName());
        final int[] count = {0};
        InputStream testInputStream;

        String path = "../DPref/app/src/main/assets/tests/fixedplay";
        File f = new File(path);
        Logger.println(f.getAbsolutePath());
        try {
            testInputStream = new FileInputStream(path);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        try {
            gameManager.runGame(testInputStream, 0);
            String t = config.info();
            String text = t + "\n" + String.format("maxBuildTime=%,d msec, maxPositions=%,d, maxMapSize=%,d\n",
                TrickList.maxListBuildTime, TrickList.maxPositions, SimpleLongIntMap.maxSize);
            println(text);
        } catch (Config.PrefExceptionRerun e) {
            // ignore
        }

    }
}