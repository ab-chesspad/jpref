package com.ab.util;
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
 * Created: 9/3/2025
 *
 * Load bid data generated from utyatsky1, utyatsky2 and tricks-src.
 * Complete
 */

import com.ab.jpref.config.Config;
import com.ab.jpref.config.Config.Bid;
import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.Card.Suit;
import com.ab.jpref.cards.Card.Rank;
import com.ab.jpref.cards.CardSet;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

import static com.ab.jpref.cards.Card.TOTAL_SUITS;

public class BidData {
    private static final boolean DEBUG_LOG = false;

    public static final int NOP = Config.NOP;   // Number of players
//    private static final Map<String, BidData> maxBidData = loadBidData("utyatsky-4");
    private static final Map<String, BidData> allBidData = loadBidData("utyatsky-12");
    public static final List<Pair<String, int[]>> tricks = loadTricks("tricks");

    private static Map<String, BidData> loadBidData(String resourceName) {
        Map<String, BidData> data = new HashMap<>();
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try (InputStream is = classloader.getResourceAsStream("jpref/" + resourceName);
            BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(": | -> ");
                String key = parts[1];
                String[] bidParts = parts[2].substring(1, parts[2].length() - 1).split(", |\\[|\\]");
                BidData bidData = new BidData();
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
                        bidData.allBids[++i] = new BidData.OneBid(bid, drop0, drop1);
                        j = -1;
                    }
                }
                data.put(key, bidData);
            }
        } catch (Exception e) {
            Logger.println(e.getMessage());
        }
        return data;
    }

    private static List<Pair<String, int[]>> loadTricks(String resourceName) {
        List<Pair<String, int[]>> tricks = new ArrayList<>();
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
                int[] values = new int[3];
                int oldVal = -1;
                for (int i = 0; i < values.length; ++i) {
                    if (i < parts.length - 1) {
                        values[i] = Integer.parseInt(parts[i + 1]);
                        oldVal = values[i];
                    } else {
                        values[i] = oldVal;
                    }
                }
                tricks.add(new Pair<>(parts[0], values));
            }
        } catch (Exception e) {
            Logger.println(e.getMessage());
        }
        return tricks;
    }

    public final OneBid[] allBids = new OneBid[6];

    public BidData() {}

    public OneBid set(OneBid oneBid, int hand, boolean withBidding) {
        int index = 0;
        if (withBidding) {
            index = NOP;
        }
        index += hand;
        OneBid old = allBids[index];
        allBids[index] = oneBid;
        return old;
    }

    // get pair for each suit
    public static List<Pair<String, Integer>> toSuitChunks(CardSet hand, int turn) {
        List<Pair<String, Integer>> pairs = new ArrayList<>();
        Iterator<CardSet> suitIterator = hand.suitIterator();
        while (suitIterator.hasNext()) {
            CardSet cardSet = suitIterator.next();
            StringBuilder sb = new StringBuilder();
            Iterator<Card> cardIterator = cardSet.reverseIterator();
            while (cardIterator.hasNext()) {
                int rank = cardIterator.next().getRank().getValue();
                String s = String.format("%X", rank);
                sb.append(s);
            }
            String chunk = new String(sb);
            Pair<String, Integer> pair = searchTricks(chunk, turn);
            // append suit code to be able to locate real suits after sorting
            chunk = pair.first + cardSet.first().getSuit().toString();
            pair.first = chunk;
            pairs.add(pair);
        }
        // sort in descending order
        Collections.sort(pairs, (p0, p1) -> {
            int diff = p1.first.substring(0, p1.first.length() - 1).compareTo(p0.first.substring(0, p0.first.length() - 1));
            if (diff != 0) {
                return diff;
            }
            return hand.list(getSuit(p1.first)).compareTo(hand.list(getSuit(p0.first)));
        });
        return pairs;
    }

    // return index in pairs
    private static int getTrumpNum(List<Pair<String, Integer>> pairs, int elderHand) {
        int trumpSuitNum = 4;
        String chunk0 = pairs.get(0).first;
        int suitLen0 = Integer.parseInt(chunk0.substring(0, 1));
        if (suitLen0 >= 5) {
            trumpSuitNum = 0;
        } else if (suitLen0 == 4) {
            trumpSuitNum = 0;
            if (elderHand != 0) {
                String chunk1 = pairs.get(1).first;
                int suitLen1 = Integer.parseInt(chunk1.substring(0, 1));
                if (suitLen1 >= 4) {
                    trumpSuitNum = 1;
                }
            }
        }
        return trumpSuitNum;
    }

    // called with 11 (getMaxBid) or 12 (declareRound) cards
    public static PlayerBid getBid(CardSet hand, Bid minBid, int elderHand) {
        PlayerBid playerBid;
        hand = hand.clone();
        List<Card> added = new ArrayList<>();
        List<Pair<String, Integer>> pairs = toSuitChunks(hand, elderHand);

        // fill hand up to 12 cards
        int handSize = hand.size();
        int add = 12 - handSize;
        while (--add >= 0) {
            if (pairs.size() < TOTAL_SUITS) {
                Suit[] suits = {Suit.SPADE, Suit.CLUB, Suit.DIAMOND, Suit.HEART};
                for (Pair<String, Integer> p : pairs) {
                    int suitNum = getSuit(p.first).getValue();
                    suits[suitNum] = null;
                }
                for (Suit s : suits) {
                    if (s == null) {
                        continue;
                    }
                    Card card = Card.fromName(s + "7");
                    added.add(card);
                    hand.add(card);
                }
            } else {
                Card card;
                Suit suit = getSuit(pairs.get(TOTAL_SUITS - 1).first);
                Card first = hand.list(suit).first();
                if (first.getRank().compare(Rank.SEVEN) > 0) {
                    card = Card.fromName(suit + "7");
                } else {
                    Card next;
                    while(first.compareInTrick(next = hand.next(first)) == -1) {
                        first = next;
                    }
                    card = Card.fromName(first.getSuit().toString() + (first.getRank().getValue() + 1));
                }
                added.add(card);
                hand.add(card);
            }
            pairs = toSuitChunks(hand, elderHand);
        }

        OneBid oneBid;
        // create key for allBidData, compute totalTricks preliminary
        int totalTricks = 0;
        StringBuilder sb = new StringBuilder();
        for (Pair<String, Integer> pair : pairs) {
            String chunk = pair.first;
            totalTricks += pair.second;
            int len = chunk.length() - 1;
            sb.append(chunk, 0, len);
        }
        String key = new String(sb);
        BidData bidData = allBidData.get(key);
        if (bidData != null) {
            int index = elderHand;
            if (!minBid.equals(Bid.BID_6S)) {
                index += NOP;
            }
            oneBid = bidData.allBids[index];
            int value = toBidValue(pairs, oneBid.bid);
            playerBid = new PlayerBid(value);
            if (!added.isEmpty()) {
                return playerBid;
            }
            Config.Bid bid = playerBid.toBid();
            if (minBid.compareTo(bid) <= 0) {
                CardSet handCopy = hand.clone();
                for (int i = 0; i < oneBid.drops.length; ++i) {
                    String handChunk = pairs.get(oneBid.drops[i]).first;
                    Suit suit = getSuit(handChunk);
                    Card drop = handCopy.list(suit).first();
                    playerBid.drops.add(drop);
                    handCopy.remove(drop);
                }
                return playerBid;
            }
        }
        // not found or overbidding, use suit lists
        int trumpSuitNum = getTrumpNum(pairs, elderHand);
        // todo: calc total tricks depending on the trump
        int value = toBidValue(pairs, totalTricks * 10 + trumpSuitNum);
        playerBid = new PlayerBid(value);

        // find 1 or 2 drops
        CardSet handCopy = hand.clone();
        // brute force:
        int maxTricks = 0;
        List<Pair<String, Integer>> bestPairs = pairs;
        CardSet bestHand = handCopy.clone();
        // search starting from the least valuable suits, so for cases with the same
        // number of tricks, we drop the least valuable cards
        for (int i = pairs.size() - 1; i >= 0; --i) {
            String handChunk = pairs.get(i).first;
            Suit suit = getSuit(handChunk);
            for (Card d0 : handCopy.list(suit)) {
                handCopy.remove(d0);
                List<Pair<String, Integer>> pairs0 = toSuitChunks(handCopy, elderHand);
                if (added.size() == 1) {
                    int tricks = 0;
                    for (Pair<String, Integer> pair : pairs0) {
                        tricks += pair.second;
                    }
                    if (maxTricks < tricks) {
                        maxTricks = tricks;
                        bestPairs = pairs0;
                        bestHand = handCopy.clone();
                    }
                } else {
                    for (int j = pairs0.size() - 1; j >= 0; --j) {
                        String handChunk0 = pairs0.get(j).first;
                        Suit suit0 = getSuit(handChunk0);
                        for (Card d1 : handCopy.list(suit0)) {
                            handCopy.remove(d1);
                            List<Pair<String, Integer>> pairs1 = toSuitChunks(handCopy, elderHand);
                            int tricks = 0;
                            for (Pair<String, Integer> pair : pairs1) {
                                tricks += pair.second;
                            }
                            if (maxTricks < tricks) {
                                maxTricks = tricks;
                                bestPairs = pairs1;
                                bestHand = handCopy.clone();
                            }
                            handCopy.add(d1);
                        }
                    }
                }
                handCopy.add(d0);
            }
            trumpSuitNum = getTrumpNum(bestPairs, elderHand);
            value = toBidValue(bestPairs, maxTricks * 10 + trumpSuitNum);
            playerBid = new PlayerBid(value);
            playerBid.drops = hand.intersection(bestHand.complement());
        }
        if (added.size() == 1) {
            return playerBid;
        }
        if (elderHand != 0 && trumpSuitNum >= pairs.size()) {
            // Not an eldernahd, no Trump
            if (pairs.size() < 4) {
                playerBid.value = 64;   // the lowest bid, some suit is missing
                return playerBid;
            }
            pairs = toSuitChunks(bestHand, elderHand);
            int needToLet = 0;
            int singleAceCount = 0;
            for (Pair<String, Integer> pair : pairs) {
                if (pair.first.substring(1, 3).equals("ED")) {
                    continue;   // ok, top cards KA
                }
                if (elderHand == 2 && pair.first.substring(1, 3).equals("EC")) {
                    continue;   // ok, top cards QA
                }
                if (pair.first.length() >= 5 && pair.first.substring(1, 4).equals("ECB")) {
                    ++needToLet;   // top cards JQA
                    continue;
                }
                if (pair.first.substring(1, 2).equals("E")) {
                    ++singleAceCount;
                    continue;
                }
                ++needToLet;
            }
            if (singleAceCount >= 1 && needToLet > 0) {
                // e.g. "♠78QK ♣KA ♦JA ♥KA"
                playerBid.value -= 10;   // 1 trick less?
                return playerBid;
            }

            String chunk0 = pairs.get(0).first;
            int suitLen0 = Integer.parseInt(chunk0.substring(0, 1));
            if (suitLen0 < 4) {
                //??
            }
        }
        Config.Bid bid = playerBid.toBid();
        if (minBid.compareTo(bid) > 0) {
            // handle overbidding to our best
            int minGoal = minBid.goal();
            int minSuit = minBid.getValue() % 10;
            int bidSuit = playerBid.value % 10;
            if (bidSuit < minSuit) {
                handCopy = hand.clone();
                // should we challenge previously found drops?
                handCopy.remove(playerBid.drops);
                pairs = toSuitChunks(handCopy, elderHand);
                trumpSuitNum = getTrumpNum(pairs, elderHand);
                int i = trumpSuitNum;
                if (trumpSuitNum == 0) {    // always?
                    i = 1;
                }
                Pair<String, Integer> pair = pairs.get(i);
                String chunk = pair.first;
                Suit suit = getSuit(chunk);
                int len = Integer.parseInt(chunk.substring(0, 1));
                if (suit.getValue() >= minSuit && len >= 4) {
                    // let's take the 2nd best suit as trump
                    value = minGoal * 10 + suit.getValue() + 1;
                } else {
                    value = (minGoal + 1) * 10 + bidSuit;
                }
            } else {
                value = minGoal * 10 + bidSuit;
            }
            CardSet drops = playerBid.drops;
            playerBid = new PlayerBid(value);
            playerBid.drops = drops;
        }
        return playerBid;
    }

    public static Suit getSuit(String handChunk) {
        return Suit.fromCode(handChunk.charAt(handChunk.length() - 1));
    }

    static int toBidValue(List<Pair<String, Integer>> pairs, int value) {
        int tricks = value / 10;
        int suitNum = value % 10;
        if (suitNum != 4) {
            String handChunk = pairs.get(suitNum).first;
            suitNum = getSuit(handChunk).getValue();
        }
        return tricks * 10 + suitNum + 1;
    }

    public static Pair<String, Integer> searchTricks(String chunk, int turn) {
        Pair<String, Integer> result = null;
        int len = chunk.length();
        chunk = len + chunk.replaceAll("x", "");
        for (Pair<String, int[]> pair : tricks) {
            String tableChunk = pair.first;
            int tableLen = Integer.parseInt(tableChunk.substring(0, 1));
            if (tableLen > len) {
                continue;
            }
            if (tableLen < len) {
                break;
            }
            int _len = tableChunk.length();
            if (chunk.length() < _len) {
                continue;
            }
            int res = tableChunk.compareTo(chunk.substring(0, _len));
            if (res < 0) {
                break;
            }
            if (res == 0) {
                int value = pair.second[turn];
                result = new Pair<>(pair.first, value);
                break;
            }
        }
        if (result == null) {
            result = new Pair<>("" + len, 0);
        }
        return result;
    }

    @Override
    public String toString() {
        return Arrays.toString(allBids);
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

    public static class PlayerBid {
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