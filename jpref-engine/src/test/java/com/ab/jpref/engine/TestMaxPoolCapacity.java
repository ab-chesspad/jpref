/*
 * Scratch experiment: find how large TrickList.TrickPool.DEFAULT_CAPACITY
 * needs to be for the worst case - a no-trump round where a defender
 * leads the first trick (forces a full-depth minimax search with no
 * trump-based branch narrowing).
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Config;

import static com.ab.util.Logger.printf;
import static com.ab.util.Logger.println;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class TestMaxPoolCapacity extends BaseTest {
    static GameManager gameManager;
    static TrickList trickList;

    @Before
    public void init() {
        trickList = new TrickList();
        gameManager = new GameManager();
        gameManager.init(host);
        GameManager.DEBUG_LOG = false;
        config.pauseBetweenRounds.set(0);
        Bot.targetBot = null;
    }

    private void run(String hand0, String hand1, String hand2, String talon,
                      Config.Bid bid, int elderHand, String label) throws IOException {
        CardList deck = new CardList();
        deck.addAll(util.toCardList(hand0));
        deck.addAll(util.toCardList(hand1));
        deck.addAll(util.toCardList(hand2));
        CardList talonCards = util.toCardList(talon);
        deck.addAll(talonCards);
        deck.verifyDeck();

        Bot.debugDrop = new CardList(talonCards);
        gameManager.elderHand = elderHand;
        gameManager.deal(deck);
        gameManager.prepareTest(0, bid, talonCards);

        long start = System.currentTimeMillis();
        gameManager.playRoundForTricks();
        long dur = System.currentTimeMillis() - start;

        printf("=== %s: bid %s, elderHand %d, declarer 0, duration %,d msec, declarerTricks %d ===\n",
            label, bid, elderHand, dur, gameManager.getDeclarer().getTricks());
        printf("maxPoolCount %,d, maxPositions %,d, maxSimilar %,d, maxListBuildTime %,d msec\n",
            TrickList.maxPoolCount, TrickList.maxPositions, TrickList.maxSimilar, TrickList.maxListBuildTime);
        println();
    }

    @Test
    public void ntDefenderLeads() throws IOException {
        println("running: ntDefenderLeads");
        run("♠79 ♣Q ♦789JQA ♥K", "♠JQA ♣89JA ♥7XQ", "♠8XK ♣7XK ♦XK ♥8A", "♥J9",
            Config.Bid.BID_6N, 1, "NT, defender(1) leads");
    }

    @Test
    public void trumpDefenderLeads() throws IOException {
        println("running: trumpDefenderLeads");
        init();
        run("♠79 ♣Q ♦789JQA ♥K", "♠JQA ♣89JA ♥7XQ", "♠8XK ♣7XK ♦XK ♥8A", "♥J9",
            Config.Bid.BID_6D, 1, "6D trump, defender(1) leads");
    }

    @Test
    public void ntDeclarerLeads() throws IOException {
        println("running: ntDeclarerLeads");
        init();
        run("♠79 ♣Q ♦789JQA ♥K", "♠JQA ♣89JA ♥7XQ", "♠8XK ♣7XK ♦XK ♥8A", "♥J9",
            Config.Bid.BID_6N, 0, "NT, declarer(0) leads");
    }

    @Test
    public void ntOtherDefenderLeads() throws IOException {
        println("running: ntOtherDefenderLeads");
        init();
        run("♠79 ♣Q ♦789JQA ♥K", "♠JQA ♣89JA ♥7XQ", "♠8XK ♣7XK ♦XK ♥8A", "♥J9",
            Config.Bid.BID_6N, 2, "NT, defender(2) leads");
    }

    @Test
    public void ntDefenderLeadsDeal2() throws IOException {
        println("running: ntDefenderLeadsDeal2");
        init();
        run("♠7 ♣JA ♦8QKA ♥XJK", "♠9JKA ♣89Q ♦J ♥9A", "♠8XQ ♣7K ♦7X ♥78Q", "♦9 ♣X",
            Config.Bid.BID_6N, 1, "NT, defender(1) leads, deal2");
    }

    @Test
    public void ntDefenderLeadsDeal2Other() throws IOException {
        println("running: ntDefenderLeadsDeal2Other");
        init();
        run("♠7 ♣JA ♦8QKA ♥XJK", "♠9JKA ♣89Q ♦J ♥9A", "♠8XQ ♣7K ♦7X ♥78Q", "♦9 ♣X",
            Config.Bid.BID_6N, 2, "NT, defender(2) leads, deal2");
    }

    // found by rotating deal1's hands among seats (with elderHand=1): worse than
    // ntOtherDefenderLeads above under the current bm4Iteration
    // (positions 460,690 vs 396,866, similar 2,898,437 vs 2,052,148)
    @Test
    public void ntDefenderLeadsDeal1Rotated() throws IOException {
        println("running: ntDefenderLeadsDeal1Rotated");
        init();
        run("♠8XK ♣7XK ♦XK ♥8A", "♠79 ♣Q ♦789JQA ♥K", "♠JQA ♣89JA ♥7XQ", "♥J9",
            Config.Bid.BID_6N, 1, "NT, defender(1) leads, deal1 rotated");
    }

    // deliberately adversarial: each hand void in two whole suits, to
    // maximize free-choice branching once a defender runs out of the led suit
    @Test
    public void ntDefenderLeadsClumped() throws IOException {
        println("running: ntDefenderLeadsClumped");
        init();
        run("♠789XJQKA ♣78", "♣9XJQKA ♦789X", "♦JQKA ♥789XJQ", "♥KA",
            Config.Bid.BID_6N, 1, "NT, defender(1) leads, clumped suits");
    }

    @Test
    public void ntDefenderLeadsClumpedOther() throws IOException {
        println("running: ntDefenderLeadsClumpedOther");
        init();
        run("♠789XJQKA ♣78", "♣9XJQKA ♦789X", "♦JQKA ♥789XJQ", "♥KA",
            Config.Bid.BID_6N, 2, "NT, defender(2) leads, clumped suits");
    }
}
