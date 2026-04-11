package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.jpref.config.Config.Bid;
import com.ab.util.BidData;
import com.ab.util.Logger;
import com.ab.util.Util;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static com.ab.util.Logger.println;
import static com.ab.util.Util.currMethodName;

public class TestTrickList {
    public static final int NOP = Config.NOP;

    static final Config config = Config.getInstance();
    static final Util util = Util.getInstance();
    static GameManager gameManager;

    @Before
    public void initClass() {
        gameManager = new GameManager(config, null);
        GameManager.DEBUG_LOG = false;      // suppress thread status logginga
        config.pauseBetweenRounds.set(0);
    }

/*
    @Test
    @Ignore("doesn't work")
    public void testRefineDrop() throws IOException {
        String[] sources = {
            // hands, bid, 1st move, 0th player tricks
//            "♣J ♦Q ♥8A  ♥XQ  ♥9K : 7♣ : ♥A -> 2",
            "♣9QK ♦78XJQK  ♠9XA ♣8XA ♥7JA  ♠QK ♣7J ♦9A ♥9QK : 6♦ : ♦X -> 6",
            "♠K ♦A ♥9XK  ♠XJ ♦8 ♥78  ♣X ♦K ♥JQA : 6♦ : ♣X ♦Q -> 3",
//            "♠K ♣8K ♦XQA ♥9XK  ♠XJ ♣JQ ♦78 ♥78  ♣79X ♦JK ♥JQA : 6♦ : ♣7A -> 4",
//            "♠89XKA ♣7X ♦JK  ♠Q ♣9J ♦78X ♥JKA  ♠7J ♣A ♦9QA ♥79Q : 6♠ -> 5",
        };

        println("running: " + currMethodName());
        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split("\\s+(:|->)\\s+");
            CardList cards = util.toCardList(parts[0]);
            int size = cards.size() / NOP;
            CardSet[] hands = new CardSet[NOP];
            int i = NOP;
            int k = cards.size();
            hands[--i] = new CardSet(cards.subList(k - size, k));
            k -= size;
            hands[--i] = new CardSet(cards.subList(k - size, k));
            k -= size;
            hands[--i] = new CardSet(cards.subList(0, k));  // the rest
            Bid bid = Bid.fromName(parts[1]);
            gameManager.minBid = bid;
            CardList trickCards = util.toCardList(parts[2]);
            int turn = -1;
            for (int j = 0; j < NOP; ++j) {
                if (hands[j].remove(trickCards.first())) {
                    turn = j;
                    break;
                }
            }
            Trick trick = new Trick();
            trick.clear(turn);
            trick.minBid = bid;
            trick.trumpSuit = bid.getTrump();
            trick.setNumber(10 - size);
            for (Card c : trickCards) {
                trick.add(c);
                Trick clone = new Trick(trick);
                Logger.println(clone.toColorString());
            }
//            trick.trickCards = trickCards;
            Bot.playerBid = new BidData.PlayerBid();
            ForTricksBot forTricksBot = new ForTricksBot(hands);
            Bot.trick = trick;
            forTricksBot.refineDrop(hands);

            int expectdTricks = Integer.parseInt(parts[3]);
            TrickList trickList = new TrickList(forTricksBot, trick, hands);
            int tricks = trickList.getEstimate();
            Assert.assertEquals("tricks", expectdTricks, tricks);
            Card card = trickList.getCard(trick, hands);

            Logger.println("ok");

        }
    }
*/

    @Test
    public void testTrickList() throws IOException {
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