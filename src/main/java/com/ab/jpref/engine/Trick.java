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
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.util.Logger;

public class Trick {
    public static final int NUMBER_OF_PLAYERS = GameManager.NUMBER_OF_PLAYERS;

    Card.Suit startingSuit, trumpSuit;
    Config.Bid minBid;
    int startedBy = -1;
    boolean startedFromTalon;
    int top = -1;
    Card topCard;
    CardList trickCards = new CardList(); // todo: cards are always sorted
    int number = 0;
//    int declarerNum = -1;

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

    public String toColorString() {
        GameManager gameManager = GameManager.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append("trick ").append(this.number).append(": ");
        String sep = "";
        if (this.startedFromTalon) {
            sb.append("talon: ").append(gameManager.getTalonCards().last().toColorString());
            sep = ", ";
        }

        for (int j = 0; j < gameManager.players.length; ++j) {
            Player player = gameManager.players[(this.startedBy + j) % gameManager.players.length];
            sb.append(sep).append(player.getName()).append(": ")
                .append(this.trickCards.get(j).toColorString());
            sep = ", ";
        }
        return new String(sb);
    }

    public CardList getTrickCards() {
        return trickCards;
    }

    public void clear(int startedBy) {
        clear();
        this.startedBy = startedBy;
        number = 0;
        MisereBot.trickTree = null;
        GameManager.getInstance().discarded = new CardSet();
        Logger.printf("DeclarerDrop %s\n", MisereBot.declarerDrop.name());
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

    protected void drop(Card card) {
        int droppingPlayer = -1;
        if (startingSuit != null && !card.getSuit().equals(startingSuit)) {
            droppingPlayer = getTurn();
        }

        Player[] players = GameManager.getInstance().players;
        if (droppingPlayer >= 0) {
            // 0 -> 2:left, 1:right
            // 1 -> 0:left, 2:right
            // 2 -> 1:left, 0:right
            players[(droppingPlayer + 2) % players.length].leftHand.list(startingSuit).clear();
            players[(droppingPlayer + 1) % players.length].rightHand.list(startingSuit).clear();
        }
        for (Player player : players) {
            player.drop(card);
        }

        for (int i = 0; i < players.length; ++i) {
            int totalLeft = players[(i + 1) % players.length].totalCards();
            int totalRight = players[(i + 2) % players.length].totalCards();
            Player player = players[i];
            player.updateOthers(totalLeft, totalRight);
        }
        GameManager.getInstance().discarded.add(card);
    }

    public void add(Card card, boolean fromTalon) {
        if (fromTalon) {
            startingSuit = card.getSuit();
            startedFromTalon = true;
        }
        drop(card);
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
        drop(card);
        trickCards.add(card);
    }

    @Override
    public String toString() {
        return trickCards.toString();
    }

}