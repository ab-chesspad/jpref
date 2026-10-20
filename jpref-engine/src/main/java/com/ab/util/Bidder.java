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
 * Created: 9/3/2025
 *
 * Load bid data generated from utyatsky1, utyatsky2 and tricks-src.
 * Completed with my own data
 */

package com.ab.util;

import static com.ab.jpref.config.Config.NOP;
import static com.ab.jpref.config.Config.ROUND_SIZE;
import static com.ab.jpref.config.Config.Bid;
import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.Card.Suit;
import com.ab.jpref.cards.Card.Rank;
import com.ab.jpref.cards.CardSet;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.*;

import static com.ab.jpref.cards.Card.TOTAL_SUITS;

public class Bidder {
    private static final boolean DEBUG_LOG = false;

    public static int TOTAL_BIDS = 6;
    private final Map<String, OneBid[]> allBidData = loadBidData("utyatsky-12");
    public final List<SuitTricks> tricks = loadTricks("tricks");
    final OneBid[] allBids = new OneBid[TOTAL_BIDS];

    private static class Holder {
        static final Bidder instance = new Bidder();
    }

    public static Bidder getInstance() {
        return Holder.instance;
    }

    private Bidder() {}

    private Map<String, OneBid[]> loadBidData(String resourceName) {
        Map<String, OneBid[]> data = new HashMap<>();
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try (InputStream is = classloader.getResourceAsStream("jpref/" + resourceName);
            BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(": | -> ");
                String key = parts[1];
                String[] bidParts = parts[2].substring(1, parts[2].length() - 1).split(", |\\[|]");
                OneBid[] allBids = new OneBid[TOTAL_BIDS];
                int i = -1;
                int j = -1;
                String[] p = new String[3];
                for (String s : bidParts) {
                    if (s.isEmpty()) {
                        continue;
                    }
                    p[++j] = s;     // collect data
                    if (j == p.length - 1) {
                        int drop0 = Integer.parseInt(p[0]);
                        int drop1 = Integer.parseInt(p[1]);
                        int bid = Integer.parseInt(p[2]);
                        allBids[++i] = new Bidder.OneBid(bid, drop0, drop1);
                        j = -1;
                    }
                }
                data.put(key, allBids);
            }
        } catch (Exception e) {
            Logger.println(e.getMessage());
        }
        return data;
    }

    private List<SuitTricks> loadTricks(String resourceName) {
        List<SuitTricks> tricks = new ArrayList<>();
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try (InputStream is = classloader.getResourceAsStream("jpref/" + resourceName);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                Logger.println(DEBUG_LOG, line);
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                SuitTricks suitTricks = new SuitTricks(Integer.parseInt(parts[0].substring(0, 1)));
                suitTricks.chunk = parts[0];
                suitTricks.moves = Integer.parseInt(parts[1]);
                int oldVal = -1;
                for (int i = 0; i < suitTricks.tricks.length; ++i) {
                    if (i < parts.length - 1) {
                        suitTricks.tricks[i] = Integer.parseInt(parts[i + 2]);
                        oldVal = suitTricks.tricks[i];
                    } else {
                        suitTricks.tricks[i] = oldVal;
                    }
                }
                tricks.add(suitTricks);
            }
        } catch (Exception e) {
            Logger.println(e.getMessage());
        }
        return tricks;
    }

    // get SuitTricks for all suits
    public List<SuitTricks> toSuitChunks(CardSet hand) {
        List<SuitTricks> pairs = new ArrayList<>();
        int bitset = 0;
        while ((bitset = CardSet.bm4NextSuit(hand.getBitmap(), bitset)) != 0) {
            StringBuilder sb = new StringBuilder();
            Suit suit = null;
            int bit = 0;
            while ((bit = CardSet.prev(bitset, bit)) != 0) {
                int rank = Card.get(bit).getRank().getValue();
                suit = Card.get(bit).getSuit();
                sb.append(String.format("%X", rank));
            }
            SuitTricks pair = searchTricks(new String(sb));
            pair.suit = suit;
            pairs.add(pair);
        }

        // sort in descending order
        Collections.sort(pairs, (p0, p1) -> {
            int diff = p1.chunk.compareTo(p0.chunk);
            if (diff != 0) {
                return diff;
            }
            return CardSet.compareRanks(hand.list(p1.suit).getBitmap(), hand.list(p0.suit).getBitmap());
        });
        return pairs;
    }

    // called with:
    // 11 cards to guess max bid (getMaxPlayerBid), after human declarer's 1st move
    // 12 cards to define drops and declare round (getDrop)
    public PlayerBid getBid(CardSet hand, Bid minBid, int elderHand, int nDrops) {
        PlayerBid playerBid;
        Card added = null;
        List<SuitTricks> pairs = toSuitChunks(hand);

        // fill hand up to 12 cards to use Utyatsky's table
        // using the least significant suits and cards to minimize the impact
        int handSize = hand.size();
        CardSet handCopy = new CardSet(hand);
        if (handSize < 12) {
            if (pairs.size() < TOTAL_SUITS) {
                // add to empty suit
                final Suit[] suits = {Suit.SPADE, Suit.CLUB, Suit.DIAMOND, Suit.HEART};
                for (SuitTricks p : pairs) {
                    int suitNum = p.suit.getValue();
                    suits[suitNum] = null;
                }
                for (Suit s : suits) {
                    if (s == null) {
                        continue;
                    }
                    Card card = Card.fromName(s + "7");
                    added = card;
                    handCopy.add(card);
                    ++handSize;
                }
            } else {
                Card card;
                Suit suit = pairs.get(pairs.size() - 1).suit;
                Card first = handCopy.list(suit).first();
                if (first.getRank().compare(Rank.SEVEN) > 0) {
                    card = Card.fromName(suit + "7");
                } else {
                    Card next;
                    while(first.compareInTrick(next = handCopy.next(first)) == -1) {
                        first = next;
                    }
                    card = Card.fromName(first.getSuit().toString() + (first.getRank().getValue() + 1));
                }
                added = card;
                handCopy.add(card);
                ++handSize;
            }
            pairs = toSuitChunks(handCopy);
        }

        OneBid oneBid;
        // create key for allBidData, compute totalTricks preliminary
        StringBuilder sb = new StringBuilder();
        for (SuitTricks pair : pairs) {
            sb.append(pair.chunk);
        }
        OneBid[] allBids = allBidData.get(new String(sb));
        if (allBids != null) {
            int index = elderHand;
            if (!minBid.equals(Bid.BID_6S)) {
                index += NOP;
            }
            oneBid = allBids[index];
            int value = toBidValue(pairs, oneBid.bid);
            playerBid = new PlayerBid(value);
            if (minBid.compareTo(playerBid.toBid()) <= 0) {
                for (int i = 0; i < oneBid.drops.length; ++i) {
                    Suit suit = pairs.get(oneBid.drops[i]).suit;
                    Card drop = handCopy.list(suit).first();
                    playerBid.drops.add(drop);
                    handCopy.remove(drop);
                    if (drop.equals(added)) {
                        added = null;
                    }
                }
                if (added == null) {
                    // the table's trump choice stands, but it may miss a higher no trump
                    PlayerBid searched = searchBid(hand, minBid, elderHand);
                    if (searched.value % 10 == 5 && searched.value > playerBid.value) {
                        return searched;
                    }
                    return playerBid;
                }
            }
        }

        // not found or overbidding or invalid drops
        return searchBid(hand, minBid, elderHand);
    }

    // try every drop and every denomination, the max bid in suit order wins:
    // ♠ < ♣ < ♦ < ♥ < no trump; tricks are counted with the suit table,
    // the trump suit as if on lead; no trump only with enough cushions in all suits
    PlayerBid searchBid(CardSet hand, Bid minBid, int elderHand) {
        int bitmap = hand.getBitmap();
        // from the hand before drops, a drop must not dodge it
        Suit notTrump = getNotTrump(toSuitChunks(hand), elderHand);
        Candidate best = null;
        int bit0 = 0;
        while ((bit0 = CardSet.next(bitmap, bit0)) != 0) {
            if (hand.size() - ROUND_SIZE == 1) {
                best = evalDrops(hand, bit0, minBid, elderHand, notTrump, best);
                continue;
            }
            int bit1 = bit0;
            while ((bit1 = CardSet.next(bitmap, bit1)) != 0) {
                best = evalDrops(hand, bit0 | bit1, minBid, elderHand, notTrump, best);
            }
        }

        PlayerBid playerBid = new PlayerBid();
        playerBid.drops = new CardSet(best.drops);
        if (best.shortfall == 0) {
            playerBid.value = best.value;
        } else if (hand.size() < 12) {
            // bidding, pass
            playerBid.value = Bid.BID_PASS.getValue();
        } else {
            // declaring round, overbidding
            playerBid.value = best.contract;
        }
        return playerBid;
    }

    private Candidate evalDrops(CardSet hand, int drops, Bid minBid, int elderHand, Suit notTrump, Candidate best) {
        // length matters only with an honor to play for
        int dropSuitLengths = 0;
        int bit = 0;
        while ((bit = CardSet.next(drops, bit)) != 0) {
            CardSet suitCards = hand.list(Card.get(bit).getSuit());
            if (suitCards.last().getRank().compare(Rank.JACK) >= 0) {
                dropSuitLengths += suitCards.size();
            }
        }
        CardSet rest = new CardSet(hand.getBitmap() & ~drops);
        List<SuitTricks> pairs = toSuitChunks(rest);
        SuitTricks longest = pairs.get(0);
        best = evalContract(rest, pairs, drops, dropSuitLengths, longest, 5, false, minBid, elderHand, best);
        // 3-card trump only when there is no longer suit
        int minTrumpLength = Math.min(4, longest.length);
        for (SuitTricks trump : pairs) {
            if (trump.length >= minTrumpLength) {
                best = evalContract(rest, pairs, drops, dropSuitLengths, trump, trump.suit.getValue() + 1,
                    trump.suit == notTrump, minBid, elderHand, best);
            }
        }
        return best;
    }

    // the empirical rule for two 4-card suits, one of them AKxx: with the lead
    // the stronger one is trump, otherwise the weaker one;
    // returns the one not to choose unless it saves overbidding
    private Suit getNotTrump(List<SuitTricks> pairs, int elderHand) {
        if (pairs.size() < 2 || pairs.get(0).length != 4 || pairs.get(1).length != 4) {
            return null;
        }
        SuitTricks s0 = pairs.get(0);
        SuitTricks s1 = pairs.get(1);
        boolean ak0 = s0.chunk.startsWith("4ED");
        boolean ak1 = s1.chunk.startsWith("4ED");
        if (ak0 == ak1) {
            return null;
        }
        SuitTricks stronger = ak0 ? s0 : s1;
        SuitTricks weaker = ak0 ? s1 : s0;
        return elderHand == 0 ? weaker.suit : stronger.suit;
    }

    static final int CUSHIONS_NONE = 0;
    static final int CUSHIONS_NOT_NEEDED = 1;   // never loses the lead, a suit has no stopper
    static final int CUSHIONS_ENOUGH = 2;

    // no trump: each move gives the lead to the defenders, the opening lead too
    // without the elderhand, and each trick short of the contract; the defenders
    // can lead the same suit every time, so every suit must stop it as many times.
    // Without giving up the lead no cushions are needed at all
    private int getCushions(List<SuitTricks> pairs, int elderHand, int shortfall) {
        int defenderLeads = shortfall + (elderHand == 0 ? 0 : 1);
        for (SuitTricks pair : pairs) {
            defenderLeads += pair.moves;
        }
        int minCushions = pairs.size() < TOTAL_SUITS ? 0 : Integer.MAX_VALUE;  // a void
        for (SuitTricks pair : pairs) {
            minCushions = Math.min(minCushions, Math.min(pair.tricks[1], pair.tricks[2]));
        }
        if (minCushions < defenderLeads) {
            return CUSHIONS_NONE;
        }
        if (minCushions == 0) {
            return CUSHIONS_NOT_NEEDED;
        }
        return CUSHIONS_ENOUGH;
    }

    private Candidate evalContract(CardSet rest, List<SuitTricks> pairs, int drops, int dropSuitLengths, SuitTricks trump, int suitNum,
                                   boolean notTrump, Bid minBid, int elderHand, Candidate best) {
        int minValue = minBid.getValue();
        int minTricks = minValue / 10;
        int minSuitNum = minValue % 10;
        int tricks = calcTricks(pairs, elderHand, trump.suit);
        // the cheapest legal level in this denomination
        int level = suitNum >= minSuitNum ? minTricks : minTricks + 1;
        Candidate c = new Candidate();
        c.drops = drops;
        c.value = tricks * 10 + suitNum;
        c.shortfall = Math.max(0, level - tricks);
        if (suitNum == 5) {
            int cushions = getCushions(pairs, elderHand, c.shortfall);
            if (cushions == CUSHIONS_NONE && trump.length >= 4) {
                return best;
            }
            if (cushions == CUSHIONS_NONE) {
                // no suit of 4: the other choice is a 3-card trump, the defenders hold 5 of them,
                // so no trump stays, as weak as that trump: ♠XJK ♣KA ♦JQA ♥KA without the lead is 6-
                c.weak = true;
            } else {
                // a trump contract cannot win more than all the tricks, nor more than the top cards:
                // ♠9XJ ♣XJQKA ♥KA on lead, 7- and 7♣ take the same 7 tricks
                c.weak = cushions == CUSHIONS_NOT_NEEDED && tricks < ROUND_SIZE &&
                    !allTopTricks(rest, pairs, elderHand);
            }
        } else {
            c.weak = trump.length < 4 || notTrump;
            c.trumpMoves = trump.moves;
            c.trumpWorthy = trump.tricks[0] >= trump.moves;
            c.onLead = elderHand == 0;
            c.trumpLength = trump.length;
        }
        if (level > ROUND_SIZE) {
            c.contract = ROUND_SIZE * 10 + 5;   // only X no trump is left
        } else {
            c.contract = Math.max(level, tricks) * 10 + suitNum;
        }
        CardSet dropped = new CardSet(drops);
        c.droppedTrumps = suitNum == 5 ? 0 : dropped.size(trump.suit);
        c.voids = TOTAL_SUITS - pairs.size();
        int weakPairs = 0;
        for (Suit suit : Suit.values()) {
            if (suitNum != 5 && suit == trump.suit) {
                continue;
            }
            int kept = 0;
            for (SuitTricks pair : pairs) {
                if (pair.suit == suit) {
                    kept = pair.length;
                }
            }
            if (kept >= 3) {
                c.longSide = Math.max(c.longSide, kept);
            }
            CardSet suitDropped = dropped.list(suit);
            if (suitNum != 5 && kept == 1 && suitDropped.size() == 1) {
                CardSet original = new CardSet(rest.list(suit), suitDropped);
                if (original.last().getRank().compare(Rank.QUEEN) <= 0) {
                    ++weakPairs;
                }
            }
        }
        c.weakPairsSplit = weakPairs == 2;
        for (SuitTricks pair : pairs) {
            // a side suit giving up the lead more times than it takes, no trump has its cushions
            if (suitNum != 5 && pair.suit != trump.suit && pair.tricks[elderHand] < pair.moves) {
                c.unsureTricks += pair.tricks[elderHand];
            }
        }
        for (SuitTricks pair : pairs) {
            if (pair.length != 1) {
                continue;
            }
            if (suitNum != 5 && pair.suit != trump.suit) {
                ++c.singletons;
            } else if (suitNum == 5 && pair.tricks[elderHand] == 0) {
                ++c.singletons;     // no trump: a bare card that takes nothing loses the suit, a bare ace does not
            }
        }
        if (suitNum == 5 && elderHand == 0) {
            // with a trump, short side suits to ruff count more
            CardSet dealt = new CardSet(rest.getBitmap() | drops);
            for (SuitTricks pair : pairs) {
                c.extraChance += extraChance(rest.list(pair.suit), dealt.list(pair.suit).size());
            }
        }
        c.dropSuitLengths = dropSuitLengths;
        int bit = 0;
        while ((bit = CardSet.next(drops, bit)) != 0) {
            Rank rank = Card.get(bit).getRank();
            c.dropRanks += rank.getValue();
            if (rank.equals(Rank.ACE)) {
                ++c.droppedAces;
            }
        }
        return c.isBetterThan(best) ? c : best;
    }

    // no trump on lead, the chance of a trick more in a suit: the first card below the top ones wins
    // when the only outside card above it falls while the top ones are played, its holder has
    // no more cards in the suit; the 20 unseen cards split 10 - 10. Alone the declarer cannot
    // finesse: ♦AKJ, the ♦Q falls in 2 rounds 33%; ♠AQ, the ♠K under the ace 5%
    static double extraChance(CardSet suitCards, int dealtLength) {
        int top = 0;
        Card next = null;
        for (Card card = suitCards.last(); card != null; card = suitCards.prev(card)) {
            if (card.getRank().getValue() != Rank.ACE.getValue() - top) {
                next = card;
                break;
            }
            ++top;
        }
        if (next == null || top == 0 || Rank.ACE.getValue() - top - next.getRank().getValue() != 1) {
            return 0;   // no card to promote, nothing on top to drop it, or more than one card above it
        }
        int outside = 8 - dealtLength;
        int unseen = 20;
        double res = 0;
        for (int length = 1; length <= Math.min(top, outside); ++length) {
            // the holder of that card has length cards of the suit, it among them
            res += comb(outside - 1, length - 1) * comb(unseen - outside, 10 - length) / comb(unseen - 1, 9);
        }
        return res;
    }

    private static double comb(int n, int k) {
        if (k < 0 || k > n) {
            return 0;
        }
        double res = 1;
        for (int i = 1; i <= k; ++i) {
            res = res * (n - k + i) / i;
        }
        return res;
    }

    // every trick counted is a top card, from the ace down without a gap
    private boolean allTopTricks(CardSet hand, List<SuitTricks> pairs, int elderHand) {
        for (SuitTricks pair : pairs) {
            CardSet suitCards = hand.list(pair.suit);
            int top = 0;
            for (Card card = suitCards.last(); card != null; card = suitCards.prev(card)) {
                if (card.getRank().getValue() != Rank.ACE.getValue() - top) {
                    break;
                }
                ++top;
            }
            if (pair.tricks[elderHand] > top) {
                return false;
            }
        }
        return true;
    }

    private int calcTricks(List<SuitTricks> pairs, int elderHand, Suit trumpCandidate) {
        int tricks = 0;
        for (SuitTricks pair : pairs) {
            int turn = elderHand;
            if (pair.suit.equals(trumpCandidate) && pair.length >= 4 && !forced(pairs, pair, elderHand)) {
                // 4 trumps or more take the lead back; 3 cannot, the defenders hold 5 of them:
                // ♠XJK without the lead is no trick
                turn = 0;
            }
            tricks += pair.tricks[turn];
        }
        return tricks;
    }

    // without the lead the defenders lead a suit the declarer has to ruff, a void or a singleton
    // without the ace, every ruff shortens the trumps; with 4 of them and no ace, as many as
    // the defenders hold with the ace, the declarer cannot draw trumps then:
    // ♠JA ♣XJQK ♦8JQA, ♣ trump, ♥ led, ♣K ruffs, ♣A takes and ♥ again - 6 tricks, not 7
    private static boolean forced(List<SuitTricks> pairs, SuitTricks trump, int elderHand) {
        if (elderHand == 0 || trump.length != 4 || hasAce(trump)) {
            return false;
        }
        if (pairs.size() < TOTAL_SUITS) {
            return true;    // a void
        }
        for (SuitTricks pair : pairs) {
            if (pair != trump && pair.length == 1 && !hasAce(pair)) {
                return true;
            }
        }
        return false;
    }

    // the chunk is the length and the honors, E for the ace
    private static boolean hasAce(SuitTricks pair) {
        return pair.chunk.length() > 1 && pair.chunk.charAt(1) == 'E';
    }

    private int toBidValue(List<SuitTricks> pairs, int value) {
        int tricks = value / 10;
        int suitNum = value % 10;
        if (suitNum != 4) {
            suitNum = pairs.get(suitNum).suit.getValue();
        }
        return tricks * 10 + suitNum + 1;
    }

    public SuitTricks searchTricks(String chunk) {
        SuitTricks result = null;
        int len = chunk.length();
        chunk = len + chunk.replaceAll("x", "");
        for (SuitTricks pair : tricks) {
            if (pair.length > len) {
                continue;
            }
            if (pair.length < len) {
                break;
            }
            int _len = pair.chunk.length();
            if (chunk.length() < _len) {
                continue;
            }
            int res = pair.chunk.compareTo(chunk.substring(0, _len));
            if (res < 0) {
                break;
            }
            if (res == 0) {
                result = new SuitTricks(pair);
                break;
            }
        }
        if (result == null) {
            result = new SuitTricks(len);
        }
        return result;
    }

    // a candidate: drops and the contract they allow
    private static class Candidate {
        int drops;          // bitmap
        int value;          // natural bid, tricks * 10 + suitNum
        int shortfall;      // tricks missing to reach minBid in this denomination
        int contract;       // value to declare
        // 3-card trump, the last resort, the wrong one of two 4-card suits,
        // or no trump without stoppers: sound, but a trump contract
        // on the same level wins more than the worst case
        boolean weak;
        int trumpMoves = -1;    // -1 for no trump
        boolean trumpWorthy;    // takes at least as many tricks as moves it needs
        boolean onLead;
        int trumpLength;
        int droppedTrumps;
        int droppedAces;
        // drops from side suits: from 4 or 3 drop from 3, from 3 and 3 both from
        // one suit - keep the longest 3+ card side suit
        int longSide;
        // the same tricks: sure ones, not the ones that need more moves than they take,
        // ♠79XJ ♣XJQKA ♥JKA, 7♣ on lead: ♣ and ♥AK are 7 sure tricks, ♠79XJ (1 for 3 moves) may give none
        int unsureTricks;
        // from two weak (up to Q) side doubletons one from each, in a trump contract;
        // otherwise the weaker doubleton goes
        boolean weakPairsSplit;
        int voids;
        int singletons;     // side suits, to ruff after the single card; no trump: bare cards with no trick
        double extraChance; // no trump on lead, the chances of tricks more than counted, see extraChance()
        int dropSuitLengths;    // keep longer side suits, more chances to catch a defender's card
        int dropRanks;

        // max bid first, then the least valuable drops
        boolean isBetterThan(Candidate that) {
            if (that == null) {
                return true;
            }
            if (shortfall != that.shortfall) {
                return shortfall < that.shortfall;
            }
            if (weak != that.weak) {
                return !weak;
            }
            // the same tricks in two trumps: the longest one, the moves below
            // decide only between two trumps of the same length
            if (trumpMoves >= 0 && that.trumpMoves >= 0 && value / 10 == that.value / 10
                && trumpLength != that.trumpLength) {
                return trumpLength > that.trumpLength;
            }
            // the same tricks in two trumps: never one that needs more moves than it takes
            if (trumpMoves >= 0 && that.trumpMoves >= 0 && value / 10 == that.value / 10
                && trumpWorthy != that.trumpWorthy) {
                return trumpWorthy;
            }
            // the same tricks in two trumps worth playing: on lead the trump ready
            // to play, otherwise the one needing more moves, trumps protect it meanwhile
            if (trumpMoves >= 0 && that.trumpMoves >= 0 && value / 10 == that.value / 10
                && trumpWorthy && that.trumpWorthy && trumpMoves != that.trumpMoves) {
                return onLead == trumpMoves < that.trumpMoves;
            }
            if (value != that.value) {
                return value > that.value;
            }
            if (droppedTrumps != that.droppedTrumps) {
                return droppedTrumps < that.droppedTrumps;
            }
            if (droppedAces != that.droppedAces) {
                return droppedAces < that.droppedAces;
            }
            if (Math.abs(extraChance - that.extraChance) > 1e-9) {
                return extraChance > that.extraChance;
            }
            // the same tricks: keep the long side suit, drop a short one whole, ♠A ♣QK ♦89XJA ♥8XJK
            // 6♦ at elderhand 1: ♣QK, not ♥8X - ♥8XJK takes 2 tricks when the ♥ split 2 - 2, ♣ is ruffed
            if (longSide != that.longSide) {
                return longSide > that.longSide;
            }
            if (voids != that.voids) {
                return voids > that.voids;
            }
            if (unsureTricks != that.unsureTricks) {
                return unsureTricks < that.unsureTricks;
            }
            if (weakPairsSplit != that.weakPairsSplit) {
                return weakPairsSplit;
            }
            if (singletons != that.singletons) {
                // a trump contract ruffs after the single card, no trump loses the suit:
                // ♠XJK ♣8KA ♦JQA ♥9KA 6-, a bare ♠K stops nothing, ♠XJK may; a bare ace is no loss
                return trumpMoves < 0 ? singletons < that.singletons : singletons > that.singletons;
            }
            if (dropSuitLengths != that.dropSuitLengths) {
                return dropSuitLengths < that.dropSuitLengths;
            }
            return dropRanks < that.dropRanks;
        }
    }

    public static class SuitTricks {
        public String chunk = "";
        public int length;
        public int moves;
        public int[] tricks = new int[3];
        public Suit suit;

        public SuitTricks(int length) {
            this.length = length;
            chunk = "" + length;
        }

        public SuitTricks(SuitTricks that) {
            chunk = new String(that.chunk);
            length = that.length;
            moves = that.moves;
            System.arraycopy(that.tricks, 0, tricks, 0, tricks.length);
            suit = that.suit;
        }

    }

    public static class OneBid {
        public final int[] drops = new int[2];
        public final int bid;

        public OneBid(int bid, int... drops) {
            System.arraycopy(drops, 0, this.drops, 0, drops.length);
            this.bid = bid;
        }

        @Override
        public String toString() {
            return Arrays.toString(drops) + ", " + bid;
        }
    }

    public static class PlayerBid implements Serializable {
        public CardSet drops = new CardSet();
        public int value;

        public PlayerBid() {}

        public PlayerBid(Bid bid) {
            this.value = bid.getValue();
        }

        public PlayerBid(int value) {
            this.value = value;
        }

        public Bid toBid() {
            if (value <= Bid.BID_PASS.getValue()) {
                return Bid.BID_PASS;
            }
            return Bid.fromValue(value);
        }

        public void setBid(Bid bid) {
            this.value = bid.getValue();
        }

    }
}