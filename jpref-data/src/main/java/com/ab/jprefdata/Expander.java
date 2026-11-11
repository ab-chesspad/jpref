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
 * Utyatsky's hands after the drop are 10 cards; any 2 meaningless cards added to them
 * give 12-card hands with the same contract, the added cards are the drop:
 * ТКДх ТКхх КД + ♣x ♥x -> ТКДх ТКхх КДх х, drop ♣x ♥x
 * A card is meaningless when it is the lowest one in its suit, not a trump
 * and the suit does not get a trick or a move more by the tricks table: a new suit, Тх -> Тхх, КД -> КДх
 */
package com.ab.jprefdata;

import static com.ab.util.Logger.printf;

import com.ab.util.Bidder;
import static com.ab.util.Bidder.OneBid;
import static com.ab.util.Bidder.SuitTricks;
import static com.ab.util.Bidder.TOTAL_BIDS;
import com.ab.util.Tuple;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Expander {
    static final int NO_TRUMP = 4;
    static final int MAX_SUITS = 4;
    static final int SUIT_SIZE = 8;

    // a suit as the table sees it: the honors and the number of small cards
    static class Suit {
        final String honors;
        final int small;
        final boolean trump;
        final boolean added;    // got a meaningless card
        final int orig;         // the length in the source 12-card hand, 0 for a new suit

        Suit(String honors, int small, boolean trump, boolean added, int orig) {
            this.honors = honors;
            this.small = small;
            this.trump = trump;
            this.added = added;
            this.orig = orig;
        }

        int length() {
            return honors.length() + small;
        }

        SuitTricks tricks() {
            StringBuilder sb = new StringBuilder(honors);
            for (int i = 0; i < small; ++i) {
                sb.append('x');
            }
            return Bidder.getInstance().searchTricks(new String(sb));
        }

        String chunk() {
            return tricks().chunk;
        }
    }

    static class Candidate {
        final String source;
        final Tuple<Object> origin;     // the source line
        final OneBid oneBid;
        final int distance;

        Candidate(String source, Tuple<Object> origin, OneBid oneBid, int distance) {
            this.source = source;
            this.origin = origin;
            this.oneBid = oneBid;
            this.distance = distance;
        }
    }

    private final Map<String, Candidate[]> generated = new LinkedHashMap<>();
    private int conflicts;
    private Tuple<Object> origin;

    void run(List<Tuple<Object>> allBidData) {
        Set<String> known = new HashSet<>();
        for (Tuple<Object> t : allBidData) {
            known.add(t.getValue(1).toString());
        }
        for (Tuple<Object> t : allBidData) {
            String source = t.getValue(0).toString();
            String key = t.getValue(1).toString();
            OneBid[] bidData = (OneBid[]) t.getValue(2);
            origin = t;
            for (int slot = 0; slot < bidData.length; ++slot) {
                if (bidData[slot] == null) {
                    continue;
                }
                if (!validDrops(key, bidData[slot])) {
                    printf("ERROR %s: %s slot %d drops %s, not in the hand\n",
                        source, key, slot, Arrays.toString(bidData[slot].drops));
                    continue;
                }
                expand(source, key, slot, bidData[slot], known);
            }
        }

        // each new line goes right after its source line, labeled source-a, source-b, ...
        // the source label repeats in several lines, the letters go on across them
        Map<Tuple<Object>, List<Tuple<Object>>> derived = new IdentityHashMap<>();
        Map<String, Integer> letters = new HashMap<>();
        for (Map.Entry<String, Candidate[]> entry : generated.entrySet()) {
            Candidate[] candidates = entry.getValue();
            OneBid[] bidData = new OneBid[TOTAL_BIDS];
            Candidate first = null;
            for (int i = 0; i < candidates.length; ++i) {
                if (candidates[i] != null) {
                    bidData[i] = candidates[i].oneBid;
                    if (first == null) {
                        first = candidates[i];
                    }
                }
            }
            List<Tuple<Object>> list = derived.computeIfAbsent(first.origin, k -> new ArrayList<>());
            int letter = letters.merge(first.source, 1, Integer::sum) - 1;
            list.add(new Tuple<>(first.source + "-" + label(letter), entry.getKey(), bidData));
        }
        List<Tuple<Object>> all = new ArrayList<>();
        for (Tuple<Object> t : allBidData) {
            all.add(t);
            List<Tuple<Object>> list = derived.get(t);
            if (list != null) {
                all.addAll(list);
            }
        }
        allBidData.clear();
        allBidData.addAll(all);
        printf("expanded: %d hands, %d conflicts\n", generated.size(), conflicts);
    }

    private void expand(String source, String key, int slot, OneBid oneBid, Set<String> known) {
        List<String> chunks = split(key);
        int suitNum = oneBid.bid % 10;
        boolean contract = suitNum <= NO_TRUMP;
        List<Suit> suits = new ArrayList<>();
        for (int i = 0; i < chunks.size(); ++i) {
            String chunk = chunks.get(i);
            int len = Integer.parseInt(chunk.substring(0, 1));
            String honors = chunk.substring(1);
            suits.add(new Suit(honors, len - honors.length(), contract && i == suitNum, false, len));
        }

        // the drops, the lowest card of the suit, both of them refer to the 12-card order
        int[] dropped = new int[suits.size()];
        for (int d : oneBid.drops) {
            ++dropped[d];
        }
        List<Suit> rest = new ArrayList<>();
        List<Integer> vanished = new ArrayList<>();
        for (int i = 0; i < suits.size(); ++i) {
            Suit s = suits.get(i);
            String honors = s.honors;
            int small = s.small;
            for (int j = 0; j < dropped[i]; ++j) {
                if (small > 0) {
                    --small;
                } else if (!honors.isEmpty()) {
                    honors = honors.substring(0, honors.length() - 1);
                }
            }
            if (honors.length() + small > 0) {
                rest.add(new Suit(honors, small, s.trump, false, s.orig));
            } else {
                vanished.add(s.orig);
            }
        }

        // add 2 cards, the second one to the same or a later target to avoid repeats
        int targets = rest.size() + 2;
        for (int t0 = 0; t0 < targets; ++t0) {
            for (int t1 = t0; t1 < targets; ++t1) {
                List<Suit> hand = new ArrayList<>(rest);
                if (!add(hand, t0, rest.size()) || !add(hand, t1, rest.size())) {
                    continue;
                }
                store(source, slot, oneBid, suitNum, hand, vanished, known);
            }
        }
    }

    // target: an existing suit, or new suit 1 or 2
    private boolean add(List<Suit> hand, int target, int restSize) {
        int index = target;
        if (target >= restSize) {
            index = restSize + (target - restSize);
            if (index > hand.size()) {
                // new suit 2 before new suit 1
                return false;
            }
            if (index == hand.size()) {
                if (hand.size() >= MAX_SUITS) {
                    return false;
                }
                hand.add(new Suit("", 1, false, true, 0));
                return true;
            }
        }
        Suit s = hand.get(index);
        if (s.trump || s.length() >= SUIT_SIZE) {
            return false;
        }
        Suit t = new Suit(s.honors, s.small + 1, false, true, s.orig);
        SuitTricks before = s.tricks();
        SuitTricks after = t.tricks();
        if (s.length() > 0 && (before.moves != after.moves || !Arrays.equals(before.tricks, after.tricks))) {
            return false;
        }
        hand.set(index, t);
        return true;
    }

    // how far the 12-card hand is from the source one, by suit lengths: what the defenders hold;
    // new suits stand for the source suits dropped entirely, the longest for the longest;
    // the kept suits changed weigh more: ТКххх КДВхх хх is closer to ТКххх КДВхх х х than ТКхххх КДВхх х
    private static int distance(List<Suit> hand, List<Integer> vanished) {
        final int keptWeight = 10;
        int distance = 0;
        List<Integer> fresh = new ArrayList<>();
        for (Suit s : hand) {
            if (s.orig == 0) {
                fresh.add(s.length());
            } else {
                distance += keptWeight * Math.abs(s.length() - s.orig);
            }
        }
        List<Integer> gone = new ArrayList<>(vanished);
        fresh.sort(Collections.reverseOrder());
        gone.sort(Collections.reverseOrder());
        for (int i = 0; i < Math.max(fresh.size(), gone.size()); ++i) {
            int f = i < fresh.size() ? fresh.get(i) : 0;
            int g = i < gone.size() ? gone.get(i) : 0;
            distance += Math.abs(f - g);
        }
        return distance;
    }

    private void store(String source, int slot, OneBid oneBid, int suitNum, List<Suit> hand, List<Integer> vanished,
                       Set<String> known) {
        List<Suit> sorted = new ArrayList<>(hand);
        // descending by chunk as Bidder.toSuitChunks, the same chunks stay in the source order
        Collections.sort(sorted, (s0, s1) -> s1.chunk().compareTo(s0.chunk()));
        StringBuilder sb = new StringBuilder();
        int bidSuit = suitNum;
        List<Integer> drops = new ArrayList<>();
        for (int i = 0; i < sorted.size(); ++i) {
            Suit s = sorted.get(i);
            sb.append(s.chunk());
            if (s.trump) {
                bidSuit = i;
            }
            if (s.added) {
                drops.add(i);
            }
        }
        String key = new String(sb);
        for (int i : drops) {
            if (bidSuit < sorted.size() && sorted.get(i).chunk().equals(sorted.get(bidSuit).chunk()) && i != bidSuit) {
                return;     // Bidder could not tell the dropped suit from the trump
            }
        }
        if (known.contains(key)) {
            return;
        }
        // a suit with 2 added cards
        if (drops.size() == 1) {
            drops.add(drops.get(0));
        }
        if (drops.size() != 2) {
            return;
        }
        int bid = oneBid.bid / 10 * 10 + bidSuit;
        OneBid newBid = new OneBid(bid, drops.get(0), drops.get(1));
        Candidate[] candidates = generated.computeIfAbsent(key, k -> new Candidate[TOTAL_BIDS]);
        Candidate candidate = new Candidate(source, origin, newBid, distance(hand, vanished));
        Candidate old = candidates[slot];
        if (old == null) {
            candidates[slot] = candidate;
            return;
        }
        if (old.oneBid.bid == newBid.bid) {
            if (candidate.distance < old.distance) {
                candidates[slot] = candidate;
            }
            return;
        }
        ++conflicts;
        // the closest source hand, then the lower contract
        boolean better = candidate.distance < old.distance ||
            candidate.distance == old.distance && newBid.bid / 10 < old.oneBid.bid / 10;
        Candidate winner = better ? candidate : old;
        printf("conflict %s slot %d: %s %d (%d), %s %d (%d) -> %s\n", key, slot,
            old.source, old.oneBid.bid, old.distance, source, newBid.bid, candidate.distance, winner.source);
        candidates[slot] = winner;
    }

    // a, b, ..., z, aa, ab, ...
    static String label(int n) {
        String res = "";
        do {
            res = (char) ('a' + n % 26) + res;
            n = n / 26 - 1;
        } while (n >= 0);
        return res;
    }

    // a typo in the source, 2 cards dropped from a singleton, makes Bidder drop a card it does not have
    static boolean validDrops(String key, OneBid oneBid) {
        List<String> chunks = split(key);
        int[] dropped = new int[chunks.size()];
        for (int d : oneBid.drops) {
            if (d < 0 || d >= chunks.size()) {
                return false;
            }
            if (++dropped[d] > Integer.parseInt(chunks.get(d).substring(0, 1))) {
                return false;
            }
        }
        return true;
    }

    static List<String> split(String key) {
        List<String> chunks = new ArrayList<>();
        Matcher m = Pattern.compile("\\d[A-E]*").matcher(key);
        while (m.find()) {
            chunks.add(m.group());
        }
        return chunks;
    }
}
