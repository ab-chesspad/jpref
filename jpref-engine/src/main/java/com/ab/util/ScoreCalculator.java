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
 * Created: 3/4/2025
 */

package com.ab.util;

import com.ab.jpref.config.Config;
import static com.ab.jpref.config.Config.NOP;
import com.ab.jpref.engine.GameManager;
import com.ab.jpref.engine.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ScoreCalculator {
    static final int leftPoints = Player.PlayerPoints.leftPoints.ordinal();
    static final int rightPoints = Player.PlayerPoints.rightPoints.ordinal();
    static final int poolPoints = Player.PlayerPoints.poolPoints.ordinal();
    static final int dumpPoints = Player.PlayerPoints.dumpPoints.ordinal();
    static final int statusPoints = Player.PlayerPoints.status.ordinal();

    static final Map<Integer, Integer> factors = new HashMap<Integer, Integer>() {{
        put(6, 2);
        put(7, 4);
        put(8, 6);
        put(9, 8);
        put(10, 10);
        put(86, 10);
    }};

    protected final Config config;
    protected final int gameFactor;

    abstract void doHelp(Player[] players);
    abstract boolean poolComplete(int playerNumber);

    protected ScoreCalculator(Config config, int gameFactor) {
        this.config = config;
        this.gameFactor = gameFactor;
    }

    private static ScoreCalculator getScoreCalculator() {
        Config config = GameManager.getInstance().config();
        ScoreCalculator instance = null;
        Config.GameType gameType = config.gameType.get().getSelectedValue();
        switch (gameType) {
            case Miami:   // Сочи
                instance = new MiamiCalculator(config);
                break;
            case Peter:   // Питер
                instance = new PeterCalculator(config);
                break;
        }
        return instance;
    }

    public static int gameFactor() {
        return getScoreCalculator().gameFactor;
    }

    public static boolean isGameOver() {
        Player[] players = GameManager.getInstance().getPlayers();
        int totalPool = 0;
        for (Player player : players) {
            for (Player.RoundResults roundResults : player.getGameHistory()) {
                totalPool += roundResults.getPoints(Player.PlayerPoints.poolPoints);
            }
        }
        return totalPool >= GameManager.getInstance().config().poolSize.get() * NOP;
    }

    public static boolean isPoolComplete(int playerNumber) {
        ScoreCalculator instance = getScoreCalculator();
        return instance.poolComplete(playerNumber);
    }

    public static void calculate(int param) {
        ScoreCalculator instance = getScoreCalculator();
        Player declarer = GameManager.getInstance().getDeclarer();
        Player[] players = GameManager.getInstance().getPlayers();
        if (declarer == null) {
            instance.calculateAllPass(players, param * instance.gameFactor);
        } else if (Config.Bid.BID_WITHOUT_THREE.equals(declarer.getBid())) {
            instance.calculateWithout3(declarer, param);
        } else if (Config.Bid.BID_MISERE.equals(declarer.getBid())) {
            instance.calculateMisere(players);
        } else {
            instance.calculateForTricks(players);
        }
        instance.doHelp(players);
        instance.calculateStatus(players);
    }

    private void calculateAllPass(Player[] players, int trickCost) {
        int minTricks = Integer.MAX_VALUE;
        for (Player player : players) {
            if (minTricks > player.getTricks()) {
                minTricks = player.getTricks();
            }
        }

        for (Player player : players) {
            Player.RoundResults roundResults = player.getRoundResults();
            if (player.getTricks() == 0) {
                roundResults.setPoints(poolPoints, trickCost);
            } else {
                int tricks = player.getTricks() - minTricks;
                roundResults.setPoints(dumpPoints, trickCost * tricks);
            }
        }
    }

    private void calculateWithout3(Player declarer, int declarerGoal) {
        int _dumpPoints = 3 * factors.get(declarerGoal) * gameFactor;
        Player.RoundResults roundResults = declarer.getRoundResults();
        roundResults.setPoints(Player.PlayerPoints.dumpPoints, _dumpPoints);
    }

    private void calculateMisere(Player[] players) {
        Player declarer = null;
        for (Player p : players) {
            if (p.getBid().compareTo(Config.Bid.BID_MISERE) == 0) {
                declarer = p;
                break;
            }
        }
        int factor = factors.get(Config.Bid.BID_MISERE.getValue());
        int _dumpPoints = factor * declarer.getTricks() * gameFactor;
        Player.RoundResults roundResults = declarer.getRoundResults();
        roundResults.setPoints(dumpPoints, _dumpPoints);
        if (declarer.getTricks() == 0) {
            roundResults.setPoints(poolPoints, factor);
        }
    }

    private void calculateForTricks(Player[] players) {
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
                case BID_WHIST_LAYING:
                case BID_WHIST_STANDING:
                    whistNum = player.getNumber();
                    break;
                case BID_HALF_WHIST:
                    // do nothing
                    halfWhistNum = player.getNumber();
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
        boolean showTotus = goal == 10 && !config.whistTotus.get();
        if (showTotus) {
            if (declarerDiff == 0) {
                players[declarerNum].getRoundResults().setPoints(poolPoints, factor);
                return;
            }
        }
        if (halfWhistNum >= 0) {
            players[declarerNum].getRoundResults().setPoints(poolPoints, factor);
            int where = leftPoints;
            if (halfWhistNum == (declarerNum + 1) % NOP) {
                where = rightPoints;
            }
            players[halfWhistNum].getRoundResults().setPoints(where, ((10 - goal) / 2) * factor * gameFactor);
            return;
        }
        if (whistNum >= 0 && passNum >= 0) {
            players[whistNum].setTricks(players[whistNum].getTricks() + players[passNum].getTricks());
            players[passNum].setTricks(0);
        }
        Player player;
        player = players[(declarerNum + 1) % NOP];
        player.getRoundResults().setPoints(rightPoints, player.getTricks() * factor * gameFactor);
        player = players[(declarerNum + 2) % NOP];
        player.getRoundResults().setPoints(leftPoints, player.getTricks() * factor * gameFactor);

        boolean gentlemanWhist = config.whistType.get().getSelectedValue().equals(Config.WhistType.Gentleman);
        boolean gentlemanHalfWhist = config.whistType.get().getSelectedValue().equals(Config.WhistType.GentlemanHalf);
        if (declarerDiff < 0) {
            int extra = -declarerDiff * factor * gameFactor;
            players[declarerNum].getRoundResults().setPoints(dumpPoints, extra);
            if (passNum < 0 || !(gentlemanWhist || gentlemanHalfWhist)) {
                players[(declarerNum + 1) % NOP].getRoundResults().addPoints(rightPoints, extra);
                players[(declarerNum + 2) % NOP].getRoundResults().addPoints(leftPoints, extra);
            } else {
                int newPoints = (10 - declarerTricks + extra);
                if (!players[(declarerNum + 1) % NOP].halfWhist) {
                    players[(declarerNum + 1) % NOP].getRoundResults().setPoints(rightPoints, newPoints);
                }
                if (!players[(declarerNum + 2) % NOP].halfWhist) {
                    players[(declarerNum + 2) % NOP].getRoundResults().setPoints(leftPoints, newPoints);
                }
            }
        } else {
            players[declarerNum].getRoundResults().setPoints(poolPoints, factor);
            if (defendersDiff < 0) {
                int fine = -defendersDiff * factor * gameFactor;
                if (gentlemanHalfWhist) {
                    fine /= 2;
                }
                if (passNum >= 0) {
                    if (whistNum >= 0) {
                        // single whister
                        players[whistNum].getRoundResults().addPoints(dumpPoints, fine);
                    }
                } else {
                    if (goal <= 7) {
                        for (int i = 0; i < 2; ++i) {
                            player = players[(declarerNum + 1 + i) % NOP];
                            int deficit = bid.defenderGoal() / 2 - player.getTricks();
                            if (deficit > 0) {
                                fine = deficit * factor * gameFactor;
                                if (gentlemanHalfWhist) {
                                    fine /= 2;
                                }
                                player.getRoundResults().setPoints(dumpPoints, fine);
                            }
                        }
                    } else {
                        players[(declarerNum + 2) % NOP].getRoundResults().setPoints(dumpPoints, fine);
                    }
                }
            }
        }
    }

    private void calculateStatus(Player[] players) {
        // http://www.pocketpref.ru/pref/rules/rules.phtml?part_num=4.2
        Player.RoundResults[] totals = new Player.RoundResults[NOP];
        int sumDump = 0;
        for (int i = 0; i < totals.length; ++i) {
            totals[i] = new Player.RoundResults();
            Player player = players[i];
            for (Player.RoundResults roundResults : player.getGameHistory()) {
                for (int j = 0; j < roundResults.points.length; ++j) {
                    Player.PlayerPoints playerPoints = Player.PlayerPoints.values()[j];
                    switch (playerPoints) {
                        case poolPoints:
                            totals[i].points[dumpPoints] -= roundResults.points[j] * gameFactor;
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
        double v = (double)sumDump * 10 / NOP;
        if (v > 0) {
            v += 0.5;
        } else {
            v -= 0.5;
        }
        int _dumpPoints = (int)v;
        int sum = 0;
        totals[1].points[dumpPoints] = _dumpPoints - 10 * totals[1].points[dumpPoints];
        sum += totals[1].points[dumpPoints];
        totals[2].points[dumpPoints] = _dumpPoints - 10 * totals[2].points[dumpPoints];
        sum += totals[2].points[dumpPoints];
        totals[0].points[dumpPoints] = -sum;

        // calculate whists:
        int diff = totals[0].points[leftPoints] - totals[1].points[rightPoints];
        totals[0].points[leftPoints] = diff;
        totals[1].points[rightPoints] = - diff;

        diff = totals[0].points[rightPoints] - totals[2].points[leftPoints];
        totals[0].points[rightPoints] = diff;
        totals[2].points[leftPoints] = - diff;

        diff = totals[1].points[leftPoints] - totals[2].points[rightPoints];
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

    private static class MiamiCalculator extends ScoreCalculator {
        MiamiCalculator(Config config) {
            super(config, 1);
        }

        @Override
        void doHelp(Player[] players) {
            final int poolSize = config.poolSize.get();
            Player helper = null;
            int extra = 0;
            int[] pools = new int[NOP];
            for (int i = 0; i < NOP; ++i) {
                Player player = players[i];
                int pool = 0;
                for (Player.RoundResults roundResults : player.getGameHistory()) {
                    pool += roundResults.getPoints(poolPoints);
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
            List<Player.RoundResults> helperHistory = helper.getGameHistory();
            Player.RoundResults helperResults = helperHistory.get(helperHistory.size() - 1);
            helperResults.setPoints(poolPoints, helperResults.getPoints(poolPoints) - extra);
            for (Player player : helpedPlayers) {
                int help = poolSize - pools[player.getNumber()];
                if (extra - help < 0) {
                    help = extra;
                }
                if (player.getNumber() == (helper.getNumber() + 1) % NOP) {
                    helperResults.setPoints(leftPoints, helperResults.getPoints(leftPoints) + 10 * help);
                } else {
                    helperResults.setPoints(rightPoints, helperResults.getPoints(rightPoints) + 10 * help);
                }
                pools[player.getNumber()] += help;
                pools[helper.getNumber()] -= help;
                List<Player.RoundResults> history = player.getGameHistory();
                Player.RoundResults helpedResults = history.get(history.size() - 1);
                helpedResults.setPoints(poolPoints, helpedResults.getPoints(poolPoints) + help);
                extra = pools[helper.getNumber()] - poolSize;
                if (extra <= 0) {
                    return;
                }
            }
            helperResults.setPoints(dumpPoints, helperResults.getPoints(dumpPoints) - extra);
        }

        @Override
        boolean poolComplete(int playerNumber) {
            Player player = GameManager.getInstance().getPlayers()[playerNumber];
            int totalPool = 0;
            for (Player.RoundResults roundResults : player.getGameHistory()) {
                totalPool += roundResults.getPoints(Player.PlayerPoints.poolPoints);
            }
            return totalPool >= GameManager.getInstance().config().poolSize.get();
        }
    }

    private static class PeterCalculator extends ScoreCalculator {
        PeterCalculator(Config config) {
            super(config, 2);
        }

        @Override
        void doHelp(Player[] players) {/* do nothing*/ }

        @Override
        boolean poolComplete(int playerNumber) {
            return false;
        }
    }
}