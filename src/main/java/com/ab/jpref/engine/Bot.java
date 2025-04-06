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
 * Created: 12/22/2024.
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Config;
import com.ab.util.Logger;

import java.util.*;

public class Bot extends Player {
    public static boolean DEBUG = false;
    protected final int number;

    public Bot(String name, int number) {
        super(name);
        this.number = number;
    }

    public Bot(String name, Collection<Card> cards) {
        super(name, cards);
        number = 0;
    }

    @Override
    public void declareRound(Config.Bid minBid, boolean elderHand) {
        SuitResults suitResults = null;
        if (Config.Bid.BID_MISERE.equals(minBid)) {
            suitResults = discardForMisere(elderHand);
        }
        // todo

        discard(suitResults.discarded);
    }

    private Card discardAny() {
        int maxProblemTricks = 0;
        CardList problemSuit = null;
        Card res = null;

        for (int i = 0; i < mySuits.length; ++i) {
            CardList suit = mySuits[i];
            if (suit.isEmpty()) {
                continue;
            }
            res = suit.get(suit.size() - 1);
            CardList.ListData listData = suit.getListData(leftSuits[i], rightSuits[i]);
            if (listData.ok1stMove) {
                // no problems
                continue;
            } else {
                // problems
                // quick and dirty:
                if (maxProblemTricks < listData.minMeStart) {
                    maxProblemTricks = listData.minMeStart;
                    problemSuit = suit;
                }
            }
        }

        if (problemSuit != null) {
            res = problemSuit.get(problemSuit.size() - 1);
        }
        return res;
    }

    @Override
    public Card play(Trick trick) {
        // todo
        return playAllPass(trick);
    }

    Card playAllPass1stHand() {
        Card problems = null;
        Card anycard = null;
        int maxNoProblemTricks = -1;
        CardList noProblemSuit = null;

        // ♣8JQK ♦89 ♥79   ♠A ♣79 ♦XJKA ♥8   ♠J ♣XA ♦7 ♥XJQA 1 - must: ♠A, ♦X and ♦XJ?
        for (int i = 0; i < this.mySuits.length; ++i) {
            CardList suit = this.mySuits[i];
            if (suit.isEmpty()) {
                continue;
            }
            anycard = suit.get(suit.size() - 1);
            int suitNum = suit.get(0).getSuit().getValue();
            // не надо 'козырять'
            if (leftSuits[suitNum].isEmpty() && rightSuits[suitNum].isEmpty()) {
                continue;
            }
            CardList.ListData listData = suit.getListData(leftSuits[i], rightSuits[i]);
            if (listData.ok1stMove) {
                // no problems
                if (maxNoProblemTricks < listData.minMeStart) {
                    maxNoProblemTricks = listData.minMeStart;
                    noProblemSuit = suit;
                }
            } else {
                // problems
                if (suit.size() > 1) {
                    if (listData.minMeStart > listData.minTheyStart) {
                        problems = suit.get(0);     // give them a chance to take it
                    } else {
                        problems = suit.get(1);     // ?? second from bottom
                    }
                } else {
                    problems = suit.get(0);
                }
            }
        }

        Card res;
        if (noProblemSuit != null) {
            // further check?
            res = noProblemSuit.get(noProblemSuit.size() - 1);
        } else if (problems != null) {
            res = problems;
        } else {
            res = anycard;
        }
        return res;
    }

    // https://review-pref.ru/school/147/111/
    // all-pass: https://review-pref.ru/school/145/17/
    public Card playAllPass(Trick trick) {
        Card res;
        if (trick.startingSuit == null) {
            return playAllPass1stHand();
        }

        int suitNum = trick.startingSuit.getValue();
        CardList suit = this.mySuits[suitNum];
        if (suit.isEmpty()) {
            return discardAny();
        }

        Card myMin = suit.get(0);
        if (suit.size() == 1) {
            return myMin;
        }

        Card myMax = suit.get(suit.size() - 1);
        Card topTrickCard = trick.topCard;

        // todo
        // 2nd  and 3rd hand
        CardList.ListData listData = suit.getListData(leftSuits[suitNum], rightSuits[suitNum]);
        if (myMin.compareInTrick(topTrickCard) > 0) {
            Card leftMin = null;
            if (!listData.leftSuit.isEmpty()) {
                leftMin = listData.leftSuit.get(0);
            }
            Card rightMin = null;
            if (!listData.rightSuit.isEmpty()) {
                rightMin = listData.rightSuit.get(0);
            }
            if (myMin.compareInTrick(leftMin) > 0 && myMin.compareInTrick(rightMin) > 0) {
                res = myMax;
            } else {
                res = myMin;    // give an opponent a chance to take the trick
            }
        } else {
            res = suit.getMaxLessThan(topTrickCard);
        }

        return res;
    }

    @Override
    public Config.Bid getBid(Config.Bid minBid, boolean meStart) {
        Config.Bid bid = getMaxBid(meStart);
        if (bid.compareTo(minBid) >= 0) {
            if (!Config.Bid.BID_MISERE.equals(bid)) {
                bid = minBid;
            }
        } else {
            bid = Config.Bid.BID_PASS;
        }
        this.bid = bid;
        return bid;
    }

    protected Config.Bid getMaxBid(boolean meStart) {
        boolean _meStart = true;     // game declarer mostly starts tricks
        Card.Suit longestSuit = Card.Suit.NO_SUIT;    // no trump
        int maxLen = 0;
        int totalTricks = 0;
        for (CardList suit : mySuits) {
            if (suit.isEmpty()) {
                continue;
            }
            int suitNum = suit.get(0).getSuit().getValue();
            CardList.ListData listData = new CardList.ListData();
            listData.suit = suit.get(0).getSuit();
            if (_meStart) {
                listData.maxTheyStart = 0;
                listData.minTheyStart = 0;
            } else {
                listData.maxMeStart = 0;
                listData.minMeStart = 0;
            }
            suit.calcMaxTricks(listData, leftSuits[suitNum], rightSuits[suitNum]);
            totalTricks += listData.maxMeStart + listData.maxTheyStart;
            if (suit.size() > 3 && maxLen < suit.size()) {
                maxLen = suit.size();
                longestSuit = suit.get(0).getSuit();
            }
        }
        Config.Bid bid = null;
        if (totalTricks < 10) {
            if (evalMisere(meStart)) {
                bid = Config.Bid.BID_MISERE;
            } else if (totalTricks < 6) {
                // todo: compare all-pass loss vs minimal bid
                bid = Config.Bid.BID_PASS;
            }
        }
        if (bid == null) {
            // convert totalTricks and longestSuit to bid
            bid = Config.Bid.fromName("" + totalTricks + longestSuit.getUnicode());
        }
        return bid;
    }

    boolean evalMisere(boolean meStart) {
        int minMeStart = 0, minTheyStart = 0;
        CardList badSuit = null;
        for (CardList suit : mySuits) {
            if (suit.isEmpty()) {
                continue;
            }
            int suitNum = suit.get(0).getSuit().getValue();
            CardList.ListData listData = new CardList.ListData();
            listData.suit = suit.get(0).getSuit();
            suit.calcMinTricks(listData, leftSuits[suitNum], rightSuits[suitNum]);   // me start
            suit.calcMinTricks(listData, leftSuits[suitNum], rightSuits[suitNum]);   // they start
            minMeStart += listData.minMeStart;
            minTheyStart += listData.minTheyStart;
            if (meStart && listData.minMeStart == 0) {
                meStart = false;
            }
            if (minTheyStart > 0) {
                badSuit = suit;
            }
        }

        if (badSuit == null) {
            // let's go for it even if we have no good starting move
            return true;
        }

        // applying 'the rule of 7 cards'
        // https://gambiter.ru/pref/mizer-preferans.html
        // brute force
        Set<Card> myHand = new HashSet<>();
        Set<Card> talonCandidates = new HashSet<>(CardList.getDeck());
        for (CardList suit : mySuits) {
            myHand.addAll(suit);
            for (Card card : suit) {
                talonCandidates.remove(card);
            }
        }

        Logger.printf(DEBUG,"good from talon:\n");
        int goodCards = 0;
        for (Card card : talonCandidates) {
            Set<Card> probeHand = new HashSet<>(myHand);
            probeHand.add(card);
            for (Card c : myHand) {
                probeHand.remove(c);
                SuitResults suitResults = new Bot("probe" + card + c, probeHand).misereTricks(meStart);
                if (suitResults.totalTricks == 0) {
                    ++goodCards;
                    Logger.printf(DEBUG, "talon %s, discard %s\n", card, c);
                    if (goodCards >= 7) {
                        return true;
                    }
                    break;
                }
                probeHand.add(c);
            }
        }
        return false;
    }

    SuitResults misereTricks(boolean meStart) {
        SuitResults suitResults = new SuitResults();
        int maxTricks = 0;
        int eval = 0;
        for (CardList suit : mySuits) {
            if (suit.isEmpty()) {
                continue;
            }
            int suitNum = suit.get(0).getSuit().getValue();
            CardList.ListData listData = new CardList.ListData();
            listData.suit = suit.get(0).getSuit();
            if (meStart) {
                listData.minTheyStart = 0;
            } else {
                listData.minMeStart = 0;
            }
            suit.calcMinTricks(listData, leftSuits[suitNum], rightSuits[suitNum]);
            int tricks = listData.minMeStart + listData.minTheyStart;
            if (maxTricks < tricks || eval < listData.misereEval) {
                eval = listData.misereEval;
                suitResults.listData = listData;
                maxTricks = tricks;
            }
            suitResults.totalTricks += tricks;
        }
        return suitResults;
    }

    // select 2 cards to discard to minimize tricks and their probability
    SuitResults discardForMisere(boolean elderHand) {
        // optimistically we assume that there will be a way to turn elderHand to false
        boolean _elderHand = false;
        Set<Card> myHand = new HashSet<>();
        for (CardList suit : mySuits) {
            myHand.addAll(suit);
        }

        SuitResults suitResults = new SuitResults();
        Set<Card> probeSet1 = new HashSet<>(myHand);
probes:
        for (Card card1 : myHand) {
            probeSet1.remove(card1);
            for (Card card2 : probeSet1) {
                Set<Card> probeHand = new HashSet<>(myHand);
                probeHand.remove(card1);
                probeHand.remove(card2);
                Bot bot = new Bot("test"+card1+card2, probeHand);
                Logger.printf(DEBUG, "probe discard %s, %s, bot %s\n",
                    card1, card2, bot.toString());
                SuitResults _suitResults = bot.misereTricks(_elderHand);
                boolean refresh;
                int eval = 0;
                if (_suitResults.totalTricks == 0) {
                    refresh = true;
                } else {
                    eval = _suitResults.listData.misereEval;
                    refresh = suitResults.eval > eval;
                }
                if (refresh) {
                    suitResults = _suitResults;
                    suitResults.eval = 0;
                    if (suitResults.listData != null) {
                        suitResults.eval = suitResults.listData.misereEval;
                    }
                    suitResults.discarded.clear();
                    suitResults.discarded.add(card1);
                    suitResults.discarded.add(card2);
                    Logger.printf(DEBUG, "discard %s, %s, tricks %d, eval=%d\n",
                        card1, card2, suitResults.totalTricks, eval);
                    if (suitResults.totalTricks == 0) {
                        break probes;
                    }
                }
            }
        }
        return suitResults;
    }

    static class SuitResults {
        CardList discarded = new CardList();
        int totalTricks = 0;    // the best
        // the worst:
        int eval = Integer.MAX_VALUE;
        CardList.ListData listData;
    }
}