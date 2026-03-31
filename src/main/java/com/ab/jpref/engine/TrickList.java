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
 * Created: 8/3/2025
 * TrickList searches for minimax best list of tricks
 */

package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;

import static com.ab.jpref.cards.Card.TOTAL_RANKS;
import static com.ab.util.Logger.println;
import static com.ab.util.Logger.printf;

import java.util.*;

public class TrickList {
    public static final boolean DEBUG_LOG = false;
    public static final boolean PRINT_BEST_PATH = true;    // for debug

    public static final int NOP = Config.NOP;   // Number of players
    public static final int ROUND_SIZE = Config.ROUND_SIZE;

    static final TrickNode[] bestNodes = new TrickNode[ROUND_SIZE + 1];
    static int nodeIndex = 0;

    final Bot targetBot;
    final Map<Long, BaseTrick> positions = new HashMap<>(1000000); // to reuse duplicate situations

    final int myNum;

    // just statistics, not used
    long start;
    public static long maxListBuildTime = 0;
    public static long maxSimilar = 0;
    public static long maxBaseCount = 0;
    public static long maxBaseDeleted = 0;
    public static long maxLocalCount = 0;
    int nodeCount = 0;
    int similar = 0;

    final TrickNode root;

    public GameManager gameManager() {
        return GameManager.getInstance();
    }

    public TrickList(Bot targetBot, Trick trick, CardSet... hands) {
        this.targetBot = targetBot;
        myNum = gameManager().declarerNumber;
        if (bestNodes[0] == null) {
            for (int i = 0; i <= ROUND_SIZE; ++i) {
                bestNodes[i] = new TrickNode();
            }
        }
        root = new TrickNode(trick, hands);
    }

    public int getEstimate() {
        return bestNodes[0].getFutureTricks();
    }

    public Card getCard(Trick trick, CardSet... hands) {
        TrickNode bestNode = bestNodes[nodeIndex];
        boolean expected = true;
        if (trick.getNumber() == bestNode.getNumber()) {
            for (int i = 0; i < trick.size(); ++i) {
                if (!trick.get(i).equals(bestNode.get(i))) {
                    expected = false;
                    break;
                }
            }
        } else {
            // next trick
            CardSet handsCardSet = CardSet.union(hands);
            CardSet nodeCardSet = CardSet.union(bestNode.hands);
            CardSet trickCardSet = trick.cards2CardSet();
            while (!trickCardSet.isEmpty() && handsCardSet.size() < nodeCardSet.size()) {
                Card card = trickCardSet.removeLast();
                handsCardSet.add(card);
            }
            expected = nodeCardSet.equals(handsCardSet);
            for (int i = 0; i < trick.size(); ++i) {
                Card card = trick.get(i);
                if (!card.equals(bestNode.next.get(i))) {
                    expected = false;
                    break;
                }
            }
            // relative to declarer:
            int startedBy = (trick.getStartedBy() - gameManager().declarerNumber + NOP) % NOP;
            if (startedBy != bestNodes[nodeIndex + 1].getStartedBy()) {
                expected = false;
            }
        }

        if (expected) {
            if (trick.getNumber() != bestNode.getNumber()) {
                bestNode = bestNodes[++nodeIndex];
            }
        } else {
            // unexpected move, need to rebuild trick list
            String s = trick.toString();
            if (s.isEmpty()) {
                s = "not getting " + bestNode;
            }
            printf("rebuild list after %s\n", s);
            rebuild(trick, hands);
            bestNode = bestNodes[++nodeIndex];
        }

        int indx = trick.size();
        Card res = bestNode.get(indx);
        return res;
    }

    private void rebuild(Trick trick, CardSet... _hands) {
        CardSet[] hands = new CardSet[NOP];
        int[] lengths = new int[NOP];
        for (int i = 0; i < NOP; ++i) {
            hands[i] = _hands[i].clone();
            lengths[i] = hands[i].size();
        }
        int turn = (trick.getStartedBy() - gameManager().declarerNumber + NOP) % NOP;
        for (int i = 0; i < trick.size(); ++i) {
            ++lengths[turn];
            turn =  (turn + 1) % NOP;
        }

        if (lengths[0] == lengths[1] && lengths[0] == lengths[2]) {
            // looks like just an unexpected move with the same cards
            TrickNode newRoot = new TrickNode(trick, hands);
            return;
        }

        // wrong drop guess, let's redo it
        final TrickList.TrickNode[] bestNodes = new TrickList.TrickNode[ROUND_SIZE + 1];
        bestNodes[0] = null;
        CardSet drops = new CardSet();
        int maxSize = TOTAL_RANKS;
        CardSet hand0 = hands[0];
        hand0.add(Bot.playerBid.drops);
        hand0.remove(trick.cards2CardSet());
        hand0.remove(gameManager().discarded);
        CardSet dropCandidates = hand0.clone();
        Card.Suit trumpSuit = gameManager().getMinBid().getTrump();
        if (trumpSuit != null) {
            // don't consider dropping trump cards
            dropCandidates.remove(hand0.list(trumpSuit));
        }
        for (Card card0 : dropCandidates) {
            Card.Suit suit0 = card0.getSuit();
            dropCandidates.remove(card0);
            CardSet hand = dropCandidates;
            for (Card card1 : hand) {
                Card.Suit suit1 = card1.getSuit();
                printf("probing drops %s, %s: ", card0.toColorString(), card1.toColorString());
                hand0.remove(card0);
                hand0.remove(card1);
                // check the original lengths
                int _maxSize = gameManager().initialDeclarerHand.list(suit0).size();
                int size1 = gameManager().initialDeclarerHand.list(suit1).size();
                if (_maxSize < size1) {
                    _maxSize = size1;
                }
                // do analysis
                TrickList trickList = new TrickList(targetBot, trick, hands);
                int diff = compare(bestNodes[0], TrickList.bestNodes[0], 0);
                if (diff < 0 || diff == 0 && maxSize > _maxSize) {
                    drops.clear();
                    drops.add(card0);
                    drops.add(card1);
                    maxSize = _maxSize;
                    System.arraycopy(TrickList.bestNodes, 0, bestNodes, 0, bestNodes.length);
                }
                println();
                hand0.add(card1);
                hand0.add(card0);
            }
        }
        Bot.playerBid.drops.clear();
        Bot.playerBid.drops.add(drops);
        hand0.remove(drops);
        targetBot.myHand = hand0;   // replace on the newly found cards
        System.arraycopy(bestNodes, 0, TrickList.bestNodes, 0, bestNodes.length);
        printf(DEBUG_LOG, "list rebuilt after %s\n", trick.toString());
    }

    // minimax criteria, pass to targetBot
    private int compare(BaseTrick bestNode, BaseTrick probe, int index) {
        if (bestNode == null) {
            return -1;
        }
        if (probe == null) {
            return 1;
        }
        return targetBot.compare(bestNode, probe, index);
    }

int testNum = 6;
String test = null;
//String test = "[♠K9J, ♠8 ♦7 ♠Q, ♦8 ♠A ♦X, ♠X ♦9 ♣8, ♥9XA, ♣X";

    public class TrickNode extends Trick {
        CardSet[] hands = new CardSet[NOP];

        TrickNode() {}

        @Override
        public void clear() {
            int pastTricks = this.getPastTricks();
            int futureTricks = this.getFutureTricks();
            super.clear();
            this.setPastTricks(pastTricks);
            this.setFutureTricks(futureTricks);
        }

        // single-threaded
        private BaseTrick buildSubList(CardList cards) {
            if (this.hands[0].size() <= 0) {
                if (this.getNumber() < 9) {
                    throw new RuntimeException("empty hands for trick #" + this.getNumber());
                }
                return null;
            }
            int localCount = 0;
            int trickData = this.trickData;
            int trickNum = this.number;
            Card.Suit startingSuit = this.startingSuit;
            Card topCard = this.topCard;

            this.clear();
            BaseTrick bestNode0 = null;
            CardSet.CardIterator it0 = this.getBuildIterator(cards);
            while (it0.hasNext()) {
                Card card0 = it0.next();
                this.add(card0);
                BaseTrick bestNode1 = null;
                CardSet.CardIterator it1 = this.getBuildIterator(cards);
                while (it1.hasNext()) {
                    Card card1 = it1.next();
                    this.add(card1);
                    BaseTrick bestNode2 = null;
                    CardSet.CardIterator it2 = this.getBuildIterator(cards);
                    while (it2.hasNext()) {
                        Card card2 = it2.next();
                        this.add(card2);
                        BaseTrick probe = new BaseTrick(this);
                        ++localCount;
                        BaseTrick old;
                        if ((old = this.addPosition(probe)) == null) {
                            probe.next = this.buildSubList(null);
                        } else {
                            ++similar;
                            probe.next = old.next;
                        }
                        if (probe.next != null) {
                            int futureTricks = probe.next.getFutureTricks();
                            if (probe.next.getTop() == 0) {
                                ++futureTricks;
                            }
                            if (futureTricks < 0) {
                                throw new RuntimeException("futureTricks!");
                            }
                            probe.setFutureTricks(futureTricks);
                        }
                        probe.setDone();
                        if (compare(bestNode2, probe, 2) < 0) {
                            bestNode2 = probe;
                        }
                        Card c = this.removeLast();   // remove 2
                    }
                    if (compare(bestNode1, bestNode2, 1) < 0) {
                        bestNode1 = bestNode2;
                    }
                    Card c = this.removeLast();   // remove 1
                }
                if (compare(bestNode0, bestNode1, 0) < 0) {
                    bestNode0 = bestNode1;
                }
                Card c = this.removeLast();   // remove 0
            }
            // restore this:
            this.setTrickData(trickData);
            this.setNumber(trickNum);
            this.number = trickNum;
            this.startingSuit = startingSuit;
            this.topCard = topCard;
            if (maxLocalCount < localCount) {
                maxLocalCount = localCount;
            }
            return bestNode0;
        }

        // multi-threaded
        private BaseTrick buildSubList() {
            final BaseTrick[] bestTrickNode0 = {null};
            List<Thread> workers = new ArrayList<>();
            CardSet.CardIterator it0 = getBuildIterator(new CardList());
            while (it0.hasNext()) {
                final Card card0 = it0.next();
                final TrickNode trickNode = new TrickNode(this);
                Thread worker = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        trickNode.next = trickNode.buildSubList(new CardList(Arrays.asList(card0)));
                        synchronized (this) {
                            if (compare(bestTrickNode0[0], trickNode.next, 0) < 0) {
                                bestTrickNode0[0] = trickNode.next;
                            }
                        }
                    }
                });
                workers.add(worker);
                worker.start();
            }
            try {
                for (Thread worker : workers) {
                    worker.join();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return bestTrickNode0[0];
        }

        CardSet.CardIterator getBuildIterator(CardList cards) {
            if (cards != null && !cards.isEmpty()) {
                Card card = cards.removeFirst();
                return new CardSet(card).iterator();
            }
            return targetBot.getIterator(this);
        }
        private BaseTrick buildList(CardList cards) {
/*  debugging
            return buildSubList(cards);    // single-threaded
/*/
            // no sense to use multithreaded version when 1st trick cards are known
            if (cards.isEmpty()) {
                return buildSubList();         // multi-threaded
            } else {
                return buildSubList(cards); // single-threaded
            }
//*/
        }

        // create root and list of tricks
        private TrickNode(Trick trick, CardSet... hands) {
            similar = 0;
            BaseTrick.count = 0;
            BaseTrick.deleted = 0;
            this.setTop((trick.getStartedBy() - myNum + NOP) % NOP);
            this.setStartedBy(this.getTop());
            this.minBid = trick.minBid;
            init(hands);
            this.setNumber(trick.getNumber() - 1);
            printf("analysing: %s\n", CardSet.toString(hands));

            start = System.currentTimeMillis();
            BaseTrick next = buildList(trick.cards2List());
            this.setPastTricks(next.getPastTricks());
            this.setFutureTricks(next.getFutureTricks());
            StringBuilder sb = new StringBuilder();
            String sep = "[";
            int k = 0;
            bestNodes[k].init(this);
            while (next != null) {
                TrickNode trickNode = bestNodes[k];
                TrickNode nextTrickNode = bestNodes[++k];
                nextTrickNode.init(trickNode);
                nextTrickNode.clear();
                int pastTricks = nextTrickNode.getPastTricks();
                nextTrickNode.setPastTricks(0);
                for (int i = 0; i < NOP; ++i) {
                    nextTrickNode.add(next.get(i));
                }
                nextTrickNode.setPastTricks(pastTricks);
                trickNode.next = nextTrickNode; // to be cast later, todo: remove
                trickNode = nextTrickNode;
                sb.append(sep).append(trickNode);
                sep = ", ";
                next = next.next;
            }

            long dur = System.currentTimeMillis() - start;
            printf("list build duration: %d sec, positions %d, similar %,d\n",
                (dur + 500) / 1000, positions.size(), similar);
            if (PRINT_BEST_PATH) {
                sb.append("]");
                println(sb.toString());
                printf("declarer: %d tricks\n",
                    targetBot.getTricks() + this.getPastTricks() + this.getFutureTricks());
            }

            positions.clear();
            if (maxListBuildTime < dur) {
                maxListBuildTime = dur;
            }
            if (maxSimilar < similar) {
                maxSimilar = similar;
            }
            if (maxBaseCount < BaseTrick.count) {
                maxBaseCount = BaseTrick.count;
            }
            if (maxBaseDeleted < BaseTrick.deleted) {
                maxBaseDeleted = BaseTrick.deleted;
            }
            nodeIndex = 0;
        }

        void init(TrickNode that) {
            this.trickData = that.getTrickData();
            this.refresh();
            this.startingSuit = that.startingSuit;
            this.trumpSuit = that.trumpSuit;
            this.minBid = that.minBid;
            this.topCard = that.topCard;
            this.number = that.number;
            init(that.hands);
            this.next = that.next;
        }

        TrickNode(TrickNode that) {
            super(that);
            this.number = that.number;
            init(that.hands);
            this.next = that.next;
        }

        private void init(CardSet... hands) {
            this.trumpSuit = gameManager().minBid.getTrump();
            this.hands = new CardSet[hands.length];
            for (int i = 0; i < hands.length; ++i) {
                CardSet hand = hands[i];
                this.hands[i] = hand.clone();
            }
            ++nodeCount;
        }

        public BaseTrick addPosition(BaseTrick trick) {
            int bitmap = CardSet.union(hands).getBitmap();
            if (bitmap == 0) {
                return null;
            }
            long key = ((long) this.getTop() << 32) | (bitmap & 0x0ffffffffL);
            BaseTrick res;
            synchronized (positions) {
//                res = positions.putIfAbsent(key, this);   missing on Android
                res = positions.get(key);
                if (res == null) {
                    positions.put(key, trick);
                    return null;
                }
            }

            synchronized (res) {
                while (!res.isDone()) {
                    try {
                        res.wait(50);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            return res;
        }

        @Override
        public void add(Card card) {
            super.add(card);
            if (this.size() == 3) {
                if (getTop() == 0) {
                    this.updatePastTricks(1);
                }
            }
        }

        @Override
        protected void drop(Card card) {
            for (CardSet hand : hands) {
                hand.remove(card);
            }
        }

        public Card removeLast() {
            if (this.size() == 3) {
                if (this.getTop() == 0) {
                    this.updatePastTricks(-1);
                }
            }
            Card last = super.removeLast();
            if (last.equals(this.topCard)) {
                this.startingSuit = null;
                int turn = this.getStartedBy() - 1;
                this.topCard = null;
                int top = -1;
                for (int i = 0; i < size(); ++i) {
                    Card card = get(i);
                    Card.Suit suit = card.getSuit();
                    turn = ++turn % NOP;
                    if (startingSuit == null) {
                        startingSuit = suit;
                        topCard = card;
                        top = turn;
                    } else if (suit.equals(trumpSuit)) {
                        if (card.compareInTrick(topCard) > 0) {
                            topCard = card;
                            top = turn;
                        }
                    } else if (suit.equals(startingSuit)) {
                        if (topCard == null || topCard.compareInTrick(card) < 0) {
                            topCard = card;
                            top = turn;
                        }
                    }
                }
                this.setTop(top);
            }
            int num = this.getTurn();
            this.hands[num].add(last);
            return last;
        }
    }
}