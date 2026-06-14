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
 * Created: 2/13/2026
 *
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import static com.ab.jpref.config.Config.NOP;
import static com.ab.jpref.config.Config.ROUND_SIZE;

public class BaseTrick {
    public static final int MAX_TRICK_CARDS = 3;
    public static final int NULL_DATA = 0;
    private static final int CARD_MASK = Card.TOTAL_SUITS * Card.TOTAL_RANKS - 1;
    private static final int CARD_MASK_LEN = 5;         // bits needed to store 0-31 card values

    private static final int TOTAL_CARDS_MASK = 0x03;   // up to 3 cards in the trick
    private static final int TOTAL_CARDS_MASK_LEN = 2;
    private static final int TOTAL_CARDS_SHIFT = CARD_MASK_LEN * MAX_TRICK_CARDS;           // 15

    private static final int TRICKS_MASK = 0x0f;        // 0-10 tricks
    private static final int TRICKS_MASK_LEN = 4;
    private static final int FUTURE_TRICKS_SHIFT = TOTAL_CARDS_SHIFT + TOTAL_CARDS_MASK_LEN; // 17
    private static final int PAST_TRICKS_SHIFT = FUTURE_TRICKS_SHIFT + TRICKS_MASK_LEN;      // 21

    private static final int STARTED_BY_MASK = 0x03;    // 0 - 2
    private static final int STARTED_BY_LEN = 2;
    private static final int STARTED_BY_SHIFT = PAST_TRICKS_SHIFT + TRICKS_MASK_LEN;        // 25

    private static final int TOP_MASK = 0x03;           // 0 - 2
    private static final int TOP_LEN = 2;
    private static final int TOP_SHIFT = STARTED_BY_SHIFT + STARTED_BY_LEN;                 // 27

    private static final int INDEX_MASK_LEN = 24;                                           // 24
    private static final int INDEX_MASK = (1 << INDEX_MASK_LEN) - 1;
    private static final int INDEX_SHIFT = TOP_SHIFT + TOP_LEN;                             // 30

    public static final int TOTAL_USED_BITS = INDEX_SHIFT + INDEX_MASK_LEN;                 // 53

    private static final long FORECAST_DONE_BIT = (1L << 63);                                     // 63

    long trickData = 0;

    // debug-only fields: call refresh() in the debugger when needed
    int _nextIndex;
    int _pastTricks;
    int _futureTricks;
    int _startedBy;
    int _turn;
    int _top;

    public void refresh() {
        _pastTricks = getPastTricks();
        _futureTricks = getFutureTricks();
        _startedBy = getStartedBy();
        _turn = getTurn();
        _top = getTop();
        _nextIndex = getNextIndex();
    }

    public BaseTrick() {}

/* for debugging only
    public BaseTrick(long trickData) {
        this.trickData = trickData;
        refresh();
    }

    public BaseTrick(BaseTrick that) {
        this();
        trickData = that.getTrickData();
        refresh();
    }

    public static BaseTrick getTrick(int index) {
        return new BaseTrick(trickPool[index]);
    }
*/

    public static Card getCard(long trickData, int i) {
        if (i >= size(trickData)) {
            return null;
        }
        int val = (int)(trickData >>> CARD_MASK_LEN * i) & CARD_MASK;
        return Card.fromValue(val);
    }

    public static int size(long trickData) {
        return (int)(trickData >>> TOTAL_CARDS_SHIFT) & TOTAL_CARDS_MASK;
    }

    public static int getPastTricks(long trickData) {
        return (int)((trickData >>> PAST_TRICKS_SHIFT) & TRICKS_MASK);
    }

    static long setFutureTricks(long trickData, int futureTricks) {
        if (futureTricks < 0 || futureTricks > ROUND_SIZE) {
            throw new RuntimeException(String.format("invalid futureTricks %d", futureTricks));
        }
        int mask = TRICKS_MASK << FUTURE_TRICKS_SHIFT;
        trickData &= ~mask;
        trickData |= futureTricks << FUTURE_TRICKS_SHIFT;
        return trickData;
    }

    public static int getFutureTricks(long trickData) {
        return (int)((trickData >>> FUTURE_TRICKS_SHIFT) & TRICKS_MASK);
    }

    public static int getStartedBy(long trickData) {
        int res = (int)(trickData >>> STARTED_BY_SHIFT) & STARTED_BY_MASK;
        if (res > 9) {
            res = res - 16;
        }
        return res;
    }

    public static int getTop(long trickData) {
        int res = (int)(trickData >>> TOP_SHIFT) & TOP_MASK;
        if (res > 2) {
            res = -1;
        }
        return res;
    }

    public static long incrementSize(long trickData) {
        return updateSize(trickData, 1);
    }

    private static long updateSize(long trickData, int diff) {
        int size = size(trickData) + diff;
        if (size > MAX_TRICK_CARDS) {
            throw new RuntimeException(String.format("exceeding trick capacity: %d", size));
        }
        int mask = TOTAL_CARDS_MASK << TOTAL_CARDS_SHIFT;
        trickData &= ~mask;
        trickData |= (long)size << TOTAL_CARDS_SHIFT;
        return trickData;
    }

    public static long add(long trickData, Card card) {
        int val = card.toInt();
        int totalCards = size(trickData);
        int mask = CARD_MASK << CARD_MASK_LEN * totalCards;
        trickData &= ~mask;
        trickData |= (long)val << CARD_MASK_LEN * totalCards;
        trickData = incrementSize(trickData);
        return trickData;
    }

    public int getNextIndex() {
        return (int)(trickData >>> INDEX_SHIFT) & INDEX_MASK;
    }

    public static long setNextIndex(long trickData, int nextIndex) {
        nextIndex &= INDEX_MASK;
        long mask = (long)INDEX_MASK << INDEX_SHIFT;
        trickData &= ~mask;
        trickData |= (long)nextIndex << INDEX_SHIFT;
        return trickData;
    }

    public static int getNextIndex(long trickData) {
        return (int)(trickData >>> INDEX_SHIFT) & INDEX_MASK;
    }

    public static String toString(long trickData) {
        StringBuilder sb = new StringBuilder();
        Card.Suit suit = null;
        String sep = "";
        for (int j = 0; j < size(trickData); ++j) {
            Card c = getCard(trickData, j);
            Card.Suit s = c.getSuit();
            if (!s.equals(suit)) {
                suit = s;
                sb.append(sep).append(suit);
                sep = " ";
            }
            sb.append(c.getRank());
        }
        return sb.toString();
    }


    public long getTrickData() {
        return trickData;
    }

    public void setTrickData(long trickData) {
        this.trickData = trickData;
    }

    public void clear() {
        trickData = 0;
    }

    public Card getCard(int i) {
        if (i >= size()) {
            return null;
        }
        int val = (int)(trickData >>> CARD_MASK_LEN * i) & CARD_MASK;
        return Card.fromValue(val);
    }

    public int size() {
        return (int)(trickData >>> TOTAL_CARDS_SHIFT) & TOTAL_CARDS_MASK;
    }

    public boolean isEmpty() {
        return ((trickData >>> TOTAL_CARDS_SHIFT) & TOTAL_CARDS_MASK) == 0;
    }

    private void incrementSize() {
        updateSize(1);
    }

    public void decrementSize() {
        updateSize(-1);
    }

    private void updateSize(int diff) {
        int size = size() + diff;
        if (size > MAX_TRICK_CARDS) {
            throw new RuntimeException(String.format("exceeding trick capacity: %d", size));
        }
        int mask = TOTAL_CARDS_MASK << TOTAL_CARDS_SHIFT;
        trickData &= ~mask;
        trickData |= (long)size << TOTAL_CARDS_SHIFT;
    }

    public void setPastTricks(int pastTricks) {
        if (pastTricks < 0 || pastTricks > ROUND_SIZE) {
            throw new RuntimeException(String.format("invalid pastTricks %d", pastTricks));
        }
        int mask = TRICKS_MASK << PAST_TRICKS_SHIFT;
        trickData &= ~mask;
        trickData |= pastTricks << PAST_TRICKS_SHIFT;
    }

    public int getPastTricks() {
        return (int)((trickData >>> PAST_TRICKS_SHIFT) & TRICKS_MASK);
    }

    public void updatePastTricks(int diff) {
        setPastTricks(getPastTricks() + diff);
    }

    void setFutureTricks(int futureTricks) {
        if (futureTricks < 0 || futureTricks > ROUND_SIZE) {
            throw new RuntimeException(String.format("invalid futureTricks %d", futureTricks));
        }
        int mask = TRICKS_MASK << FUTURE_TRICKS_SHIFT;
        trickData &= ~mask;
        trickData |= futureTricks << FUTURE_TRICKS_SHIFT;
    }

    int getFutureTricks() {
        return (int)((trickData >>> FUTURE_TRICKS_SHIFT) & TRICKS_MASK);
    }

    public void updateFutureTricks(int diff) {
        setFutureTricks(getFutureTricks() + diff);
    }

    public int getStartedBy() {
        int res = (int)(trickData >>> STARTED_BY_SHIFT) & STARTED_BY_MASK;
        if (res > 9) {
            res = res - 16;
        }
        return res;
    }

    public void setStartedBy(int number) {
        number &= STARTED_BY_MASK;
        int mask = STARTED_BY_MASK << STARTED_BY_SHIFT;
        trickData &= ~mask;
        trickData |= number << STARTED_BY_SHIFT;
    }

    public int getTop() {
        int res = (int)(trickData >>> TOP_SHIFT) & TOP_MASK;
        if (res > 2) {
            res = -1;
        }
        return res;
    }

    public void setTop(int top) {
        top &= TOP_MASK;
        int mask = TOP_MASK << TOP_SHIFT;
        trickData &= ~mask;
        trickData |= top << TOP_SHIFT;
    }

    public int getTurn() {
        return (getStartedBy() + size()) % NOP;
    }

    public void add(Card card) {
        int val = card.toInt();
        int totalCards = size();
        int mask = CARD_MASK << CARD_MASK_LEN * totalCards;
        trickData &= ~mask;
        trickData |= (long)val << CARD_MASK_LEN * totalCards;
        incrementSize();
    }

    public Card removeLast() {
        int totalCards = size();
        if (totalCards == 0) {
            throw new RuntimeException("removing card from empty trick");
        }
        Card card = getCard(totalCards - 1);
        decrementSize();
        return card;
    }

    @Override
    public String toString() {
        return toString(this.trickData);
    }

    public String toColorString() {
        Card.Suit suit = null;
        String sep = "";
        StringBuilder sb = new StringBuilder();

        for (int j = 0; j < size(); ++j) {
            Card c = getCard(j);
            Card.Suit s = c.getSuit();
            if (!s.equals(suit)) {
                suit = s;
                if (s.equals(Card.Suit.DIAMOND) || s.equals(Card.Suit.HEART)) {
                    sb.append(Card.ANSI_RED);
                } else {
                    sb.append(Card.ANSI_RESET);
                }
                sb.append(sep).append(suit);
                sep = " ";
            }
            sb.append(c.getRank());
        }
        sb.append(Card.ANSI_RESET);
        return new String(sb);
    }
}