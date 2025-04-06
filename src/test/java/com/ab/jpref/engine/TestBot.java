/*
     Copyright (C) 2024-2025	Alexander Bootman, alexbootman@gmail.com
 *
 * Created by Alexander Bootman on 12/22/2024.
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Config;
import com.ab.util.Logger;
import com.ab.util.Util;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class TestBot {
    static final Config config = Config.getInstance();
    static GameManager gameManager;

    @BeforeClass
    public static void initClass() {
        Logger.set(System.out);
        gameManager = new GameManager(config, null, playerFactory());
        GameManager.DEBUG = false;  // suppress thread status logginga
    }

    private static GameManager.PlayerFactory playerFactory() {
        return index -> new Bot("test", index);
    }

    @Test
    public void testGetMaxBid() throws IOException {
        GameManager.DEBUG = false;  // suppress thread status logging
        Util.getList("etc/tests/get-max-bid",
            (res, tokens) -> {
                CardList cards = new CardList();
                int i;
                for (i = 0; cards.size() < 10; ++i) {
                    cards.addAll(Util.toCardList(tokens.get(i)));
                }
                int turn = Integer.parseInt(tokens.get(i++));
                Config.Bid leftBid = Config.Bid.fromName(tokens.get(i++));
                Bot player = new Bot("test", cards);
//            Config.Bid bid = player.getMaxBid(turn, leftBid, rightBid, minBid);
                Config.Bid bid = player.getMaxBid(turn == 0);
                String[] parts = res.split(" ");
                Config.Bid expectedBid = Config.Bid.fromName(parts[0]);
                Assert.assertEquals(expectedBid, bid);
            });
    }

/*
    @Test
    public void testDeclareGame() throws IOException {
//        File f = new File("x");
//        System.out.printf("%s\n", f.getAbsolutePath());
        Util.getList("etc/tests/declare-game",
                    (res, tokens) -> {
            String[] parts = res.split(", ");
            Config.Bid expectedBid = Config.Bid.fromName(parts[0]);
            Config.Bid minBid = Config.Bid.BID_6S;
            if (expectedBid.equals(Config.Bid.BID_MISERE)) {
                minBid = Config.Bid.BID_MISERE;
            }
            CardList cards = new CardList();
            int i;
            for (i = 0; cards.size() < 12; ++i) {   // including talon
                cards.addAll(Util.toCardList(tokens.get(i)));
            }
            int turn = Integer.parseInt(tokens.get(i++));
            Logger.printf("%s %d -> %s\n", cards.toString(), turn, res);
            Bot player = new Bot("test", cards);
            Bot.RoundData roundData =
                    player.declareRound(minBid, turn);
            Assert.assertEquals(expectedBid, roundData.bid);
            //todo: check discards
        });
    }
*/

    @Test
    public void testDiscard4Misere() throws IOException {
        GameManager.DEBUG = false;  // suppress thread status logging
        Util.getList("etc/tests/declare-misere",
            (res, tokens) -> {
                CardList cards = new CardList();
                int i;
                for (i = 0; cards.size() < 10; ++i) {
                    cards.addAll(Util.toCardList(tokens.get(i)));
                }
                boolean elderHand = Integer.parseInt(tokens.get(i++)) == 0;
                Bot bot = new Bot("bid", cards);
                String botHand = bot.toString();
                boolean misereOK = bot.evalMisere(elderHand);
                Assert.assertTrue(String.format("no misere %s %b", botHand, elderHand), misereOK);
                for (; cards.size() < 12; ++i) {
                    cards.addAll(Util.toCardList(tokens.get(i)));
                }
                bot = new Bot("declare", cards);
                Bot.SuitResults suitResults = bot.discardForMisere(elderHand);
                Logger.printf("%s %b -> %s, eval=%d\n",
                    bot.toString(), elderHand, suitResults.discarded.toString(), suitResults.eval);
                if (suitResults.eval > 0) {
                    String[] parts = res.split(" #");
                    Set<Card> expected = new HashSet<>(Util.toCardList(parts[0]));
                    for (Card card : suitResults.discarded) {
                        Assert.assertTrue(String.format("%s %b -> discarded %s", botHand, elderHand, card),
                            expected.contains(card));
                    }
                }
            });
    }
}