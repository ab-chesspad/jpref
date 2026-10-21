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
 * Created: 10/11/2026
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardSet;

import java.util.Arrays;

import static com.ab.jpref.config.Config.NOP;

/**
 * All-pass endgame: every split of the unseen cards between the other two players that agrees
 * with the known voids and the card counts is played out with open cards, each player taking
 * as few tricks as possible. The card with the fewest tricks on average is chosen,
 * on a tie in one suit the highest of them.
 * Hands are bitmaps as in CardSet, players are absolute numbers.
 */
class AllPassEndgame {
    // in my hand; from 6 cards on, solving with open cards gains nothing and from 7 it plays worse
    static final int MAX_CARDS = 5;
    // drop the worlds where a card already played to the trick is a mistake with open cards
    static boolean filterPlayed = true;

    // tricks of all players packed in an int, 4 bits per player
    private static final int TRICK_BITS = 4;
    private static final int TRICK_MASK = (1 << TRICK_BITS) - 1;

    // trick-start positions: who holds each card (2 bits per bit position) and the leader
    private final Memo[] memo;

    AllPassEndgame() {
        memo = new Memo[NOP];
        for (int i = 0; i < NOP; ++i) {
            memo[i] = new Memo();
        }
    }

    private static int tricks(int packed, int player) {
        return (packed >> (TRICK_BITS * player)) & TRICK_MASK;
    }

    /**
     * @param myHand        my cards, I am the player to move in the trick
     * @param leftPossible  cards the left player (me + 1) may hold
     * @param rightPossible cards the right player (me + 2) may hold
     * @param trick         the current trick, after the talon tricks
     * @return the best card, or null on a tie between suits
     */
    Card bestCard(int myHand, int leftPossible, int rightPossible, Trick trick) {
        int me = trick.getTurn();
        int left = (me + 1) % NOP;
        int right = (me + 2) % NOP;
        // the players who already played to the trick hold a card less
        int myCount = CardSet.size(myHand);
        int leftCount = trick.size() == 2 ? myCount - 1 : myCount;
        int rightCount = !trick.isEmpty() ? myCount - 1 : myCount;
        int unseen = leftPossible | rightPossible;
        if (CardSet.size(unseen) != leftCount + rightCount) {
            return null;    // the counts do not match, the hands would come out uneven
        }
        int leader = trick.getStartedBy();
        int played = trick.size();
        int suitMask = 0;
        int topBit = 0;
        int top = -1;
        int trickBits = 0;
        for (int i = 0; i < played; ++i) {
            int bit = CardSet.bit(trick.getCard(i));
            trickBits |= bit;
            if (i == 0) {
                suitMask = suitMaskOf(bit);
            }
            if ((bit & suitMask) != 0 && Integer.compareUnsigned(bit, topBit) > 0) {
                topBit = bit;
                top = (leader + i) % NOP;
            }
        }
        int legal = legal(myHand, played == 0 ? 0 : suitMask);

        // cards only one of them may hold are theirs, the others are split
        int forcedLeft = unseen & ~rightPossible;
        int free = leftPossible & rightPossible;
        int[] freeBits = bits(free);
        int n = freeBits.length;
        int k = leftCount - CardSet.size(forcedLeft);
        if (k < 0 || k > n || (forcedLeft & ~leftPossible) != 0) {
            return null;
        }
        int[] worldLeftHands = worlds(freeBits, k, forcedLeft);

        int candidates = CardSet.size(legal);
        int[] cards = bits(legal);
        long[] total = new long[candidates];
        int all = myHand | unseen | trickBits;
        int[] trickCards = new int[played];
        for (int i = 0; i < played; ++i) {
            trickCards[i] = CardSet.bit(trick.getCard(i));
        }
        if (filterPlayed && played > 0) {
            int[] consistent = new int[worldLeftHands.length];
            int count = 0;
            for (int leftHand : worldLeftHands) {
                int[] h = new int[NOP];
                h[me] = myHand;
                h[left] = leftHand;
                h[right] = unseen & ~leftHand;
                if (playedRight(h, leader, trickCards)) {
                    consistent[count++] = leftHand;
                }
            }
            if (count > 0) {
                worldLeftHands = Arrays.copyOf(consistent, count);
            }
        }
        int worlds = worldLeftHands.length;
        for (int leftHand : worldLeftHands) {
            int rightHand = unseen & ~leftHand;
            int[] h = new int[NOP];
            h[me] = myHand;
            h[left] = leftHand;
            h[right] = rightHand;
            for (int c = 0; c < candidates; ++c) {
                int bit = cards[c];
                if (c > 0 && equivalent(cards[c - 1], bit, all)) {
                    continue;   // the same tricks, copied below
                }
                h[me] ^= bit;
                int newSuitMask = played == 0 ? suitMaskOf(bit) : suitMask;
                int newTopBit = topBit;
                int newTop = top;
                if (played == 0 || (bit & suitMask) != 0 && Integer.compareUnsigned(bit, topBit) > 0) {
                    newTopBit = bit;
                    newTop = me;
                }
                int r = play(h, leader, played + 1, newSuitMask, newTopBit, newTop, trickBits | bit);
                total[c] += tricks(r, me);
                h[me] ^= bit;
            }
        }
        if (worlds == 0) {
            return null;
        }
        for (int c = 1; c < candidates; ++c) {
            if (equivalent(cards[c - 1], cards[c], all)) {
                total[c] = total[c - 1];
            }
        }
        int best = -1;
        for (int c = 0; c < candidates; ++c) {
            if (best < 0 || total[c] < total[best]) {
                best = c;
            }
        }
        int tied = 0;
        for (int c = 0; c < candidates; ++c) {
            if (total[c] == total[best]) {
                tied |= cards[c];
            }
        }
        if ((tied & ~suitMaskOf(CardSet.next(tied, 0))) == 0) {
            // one card, or a tie in one suit: the highest, ♥JKA after ♥QX are gone -> ♥A
            return Card.get(CardSet.msb(tied));
        }
        return null;    // a tie between suits, leave it to the heuristics
    }

    // every card played to the trick takes no more tricks for its player than another legal card would,
    // h holds the cards not played yet
    private boolean playedRight(int[] h, int leader, int[] trickCards) {
        int played = trickCards.length;
        for (int i = 0; i < played; ++i) {
            int[] hh = h.clone();
            for (int j = i; j < played; ++j) {
                hh[(leader + j) % NOP] |= trickCards[j];
            }
            int p = (leader + i) % NOP;
            int suitMask = i == 0 ? 0 : suitMaskOf(trickCards[0]);
            int topBit = 0;
            int top = -1;
            int trickBits = 0;
            for (int j = 0; j < i; ++j) {
                trickBits |= trickCards[j];
                if ((trickCards[j] & suitMask) != 0 && Integer.compareUnsigned(trickCards[j], topBit) > 0) {
                    topBit = trickCards[j];
                    top = (leader + j) % NOP;
                }
            }
            int all = hh[0] | hh[1] | hh[2] | trickBits;
            int legal = legal(hh[p], suitMask);
            int actual = -1;
            int min = -1;
            int prevBit = 0;
            for (int bit = CardSet.next(legal, 0); bit != 0; prevBit = bit, bit = CardSet.next(legal, bit)) {
                if (prevBit != 0 && equivalent(prevBit, bit, all) && bit != trickCards[i]) {
                    continue;
                }
                int newSuitMask = i == 0 ? suitMaskOf(bit) : suitMask;
                int newTopBit = topBit;
                int newTop = top;
                if (i == 0 || (bit & suitMask) != 0 && Integer.compareUnsigned(bit, topBit) > 0) {
                    newTopBit = bit;
                    newTop = p;
                }
                hh[p] ^= bit;
                int t = tricks(play(hh, leader, i + 1, newSuitMask, newTopBit, newTop, trickBits | bit), p);
                hh[p] ^= bit;
                if (bit == trickCards[i]) {
                    actual = t;
                }
                if (min < 0 || t < min) {
                    min = t;
                }
            }
            if (actual > min) {
                return false;
            }
        }
        return true;
    }

    // all the possible hands of the left player
    private static int[] worlds(int[] freeBits, int k, int forcedLeft) {
        int n = freeBits.length;
        int[] res = new int[(int) combinations(n, k)];
        int w = 0;
        int[] idx = new int[k];
        for (int i = 0; i < k; ++i) {
            idx[i] = i;
        }
        while (true) {
            int leftHand = forcedLeft;
            for (int i : idx) {
                leftHand |= freeBits[i];
            }
            res[w++] = leftHand;
            int i = k - 1;
            while (i >= 0 && idx[i] == n - k + i) {
                --i;
            }
            if (i < 0) {
                break;
            }
            ++idx[i];
            for (int j = i + 1; j < k; ++j) {
                idx[j] = idx[j - 1] + 1;
            }
        }
        return res;
    }

    private static long combinations(int n, int k) {
        long res = 1;
        for (int i = 1; i <= k; ++i) {
            res = res * (n - k + i) / i;
        }
        return res;
    }

    // tricks of every player from a position inside a trick to the end, packed;
    // h holds the cards not played yet
    private int play(int[] h, int leader, int k, int suitMask, int topBit, int top, int trickBits) {
        if (k == NOP) {
            return solve(h, top) + (1 << (TRICK_BITS * top));
        }
        int p = (leader + k) % NOP;
        int legal = legal(h[p], suitMask);
        int all = h[0] | h[1] | h[2] | trickBits;
        int best = -1;
        int prevBit = 0;
        for (int bit = CardSet.next(legal, 0); bit != 0; prevBit = bit, bit = CardSet.next(legal, bit)) {
            if (prevBit != 0 && equivalent(prevBit, bit, all)) {
                continue;
            }
            h[p] ^= bit;
            int newTopBit = topBit;
            int newTop = top;
            if ((bit & suitMask) != 0 && Integer.compareUnsigned(bit, topBit) > 0) {
                newTopBit = bit;
                newTop = p;
            }
            int r = play(h, leader, k + 1, suitMask, newTopBit, newTop, trickBits | bit);
            h[p] ^= bit;
            if (best < 0 || tricks(r, p) < tricks(best, p)) {
                best = r;
            }
        }
        return best;
    }

    // tricks of every player from the start of a trick to the end, packed
    private int solve(int[] h, int leader) {
        if (h[leader] == 0) {
            return 0;
        }
        long key = key(h);
        int res = memo[leader].get(key);
        if (res >= 0) {
            return res;
        }
        int best = -1;
        int all = h[0] | h[1] | h[2];
        int prevBit = 0;
        int mine = h[leader];
        for (int bit = CardSet.next(mine, 0); bit != 0; prevBit = bit, bit = CardSet.next(mine, bit)) {
            if (prevBit != 0 && equivalent(prevBit, bit, all)) {
                continue;
            }
            h[leader] ^= bit;
            int r = play(h, leader, 1, suitMaskOf(bit), bit, leader, bit);
            h[leader] ^= bit;
            if (best < 0 || tricks(r, leader) < tricks(best, leader)) {
                best = r;
            }
        }
        memo[leader].put(key, best);
        return best;
    }

    // two cards of the same suit with no other card left between them
    static boolean equivalent(int lower, int higher, int all) {
        if (suitMaskOf(lower) != suitMaskOf(higher)) {
            return false;
        }
        int between = (higher - 1) & ~(lower | (lower - 1));
        return (all & between) == 0;
    }

    static int legal(int hand, int suitMask) {
        int follow = hand & suitMask;
        return follow != 0 ? follow : hand;
    }

    static int suitMaskOf(int bit) {
        int suitNum = Integer.numberOfTrailingZeros(bit) / CardSet.SUIT_LIST_LENGTH;
        return CardSet.SUIT_MASK << (suitNum * CardSet.SUIT_LIST_LENGTH);
    }

    private static int[] bits(int bitmap) {
        int[] res = new int[CardSet.size(bitmap)];
        int i = 0;
        for (int bit = CardSet.next(bitmap, 0); bit != 0; bit = CardSet.next(bitmap, bit)) {
            res[i++] = bit;
        }
        return res;
    }

    // owner of every card, 2 bits per card position: player p + 1
    static long key(int[] h) {
        long key = 0;
        for (int p = 0; p < NOP; ++p) {
            key += (p + 1) * spread(h[p]);
        }
        return key;
    }

    // bit i moves to bit 2 * i
    private static long spread(int bitmap) {
        long x = bitmap & 0xFFFFFFFFL;
        x = (x | x << 16) & 0x0000FFFF0000FFFFL;
        x = (x | x << 8) & 0x00FF00FF00FF00FFL;
        x = (x | x << 4) & 0x0F0F0F0F0F0F0F0FL;
        x = (x | x << 2) & 0x3333333333333333L;
        x = (x | x << 1) & 0x5555555555555555L;
        return x;
    }

    // long -> int with open addressing, also for OpenPlay: Long.hashCode() folds these keys into few buckets,
    // and HashMap boxes every key; 0 is a free slot, a solved position always has some card
    static class Memo {
        private long[] keys = new long[1 << 12];
        private int[] values = new int[keys.length];
        private int size;

        // keeps the grown capacity
        void clear() {
            Arrays.fill(keys, 0);
            size = 0;
        }

        // -1 if not found
        int get(long key) {
            int mask = keys.length - 1;
            for (int i = index(key, mask); keys[i] != 0; i = (i + 1) & mask) {
                if (keys[i] == key) {
                    return values[i];
                }
            }
            return -1;
        }

        void put(long key, int value) {
            if (2 * (size + 1) > keys.length) {
                grow();
            }
            int mask = keys.length - 1;
            int i = index(key, mask);
            while (keys[i] != 0 && keys[i] != key) {
                i = (i + 1) & mask;
            }
            if (keys[i] == 0) {
                ++size;
            }
            keys[i] = key;
            values[i] = value;
        }

        private void grow() {
            long[] oldKeys = keys;
            int[] oldValues = values;
            keys = new long[2 * oldKeys.length];
            values = new int[keys.length];
            size = 0;
            for (int i = 0; i < oldKeys.length; ++i) {
                if (oldKeys[i] != 0) {
                    put(oldKeys[i], oldValues[i]);
                }
            }
        }

        // Fibonacci hashing: the high bits of the product depend on all the key bits
        private static int index(long key, int mask) {
            return (int) ((key * 0x9E3779B97F4A7C15L) >>> 32) & mask;
        }
    }
}
