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
 *
 * 32-card deck as bitset
 * simplified java.util.BitSet
 * most operations, except size(), get(i) and iterators, are O(1)
 */

package com.ab.jpref.cards;

import com.ab.jpref.cards.Card.Rank;
import com.ab.jpref.cards.Card.Suit;
import com.ab.jpref.engine.Bot;
import com.ab.util.Pair;

import java.util.*;

public class CardSet {
    // todo: first, last, random
    public static final boolean RANDOM_ANY_CARD = false;
    /* this is nice, but I don't want to see it in debugger
        private static int _count = 0;
        static {
            int n = Integer.MAX_VALUE;
            while (n != 0) {
                n &= n - 1;    // clear the least significant bit
                ++_count;
            }
        }
    /*/
    private static final int _count = 31;
    //*/
    public static final int TOTAL_BITS = _count + 1;
    public static final int MSB = 1 << _count;
    public static final int SUIT_LIST_LENGTH = TOTAL_BITS / 4;
    public static final int SUIT_LIST_MASK = (1 << (SUIT_LIST_LENGTH)) - 1;

    public static final int[] suitMasks = {0xff, 0xff00, 0xff0000, 0xff000000};

    // number of necessary smaller cards     7  8  9  X  J  Q  K  A
    public static final int[] misereCards = {0, 1, 1, 2, 2, 3, 3, 4};
    public static final Map<Integer, Integer> clean4Misere = new HashMap<>();

    static {
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                if (rank.equals(Rank.SIX)) {
                    continue;
                }
                int j = rank.getValue() - Rank.SEVEN.getValue();
                int bit = 1 << (suitOffset(suit) + j);
                clean4Misere.put(bit, misereCards[j]);
            }
        }
    }

    public static final Random random = new Random();

    protected int bitmap;

    public CardSet() {
    }

    public CardSet(Card card) {   // CardSet contains a single card
        add(card);
    }

    public CardSet(CardSet that) {
        this.bitmap = that.bitmap;
    }

    public CardSet(CardSet... cardSets) {
        for (CardSet cardSet : cardSets) {
            add(cardSet);
        }
    }

    public CardSet(Collection<Card> cards) {
        add(cards);
    }

    public CardSet(int bitmap) {
        this.bitmap = bitmap;
    }

    public static CardSet getDeck() {
        return new CardSet(-1);
    }

    public static CardSet getList(Suit suit) {
        int bits = suitMask(suit);
        return new CardSet(bits);
    }

    public CardSet union(CardSet cardSet) {
        CardSet newOne = new CardSet();
        newOne.bitmap = bitmap | cardSet.bitmap;
        return newOne;
    }

    public CardSet diff(CardSet cardSet) {
        CardSet newOne = new CardSet();
        newOne.bitmap = bitmap & ~cardSet.bitmap;
        return newOne;
    }

    public int getBitmap() {
        return bitmap;
    }

    public static CardSet union(CardSet... cardSets) {
        int bitmap = 0;
        for (CardSet cardSet : cardSets) {
            bitmap |= cardSet.bitmap;
        }
        return new CardSet(bitmap);
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

    protected static int suitOffset(Suit suit) {
        int suitNum = suit.getValue();
        return suitNum * SUIT_LIST_LENGTH;
    }

    public static int bit(Card card) {
        return 1 << offset(card);
    }

    public static int offset(Card card) {
        int suitNum = card.getSuit().getValue();
        int rankNum = card.getRank().getValue() - Rank.SEVEN.getValue();
        return suitNum * SUIT_LIST_LENGTH + rankNum;
    }

    public static int suitMask(Suit suit) {
        if (suit == null) {
            return -1;      // all suits
        }
        return SUIT_LIST_MASK << (suit.getValue() * SUIT_LIST_LENGTH);
    }

    // https://www.geeksforgeeks.org/dsa/find-significant-set-bit-number/
    public static int lastBit(int bitmap) {
        if (bitmap == 0) {
            return -1;
        }
        int n = bitmap;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        if (n == -1) {
            return 0x80000000;
        }
        return ++n >>> 1;
    }

    // cards are equivalent if they are from a consecutive list, e.g. 89XJ
    public boolean equiv(Card card0, Card card1) {
        if (!card0.getSuit().equals(card1.getSuit())) {
            return false;
        }
        Card c0 = card0;
        Card c1 = card1;
        if (card0.compareTo(card1) > 0) {
            c0 = card1;
            c1 = card0;
        }
        int bit0 = 1 << offset(c0);
        int bit1 = 1 << offset(c1);
        int bitmap = this.bitmap | ((bit0 << 1) - 1);   // set all 1s on the right of c0
        bitmap |= -bit1;                   // set all 1s on the left of c1
        return ~bitmap == 0;
    }

    public boolean equals(Object that) {
        if (!(that instanceof CardSet)) {
            return false;
        }
        return this.equals((CardSet) that);
    }

    public boolean equals(CardSet that) {
        return this.bitmap == that.bitmap;
    }

    // compare ranks
    public int compareTo(CardSet that) {
        Card thisLast = this.last();
        Card thatLast = that.last();

        if (thatLast == null) {
            return thisLast == null ? 0 : 1;
        }
        if (thisLast == null) {
            return -1;
        }

        int s0 = this.bitmap >>> suitOffset(thisLast.getSuit());
        int s1 = that.bitmap >>> suitOffset(thatLast.getSuit());
        return s0 - s1;
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

    public boolean contains(CardSet cardSet) {
        if (cardSet == null) {
            return true;    // let's treat null as empty set
        }
        int intersection = bitmap & cardSet.bitmap;
        return intersection == cardSet.bitmap;
    }

    public CardSet intersection(CardSet cardSet) {
        CardSet newOne = new CardSet();
        newOne.bitmap = bitmap & cardSet.bitmap;
        return newOne;
    }

    public boolean contains(Card card) {
        int mask = 1 << offset(card);
        return (bitmap & mask) != 0;
    }

    public int size() {
        return size(bitmap);
    }

    public int size(Suit suit) {
        int bits = bitmap & suitMask(suit);
        return size(bits);
    }

    public void add(Card card) {
        if (card == null) {
            return;
        }
        int bit = 1 << offset(card);
        bitmap |= bit;
    }

    public CardSet list() {
        return list(null);
    }

    public CardSet list(Suit suit) {
        CardSet cardSet = new CardSet(this);
        if (suit != null) {
            cardSet.bitmap = cardSet.bitmap & suitMask(suit);
        }
        return cardSet;
    }

    public void set(CardSet cardSet) {
        this.bitmap = cardSet.bitmap;
    }

    public Card anyCard() {
        return anyCard(null);
    }

    public Card anyCard(Suit suit) {
        int bitmap = this.bitmap;
        if (suit != null) {
            bitmap = bitmap & suitMask(suit);
        }

        if (bitmap == 0) {
            bitmap = this.bitmap;   // will return any card
        }

        int index;
        if (RANDOM_ANY_CARD) {
            int size = size(bitmap);
            index = random.nextInt(size);
            return Card.fromValue(index);
        }
        return Card.get(lastBit(bitmap));
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
        if (card == null) {
            return false;
        }
        int bit = 1 << offset(card);
        boolean res = (bitmap & bit) != 0;
        bitmap &= ~bit;
        return res;
    }

    public CardSet remove(CardSet other) {
        this.bitmap &= ~other.bitmap;
        return this;
    }

    public CardSet remove(Collection<Card> cards) {
        for (Card card : cards) {
            remove(card);
        }
        return this;
    }

    public Card remove(int order) {
        return get(order, true);
    }

    private Card get(int index, boolean remove) {
        if (index < 0 || index >= TOTAL_BITS) {
            return null;
        }
        int mask = 1;
        int _index = 0;
        for (int i = 0; i < TOTAL_BITS; ++i) {
            if ((bitmap & mask) != 0) {
                if (--index < 0) {
                    break;
                }
            }
            mask <<= 1;
            ++_index;
        }
        if (_index >= TOTAL_BITS) {
            return null;
        }
        if (remove) {
            bitmap &= ~mask;
        }
        return Card.fromValue(_index);
    }

    public Card first() {
        return first(bitmap);
    }

    public static Card first(int bitmap) {
        if (bitmap == 0) {
            return null;
        }
        int bit = bitmap ^ (bitmap & (bitmap - 1));
        return Card.get(bit);
    }

    public Card removeFirst() {
        if (bitmap == 0) {
            return null;
        }
        int bit = bitmap ^ (bitmap & (bitmap - 1));
        Card card = Card.get(bit);
        bitmap &= ~bit;
        return card;
    }

    public static Card last(int bitmap) {
        return Card.get(lastBit(bitmap));
    }

    public Card last() {
        return last(bitmap);
    }

    public Card removeLast() {
        int bit = lastBit(bitmap);
        if (bit == 0) {
            return null;
        }
        Card card = Card.get(bit);
        bitmap &= ~bit;
        return card;
    }

    public Card get(int order) {
        return get(order, false);
    }

    public CardList toCardList() {
        return toCardList(this.bitmap);
    }

    public static CardList toCardList(int bitmap) {
        CardList cardList = new CardList();
        int bit = 0;
        while ((bit = CardSet.next(bitmap, bit)) != 0) {
            cardList.add(Card.get(bit));
        }
        return cardList;
    }

    public Card next(Card card) {
        if (card == null) {
            return first();
        }

        int bit = 1 << offset(card);
        if (bit == MSB) {
            return null;
        }

        // clear all bits lesser than card
        int bitmap = this.bitmap & -(bit << 1);
        return first(bitmap);
    }

    public Card prev(Card card) {
        if (card == null) {
            return null;
        }

        int bit = 1 << offset(card);

        // clear all bits greater than card
        int bitmap = this.bitmap & (bit - 1);
        return Card.get(lastBit(bitmap));
    }

    public boolean isClean4Misere() {
        if (this.bitmap == 0) {
            return true;
        }
        int bitmap = this.bitmap;
        int count = 0;
        int bit0 = bitmap ^ (bitmap & (bitmap - 1));
        int mask = 0;
        for (int m : suitMasks) {
            if ((bit0 & m) != 0) {
                mask = m;
                break;
            }
        }
        while (bitmap != 0) {
            int bit = bitmap ^ (bitmap & (bitmap - 1));
            if ((bit & mask) == 0) {
                // sanity check
                throw new InputMismatchException(String.format("invalid misere test for suit %s", this));
            }
            if (count < clean4Misere.get(bit)) {
                return false;
            }
            ++count;
            bitmap &= ~bit;
        }
        return true;
    }

    public int prevIndex(Card other) {
        if (other == null) {
            return 0;
        }
        int offset = other.getSuit().getValue() * SUIT_LIST_LENGTH;
        int mask = 1 << offset;
        int rank = other.getRank().getValue() - Rank.SEVEN.getValue();
        int index = -1;
        while (--rank >= 0) {
            if ((mask & bitmap) != 0) {
                ++index;
            }
            mask <<= 1;
        }
        return index;
    }

    public static int suitCount(int bitmap) {
        int count = 0;
        for (int m : suitMasks) {
            if ((bitmap & m) != 0) {
                ++count;
            }
        }
        return count;
    }

    public Card getOptimalStart(CardSet leftSuit, CardSet rightSuit) {
        if (this.isEmpty()) {
            return null;
        }
        Card myFirst = this.first();
        if (this.size() == 1) {
            return myFirst;
        }

        if (myFirst.compareInTrick(min(leftSuit, rightSuit)) > 0) {
            return myFirst;
        }

        // size >= 2
        Card myNext = this.next(myFirst);
        if (leftSuit.size() > 1 && rightSuit.size() > 1 ||
            leftSuit.size() == 1 && myNext.compareInTrick(leftSuit.first()) < 0 ||
            rightSuit.size() == 1 && myNext.compareInTrick(rightSuit.first()) < 0) {
            return myNext;     // preserve min
        }
        return myFirst;
    }

    // clone lists and remove duplicates
    CardSet[] simplifyHands(CardSet _leftSuit, CardSet _rightSuit, boolean forMisere) {
        CardSet rightSuit = new CardSet(_rightSuit);
        CardSet leftSuit = new CardSet(_leftSuit);
        if (forMisere) {
            CardSet common = rightSuit.intersection(leftSuit);
            leftSuit.remove(common);
        } else {
            CardSet common = leftSuit.intersection(leftSuit);
            rightSuit.remove(common);
        }
        CardSet[] cardLists = new CardSet[3];
        cardLists[0] = new CardSet(this);
        cardLists[1] = leftSuit;
        cardLists[2] = rightSuit;
        return cardLists;
    }

    public ListData maxUnwantedTricks(CardSet leftSuit, CardSet rightSuit, int elderHand) {
        ListData listData = new ListData(this);
        listData.cardsLeft = 0;
        if (this.isEmpty()) {
            return listData;
        }
        listData.suit = this.first().getSuit();

        CardSet[] cardLists = simplifyHands(leftSuit, rightSuit, true);
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
        while (!cardLists[n].isEmpty()) {
            int k = (n + 1) % cardLists.length;
            int r = (n + 2) % cardLists.length;
            Card topCard = cardLists[n].getOptimalStart(cardLists[k], cardLists[r]);
            cardLists[n].remove(topCard);
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
                            j = cardLists[k].prevIndex(topCard);
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
                            j = cardLists[k].prevIndex(topCard);
                        }
                    } else {
                        // self has not played yet
                        int jLeft = Integer.MAX_VALUE;
                        int jTop = Integer.MAX_VALUE;
                        myMin = cardLists[k].first();
                        if (k == 0) {
                            // self playing
                            if (myMin.compareTo(cardLists[left].first()) < 0) {
                                jLeft = cardLists[k].prevIndex(cardLists[left].first());
                            }
                            if (myMin.compareTo(topCard) < 0) {
                                jTop = cardLists[k].prevIndex(topCard);
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
                                jLeft = cardLists[k].prevIndex(cardLists[left].first());
                            }
                            if (myMin.compareTo(topCard) < 0) {
                                jTop = cardLists[k].prevIndex(topCard);
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
        ListData listData = this.maxUnwantedTricks(dummy, otherSuits, 2);
        if (this.size() <= 1 || myMin.compareInTrick(min(leftSuit, rightSuit)) > 0) {
            // do nothing, myMin is not the lowest
        } else if (this.size() <= 4 && myMax.getRank().compare(Rank.ACE) >= 0 &&
            rightSize > 0 && leftSize > 0) {
            listData.maxTheyStart = 0;
        } else if (this.size() <= 3 && myMax.getRank().compare(Rank.QUEEN) >= 0 && rightSize >= 3 &&
            myMax.compareInTrick(rightSuit.get(mySize - 1)) < 0) {
            listData.maxTheyStart = 0;
        } else if ((myNextMin = this.get(1)).getRank().compare(Rank.TEN) >= 0) {
            // 7X, et al.
            if (leftSize >= 2 && myNextMin.compareInTrick(leftSuit.first()) > 0 ||
//                leftSize < 2 && myNextMin.compareInTrick(rightSuit.get(1)) > 0 ||  // "7X 8 9JQKA 0"
                rightSize >= 2 && myNextMin.compareInTrick(rightSuit.get(1)) > 0) {
                // ловится:
                listData.maxTheyStart = 1;
            } else {
                listData.maxTheyStart = 0;
            }
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

    public static Card min(CardSet... hands) {
        int union = 0;
        for (CardSet h : hands) {
            union |= h.bitmap;
        }
        return first(union);
    }

    public static Card max(CardSet... hands) {
        int union = 0;
        for (CardSet h : hands) {
            union |= h.bitmap;
        }
        return last(union);
    }

    // assuming always self's turn
    public Pair<Integer, Card> minWantedTricks(CardSet _leftSuit, CardSet _rightSuit) {
        CardSet[] hands = simplifyHands(_leftSuit, _rightSuit, false);
        return _minWantedTricks(hands[1], hands[2]);
    }


    // assuming always self's turn
    private Pair<Integer, Card> _minWantedTricks(CardSet _leftSuit, CardSet _rightSuit) {
        Pair<Integer, Card> res = new Pair<>(0, null);
        if (this.isEmpty()) {
            return res;
        }
        int bm = this.bitmap;
        int bit = 0;
        while ((bit = prev(bm, bit)) != 0) {
            Card myCard = Card.get(bit);
            CardSet leftSuit = new CardSet(_leftSuit);
            CardSet rightSuit = new CardSet(_rightSuit);
            int _count = 0;
            CardSet clone = new CardSet(this);
            clone.remove(myCard);
            Card left = leftSuit.next(myCard);
            Card right = rightSuit.next(myCard);
            if (left == null && right == null) {
                leftSuit.removeFirst();
                rightSuit.removeFirst();
                ++_count;
            } else if (left != null) {
                if (right == null || left.compareInTrick(right) < 0) {
                    leftSuit.remove(left);
                    rightSuit.removeFirst();
                } else {
                    leftSuit.removeFirst();
                    rightSuit.remove(right);
                }
            } else {
                rightSuit.remove(right);
                leftSuit.removeFirst();
            }
            Pair<Integer, Card> pair = clone._minWantedTricks(leftSuit, rightSuit);
            _count += pair.first;
            if (res.first < _count) {
                res.first = _count;
                res.second = myCard;
            }
        }
        return res;
    }

    /// //////////////////////////////
    @Override
    public String toString() {
        return toColorString(bitmap, false);
    }

    public String toColorString() {
        return toColorString(bitmap, true);
    }

    public String toColorString(boolean color) {
        return toColorString(bitmap, color);
    }

    static String toString(int bitmap) {
        return toColorString(bitmap, false);
    }

    static String toColorString(int bitmap, boolean color) {
        Suit suit = null;
        String sep = "";
        StringBuilder sb = new StringBuilder();
        while (bitmap != 0) {
            int bit = bitmap ^ (bitmap & (bitmap - 1));
            Card c = Card.get(bit);
            bitmap &= ~bit;
            Suit s = c.getSuit();
            if (!s.equals(suit)) {
                suit = s;
                if (color) {
                    if (s.equals(Suit.DIAMOND) || s.equals(Suit.HEART)) {
                        sb.append(Card.ANSI_RED);
                    } else {
                        sb.append(Card.ANSI_RESET);
                    }
                }
                sb.append(sep).append(suit);
                sep = " ";
            }
            sb.append(c.getRank());
        }
        if (color) {
            sb.append(Card.ANSI_RESET);
        }
        return sb.toString();
    }
////////////////////////////
    // to use in iterations:

    // compare bitmaps
    private static int compare(int b0, int b1) {
        int r0 = (b0 & 1) - (b1 & 1);
        int r1 = (b0 >>> 1) - (b1 >>> 1);
        if (r1 == 0) {
            return r0;
        }
        return r1;
    }

    public static int next(int bitmap, int bit) {
        if (bitmap == 0) {
            return 0;
        }
        if (bit != 0) {
            // clear bit and all lower bits
            bitmap &= ~(bit | (bit - 1));
        }
        bit = bitmap ^ (bitmap & (bitmap - 1));
        return bit;
    }

    public static int prev(int bitmap, int bit) {
        if (bitmap == 0) {
            return 0;
        }
        if (bit != 0) {
            // clear bit and all higher bits
            bitmap &= bit - 1;
        }
        if (bitmap == 0) {
            return 0;
        }
        bit = lastBit(bitmap);
        return bit;
    }

    // convenience method
    public static int next(long bitmap, int bit) {
        boolean bw = (bitmap & Bot.BACKWARD_FLAG) != 0;
        if (bw) {
            return CardSet.prev((int)bitmap, bit);
        } else {
            return CardSet.next((int)bitmap, bit);
        }
    }

    public static int bm4NextSuit(int bitmap, int suitSet) {
        if (bitmap == 0) {
            return 0;
        }
        int mask = suitMasks[0];
        if (suitSet != 0) {
            while ((mask & suitSet) == 0) {
                bitmap &= ~mask;
                mask <<= SUIT_LIST_LENGTH;
            }
            bitmap &= ~mask;
            mask <<= SUIT_LIST_LENGTH;
        }
        if (bitmap == 0) {
            return 0;
        }
        while ((bitmap & mask) == 0) {
            mask <<= SUIT_LIST_LENGTH;
        }
        bitmap &= mask;
        return bitmap;
    }

    public static int bm4PrevSuit(int bitmap, int suitSet) {
        if (bitmap == 0) {
            return 0;
        }
        int mask = suitMasks[suitMasks.length - 1];
        if (suitSet != 0) {
            while ((mask & suitSet) == 0) {
                bitmap &= ~mask;
                mask >>>= SUIT_LIST_LENGTH;
            }
            bitmap &= ~mask;
            mask >>>= SUIT_LIST_LENGTH;
        }
        if (bitmap == 0) {
            return 0;
        }
        while ((bitmap & mask) == 0) {
            mask >>>= SUIT_LIST_LENGTH;
        }
        return bitmap & mask;
    }

    // skipping consecutive cards, e.g. for ♠KA ♣7JQA ♦XJQKA ♥7 -> ♠K ♣7JA ♦X ♥7
    public static int bm4buildForward(int thisBitmap, int othersBitmap) {
        int others = ~othersBitmap;
        int bitmap = thisBitmap;
        int bit = bitmap ^ (bitmap & (bitmap - 1));
        others &= -bit;
        others &= ((lastBit(bitmap) << 1) - 1);
        bitmap |= others;
        int suitListMask = SUIT_LIST_MASK;
        int mask = 0;
        while (bit != 0) {
            mask |= bit;
            while ((bit & suitListMask) == 0) {
                suitListMask <<= SUIT_LIST_LENGTH;
            }
            int m0 = (bitmap + bit) & suitListMask;
            m0 &= (m0 - 1);
            bitmap = bitmap & ~suitListMask | m0;
            bit = bitmap ^ (bitmap & (bitmap - 1));
            while (bit != 0 && (bit & thisBitmap) == 0) {
                bitmap &= ~bit;
                bit <<= 1;
            }
        }
        return mask;
    }

    public static int bm4buildBackward(int thisBitmap, int othersBitmap) {
        int others = ~othersBitmap;
        int bitmap = thisBitmap;
        int bit = bitmap ^ (bitmap & (bitmap - 1));
        others &= -bit;
        others &= ((lastBit(bitmap) << 1) - 1);
        bitmap |= others;
        int suitListMask = SUIT_LIST_MASK;
        int mask = 0;
        while (bit != 0) {
            while ((bit & suitListMask) == 0) {
                suitListMask <<= SUIT_LIST_LENGTH;
            }
            int m0 = (bitmap + bit) & suitListMask;
            int groupMSB;
            if (m0 == 0) {
                groupMSB = lastBit(suitListMask);
            } else {
                groupMSB = (m0 ^ (m0 & (m0 - 1))) >>> 1;
            }
            while (groupMSB != 0 && (groupMSB & thisBitmap) == 0) {
                groupMSB >>>= 1;
            }
            mask |= groupMSB;
            m0 &= (m0 - 1);
            bitmap = bitmap & ~suitListMask | m0;
            bit = bitmap ^ (bitmap & (bitmap - 1));
        }
        return mask;
    }

    public static int bm4build(int thisBitmap, int friendBitmap, int foeBitmap) {
        int union = thisBitmap | friendBitmap;
        int res = 0;
        int suitListMask = SUIT_LIST_MASK;
        int lsb = union ^ (union & (union - 1));
        union |= ~foeBitmap;
        union &= -lsb;
        while (lsb != 0) {
            while ((lsb & suitListMask) == 0) {
                suitListMask <<= SUIT_LIST_LENGTH;
            }
            int u = union & suitListMask;
            int msb = lastBit(u);
            int m0 = (u + lsb) & suitListMask;
            int groupMSB;
            if (m0 == 0) {
                groupMSB = msb;
            } else {
                groupMSB = (m0 ^ (m0 & (m0 - 1))) >>> 1;
            }
            if ((thisBitmap & lsb) != 0) {
                int friendSuitBitmap = friendBitmap & suitListMask;
                int b = friendSuitBitmap & -(lsb << 1);
                int friendLSB = b ^ (b & (b - 1));
                int friendMSB = lastBit(b);
                if ((friendLSB & suitListMask) == 0) {
                    res |= lsb;
                } else {
                    b = thisBitmap & -(friendLSB << 1);
                    int thisLSB = b ^ (b & (b - 1));
                    if (compare(friendMSB, thisLSB) < 0) {
                        res |= lsb;
                        res |= thisLSB;
                    }
                    if (thisLSB == 0 || thisLSB == msb || thisLSB == groupMSB) {
                        res |= lsb;
                    } else {
                        res |= thisLSB;
                    }
                }
            } else {
                int b = thisBitmap & -(lsb << 1);
                b &= (groupMSB << 1) - 1;
                int next = b ^ (b & (b - 1));
                if (next != 0) {
                    res |= next;
                }
            }
            m0 &= (m0 - 1);
            union = union & ~suitListMask | m0;
            if ((thisBitmap & union) == 0) {
                break;
            }
            lsb = union ^ (union & (union - 1));
        }
        return res;
    }

    public static class ListData {
        public Suit suit;
        public final CardSet thisSuit;
        public int maxTheyStart;    // unwanted tricks
        public int maxMeStart;      // unwanted tricks
        public int cardsLeft = -1;  // when I always start
        public boolean good;        // for all-pass, includes smallest rank
        public int misereEval = 0;

        public ListData(CardSet thisSuit) {
            this.thisSuit = thisSuit;
        }
    }
}