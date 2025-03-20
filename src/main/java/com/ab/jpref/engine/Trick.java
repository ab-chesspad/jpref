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
    //        Card leftCard, rightCard;
    CardList trickCards = new CardList();

/*
    public Trick(int startedBy) {
        this.startedBy = startedBy;
    }
*/

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

    public int Started() {
        return startedBy;
    }

    public CardList getTrickCards() {
        return trickCards;
    }

    public void clear(int elderHand) {
        clear();
        startedBy = elderHand;
    }

    public void clear() {
        trickCards.clear();
        topCard = null;
        startingSuit = null;
        minBid = null;
        if (!startedFromTalon) {
//            startedBy = ++startedBy % NUMBER_OF_PLAYERS;
            startedBy = top;
        }
        startedFromTalon = false;
        top = -1;
    }

    private void discard(Card card) {
        // 0 -> 2:left, 1:right
        // 1 -> 0:left, 2:right
        // 2 -> 1:left, 0:right
        int[][] others = {{2,1},{0,2},{1,0}};   // right, left
        int discardingPlayer = -1;
        int suitNum = card.getSuit().getValue();
        if (startingSuit != null && !card.getSuit().equals(startingSuit)) {
            discardingPlayer = getTurn();
        }

        Player[] players = GameManager.getInstance().players;
        for (int i = 0; i < players.length; ++i) {
            Player player = players[i];
            player.discard(card);
        }
        if (discardingPlayer >= 0) {
            int[] _others = others[discardingPlayer];
            players[_others[0]].leftSuits[startingSuit.getValue()].clear();
            players[_others[1]].rightSuits[startingSuit.getValue()].clear();
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

//??        turn = ++turn % GameManager.getInstance().players.length;
    }

}