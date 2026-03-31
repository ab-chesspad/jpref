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
 * Created: 2/13/2026
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.config.Config;

public class BaseTrick {
    public static final int NOP = Config.NOP;   // Number of players
    public static final int ROUND_SIZE = Config.ROUND_SIZE;
    public static final int MAX_TRICK_CARDS = 3;
    public static final int CARD_MASK = Card.TOTAL_SUITS * Card.TOTAL_RANKS - 1;
    private static final int _CARD_MASK_LEN = 5;  // bits needed to store 0-31 card values
    /*
        private static int _CARD_MASK_LEN = 0;
        static {
            int mask = CARD_MASK;
            while (mask != 0) {
                ++_CARD_MASK_LEN;
                mask >>>= 1;
            }
        }
    */
    public static final int CARD_MASK_LEN = _CARD_MASK_LEN;     // 5
    public static final int FULL_CARD_MASK = (1 << _CARD_MASK_LEN * MAX_TRICK_CARDS) - 1;

    public static final int TOTAL_CARDS_MASK = 0x03;    // up to 3 cards in the trick
    public static final int TOTAL_CARDS_LEN = 2;
    public static final int TOTAL_CARDS_SHIFT = CARD_MASK_LEN * MAX_TRICK_CARDS;            // 15

    public static final int PROBE_TRICKS_MASK = 0x7f;   // for 120 past & future tricks
    public static final int PROBE_TRICKS_LEN = 7;
    public static final int PROBE_TRICKS_SHIFT = TOTAL_CARDS_SHIFT + TOTAL_CARDS_LEN;  // 17

    public static final int STARTED_BY_MASK = 0x03;    // 0 - 2
    public static final int STARTED_BY_LEN = 2;
    public static final int STARTED_BY_SHIFT = PROBE_TRICKS_SHIFT + PROBE_TRICKS_LEN;  // 24

    public static final int TOP_MASK = 0x03;    // 0 - 2
    public static final int TOP_LEN = 2;
    public static final int TOP_SHIFT = STARTED_BY_SHIFT + STARTED_BY_LEN;              // 26

    public static final int FORECAST_DONE_BIT = (1 << 31);                              // 31

    public static int count = 0;        // todo: fix
    public static int deleted = 0;

    BaseTrick next;
    int trickData = 0;

/*
    int _pastTricks;
    int _futureTricks;
    int _startedBy;
    int _turn;
    int _top;
    boolean _done;
//*/

    public void refresh() {
/*
        _done = isDone();
        _pastTricks = getPastTricks();
        _futureTricks = getFutureTricks();
        _startedBy = getStartedBy();
        _turn = getTurn();
        _top = getTop();
//*/
    }

    public BaseTrick() {
        ++count;
    }

    public BaseTrick(Trick that) {
        this();
        trickData = that.getTrickData();
        refresh();
    }

    @Override
    protected void finalize() {
        ++deleted;
    }

    public int getTrickData() {
        return trickData;
    }

    public void setTrickData(int trickData) {
        this.trickData = trickData;
        refresh();
    }

    public void clear() {
        trickData = 0;
        refresh();
    }

    public Card get(int i) {
        if (i >= size()) {
            return null;
        }
        int val = (trickData >>> CARD_MASK_LEN * i) & CARD_MASK;
        return Card.fromValue(val);
    }

    public int size() {
        return (trickData >>> TOTAL_CARDS_SHIFT) & TOTAL_CARDS_MASK;
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
        trickData |= size << TOTAL_CARDS_SHIFT;
    }

    synchronized boolean isDone() {
        return (trickData & FORECAST_DONE_BIT) != 0;
    }

    synchronized void setDone() {
        trickData |= FORECAST_DONE_BIT;
        refresh();
        notifyAll();
    }

    // for testing
    synchronized void clearDone() {
        trickData &= ~FORECAST_DONE_BIT;
        refresh();
        notifyAll();
    }

    private int getProbeData() {
        return (trickData >>> PROBE_TRICKS_SHIFT) & PROBE_TRICKS_MASK;
    }

    private void setProbeData(int pastTricks, int futureTricks) {
        int val = futureTricks * (ROUND_SIZE + 1) + pastTricks;
        int mask = PROBE_TRICKS_MASK << PROBE_TRICKS_SHIFT;
        trickData &= ~mask;
        trickData |= val << PROBE_TRICKS_SHIFT;
        refresh();
    }

    public void setPastTricks(int pastTricks) {
        if (pastTricks < 0 || pastTricks > ROUND_SIZE) {
            throw new RuntimeException(String.format("invalid pastTricks %d", pastTricks));
        }
        int futureTricks = getFutureTricks();
        setProbeData(pastTricks, futureTricks);
    }

    public int getPastTricks() {
        int val = getProbeData();
        return val % (ROUND_SIZE + 1);
    }

    public void updatePastTricks(int diff) {
        setPastTricks(getPastTricks() + diff);
    }

    void setFutureTricks(int futureTricks) {
        if (futureTricks < 0 || futureTricks > ROUND_SIZE) {
            throw new RuntimeException(String.format("invalid futureTricks %d", futureTricks));
        }
        int pastTricks = getPastTricks();
        setProbeData(pastTricks, futureTricks);
    }

    int getFutureTricks() {
        int val = getProbeData();
        return val / (ROUND_SIZE + 1);
    }

    public void updateFutureTricks(int diff) {
        setFutureTricks(getFutureTricks() + diff);
    }

    public int getStartedBy() {
        int res = (trickData >>> STARTED_BY_SHIFT) & STARTED_BY_MASK;
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
        refresh();
    }

    public int getTop() {
        int res = (trickData >>> TOP_SHIFT) & TOP_MASK;
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
        refresh();
    }

    public int getTurn() {
        return (getStartedBy() + size()) % NOP;
    }

    public void add(Card card) {
        int val = card.toInt();
        int totalCards = size();
        int mask = CARD_MASK << CARD_MASK_LEN * totalCards;
        trickData &= ~mask;
        trickData |= val << CARD_MASK_LEN * totalCards;
        incrementSize();
        refresh();
    }

    public Card removeLast() {
        int totalCards = size();
        if (totalCards == 0) {
            throw new RuntimeException("removing card from empty trick");
        }
        Card card = get(totalCards - 1);
        decrementSize();
        refresh();
        return card;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Card.Suit suit = null;
        String sep = "";
        for (int j = 0; j < size(); ++j) {
            Card c = get(j);
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

    public String toColorString() {
        Card.Suit suit = null;
        String sep = "";
        StringBuilder sb = new StringBuilder();

        for (int j = 0; j < size(); ++j) {
            Card c = get(j);
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