package com.ab.jpref.engine;

import com.ab.jpref.config.Config;

public class Scores {

    public static void calculate(Config.Bid round, int trickCost, Player declarer, Player[] players) {
        if (round.equals(Config.Bid.BID_ALL_PASS)) {
            calculateAllPass(trickCost, players);
        }
    }

    private static void calculateAllPass(int trickCost, Player[] players) {
        int minTricks = Integer.MAX_VALUE;
        for (Player player : players) {
            if (minTricks < player.getTricks()) {
                minTricks = player.getTricks();
            }
        }

        for (Player player : players) {
            if (player.getTricks() == 0) {
//                player.getRoundData().setPoolPonts(trickCost);
                continue;
            }
            int tricks = player.getTricks() - minTricks;
//            player.dumpPoints += trickCost * tricks;
        }
    }
}
