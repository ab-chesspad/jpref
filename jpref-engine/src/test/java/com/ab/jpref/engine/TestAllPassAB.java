package com.ab.jpref.engine;

import com.ab.jpref.cards.CardList;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Random;

import static com.ab.jpref.config.Config.NOP;
import static com.ab.util.Logger.printf;
import static com.ab.util.Logger.println;
import static com.ab.util.Util.currMethodName;

/**
 * All-pass A/B: one Bot against two AllPassReferenceBot on the same random deals.
 * Each deal is played NOP times, Bot taking every seat in turn, so the seat and the lead
 * even out and equal play averages exactly 10/3 tricks per deal. Less is better.
 * The control run seats three AllPassReferenceBot and must give exactly 10/3.
 * Default 1000 deals, -Ddeals=N to change.
 */
public class TestAllPassAB extends BaseTest {
    static final double EQUAL_PLAY = 10.0 / NOP;
    static final double TOLERANCE = 0.01;   // noise of 1000 deals is well below that

    static class ABGameManager extends GameManager {
        final int botSeat;      // -1 for the control run

        ABGameManager(int botSeat) {
            this.botSeat = botSeat;
        }

        @Override
        protected Bot newBot(int number) {
            return number == botSeat ? new Bot(number) : new AllPassReferenceBot(number);
        }

        // the round is played by avatars, created here
        @Override
        protected Bot newBot(Player realPlayer) {
            return realPlayer.getNumber() == botSeat ? new Bot(realPlayer) : new AllPassReferenceBot(realPlayer);
        }
    }

    // average tricks of the player in botSeat
    private double run(int deals, boolean control) {
        Random seeds = new Random(7);
        long total = 0;
        int games = 0;
        for (int i = 0; i < deals; ++i) {
            long seed = seeds.nextLong();
            int turn = i % NOP;
            for (int seat = 0; seat < NOP; ++seat) {
                CardList deck = CardList.getDeck();
                Collections.shuffle(deck, new Random(seed));
                GameManager gameManager = new ABGameManager(control ? -1 : seat);
                gameManager.init(host);
                GameManager.DEBUG_LOG = false;
                config.pauseBetweenRounds.set(0);
                Bot.targetBot = null;
                new TrickList();
                gameManager.getTrick().clear(turn);
                gameManager.elderHand = turn;
                gameManager.deal(deck);
                gameManager.playRoundAllPass();
                total += gameManager.getPlayers()[seat].getTricks();
                ++games;
            }
        }
        return (double) total / games;
    }

    @Test
    public void testAllPassAB() {
        println("running: " + currMethodName());
        int deals = Integer.getInteger("deals", 1000);
        double control = run(deals, true);
        double bot = run(deals, false);
        printf("all-pass A/B, %d deals: reference vs reference %.4f, Bot vs reference %.4f, equal play %.4f\n",
            deals, control, bot, EQUAL_PLAY);
        Assert.assertEquals("control run must be even", EQUAL_PLAY, control, 1e-9);
        Assert.assertTrue(String.format("Bot plays all-pass worse than the reference: %.4f", bot),
            bot <= EQUAL_PLAY + TOLERANCE);
    }
}
