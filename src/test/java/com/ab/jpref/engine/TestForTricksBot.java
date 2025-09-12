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
 * Created: 9/4/25
 */

package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.util.Logger;
import com.ab.util.Util;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestForTricksBot {
    static final Config config = Config.getInstance();
    static GameManager gameManager = GameManager.getInstance();

    @BeforeClass
    public static void initClass() {
        Logger.set(System.out);
    }

    @Test
    @Ignore
    public void testToSuitChunks() {
        final String[] sources = {
            "♠AJX97 ♣AK87 ♦AX ♥A -> [5EBA♠, 4ED♣, 2EA♦, 1E♥]",     // 5E4ED2E1E
            "♣QJ97 ♦XJ ♥QJ87 -> [4CB♥, 4CB♣, 2BA♦, 2♦]",
            "♠KQ97 ♣K8 ♦QX ♥KX -> [4DC♠, 3DA♥, 3CA♦, 2D♣]",
            "♠7 ♣8 ♦X ♥789XJQKA -> [8EDCBA♥, 2A♦, 1♣, 1♠]",
            "♠8 ♦X ♥789XJQKA -> [8EDCBA♥, 2A♦, 1♠, 1♦]",
        };
        for (String src : sources) {
            String parts[] = src.split(" -> ");
            CardSet hand = new CardSet(Util.toCardList(parts[0]));
            List<Integer> added = new ArrayList<>();
//            List<String> handChunks = BidData.toSuitChunks(hand, added);
//            Logger.println(Arrays.toString(handChunks.toArray()));
//            Assert.assertEquals(parts[1], Arrays.toString(handChunks.toArray()));
        }

    }

    @Test
    public void testDeclareGame() throws IOException {
        Util.getList("etc/tests/declare-game",
            (res, tokens) -> {
                String[] parts = res.split(", ");
                Card drop0 = new Card(parts[0]);
                Card drop1 = new Card(parts[1]);
                Config.Bid expectedBid = Config.Bid.fromName(parts[2]);

                int _elderHand = 0;
                CardList hand = new CardList();
                Config.Bid minBid = Config.Bid.BID_PASS;
                for (String token : tokens) {
                    try {
                        _elderHand = Integer.parseInt(token);
                        break;
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                    Config.Bid bid = Config.Bid.fromName(token);
                    if (bid != null) {
                        minBid = bid;
                        continue;
                    }
                    hand.addAll(Util.toCardList(token));
                }
                Assert.assertEquals(12, hand.size());
                ForTricksBot bot = new ForTricksBot();
                bot.myHand = new CardSet(hand);
                Player.PlayerBid playerBid = bot.declareRound(minBid, _elderHand);
                Config.Bid bid = playerBid.toBid();
                Assert.assertEquals(expectedBid, bid);
                if (!bid.equals(Config.Bid.BID_PASS)) {
                    Assert.assertTrue(String.format("%s not found", drop0.toColorString()), playerBid.drops.contains(drop0));
                    Assert.assertTrue(String.format("%s not found", drop1.toColorString()), playerBid.drops.contains(drop1));
                }
            });
    }
}
