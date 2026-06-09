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
 * Created: 9/4/2025
 */

package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.util.BidData;
import com.ab.util.BidData.PlayerBid;
import com.ab.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.ab.jpref.cards.Card.Suit;

public class ForTricksBot extends Bot {
    PlayerBid maxPlayerBid;

    public ForTricksBot(CardSet... hands) {
        super(hands);
    }

    public ForTricksBot(Player player) {
        super(player);
    }

    public PlayerBid getMaxPlayerBid(Config.Bid minBid, int elderHand) {
        if (maxPlayerBid != null) {
            return maxPlayerBid;
        }
        CardSet talonCandidates = myHand.complement();
        CardSet myHand = new CardSet(this.myHand);
        List<PlayerBid> playerBids = new ArrayList<>();

        int bit0 = 0;
        while ((bit0 = CardSet.next(talonCandidates.getBitmap(), bit0)) != 0) {
            Card card0 = Card.get(bit0);
            myHand.add(card0);
            // 11 cards
            PlayerBid playerBid = BidData.getBid(myHand, minBid, elderHand);
            playerBid.drops.add(card0);     // for testing
            playerBids.add(playerBid);
            myHand.remove(card0);
        }

        Collections.sort(playerBids, (b1, b2) -> b2.value - b1.value);
        int index = playerBids.size() - 1;
        // the same rule of 7 cards
        // https://gambiter.ru/pref/mizer-preferans.html
        if (index > 6) {
            index = 6;
        }
        maxPlayerBid = playerBids.get(index);
        return maxPlayerBid;
    }

    @Override
    public PlayerBid getDrop(int elderHand) {
        if (debugDrop != null) {
            PlayerBid playerBid = new PlayerBid(this.bid);
            playerBid.drops = new CardSet(debugDrop);
            return playerBid;
        }
        if (playerBid == null || !myHand.contains(playerBid.drops)) {
            playerBid = BidData.getBid(myHand, bid, elderHand);
        }
        this.bid = playerBid.toBid();
        return playerBid;
    }

    @Override
    public Card play(Trick trick) {
        Bot.trick = trick;

        if (trick.getNumber() == 0 && trick.isEmpty() &&
                trick.getTurn() == gameManager().declarerNumber) {
            return declarerPlay();
        } else {
            // create and use trick list
            if (trickList == null) {
                trickList = new TrickList(this, trick, myHand, leftHand, rightHand);
            }
            Card res = trickList.getCard(trick, myHand, leftHand, rightHand);
            if (res != null) {
                return res;
            }
            return this.anyCard(trick, true);
        }
    }

    static final int suitFlagTrump = 0x8;
    static final int suitFlagAllMine = 0x4;
    static final int suitFlagSecond = 0x2;
    static final int suitFlagMyTop = 0x1;

    public static class SuitInfo {
        String chunk;
        int length;
        Suit suit;
        int flags;
        CardSet cardSet;
        int minTricks;
        Card best;
    }

    // always 1st hand
    // returns sorted in ascending order
    List<SuitInfo> getAllSuitInfo() {
        List<SuitInfo> allSuitInfo = new ArrayList<>();
        List<Pair<String, Integer>> pairs = BidData.toSuitChunks(myHand, 0);
        for (Pair<String, Integer> pair : pairs) {
            SuitInfo suitInfo = new SuitInfo();
            allSuitInfo.add(suitInfo);
            suitInfo.suit = BidData.getSuit(pair.first);
            suitInfo.cardSet = myHand.list(suitInfo.suit);
            suitInfo.chunk = pair.first.substring(1, pair.first.length() - 1);
            suitInfo.length = suitInfo.cardSet.size();
            Pair<Integer, Card> p = suitInfo.cardSet.minWantedTricks(leftHand.list(suitInfo.suit), rightHand.list(suitInfo.suit));
            suitInfo.minTricks = p.first;
            suitInfo.best = p.second;
            if (suitInfo.suit.equals(this.bid.getTrump())) {
                suitInfo.flags |= suitFlagTrump;
            } else if (suitInfo.length >= 4) {
                suitInfo.flags |= suitFlagSecond;
            } else if (suitInfo.length == suitInfo.minTricks) {
                suitInfo.flags |= suitFlagAllMine;
            } else {
                Card theirMax = CardSet.max(leftHand.list(suitInfo.suit), rightHand.list(suitInfo.suit));
                if (suitInfo.cardSet.last().compareInTrick(theirMax) > 0) {
                    suitInfo.flags |= suitFlagMyTop;
                }
            }
        }
        Collections.sort(allSuitInfo, (s0, s1) -> {
            int res = s0.flags - s1.flags;
            if (res != 0) {
                return res;
            }
            res = s0.minTricks - s1.minTricks;
            if (res != 0) {
                return res;
            }
            return s0.length - s1.length;
        });
        return allSuitInfo;
    }

    // always 1st hand
    public Card declarerPlay() {
        List<SuitInfo> allSuitInfo = getAllSuitInfo();
        SuitInfo trumpSuitInfo = null;
        SuitInfo secondSuitInfo = null;
        SuitInfo allMineSuitInfo = null;
        SuitInfo myTopSuitInfo = null;
        SuitInfo needToPlaySuitInfo = null;

        for (SuitInfo suitInfo : allSuitInfo) {
            if ((suitInfo.flags & suitFlagTrump) != 0) {
                trumpSuitInfo = suitInfo;
            }
            if ((suitInfo.flags & suitFlagSecond) != 0) {
                secondSuitInfo = suitInfo;
            }
            if ((suitInfo.flags & suitFlagAllMine) != 0) {
                allMineSuitInfo = suitInfo;
            } else if ((suitInfo.flags & suitFlagTrump) == 0 && suitInfo.minTricks > 0) {
                needToPlaySuitInfo = suitInfo;
            }
            if ((suitInfo.flags & suitFlagMyTop) != 0) {
                myTopSuitInfo = suitInfo;
            }
        }

        if (trumpSuitInfo != null) {
            if (needToPlaySuitInfo != null) {
                int trumpDeficit = trumpSuitInfo.length - trumpSuitInfo.minTricks;
                int deficit = needToPlaySuitInfo.length - needToPlaySuitInfo.minTricks;
                if (trumpSuitInfo.minTricks > deficit) {
                    CardSet leftSet = leftHand.list(trumpSuitInfo.suit);
                    CardSet rightSet = rightHand.list(trumpSuitInfo.suit);
                    if (trumpSuitInfo.cardSet.last().compareInTrick(CardSet.max(leftSet, rightSet)) > 0) {
                        return trumpSuitInfo.cardSet.last();
                    }
                }
                if (deficit > 0) {
                    CardSet leftSet = leftHand.list(needToPlaySuitInfo.suit);
                    CardSet rightSet = rightHand.list(needToPlaySuitInfo.suit);
                    Card leftMax = leftSet.last();
                    Card rightMax = rightSet.last();
                    if (leftMax == null && !leftHand.list(trumpSuitInfo.suit).isEmpty()
                            || rightMax == null && !rightHand.list(trumpSuitInfo.suit).isEmpty()) {
                        return needToPlaySuitInfo.cardSet.first();
                    }
                    if (myHand.list(needToPlaySuitInfo.suit).last().compareInTrick(CardSet.max(leftSet, rightSet)) > 0) {
                        return needToPlaySuitInfo.cardSet.last();
                    }
                }
                return needToPlaySuitInfo.best;
            }
            Suit trumpSuit = trumpSuitInfo.suit;
            int theirMaxLen = Math.max(leftHand.list(trumpSuit).size(), rightHand.list(trumpSuit).size());
            if (theirMaxLen > 0) {
                if (trumpSuitInfo.minTricks == trumpSuitInfo.length ||
                        trumpSuitInfo.length > theirMaxLen && trumpSuitInfo.chunk.startsWith("ED")) {
                    return trumpSuitInfo.cardSet.last();
                }
            }
        }
        if (allMineSuitInfo != null) {
            return allMineSuitInfo.cardSet.last();
        }
        if (needToPlaySuitInfo != null) {
            return needToPlaySuitInfo.best;
        }
        if (myTopSuitInfo != null) {
            return myTopSuitInfo.cardSet.last();
        }
        return myHand.anyCard();
    }

    @Override
    long bm4Iteration(TrickList.TrickNode trickNode) {
        int num = trickNode.getTurn();
        int bitmap = trickNode.hands[num].getBitmap() & CardSet.suitMask(trickNode.startingSuit);
        if (bitmap == 0 && trickNode.trumpSuit != null) {
            bitmap = trickNode.hands[num].getBitmap() & CardSet.suitMask(trickNode.trumpSuit);
        }
        if (bitmap == 0) {
            bitmap = trickNode.hands[num].getBitmap();
        }

        int n1 = (num + 1) % NOP;
        int n2 = (num + 2) % NOP;
        int declarerNumber = 0; // when building TrickList
        if (num == declarerNumber) {
            // todo: refactor
            int others = trickNode.hands[n1].getBitmap() | trickNode.hands[n2].getBitmap();
            for (int i = 0; i < trickNode.size(); ++i) {
                Card card = trickNode.getCard(i);
                int bit = 1 << CardSet.offset(card);
                others |= bit;
            }
            return (long)CardSet.bm4buildForward(bitmap, others) & 0x0ffffffffL;
        }

        int friend = n1;
        int foe = n2;
        if (n1 == declarerNumber) {
            friend = n2;
            foe = n1;
        }
        int foeHand = trickNode.hands[foe].getBitmap();
        for (int i = 0; i < trickNode.size(); ++i) {
            Card card = trickNode.getCard(i);
            int bit = 1 << CardSet.offset(card);
            foeHand |= bit;
        }
        long res = (long)CardSet.bm4build(bitmap, trickNode.hands[friend].getBitmap(), foeHand) & 0x0ffffffffL;
        return res | BACKWARD_FLAG;
    }

    @Override
    protected int compare(long bestSoFarTrickData, long probeTrickData, int turn) {
        int num = (BaseTrick.getStartedBy(probeTrickData) + turn) % NOP;   // who played last

        int bestSoFarPastTricks = BaseTrick.getPastTricks(bestSoFarTrickData);
        int bestSoFarFutureTricks = BaseTrick.getFutureTricks(bestSoFarTrickData);
        int bestSoFarTricks = bestSoFarPastTricks + bestSoFarFutureTricks;

        int probePastTricks = BaseTrick.getPastTricks(probeTrickData);
        int probeFutureTricks = BaseTrick.getFutureTricks(probeTrickData);
        int probeTricks = probePastTricks + probeFutureTricks;

        int diff = 10 * (bestSoFarTricks - probeTricks);
        if (diff != 0) {
            if (num == 0) {
                return diff;
            }
            return -diff;
        }
        diff = bestSoFarPastTricks - probePastTricks;
        if (diff != 0) {
            if (num == 0) {
                return diff;
            }
            return -diff;
        }
        int _bestSoFarTop = BaseTrick.getTop(bestSoFarTrickData);
        int _probeTop = BaseTrick.getTop(probeTrickData);
        if (_bestSoFarTop == 0 && _probeTop != 0 ||
            _bestSoFarTop != 0 && _probeTop == 0) {
            return diff;
        } else {
            // todo
        }
        return diff;
    }
}