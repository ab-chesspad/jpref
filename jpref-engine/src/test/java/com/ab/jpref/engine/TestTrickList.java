package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.jpref.config.Config.Bid;
import com.ab.jpref.trickpool.TrickPool;
import com.ab.util.Logger;
import com.ab.util.Util;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestTrickList {
    public static final int NOP = Config.NOP;

    static final Config config = Config.getInstance();
    static final Util util = Util.getInstance();
    static GameManager gameManager;

    @Before
    public void initClass() {
        TrickList.setTrickPool(new TrickPool());
        gameManager = new GameManager(config, null);
        GameManager.DEBUG_LOG = false;      // suppress thread status logginga
        config.pauseBetweenRounds.set(0);
    }

    @Test
    public void testTrickList() {
        String[] sources = {
            "♦XA ♥7JQK  ♠K ♦78QK ♥X  ♠9 ♣K ♦9 ♥89A : 6♣ -> 3",
            "♠79 ♥XJ  ♠JQ ♦A ♥K  ♠K ♦K ♥QA : 6♥ -> 0",  // [♠7JK, ♦K ♥X ♦A, ♠9Q ♥Q, ♥AJK]
//            "♠89XKA ♣7X ♦JK  ♠Q ♣9J ♦78X ♥JKA  ♠7J ♣A ♦9QA ♥79Q : 6♠ -> 5",
        };

        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split("\\s+(:|->)\\s+");
            CardList cards = util.toCardList(parts[0]);
            int size = cards.size() / NOP;
            CardSet[] hands = new CardSet[NOP];

            for (int j = 0; j < NOP; ++j) {
                int k = j * size;
                hands[j] = new CardSet(cards.subList(k, k + size));
            }
            ForTricksBot forTricksBot = new ForTricksBot(hands);
            Bid bid = Bid.fromName(parts[1]);
            int expectdTricks = Integer.parseInt(parts[2]);
            Trick trick = new Trick();
            trick.clear(0);
            gameManager.minBid = bid;
            trick.minBid = bid;
            trick.trumpSuit = bid.getTrump();
            trick.setNumber(10 - size);
            TrickList trickList = new TrickList(forTricksBot, trick, hands);
            int tricks = trickList.root.getPastTricks() + trickList.root.getFutureTricks();
            Assert.assertEquals("tricks", expectdTricks, tricks);
            Card card = trickList.getCard(trick, hands);
            Logger.println("ok");
        }
    }
}