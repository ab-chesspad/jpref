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
 * Copyright (C) 2025-2026 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 9/4/2025
 */

package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.util.Logger;
import com.ab.util.ScoreCalculator;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestScoreCalculator {
    public static final int NOP = Config.NOP;
    static final Config config = Config.getInstance();
    final int TEST_POOL_SIZE = 20;

    @BeforeClass
    public static void initClass() {
        GameManager.DEBUG_LOG = false;      // suppress thread status logginga
        config.pauseBetweenRounds.set(0);
    }

    @Test
    public void testScoreCalculator() {
        String[] sources = {
            // for each: {bid, tricks} -> results
            "p 7 p 2 p 1 -> -37 13 24",
            "6 5 w 3 w 2 -> -28 14 14",   // ?
            "6 5 w 2 w 3 -> -28 12 16",   // ?
            "9 9 w 1 w 0 -> 46 -18 -28",
            "9 10 w 0 w 0 -> 80 0 -80",
            "10 10 w 0 w 0 -> 100 0 -100",
            "8 10 w 0 w 0 -> 60 0 -60",
            "6 7 w 2 w 1 -> 14 4 -18",
            "7f 7 p 0 p 0 -> -80 40 40",
            "7 9 h 1 p 0 -> 23 -9 -14",
            "7 9 w 1 p 0 -> 36 -36 0",
            "7 9 w 1 w 0 -> 36 4 -40",
            "7 5 w 5 w 0 -> -90 54 36",
            "7 6 w 4 w 0 -> -51 33 18",
            "7 7 w 1 w 2 -> 15 -9 -6",
            "m 0 w 7 w 3 -> 67 -33 -34",
        };

        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split(" -> ");
            String[] params = parts[0].split("\\s+");
            int declarerGoal = 1;
            Player[] players = new Player[NOP];
            for (int i = 0; i < NOP; ++i) {
                Player p = new Bot(i);
                p.setHand(new CardSet());   // dummy to create history
                players[i] = p;
                String bid = params[2 * i];
                switch (bid) {
                    case "h":
                        p.bid = Config.Bid.BID_HALF_WHIST;
                        break;
                    case "m":
                        p.bid = Config.Bid.BID_MISERE;
                        break;
                    case "p":
                        p.bid = Config.Bid.BID_PASS;
                        break;
                    case "w":
                        p.bid = Config.Bid.BID_WHIST;
                        break;
                    default:
                        if (bid.endsWith("f")) {
                            p.bid = Config.Bid.BID_WITHOUT_THREE;
                            declarerGoal = Config.Bid.fromName(bid.substring(0, 1) + Card.Suit.CLUB).goal();
                        } else {
                            if (bid.equals("10")) {
                                p.bid = Config.Bid.BID_XC;
                            } else {
                                p.bid = Config.Bid.fromName(bid + Card.Suit.CLUB);
                            }
                            declarerGoal = p.bid.goal();
                        }
                }
                p.setTricks(Integer.parseInt(params[2 * i + 1]));
            }
            ScoreCalculator.getInstance().calculate(players, declarerGoal);
            params = parts[1].split("\\s+");
            for (int i = 0; i < NOP; ++i) {
                Player p = players[i];
                Assert.assertEquals(Integer.parseInt(params[i]), p.getRoundResults().getPoints(Player.PlayerPoints.status));
            }
        }
        Logger.println("done");
    }

}