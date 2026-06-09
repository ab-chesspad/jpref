/*  This file is part of JPref project.
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
import com.ab.util.SimpleLongIntMap;

import static com.ab.jpref.config.Config.NOP;
import static com.ab.jpref.config.Config.ROUND_SIZE;

import static com.ab.jpref.cards.Card.TOTAL_RANKS;
import static com.ab.util.Logger.println;
import static com.ab.util.Logger.printf;

import java.util.*;

public class TrickList {
    public static final boolean DEBUG_LOG = false;
    public static final boolean MULTI_THREADED = false;
    public static final boolean PRINT_BEST_PATH = true;    // for debug

    static final TrickNode[] bestNodes = new TrickNode[ROUND_SIZE + 1];
    static int nodeIndex = 0;

    static TrickPool trickPool;
    static SimpleLongIntMap positions;

    final Bot targetBot;
    final int myNum;

    // just statistics, not used
    long start;
    public static long maxListBuildTime = 0;
    public static long maxSimilar = 0;
    public static long maxPoolCount = 0;
    public static long maxPositions = 0;
    int similar = 0;

    final TrickNode root;

    public static void setTrickPool(TrickPool trickPool) {
        TrickList.trickPool = trickPool;
        positions = new SimpleLongIntMap();
    }

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
        return bestNodes[0].getPastTricks() + bestNodes[0].getFutureTricks();
    }

    public Card getCard(Trick trick, CardSet... hands) {
        TrickNode bestNode = bestNodes[nodeIndex];
        boolean expected = true;
        if (trick.getNumber() == bestNode.getNumber()) {
            for (int i = 0; i < trick.size(); ++i) {
                if (!trick.getCard(i).equals(bestNode.getCard(i))) {
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
            TrickNode nextBestNode = bestNodes[nodeIndex + 1];
            for (int i = 0; i < trick.size(); ++i) {
                Card card = trick.getCard(i);
                if (!card.equals(nextBestNode.getCard(i))) {
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
        return bestNode.getCard(indx);
    }

    private void rebuild(Trick trick, CardSet... _hands) {
        CardSet[] hands = new CardSet[NOP];
        int[] lengths = new int[NOP];
        for (int i = 0; i < NOP; ++i) {
            hands[i] = new CardSet(_hands[i]);
            lengths[i] = hands[i].size();
        }
        int turn = (trick.getStartedBy() - gameManager().declarerNumber + NOP) % NOP;
        for (int i = 0; i < trick.size(); ++i) {
            ++lengths[turn];
            turn =  (turn + 1) % NOP;
        }

        int diff = lengths[1] - lengths[0];
        if (diff == 0) {
            diff = lengths[2] - lengths[0];
        }

        if (diff == 0) {
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

        if (hand0.equals(gameManager().declarerHand)) {
            // no drop search, all cards are known
            hands[0] = hand0;
            targetBot.myHand = new CardSet(hand0);
            TrickNode newRoot = new TrickNode(trick, hands);
            return;
        }

        CardSet dropCandidates = new CardSet(hand0);
        Card.Suit trumpSuit = gameManager().getMinBid().getTrump();
        if (trumpSuit != null) {
            // don't consider dropping trump cards
            dropCandidates.remove(hand0.list(trumpSuit));
        }
        int bit0 = 0;
        while ((bit0 = CardSet.next(dropCandidates.getBitmap(), bit0)) != 0) {
            Card card0 = Card.get(bit0);
            Card.Suit suit0 = card0.getSuit();
            dropCandidates.remove(card0);
            CardSet hand = dropCandidates;
            if (diff == 1) {
                hand = new CardSet(card0);
            }
            int bit1 = 0;
            while ((bit1 = CardSet.next(hand.getBitmap(), bit1)) != 0) {
                Card card1 = Card.get(bit1);
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
                int _diff = -1;
                if (bestNodes[0] != null) {
                    _diff = targetBot.compare(bestNodes[0].trickData, TrickList.bestNodes[0].trickData, 0);
                }
                if (_diff < 0 || _diff == 0 && maxSize > _maxSize) {
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
        targetBot.myHand = hand0;   // replace for the newly found cards
        System.arraycopy(bestNodes, 0, TrickList.bestNodes, 0, bestNodes.length);
        printf(DEBUG_LOG, "list rebuilt after %s\n", trick);
    }

    // minimax criteria, delegate to targetBot
    private int compare(int bestSoFarIndex, int probeIndex, int turn) {
        if (bestSoFarIndex == 0) {
            return -1;
        }
        if (probeIndex == 0) {
            return 1;
        }
        return targetBot.compare(trickPool.get(bestSoFarIndex), trickPool.get(probeIndex), turn);
    }

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
        private int buildSubList(CardList cards) {
            if (this.hands[0].size() <= 0) {
                return 0;
            }
            long trickData = this.trickData;
            int trickNum = this.number;
            Card.Suit startingSuit = this.startingSuit;
            Card topCard = this.topCard;

            this.clear();
            int bestNode0 = 0;
            long bm0 = this.bm4Iteration(cards);
            int bit0 = 0;
            while ((bit0 = CardSet.next(bm0, bit0)) != 0) {
                Card card0 = Card.get(bit0);
                this.add(card0);
                int bestNode1 = 0;
                long bm1 = this.bm4Iteration(cards);
                int bit1 = 0;
                while ((bit1 = CardSet.next(bm1, bit1)) != 0) {
                    Card card1 = Card.get(bit1);
                    this.add(card1);
                    int bestNode2 = 0;
                    long bm2 = this.bm4Iteration(cards);
                    int bit2 = 0;
                    while ((bit2 = CardSet.next(bm2, bit2)) != 0) {
                        Card card2 = Card.get(bit2);
                        this.add(card2);
                        int probeIndex = trickPool.alloc(this.getTrickData());
                        long probeData = this.getTrickData();
                        int oldIndex;
                        int nextIndex;
                        if ((oldIndex = this.addPosition(probeIndex)) == 0) {
                            nextIndex = this.buildSubList(null);
                        } else {
                            ++similar;
                            long oldData = trickPool.get(oldIndex);
//                            nextIndex = getNextIndex(oldIndex);
                            nextIndex = getNextIndex(oldData);
                        }
                        probeData = setNextIndex(probeData, nextIndex);
                        if (nextIndex != 0) {
                            long nextTrickData = trickPool.get(nextIndex);
                            int futureTricks = getFutureTricks(nextTrickData);
                            if (getTop(nextTrickData) == 0) {
                                ++futureTricks;
                            }
                            probeData = setFutureTricks(probeData, futureTricks);
                        }
                        probeData = setDone(probeData);
                        trickPool.set(probeIndex, probeData);
                        if (compare(bestNode2, probeIndex, 2) < 0) {
                            bestNode2 = probeIndex;
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
            return bestNode0;
        }

        // multi-threaded
        private int buildSubList() {
            final int[] bestTrickNode0 = {0};
            List<Thread> workers = new ArrayList<>();
            long bm0 = this.bm4Iteration(null);
            int bit0 = 0;
            while ((bit0 = CardSet.next(bm0, bit0)) != 0) {
                final Card card0 = Card.get(bit0);
                final TrickNode trickNode = new TrickNode(this);
                Thread worker = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        int next = trickNode.buildSubList(new CardList(Arrays.asList(card0)));
                        synchronized (this) {
                            if (compare(bestTrickNode0[0], next, 0) < 0) {
                                bestTrickNode0[0] = next;
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

        private long bm4Iteration(CardList cards) {
            if (cards != null && !cards.isEmpty()) {
                Card card = cards.removeFirst();
                return CardSet.bit(card);
            }
            return targetBot.bm4Iteration(this);
        }

        private int buildList(CardList cards) {
            if (MULTI_THREADED) {
                // no sense to use multithreaded version when some trick cards are known
                if (cards.isEmpty()) {
                    return buildSubList();          // multi-threaded
                } else {
                    return buildSubList(cards);     // single-threaded
                }
            } else {
                return buildSubList(cards);         // single-threaded
            }
        }

        // create root and list of tricks
        private TrickNode(Trick trick, CardSet... hands) {
            similar = 0;
            trickPool.clear();
            this.setTop((trick.getStartedBy() - myNum + NOP) % NOP);
            this.setStartedBy(this.getTop());
            this.minBid = trick.minBid;
            init(hands);
            this.setNumber(trick.getNumber() - 1);
            printf("analyzing: %s\n", CardSet.toString(hands));

            start = System.currentTimeMillis();
            int nextIndex = buildList(trick.cards2List());
            long nextTrickData = trickPool.get(nextIndex);
            int pastTricks = getPastTricks(nextTrickData);
            this.setPastTricks(pastTricks);
            this.setFutureTricks(getFutureTricks(nextTrickData));
            StringBuilder sb = new StringBuilder();
            String sep = "[";
            int k = 0;
            bestNodes[k].init(this);
            while (nextIndex != 0) {
                nextTrickData = trickPool.get(nextIndex);
                TrickNode trickNode = bestNodes[k];
                TrickNode nextTrickNode = bestNodes[++k];
                nextTrickNode.init(trickNode);
                nextTrickNode.clear();
                pastTricks = nextTrickNode.getPastTricks();
                nextTrickNode.setPastTricks(0);
                for (int i = 0; i < NOP; ++i) {
                    nextTrickNode.add(getCard(nextTrickData, i));
                }
                nextTrickNode.setPastTricks(pastTricks);
                sb.append(sep).append(nextTrickNode);
                sep = ", ";
                nextIndex = getNextIndex(nextTrickData);
            }
            bestNodes[0].setFutureTricks(this.getFutureTricks() + targetBot.getTricks());
            long dur = System.currentTimeMillis() - start;
            printf("list build duration: %d sec, positions %d, similar %,d\n",
                (dur + 500) / 1000, positions.size(), similar);
            if (PRINT_BEST_PATH) {
                sb.append("]");
                println(sb);
                printf("declarer: %d tricks\n", getEstimate());
            }

            if (maxPositions < positions.size()) {
                maxPositions = positions.size();
            }
            if (maxListBuildTime < dur) {
                maxListBuildTime = dur;
            }
            if (maxSimilar < similar) {
                maxSimilar = similar;
            }
            if (maxPoolCount < trickPool.size()) {
                maxPoolCount = trickPool.size();
            }
            positions.clear();
            nodeIndex = 0;
        }

        void init(TrickNode that) {
            this.trickData = that.getTrickData();
            this.startingSuit = that.startingSuit;
            this.trumpSuit = that.trumpSuit;
            this.minBid = that.minBid;
            this.topCard = that.topCard;
            this.number = that.number;
            init(that.hands);
            this.trickData = BaseTrick.setNextIndex(this.trickData, that.getNextIndex());
        }

        TrickNode(TrickNode that) {
            super(that);
            this.number = that.number;
            init(that.hands);
            this.trickData = BaseTrick.setNextIndex(this.trickData, that.getNextIndex());
        }

        private void init(CardSet... hands) {
            this.trumpSuit = gameManager().minBid.getTrump();
            this.hands = new CardSet[hands.length];
            for (int i = 0; i < hands.length; ++i) {
                CardSet hand = hands[i];
                this.hands[i] = new CardSet(hand);
            }
        }

        public int addPosition(int probeIndex) {
            int bitmap = CardSet.union(hands).getBitmap();
            if (bitmap == 0) {
                return 0;
            }
            long key = ((long)this.getTop() << 32) | (bitmap & 0x0ffffffffL);
            int res;
            synchronized (positions) {
                res = positions.get(key);
                if (res == 0) {
                    positions.put(key, probeIndex);
                    return 0;
                }
            }

            Thread thread = Thread.currentThread();
            synchronized (thread)
            {
                int count = 0;
                while (!isDone(trickPool.get(res))) {
                    if (++count > 100) {
                        println("still waiting...");
                        count = 0;
                    }
                    try {
                        thread.wait(50);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    res = positions.get(key);
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
                    Card card = getCard(i);
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

    public interface TrickPool {
        int DEFAULT_CAPACITY = 7000000;
        void clear();
        int alloc(long trickData);
        void set(int index, long trickData);
        long get(int index);
        int size();
        int capacity();
    }
}