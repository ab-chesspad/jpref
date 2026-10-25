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
 * Created: 9/4/2025
 */

package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.jpref.ui.Host;
import com.ab.util.Logger;
import com.ab.util.ScoreCalculator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.ab.util.Logger.println;
import static com.ab.util.Util.currMethodName;

public class TestScoreCalculator extends BaseTest {
    public static final int NOP = Config.NOP;
    static GameManager gameManager;

    @Before
    public void init() {
        GameManager.DEBUG_LOG = false;      // suppress thread status logginga
        gameManager = new GameManager();
        gameManager.init(host);
        config.pauseBetweenRounds.set(0);
    }

    @Test
    public void testMiamiGreedy() {
        String[] sources = {
            // w - whist
            // p - pass
            // m - miser
            // h - half whist
            // f - without 3
            // for each: {bid, tricks} -> results
///
            "p 7 p 2 p 1 -> -36 13 23",
            "6 5 w 3 p 2 -> -28 19 9",
            "6 5 w 3 w 2 -> -28 15 13",
            "6 5 w 2 w 3 -> -28 13 15",
            "6 6 w 2 w 2 -> 6 -3 -3",
            "6 6 w 0 p 4 -> 6 1 -7",
            "6 6 w 3 p 1 -> 6 1 -7",
            "6 7 w 2 w 1 -> 14 4 -18",
            "7f 7 p 0 p 0 -> -80 40 40",
            "7 5 w 5 w 0 -> -90 55 35",
            "7 6 w 4 w 0 -> -50 33 17",
            "7 7 w 1 w 2 -> 14 -9 -5",
            "7 9 h 1 p 0 -> 22 -9 -13",
            "7 9 w 1 p 0 -> 36 -36 0",
            "7 9 w 1 w 0 -> 36 4 -40",
            "7 10 w 0 w 0 -> 54 -27 -27",
            "8 10 w 0 w 0 -> 60 0 -60",
            "m 0 w 7 w 3 -> 66 -33 -33",
            "9 9 w 1 w 0 -> 46 -19 -27",
            "9 10 w 0 w 0 -> 80 0 -80",
            "10 10 w 0 w 0 -> 100 0 -100",
        };
        println("running: " + currMethodName());
        config.gameType.get().setSelectedValue(Config.GameType.Miami);
        config.whistType.get().setSelectedValue(Config.WhistType.Greedy);
        config.whistTotus.set(true);
        testScoreCalculator(sources);
        config.whistTotus.set(false);
        testScoreCalculator("10 10 w 0 w 0 -> 66 -33 -33");
        Logger.println("done");
    }

    @Test
    public void testMiamiGentleman() {
        String[] sources = {
            // w - whist
            // p - pass
            // m - miser
            // h - half whist
            // f - without 3
            // for each: {bid, tricks} -> results
///
            "6 5 w 3 p 2 -> -28 14 14",
            "6 5 w 3 w 2 -> -28 15 13",
            "6 6 w 3 p 1 -> 6 1 -7",
            "6 7 w 2 w 1 -> 14 4 -18",
            "9 10 p 0 w 0 -> 80 0 -80",
            "8 10 w 0 p 0 -> 60 -60 0",
            "7 5 p 5 w 0 -> -80 40 40",
            "7 6 p 4 w 0 -> -42 21 21",
        };
        println("running: " + currMethodName());
        config.gameType.get().setSelectedValue(Config.GameType.Miami);
        config.whistType.get().setSelectedValue(Config.WhistType.Gentleman);
        config.whistTotus.set(true);
        testScoreCalculator(sources);
        config.whistTotus.set(false);
        testScoreCalculator("10 10 w 0 w 0 -> 66 -33 -33");
        Logger.println("done");
    }

    @Test
    public void testMiamiGentleHalf() {
        String[] sources = {
            // w - whist
            // p - pass
            // m - miser
            // h - half whist
            // f - without 3
            // for each: {bid, tricks} -> results
///
            "6 6 w 3 p 1 -> 6 1 -7",
            "6 7 w 2 w 1 -> 10 1 -11",
            "6 8 w 2 w 0 -> 16 4 -20",
            "6 8 p 2 w 0 -> 16 0 -16",

            "7 5 w 5 w 0 -> -90 55 35",
            "7 6 w 4 w 0 -> -50 33 17",

            "7 9 w 1 p 0 -> 30 -23 -7",
            "7 9 w 1 w 0 -> 30 -3 -27",
            "7 10 w 0 w 0 -> 40 -20 -20",
            "8 10 w 0 p 0 -> 50 -40 -10",
            "8 10 w 0 w 0 -> 50 -10 -40",
            "9 9 w 1 w 0 -> 46 -19 -27",
            "9 10 w 0 w 0 -> 66 -13 -53",
            "9 10 p 0 w 0 -> 66 -13 -53",
            "10 10 w 0 w 0 -> 84 -17 -67",
        };
        println("running: " + currMethodName());
        config.gameType.get().setSelectedValue(Config.GameType.Miami);
        config.whistType.get().setSelectedValue(Config.WhistType.GentlemanHalf);
        config.whistTotus.set(true);
        testScoreCalculator(sources);
        config.whistTotus.set(false);
        testScoreCalculator("10 10 w 0 w 0 -> 66 -33 -33");
        Logger.println("done");
    }

    @Test
    public void testPeterGreedy() {
        String[] sources = {
            // w - whist
            // p - pass
            // m - miser
            // h - half whist
            // f - without 3
            // for each: {bid, tricks} -> results
/// todo: verify
            "m 0 w 7 w 3 -> 134 -67 -67",
            "p 7 p 2 p 1 -> -74 27 47",
            "6 5 w 3 p 2 -> -54 37 17",
            "6 5 w 3 w 2 -> -54 29 25",
            "6 5 w 2 w 3 -> -54 25 29",
            "6 6 w 2 w 2 -> 10 -5 -5",
            "6 6 w 0 p 4 -> 10 3 -13",
            "6 6 w 3 p 1 -> 10 3 -13",
            "6 7 w 2 w 1 -> 28 8 -36",
            "7f 7 p 0 p 0 -> -160 80 80",
            "7 5 w 5 w 0 -> -178 109 69",
            "7 6 w 4 w 0 -> -102 67 35",
            "7 7 w 1 w 2 -> 30 -19 -11",
            "7 9 h 1 p 0 -> 46 -19 -27",
            "7 9 w 1 p 0 -> 72 -72 0",
            "7 9 w 1 w 0 -> 72 8 -80",
            "7 10 w 0 w 0 -> 106 -53 -53",
            "8 10 w 0 w 0 -> 120 0 -120",
            "9 9 w 1 w 0 -> 90 -37 -53",
            "9 10 w 0 w 0 -> 160 0 -160",
            "10 10 w 0 w 0 -> 200 0 -200",
        };
        println("running: " + currMethodName());
        config.gameType.get().setSelectedValue(Config.GameType.Peter);
        config.whistType.get().setSelectedValue(Config.WhistType.Greedy);
        config.whistTotus.set(true);
        testScoreCalculator(sources);
        config.whistTotus.set(false);
        testScoreCalculator("10 10 w 0 w 0 -> 134 -67 -67");
        Logger.println("done");
    }

    @Test
    public void testPeterGentleman() {
        String[] sources = {
            // w - whist
            // p - pass
            // m - miser
            // h - half whist
            // f - without 3
            // for each: {bid, tricks} -> results
///
            "6 5 w 3 p 2 -> -44 22 22",
            "6 5 w 3 w 2 -> -54 29 25",
            "6 6 w 3 p 1 -> 10 3 -13",
            "6 7 w 2 w 1 -> 28 8 -36",
            "7 5 p 5 w 0 -> -148 74 74",
            "7 6 p 4 w 0 -> -78 39 39",
            "8 10 w 0 p 0 -> 120 -120 0",
            "9 10 p 0 w 0 -> 160 0 -160",
            "10 10 w 0 w 0 -> 200 0 -200",
        };
        println("running: " + currMethodName());
        config.gameType.get().setSelectedValue(Config.GameType.Peter);
        config.whistType.get().setSelectedValue(Config.WhistType.Gentleman);
        config.whistTotus.set(true);
        testScoreCalculator(sources);
        config.whistTotus.set(false);
        testScoreCalculator("10 10 w 0 w 0 -> 134 -67 -67");
        Logger.println("done");
    }

    @Test
    public void testPeterGentleHalf() {
        String[] sources = {
            // w - whist
            // p - pass
            // m - miser
            // h - half whist
            // f - without 3
            // for each: {bid, tricks} -> results
/// todo: verify
            "6 6 w 3 p 1 -> 10 3 -13",
            "6 7 w 2 w 1 -> 22 1 -23",
            "6 8 w 2 w 0 -> 32 8 -40",
            "6 8 p 2 w 0 -> 32 0 -32",
            "7 5 w 5 w 0 -> -178 109 69",
            "7 6 w 4 w 0 -> -102 67 35",
            "7 9 w 1 p 0 -> 58 -45 -13",
            "7 9 w 1 w 0 -> 58 -5 -53",
            "7 10 w 0 w 0 -> 80 -40 -40",
            "8 10 w 0 p 0 -> 100 -80 -20",
            "8 10 w 0 w 0 -> 100 -20 -80",
            "9 9 w 1 w 0 -> 90 -37 -53",
            "9 10 w 0 w 0 -> 134 -27 -107",
            "9 10 p 0 w 0 -> 134 -27 -107",
            "10 10 w 0 w 0 -> 166 -33 -133",
        };
        println("running: " + currMethodName());
        config.gameType.get().setSelectedValue(Config.GameType.Peter);
        config.whistType.get().setSelectedValue(Config.WhistType.GentlemanHalf);
        config.whistTotus.set(true);
        testScoreCalculator(sources);
        config.whistTotus.set(false);
        testScoreCalculator("10 10 w 0 w 0 -> 134 -67 -67");
        Logger.println("done");
    }

    private void testScoreCalculator(String... sources) {
        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split(" -> ");
            String[] params = parts[0].split("\\s+");
            int declarerGoal = 1;
            gameManager.declarerNumber = -1;
            gameManager.declarer = null;
            for (int i = 0; i < NOP; ++i) {
                Player p = gameManager.getPlayers()[i];
                p.clear();
                p.gameHistory.clear();
                p.setHand(new CardSet());   // dummy to create history
                String bid = params[2 * i];
                switch (bid) {
                    case "h":
                        p.bid = Config.Bid.BID_HALF_WHIST;
                        break;
                    case "m":
                        p.bid = Config.Bid.BID_MISERE;
                        gameManager.declarerNumber = i;
                        gameManager.declarer = p;
                        break;
                    case "p":
                        p.bid = Config.Bid.BID_PASS;
                        break;
                    case "w":
                        p.bid = Config.Bid.BID_WHIST;
                        break;
                    default:
                        gameManager.declarerNumber = i;
                        gameManager.declarer = p;
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
            ScoreCalculator.calculate(declarerGoal);
            StringBuilder sb = new StringBuilder();
            String sep = "";
            for (Player p : gameManager.getPlayers()) {
                sb.append(sep).append(p.getRoundResults().getPoints(Player.PlayerPoints.status));
                sep = " ";
            }
/*
            if (!parts[1].equals(sb.toString())) {
                System.out.printf("\"%s -> %s\",\n", parts[0], sb);
            }
/*/
            Assert.assertEquals(parts[1], sb.toString());
//*/
        }

    }
}