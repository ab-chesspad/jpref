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
import com.ab.jpref.cards.Card.Suit;
import com.ab.jpref.cards.Card.Rank;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Config;
import com.ab.jpref.config.Config.Bid;
import com.ab.jpref.engine.GameManager.Trick;

import java.util.*;

public class Bot extends Player{

    public Bot(String name) {
        super(name);
    }

    public Bot(String name, Collection<Card> cards) {
        super(name, cards);
    }

    // consider single 8 or 7-10 or 7-9-Q or 7-9-J-A as 'small holes'
    // also a singleton on the 1st turn
    private int holesForMisere(CardList[] mySuits, int turn) {
        int totalHoles = 0;
        boolean ok1stMove = turn != 0;
        for (CardList suit : mySuits) {
            CardList.ListData listData = suit.getListData(new HashSet<>());
            int _holes = listData.holes;
            if (_holes > 0 && suit.size() == 1 && turn == 0) {
                ++totalHoles;       // singleton
                ok1stMove = true;   // this will be the 1st move
            } else {
                totalHoles += _holes;
            }
            if (listData.ok1stMove) {
                ok1stMove = true;
            }
        }
        if (!ok1stMove) {
            ++totalHoles;
        }
        return totalHoles;
    }

    @Override
    public Config.Bid getBid(Config.Bid minBid, int turn) {
//        Config.Bid bid = getMaxBid(turn, Bid.BID_UNDEFINED, Bid.BID_UNDEFINED, minBid);
        Config.Bid bid = getMaxBid(turn);
        if (bid.compareTo(minBid) >= 0) {
            if (!Bid.BID_MISERE.equals(bid)) {
                bid = minBid;
            }
        } else {
            bid = Bid.BID_PASS;
        }
        this.bid = bid;
        return bid;
    }

//    protected Bid getMaxBid(int turn, Config.Bid leftBid, Config.Bid rightBid, Config.Bid minBid) {
    protected Bid getMaxBid(int turn) {
/*
        bid = Config.Bid.BID_PASS;
/*/ // for the future
        Suit longestSuit = Suit.NO_SUIT;    // no trump
        int maxLen = 0;
        int totalTricks = 0;
        for (CardList suit : mySuits) {
            if (suit.isEmpty()) {
                continue;
            }
            totalTricks += suit.getMaxTricks(GameManager.getState().discarded);
            if (suit.size() > 3 && maxLen < suit.size()) {
                maxLen = suit.size();
                longestSuit = suit.get(0).getSuit();
            }
        }
        Bid bid = null;
        if (totalTricks < 10) {
            if (holesForMisere(this.mySuits, turn) <= 1) {     // m.b 2 is ok?
                bid = Bid.BID_MISERE;
            } else if (totalTricks < 6) {
                // todo: compare all-pass loss vs minimal bid
                bid = Bid.BID_PASS;
            }
        }
        if (bid == null) {
            // convert totalTricks and longestSuit to bid
            bid = Bid.fromName("" + totalTricks + longestSuit.getUnicode());
        }
//*/
        return bid;
    }

    @Override
//    public RoundData declareRound(Config.Bid minBid, int turn, Config.Bid leftBid, Config.Bid rightBid) {
    public RoundData declareRound(Config.Bid minBid, int turn) {
        if (minBid == Bid.BID_MISERE) {
//            return declareMisere(turn, leftBid, rightBid, minBid);
            return declareMisere(turn);
        }
        List<RoundData> allRes = new LinkedList<>();
        allRes.add(new RoundData(Bid.BID_PASS, null));
        Bid maxRes = minBid;
        for (int s = 0; s < this.mySuits.length; ++s) {
            CardList suit = this.mySuits[s];
            for (int i : new int[]{0, 1}) {
                if (i >= suit.size()) {
                    break;
                }
                for (int s1 = s; s1 < this.mySuits.length; ++s1) {
                    CardList suit1 = this.mySuits[s1];
                    for (int k : new int[]{0, 1}) {
                        if (s1 == s) {
                             if (i >= k) {
                                 continue;
                             }
                        }
                        if (k >= suit1.size()) {
                            continue;
                        }

                        CardList[] mySuits = CardList.clone(this.mySuits);
//                        CardList[] theirSuits = CardList.clone(this.theirSuits);
                        Set<Card> discarded = new HashSet<>();
                        Card c2 = mySuits[s1].remove(k);
                        Card c1 = mySuits[s].remove(i);
                        discarded.add(c1);
                        discarded.add(c2);
                        Bid bid = getMaxBid(turn);
//                        Bid bid = getMaxBid(mySuits, discarded, 1, leftBid, rightBid, minBid);
                        if (maxRes.compareTo(bid) < 0) {
                            maxRes = bid;
                            allRes.clear();
                            allRes.add(new RoundData(bid, discarded));
//                            System.out.printf("discard %s, %s -> %s\n", c1, c2, bid);
                        } else if (maxRes.compareTo(bid) == 0) {
                            allRes.add(new RoundData(bid, discarded));
//                            System.out.printf("discard %s, %s -> %s\n", c1, c2, bid);
                        }
                    }
                }
            }
        }

        if (allRes.size() > 1) {
            // https://review-pref.ru/literatura/75/181/
            // https://review-pref.ru/literatura/52/218/
            // todo: find good discard option!
        }
        return allRes.get(0);
    }

//    private RoundData declareMisere(int turn, Config.Bid leftBid, Config.Bid rightBid, Config.Bid minBid) {
    private RoundData declareMisere(int turn) {
        List<RoundData> allRes = new LinkedList<>();
        int minHoles = 99;
        for (int s = 0; s < this.mySuits.length; ++s) {
            CardList suit = this.mySuits[s];
            for (int ii : new int[]{0, 1}) {
                int i = suit.size() - 1 - ii;
                if (i < 0) {
                    break;
                }
                for (int s1 = s; s1 < this.mySuits.length; ++s1) {
                    CardList suit1 = this.mySuits[s1];
                    for (int kk : new int[]{0, 1}) {
                        int k = suit1.size() - 1 - kk;
                        if (s1 == s) {
                            if (i <= k) {
                                continue;
                            }
                        }
                        if (k < 0) {
                            continue;
                        }
                        CardList[] mySuits = CardList.clone(this.mySuits.clone());
//                        CardList[] theirSuits = CardList.clone(this.theirSuits.clone());
                        Set<Card> discarded = new HashSet<>();
                        Card c1 = mySuits[s].remove(i);
                        Card c2 = mySuits[s1].remove(k);
                        discarded.add(c1);
                        discarded.add(c2);
                        int total = holesForMisere(mySuits, turn);
                        if (minHoles > total) {
                            minHoles = total;
                            allRes.clear();
                            allRes.add(new RoundData(Bid.BID_MISERE, total, discarded));
//                            System.out.printf("discard %s, %s -> %d\n", c1, c2, total);
                        } else if (minHoles == total) {
                            allRes.add(new RoundData(Bid.BID_MISERE, total, discarded));
//                            System.out.printf("discard %s, %s -> %d\n", c1, c2, total);
                        }
                    }
                }
            }
        }

        if (allRes.size() > 1) {
            // todo: find good discard option!
        }

        return allRes.get(0);
    }

    // https://review-pref.ru/school/145/17/
    public Card discard(Set<Card> discarded) {
        final String doubles = "AK, AQ, AJ, A8, A7, K7, KQ, KJ, Q7, Q8, AX, KX, QX, QJ, JX, A9, K9, Q9, K8";
        // AK, AQ, AJ, A8, A7, K7, KQ, Q7, Q8, AX, KX, QX, QJ, JX, K9, Q9, K8, J8, J7, J9, X9, X7
        // 78, 79, 89, 810, КВ, Т9
        int index = Integer.MAX_VALUE;
        Card maxSinglet = null;
        Card maxDoublet = null;
        Card maxTriplet = null;
        int maxSize = 0;
        Card longestSuitMax = null;
        Card anyCard = null;
/* debug
        if (discarded.size() == 28) {
            anyCard = null;
        }
//*/
        for (CardList suit : mySuits) {
            if (suit.isEmpty()) {
                continue;
            }
            anyCard = suit.get(suit.size() - 1);
            if (suit.size() == 1) {
                Rank r = suit.get(0).getRank();
                if (maxSinglet == null || maxSinglet.getRank().compareTo(r) < 0) {
                    maxSinglet = suit.get(0);
                }
                continue;
            }
            if (suit.size() == 2) {
                int i = doubles.indexOf(suit.toRankString());
                if (i > 0 && i < index) {
                    index = i;
                    maxDoublet = suit.get(suit.size() - 1);
                }
                continue;
            }
            if (suit.size() == 3) {
                CardList.ListData listData = suit.getListData(discarded);
                if (!listData.ok1stMove) {
                    if (maxTriplet == null || maxTriplet.getRank().compareTo(suit.get(suit.size() - 1).getRank()) < 0) {
                        maxTriplet = suit.get(suit.size() - 1);
                    }
                }
            }
            if (maxSize < suit.size()) {
                maxSize = suit.size();
                longestSuitMax = suit.get(suit.size() - 1);
            }
        }
        Card res = null;
        if (maxSinglet != null && maxSinglet.getRank().compareTo(Rank.NINE) >= 0) {
            res = maxSinglet;
        }
        if (res == null && maxDoublet != null) {
            res = maxDoublet;
        }
        if (res == null && maxTriplet != null) {
            res = maxTriplet;
        }
        if (res == null && longestSuitMax != null) {
            res = longestSuitMax;
        }
        if (res == null) {
            res = anyCard;
        }
        return res;
    }

    @Override
    public Card play(Trick trick) {
        // todo
        return playAllPass(trick);
    }

    Card playAllPass1stHand(Set<Card> discarded) {
//        CardList.ListData[] allListData =  new CardList.ListData[this.mySuits.length];
        Card singlet = null;
        int maxSingletHoles = 0;
        Card ok1stBad = null;
        Card ok1stGood = null;
        Card problems = null;
        Card anycard = null;
//        int minDistanceToTop = Config.MAX_DISTANCE_TO_TOP;
        for (int i = 0; i < this.mySuits.length; ++i) {
            CardList suit = this.mySuits[i];
            theirSuits[i].removeAll(discarded);
            if (suit.isEmpty()) {
                continue;
            }
            anycard = suit.get(suit.size() - 1);
            if (theirSuits[i].isEmpty()) {
                continue;
            }
            CardList.ListData listData = suit.getListData(discarded);
            if (suit.size() == 1) {
                if (theirSuits[i].size() == 1) {
                    // in fact, sometimes we should take the last card in the suit
                    if (suit.get(0).compareInTrick((Card) theirSuits[i].toArray()[0]) > 0) {
                        continue;
                    }
                }
                // compare distanceToTop?
                if (maxSingletHoles <= listData.holes) {
                    maxSingletHoles = listData.holes;
                    singlet = suit.get(0);
                }
                continue;
            }
            if (listData.ok1stMove) {
                // no problems
                if (listData.good) {
                    // use 1st!
                    ok1stGood = suit.get(0);
                    // use the topmost
//                    if (ok1stGood == null && ok1stBad.getRank().compareTo(suit.get(suit.size() - 1).getRank()) < 0) {
//                        ok1stGood = suit.get(suit.size() - 1);
//                    }
//                    if (minDistanceToTop > listData.distanceToTop) {
//                        minDistanceToTop = listData.distanceToTop;
//                        ok1stGood = suit.get(suit.size() - 1);
//                    }
                } else {
                    // use the bottom
                    if (ok1stBad == null || ok1stBad.getRank().compareTo(suit.get(0).getRank()) > 0) {
                        ok1stBad = suit.get(0);
                    }
                }
            } else {
                // problems
                problems = suit.get(1);     // ?? second from bottom
            }
//            anycard = suit.get(suit.size() - 1);
        }

        Card res;
        if (singlet != null) {
            res = singlet;
        } else if (ok1stGood != null) {
            res = ok1stGood;
        } else if (ok1stBad != null) {
            res = ok1stBad;
        } else if (problems != null) {
            res = problems;
        } else {
            res = anycard;
        }
        return play(res, discarded);
    }

    // https://review-pref.ru/school/147/111/
    // all-pass: https://review-pref.ru/school/145/17/
    public Card playAllPass(Trick trick) {
//        int turn;
/*
        if (leftCard != null) {
            theirSuits[leftCard.getSuit().ordinal()].remove(leftCard);
        }
        if (rightCard != null) {
            theirSuits[rightCard.getSuit().ordinal()].remove(rightCard);
        }
*/
        if (trick.rightCard == null && trick.startingSuit == null) {
//            turn = 0;
            return playAllPass1stHand(GameManager.getState().discarded);
        }
//        int suitNum;
//        Card res = new Card(Suit.SPADE, Rank.SIX);
        Card res;
        Card topTrickCard;
//        int maxIntercepts = 0;
        if (trick.leftCard != null) {
            // 3rd hand
//            suitNum = leftCard.getSuit().ordinal();
            topTrickCard = trick.leftCard;
            if (!trick.leftCard.getSuit().equals(trick.startingSuit) || trick.rightCard.compareInTrick(trick.leftCard) > 1) {
                topTrickCard = trick.rightCard;
            }
//            turn = 2;
        } else {
            // 2nd hand or 1st on the first 2 tricks
//            suitNum = rightCard.getSuit().ordinal();
//            if (rightCard.getRank().equals(Rank.SIX)) {
//                turn = 0;
//            } else {
//                turn = 1;
//            }
            if (trick.rightCard != null && trick.startingSuit.equals(trick.rightCard.getSuit())) {
                topTrickCard = trick.rightCard;
            } else {
                topTrickCard = new Card(trick.startingSuit, Rank.SIX);
            }
        }
        // 2nd and 3rd hand:
        int suitNum = trick.startingSuit.ordinal();
/* debug
        if (leftCard != null && leftCard.toString().equals("♣A")) {
            suitNum = startSuit.ordinal();
        }
*/
        CardList suit = this.mySuits[suitNum];
        theirSuits[suitNum].removeAll(GameManager.getState().discarded);
        if (suit.size() == 1) {
            return play(suit.get(0), GameManager.getState().discarded);
        }
        if (suit.isEmpty()) {
            res = discard(GameManager.getState().discarded);
            return play(res, GameManager.getState().discarded);
        }
        if (theirSuits[suitNum].size() == 1) {
            // m.b. extend to other cases?
            Card myMin = suit.get(0);
            if (trick.leftCard != null) {
                // 3rd hand
                if (myMin.compareInTrick(trick.leftCard) > 0) {
                    return play(suit.get(suit.size() - 1), GameManager.getState().discarded);
                }
            }
            if (myMin.compareInTrick((Card)theirSuits[suitNum].toArray()[0]) < 0) {
                return play(suit.get(0), GameManager.getState().discarded);
            }
        }
//        CardList.ListData listData =  suit.getListData(GameManager.getState().discarded);
        if (suit.size() == 2) {
            Card myMin = suit.get(0);
            Card myMax = suit.get(suit.size() - 1);
            if (myMin.compareInTrick(topTrickCard) > 0) {
                return play(myMax, GameManager.getState().discarded);
            } else {
                Card c = suit.getMaxLessThan(topTrickCard);
                return play(c, GameManager.getState().discarded);
            }
        }
        // 3 or more cards in suit:
        Card myMin = suit.get(0);
        Card myMid = suit.get(1);   // my 2nd min card
        if (myMin.compareInTrick(topTrickCard) > 0) {
            return play(myMid, GameManager.getState().discarded);
        }
        Card card = suit.getMaxLessThan(topTrickCard);
        return play(card, GameManager.getState().discarded);



/*

        for (int s = firstSuitNum; s < lastSuitNum; ++s) {
            CardList suit = this.mySuits[s];
            if (suit.isEmpty()) {
                continue;
            }
            if (rightCard == null || rightCard.getRank().equals(Rank.SIX)) {
                // 1st or trick starts from talon
                res = suit.get(0);
            } else if (res.getRank().compareTo(suit.get(suit.size() - 1).getRank()) < 0) {
                res = suit.get(suit.size() - 1);
            }

*/
/*
            if (leftCard == null && rightCard == null) {
                // me start the trick
                if (!suitTricks[s].second.first && !suitTricks[s].second.second) {
                    // bad and not problematic (the same number of tricks regardless of who starts the trick)
                    // todo: ugly!
                    CardList theirs = this.mySuits[s].getTheirs(suit.get(0).getSuit(), discarded);
                    Card myMax = this.mySuits[s].get(this.mySuits[s].size() - 1);
                    if (theirs.size() == 0) {
                        // the whole suit is ours, if there are no other suits, we got паровоз
                        // todo: select a card anyway
                        continue;
                    }
                    // todo: find the topmost card among these bad and non-problematic suits
//                    int intercepts = 0;
//                    int theirMax = theirs.size() - 1;
//                    while (myMax.getRank().compareTo(theirs.get(theirMax).getRank()) < 0) {
//                    if (res.getRank().compareTo())
                }
            } else if (leftCard == null && rightCard != null) {
                // me playing 2nd
            } else {
                // me playing 3rd
            }
*/
        /*

        }
        CardList suit = this.mySuits[res.getSuit().ordinal()];
//        suit.remove(suit.size() - 1);
        suit.remove(res);       // fix it
        discarded.add(res);
        return res;
*/
    }



}