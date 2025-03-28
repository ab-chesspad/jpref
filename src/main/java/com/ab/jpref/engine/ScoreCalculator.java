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
 * Created: 3/4/25
 */

package com.ab.jpref.engine;

import com.ab.jpref.config.Config;
import com.ab.util.Logger;

import java.util.HashMap;
import java.util.Map;

public abstract class ScoreCalculator {
    static final Config config = Config.getInstance();
    protected static ScoreCalculator instance;

    private static ScoreCalculator getScoreCalculator() {
        ScoreCalculator instance = null;
        String gameType = config.gameType.get();
        switch (gameType) {
            case "Miami":
                instance = new MiamiScoreCalculator();
                break;
            case "Leningrad":
            case "Rostov":
                instance = null;    // to do
                break;
        }
        return instance;
    }

    public static ScoreCalculator getInstance() {
        if (instance == null) {
            instance = getScoreCalculator();
        }
        return instance;
    }

    abstract void calculateAllPass(Player[] players, int trickCost);
    abstract void calculateWithout3(Player player);

    public void calculate(Player declarer, Player[] players, int trickCost) {
        if (declarer == null) {
            instance.calculateAllPass(players, trickCost);
        } else if (Config.Bid.BID_WITHOUT_THREE.equals(declarer.getBid())) {
            instance.calculateWithout3(declarer);
        }
        // todo: calc declared round

        calculateStatus(players);
    }

    public void calculateStatus(Player[] players) {
        Player.RoundResults[] totals = new Player.RoundResults[GameManager.NUMBER_OF_PLAYERS];
        int sumDump = 0;
        for (int i = 0; i < totals.length; ++i) {
            totals[i] = new Player.RoundResults();
            Player player = players[i];
            for (Player.RoundResults roundResults : player.getHistory()) {
                for (int j = 0; j < roundResults.points.length; ++j) {
                    Player.PlayerPoints playerPoints = Player.PlayerPoints.values()[j];
                    switch (playerPoints) {
                        case poolPoints:
                            totals[i].points[Player.PlayerPoints.dumpPoints.ordinal()] -= roundResults.points[j];
                            break;

                        case dumpPoints:
                            totals[i].points[Player.PlayerPoints.dumpPoints.ordinal()] += roundResults.points[j];
                            break;

                        case leftPoints:
                            totals[i].points[Player.PlayerPoints.leftPoints.ordinal()] += roundResults.points[j];
                            break;

                        case rightPoints:
                            totals[i].points[Player.PlayerPoints.rightPoints.ordinal()] += roundResults.points[j];
                            break;
                    }
                }
            }
            sumDump += totals[i].points[Player.PlayerPoints.dumpPoints.ordinal()];
        }

        // calc dump whists keeping total balance == 0
        int dumpPoints = sumDump * 10 / GameManager.NUMBER_OF_PLAYERS;
        int sum = 0;
        int j = Player.PlayerPoints.dumpPoints.ordinal();
        totals[0].points[j] = dumpPoints - 10 * totals[0].points[j];
        sum += totals[0].points[j];
        totals[1].points[j] = dumpPoints - 10 * totals[1].points[j];
        sum += totals[1].points[j];
        totals[2].points[j] = -sum;

        // calculate whists:
        int diff = totals[0].points[Player.PlayerPoints.leftPoints.ordinal()]
            - totals[1].points[Player.PlayerPoints.rightPoints.ordinal()];
        totals[0].points[Player.PlayerPoints.leftPoints.ordinal()] = diff;
        totals[1].points[Player.PlayerPoints.rightPoints.ordinal()] = - diff;

        diff = totals[0].points[Player.PlayerPoints.rightPoints.ordinal()]
            - totals[2].points[Player.PlayerPoints.leftPoints.ordinal()];
        totals[0].points[Player.PlayerPoints.rightPoints.ordinal()] = diff;
        totals[2].points[Player.PlayerPoints.leftPoints.ordinal()] = - diff;

        diff = totals[1].points[Player.PlayerPoints.leftPoints.ordinal()]
            - totals[2].points[Player.PlayerPoints.rightPoints.ordinal()];
        totals[1].points[Player.PlayerPoints.leftPoints.ordinal()] = diff;
        totals[2].points[Player.PlayerPoints.rightPoints.ordinal()] = - diff;

        // store in each player status
        j = Player.PlayerPoints.status.ordinal();
        StringBuilder sb = new StringBuilder("status: ");
        String sep = "";
        for (int i = 0; i < totals.length; ++i) {
            Player player = players[i];
            player.getRoundResults().points[j] =
                totals[i].points[Player.PlayerPoints.leftPoints.ordinal()]
                + totals[i].points[Player.PlayerPoints.rightPoints.ordinal()]
                + totals[i].points[Player.PlayerPoints.dumpPoints.ordinal()];
            sb.append(sep).append(player.getName()).append(" ")
                .append(player.getRoundResults().points[j]);
            sep = "; ";
        }
        Logger.println(sb.toString());
    }

    private static class MiamiScoreCalculator extends ScoreCalculator {
        private final Map<Integer, Integer> fines = new HashMap<Integer, Integer>() {{
            put(6, 2);
            put(7, 4);
            put(8, 6);
            put(9, 8);
            put(10, 10);
        }};

        @Override
        void calculateAllPass(Player[] players, int trickCost) {
            int minTricks = Integer.MAX_VALUE;
            for (Player player : players) {
                if (minTricks > player.getTricks()) {
                    minTricks = player.getTricks();
                }
            }

            for (Player player : players) {
                Player.RoundResults roundResults = player.getRoundResults();
                if (player.getTricks() == 0) {
                    roundResults.setPoints(Player.PlayerPoints.poolPoints, trickCost);
                } else {
                    int tricks = player.getTricks() - minTricks;
                    roundResults.setPoints(Player.PlayerPoints.dumpPoints, trickCost * tricks);
                }
                player.endRound();
            }
        }

        void calculateWithout3(Player player) {
            int dumpPoints = 3 * fines.get(GameManager.getInstance().getMinBid().getValue() / 10);
            Player.RoundResults roundResults = player.getRoundResults();
            roundResults.setPoints(Player.PlayerPoints.dumpPoints, dumpPoints);
            player.endRound();
        }
    }
}
