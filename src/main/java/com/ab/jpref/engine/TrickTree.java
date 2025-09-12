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
 * Created: 8/3/25
 */

package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.util.Logger;

import java.util.*;

public class TrickTree {
    public static boolean DEBUG_LOG = false;
    public static final boolean CHECK_BUILD_TRICKS = false;   // for debug
    public static final boolean PRINT_WINNING_PATH = true;    // for debug
    public static final boolean ABORT_AFTER_1ST_FIND = true;

    public static final int NUMBER_OF_PLAYERS = GameManager.NUMBER_OF_PLAYERS;

    public static long maxTreeBuildTime = 0;

    final Declarer declarer;
    @SuppressWarnings("unchecked")
    final Set<Integer>[] positions = new Set[NUMBER_OF_PLAYERS]; // to remove duplicate situations

    final int myNum;
    boolean goalFound;
    long start;
    int trickCount = 0;
    int similar = 0;
    int duplicates = 0;

    TrickNode root;
    TrickNode currentTrick;

    public TrickTree(Declarer declarer, Trick trick, CardSet... hands) {
        for (int i = 0; i < positions.length; ++i) {
            positions[i] = new HashSet<>();
        }
        this.declarer = declarer;
        myNum = GameManager.getInstance().declarer.getNumber();
        root = new TrickNode(trick, hands);
//        Logger.println("done");
    }

    public Card getCard(Trick trick, CardSet... hands) {
        if (currentTrick == null) {
            if (root.children.isEmpty()) {
                return null;                // winning path not found
            }
            currentTrick = root;
        }

         if (trick.number != currentTrick.number) {
            if (!currentTrick.children.isEmpty()) {
                currentTrick = currentTrick.children.get(0);    // randomize?
            }
         }

         boolean unexpected;
         if (trick.trickCards.isEmpty()) {
             int newKey = CardSet.merge(hands);
             int oldKey = CardSet.merge(currentTrick.parent.hands);
             unexpected = newKey != oldKey;
         } else {
             unexpected = false;
             for (int i = 0; i < trick.trickCards.size(); ++i) {
                 Card newCard = trick.trickCards.get(i);
                 Card oldCard = currentTrick.trickCards.get(i);
                 if (!newCard.equals(oldCard)) {
                     unexpected = true;
                     break;
                 }
             }
         }

        if (unexpected) {
            // unexpected declarer's move, try to guess her drop anew
            hands[0] = declarer.refineDrop(GameManager.getInstance().declarerHand);
            TrickNode newRoot = new TrickNode(trick, hands);
            if (newRoot.children.isEmpty()) {
                return null;    // tree exhausted
            }
            Logger.printf(DEBUG_LOG, "tree rebuilt after %s\n", trick.toString());
            root = newRoot;
            currentTrick = root.children.get(0);
        }
        int indx = trick.trickCards.size();
        Card res = currentTrick.trickCards.get(indx);
        return res;
    }

    public class TrickNode extends Trick implements Cloneable {
        final TrickNode parent;
        final CardList initialTrick;
        int siblingIndex = 0;
        List<TrickNode> children = new ArrayList<>();
        CardSet[] hands = new CardSet[NUMBER_OF_PLAYERS];
        boolean marked;

        // to create tree root
        private TrickNode(Trick trick, CardSet... hands) {
            this.top =
            this.startedBy = (trick.startedBy - myNum + NUMBER_OF_PLAYERS) % NUMBER_OF_PLAYERS;
            parent = null;
            init(hands);
            this.number = trick.number - 1;
            initialTrick = (CardList) trick.trickCards.clone();
            // add trick cards back to hands and use them in getList()
            int i = this.startedBy;
            for (Card card : initialTrick) {
                this.hands[i].add(card);
                i = ++i % NUMBER_OF_PLAYERS;
            }
            buildTree();
        }

        private TrickNode(TrickNode parent) {
            this.parent = parent;
            this.startedBy = parent.top;
            this.number = parent.number + 1;
            init(parent.hands);
            initialTrick = (CardList) parent.initialTrick.clone();
        }

        private void init(CardSet... hands) {
            this.hands = new CardSet[hands.length];
            for (int i = 0; i < hands.length; ++i) {
                CardSet hand = hands[i];
                this.hands[i] = hand.clone();
            }
            ++trickCount;
        }

        public TrickNode cloneTrick() {
            TrickNode clone;
            try {
                clone = (TrickNode) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
            clone.init(this.hands);
            clone.children = new LinkedList<>(this.children);
            clone.trickCards = (CardList) this.trickCards.clone();
            clone.siblingIndex = this.siblingIndex + 1;
            return clone;
        }

        public int size() {
            return trickCards.size();
        }

        public int merge() {
            return CardSet.merge(hands);
        }

        @Override
        public void add(Card card) {
            if (startingSuit == null) {
                startingSuit = card.getSuit();
            }
            if (startingSuit.equals(card.getSuit())) {
                if (card.compareInTrick(topCard) > 0) {
                    topCard = card;
                    top = getTurn();
                }
            }
            drop(card);
            trickCards.add(card);
        }

        @Override
        protected void drop(Card card) {
            for (CardSet hand : hands) {
                hand.remove(card);
            }
        }

        public Card removeLast() {
            Card last = trickCards.removeLast();
            if (last.equals(this.topCard)) {
                if (!startedFromTalon) {    // just for completeness
                    this.startingSuit = null;
                }
                int turn = this.startedBy - 1;
                this.topCard = null;
                this.top = -1;
                for (Card card : trickCards) {
                    turn = ++turn % NUMBER_OF_PLAYERS;
                    if (startingSuit == null) {
                        startingSuit = card.getSuit();
                    }
                    if (startingSuit.equals(card.getSuit())) {
                        if (topCard == null) {
                            topCard = card;
                            top = turn;
                        } else {
                            if (topCard.compareInTrick(card) < 0) {
                                topCard = card;
                                top = turn;
                            }
                        }
                    }
                }
            }
            int num = this.getTurn();
            this.hands[num].add(last);
            return last;
        }

        CardSet getList() {
            if (!initialTrick.isEmpty()) {
                Card card = initialTrick.removeFirst();
                return new CardSet(card);
            }
            int num = this.getTurn();
            if (num == 0) {
/*
                if (getPath().toString().startsWith("[♣89A, ♣X")) {
                    Logger.println(CardSet.toString(hands));
                }
//*/
                Card card = TrickTree.this.declarer.playForTree(this);
                if (!this.hands[num].list(card.getSuit()).contains(card)) { // sanity check
                    alert(card);
                }
                return new CardSet(card);
            }
            Card.Suit suit = this.startingSuit;
            CardSet cardList = null;
            if (suit != null) {
                cardList = this.hands[num].list(suit);
                if (cardList.isEmpty()) {
                    suit = null;
                }
            }
            CardSet list;
            if (suit == null) {
                list = this.hands[num].list();
            } else {
                list = cardList.clone();
            }
            return list;
        }

        List<TrickNode> getPath() {
            List<TrickNode> path = new LinkedList<>();
            TrickNode trickNode = this;
            while (trickNode.parent != null) {
                path.add(0, trickNode);
                trickNode = trickNode.parent;
            }
            return path;
        }

        private void markPath() {
            TrickNode trickNode = this;
            while (trickNode.parent != null) {
                trickNode.marked = true;
                trickNode = trickNode.parent;
            }
        }

        private void buildTree() {
//            Logger.println("wait...");
            start = System.currentTimeMillis();
            goalFound = false;
            buildSubTree();
            trimSubTree();
            int totalPositions = 0;
            for (Set<Integer> position : positions) {
                totalPositions += position.size();
                position.clear();
            }
            long dur = System.currentTimeMillis() - start;
            Logger.printf("tree build duration: %d sec, positions %d\n", (dur + 500) / 1000, totalPositions);
            if (maxTreeBuildTime < dur) {
                maxTreeBuildTime = dur;
            }
        }

        private void buildSubTree() {
            TrickNode trickNode = new TrickNode(this);
            int m = trickNode.startedBy;
            int l = (trickNode.startedBy + 1) % NUMBER_OF_PLAYERS;
            int r = (trickNode.startedBy + 2) % NUMBER_OF_PLAYERS;
            CardSet list0 = trickNode.getList();
            Iterator<Card> it0 = list0.trickTreeIterator();
            while (it0.hasNext()) {
                Card card0 = it0.next();
                trickNode.add(card0);
                if (CHECK_BUILD_TRICKS) {
                    if (trickNode.hands[m].size() != trickNode.hands[r].size() - 1 ||
                        trickNode.hands[l].size() != trickNode.hands[r].size()) {
                        alert(card0);
                    }
                }
/*
                if (trickNode.getPath().toString().startsWith("[♦J8K, ♦9X ♣8, ♦7 ♥AK")) {
                    Logger.println(CardSet.toString(hands));
                }
//*/
                CardSet list1 = trickNode.getList();
                Iterator<Card> it1 = list1.trickTreeIterator();
                while (it1.hasNext()) {
                    Card card1 = it1.next();
                    trickNode.add(card1);
                    if (CHECK_BUILD_TRICKS) {
                        if (trickNode.hands[l].size() != trickNode.hands[m].size() ||
                            trickNode.hands[l].size() != trickNode.hands[r].size() - 1) {
                            alert(card1);
                        }
                    }
                    CardSet list2 = trickNode.getList();
                    Iterator<Card> it2 = list2.trickTreeIterator();
                    while (it2.hasNext()) {
                        Card card2 = it2.next();
                        trickNode.add(card2);
                        if (CHECK_BUILD_TRICKS) {
                            if (trickNode.hands[m].size() != trickNode.hands[l].size() ||
                                trickNode.hands[l].size() != trickNode.hands[r].size()) {
                                alert(card2);
                            }
                        }
                        Logger.printf(DEBUG_LOG, "%s, -> ", trickNode.toString());
                        if (trickNode.newRes()) {
                            // do not add the last trick
                            int key = trickNode.merge();
                            if (positions[trickNode.top].add(key)) {
                                Logger.printf(DEBUG_LOG, "%s, %s", trickNode.toString(), trickNode.getPath().toString());
                                this.children.add(trickNode);
                                if (declarer.stopTreeBuild(trickNode)) {
                                    trickNode.markPath();
                                    if (PRINT_WINNING_PATH) {
                                        List<TrickNode> path = trickNode.getPath();
                                        Logger.println(path.toString());
                                    }
                                    goalFound = true;
                                    if (ABORT_AFTER_1ST_FIND) {
                                        return;
                                    }
                                }
                                trickNode = trickNode.cloneTrick();   // preserve hands
                            } else {
                                ++duplicates;
                                Logger.printf(DEBUG_LOG, "duplicate, %d", duplicates);
                            }
                        } else {
                            ++similar;
                            Logger.printf(DEBUG_LOG, "similar, %d", similar);
                        }
                        Logger.println(DEBUG_LOG);
                        Card c = trickNode.removeLast();   // remove r
                        if (CHECK_BUILD_TRICKS) {
                            if (trickNode.hands[m].size() != trickNode.hands[l].size() ||
                                trickNode.hands[m].size() != trickNode.hands[r].size() - 1) {
                                alert(c);
                            }
                        }
                    }
                    Card c = trickNode.removeLast();   // remove l
                    if (CHECK_BUILD_TRICKS) {
                        if (trickNode.hands[r].size() != trickNode.hands[l].size() ||
                            trickNode.hands[m].size() != trickNode.hands[r].size() - 1) {
                            alert(c);
                        }
                    }
                }
                Card c = trickNode.removeLast();   // remove m
                if (CHECK_BUILD_TRICKS) {
                    if (trickNode.hands[r].size() != trickNode.hands[l].size() ||
                        trickNode.hands[m].size() != trickNode.hands[r].size()) {
                        alert(c);
                    }
                }
            }
            if (this.hands[0].size() <= 1) {
                return;
            }
            for (TrickNode child : this.children) {
                child.buildSubTree();
                if (ABORT_AFTER_1ST_FIND && goalFound) {
                    return;
                }
            }
        }

        private void alert(Card c) {
            // testing
            Logger.printf("error %s!\n", c.toString());
        }

        private boolean newRes() {
            if (this.siblingIndex == 0) {
                return true;
            }
            TrickNode prev = parent.children.get(this.siblingIndex - 1);
            if (this.top != prev.top) {
                return true;
            }

            Card.Suit firstSuit = null;
            for (int i = 0; i < this.trickCards.size(); ++i) {
                Card thisCard = this.trickCards.get(i);
                Card prevCard = prev.trickCards.get(i);
                if (!thisCard.getSuit().equals(prevCard.getSuit())) {
                    return true;
                }
                if (firstSuit == null) {
                    firstSuit = thisCard.getSuit();
                } else if (!firstSuit.equals(thisCard.getSuit())) {
                    return true;    // drops are important!
                }
            }
            return TrickTree.this.declarer.keepDetails(this);
        }

        private void trimSubTree() {
            int i = this.children.size();
            while (--i >= 0) {
                TrickNode child = this.children.get(i);
                if (!child.marked) {
                    this.children.remove(i);
                } else {
                    child.trimSubTree();
                }
            }
        }
    }

    public interface Declarer {
        Card playForTree(TrickNode trickNode);
        boolean keepDetails(TrickNode trickNode);
        CardSet refineDrop(CardSet hand);
        boolean stopTreeBuild(TrickNode trickNode);
    }
}