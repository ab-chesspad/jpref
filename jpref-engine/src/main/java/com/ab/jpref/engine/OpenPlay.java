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
 * Created: 10/17/2026
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;

import static com.ab.jpref.config.Config.NOP;

/**
 * The rest of the round played with open cards, two sides: the maximizers try to give the counted
 * players as many tricks as they can, the others as few. Hands are bitmaps as in CardSet,
 * players are absolute numbers, players' sets are bitmaps by player number.
 * All-pass talon tricks: the talon card sets the suit, everybody follows, the same player
 * starts the next trick; when nobody follows the suit, the starting player takes it.
 */
public class OpenPlay {
    private static final int BOUND_BITS = 4;
    private static final int BOUND_MASK = (1 << BOUND_BITS) - 1;
    private static final int MAX_TALON = 2;

    // one for every call, its memo keeps the grown capacity
    private static final OpenPlay instance = new OpenPlay();

    private int counted;
    private int maximizers;
    private int trumpMask;    // 0 without trump
    private int[] talon;      // the talon cards still to start a trick, in order
    // trick-start positions by leader and talon cards played: the known bounds of the counted tricks
    private final AllPassEndgame.Memo[] memo = new AllPassEndgame.Memo[NOP * (MAX_TALON + 1)];

    private OpenPlay() {
        for (int i = 0; i < memo.length; ++i) {
            memo[i] = new AllPassEndgame.Memo();
        }
    }

    private void init(int counted, int maximizers, int trumpMask, int[] talon) {
        this.counted = counted;
        this.maximizers = maximizers;
        this.trumpMask = trumpMask;
        this.talon = talon;
        for (AllPassEndgame.Memo m : memo) {
            m.clear();
        }
    }

    // the tricks of every player from the current trick to the end of the round, along one line
    // of the open play: the counted players get all the maximizers can force, not more
    public static int[] futureTricksByPlayer(int counted, int maximizers) {
        GameManager gameManager = GameManager.getInstance();
        Player[] players = gameManager.getPlayers();
        int[] h = new int[NOP];
        for (int i = 0; i < NOP; ++i) {
            h[i] = players[i].myHand.getBitmap();
        }
        Config.Bid minBid = gameManager.getMinBid();
        int trumpMask = 0;
        if (minBid != null && minBid.getTrump() != null) {
            trumpMask = CardSet.suitMask(minBid.getTrump());
        }
        int[] talon = new int[0];
        CardList talonCards = gameManager.getTalonCards();
        if (Config.Bid.BID_ALL_PASS.equals(minBid) && !talonCards.isEmpty()) {
            // the last one starts the current trick, or the next one when the current is not started
            talon = new int[talonCards.size()];
            for (int i = 0; i < talon.length; ++i) {
                talon[i] = CardSet.bit(talonCards.get(talonCards.size() - 1 - i));
            }
        }
        Trick trick = gameManager.getTrick();
        int[] cards = new int[NOP];
        for (int i = 0; i < trick.size(); ++i) {
            cards[i] = CardSet.bit(trick.getCard(i));
        }
        return futureTricksByPlayer(h, trick.getStartedBy(), cards, trick.size(), trumpMask, talon,
            counted, maximizers);
    }

    // the counted players' tricks from the current trick to the end of the round
    static int futureTricks(int[] h, int leader, int[] cards, int k, int trumpMask, int[] talon,
                            int counted, int maximizers) {
        int[] byPlayer = futureTricksByPlayer(h, leader, cards, k, trumpMask, talon, counted, maximizers);
        int res = 0;
        for (int i = 0; i < NOP; ++i) {
            res += ((counted >> i) & 1) * byPlayer[i];
        }
        return res;
    }

    // h: the cards not played yet, cards: k cards of the current trick, talon[0] starts it if any
    static synchronized int[] futureTricksByPlayer(int[] h, int leader, int[] cards, int k, int trumpMask,
                                                   int[] talon, int counted, int maximizers) {
        int suitMask = 0;
        if (talon.length > 0) {
            suitMask = AllPassEndgame.suitMaskOf(talon[0]);
        } else if (k > 0) {
            suitMask = AllPassEndgame.suitMaskOf(cards[0]);
        }
        OpenPlay openPlay = instance;
        openPlay.init(counted, maximizers, trumpMask, talon);
        int total = CardSet.size(h[leader]) + (k > 0 ? 1 : 0);
        int res = 0;
        while (res < total && openPlay.play(h.clone(), leader, 0, k, suitMask, cards.clone(), res + 1)) {
            ++res;
        }
        return openPlay.line(h.clone(), leader, 0, k, suitMask, cards.clone(), res);
    }

    // plays one line on: every card keeps the counted players' tricks to come at value, exactly;
    // the tricks of every player
    private int[] line(int[] h, int leader, int t, int k, int suitMask, int[] cards, int value) {
        int[] res = new int[NOP];
        while (true) {
            if (k == NOP) {
                int winner = winner(leader, cards, suitMask);
                ++res[winner];
                value -= (counted >> winner) & 1;
                if (t < talon.length) {
                    ++t;    // a talon trick: the same player starts the next one
                } else {
                    leader = winner;
                }
                if ((h[0] | h[1] | h[2]) == 0) {
                    return res;
                }
                k = 0;
                cards = new int[NOP];
                suitMask = t < talon.length ? AllPassEndgame.suitMaskOf(talon[t]) : 0;
                continue;
            }
            int p = (leader + k) % NOP;
            int legal = suitMask == 0 ? h[p] : AllPassEndgame.legal(h[p], suitMask);
            boolean maximizer = ((maximizers >> p) & 1) != 0;
            int chosen = 0;
            for (int bit = CardSet.next(legal, 0); bit != 0; bit = CardSet.next(legal, bit)) {
                h[p] ^= bit;
                cards[k] = bit;
                int nextSuitMask = suitMask == 0 ? AllPassEndgame.suitMaskOf(bit) : suitMask;
                boolean keeps = maximizer ? play(h, leader, t, k + 1, nextSuitMask, cards.clone(), value)
                    : !play(h, leader, t, k + 1, nextSuitMask, cards.clone(), value + 1);
                h[p] ^= bit;
                if (keeps) {
                    chosen = bit;
                    break;
                }
            }
            h[p] ^= chosen;
            cards[k] = chosen;
            if (suitMask == 0) {
                suitMask = AllPassEndgame.suitMaskOf(chosen);
            }
            ++k;
        }
    }

    // whether the maximizers can give the counted players need tricks more from the start of a trick
    private boolean canReach(int[] h, int leader, int t, int need) {
        if (need <= 0) {
            return true;
        }
        int left = CardSet.size(h[leader]);
        if (need > left) {
            return false;
        }
        AllPassEndgame.Memo m = memo[t * NOP + leader];
        long key = AllPassEndgame.key(h);
        int lo = 0;     // reachable
        int hi = left;  // not reachable above it
        int packed = m.get(key);
        if (packed >= 0) {
            lo = packed & BOUND_MASK;
            hi = packed >> BOUND_BITS;
        }
        if (need <= lo) {
            return true;
        }
        if (need > hi) {
            return false;
        }
        int suitMask = t < talon.length ? AllPassEndgame.suitMaskOf(talon[t]) : 0;
        boolean res = play(h, leader, t, 0, suitMask, new int[NOP], need);
        if (res) {
            lo = need;
        } else {
            hi = need - 1;
        }
        m.put(key, lo | hi << BOUND_BITS);
        return res;
    }

    // inside a trick: k cards played, suitMask 0 until the first card unless a talon card started it
    private boolean play(int[] h, int leader, int t, int k, int suitMask, int[] cards, int need) {
        if (k == NOP) {
            int winner = winner(leader, cards, suitMask);
            int won = (counted >> winner) & 1;
            if (t < talon.length) {
                return canReach(h, leader, t + 1, need - won);
            }
            return canReach(h, winner, t, need - won);
        }
        int p = (leader + k) % NOP;
        int legal = suitMask == 0 ? h[p] : AllPassEndgame.legal(h[p], suitMask);
        int all = h[0] | h[1] | h[2];
        for (int i = 0; i < k; ++i) {
            all |= cards[i];
        }
        boolean maximizer = ((maximizers >> p) & 1) != 0;
        int prevBit = 0;
        for (int bit = CardSet.next(legal, 0); bit != 0; prevBit = bit, bit = CardSet.next(legal, bit)) {
            if (prevBit != 0 && AllPassEndgame.equivalent(prevBit, bit, all)) {
                continue;
            }
            h[p] ^= bit;
            cards[k] = bit;
            int nextSuitMask = suitMask == 0 ? AllPassEndgame.suitMaskOf(bit) : suitMask;
            boolean res = play(h, leader, t, k + 1, nextSuitMask, cards, need);
            h[p] ^= bit;
            if (res == maximizer) {
                return res;     // the maximizer found it, or the other side stopped it
            }
        }
        return !maximizer;
    }

    // the highest trump, else the highest card of the suit; nobody followed a talon card: the leader
    private int winner(int leader, int[] cards, int suitMask) {
        int top = -1;
        int topBit = 0;
        boolean trumped = false;
        for (int i = 0; i < NOP; ++i) {
            int bit = cards[i];
            boolean trump = (bit & trumpMask) != 0;
            if (trump && !trumped) {
                trumped = true;
                top = i;
                topBit = bit;
            } else if (trump == trumped && (trumped || (bit & suitMask) != 0) &&
                    Integer.compareUnsigned(bit, topBit) > 0) {
                top = i;
                topBit = bit;
            }
        }
        if (top < 0) {
            return leader;
        }
        return (leader + top) % NOP;
    }
}
