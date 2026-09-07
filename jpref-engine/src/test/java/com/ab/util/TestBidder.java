package com.ab.util;

import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config.Bid;
import com.ab.jpref.engine.BaseTest;
import org.junit.Assert;
import org.junit.Test;

public class TestBidder extends BaseTest {
    @Test
    public void testGetBid() {
        String[] sources = {
            // hand (11 - 12 cards, elderhand, [min bid] -> bid : drop
            "♠78QK ♣QKA ♦QA ♥JQA  2 -> 7♠ : ♦Q ♥J",
/////////
            "♠9 ♣89JQ ♦78XA ♥7XQ  0 -> 6♦ : ♥7 ♠9",
            "♠9 ♣89JQ ♦78XA ♥7XQ  1 -> 6♣ : ♥7 ♠9",

            "♠JQK ♣8QKA ♦8A ♥QK  0 -> 8♣ : ♦8",  // todo: 7♣
            "♠JQK ♣8QKA ♦8A ♥QK  1 -> 7♣ : ♦8",

            "♠8XJQK ♣JA ♦7K ♥7Q  1 -> Pass",
            "♠78XJQK ♣JA ♦7K ♥7Q  1  6♠ -> 6♠ : ♥7Q",
            "♠78XJQK ♣JA ♦7K ♥7Q  1  6♣ -> 7♠ : ♥7Q",   // overbidding

            "♠78XJQK ♣JA ♦7K ♥Q  1 -> 6♠",
            "♠78QK ♣QKA ♦QA ♥JQA  0 -> 7♠ : ♦Q ♥J",
            "♠78QK ♣QKA ♦QA ♥JQA  1 -> 7♠ : ♦Q ♥J",
            "♠78QK ♣QKA ♦QA ♥JQA  2 -> 7♠ : ♦Q ♥J",
            "♠A ♣QK ♦89XJA ♥8XJK  1 -> 6♦ : ♣QK",
            "♠A ♣QK ♦89XJA ♥8XJK  2 -> 6♦ : ♣QK",
            "♠QK ♣KA ♦8QK ♥78JQA  0 -> 7♥ : ♠QK",
            "♠78JQA ♣8QK ♦QK ♥KA  0 -> 7♠ : ♦QK",
            "♠79XJQA ♣8JA ♦XK ♥8  2  6♠ -> 6♠ : ♦X ♥8",
            "♠79XJQA ♦8XJKA  2 -> 8♠",
            "♣8 ♦89XKA ♥79XQKA  1 -> 9♦ : ♣8 ♥7",

            "♠79JQA ♣JA ♦XK ♥79  2 -> Pass",
            "♠JA ♣79JQA ♦XK ♥78  2 -> Pass",
            "♠JQK ♣8QA ♦8A ♥QKA  0 -> 7-",
            "♠79XJQA ♣8JA ♦K ♥8  2 -> 6♠",

            "♠XJQ ♣89JQKA ♦7X ♥A  0 -> 8♣ : ♦7X",
            "♠XJQ ♣89JQKA ♦7X ♥A  1  7♠ -> 7♣ : ♦7X",
            "♠79XJQA ♣8JA ♦XK ♥8  2  6♠ -> 6♠ : ♦X ♥8",

            "♠7JQ ♣QA ♦XQKA ♥89X  0  7- -> 8♦ : ♣Q ♥8",
            "♠8KA ♣78KA ♦7 ♥7JQK  0  8♦ -> 8♥ : ♠8 ♦7", // overbidding
            "♠XJQ ♣89JQKA ♦7X ♥A  1  7♦ -> 8♣ : ♦7X",
            "♠XJQ ♣89JQKA ♦7X ♥A  1  8♠ -> 8♣ : ♦7X",
            "♠79XJQA ♣8JA ♦XK ♥8  2  6♥ -> 7♠ : ♦X ♥8",
            "♠7JQ ♣QA ♦XQKA ♥89X  1 -> 6♦ : ♣Q ♥8",
            "♣A ♦79XQA ♥79XQKA  0 -> 8♥ : ♦79",
            "♦789XQA ♥79XQKA  0 -> 9♦ : ♥79",
            "♠KA ♣9XJ ♦KA ♥78JQA  1 -> 8♥ : ♣9X",
            "♠9JQKA ♣8QA ♦8A ♥QK  0 -> 8♠ : ♣8 ♦8",
            "♠JQA ♣89JA ♦Q ♥89KA  0 -> 6♥ : ♠J ♦Q",
            "♣8 ♦89XKA ♥79XQKA  0 -> 9♥ : ♣8 ♦8",
            "♣8 ♦89XKA ♥79XQKA  1 -> 9♦ : ♣8 ♥7",
            "♠JQA ♣89JA ♦Q ♥89KA  1 -> 6♣ : ♠J ♦Q",
        };
        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split(" -> ");
            String[] _parts = parts[0].split("  ");
            CardSet hand = new CardSet(util.toCardList(_parts[0]));
            Assert.assertTrue("invalid test", hand.size() >= 11);
            int elderhand = Integer.parseInt(_parts[1]);
            Bid minBid = Bid.BID_6S;
            if (_parts.length >= 3) {
                minBid = Bid.fromName(_parts[2]);
            }
            _parts = parts[1].split(" : ");
            Bid expectedBid = Bid.fromName(_parts[0]);
            CardSet expectedDrops = null;
            if (_parts.length >= 2) {
                expectedDrops = new CardSet(util.toCardList(_parts[1]));
            }

            Bidder.PlayerBid playerBid = Bidder.getInstance().getBid(hand, minBid, elderhand, hand.size() - 10);
            Bid bid = playerBid.toBid();
            if (expectedDrops != null) {
                Assert.assertEquals("drops", expectedDrops, playerBid.drops);
            }
            Assert.assertEquals("bid", expectedBid, bid);
        }
    }

}