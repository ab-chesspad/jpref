package com.ab.util;

import com.ab.jpref.cards.CardSet;
import com.ab.config.Config.Bid;
import org.junit.Assert;
import org.junit.Test;

public class TestBidData {
    private final Util util = Util.getInstance();

    @Test
    public void testGetBid() {
        String[] sources = {
            // hand (11 - 12 cards, elderhand, [min bid] -> bid : drop
            "♣8 ♦89XKA ♥79XQKA  1 -> 9♦ : ♣8 ♥7",
//            "♠7JQ ♣QA ♦XQKA ♥89X  0  7- -> 8♦ : ♣Q ♥8",
            // 11 cards, no drop:
            "♠79JQA ♣JA ♦XK ♥79  2 -> Pass",
            "♠JA ♣79JQA ♦XK ♥78  2 -> Pass",
            "♠JQK ♣8QA ♦8A ♥QKA  0 -> 7-",
            "♠JQK ♣8QKA ♦8A ♥QK  0 -> 8♣",
            "♠JQK ♣8QKA ♦8A ♥QK  1 -> 7♣",
            "♠79XJQA ♦8XJKA  2 -> 9♠",
            "♠79XJQA ♣8JA ♦K ♥8  2 -> 6♠",

            "♠8KA ♣78KA ♦7 ♥7JQK  0  8♦ -> 8♥ : ♠8 ♦7",
            "♠XJQ ♣89JQKA ♦7X ♥A  1  7♦ -> 8♣ : ♦7X",
            "♠XJQ ♣89JQKA ♦7X ♥A  1  8♠ -> 8♣ : ♦7X",
            "♠XJQ ♣89JQKA ♦7X ♥A  0 -> 8♣ : ♦7X",
            "♠XJQ ♣89JQKA ♦7X ♥A  1  7♠ -> 7♣ : ♦7X",
            "♠79XJQA ♣8JA ♦XK ♥8  2  6♠ -> 6♠ : ♦X ♥8",
            "♠79XJQA ♣8JA ♦XK ♥8  2  6♥ -> 7♠ : ♦X ♥8",
            "♠7JQ ♣QA ♦XQKA ♥89X  1 -> 6♦ : ♣Q ♥8",
            "♠78JQA ♣8QK ♦QK ♥KA  0 -> 7♠ : ♦QK",
            "♣A ♦79XQA ♥79XQKA  0 -> 8♥ : ♣A ♦7",
            "♠A ♣QK ♦89XJA ♥8XJK  2 -> 6♦ : ♣QK",
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

            BidData.PlayerBid playerBid = BidData.getBid(hand, minBid, elderhand);
            Bid bid = playerBid.toBid();
            Assert.assertEquals("bid", expectedBid, bid);
            if (expectedDrops != null) {
                Assert.assertEquals("drops", expectedDrops, playerBid.drops);
            }
        }
    }

}