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

import java.util.Collection;

public class Bot extends Player{

    public Bot(String name) {
        super(name);
    }

    public Bot(String name, Collection<Card> cards) {
        super(name, cards);
    }

    @Override
    public void declareRound(Config.Bid minBid, boolean elderHand) {

    }

    // consider single 8 or 7-10 or 7-9-Q or 7-9-J-A as 'small holes'
    // also a singleton on the 1st turn
    private int holesForMisere(CardList[] mySuits, boolean meStart) {
        int totalHoles = 0;
        boolean ok1stMove = !meStart;
        for (CardList suit : mySuits) {
//            CardList.ListData listData = suit.getListData(new HashSet<>());
            CardList.ListData listData = null;
            int _holes = listData.holes;
            if (_holes > 0 && suit.size() == 1 && meStart) {
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

/*
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
                            allRes.add(new RoundData(Bid.BID_MISERE));
//                            System.out.printf("discard %s, %s -> %d\n", c1, c2, total);
                        } else if (minHoles == total) {
                            allRes.add(new RoundData(Bid.BID_MISERE));
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
*/

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

/*
        // ♠XQ ♥89   ♠9JA ♦K   ♠78 ♣K ♥A
        // debug:
        if (tricks == 2) {
            noProblemSuit = null;
        }
//*/
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
//            res = noProblemSuit.get(0);
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
            if (listData.leftSuit.size() > 0) {
                leftMin = listData.leftSuit.get(0);
            }
            Card rightMin = null;
            if (listData.rightSuit.size() > 0) {
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
            if (!meStart) {
                listData.maxMeStart = 0;
            } else {
                listData.maxTheyStart = 0;
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
/*
            if (holesForMisere(this.mySuits, meStart) <= 1) {     // m.b 2 is ok?
                bid = Config.Bid.BID_MISERE;
            } else
*/
            if (totalTricks < 6) {
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

}