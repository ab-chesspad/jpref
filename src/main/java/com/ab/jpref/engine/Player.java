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
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.util.Logger;

import java.util.*;

public abstract class Player {
    public static boolean DEBUG_LOG = false;
    public static final int NUMBER_OF_PLAYERS = GameManager.NUMBER_OF_PLAYERS;
    //    public static DeclarerDrop declarerDrop = DeclarerDrop.First;
    public static DeclarerDrop declarerDrop = DeclarerDrop.Last;

    enum DeclarerDrop {
        First,
        Last,
        Random
    }

    public enum PlayerPoints {
        leftPoints, rightPoints, poolPoints, dumpPoints, status
    }
    protected final String name;
    protected Config.Bid bid;
    protected CardSet myHand = new CardSet();
    protected CardSet leftHand = new CardSet();
    protected CardSet rightHand = new CardSet();

    private final List<RoundResults> history = new LinkedList<>();
    int tricks;
    protected RoundResults roundResults;

    public abstract Config.Bid getBid(Config.Bid minBid, int elderHand);
    public abstract void declareRound(Config.Bid minBid, int elderHand);
    public abstract Card play(Trick trick);

    // number is being declared in subclasses, then it is more visible in debugger
    public abstract int getNumber();

    // to be implemented in a subclass (human player)
    public void accept(Config.Queueable q) {}

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
            myHand = other.myHand.clone();
            leftHand = other.leftHand.clone();
            rightHand = other.rightHand.clone();
        }
        tricks = 0;
    }

    public Player(String name, CardSet cards) {
        this(name);
        setHand(cards);
        roundResults = new RoundResults();
    }

    public void setHand(CardSet cards) {
        clear();
        this.myHand.add(cards);
        CardSet complement = this.myHand.complement();
        this.leftHand.set(complement);
        this.rightHand.set(complement);
        bid = Config.Bid.BID_UNDEFINED;
        roundResults = new RoundResults();
    }

    // do not check card list size
    public void setHand(Collection<Card> thisHand) {
        clear();
        this.myHand.add(thisHand);
        CardSet complement = this.myHand.complement();
        this.leftHand.set(complement);
        this.rightHand.set(complement);
        bid = Config.Bid.BID_UNDEFINED;
        roundResults = new RoundResults();
    }

    public void clear() {
        myHand.clear();
        leftHand.clear();
        rightHand.clear();
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
        return this.myHand.toColorString(color);
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
        this.myHand.add(talon);
        this.leftHand.remove(talon);
        this.rightHand.remove(talon);
        talon.clear();
    }

    public CardSet getMyHand() {
        return myHand;
    }

    public List<RoundResults> getHistory() {
        return history;
    }

    public Card anyCard() {
        return anyCard(null);
    }

    public Card anyCard(Card.Suit suit) {
        return myHand.anyCard(suit);
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
        if (myHand.remove(card)) {
            return;     // my own card
        }
        leftHand.remove(card);
        rightHand.remove(card);
    }

    public int totalCards() {
        return myHand.list().size();
    }

    public void updateOthers(int totalLeft, int totalRight) {
        CardSet allKnown = null;
        CardSet third = null;
        if (totalLeft == leftHand.size()) {
            allKnown = leftHand;   // I know all cards, no more guessing
            third = rightHand;     // thus I know all the cards
        } else if (totalRight == rightHand.size()) {
            allKnown = rightHand;  // I know all cards, no more guessing
            third = leftHand;      // thus I know all the cards
        }

        if (allKnown != null) {
            third.remove(allKnown);
        }
    }

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
