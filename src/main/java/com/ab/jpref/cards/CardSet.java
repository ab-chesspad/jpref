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
 * Copyright 2025 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 8/19/25
 *
 * 32-card deck as bitset
 */

package com.ab.jpref.cards;

import java.util.*;

public class CardSet implements Iterable<Card> {
    public static final boolean RANDOM_ANY_CARD = false;
    private static int _count = 0;
    static {
        int n = Integer.MAX_VALUE;
        while (n != 0) {
            n &= n - 1;    // clear the least significant bit
            ++_count;
        }
    }
    public static final int TOTAL_BITS = _count + 1;
    public static final int MSB = 1 << _count;
    public static final int SUIT_LIST_LENGTH = TOTAL_BITS / 4;
    public static final int SUIT_LIST_MASK = (1 << (SUIT_LIST_LENGTH)) - 1;
    public static final int TOTAL_RANKS = Card.TOTAL_RANKS - 1;

    public static final Card[] cards = new Card[Card.TOTAL_SUITS * TOTAL_RANKS];
    static {
        int i = -1;
        for (Card.Suit suit : Card.Suit.values()) {
            for (int j = 0; j < TOTAL_RANKS; ++j) {
                Card.Rank rank = Card.Rank.values()[j + 1];
                cards[++i] = new Card(suit, rank);
            }
        }
    }
    public static final Random random = new Random();

    protected int bitmap;

    public CardSet() {}

    public CardSet(Card card) {   // CardSet consists of a single card
        add(card);
    }

    public CardSet(CardSet... cardSets) {
        for (CardSet cardSet : cardSets) {
            add(cardSet);
        }
    }

    public CardSet(Collection<Card> cards) {
        add(cards);
    }

    CardSet(int bitmap) {
        this.bitmap = bitmap;
    }

    public static CardSet getDeck() {
        return new CardSet(-1);
    }

    public static int merge(CardSet[] cardSets) {
        int res = 0;
        for (CardSet cardSet : cardSets) {
            res |= cardSet.bitmap;
        }
        return res;
    }

    public static String toString(CardSet[] cardSets) {
        StringBuilder res = new StringBuilder();
        String sep = "";
        for (CardSet cardSet : cardSets) {
            res.append(sep).append(cardSet.toColorString(false));
            sep = "  ";
        }
        return res.toString();
    }

    protected static int suitOffset(Card.Suit suit) {
        int suitNum = suit.getValue();
        return suitNum * SUIT_LIST_LENGTH;
    }

    protected static int offset(Card card) {
        int suitNum = card.getSuit().getValue();
        int rankNum = card.getRank().getValue() - Card.Rank.SEVEN.getValue();
        return suitNum * SUIT_LIST_LENGTH + rankNum;
    }

    // 1st index of 1
    protected static int first(int bitmap) {
        int index = 0;
        int mask = 1;
        while ((bitmap & mask) == 0) {
            ++index;
            mask <<= 1;
        }
        return index;
    }

    // last index of 1
    protected static int last(int bitmap) {
        if (bitmap == 0) {
            return -1;
        }
        int index = TOTAL_BITS - 1;
        int mask = 1 << TOTAL_BITS - 1;
        while ((bitmap & mask) == 0) {
            --index;
            mask >>>= 1;
        }
        return index;
    }

    protected static int listMask(Card.Suit suit) {
        int mask = SUIT_LIST_MASK << (suit.getValue() * SUIT_LIST_LENGTH);
        return mask;
    }

    public static Card getMin(CardSet leftSuit, CardSet rightSuit) {
        Card res = null;
        if (!rightSuit.isEmpty()) {
            res = rightSuit.first();
        }
        if (!leftSuit.isEmpty()) {
            Card card = leftSuit.first();
            if (res == null || card.compareInTrick(res) < 0) {
                res = card;
            }
        }
        return res;
    }

    public boolean equals(CardSet that) {
        return this.bitmap == that.bitmap;
    }

    public CardSet clone() {
        return new CardSet(bitmap);
    }

    public CardSet complement() {
        CardSet newOne = new CardSet();
        newOne.bitmap = ~this.bitmap;
        return newOne;
    }

    public static int size(int bitmap) {
        int count = 0;
        while (bitmap != 0) {
            bitmap = bitmap & (bitmap - 1);    // clear the least significant bit set
            ++count;
        }
        return count;
    }

    public void clear() {
        bitmap = 0;
    }

    public boolean isEmpty() {
        return bitmap == 0;
    }

    public boolean contains(Card card) {
        int mask = 1 << offset(card);
        return (bitmap & mask) != 0;
    }

    public int size() {
        return size(bitmap);
    }

    public int size(Card.Suit suit) {
        int bits = bitmap & listMask(suit);
        return size(bits);
    }

    public boolean isEmpty(Card.Suit suit) {
        int bits = bitmap & listMask(suit);
        return bits == 0;
    }

    public void add(Card card) {
        int pos = 1 << offset(card);
        bitmap |= pos;
    }

    public CardSet list() {
        return list(null);
    }

    public CardSet list(Card.Suit suit) {
        CardSet cardSet = new CardSet();
        int ind = 0;
        int total = TOTAL_BITS;
        if (suit != null) {
            ind = offset(new Card(suit, Card.Rank.SEVEN));
            total = SUIT_LIST_LENGTH;
        }
        int mask = 1 << ind;
        for (int i = 0; i < total; ++i) {
            if ((bitmap & mask) != 0) {
                cardSet.add(cards[ind + i]);
            }
            mask <<= 1;
        }
        return cardSet;
    }

    public void set(CardSet cardSet) {
        this.bitmap = cardSet.bitmap;
    }

    public Card anyCard() {
        return anyCard(null);
    }

    public Card anyCard(Card.Suit suit) {
        int bitmap = this.bitmap;
        if (suit != null) {
            int mask = SUIT_LIST_MASK << suitOffset(suit);
            bitmap &= mask;
        }

        if (bitmap == 0) {
            bitmap = this.bitmap;   // will return any card
        }

        int size = size(bitmap);
        int rand = size - 1;
        if (RANDOM_ANY_CARD) {
            rand = random.nextInt(size);
        }
        int mask = 1;
        int index = -1;
        while (rand >= 0) {
            if ((bitmap & mask) != 0) {
                --rand;
            }
            mask <<= 1;
            ++index;
        }
        if (index < 0) {
            index = -1;
        }
        return cards[index];
    }

    public void add(CardSet cardSet) {
        this.bitmap |= cardSet.bitmap;
    }

    public void add(Collection<Card> cards) {
        for (Card card : cards) {
            add(card);
        }
    }

    public boolean remove(Card card) {
        int pos = 1 << offset(card);
        boolean res = (bitmap & pos) != 0;
        bitmap &= ~pos;
        return res;
    }

    public void remove(CardSet other) {
        this.bitmap &= ~other.bitmap;
    }

    public void remove(Collection<Card> cards) {
        for (Card card : cards) {
            remove(card);
        }
    }

    public Card remove(int order) {
        return get(order, true);
    }

    private Card get(int order, boolean remove) {
        if (order < 0 || order >= TOTAL_BITS) {
            return null;
        }
        int mask = 1;
        int index = 0;
        for (int i = 0; i < TOTAL_BITS; ++i) {
            if ((bitmap & mask) != 0) {
                if (--order < 0) {
                    break;
                }
            }
            mask <<= 1;
            ++index;
        }
        if (index >= TOTAL_BITS) {
            return null;
        }
        if (remove) {
            bitmap &= ~mask;
        }
        return cards[index];
    }

    public Card first() {
        if (bitmap == 0) {
            return null;
        }
        return cards[first(bitmap)];
    }

    public Card removeFirst() {
        if (bitmap == 0) {
            return null;
        }
        int bit = bitmap ^ (bitmap & (bitmap - 1));
        Card card = cards[first(bit)];
        bitmap &= ~bit;
        return card;
    }

    public Card last() {
        if (bitmap == 0) {
            return null;
        }
        return cards[last(bitmap)];
    }

    public Card removeLast() {
        int index = last(bitmap);
        if (index < 0) {
            return null;
        }
        Card card = cards[index];
        bitmap &= ~(1 << index);
        return card;
    }

    public Card get(int order) {
        return get(order, false);
    }

    public CardList toCardList() {
        CardList cardList = new CardList();
        for (Card card : this) {
            cardList.add(card);
        }
        return cardList;
    }

    /// /////////////////////

    public int getMaxLessThan(Card other) {
        if (other == null) {
            return 0;
        }
        int offset = other.getSuit().getValue() * SUIT_LIST_LENGTH;
        int mask = 1 << offset;
        int rank = other.getRank().getValue() - Card.Rank.SEVEN.getValue();
        int index = -1;
        while (--rank >= 0) {
            if ((mask & bitmap) != 0) {
                ++index;
            }
            mask <<= 1;
        }
        return index;
    }

    public int getOptimalStart(CardSet leftSuit, CardSet rightSuit) {
        if (this.isEmpty()) {
            return -1;
        }
        if (this.size() == 1) {
            return 0;
        }

        if (this.first().compareInTrick(getMin(leftSuit, rightSuit)) > 0 ) {
            return 0;
        }

        // size >= 2
        Card myNext = get(1);
        int next = getMaxLessThan(myNext);
        if (next == 0) {
            if (leftSuit.size() > 1 && rightSuit.size() > 1 ||
                leftSuit.size() == 1 && myNext.compareInTrick(leftSuit.first()) < 0 ||
                rightSuit.size() == 1 && myNext.compareInTrick(rightSuit.first()) < 0) {
                ++next;     // preserve min
            }
        }
        return next;
    }

    public int getMinGreaterThan(Card other) {
        if (other == null) {
            return 0;
        }
        Card.Suit suit = other.getSuit();
        int mask = SUIT_LIST_MASK << suitOffset(suit);
        int bitmap = this.bitmap & mask;
        if (bitmap == 0) {
            return -1;
        }

        int offset = suit.getValue() * SUIT_LIST_LENGTH;
        mask = 1 << offset;
        int rank = other.getRank().getValue() - Card.Rank.SEVEN.getValue();
        int index = -1;
        for (int i = 0; i < SUIT_LIST_LENGTH; ++i) {
            if ((mask & bitmap) != 0) {
                ++index;
                if (rank < 0) {
                    return index;
                }
            }
            --rank;
            mask <<= 1;
        }
        return -1;
    }

    // clone lists and remove duplicates from leftSuit
    CardSet[] simplifyHands(CardSet _leftSuit, CardSet _rightSuit) {
        CardSet rightSuit = _rightSuit.clone();
        CardSet leftSuit = _leftSuit.clone();
        int common = rightSuit.bitmap & leftSuit.bitmap;
        leftSuit.bitmap &= ~common;
        CardSet[] cardLists = new CardSet[3];
        cardLists[0] = this.clone();
        cardLists[1] = leftSuit;
        cardLists[2] = rightSuit;
        return cardLists;
    }

    public ListData maxUnwantedTricks(CardSet leftSuit, CardSet rightSuit, boolean meStart) {
        ListData listData = new ListData(this);
        if (this.isEmpty()) {
            return listData;
        }
        listData.suit = this.first().getSuit();

        CardSet[] cardLists = simplifyHands(leftSuit, rightSuit);
        listData.good = true;
        Card.Rank myMin = this.first().getRank();
        if (listData.good && !leftSuit.isEmpty()) {
            listData.good = myMin.compare(leftSuit.first().getRank()) < 0;
        }
        if (listData.good && !rightSuit.isEmpty()) {
            listData.good = myMin.compare(rightSuit.first().getRank()) < 0;
        }

        boolean keepDoing = true;
        boolean firstMove = true;
        mainLoop:
        while (keepDoing) {
            int n = 0;
            if (!meStart) {
                n = 2;
            }
            if (cardLists[n].isEmpty()) {
                break;
            }
            int j0 = 0;
            if (meStart) {
                j0 = cardLists[n].getOptimalStart(
                    cardLists[(n + 1) % cardLists.length], cardLists[(n + 2) % cardLists.length]);      // 8JQK should start with JQK
            }

            Card firstCard = cardLists[n].get(j0);
            cardLists[n].remove(j0);
            boolean myTrick = true;
            int empty = 0;
            for (int i = 1; i < cardLists.length; ++i) {
                int k = (n + i) % cardLists.length;
                if (cardLists[k].isEmpty()) {
                    if (k == 0) {
                        myTrick = false;
                    }
                    if (++empty >= 2) {
                        break mainLoop;
                    }
                    continue;
                }
                int j;
                if (meStart) {
                    if (cardLists[k].first().compareInTrick(firstCard) < 0) {
                        j = cardLists[k].getMaxLessThan(firstCard);
                    } else {
                        myTrick = false;
                        j = cardLists[k].size() - 1;
                    }
                } else {
                    if (cardLists[k].first().compareInTrick(firstCard) < 0) {
                        if (k == 0) {
                            myTrick = false;
                        }
                        j = cardLists[k].getMaxLessThan(firstCard);
                    } else {
                        j = cardLists[k].size() - 1;
                        firstCard = cardLists[k].get(j);
                    }
                }
                cardLists[k].remove(j);
            }

            if (meStart) {
                if (myTrick) {
                    ++listData.maxMeStart;
                }
            } else {
                if (myTrick) {
                    ++listData.maxTheyStart;
                } else if (firstMove) {
                    meStart = false;
                }
            }
            firstMove = false;
        }

        if (meStart) {
            listData.cardsLeft = cardLists[0].size() - cardLists[2].size();
        }
        return listData;
    }

    public ListData maxUnwantedTricks(CardSet leftSuit, CardSet rightSuit, int elderHand) {
        ListData listData = new ListData(this);
        listData.cardsLeft = 0;
        if (this.isEmpty()) {
            return listData;
        }
        listData.suit = this.first().getSuit();

        CardSet[] cardLists = simplifyHands(leftSuit, rightSuit);
        listData.good = true;
        Card myMin = this.first();
        if (listData.good && !leftSuit.isEmpty()) {
            listData.good = myMin.compareInTrick(leftSuit.first()) < 0;
        }
        if (listData.good && !rightSuit.isEmpty()) {
            listData.good = myMin.compareInTrick(rightSuit.first()) < 0;
        }

        int n = elderHand;
mainLoop:
        while (cardLists[n].size() > 0) {
            int k = (n + 1) % cardLists.length;
            int r = (n + 2) % cardLists.length;
            int j0 = cardLists[n].getOptimalStart(cardLists[k], cardLists[r]);
            Card topCard = cardLists[n].remove(j0);
            int top = n;
            int empty = 0;
            for (int i = 1; i < cardLists.length; ++i) {
                k = (n + i) % cardLists.length;
                int left = (n + i + 1) % cardLists.length;
                boolean lastTrickCard = i == cardLists.length - 1;
                if (cardLists[k].isEmpty()) {
                    if (++empty >= 2) {
                        if (k == 0) {
                            ++listData.cardsLeft;
                        }
                        break mainLoop;
                    }
                    continue;
                }
                int j;
                if (lastTrickCard) {
                    if (topCard.compareTo(cardLists[k].first()) < 0) {
                        j = cardLists[k].size() - 1;    // will take it anyway
                    } else {
                        if (top == 0 || k == 0) {
                            // yield it
                            j = cardLists[k].getMaxLessThan(topCard);
                        } else {
                            j = cardLists[k].size() - 1;    // ok to grab it
                        }
                    }
                } else {
                    // left hand did not play yet
                    if (top == 0) {
                        // self played top
                        if (topCard.compareTo(cardLists[k].first()) < 0 ||
                            topCard.compareTo(cardLists[left].first()) < 0) {
                            j = cardLists[k].size() - 1;    // ok to grab it
                        } else {
                            // both are smaller than top
                            j = cardLists[k].getMaxLessThan(topCard);
                        }
                    } else {
                        // self has not played yet
                        int jLeft = Integer.MAX_VALUE;
                        int jTop = Integer.MAX_VALUE;
                        myMin = cardLists[k].first();
                        if (k == 0) {
                            // self playing
                            if (myMin.compareTo(cardLists[left].first()) < 0) {
                                jLeft = cardLists[k].getMaxLessThan(cardLists[left].first());
                            }
                            if (myMin.compareTo(topCard) < 0) {
                                jTop = cardLists[k].getMaxLessThan(topCard);
                            }
                            j = cardLists[k].size() - 1;
                            if (j > jLeft) {
                                j = jLeft;
                            }
                            if (j > jTop) {
                                j = jTop;
                            }
                        } else {
                            if (myMin.compareTo(cardLists[left].first()) < 0) {
                                // compare to self
                                jLeft = cardLists[k].getMaxLessThan(cardLists[left].first());
                            }
                            if (myMin.compareTo(topCard) < 0) {
                                jTop = cardLists[k].getMaxLessThan(topCard);
                            }
                            j = cardLists[k].size() - 1;    // ok to grab it
                            if (j > jTop) {
                                j = jTop;
                            }
                            if (j > jLeft) {
                                j = jLeft;
                            }
                        }
                    }
                }
                Card card = cardLists[k].remove(j);
                if (card.compareTo(topCard) > 0) {
                    topCard = card;
                    top = k;
                }
            }

            if (top == 0) {
                if (elderHand == 0) {
                    ++listData.maxMeStart;
                } else {
                    ++listData.maxTheyStart;
                }
            }
            n = top;
        }

        if (n == 0) {
            listData.cardsLeft += cardLists[0].size();
        }
        return listData;
    }

    public ListData maxUnwantedTricks(CardSet leftSuit, CardSet rightSuit) {
        final CardSet dummy = new CardSet();
        Card myMin = this.first();
        Card myNextMin;
        Card myMax = this.last();
        int mySize = this.size();
        int leftSize = leftSuit.size();
        int rightSize = rightSuit.size();
        CardSet otherSuits = new CardSet(leftSuit);
        otherSuits.add(rightSuit);
        ListData listData = this.maxUnwantedTricks(dummy, otherSuits.list(), 2);
        try {
            if (this.size() <= 1 || myMin.compareInTrick(getMin(leftSuit, rightSuit)) > 0) {
                // do nothing, myMin is not the lowest
            } else if (this.size() <= 4 && myMax.getRank().compare(Card.Rank.ACE) >= 0 &&
                rightSize > 0 && leftSize > 0) {
                listData.maxTheyStart = 0;
            } else if (this.size() <= 3 && myMax.getRank().compare(Card.Rank.QUEEN) >= 0 && rightSize >= 3 &&
                myMax.compareInTrick(rightSuit.get(mySize - 1)) < 0) {
                listData.maxTheyStart = 0;
            } else if ((myNextMin = this.get(1)).getRank().compare(Card.Rank.TEN) >= 0) {
                // 7X, et al.
                if (leftSize >= 2 && myNextMin.compareInTrick(leftSuit.first()) > 0 ||
//                leftSize < 2 && myNextMin.compareInTrick(rightSuit.get(1)) > 0 ||  // "7X 8 9JQKA 0"
                    rightSize >= 2 && myNextMin.compareInTrick(rightSuit.get(1)) > 0) {
                    // ловится:
                } else {
                    listData.maxTheyStart = 0;
                }
            }
        } catch (java.lang.IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        return listData;
    }

    public ListData getUnwantedTricks(CardSet leftSuit, CardSet rightSuit) {
        ListData listData = maxUnwantedTricks(leftSuit, rightSuit, 0);
        ListData listData1 = maxUnwantedTricks(leftSuit, rightSuit, 2);
        // consolidate:
        listData.maxTheyStart = listData1.maxTheyStart;
        return listData;
    }

    public ListData minWantedTricks(CardSet leftSuit, CardSet rightSuit, int elderHand) {
        ListData listData = new ListData(this);
        if (this.isEmpty()) {
            return listData;
        }
        // todo!
/*
//        listData.suit = this.get(0).getSuit();
        listData.suitNum = this.get(0).getSuit().getValue();
        simplifyHands(listData, leftSuit, rightSuit);
        int[] order;
        if (meStart) {
            order = new int[] {0,1,2};
        } else {
            order = new int[] {2,1,0};
        }

        CardList[] cardLists = new CardList[3];
        cardLists[0] = listData.thisSuit;
        cardLists[1] = listData.leftSuit;
        cardLists[2] = listData.rightSuit;

        int n = order[0];
        while (cardLists[n].size() > 0) {
            int j = cardLists[n].size() - 1;
            Card.Rank firstCard = cardLists[n].get(j).getRank();
            cardLists[n].remove(j);
            boolean myTrick = true;
            for (int i = 1; i < cardLists.length; ++i) {
                int k = order[i];
                if (cardLists[k].size() == 0) {
                    if (k == 0) {
                        myTrick = false;
                    }
                    continue;
                }

                int m = cardLists[k].size() - 1;
                if (meStart) {
                    if (cardLists[k].get(m).getRank().compare(firstCard) < 0) {
                        j = 0;
                    } else {
                        j = cardLists[k].getMinGreaterThan(firstCard);
                        firstCard = cardLists[k].get(j).getRank();
                        myTrick = false;
                    }
                } else {
                    if (cardLists[k].get(m).getRank().compare(firstCard) < 0) {
                        if (k == 0) {
                            myTrick = false;
                        }
                        j = 0;
                    } else {
                        j = cardLists[k].getMinGreaterThan(firstCard);
                        firstCard = cardLists[k].get(j).getRank();
                    }
                }
                cardLists[k].remove(j);
            }

            if (meStart) {
                if (myTrick) {
                    ++listData.minMeStart;
                }
            } else {
                if (myTrick) {
                    ++listData.minTheyStart;
                }
            }
        }
*/
        return listData;
    }

/////////////////////////////////
    @Override
    public String toString() {
        return toColorString(false);
    }

    public String toColorString() {
        return toColorString(true);
    }

    public String toColorString(boolean color) {
        Iterator<Card> iterator = this.iterator();
        StringBuilder sb = new StringBuilder();
        Card.Suit suit = null;
        String sep = "";
        while (iterator.hasNext()) {
            Card c = iterator.next();
            Card.Suit s = c.getSuit();
            if (!s.equals(suit)) {
                suit = s;
                if (color) {
                    if (s.equals(Card.Suit.DIAMOND) || s.equals(Card.Suit.HEART)) {
                        sb.append(Card.ANSI_RED);
                    } else {
                        sb.append(Card.ANSI_RESET);
                    }
                }
                sb.append(sep).append(suit.toString());
                sep = " ";
            }
            sb.append(c.getRank());
        }
        if (color) {
            sb.append(Card.ANSI_RESET);
        }
        return new String(sb);
    }

    public Iterator<CardSet> listIterator() {
        return listIterator(null);
    }

    public Iterator<CardSet> listIterator(final Card.Suit exclude) {
        return new Iterator<CardSet>() {
            int index = nextSuitNum(-1);

            public int nextSuitNum(int index) {
                // skip empty lists
                int suitBitMap = 0;
                do {
                    if (++index >= Card.TOTAL_SUITS) {
                        break;
                    }
                    Card.Suit suit = Card.Suit.values()[index];
                    if (suit.equals(exclude)) {
                        continue;
                    }
                    suitBitMap = bitmap & listMask(suit);
                } while(suitBitMap == 0);
                return index;
            }

            @Override
            public boolean hasNext() {
                return index < Card.TOTAL_SUITS;
            }

            @Override
            public CardSet next() {
                CardSet cardSet = list(Card.Suit.values()[index]);
                index = nextSuitNum(index);
                return cardSet;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("not supported");
            }
        };
    }

    @Override
    public Iterator<Card> iterator() {
        return new Iterator<Card>() {
            int bitmap = CardSet.this.bitmap;
            int index = 0;

            @Override
            public boolean hasNext() {
                return bitmap != 0;
            }

            @Override
            public Card next() {
                int res = bitmap;
                index = 0;
                while ((res & 1) == 0) {
                    res >>>= 1;
                    ++index;
                }
                bitmap = bitmap & (bitmap - 1);
                if (index < cards.length) {
                    return cards[index];
                }
                return null;
            }

            @Override
            public void remove() {
                int mask = 1 << index;
                CardSet.this.bitmap &= ~ mask;
            }
        };
    }

    // reversed order, skipping consecutive cards, e.g. for ♠KA ♣7JQA ♦XJQKA ♥7 -> ♥7 ♦A ♣AQ7 ♠A
    public Iterator<Card> trickTreeIterator() {
        return new Iterator<Card>() {
            int index = last(CardSet.this.bitmap);
            int bitmap = CardSet.this.bitmap << 31 - index;
            int nextIndex = toNext();

            private int toNext() {
                int nextIndex = index;
                if (index < 0) {
                    return nextIndex;
                }

                // skip all 1s
                while ((bitmap & MSB) != 0) {
                    bitmap <<= 1;
                    if ((nextIndex-- % SUIT_LIST_LENGTH) == 0) {
                        break;
                    }
                    if (nextIndex < 0) {
                        break;
                    }
                }

                // skip all 0s
                while ((bitmap & MSB) == 0) {
                    bitmap <<= 1;
                    if (--nextIndex < 0) {
                        break;
                    }
                }
                return nextIndex;
            }

            @Override
            public boolean hasNext() {
                return index >= 0;
            }

            @Override
            public Card next() {
                Card card = cards[index];
                index = nextIndex;
                nextIndex = toNext();
                return card;
            }

            @Override
            public void remove() {
                // just for completeness
                int mask = 1 << index;
                CardSet.this.bitmap &= ~mask;
            }
        };
    }

    public static class ListData {
        public Card.Suit suit;
        public final CardSet thisSuit;
        public int maxTheyStart;    // unwanted tricks
        public int maxMeStart;      // unwanted tricks
        public int cardsLeft = -1;  // when I always start
        public boolean good;        // for all-pass, includes smallest rank
        public int misereEval = 0;

        public int minMeStart;      // wanted tricks
        public int minTheyStart;    // wanted tricks

        public ListData(CardSet thisSuit) {
            this.thisSuit = thisSuit;
        }
    }
}
