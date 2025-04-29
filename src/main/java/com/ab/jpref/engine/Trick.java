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
 * Created: 3/20/2025
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Config;

public class Trick {
    public static final int NUMBER_OF_PLAYERS = GameManager.NUMBER_OF_PLAYERS;

    Card.Suit startingSuit, trumpSuit;
    Config.Bid minBid;
    int startedBy = -1;
    boolean startedFromTalon;
    int top = -1;
    Card topCard;
    CardList trickCards = new CardList();
    int number = 0;

    public int getNumber() {
        return number;
    }

    public Card.Suit getStartingSuit() {
        return startingSuit;
    }

    public int getStartedBy() {
        return startedBy;
    }

    public int getTurn() {
        return (startedBy + trickCards.size()) % NUMBER_OF_PLAYERS;
    }

    public Card.Suit getTrumpSuit() {
        return trumpSuit;
    }

    public void setTrumpSuit(Card.Suit trumpSuit) {
        this.trumpSuit = trumpSuit;
    }

    public Config.Bid getMinBid() {
        return minBid;
    }

    public CardList getTrickCards() {
        return trickCards;
    }

    public void clear(int elderHand) {
        clear();
        startedBy = elderHand;
        number = 0;
    }

    public void clear() {
        trickCards.clear();
        ++number;
        topCard = null;
        startingSuit = null;
        minBid = null;
        if (!startedFromTalon) {
            startedBy = top;
        }
        startedFromTalon = false;
        top = -1;
    }

    private void discard(Card card) {
        int discardingPlayer = -1;
        if (startingSuit != null && !card.getSuit().equals(startingSuit)) {
            discardingPlayer = getTurn();
        }

        Player[] players = GameManager.getInstance().players;
        if (discardingPlayer >= 0) {
            // 0 -> 2:left, 1:right
            // 1 -> 0:left, 2:right
            // 2 -> 1:left, 0:right
            players[(discardingPlayer + 2) % players.length].leftSuits[startingSuit.getValue()].clear();
            players[(discardingPlayer + 1) % players.length].rightSuits[startingSuit.getValue()].clear();
        }
        for (Player player : players) {
            player.discard(card);
        }

        for (int i = 0; i < players.length; ++i) {
            int totalLeft = players[(i + 1) % players.length].totalCards();
            int totalRight = players[(i + 2) % players.length].totalCards();
            Player player = players[i];
            player.updateOthers(totalLeft, totalRight);
        }
    }

    public void add(Card card, boolean fromTalon) {
        if (fromTalon) {
            startingSuit = card.getSuit();
            startedFromTalon = true;
        }
        discard(card);
    }

    public void add(Card card) {
        if (startingSuit == null) {
            startingSuit = card.getSuit();
        }
        if (startingSuit.equals(card.getSuit())) {
            if (topCard == null) {
                topCard = card;
                top = getTurn();
            } else {
                if (topCard.compareInTrick(card) < 0) {
                    topCard = card;
                    top = getTurn();
                }
            }
        }
        discard(card);
        trickCards.add(card);
    }

}