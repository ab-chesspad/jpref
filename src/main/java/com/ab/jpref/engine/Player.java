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
 * Created: 1/2025/2025
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.util.Logger;

import java.util.*;

public abstract class Player {
    public static final boolean DEBUG_LOG = false;
    public static final int NOP = Config.NOP;   // Number of players
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

    protected int number;
    protected Config.Bid bid;
    protected CardSet myHand = new CardSet();
    protected CardSet leftHand = new CardSet();
    protected CardSet rightHand = new CardSet();

    private final List<RoundResults> history = new ArrayList<>();
    protected int tricks;

    public abstract Config.Bid getBid(Config.Bid minBid, int elderHand);
    public abstract void declareRound(Config.Bid minBid, int elderHand);
    public abstract void respondOnDeclaration(); // return whist or half-whist or pass
    public abstract Card play(Trick trick);

    // to be implemented in a subclass (human player)
    public void accept(Config.Queueable q) {}

    // to be implemented in a subclass (human player)
    public void clearQueue() {}

    // to be implemented in a subclass (human player)
    public void abortThread(GameManager.RestartCommand restartCommand) {}

    // to be implemented in a subclass e.g. human player
    // returns BID_WITHOUT_THREE or a game
    public Config.Bid drop() {
        return bid;
    }

    // to be implemented in a subclass (human player)
    public boolean playWhistLaying() { return true; }

    public static GameManager gameManager() {
        return GameManager.getInstance();
    }

    public int getNumber() {
        return number;
    }

    public Player() {}

    public Player(Collection<Card> cards) {
        setHand(cards);
    }

    public Player(Player other) {
        if (other != null) {
            bid = other.bid;
            myHand = other.myHand.clone();
            leftHand = other.leftHand.clone();
            rightHand = other.rightHand.clone();
        }
        tricks = 0;

    }

    public Player(CardSet cards) {
        setHand(cards);
    }

    public void setHand(CardSet cards) {
        clear();
        this.myHand.add(cards);
        CardSet complement = this.myHand.complement();
        this.leftHand.set(complement);
        this.rightHand.set(complement);
        bid = Config.Bid.BID_UNDEFINED;
        history.add(new RoundResults());
    }


    // do not check card list size
    public void setHand(Collection<Card> thisHand) {
        clear();
        this.myHand.add(thisHand);
        CardSet complement = this.myHand.complement();
        this.leftHand.set(complement);
        this.rightHand.set(complement);
    }

    public void clear() {
        myHand.clear();
        leftHand.clear();
        rightHand.clear();
        tricks = 0;
        bid = Config.Bid.BID_UNDEFINED;
    }

    public String getName() {
        return GameManager.getConfig().getPlayerName(this.number);
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
        return history.get(history.size() - 1);
    }

    public void incrementTricks() {
        setTricks(tricks + 1);
    }

    public int getTricks() {
        return tricks;
    }

    public void setTricks(int tricks) {
        this.tricks = tricks;
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

    public void clearHistory() {
        history.clear();
    }

    public void removeLastRoundResults() {
        history.remove(history.size() - 1);
    }

    public Card anyCard() {
        return anyCard(null);
    }

    public Card anyCard(Card.Suit suit) {
        return myHand.anyCard(suit);
    }

    protected Card anyCard(Trick trick, boolean grab) {
        CardSet myHand = gameManager().players[this.number].myHand;
        CardSet cardSet = new CardSet();
        if (trick.startingSuit != null) {
            cardSet = myHand.list(trick.startingSuit);
        }
        if (cardSet.isEmpty()) {
            return myHand.anyCard();
        }
        // todo: use grab
        Card card = cardSet.prev(trick.topCard);
        if (card != null) {
            return card;
        }
        return cardSet.first();
    }

    public void remove(CardSet drop) {
        Logger.printf(DEBUG_LOG, "player %s removed %s\n", this.getName(), drop.toColorString());
        myHand.remove(drop);
        leftHand.remove(drop);
        rightHand.remove(drop);
    }

    public void drop(CardSet drop) {
        remove(drop);
        accept(Config.Bid.BID_6S);
    }

    public void drop(Card card) {
        myHand.remove(card);
        leftHand.remove(card);
        rightHand.remove(card);
    }

    public int totalCards() {
        return myHand.list().size();
    }

    public static class RoundResults {
        public final int[] points = new int[PlayerPoints.values().length];

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

        public int getPoints(int index) {
            return points[index];
        }

        public void setPoints(PlayerPoints location, int points) {
            this.points[location.ordinal()] = points;
        }

        public void setPoints(int index, int points) {
            this.points[index] = points;
        }
    }

    public static class PrefExceptionRerun extends RuntimeException {
        public PrefExceptionRerun(String msg) {
            super(msg);
        }
    }

}