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
 * Created: 3/4/2025
 */

package com.ab.util;

import com.ab.jpref.config.Config;
import com.ab.jpref.engine.GameManager;
import com.ab.jpref.engine.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ScoreCalculator {
    public static final int NOP = Config.NOP;   // Number of players
    static final Config config = Config.getInstance();
    static final int leftPoints = Player.PlayerPoints.leftPoints.ordinal();
    static final int rightPoints = Player.PlayerPoints.rightPoints.ordinal();
    static final int poolPoints = Player.PlayerPoints.poolPoints.ordinal();
    static final int dumpPoints = Player.PlayerPoints.dumpPoints.ordinal();
    static final int statusPoints = Player.PlayerPoints.status.ordinal();

    protected static ScoreCalculator instance;

    private static ScoreCalculator getScoreCalculator() {
        ScoreCalculator instance = null;
        Config.GameType gameType = config.gameType.get().getSelectedValue();
        switch (gameType) {
            case Miami:   // Сочи
                instance = new MiamiScoreCalculator();
                break;
//            case Peter:   // Ленинград
//                instance = null;    // to do
//                break;
//            case Rostov:
//                instance = null;    // to do
//                break;
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
    abstract void calculateWithout3(Player declarer, int declarerGoal);
    abstract void calculateMisere(Player[] players);
    abstract void calculateForTricks(Player[] players);

    public void calculate(Player[] players, int param) {
        Player declarer = null;
        for (Player player : players) {
            if (player.getBid().compareTo(Config.Bid.BID_PASS) > 0) {
                declarer = player;
                break;
            }
        }

        if (declarer == null) {
            instance.calculateAllPass(players, param);
        } else if (Config.Bid.BID_WITHOUT_THREE.equals(declarer.getBid())) {
            instance.calculateWithout3(declarer, param);
        } else if (Config.Bid.BID_MISERE.equals(declarer.getBid())) {
            instance.calculateMisere(players);
        } else {
            instance.calculateForTricks(players);
        }
        doHelp(players);
        calculateStatus(players);
    }

    private void doHelp(Player[] players) {
        final int poolSize = config.poolSize.get();
        Player helper = null;
        int extra = 0;
        int[] pools = new int[NOP];
        for (int i = 0; i < NOP; ++i) {
            Player player = players[i];
            int pool = 0;
            for (Player.RoundResults roundResults : player.getHistory()) {
                pool += roundResults.getPoints(Player.PlayerPoints.poolPoints);
            }
            if (pool > poolSize) {
                helper = player;
                extra = pool - poolSize;
            }
            pools[i] = pool;
        }
        if (helper == null) {
            return;
        }

        Player[] helpedPlayers = new Player[2];
        Player helped = players[(helper.getNumber() + 1) % NOP];
        helpedPlayers[0] = helped;
        int helpedPool = pools[helped.getNumber()];
        Player other = players[(helper.getNumber() + 2) % NOP];
        helpedPlayers[1] = other;
        int otherPool = pools[other.getNumber()];
        if (helpedPool >= poolSize || helpedPool < otherPool) {
            helpedPlayers[0] = other;
            helpedPlayers[1] = helped;
        }
        List<Player.RoundResults> helperHistory = helper.getHistory();
        Player.RoundResults helperResults = helperHistory.get(helperHistory.size() - 1);
        for (Player player : helpedPlayers) {
            int help = poolSize - pools[player.getNumber()];
            if (extra - help < 0) {
                help = extra;
            }
            helperResults.setPoints(poolPoints, helperResults.getPoints(poolPoints) - help);
            if (player.getNumber() == (helper.getNumber() + 1) % NOP) {
                helperResults.setPoints(leftPoints, helperResults.getPoints(leftPoints) + 10 * help);
            } else {
                helperResults.setPoints(rightPoints, helperResults.getPoints(rightPoints) + 10 * help);
            }
            pools[player.getNumber()] += help;
            pools[helper.getNumber()] -= help;
            List<Player.RoundResults> history = player.getHistory();
            Player.RoundResults helpedResults = history.get(history.size() - 1);
            helpedResults.setPoints(poolPoints, helpedResults.getPoints(poolPoints) + help);
            extra = pools[helper.getNumber()] - poolSize;
            if (extra <= 0) {
                return;
            }
        }
        helperResults.setPoints(dumpPoints, helperResults.getPoints(Player.PlayerPoints.dumpPoints) - extra);
    }

    private void calculateStatus(Player[] players) {
        // http://www.pocketpref.ru/pref/rules/rules.phtml?part_num=4.2
        Player.RoundResults[] totals = new Player.RoundResults[NOP];
        int sumDump = 0;
        for (int i = 0; i < totals.length; ++i) {
            totals[i] = new Player.RoundResults();
            Player player = players[i];
            for (Player.RoundResults roundResults : player.getHistory()) {
                for (int j = 0; j < roundResults.points.length; ++j) {
                    Player.PlayerPoints playerPoints = Player.PlayerPoints.values()[j];
                    switch (playerPoints) {
                        case poolPoints:
                            totals[i].points[dumpPoints] -= roundResults.points[j];
                            break;

                        case dumpPoints:
                            totals[i].points[dumpPoints] += roundResults.points[j];
                            break;

                        case leftPoints:
                            totals[i].points[leftPoints] += roundResults.points[j];
                            break;

                        case rightPoints:
                            totals[i].points[rightPoints] += roundResults.points[j];
                            break;
                    }
                }
            }
            sumDump += totals[i].points[dumpPoints];
        }

        // calc dump whists keeping total balance == 0
        int _dumpPoints = sumDump * 10 / NOP;
        int sum = 0;
        totals[0].points[dumpPoints] = _dumpPoints - 10 * totals[0].points[dumpPoints];
        sum += totals[0].points[dumpPoints];
        totals[1].points[dumpPoints] = _dumpPoints - 10 * totals[1].points[dumpPoints];
        sum += totals[1].points[dumpPoints];
        totals[2].points[dumpPoints] = -sum;

        // calculate whists:
        int diff = totals[0].points[leftPoints]
            - totals[1].points[rightPoints];
        totals[0].points[leftPoints] = diff;
        totals[1].points[rightPoints] = - diff;

        diff = totals[0].points[rightPoints]
            - totals[2].points[leftPoints];
        totals[0].points[rightPoints] = diff;
        totals[2].points[leftPoints] = - diff;

        diff = totals[1].points[leftPoints]
            - totals[2].points[rightPoints];
        totals[1].points[leftPoints] = diff;
        totals[2].points[rightPoints] = - diff;

        // store in each player status
        StringBuilder sb = new StringBuilder("status: ");
        String sep = "";
        for (int i = 0; i < totals.length; ++i) {
            Player player = players[i];
            player.getRoundResults().points[statusPoints] =
                totals[i].points[leftPoints]
                + totals[i].points[rightPoints]
                + totals[i].points[dumpPoints];
            sb.append(sep).append(player.getName()).append(" ")
                .append(player.getRoundResults().points[statusPoints]);
            sep = "; ";
        }
        Logger.println(sb);
    }

    private static class MiamiScoreCalculator extends ScoreCalculator {
        private final Map<Integer, Integer> factors = new HashMap<Integer, Integer>() {{
            put(6, 2);
            put(7, 4);
            put(8, 6);
            put(9, 8);
            put(10, 10);
            put(86, 10);
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
            }
        }

        void calculateWithout3(Player declarer, int declarerGoal) {
            int _dumpPoints = 3 * factors.get(declarerGoal);
            Player.RoundResults roundResults = declarer.getRoundResults();
            roundResults.setPoints(Player.PlayerPoints.dumpPoints, _dumpPoints);
        }

        void calculateMisere(Player[] players) {
            Player declarer = null;
            for (Player p : players) {
                if (p.getBid().compareTo(Config.Bid.BID_MISERE) == 0) {
                    declarer = p;
                    break;
                }
            }
            int factor = factors.get(Config.Bid.BID_MISERE.getValue());
            int _dumpPoints = factor * declarer.getTricks();
            Player.RoundResults roundResults = declarer.getRoundResults();
            roundResults.setPoints(Player.PlayerPoints.dumpPoints, _dumpPoints);
            if (declarer.getTricks() == 0) {
                roundResults.setPoints(Player.PlayerPoints.poolPoints, factor);
            }
        }

        void calculateForTricks(Player[] players) {
            Config.Bid bid = Config.Bid.BID_ALL_PASS;
            int declarerTricks = 0;
            int declarerNum = -1;
            int whistNum = -1;
            int passNum = -1;
            int halfWhistNum = -1;
            for (Player player : players) {
                Config.Bid _bid = player.getBid();
                switch (_bid) {
                    case BID_PASS:
                        passNum = player.getNumber();
                        break;
                    case BID_WHIST:
                        whistNum = player.getNumber();
                        break;
                    case BID_HALF_WHIST:
                        // do nothing
//                        halfWhistNum = player.getNumber();
                        break;
                    default:
                        bid = _bid;
                        declarerTricks = player.getTricks();
                        declarerNum = player.getNumber();
                        break;
                }
            }
            int goal = bid.goal();
            int factor = factors.get(goal);
            int declarerDiff = declarerTricks - goal;
            int defendersDiff = 10 - declarerTricks - bid.defenderGoal();

            if (whistNum >= 0 && passNum >= 0) {
                players[whistNum].setTricks(players[whistNum].getTricks() + players[passNum].getTricks());
                players[passNum].setTricks(0);
            }

            for (Player player : players) {
                Player.PlayerPoints points = Player.PlayerPoints.rightPoints;
                if (declarerNum == (player.getNumber() + 1) % NOP) {
                    points = Player.PlayerPoints.leftPoints;
                }
                switch (player.getBid()) {
                    case BID_PASS:
                        if (declarerDiff < 0) {
                            player.getRoundResults().setPoints(points, -declarerDiff * factor);
                        }
                        break;
                    case BID_WHIST:
                        int tricks = player.getTricks();
                        if (declarerDiff < 0) {
                            player.getRoundResults().setPoints(points,
                                (tricks - declarerDiff) * factor);
                        } else {
                            player.getRoundResults().setPoints(points, tricks * factor);
                            if (defendersDiff < 0) {
                                if (passNum >= 0) {
                                    // single whister
                                    player.getRoundResults().setPoints(Player.PlayerPoints.dumpPoints,
                                        -defendersDiff * factor);
                                } else {
                                    if (goal <= 7) {
                                        int trickDiff = bid.defenderGoal() / 2 - player.getTricks();
                                        player.getRoundResults().setPoints(Player.PlayerPoints.dumpPoints,
                                            trickDiff * factor);
                                    } else if ((declarerNum + 2) % NOP == player.getNumber()) {
                                        player.getRoundResults().setPoints(Player.PlayerPoints.dumpPoints, factor);
                                    }
                                }
                            }
                        }
                        break;
                    case BID_HALF_WHIST:
                        player.getRoundResults().setPoints(points, ((10 - goal) / 2) * factor);
                        break;
                    default:
                        // declarer
                        if (declarerDiff >= 0) {
                            player.getRoundResults().setPoints(Player.PlayerPoints.poolPoints,
                                factor);
                        } else {
                            player.getRoundResults().setPoints(Player.PlayerPoints.dumpPoints,
                                -declarerDiff * factor);
                        }
                }
            }
        }

    }
}