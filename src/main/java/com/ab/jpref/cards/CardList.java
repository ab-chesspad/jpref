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
 * Created: 12/22/2024
 *
 * convenience List wrapper
 */

package com.ab.jpref.cards;

import java.util.*;

public class CardList extends ArrayList<Card> {

    public static CardList getDeck() {
        CardList cardList = new CardList();
        for (Card.Suit suit : Card.Suit.values()) {
            for (Card.Rank rank : Card.Rank.values()) {
                if (rank.equals(Card.Rank.SIX)) {
                    continue;
                }
                cardList.add(new Card(suit, rank));
            }
        }
        return cardList;
    }

    public void verifyDeck() {
        StringBuilder msg = new StringBuilder();
        final CardList deck = getDeck();
        Set<Card> set = new HashSet<>(this);

        Map<Card, Integer> counts = new HashMap<>();
        for (Card card : this) {
            set.add(card);
            Integer res = counts.get(card);
            int count = 0;
            if (res != null) {
                count = res;
            }
            counts.put(card, ++count);
        }

        msg.append("occurrences: ");
        String sep = "";
        boolean found = false;
        for (Map.Entry<Card, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == 1) {
                continue;
            }
            found = true;
            msg.append(sep).append(String.format("%s: %d", entry.getKey(), entry.getValue()));
            sep = ", ";
        }

        if (found) {
            throw new RuntimeException(msg.toString());
        }

        if (set.size() < deck.size()) {
            msg.delete(0, msg.length());
            msg.append("missing: ");
            sep = "";
            for (Card card : deck) {
                if (!set.contains(card)) {
                    msg.append(sep).append(card);
                    sep = ", ";
                }
            }
            throw new RuntimeException(msg.toString());
        }
    }

    public CardList() {
        super();
    }

    public CardList(Collection<Card> cards) {
        super(cards);
    }

    public Card first() {
        if (isEmpty()) {
            return null;
        }
        return get(0);
    }

    public Card last() {
        if (isEmpty()) {
            return null;
        }
        return get(size() - 1);
    }

    @Override
    public String toString() {
        return toColorString(false);
    }

    public String toColorString() {
        return toColorString(true);
    }

    public String toColorString(boolean color) {
        StringBuilder sb = new StringBuilder();
        Card.Suit suit = null;
        String sep = "";
        for (Card c : this) {
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
        return sb.toString();
    }

    public Card removeFirst() {
        if (isEmpty()) {
            return null;
        }
        return remove(0);
    }


    public Card removeLast() {
        if (isEmpty()) {
            return null;
        }
        return remove(size() - 1);
    }
}