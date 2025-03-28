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

import java.util.*;

public abstract class Player {
    public enum PlayerPoints {
        leftPoints, rightPoints, poolPoints, dumpPoints, status
    }
    protected final String name;
    protected Config.Bid bid;
    protected final CardList[] mySuits = new CardList[Card.Suit.values().length - 1];
    protected final CardList[] leftSuits = new CardList[Card.Suit.values().length - 1];
    protected final CardList[] rightSuits = new CardList[Card.Suit.values().length - 1];

    private final List<RoundResults> history = new LinkedList<>();
    int tricks;
    protected RoundResults roundResults;

    public abstract Config.Bid getBid(Config.Bid minBid, boolean meStart);
//    //    public abstract Config.Bid getMaxBid(Config.Bid minBid, int turn, Config.Bid leftBid, Config.Bid rightBid);
//    public abstract void discardTwo();
    public abstract void declareRound(Config.Bid minBid, boolean elderHand);
    public abstract Card play(Trick trick);

    // to be implemented in a subclass (human player)
    public void accept(Queueable q) {}

    // to be implemented in a subclass (human player)
    public void clearQueue() {}

    // to be implemented in a subclass (human player)
    public void abortThread(GameManager.RestartCommand restartCommand) {}

    // to be implemented in a subclass (human player)
    // returns BID_WITHOUT_THREE or a game
    public Config.Bid discard() {
        return Config.Bid.BID_PASS;
    }

    public Player(String name) {
        this.name = name;
        for (int i = 0; i < Card.Suit.values().length - 1; ++i) {
            mySuits[i] = new CardList();
            leftSuits[i] = new CardList();
            rightSuits[i] = new CardList();
        }
//        roundData = new RoundData();
    }

    public Player(String name, Collection<Card> cards) {
        this(name);
        setHand(cards);
    }

    // we do not check card list size
    public void setHand(Collection<Card> cards) {
        clear();
        Set<Card> hand = new HashSet<>(cards);
        CardList deck = CardList.getDeck();
        for (Card card : deck) {
            if (hand.contains(card)) {
                mySuits[card.getSuit().getValue()].add(card);
            } else {
                leftSuits[card.getSuit().getValue()].add(card);
                rightSuits[card.getSuit().getValue()].add(card);
            }
        }
        for (CardList cardList : leftSuits) {
            Collections.sort(cardList);
        }
        for (CardList cardList : rightSuits) {
            Collections.sort(cardList);
        }
        bid = Config.Bid.BID_UNDEFINED;
        roundResults = new RoundResults();
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
        StringBuilder sb = new StringBuilder();
        Card.Suit oldSuit = null;
        for (CardList suit : this.mySuits) {
            String s = suit.toString();
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

    public void discard(Card card) {
        int suitNum = card.getSuit().getValue();
        mySuits[suitNum].remove(card);
        leftSuits[suitNum].remove(card);
        rightSuits[suitNum].remove(card);
    }

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
