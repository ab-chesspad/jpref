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
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.util.Bidder;
import com.ab.util.Bidder.PlayerBid;
import com.ab.util.Logger;
import com.ab.util.Pair;
import static com.ab.jpref.cards.Card.Suit;
import static com.ab.jpref.config.Config.ROUND_SIZE;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ForTricksBot extends Bot {
    // MisereBot.DeclarerDrop.Random: one of the drops as good as the Bidder's, see Bidder.equalDrops()
    public static MisereBot.DeclarerDrop declarerDrop = MisereBot.DeclarerDrop.Random;
    PlayerBid maxPlayerBid;

    private static ForTricksBot instance;

    public static ForTricksBot getInstance() {
        if (instance == null) {
            instance = new ForTricksBot();
        }
        return instance;
    }

    public static ForTricksBot getInstance(Player player) {
        ForTricksBot instance = getInstance();
        instance.init(player);
        return instance;
    }

    public static ForTricksBot getInstance(CardSet... hands) {
        ForTricksBot instance = getInstance();
        instance.init(hands);
        return instance;
    }

    private ForTricksBot() {}

    // the singleton serves every bot and deal: init() clears the previous player's bid
    @Override
    public void clear() {
        super.clear();
        maxPlayerBid = null;
    }

    // for tests only
    public ForTricksBot(CardSet... hands) {
        init(hands);
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
            PlayerBid playerBid = Bidder.getInstance().getBid(myHand, minBid, elderHand, myHand.size() - ROUND_SIZE);
            playerBid.drops.clear();
            playerBid.drops.add(card0);     // for testing
            playerBids.add(playerBid);
            myHand.remove(card0);
        }

        Collections.sort(playerBids, (b1, b2) -> b2.value - b1.value);
        // the same rule of 7 cards
        // https://gambiter.ru/pref/mizer-preferans.html
        int index = 6;
        maxPlayerBid = playerBids.get(index);
        return maxPlayerBid;
    }

    @Override
    public PlayerBid getDrop(int elderHand, int nDrops, Trick trick) {
        if (debugDrop != null) {
            PlayerBid playerBid = new PlayerBid(this.bid);
            playerBid.drops = new CardSet(debugDrop);
            return playerBid;
        }
        Card playedCard = null;
        if (playerBid == null || !myHand.contains(playerBid.drops)) {
            if (trick != null && trick.getTurn() == (gameManager().declarerNumber + 1) % NOP &&
                    myHand.size() == 11) {
                playedCard = trick.getCard(trick.size() - 1);
            }
            myHand.add(playedCard);
        }
        playerBid = Bidder.getInstance().getBid(myHand, bid, elderHand, nDrops);
        if (declarerDrop == MisereBot.DeclarerDrop.Random && playedCard == null &&
                gameManager().getDeclarer() instanceof Bot) {
            // a bot declarer's own drop, one of the equal ones; the defenders guessing a human's drop take the Bidder's
            List<CardSet> drops = Bidder.getInstance().equalDrops(myHand, bid, elderHand, playerBid.drops);
            playerBid.drops = drops.get(myRand.nextInt(drops.size()));
        }
        myHand.remove(playedCard);
        this.bid = playerBid.toBid();
        return playerBid;
    }

    @Override
    public Card play(Trick trick) {
        if (trick.getNumber() == 0 && trick.isEmpty() &&
                trick.getTurn() == gameManager().declarerNumber) {
            return declarerPlay();
        } else {
            // create and use trick list
            Card res = TrickList.getInstance().getCard(this, trick);
            if (res != null) {
                return res;
            }
            return this.anyCard(trick, true);
        }
    }

    // the declarer does not drop trumps, as TrickList.rebuild() guesses
    @Override
    boolean droppable(Card card) {
        return !card.getSuit().equals(bid.getTrump());
    }

    // the declarer drops for the most tricks, the chances of tricks more break ties:
    // ♣79XJQKA ♦XQ ♥XQK drops ♦XQ, ♥X takes a trick when ♥J falls, ♦X or ♦Q never
    @Override
    int dropRisk(CardSet dealtHand) {
        CardSet dealt = CardSet.union(gameManager().declarerHand, gameManager().declarerPlayed);
        return (int)Math.round(-1000 * Bidder.getInstance().estimateTricks(dealtHand, dealt, bid.getTrump()));
    }

    // The defenders do not know the drop, the list is built for one guess of it, see Bidder;
    // the card it plays can be the worst one under the real drop: ♠XQ ♣JQ ♦789KA ♥K + ♥8 ♦Q, 6♦,
    // the guess ♥8K dropped lets the defenders discard ♥ on the ♦ leads, the declarer with ♥8K
    // takes 8 tricks, not 6. Each of various drops, see dropGuesses(), is searched with open cards;
    // a card is safe under a guess when no other card gives the declarer fewer tricks. The card
    // safe under the most likely guesses, the least dropRisk(), then under the most guesses,
    // then the fewest tricks under the list's guess, then under all the guesses, then the list's
    // card. The various guesses alone outvote the likely one: ♠89QA vs 7 no trump ♠XJK ♦J,
    // ♣8 ♥9 dropped, ♠A is safe under ♠XJ or ♠XK dropped, ♠8 gives the declarer a trick less
    @Override
    Card robustCard(Trick trick, Card listCard) {
        if (trick.getTurn() == gameManager().declarerNumber) {
            return listCard;
        }
        List<CardSet> guesses = dropGuesses(trick);
        if (guesses.size() < 2) {
            return listCard;
        }
        CardList candidates = candidates(trick);
        if (trick.getNumber() < SAME_RUN_TRICKS) {
            candidates = sameRun(trick, candidates, listCard, guesses.get(0));
        }
        if (candidates.size() < 2) {
            return listCard;
        }
        TrickList trickList = TrickList.getInstance();
        int n = candidates.size();
        int[] safe = new int[n];
        int[] likelySafe = new int[n];
        int[] totalTricks = new int[n];
        int[] listGuess = null;
        boolean[] likely = new boolean[guesses.size()];
        int minRisk = Integer.MAX_VALUE;
        int[] risks = new int[guesses.size()];
        for (int g = 0; g < guesses.size(); ++g) {
            CardSet dealtHand = new CardSet(guesses.get(g));
            dealtHand.add(gameManager().declarerPlayed);
            risks[g] = dropRisk(dealtHand);
            minRisk = Math.min(minRisk, risks[g]);
        }
        int remainingLikely = 0;
        for (int g = 0; g < guesses.size(); ++g) {
            likely[g] = risks[g] == minRisk;
            if (likely[g]) {
                ++remainingLikely;
            }
        }
        int complete = 0;
        long start = System.currentTimeMillis();
        int remaining = guesses.size();
        // in the order of dropGuesses(), within the move's deadline, set in TrickList.getCard()
        for (int g = 0; g < guesses.size(); ++g) {
            int[] res = null;
            if (g == 0) {
                res = trickList.builtResults(trick, guesses.get(g), candidates);    // searched by the list
            }
            if (res == null) {
                res = trickList.evalCards(trick, guesses.get(g), candidates);
            }
            if (res == null) {
                break;      // deadline, the last guesses are left out
            }
            if (complete++ == 0) {
                listGuess = res;
            }
            int min = Integer.MAX_VALUE;
            for (int i = 0; i < n; ++i) {
                min = Math.min(min, res[i]);
            }
            for (int i = 0; i < n; ++i) {
                if (res[i] == min) {
                    ++safe[i];
                    if (likely[g]) {
                        ++likelySafe[i];
                    }
                }
                totalTricks[i] += res[i];
            }
            --remaining;
            if (likely[g]) {
                --remainingLikely;
            }
            if (decided(likelySafe, remainingLikely)) {
                break;      // the rest cannot change the card
            }
        }
        if (complete == 0) {
            return listCard;
        }
        int listIndex = candidates.indexOf(listCard);
        int best = 0;
        for (int i = 1; i < n; ++i) {
            int diff = likelySafe[i] - likelySafe[best];
            if (diff == 0) {
                diff = safe[i] - safe[best];
            }
            if (diff == 0) {
                diff = listGuess[best] - listGuess[i];
            }
            if (diff == 0) {
                diff = totalTricks[best] - totalTricks[i];
            }
            if (diff == 0 && i == listIndex) {
                diff = 1;
            }
            if (diff > 0) {
                best = i;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; ++i) {
            sb.append(String.format(" %s %d/%d/%d", candidates.get(i), likelySafe[i], safe[i], listGuess[i]));
        }
        Logger.printf("trick %d, drops guessed %d, safe likely/all guesses, tricks for the list's guess:%s -> %s, %,d msec\n",
            trick.getNumber(), complete, sb, candidates.get(best), System.currentTimeMillis() - start);
        return candidates.get(best);
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
        List<Bidder.SuitTricks> pairs = Bidder.getInstance().toSuitChunks(myHand);
        for (Bidder.SuitTricks pair : pairs) {
            SuitInfo suitInfo = new SuitInfo();
            allSuitInfo.add(suitInfo);
            suitInfo.suit = pair.suit;
            suitInfo.cardSet = myHand.list(suitInfo.suit);
            suitInfo.chunk = pair.chunk.substring(1);
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

    // the suit that needs a move to get its tricks, when all the other suits stop the defenders
    private Card noTrumpMove(List<SuitInfo> allSuitInfo) {
        SuitInfo moveSuitInfo = null;
        for (SuitInfo suitInfo : allSuitInfo) {
            CardSet theirs = new CardSet(leftHand.list(suitInfo.suit), rightHand.list(suitInfo.suit));
            if (theirs.isEmpty() || suitInfo.cardSet.last().compareInTrick(theirs.last()) > 0) {
                continue;   // stopped
            }
            if (moveSuitInfo != null || suitInfo.minTricks == 0) {
                return null;    // more than one suit open, or nothing to win there
            }
            moveSuitInfo = suitInfo;
        }
        if (moveSuitInfo == null || moveSuitInfo.best == null) {
            return null;
        }
        // a missing suit cannot be stopped
        if (allSuitInfo.size() < Card.TOTAL_SUITS) {
            return null;
        }
        return moveSuitInfo.best;
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
        if (trumpSuitInfo == null) {
            // no trump: give up the lead while every other suit is still stopped,
            // cashing first leaves the defenders free to run their suit later
            Card move = noTrumpMove(allSuitInfo);
            if (move != null) {
                return move;
            }
            // a suit that needs moves: never start with a top card with no cushion, it is a stopper
            // deal: ♠8X ♣XJQ ♦XK ♥8QK  ♠7JA ♣8K ♦9JQA ♥A  ♠9QK ♣79 ♦7 ♥79XJ  ♣A ♦8  1 -> 1 8-
            // ♥A ♥7 ♥K -> 6, ♦A -> 8
            SuitInfo moveSuitInfo = null;
            for (SuitInfo suitInfo : allSuitInfo) {
                if (suitInfo.length > suitInfo.minTricks && suitInfo.minTricks > 0 &&
                        (moveSuitInfo == null || suitInfo.length > moveSuitInfo.length)) {
                    moveSuitInfo = suitInfo;
                }
            }
            if (moveSuitInfo != null && moveSuitInfo.best != null) {
                return moveSuitInfo.best;
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
        final int[] bitmaps = new int[NOP];
        int num = trickNode.getTurn();
        int bitmap = trickNode.hands[num].list(trickNode.startingSuit).getBitmap();
        if (bitmap == 0 && trickNode.trumpSuit != null) {
            bitmap = trickNode.hands[num].getBitmap() & CardSet.suitMask(trickNode.trumpSuit);
        }
        if (bitmap == 0) {
            bitmap = trickNode.hands[num].getBitmap();
        }

        bitmaps[0] = bitmap;
        bitmaps[1] = trickNode.hands[(num + 1) % NOP].getBitmap();
        bitmaps[2] = trickNode.hands[(num + 2) % NOP].getBitmap();
        for (int i = 0; i < trickNode.size(); ++i) {
            Card card = trickNode.getCard(i);
            int bit = 1 << CardSet.offset(card);
            int n = (i + 3 - trickNode.size()) % NOP;
            bitmaps[n] |= bit;
        }

        int declarerNumber = 0; // when building TrickList
        if (num == declarerNumber) {
            int others = bitmaps[1] | bitmaps[2];
            return ((long)CardSet.bm4buildBackward(bitmap, others) & 0x0ffffffffL | BACKWARD_FLAG);
        }

        int friend = 1;
        int foe = 2;
        if ((num + 1) % NOP == declarerNumber) {
            friend = 2;
            foe = 1;
        }
        int trumpBM = 0;
        Suit trump = trickNode.minBid.getTrump();
        if (trump != null) {
            trumpBM = CardSet.listBitMap(bitmaps[0], trump);
        }
        bitmaps[0] &= ~trumpBM;     // excluding trump
        // trump suit needs a special treatment, e.g.
        // deal: ♠8J ♣7KA ♦78A ♥7A  ♠9XK ♣9XQ ♦9Q ♥8Q  ♠7QA ♣8J ♦XJK ♥XK  ♥J9  1 -> 6♥ 6
        int res = CardSet.bm4build(bitmaps[0], bitmaps[friend], bitmaps[foe]) |
            CardSet.bm4buildForward(trumpBM, bitmaps[foe]);
        if (trickNode.size() > 0) {
            // following suit with the partner's cards between mine, the top one unblocks the suit:
            // ♥A led, ♥79XJ, ♥8QK: ♥7, ♥K keeps ♥8 to reach ♥XJ
            int suitBM = trickNode.hands[num].list(trickNode.startingSuit).getBitmap();
            if (suitBM != 0) {
                int low = suitBM & -suitBM;
                int top = CardSet.msb(suitBM);
                if ((bitmaps[friend] & (top - 1) & ~(low | (low - 1))) != 0) {
                    res |= top;
                }
            }
        }
        return ((long)res & 0x0ffffffffL) | BACKWARD_FLAG;
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
            // decide on total past + future tricks
            if (num == 0) {
                return diff;
            }
            return -diff;
        }
        diff = bestSoFarPastTricks - probePastTricks;
        if (diff != 0) {
            // decide on past tricks
            if (num == 0) {
                return diff;
            }
            return -diff;
        }
        int _bestSoFarTop = BaseTrick.getTop(bestSoFarTrickData);
        int _probeTop = BaseTrick.getTop(probeTrickData);
        // todo?
        if (_bestSoFarTop == 0 && _probeTop != 0 ||
                _bestSoFarTop != 0 && _probeTop == 0) {
            return diff;
        } else {
            // todo
            return diff;
        }
    }
}