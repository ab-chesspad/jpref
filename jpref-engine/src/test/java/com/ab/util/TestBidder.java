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
            "♠A ♣QK ♦89XJA ♥8XJK  0 -> 6♦ : ♣QK",
            "♠A ♣QK ♦89XJA ♥8XJK  1 -> 6♦ : ♣QK",
            "♠A ♣QK ♦89XJA ♥8XJK  2 -> 6♦ : ♣QK",

            "♠78XQKA ♣8X ♦9XJK  2 -> 8♠ : ♣X ♣8",
            "♠78XQKA ♣8X ♦9XJK  1 -> 7♠ : ♣X ♣8",
            "♠78XQKA ♣8X ♦9XJK  0 -> 8♠ : ♣X ♣8",


            "♠8QA ♣A ♦JKA ♥89QKA  0 -> 9- : ♠8 ♠Q",
            "♠XJK ♣8KA ♦JQA ♥9KA  1 -> 6- : ♣8 ♥9",
            "♠9KA ♣KA ♦789A ♥8QA  0 -> 7♦ : ♥8Q",
            "♠79JQKA ♣9JKA ♦A ♥A  1 -> X- : ♣9 ♣J",
            "♠79JQKA ♣9JKA ♦KA  0 -> X- : ♣9 ♣J",
            "♠QKA ♣7JKA ♦8KA ♥KA  1 -> 9- : ♠Q ♦8",
            "♠A ♣QKA ♦89A ♥8XQKA  0 -> X- : ♦89",
            "♠K ♣JKA ♦89A ♥8XQKA  0 -> 8♥ : ♠K ♦8",
            "♠7Q ♣9K ♦8XJKA ♥JA  0 -> Pass",    // 6♦?
            "♠8JQKA ♣XQ ♦9XA ♥7X  0  6- -> 6- : ♥7 ♥X",
            "♠9 ♣89JQ ♦78XA ♥7XQ  1 -> 6♦ : ♥7 ♠9",
            "♠78QK ♣QKA ♦QA ♥JQA  0 -> 7♠ : ♦Q ♥J",
            "♠78QK ♣QKA ♦QA ♥JQA  1 -> 7♠ : ♦Q ♥J",

/////////
            "♠9 ♣89JQ ♦78XA ♥7XQ  0 -> 6♦ : ♥7 ♠9",

            "♠JQK ♣8QKA ♦8A ♥QK  0 -> 8♣ : ♦8",  // todo or not? 7♣
            "♠JQK ♣8QKA ♦8A ♥QK  1 -> 7♣ : ♦8",

            "♠8XJQK ♣JA ♦7K ♥7Q  1 -> Pass",
            "♠78XJQK ♣JA ♦7K ♥7Q  1  6♠ -> 6♠ : ♥7Q",
            "♠78XJQK ♣JA ♦7K ♥7Q  1  6♣ -> 7♠ : ♥7Q",   // overbidding

            "♠78XJQK ♣JA ♦7K ♥Q  1 -> 6♠",
            "♠78QK ♣QKA ♦QA ♥JQA  2 -> 7♠ : ♦Q ♥J",
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

            "♠7JQ ♣QA ♦XQKA ♥89X  0  7- -> 8♦ : ♥89",
            "♠7JQ ♣QA ♦XQKA ♥89X  1 -> 6♦ : ♥89",
            "♠8KA ♣78KA ♦7 ♥7JQK  0  8♦ -> 8♥ : ♠8 ♦7", // overbidding
            "♠XJQ ♣89JQKA ♦7X ♥A  1  7♦ -> 8♣ : ♦7X",
            "♠XJQ ♣89JQKA ♦7X ♥A  1  8♠ -> 8♣ : ♦7X",
            "♠79XJQA ♣8JA ♦XK ♥8  2  6♥ -> 7♠ : ♦X ♥8",
            "♣A ♦79XQA ♥79XQKA  0 -> 8♥ : ♦79",
            "♦789XQA ♥79XQKA  0 -> 9♦ : ♥79",
            "♠KA ♣9XJ ♦KA ♥78JQA  1 -> 8♥ : ♣9X",
            "♠9JQKA ♣8QA ♦8A ♥QK  0 -> 8- : ♣8 ♦8",
            "♠JQA ♣89JA ♦Q ♥89KA  0 -> 6♥ : ♠J ♦Q",
            "♣8 ♦89XKA ♥79XQKA  0 -> 9♥ : ♣8 ♦8",
            "♣8 ♦89XKA ♥79XQKA  1 -> 9♦ : ♣8 ♥7",
            "♠JQA ♣89JA ♦Q ♥89KA  1 -> 6♣ : ♠J ♦Q",

            // by Utyatsky
            // 41.Тхххх  ТКВ  ТК  хх	1  2, 3	 	2 червы	8 пик  7 пик
            "♠79XQA ♣JKA ♦KA ♥9X  2 -> 7♠ : ♥X ♥9",
            "♠79XQA ♣JKA ♦KA ♥9X  0 -> 8♠ : ♥X ♥9",

            // 37.ТКххх  ТКВ  Тх  хх	любая	 	2 червы	7 пик
            "♠79XKA ♣JKA ♦QA ♥9X  0 -> 7♠ : ♥X ♥9",
            // 38.ТКххх  ТКх  Тх  хх	любая	 	2 червы	7 пик
            "♠79XKA ♣9KA ♦QA ♥9X  0 -> 7♠ : ♥X ♥9",

            // Тхххх  ТКx  ТК  хх	1  2, 3	 	2 червы	8 пик  7 пик
            "♠79XJA ♣9KA ♦KA ♥9X  0 -> 8♠ : ♥X ♥9",
            "♠79XJA ♣9KA ♦KA ♥9X  2 -> 7♠ : ♥X ♥9",

            // 26.ТДВхх  ТВххх  хх	любая	-  +	2 бубны	7 треф  7 треф
            "♠79JQA ♣78XJA ♦89  2 -> 7♣ : ♦8 ♦9",

            "♠79XJA ♣78KA ♦XA ♥A  2 -> 8♣ : ♦X ♠7",
            "♠79XJA ♣78KA ♦XA ♥9  2 -> 7♠ : ♥9 ♦X",

            "♠8JA ♣XA ♦789KA ♥9A  2  6♦ -> 7♦ : ♣X ♥9",
            "♠78QKA ♦7X ♥89QKA  1  7♣ -> 9♥ : ♦7 ♦X",
            // "♠78QKA ♦7X ♥89QKA  1  7♣ -> 9♠ : ♦7 ♦X",
            "♠78K ♣QA ♦7X ♥89QKA  1  6♥ -> 6♥ : ♦7 ♦X",
            "♠J ♣78KA ♦XQ ♥89JKA  2  6♥ -> 7♥ : ♦X ♠J",
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
            Assert.assertEquals("bid", expectedBid, bid);
            if (expectedDrops != null) {
                Assert.assertEquals("drops", expectedDrops, playerBid.drops);
            }
        }
    }

}