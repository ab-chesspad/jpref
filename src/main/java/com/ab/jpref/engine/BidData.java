package com.ab.jpref.engine;
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
 * Created: 9/3/25
 */

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.util.Logger;
import com.ab.util.Pair;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class BidData {
    public static boolean DEBUG_LOG = false;

    static private final Map<String, BidData> allBidData = new HashMap<>();
    static private final List<Pair<String, Integer>> tricks = new ArrayList<>();
    static {
        loadData();
    }

    public static class HandComparator implements Comparator<String> {
        // descending order
        @Override
        public int compare(String s1, String s2) {
            return s2.compareTo(s1);
        }
    }

    private static void loadData() {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try (InputStream is = classloader.getResourceAsStream("jpref/utyatsky-12");
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(" -> ");
                String key = parts[0];
                String[] bidParts = parts[1].substring(1, parts[1].length() - 1).split(", |\\[|\\]");
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
                allBidData.put(key, bidData);
            }
        } catch (Exception e) {
            Logger.println(e.getMessage());
        }

        try (InputStream is = classloader.getResourceAsStream("jpref/tricks");
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                Logger.println(DEBUG_LOG, line);
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                tricks.add(new Pair(parts[0], Integer.parseInt(parts[1])));
            }
        } catch (Exception e) {
            Logger.println(e.getMessage());
        }
    }

    public final OneBid[] allBids = new OneBid[6];

    public BidData() {}

    OneBid set(OneBid oneBid, int hand, boolean withBidding) {
        int index = 0;
        if (withBidding) {
            index = GameManager.NUMBER_OF_PLAYERS;
        }
        index += hand;
        OneBid old = allBids[index];
        allBids[index] = oneBid;
        return old;
    }

    public static Player.PlayerBid getBid(CardSet hand, Config.Bid minBid, int elderHand) {
        List<Integer> added = new ArrayList<>();
        List<Pair<String, Integer>> pairs = new ArrayList<>();

        // 1. get pairs for each suit separately
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
            Pair<String, Integer> pair = searchTricks(chunk);
            // append suit code to be able to locate real suits after sorting
            chunk = pair.first + cardSet.first().getSuit().toString();
            pair.first = chunk;
            pairs.add(pair);
        }
        // sort in descending order
        Collections.sort(pairs, new Comparator<Pair<String, Integer>>() {
            @Override
            public int compare(Pair<String, Integer> p0, Pair<String, Integer> p1) {
                return p1.first.substring(0, p1.first.length() - 1).compareTo(p0.first.substring(0, p0.first.length() - 1));
            }
        });

        // 2. fill hand up to 12 cards
        int fillIn = 12 - hand.size() + 1;
/*
        if ("♠7K ♣XJQKA ♥8XQK".equals(hand.toString())) {
            fillIn = fillIn;
        }
*/
        while (--fillIn > 0) {
            // insert fictitious entries keeping the sorting order
            if (pairs.size() < 4) {
                added.add(pairs.size());
                pairs.add(new Pair<>("1♦", 0));
                continue;
            }
            for (int i = pairs.size() - 1; i > 0; --i) {
                Pair<String, Integer> pair = pairs.get(i);
                String chunk = pair.first;
                int cur = Integer.parseInt(chunk.substring(0, 1));
                int prev = Integer.parseInt(pairs.get(i - 1).first.substring(0, 1));
                if (cur < prev) {
                    chunk = (cur + 1) + chunk.substring(1);
                    pair.first = chunk;
                    added.add(i);
                    break;
                }
            }
        }

        // search allBidData:
        StringBuilder sb = new StringBuilder();
        int totalTricks = 0;    // compute just in case
        int maxSuitSize = 0;
        for (Pair<String, Integer> pair : pairs) {
            String chunk = pair.first;
            int len = chunk.length() - 1;
            sb.append(chunk.substring(0, len));
            totalTricks += pair.second;
            int suitLen = Integer.parseInt(chunk.substring(0, 1));
            if (maxSuitSize < suitLen) {
                maxSuitSize = suitLen;
            }
        }
        String key = new String(sb);
        BidData bidData = allBidData.get(key);

        OneBid oneBid;
        if (bidData == null) {
            // not found, use from suit lists
            int suitNum = 4;
            if (maxSuitSize >= 4) {
                suitNum = 0;
            }
            int value = totalTricks * 10 + suitNum;
            // now drops
            int[] drops = new int[2];
            int dropIndex = -1;
            int need2drop = 2;
            while (--need2drop >= 0) {
                int i;
                if (++dropIndex < added.size()) {
                    drops[dropIndex] = added.get(dropIndex);
                    i = added.get(dropIndex);
                } else {
                    i = pairs.size() - 1;
                    for (; i >= 0; --i) {
                        String chunk = pairs.get(i).first;
                        int len = Integer.parseInt(chunk.substring(0, 1));
                        if (len > chunk.length() - 2) {
                            break;
                        }
                    }
                    drops[dropIndex] = i;
                }
                String chunk = pairs.get(i).first;
                int len = Integer.parseInt(chunk.substring(0, 1));
                chunk = (len - 1) + chunk.substring(1);
                pairs.get(i).first = chunk;
            }
            oneBid = new OneBid(value, drops);
        } else {
            int index = elderHand;
            if (!minBid.equals(Config.Bid.BID_6S)) {
                index += GameManager.NUMBER_OF_PLAYERS;
            }
            oneBid = bidData.allBids[index];
        }
        int value = toBidValue(pairs, oneBid.bid);
        Player.PlayerBid playerBid = new Player.PlayerBid(value);
        if (added.size() == 0) {
            CardSet handCopy = hand.clone();
            for (int i = 0; i < oneBid.drops.length; ++i) {
                String handChunk = pairs.get(oneBid.drops[i]).first;
                Card.Suit suit = getSuit(handChunk);
                Card drop = handCopy.list(suit).first();
                playerBid.drops.add(drop);
                handCopy.remove(drop);
            }
        }
        return playerBid;
    }

    static Card.Suit getSuit(String handChunk) {
        return Card.Suit.fromCode(handChunk.charAt(handChunk.length() - 1));
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

    private static Pair<String, Integer> searchTricks(String chunk) {
        Pair<String, Integer> result = null;
        int len = chunk.length();
        chunk = len + chunk;
        for (Pair<String, Integer> pair : tricks) {
            String tableChunk = pair.first;
/*
if ("5E".equals(tableChunk)) {
    DEBUG_LOG = DEBUG_LOG;
}
*/
            int tableLen = Integer.parseInt(tableChunk.substring(0, 1));
            if (tableLen > len) {
                continue;
            }
            if (tableLen < len) {
                break;
            }
            int res = tableChunk.compareTo(chunk.substring(0, tableChunk.length()));
            if (res < 0) {
                break;
            }
            if (res == 0) {
                result = pair.clone();
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
//        final Card.Suit[] drop = new Card.Suit[2];
//        final Config.Bid bid;
        final int[] drops = new int[2];
        final int bid;

/*
        public OneBid(Config.Bid bid, Card.Suit... drop) {
            System.arraycopy(drop, 0, this.drop, 0, drop.length);
            this.bid = bid;
        }
*/
        public OneBid(int bid, int... drops) {
            System.arraycopy(drops, 0, this.drops, 0, drops.length);
            this.bid = bid;
        }

        @Override
        public String toString() {
            return Arrays.toString(drops) + ", " + bid;
        }
    }
}
