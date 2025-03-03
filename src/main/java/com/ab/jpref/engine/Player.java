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
        leftPoints, rightPoints, poolPoints, dumpPoints, total
    }
    protected final String name;
    protected Config.Bid bid;
    protected final CardList[] mySuits = new CardList[Card.Suit.values().length - 1];
    //    private final CardList[] theirSuits = new CardList[Suit.values().length - 1];
    @SuppressWarnings("unchecked")
    protected final Set<Card>[] theirSuits = new Set[Card.Suit.values().length - 1];
    final List<RoundResults> history = new LinkedList<>();
    int tricks;
    RoundData roundData;

//    private final List<Card> leftHand = new ArrayList<>();    // cards of left player (supposed or open)
//    private final List<Card> rightHand = new ArrayList<>();   // cards of right player (supposed or open)

    // minBid needed to compare loss between pass and bid
//    public abstract Config.Bid getMaxBid(CardList[] mySuits, Set<Card> discarded, int turn, Config.Bid leftBid, Config.Bid rightBid, Config.Bid minBid);
//    public abstract Player.RoundData declareRound(int turn, Config.Bid leftBid, Config.Bid rightBid, Config.Bid minBid);
//    public abstract Card play(Card.Suit startSuit, Card leftCard, Card rightCard, Set<Card> discarded);

    public abstract Config.Bid getBid(Config.Bid minBid, int turn);
    //    public abstract Config.Bid getMaxBid(Config.Bid minBid, int turn, Config.Bid leftBid, Config.Bid rightBid);
    public abstract Player.RoundData declareRound(Config.Bid minBid, int turn);
    public abstract Card play(GameManager.Trick trick);

    // to be implemented in a subclass
    public void accept(Queueable q) {}

    // to be implemented in a subclass
    public void clearQueue() {}

    // to be implemented in a subclass
    public void abortThread() {}

    public Player(String name) {
        this.name = name;
        for (int i = 0; i < Card.Suit.values().length - 1; ++i) {
            mySuits[i] = new CardList();
            theirSuits[i] = new HashSet<>();
        }
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
                mySuits[card.getSuit().ordinal()].add(card);
            } else {
                theirSuits[card.getSuit().ordinal()].add(card);
            }
        }
        bid = Config.Bid.BID_UNDEFINED;

        // debug:
        for (int i = 0; i < 7; ++i) {
            history.add(new RoundResults(2 + i, 3 + i,
                    10 + 2 * i, 11 + 2 * i));
        }
    }

    public void clear() {
        for (CardList cardList : mySuits) {
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
//            if (!suit.equals(oldSuit)) {
//                sb.append(" ");
//            }
            String s = suit.toString();
            if (!s.isEmpty()) {
                sb.append(s).append(" ");
            }
        }
        return sb.toString();
    }

    public void updateWithRound() {
/*
        poolPonts += roundData.poolPonts;
        dumpPoints += roundData.dumpPoints;
        leftPoints += roundData.leftPoints;
        rightPoints += roundData.rightPoints;
*/
//        roundData.clear();
    }

    public RoundData getRoundData() {
        return roundData;
    }

    public void setRoundData(RoundData roundData) {
        this.roundData = roundData;
    }

    public void incrementTricks() {
        ++tricks;
    }

    public int getTricks() {
        return tricks;
    }

    public void takeTalon(CardList talon) {
        for (Card card : talon) {
            mySuits[card.getSuit().ordinal()].add(card);
            theirSuits[card.getSuit().ordinal()].remove(card);
//            theirSuits[card.getSuit().ordinal()].remove(card);
        }
        talon.clear();
    }

    public CardList[] getMySuits() {
        return mySuits;
    }

    public List<RoundResults> getHistory() {
        return history;
    }

    protected Card play(Card card, Set<Card> discarded) {
        roundData = new RoundData();
        history.add(roundData.getRoundResults());
        int suitNum = card.getSuit().getValue();
        CardList suit = mySuits[suitNum];
        suit.remove(card);      // fix it
        discarded.add(card);
        return card;
    }

    public interface Queueable {}

    public static class RoundResults {
        final int[] points = new int[PlayerPoints.total.ordinal()];

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

        public void setPoints(int location, int points) {
            this.points[location] = points;
        }

        public int getPoints(int location) {
            return this.points[location];
        }
    }

    public static class RoundData {
        public final Config.Bid bid;
        public final int holes;
        public final Set<Card> discarded = new HashSet<>();
        RoundResults roundResults;

        public RoundData() {
            this(null, 0, null);
        }

        public RoundData(Config.Bid bid, Set<Card> discarded) {
            this(bid, 0, discarded);
        }

        public RoundData(Config.Bid bid, int holes, Set<Card> discarded) {
            this.bid = bid;
            this.holes = holes;
            if (discarded != null) {
                this.discarded.addAll(discarded);
            }
        }

        public RoundResults getRoundResults() {
            return roundResults;
        }
    }

    public static class PrefExceptionRerun extends RuntimeException {
        public PrefExceptionRerun() {
            super("game restarted");
        }
    }
}
