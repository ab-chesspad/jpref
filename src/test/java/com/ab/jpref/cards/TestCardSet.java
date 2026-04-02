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
 * Created: 8/19/2025
 */

package com.ab.jpref.cards;


import com.ab.jpref.config.Config;
import com.ab.util.Logger;
import com.ab.util.Pair;
import com.ab.util.Util;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.InputMismatchException;
import java.util.Iterator;

public class TestCardSet {
    public static final int NOP = Config.NOP;
    static final Util util = Util.getInstance();

    @Test
    public void testMisere() {
        String[] sources = {
            "♥8 : 0",
            "♥89QKA : 0",
            "♥789QKA : 1",
            "♥789QA : 1",
            "♥789A : 0",
            "♠789A : 0",
            "♠78 : 1",
            "♠79 : 1",
            "♠7X : 0",
            "♠789A ♣7 : 0", // ♠789A fails before next suit kicks in
            "♠789JA ♣78 : !",
        };

        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split("\\s+:\\s+");
            com.ab.jpref.cards.CardSet cardSet = new CardSet(util.toCardList(parts[0]));
            String expected = parts[1].trim();
            try {
                boolean res = cardSet.isClean4Misere();
                Assert.assertEquals(expected.equals("1"), res);
            } catch (InputMismatchException e) {
                Assert.assertTrue(expected.equals("!"));
            }
        }
    }

    @Test
    public void testBuildIterator() {
        String[] sources = {
            // hands -> list
            "♥7XQA  ♠78QA  ♥9K ♦8 -> ♥7XA",
            "♥9JA  ♣89J  ♣Q ♥XQ -> ♥9JA",
            "♠78XQ ♣89J ♦7J ♥8  ♣7QKA ♦9A ♥9JKA  ♠9JA ♣X ♦XQK ♥7XQ -> ♠7XQ ♣8J ♦7J ♥8",
            "♣9JK  ♣8  ♣A -> ♣9",
            "♣8XK  ♣Q  ♣9 -> ♣8XK",
            "♣7XQ  ♣K  ♣A -> ♣7",
            "♥9JKA  ♣89J ♥8  ♣Q ♥7XQ -> ♥9JK",
            "♠79QK  ♠8X ♣79J  ♠JA ♣8A -> ♠79Q",
            "♠8XQA  ♦789X  ♠79JK -> ♠8XQA",
            "♣7JQ  ♣K  ♣A -> ♣7",
            "♣8JQ  ♣9A  ♣K -> ♣8J",
            "♣78JQ  ♣9K  ♣A -> ♣7J",
            "♥7XQA  ♠78QA  ♥9K ♦8 -> ♥7XA",
            "♥7XQ  ♠78QA  ♥9A ♦8 -> ♥7X",
            "♥7XQ  ♠78QA  ♥9K ♦8 -> ♥7X",
            "♣7XQ  ♠78QA  ♣9K ♥8 -> ♣7X",
            "♣7XQA  ♠78QA  ♣9K ♥8 -> ♣7XA",
            "♣8XQA  ♠78QA  ♣9K ♥8 -> ♣8XA",
        };

        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split(" : | -> ");
            String[] _parts = parts[0].split("  ");
            CardSet[] hands = new CardSet[NOP];
            for (int i = 0; i < _parts.length; ++i) {
                hands[i] = new CardSet(util.toCardList(_parts[i]));
            }
            CardSet union = hands[1].union(hands[2]);
            CardSet.CardIterator it = hands[0].buildIterator(union);
            Assert.assertEquals(parts[1], it.toString());
        }
    }

    @Test
    public void testBuildReverseIterator() {
        String[] sources = {
            // hands -> list
            "♥7XQA  ♠78QA  ♥9K ♦8 -> ♥7QA",
            "♠8XQA  ♦789X  ♠79JK -> ♠8XQA",
            "♣7JQ  ♣K  ♣A -> ♣Q",
            "♠79QK  ♠8X ♣79J  ♠JA ♣8A -> ♠79K",
            "♣8JQ  ♣9A  ♣K -> ♣8Q",
            "♣78JQ  ♣9K  ♣A -> ♣8Q",
            "♥7XQ  ♠78QA  ♥9A ♦8 -> ♥7Q",
            "♥7XQ  ♠78QA  ♥9K ♦8 -> ♥7Q",
            "♣7XQ  ♠78QA  ♣9K ♥8 -> ♣7Q",
            "♣7XQA  ♠78QA  ♣9K ♥8 -> ♣7QA",
            "♣8XQA  ♠78QA  ♣9K ♥8 -> ♣8QA",
        };

        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split(" : | -> ");
            String[] _parts = parts[0].split("  ");
            CardSet[] hands = new CardSet[NOP];
            for (int i = 0; i < _parts.length; ++i) {
                hands[i] = new CardSet(util.toCardList(_parts[i]));
            }
            CardSet union = hands[1].union(hands[2]);
            CardSet.CardIterator it = hands[0].buildReverseIterator(union);
            CardSet res = new CardSet();
            while (it.hasNext()) {
                res.add(it.next());
            }
//            Logger.println(res.toString());
            Assert.assertEquals(parts[1], res.toString());
        }
    }

    @Test
    public void testBuildIteratorWithOthers() {
        String[] sources = {
            "♣79JQ  ♣8K  ♣A -> ♣9",
            // self, friend, foe -> list
            "♥7XQ  ♥8A  ♥9JK -> ♥XQ",   // todo: ♥7XQ
//            "♥7XQ  ♥8A  ♥9JK -> ♥7XQ",
            "♠JQA ♣89JA ♥7XQ  ♠8XK ♣7XK ♦XK ♥8A  ♠9 ♦789JQA ♥9JK -> ♠J ♣8 ♥XQ",
//            "♠JQA ♣89JA ♥7XQ  ♠8XK ♣7XK ♦XK ♥8A  ♠9 ♦789JQA ♥9JK -> ♠J ♣8 ♥7XQ",
            "♠79QK  ♠JA  ♠8X -> ♠Q",
            "♠89QA  ♠7X  ♠JK -> ♠8QA",
            "♠9A  ♠XJK  . -> ♠9A",
            "♠9A ♣QK ♦9 ♥89A  ♠XJK ♦78QK ♥X  ♣XA ♦XA ♥7JQK -> ♠9A ♣Q ♦9 ♥8A",
            "♦Q  ♦78A  ♦9XJK -> ♦Q",
            "♣7KA  ♣9JQ  . -> ♣7K",
            "♠J ♣7KA ♦Q ♥89JKA  ♠9 ♣9JQ ♦78A ♥7XQ  ♠78XQKA ♦9XJK -> ♠J ♣7K ♦Q ♥8",
            "♠79QK  ♠JA  ♠8X -> ♠Q",
            "♠79QK  .  ♠8X -> ♠79Q",
            "♠79QK  ♣8A  ♠8X ♣79J -> ♠79Q",
            "♠79QK  ♠JA ♣8A  ♠8X ♣79J -> ♠Q",
            "♦78Q  ♦J  ♦XKA -> ♦7Q",
            "♣JK ♦78Q ♥A  ♦J ♥78XJQ  ♠Q ♦XKA ♥9K -> ♣J ♦7Q ♥A",
            "♣79JK  ♣8XQA  . -> ♣9",
            "♣8XQA  ♣79JK  . -> ♣8",
            "♣7XK  ♣89JA  . -> ♣X",
            "♣89JA  ♣7XK  . -> ♣8",
            "♠8XQA  ♦789X  ♠79K ♣8 -> ♠8XA",
            "♣8K  ♣79JQ  ♣A -> ♣8",
        };

        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split(" : | -> ");
            String[] _parts = parts[0].split("  ");
            CardSet[] hands = new CardSet[NOP];
            for (int i = 0; i < _parts.length; ++i) {
                if (".".equals(_parts[i])) {
                    hands[i] = new CardSet();
                } else {
                    hands[i] = new CardSet(util.toCardList(_parts[i]));
                }
            }
            CardSet.CardIterator it = hands[0].buildIterator(hands[1], hands[2]);
            Assert.assertEquals(parts[1], it.toString());
        }
    }

    @Test
    public void testBuildReverseIteratorWithOthers() {
        String[] sources = {
            // self, friend, foe -> reverse list
            "♠79QK  ♠JA  ♠8X -> ♠Q",
            "♠79QK  ♠JA ♣8A  ♠8X ♣79J -> ♠Q",
            "♣79JQ  ♣8K  ♣A -> ♣9",
            "♠9A  ♠XJK  . -> ♠A9",
            "♠9A ♣QK ♦9 ♥89A  ♠XJK ♦78QK ♥X  ♣XA ♦XA ♥7JQK -> ♥A8 ♦9 ♣Q ♠A9",
            "♦Q  ♦78A  ♦9XJK -> ♦Q",
            "♣7KA  ♣9JQ  . -> ♣K7",
            "♠J ♣7KA ♦Q ♥89JKA  ♠9 ♣9JQ ♦78A ♥7XQ  ♠78XQKA ♦9XJK -> ♥8 ♦Q ♣K7 ♠J",
            "♠79QK  .  ♠8X -> ♠Q97",
            "♠79QK  ♣8A  ♠8X ♣79J -> ♠Q97",
            "♦78Q  ♦J  ♦XKA -> ♦Q7",
            "♣JK ♦78Q ♥A  ♦J ♥78XJQ  ♠Q ♦XKA ♥9K -> ♥A ♦Q7 ♣J",
            "♣79JK  ♣8XQA  . -> ♣9",
            "♣8XQA  ♣79JK  . -> ♣8",
            "♣7XK  ♣89JA  . -> ♣X",
            "♣89JA  ♣7XK  . -> ♣8",
            "♠8XQA  ♦789X  ♠79K ♣8 -> ♠AX8",
            "♣8K  ♣79JQ  ♣A -> ♣8",
            "♠JQA ♣89JA ♥7XQ  ♠8XK ♣7XK ♦XK ♥8A  ♠9 ♦789JQA ♥9JK -> ♥QX ♣8 ♠J",     // todo: ♥QX7 ♣8 ♠J
//            "♠JQA ♣89JA ♥7XQ  ♠8XK ♣7XK ♦XK ♥8A  ♠9 ♦789JQA ♥9JK -> ♥QX7 ♣AJ9 ♠AQ",
            "♣89JA  ♣7XK  . -> ♣8",
            "♠79QK  ♠JA ♣8A  ♠8X ♣79J -> ♠Q",
            "♠8XQA  ♦789X  ♠79K ♣8 -> ♠AX8",
            "♣8K  ♣79JQ  ♣A -> ♣8",
            "♣79JQ  ♣8K  ♣A -> ♣9",
        };

        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split(" : | -> ");
            String[] _parts = parts[0].split("  ");
            CardSet[] hands = new CardSet[NOP];
            for (int i = 0; i < _parts.length; ++i) {
                if (".".equals(_parts[i])) {
                    hands[i] = new CardSet();
                } else {
                    hands[i] = new CardSet(util.toCardList(_parts[i]));
                }
            }
            CardList res = util.toCardList(parts[1]);
            CardSet.CardIterator it = hands[0].buildReverseIterator(hands[1], hands[2]);
//            Assert.assertEquals(parts[1], it.toString());
            CardList cardList = new CardList();
            while (it.hasNext()) {
                Card c0 = res.removeFirst();
                Card c1 = it.next();
//                Assert.assertEquals(c0, c1);
                cardList.add(c1);
            }
//            Assert.assertTrue(String.format("missing cards %s", res.toString()), res.isEmpty());
            Assert.assertEquals(parts[1], cardList.toString());

//            Logger.println(it.toString());
        }
    }

    @Test
    @Ignore("used to test various CardSet operations")
    public void test_1() {
        String[] sources = {
            "♠78K ♣QA ♦7X ♥89QKA",
            "♣9",
            "♣79Q ♦8XJ",
        };

        String sep;
        CardSet cardSet;
        Card card;
        int i;
        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split(" ");
            CardList cardList = util.toCardList(source);
            cardSet = new CardSet(cardList);
            System.out.println(cardSet.toColorString());
            Assert.assertEquals(source, cardSet.toString());

            Iterator<Card> iterator = cardSet.reverseIterator();
            while (iterator.hasNext()) {
                card = iterator.next();
                System.out.println(card);
            }
            Logger.println("done");
        }

    }

    @Test
    @Ignore("used to test various CardSet operations")
    public void test() {
        String[] sources = {
//            "♥KA",
//            "♠7",
//            "♣QA",
            "♠X ♣89XJ ♦9KA ♥7JQKA",
            "♠KA ♣7JQA ♦XJQKA ♥A",
            "♠KA ♣7JQA ♦XJQKA",
            "♣7JQA ♦XJQKA ♥7",
//            "♥89K",
            "♥89KA",
            "♠78K ♣QA ♦7X ♥89QK",
            "♣9",
            "♣79Q ♦8XJ",
        };

        String sep;
        CardSet cardSet;
        Card card, c;
        int i;

        card = Card.fromName("♦A");
        int v = card.toInt();
        c = Card.fromValue(v);
        Assert.assertEquals(c, card);

        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split(" ");
            CardList cardList = util.toCardList(source);
            cardSet = new CardSet(cardList);
            System.out.println(cardSet.toColorString());
            Assert.assertEquals(source, cardSet.toString());

            CardList list = cardSet.list().toCardList();
            System.out.println(list.toColorString());

            list = cardSet.list(cardSet.first().getSuit()).toCardList();
            System.out.println(list.toColorString());
            list = cardSet.list(Card.Suit.HEART).toCardList();
            System.out.println(list.toColorString());

            card = cardSet.last();
            card = cardSet.first();

//            CardSet set = cardSet.list(Card.Suit.fromCode('♣'));
            CardSet set = new CardSet(cardSet);
            set.remove(set.list(Card.Suit.fromCode('♣')));

/*
            boolean b = set.equiv(new Card("♣J"), new Card("♣X"));
            set.remove(new Card("♣X"));
            boolean b1 = set.equiv(new Card("♣J"), new Card("♣8"));

            set = cardSet.list(Card.Suit.fromCode('♥'));
            b = set.equiv(new Card("♥J"), new Card("♥A"));
            set.remove(new Card("♥Q"));
            b1 = set.equiv(new Card("♥J"), new Card("♥A"));
*/

            card = cardSet.prev(Card.fromName("♠7"));
            card = cardSet.prev(Card.fromName("♦X"));
            card = cardSet.prev(Card.fromName("♦7"));
            card = cardSet.list(Card.Suit.DIAMOND).prev(Card.fromName("♦7"));

            card = cardSet.list(Card.Suit.DIAMOND).next(Card.fromName("♦X"));

            card = cardSet.last();
            card = cardSet.list(Card.Suit.DIAMOND).last();

            card = cardSet.anyCard();
            card = cardSet.anyCard(Card.Suit.CLUB);
            card = cardSet.anyCard(Card.Suit.HEART);
            card = cardSet.anyCard(Card.Suit.SPADE);

            card = cardSet.next(Card.fromName("♥Q"));
            card = cardSet.list(Card.Suit.DIAMOND).next(Card.fromName("♦X"));

            c = cardSet.first();

            i = -1;
//            Iterator<CardList> suitIterator = cardSet.suitIterator(Card.Suit.DIAMOND);
            Iterator<CardSet> suitIterator = cardSet.suitIterator();
            while (suitIterator.hasNext()) {
                CardSet cardList1 = suitIterator.next();
                Assert.assertEquals(parts[++i], cardList1.toString());
            }
            Assert.assertEquals(parts.length - 1, i);

            c = cardSet.anyCard(null);
            System.out.printf("any %s\n", c.toColorString());
            c = cardSet.anyCard(Card.Suit.SPADE);
            System.out.printf("any ♠ %s\n", c);
//            Assert.assertNotNull(c);
            c = cardSet.anyCard(Card.Suit.DIAMOND);
            System.out.printf("any ♦ %s\n", c.toColorString());
//            Assert.assertEquals(Card.Suit.DIAMOND, c.getSuit());
            c = cardSet.anyCard(Card.Suit.CLUB);
            System.out.printf("any ♣ %s\n", c.toColorString());
//            Assert.assertEquals(Card.Suit.CLUB, c.getSuit());

            System.out.println(cardSet.toColorString());
            sep = "";
            i = -1;
            CardSet.CardIterator iterator = cardSet.iterator();
            while (iterator.hasNext()) {
                card = iterator.next();
                System.out.print(sep + card.toColorString());
                Assert.assertEquals(cardList.get(++i), card);
                int val = card.toInt();
                c = Card.fromValue(val);
                Assert.assertEquals(c, card);
                sep = ", ";
//                if (i == 2) {
//                    iterator.remove();
//                }
            }
            System.out.println();

            i = 2;
            Card c1 = cardSet.get(i);
            c = cardSet.remove(i);
            Assert.assertEquals(c1, c);
            Assert.assertEquals(cardList.get(i), c);

            c = cardSet.last();
            Assert.assertEquals(cardList.last(), c);
            c1 = cardSet.removeLast();
            Assert.assertEquals(c1, c);
            Assert.assertEquals(cardList.last(), c1);

        }
 //*/
    }

    @Test
    public void testMinWantedTricks() {
        String[] sources = {
            // my, left, right, minTricks, startFrom
            "AQX KJ9 87 1 A",
            "9XJK Q 78A 3 K",
            "9XJK 78A Q 3 K",
            "AK987 QJX  4 A",
            "AK987 QJ X 5 A",
            "AK87 QX9 J 3 A",
            "AQJ KX9 8 2 A",
            "AQX K97 J8 2 A",
            "AQX K7 J98 2 A",
        };
        for (String source : sources) {
            System.out.printf("%s\n", source);
            String[] parts = source.split(" ");

            CardSet[] cardLists = new CardSet[3];
            for (int i = 0; i < cardLists.length; ++i) {
                String suit = parts[i]; // .replaceAll(".(?!$)", "$0s");
                if (!suit.isEmpty()) {
                    suit = "♠" + suit;
                }
                cardLists[i] = new CardSet(util.toCardList(suit));
            }

            int minTricks = Integer.parseInt(parts[3]);           // is problematic when holes > 0
            Pair<Integer, Card> p = cardLists[0].minWantedTricks(cardLists[1], cardLists[2]);
            Assert.assertEquals("minMeStart", minTricks, (int)p.first);
            if (parts.length <= 4) {
                System.out.println(p.second);
                continue;
            }
            Card firstCard = Card.fromName("♠" + parts[4]);
            // first card depends on calling Iterator or reverseIterator
            Assert.assertEquals("minMeStart", firstCard, p.second);
        }
    }

    @Test
    public void testListData() {
        String[] sources = {
            // suit, left, right, tricksTheyStart, tricksMeStart, good, ok1stMove
//            " 789JQKA 789xJQKA 0 0 0 0",    // empty
            "7X 8J 9 1 1 1 1",
            "79J 8QK XA 0 1 1 0",           // хорошая беспроблемная
            "79J XA 8QK 0 0 1 1",           // хорошая беспроблемная
            "89K 7XJQA 7XJQA 1 2 0 0",      // плохая проблемная
            "8XK 79JQA 79JQA 2 3 0 0",      // плохая проблемная
            "7X 8J 9 1 1 1 1",              // хорошая беспроблемная
            "8KA 79xJQ 79xJQ 2 3 0 0",      // плохая проблемная
            "XQA JK JK 0 1 1 0",            // хорошая проблемная
            "XQKA 79 8 2 2 0 0",            // плохая беспроблемная
            "9JK 78xQA 78xQA 3 3 0 0",      // плохая беспроблемная
            "8XQ 79JKA 79JKA 2 3 0 0",      // плохая проблемная
            "79Q 8XJKA 8XJKA 1 1 1 0",      // хорошая беспроблемная (start with 7!)
            "AK 7J 8xQ 2 2 0 1",            // плохая беспроблемная
            "8JQK 79 xA 0 1 0 0",           // плохая проблемная
            "A  J 1 1 0 1",                 // плохая беспроблемная
            "89x 7JQKA 7JQKA 1 1 0 1",      // плохая беспроблемная.
            "78Q 9XJKA 9XJKA 1 1 1 1",      // хорошая беспроблемная
            "8JK xQA xQA 0 1 1 0",          // хорошая проблемная (start with 8!)
            "8J xQKA xQKA 0 1 1 0",         // хорошая проблемная
            "789K xJQA xJQA 0 1 1 0",       // хорошая проблемная;
            "7XK 89JQA 89JQA 1 2 1 0",      // хорошая проблемная
            "8JK 9xQA 9xQA 1 2 1 0",        // хорошая проблемная
            "79J 8xQKA 8xQKA 0 1 1 0",      // хорошая проблемная (start with 7!)
            "XA 789JQK 789JQK 2 2 0 1",     // плохая беспроблемная
            "9XK 78JQA 78JQA 2 3 0 0",      // плохая проблемная
            "8X 79JQKA 79JQKA 1 2 0 0",     // плохая проблемная;
            "78J 9xQKA 9xQKA 0 1 1 0",      // хорошая проблемная;

            "89 7xJQKA 7xJQKA 1 1 0 1",     // плохая беспроблемная.
            "QKA 789xJ 789xJ 3 3 0 1",      // плохая беспроблемная;
            "JQ 789xKA 789xKA 2 2 0 1",     // плохая беспроблемная;
            "xk 789A 789A 2 2 0 1",         // плохая беспроблемная;
            "XJK 789QA 789QA 3 3 0 1",      // плохая беспроблемная;

            "789 xJQKA xJQKA 0 0 1 1",      // хорошая беспроблемная;
            "89x JQKA JQKA 0 0 1 1",        // хорошая беспроблемная
            "79x JQKA JQKA 0 0 1 1",        // хорошая беспроблемная
            "8xJ QKA QKA 0 0 1 1",          // хорошая беспроблемная

            "XKA 789JQ 789JQ 3 3 0 1",      // плохая беспроблемная;
            "8A 79xJQK 79xJQK 1 2 0 0",     // плохая проблемная;

            "7KA 89xJQ 89xJQ 2 2 1 1",      // хорошая беспроблемная;
            "7QA 89xJK 89xJK 2 2 1 1",      // хорошая беспроблемная;
/*
            "7JK 789xJQKA 789xJQKA  2 1 0 1",    // хорошая проблемная;
            "89Q 7K 0 1 1 1",  // хорошая проблемная;
            "89Q 7A 0 1 1 1",  // хорошая проблемная;
            "79J K 0 1 0 2",   // хорошая проблемная;
            "79J  0 1 0 3",    // хорошая проблемная;
            "79Q  1 1 0 2",    // хорошая проблемная;
            "79A  3 1 0 0",    // хорошая проблемная.
            "79JA  1 1 0 0",   // хорошая проблемная.
            "89Q 7 0 1 1 2",   // хорошая проблемная;
            "79Q J 0 1 0 2",   // хорошая проблемная
*/
        };

        for (String source : sources) {
            System.out.printf("%s\n", source);
            String[] parts = source.split(" ");

            CardSet[] cardSets = new CardSet[3];
            for (int i = 0; i < cardSets.length; ++i) {
                String suit = parts[i]; // .replaceAll(".(?!$)", "$0s");
                if (!suit.isEmpty()) {
                    suit = "♠" + suit;
                }
                cardSets[i] = new CardSet(util.toCardList(suit));
            }

            int i = 2;
            int tricksTheyStart = Integer.parseInt(parts[++i]);           // is problematic when holes > 0
            int tricksMeStart = Integer.parseInt(parts[++i]);           // is problematic when holes > 0
            boolean good = !parts[++i].equals("0");       // for all-pass, includes smallest rank
            CardSet.ListData listData;

            listData = cardSets[0].maxUnwantedTricks(cardSets[1], cardSets[2], 0);
            Assert.assertEquals("tricksMeStart", tricksMeStart, listData.maxMeStart);
            Assert.assertEquals("good", good, listData.good);

            CardSet.ListData listData1 = cardSets[0].maxUnwantedTricks(cardSets[1], cardSets[2], 2);
            Assert.assertEquals("tricksTheyStart", tricksTheyStart, listData1.maxTheyStart);
            listData = cardSets[0].maxUnwantedTricks(cardSets[1], cardSets[2], 0);
            Assert.assertEquals("tricksMeStart", tricksMeStart, listData.maxMeStart);
            Assert.assertEquals("good", good, listData.good);
        }
    }

    @Test
    public void testOptimalStart() {
        String[] sources = {
            // suit, left, right -> start tricks
            "79J XA 8QK -> 9 0",
            "7X 8J 9 -> 7 1",
            "79 X 8Q -> 9 0",
        };

        for (String source : sources) {
            System.out.printf("%s\n", source);
            String[] _parts = source.split(" -> ");
            String[] parts = _parts[0].split(" ");

            CardSet[] cardSets = new CardSet[3];
            for (int i = 0; i < cardSets.length; ++i) {
                String suit = parts[i]; // .replaceAll(".(?!$)", "$0s");
                if (!suit.isEmpty()) {
                    suit = "♠" + suit;
                }
                cardSets[i] = new CardSet(util.toCardList(suit));
            }

            parts = _parts[1].split(" ");
            Card optCard = Card.fromName("♠" + parts[0]);
            Card c = cardSets[0].getOptimalStart(cardSets[1], cardSets[2]);
            Assert.assertEquals("optimalStart", optCard, c);
            int expecteTricks = Integer.parseInt(parts[1]);
            CardSet.ListData listData = cardSets[0].maxUnwantedTricks(cardSets[1], cardSets[2]);
            Assert.assertEquals("tricksTheyStart", expecteTricks, listData.maxTheyStart);
        }
    }
}