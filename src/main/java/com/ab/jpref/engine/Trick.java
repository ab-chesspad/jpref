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
 * Created: 3/20/2025
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import static com.ab.jpref.cards.Card.Suit;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import static com.ab.jpref.engine.Bot.targetBot;

public class Trick extends BaseTrick {
    int number;
    Suit startingSuit, trumpSuit;
    Config.Bid minBid;
    Card topCard;

    public Trick() {
        super();
    }

    public Trick(Trick that) {
        super(that);
        this.startingSuit = that.startingSuit;
        this.trumpSuit = that.trumpSuit;
        this.minBid = that.minBid;
        this.topCard = that.topCard;
        this.setNumber(that.getNumber());
    }

    public int getNumber() {
        return number;
    }

    public void incrementNumber() {
        setNumber(getNumber() + 1);
    }

    public void decrementNumber() {
        setNumber(getNumber() - 1);
    }

    public void setNumber(int number) {
        if (number < -1 || number > 10) {   // temporary 10, next will be set to 0
            throw new RuntimeException("invalid trick number " + number);
        }
        this.number = number;
    }

    public Suit getStartingSuit() {
        return startingSuit;
    }

    public Suit getTrumpSuit() {
        return trumpSuit;
    }

    public void setBid(Config.Bid bid) {
        this.minBid = bid;
        this.trumpSuit = bid.getTrump();
    }

    public void clear(int startedBy) {
        clear();
        this.setStartedBy(startedBy);
        setNumber(0);
        minBid = null;
        trumpSuit = null;
        GameManager.getInstance().discarded.clear();
    }

    @Override
    public void clear() {
        int num = getNumber();
        int top = getTop();
        super.clear();
        if (num < 9) {
            setNumber(num + 1);
        }
        topCard = null;
        startingSuit = null;
        setStartedBy(top);
    }

    public void clear(boolean startedFromTalon) {
        int startedBy = getTop();
        if (startedFromTalon) {
            startedBy = this.getStartedBy();
        }
        clear();
        this.setStartedBy(startedBy);
    }

    public void add(Card card, boolean fromTalon) {
        startingSuit = card.getSuit();
        drop(card);
    }

    public void add(Card card, int elderHand) {
        startingSuit = card.getSuit();
        drop(card);
    }

    @Override
    public void add(Card card) {
        Suit suit = card.getSuit();
        int top = getTop();
        if (startingSuit == null) {
            startingSuit = suit;
            topCard = card;
            top = getTurn();
        } else if (suit.equals(trumpSuit)) {
            if (card.compareInTrick(topCard) > 0) {
                topCard = card;
                top = getTurn();
            }
        } else if (suit.equals(startingSuit)) {
            if (topCard == null || topCard.compareInTrick(card) < 0) {
                topCard = card;
                top = getTurn();
            }
        }
        setTop(top);
        drop(card);
        super.add(card);
    }

    protected void drop(Card card) {
        GameManager gameManager = GameManager.getInstance();
        int discardingPlayer = -1;
        if (startingSuit != null && !card.getSuit().equals(startingSuit)) {
            discardingPlayer = getTurn();
        }

        Player[] players = gameManager.players;
        if (discardingPlayer >= 0) {
            CardSet remove = CardSet.getList(startingSuit);
            players[discardingPlayer].myHand.remove(remove);
            players[(discardingPlayer + 1) % players.length].rightHand.remove(remove);
            players[(discardingPlayer + 2) % players.length].leftHand.remove(remove);
            CardSet declarerHand = gameManager.declarerHand;
            if (declarerHand != null) {
                if (discardingPlayer == gameManager.declarerNumber) {
                    declarerHand.remove(remove);
                }
            }
            if (trumpSuit != null && !card.getSuit().equals(trumpSuit)) {
                remove = CardSet.getList(trumpSuit);
                players[discardingPlayer].myHand.remove(remove);
                players[(discardingPlayer + 1) % players.length].rightHand.remove(remove);
                players[(discardingPlayer + 2) % players.length].leftHand.remove(remove);
                if (declarerHand != null) {
                    if (discardingPlayer == gameManager.declarerNumber) {
                        declarerHand.remove(remove);
                    }
                }
            }
        }
        for (Player player : players) {
            player.drop(card);
        }
        if (targetBot != null) {
            targetBot.drop(card);
        }
        if (gameManager.declarerHand != null) {
            gameManager.declarerHand.remove(card);
        }

        for (int i = 0; i < players.length; ++i) {
            int totalLeft = players[(i + 1) % players.length].totalCards();
            int totalRight = players[(i + 2) % players.length].totalCards();
            CardSet allKnown = null;
            CardSet third = null;
            Player player = players[i];
            if (totalLeft == player.leftHand.size()) {
                // thus all cards are known, no more guessing
                allKnown = player.leftHand;
                third = player.rightHand;
            } else if (totalRight == player.rightHand.size()) {
                // thus all cards are known, no more guessing
                allKnown = player.rightHand;
                third = player.leftHand;
            }

            if (allKnown != null) {
                third.remove(allKnown);
            }
        }
        gameManager.discarded.add(card);
    }

    public CardList cards2List() {
        CardList cardList = new CardList();
        for (int i = 0; i < size(); ++i) {
            cardList.add(get(i));
        }
        return cardList;
    }

    public CardSet cards2CardSet() {
        CardSet cardSet = new CardSet();
        for (int i = 0; i < size(); ++i) {
            cardSet.add(get(i));
        }
        return cardSet;
    }

    public String toColorString() {
        GameManager gameManager = GameManager.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append("trick ").append(this.getNumber()).append(": ");
        String sep = "";
        if (!gameManager.getTalonCards().isEmpty()) {
            sb.append("talon: ").append(gameManager.getTalonCards().last().toColorString());
            sep = ", ";
        }

        for (int j = 0; j < this.size(); ++j) {
            Player player = gameManager.players[(this.getStartedBy() + j) % gameManager.players.length];
            sb.append(sep).append(player.getName()).append(": ")
                .append(this.get(j).toColorString());
            sep = ", ";
        }
        return new String(sb);
    }
}