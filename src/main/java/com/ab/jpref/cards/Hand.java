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
 * Created: 7/9/25
 */

package com.ab.jpref.cards;

import java.util.*;
import java.util.function.Consumer;

public class Hand implements Iterable<CardList> {
    private final CardList[] allCards = new CardList[Card.TOTAL_SUITS];

    private void sort() {
        for (int i = 0; i < allCards.length; ++i) {
            Collections.sort(allCards[i]);
        }
    }

    public Hand() {
        for (int i = 0; i < allCards.length; ++i) {
            allCards[i] = new CardList();
        }
    }

    public boolean equals(Hand that) {
        if (this.size() != that.size()) {
            return false;
        }
        for (int i = 0; i < allCards.length; ++i) {
            if (!allCards[i].equals(that.allCards[i])) {
                return false;
            }
        }
        return true;
    }

    public Hand clone() {
        Hand newOne = new Hand();
        for (int i = 0; i < allCards.length; ++i) {
            newOne.allCards[i] = (CardList)allCards[i].clone();
        }

        return newOne;
    }

    public int size() {
        int total = 0;
        for (CardList cardList : allCards) {
            total += cardList.size();
        }
        return total;
    }

    public int size(Card.Suit suit) {
        int suitNum = suit.getValue();
        return allCards[suitNum].size();
    }

    public boolean isEmpty(Card.Suit suit) {
        return size(suit) == 0;
    }

    public void add(Card card) {
        int suitNum = card.getSuit().getValue();
        allCards[suitNum].add(card);
    }

    public void add(Collection<Card> cards) {
        for (Card card : cards) {
            int suitNum = card.getSuit().getValue();
            allCards[suitNum].add(card);
        }
        sort();
    }

    public boolean remove(Card card) {
        int suitNum = card.getSuit().getValue();
        if (allCards[suitNum].remove(card)) {
            return true;     // my own card
        }
        return false;
    }

    public void remove(Hand hand) {
        for (CardList cardList : allCards) {
            cardList.removeAll(hand.list());
        }
    }

    public void remove(Collection<Card> cards) {
        for (CardList cardList : allCards) {
            cardList.removeAll(cards);
        }
    }

    public void clear() {
        for (CardList cardList : allCards) {
            cardList.clear();
        }
    }

    public CardList list(Card.Suit suit) {
        return allCards[suit.getValue()];
    }

    public CardList list() {
        CardList _allCards = new CardList();
        for (CardList cardList : allCards) {
            _allCards.addAll(cardList);
        }
        return _allCards;
    }

    public String toColorString(boolean color) {
        StringBuilder sb = new StringBuilder();
        for (CardList cardList : this.allCards) {
            String s;
            if (color) {
                s = cardList.toColorString();
            } else {
                s = cardList.toString();
            }
            if (!s.isEmpty()) {
                sb.append(s).append(" ");
            }
        }
        return sb.toString();
    }

    public void add(CardList talon) {
        for (Card card : talon) {
            add(card);
        }
        talon.clear();
        sort();
    }

    @Override
    public Iterator<CardList> iterator() {
        // todo: skip empty lists
        return new Iterator<CardList>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < allCards.length;
            }

            @Override
            public CardList next() {
                return allCards[index++];
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("not supported");
            }
        };
    }
}
