/*
     Copyright (C) 2024-2025	Alexander Bootman, alexbootman@gmail.com
 *
 * Created by Alexander Bootman on 12/22/2024.
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Config;
import com.ab.util.Logger;
import com.ab.util.Util;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

public class TestBot {
    static Config config = Config.getInstance();
    static GameManager gameManager;

    @BeforeClass
    public static void initClass() {
        Logger.set(System.out);
        gameManager = new GameManager(config, null, playerFactory());
        GameManager.DEBUG = false;  // suppress thread status logginga
    }

    private static GameManager.PlayerFactory playerFactory() {
        return new GameManager.PlayerFactory() {
            @Override
            public Player getPlayer(int index) {
                return new Bot("" + index);
            }
        };
    }

    @Test
    public void testGetMaxBid() throws IOException {
        GameManager.DEBUG = false;  // suppress thread status logginga
        Util.getList("etc/tests/get-max-bid",
                (res, tokens) -> {
            CardList cards = new CardList();
            int i;
            for (i = 0; cards.size() < 10; ++i) {
                cards.addAll(Util.toCardList(tokens.get(i)));
            }
            int turn = Integer.parseInt(tokens.get(i++));
            Config.Bid leftBid = Config.Bid.fromName(tokens.get(i++));
            Config.Bid rightBid = Config.Bid.fromName(tokens.get(i++));
            Config.Bid minBid = Config.Bid.BID_6S;
            Bot player = new Bot("test", cards);
            System.out.printf("%s %d %s %s \n", player, turn, leftBid, rightBid);
//            Config.Bid bid = player.getMaxBid(turn, leftBid, rightBid, minBid);
            Config.Bid bid = player.getMaxBid(turn);
            String[] parts = res.split(" ");
            Config.Bid expectedBid = Config.Bid.fromName(parts[0]);
            Assert.assertEquals(expectedBid, bid);
        });
    }

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

    @Test
    public void testDiscard() {
        String[] sources = {
        };
        // ??
    }

}