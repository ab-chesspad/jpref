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
 * Created: 1/25/2025
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Config;
import com.ab.util.Logger;

import java.util.*;

public abstract class Player {
    public static boolean DEBUG_LOG = false;
    public static final int NUMBER_OF_PLAYERS = GameManager.NUMBER_OF_PLAYERS;

    public enum PlayerPoints {
        leftPoints, rightPoints, poolPoints, dumpPoints, status
    }
    protected final String name;
    protected Config.Bid bid;
    protected CardList[] mySuits = new CardList[Card.Suit.values().length - 1];
    protected CardList[] leftSuits = new CardList[Card.Suit.values().length - 1];
    protected CardList[] rightSuits = new CardList[Card.Suit.values().length - 1];

    private final List<RoundResults> history = new LinkedList<>();
    int tricks;
    protected RoundResults roundResults;

    public abstract Config.Bid getBid(Config.Bid minBid, boolean elderHand);
    public abstract void declareRound(Config.Bid minBid, boolean elderHand);
    public abstract Card play(Trick trick);

    // number is being declared in subclasses, then it is more visible in debugger
    public abstract int getNumber();

    // to be implemented in a subclass (human player)
    public void accept(Queueable q) {}

    // to be implemented in a subclass (human player)
    public void clearQueue() {}

    // to be implemented in a subclass (human player)
    public void abortThread(GameManager.RestartCommand restartCommand) {}

    // to be implemented in a subclass (human player)
    // returns BID_WITHOUT_THREE or a game
    public Config.Bid drop() {
        return Config.Bid.BID_PASS;
    }

    // to be implemented in a subclass (human player)
    // whist or half or pass
    public void respondOnRoundDeclaration(Config.Bid bid, Trick trick) {

    }

    public Player(String name) {
        this.name = name;
        init();
    }

    private void init() {
        for (int i = 0; i < Card.Suit.values().length - 1; ++i) {
            mySuits[i] = new CardList();
            leftSuits[i] = new CardList();
            rightSuits[i] = new CardList();
        }
    }

    public Player(String name, Collection<Card> cards) {
        this(name);
        setHand(cards);
    }

    public Player(Player other) {
        if (other == null) {
            name = "test";
        } else {
            name = other.name;
            bid = other.bid;
            for (int i = 0; i < Card.Suit.values().length - 1; ++i) {
                mySuits[i] = (CardList) other.mySuits[i].clone();
                leftSuits[i] = (CardList) other.leftSuits[i].clone();
                rightSuits[i] = (CardList) other.rightSuits[i].clone();
            }
        }
        tricks = 0;
    }

    public Set<Card> getHand() {
        Set<Card> hand = new HashSet<>();
        for (int i = 0; i < Card.Suit.values().length - 1; ++i) {
            hand.addAll(mySuits[i]);
        }
        return  hand;
    }

    public void subtract(CardList[] declarerCards) {
        for (int i = 0; i < this.mySuits.length; ++i) {
            declarerCards[i].removeAll(this.mySuits[i]);
        }
    }

    // to play with open cards against declarer
    public static Set<Card> declarerCards() {
        GameManager gameManager = GameManager.getInstance();
        int declarerNum = GameManager.getInstance().declarer.getNumber();
        Set<Card> declarerCards = new HashSet<>(CardList.getDeck());
        subtract(declarerCards, gameManager.players[(declarerNum + 1) % NUMBER_OF_PLAYERS]);
        subtract(declarerCards, gameManager.players[(declarerNum + 2) % NUMBER_OF_PLAYERS]);
        return declarerCards;
    }

    static void subtract(Set<Card> declarerCards, Player other) {
        for (CardList suit : other.mySuits) {
            declarerCards.removeAll(suit);
        }
    }

    // we do not check card list size
    public void setHand(Collection<Card> thisHand) {
        clear();
        split(thisHand, this.mySuits);
        Set<Card> hand = new HashSet<>(thisHand);
        CardList deck = CardList.getDeck();
        Collection<Card> others = new ArrayList<>();
        for (Card card : deck) {
            if (!hand.contains(card)) {
                others.add(card);
            }
        }
        split(others, this.leftSuits);
        split(others, this.rightSuits);
        bid = Config.Bid.BID_UNDEFINED;
        roundResults = new RoundResults();
    }

    public void setHand(Collection<Card> thisHand, Collection<Card> leftHand, Collection<Card> rightHand) {
        clear();
        split(thisHand, this.mySuits);
        split(leftHand, this.leftSuits);
        split(rightHand, this.rightSuits);
        roundResults = new RoundResults();
    }

    void split(Collection<Card> hand, CardList[] result) {
        for (Card card : hand) {
            int suitNum = card.getSuit().getValue();
            result[suitNum].add(card);
        }

        for (CardList suit : result) {
            Collections.sort(suit);
        }
    }

    public void clear() {
        for (CardList cardList : mySuits) {
            cardList.clear();
        }
        for (CardList cardList : leftSuits) {
            cardList.clear();
        }
        for (CardList cardList : rightSuits) {
            cardList.clear();
        }
        this.tricks = 0;
    }

    public String getName() {
        return name;
    }

    public void setBid(Config.Bid bid) {
        this.bid = bid;
    }

    public Config.Bid getBid() {
        return bid;
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
        Card.Suit oldSuit = null;
        for (CardList suit : this.mySuits) {
            String s;
            if (color) {
                s = suit.toColorString();
            } else {
                s = suit.toString();
            }
            if (!s.isEmpty()) {
                sb.append(s).append(" ");
            }
        }
        return sb.toString();
    }

    public RoundResults getRoundResults() {
        return roundResults;
    }

    public void endRound() {
        history.add(roundResults);
    }

    public void incrementTricks() {
        ++tricks;
    }

    public int getTricks() {
        return tricks;
    }

    public void takeTalon(CardList talon) {
        for (Card card : talon) {
            int suitNum = card.getSuit().getValue();
            mySuits[suitNum].add(card);
            Collections.sort(mySuits[suitNum]);
            leftSuits[suitNum].remove(card);
            rightSuits[suitNum].remove(card);
        }
        talon.clear();
    }

    public CardList[] getMySuits() {
        return mySuits;
    }

    public List<RoundResults> getHistory() {
        return history;
    }

    public Card anyCard() {
        for (int j = 0; j < Card.Suit.values().length - 1; ++j) {
            if (!mySuits[j].isEmpty()) {
                return mySuits[j].last();
            }
        }
        return null;    // should not be here
    }

    public void drop(CardList drop) {
        StringBuilder sb = new StringBuilder(String.format("player %s dropped ",
            this.getName()));
        String sep = "";
        for (Card card : drop) {
            sb.append(sep).append(card);
            sep = ", ";
            drop(card);
        }
        Logger.printf(DEBUG_LOG, sb + "\n");
        accept(Config.Bid.BID_6S);
    }

    public void drop(Card card) {
        int suitNum = card.getSuit().getValue();
        if (mySuits[suitNum].remove(card)) {
            return;     // my own card
        }
        leftSuits[suitNum].remove(card);
        rightSuits[suitNum].remove(card);
    }

    public int totalCards() {
        return CardList.totalCards(mySuits);
    }

    public void updateOthers(int totalLeft, int totalRight) {
        CardList[] allKnown = null;
        CardList[] third = null;
        if (totalLeft == CardList.totalCards(leftSuits)) {
            allKnown = leftSuits;   // I know all cards, no more guessing
            third = rightSuits;     // thus I know all the cards
        } else if (totalRight == CardList.totalCards(rightSuits)) {
            allKnown = rightSuits;  // I know all cards, no more guessing
            third = leftSuits;      // thus I know all the cards
        }

        if (allKnown != null) {
            for (int i = 0; i < allKnown.length; ++i) {
                third[i].removeAll(allKnown[i]);
            }
        }
    }

    // to play with open cards
/*
    public void merge(Player other) {
        boolean left = this.number < other.number;
        int suitNum = -1;
        for (CardList suit : other.mySuits) {
            ++suitNum;
            for (Card card : suit) {
                if (left) {
                    rightSuits[suitNum].remove(card);
                } else {
                    leftSuits[suitNum].remove(card);
                }
            }
            if (left) {
                leftSuits[suitNum] = (CardList) other.mySuits[suitNum].clone();
            } else {
                rightSuits[suitNum] = (CardList) other.mySuits[suitNum].clone();
            }
        }
    }
*/


    public interface Queueable {}

    public static class RoundResults {
        final int[] points = new int[PlayerPoints.values().length];

        public RoundResults() {
        }

        public RoundResults(int poolPonts, int dumpPoints, int leftPoints, int rightPoints) {
            points[PlayerPoints.poolPoints.ordinal()] = poolPonts;
            points[PlayerPoints.dumpPoints.ordinal()] = dumpPoints;
            points[PlayerPoints.leftPoints.ordinal()] = leftPoints;
            points[PlayerPoints.rightPoints.ordinal()] = rightPoints;
        }

        public int getPoints(PlayerPoints location) {
            return points[location.ordinal()];
        }

        public void setPoints(PlayerPoints location, int points) {
            this.points[location.ordinal()] = points;
        }
    }

    public static class PrefExceptionRerun extends RuntimeException {
        public PrefExceptionRerun(String msg) {
            super(msg);
        }
    }
}
