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
 * Complete with my own data
 */

package com.ab.util;

import com.ab.jpref.cards.CardList;
import static com.ab.jpref.config.Config.NOP;
import static com.ab.jpref.config.Config.Bid;
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

    private final Map<String, OneBid[]> allBidData = loadBidData("utyatsky-12");
    public final List<Pair<String, int[]>> tricks = loadTricks("tricks");
    final List<Card> added = new ArrayList<>();
    final OneBid[] allBids = new OneBid[6];

    private static class Holder {
        static final BidData instance = new BidData();
    }

    public static BidData getInstance() {
        return Holder.instance;
    }

    private BidData() {}

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
                OneBid[] allBids = new OneBid[6];
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
                        allBids[++i] = new BidData.OneBid(bid, drop0, drop1);
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

    private List<Pair<String, int[]>> loadTricks(String resourceName) {
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

    // get pairs for all suits
    public List<Pair<String, Integer>> toSuitChunks(CardSet hand, int turn) {
        List<Pair<String, Integer>> pairs = new ArrayList<>();
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
            String chunk = new String(sb);
            Pair<String, Integer> pair = searchTricks(chunk, turn);
            // append suit code to be able to locate real suits after sorting
            chunk = pair.first + suit.toString();
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

    // called with:
    // 11 cards to guess max bid
    // 12 cards to define drops and declare round
    public PlayerBid getBid(CardSet hand, Bid minBid, int elderHand, int nDrops) {
        PlayerBid playerBid;
        added.clear();
        List<Pair<String, Integer>> pairs = toSuitChunks(hand, elderHand);

        // fill hand up to 12 cards to use Utyatsky's table
        // using the least significant suits and cards to minimize the impact
        int handSize = hand.size();
        CardSet handCopy = new CardSet(hand);
        int add = 12 - handSize;
        while (--add >= 0) {
            if (pairs.size() < TOTAL_SUITS) {
                final Suit[] suits = {Suit.SPADE, Suit.CLUB, Suit.DIAMOND, Suit.HEART};
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
                    handCopy.add(card);
                    ++handSize;
                    if (add == 0) {
                        break;
                    }
                }
            } else {
                Card card;
                Suit suit = getSuit(pairs.get(TOTAL_SUITS - 1).first);
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
                added.add(card);
                handCopy.add(card);
                ++handSize;
            }
            pairs = toSuitChunks(handCopy, elderHand);
        }

        OneBid oneBid;
        // create key for allBidData, compute totalTricks preliminary
        StringBuilder sb = new StringBuilder();
        for (Pair<String, Integer> pair : pairs) {
            String chunk = pair.first;
            int len = chunk.length() - 1;
            sb.append(chunk, 0, len);
        }
        String key = new String(sb);
        OneBid[] allBids = allBidData.get(key);
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
                    String handChunk = pairs.get(oneBid.drops[i]).first;
                    Suit suit = getSuit(handChunk);
                    Card drop = handCopy.list(suit).first();
                    if (added.isEmpty() || added.contains(drop)) {
                        playerBid.drops.add(drop);
                        handCopy.remove(drop);
                        added.remove(drop);
                    }
                }
                if (playerBid.drops.size() < nDrops) {
                    PlayerBid playerBid1 = calcPlayerBid(handCopy, minBid, elderHand, nDrops - playerBid.drops.size());
                    playerBid.drops.add(playerBid1.drops);
                }
                return playerBid;
            }
        }
        // not found or overbidding, use suit lists
        playerBid = calcPlayerBid(handCopy, minBid, elderHand, nDrops);
        return playerBid;
    }

    // brute force, find the cards to drop for the max tricks
    private PlayerBid calcPlayerBid(CardSet hand, Bid minBid, int elderHand, int nDrops) {
        PlayerBid playerBid = new PlayerBid();
        List<Pair<String, Integer>> pairs = toSuitChunks(hand, elderHand);
        Suit trumpCandidate = getSuit(pairs.get(0).first);

        // create CardList sorted by suit lengths
        CardList handList0 = new CardList();
        for (int i = pairs.size() - 1; i >= 0; --i) {
            String handChunk = pairs.get(i).first;
            Suit suit = getSuit(handChunk);
            if (suit.equals(trumpCandidate)) {
                continue;   // skip
            }
            int bit0 = 0;
            int bitmap = hand.list(suit).getBitmap();
            while ((bit0 = CardSet.next(bitmap, bit0)) != 0) {
                Card card = Card.get(bit0);
                if (added.contains(card)) {
                    continue;  // skip
                }
                handList0.add(card);
            }
        }

        int maxTricks = -1;
        for (int i = 0; i < handList0.size(); ++i) {
            Card card0 = handList0.get(i);
            handList0.remove(i);
            hand.remove(card0);
            CardList handList1 = handList0;
            if (nDrops == 1) {
                handList1 = new CardList(card0);
            }
            for (Card card1 : handList1) {
                hand.remove(card1);

                int tricks = calcTricks(hand, elderHand);
                hand.add(card1);
                if (maxTricks < tricks) {
                    maxTricks = tricks;
                    playerBid.drops.clear();
                    playerBid.drops.add(card0);
                    playerBid.drops.add(card1);
                }
            }
            hand.add(card0);
            handList0.add(i, card0);
        }

        hand.remove(playerBid.drops);
        pairs = toSuitChunks(hand, elderHand);
        int tricks = calcTricks(hand, elderHand);
        hand.add(playerBid.drops);

        String chunk0 = pairs.get(0).first;
        int len0 = Integer.parseInt(chunk0.substring(0, 1));
        String chunk1 = pairs.get(1).first;
        int len1 = Integer.parseInt(chunk1.substring(0, 1));
        if (len0 == 4 && len1 == 4 && elderHand != 0) {
            // set 2nd best suit as trump
            chunk0 = pairs.get(1).first;
            chunk1 = pairs.get(0).first;
        }

        int bidSuitNum = 5; // no trump
        if (len0 > 3) {
            // the best suit must be long enough
            bidSuitNum = getSuit(chunk0).getValue() + 1;
        }
        int bidValue = tricks * 10 + bidSuitNum;

        int minValue = minBid.getValue();
        if (bidValue < minValue) {
            if (!added.isEmpty()) {
                // bidding
                playerBid.value = Bid.BID_PASS.getValue();
                return playerBid;
            }
            // declaring round, overbidding
            if (len1 >= 4 && len1 == len0) {    // 2nd best suit must be long enough
                int bidSuitNum1 = getSuit(chunk1).getValue() + 1;
                int _bidValue = tricks * 10 + bidSuitNum1;
                if (bidValue < _bidValue) {
                    bidValue = _bidValue;
                    bidSuitNum = bidSuitNum1;
                }
            }
        }

        if (bidValue < minValue) {
            int minBidSuitNum = minValue % 10;
            int minBidTricks = minValue / 10;
            if (bidSuitNum >= minBidSuitNum) {
                tricks = minBidTricks;
            } else {
                tricks = minBidTricks + 1;
            }
            bidValue = tricks * 10 + bidSuitNum;
        }
        playerBid.value = bidValue;
        return playerBid;
    }

    private int calcTricks(CardSet hand, int elderHand) {
        int tricks = 0;
        List<Pair<String, Integer>> pairs0 = toSuitChunks(hand, elderHand);
        for (Pair<String, Integer> pair : pairs0) {
            tricks += pair.second;
        }
        return tricks;
    }

    public Suit getSuit(String handChunk) {
        return Suit.fromCode(handChunk.charAt(handChunk.length() - 1));
    }

    private int toBidValue(List<Pair<String, Integer>> pairs, int value) {
        int tricks = value / 10;
        int suitNum = value % 10;
        if (suitNum != 4) {
            String handChunk = pairs.get(suitNum).first;
            suitNum = getSuit(handChunk).getValue();
        }
        return tricks * 10 + suitNum + 1;
    }

    private Pair<String, Integer> searchTricks(String chunk, int turn) {
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

    private static class OneBid {
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