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
 * Created: 8/19/2025
 */

package com.ab.jpref.cards;


import com.ab.jpref.config.Config;
import com.ab.util.Logger;
import com.ab.util.Pair;
import com.ab.util.Util;
import org.junit.Assert;
import org.junit.Test;

import java.util.InputMismatchException;

public class TestCardSet {
    public static final int NOP = Config.NOP;
    static final Util util = Util.getInstance();

    @Test
    public void testIterations() {
        String[] sources = {
            "♠78K ♣QA ♦7X ♥89QKA",
            "♥K",
            "♥A",
            "♣9",
            "♣79Q ♦8XJ",
            "♠79Q ♦8XJ ♥89QK",
        };

        CardSet cardSet;
        Card card;
        for (String source : sources) {
            Logger.println(source);
            CardList cardList = util.toCardList(source);
            cardSet = new CardSet(cardList);
            System.out.println(cardSet.toColorString());
            Assert.assertEquals(source, cardSet.toString());

            int index = -1;
            int bit = 0;
            while ((bit = CardSet.next(cardSet.bitmap, bit)) != 0) {
                card = Card.get(bit);
                Assert.assertEquals(cardList.get(++index), card);
                Logger.printf("%d: %s\n", index, card.toColorString());
            }
            Assert.assertTrue(cardList.size() == ++index);

            Logger.println("\nbackward");
            index = cardList.size();
            bit = 0;
            while ((bit = CardSet.prev(cardSet.bitmap, bit)) != 0) {
                card = Card.get(bit);
                Assert.assertEquals(cardList.get(--index), card);
                Logger.printf("%d: %s\n", index, card.toColorString());
            }
            Assert.assertTrue(index == 0);
        }
        Logger.println("done");
    }

    @Test
    public void testSuitIterations() {
        String[] sources = {
            "♥K",
            "♥A",
            "♠78K ♣QA ♦7X ♥89QKA",
            "♣9",
            "♣79Q ♦8XJ",
            "♠79Q ♥89QK",
        };

        CardSet cardSet;
        Card card = null;
        for (String source : sources) {
            CardList cardList = util.toCardList(source);
            cardSet = new CardSet(cardList);
            System.out.println(cardSet.toColorString());
            Assert.assertEquals(source, cardSet.toString());

            Logger.println("forward");
            int index = -1;
            int bitset = 0;
            while ((bitset = CardSet.bm4NextSuit(cardSet.getBitmap(), bitset)) != 0) {
                int bit = 0;
                while ((bit = CardSet.next(bitset, bit)) != 0) {
                    card = Card.get(bit);
                    Assert.assertEquals(cardList.get(++index), card);
                    Logger.printf("%d: %s\n", index, card.toColorString());
                }
                if (index < cardList.size() - 1) {
                    Assert.assertFalse(cardList.get(index + 1).getSuit().equals(card.getSuit()));
                }
            }
            Assert.assertTrue(cardList.size() == ++index);

            Logger.println("\nbackward");
            index = cardList.size();
            bitset = 0;
            while ((bitset = CardSet.bm4PrevSuit(cardSet.getBitmap(), bitset)) != 0) {
                int bit = 0;
                while ((bit = CardSet.prev(bitset, bit)) != 0) {
                    card = Card.get(bit);
                    Assert.assertEquals(cardList.get(--index), card);
                    Logger.printf("%d: %s\n", index, card.toColorString());
                }
                if (index > 0) {
                    Assert.assertFalse(cardList.get(index - 1).getSuit().equals(card.getSuit()));
                }
            }
            Assert.assertTrue(index == 0);
            Logger.println("====");
        }
        Logger.println("done");
    }

    @Test
    public void testIterate4BuildForward() {
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
            CardList res = util.toCardList(parts[1]);
            String[] _parts = parts[0].split("  ");
            CardSet[] hands = new CardSet[NOP];
            for (int i = 0; i < _parts.length; ++i) {
                hands[i] = new CardSet(util.toCardList(_parts[i]));
            }
            CardSet union = hands[1].union(hands[2]);
            int bitmap = CardSet.bm4buildForward(hands[0].bitmap, union.getBitmap());
            int i = -1;
            int bit = 0;
            while ((bit = CardSet.next(bitmap, bit)) != 0) {
                Card card = Card.get(bit);
                Assert.assertEquals(res.get(++i), card);
            }
            Assert.assertEquals(res.size(), ++i);
        }
    }

    @Test
    public void testIterate4BuildBackward() {
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
            CardList res = util.toCardList(parts[1]);
            String[] _parts = parts[0].split("  ");
            CardSet[] hands = new CardSet[NOP];
            for (int i = 0; i < _parts.length; ++i) {
                hands[i] = new CardSet(util.toCardList(_parts[i]));
            }
            CardSet union = hands[1].union(hands[2]);
            int bitmap = CardSet.bm4buildBackward(hands[0].bitmap, union.getBitmap());
            int i = res.size();
            int bit = 0;
            while ((bit = CardSet.prev(bitmap, bit)) != 0) {
                Card card = Card.get(bit);
                Assert.assertEquals(res.get(--i), card);
            }
            Assert.assertEquals(0, i);
        }
    }

    @Test
    public void testIterate4BuildForwardWithOthers() {
        String[] sources = {
            // hands -> list
            "♣79JQ  ♣8K  ♣A -> ♣9",
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
            CardList res = util.toCardList(parts[1]);
            String[] _parts = parts[0].split("  ");
            CardSet[] hands = new CardSet[NOP];
            for (int i = 0; i < _parts.length; ++i) {
                if (".".equals(_parts[i])) {
                    hands[i] = new CardSet();
                } else {
                    hands[i] = new CardSet(util.toCardList(_parts[i]));
                }
            }
            int bitmap = CardSet.bm4build(hands[0].bitmap, hands[1].getBitmap(), hands[2].getBitmap());
            int i = -1;
            int bit = 0;
            while ((bit = CardSet.next(bitmap, bit)) != 0) {
                Card card = Card.get(bit);
                Assert.assertEquals(res.get(++i), card);
            }
            Assert.assertEquals(res.size(), ++i);
        }
    }

    @Test
    public void testClean4Misere() {
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