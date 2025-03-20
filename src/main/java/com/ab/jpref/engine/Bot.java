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
import com.ab.jpref.engine.Trick;

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
//            CardList.ListData listData = suit.getListData(new HashSet<>());
            CardList.ListData listData = null;
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

    public Card discard() {
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
            CardList.ListData listData = suit.getListData(leftSuits[i], rightSuits[i]);
            if (listData.ok1stMove) {
                // no problems
                if (maxNoProblemTricks < listData.minMeStart) {
                    int suitNum = suit.get(0).getSuit().getValue();
                    // не надо 'козырять'
                    if (leftSuits[suitNum].size() > 0 || rightSuits[suitNum].size() > 0) {
                        maxNoProblemTricks = listData.minMeStart;
                        noProblemSuit = suit;
                    }
                }
            } else {
                // problems
                if (suit.size() > 1) {
                    problems = suit.get(1);     // ?? second from bottom
                } else {
                    problems = suit.get(0);
                }
            }
        }

        Card res;
        if (noProblemSuit != null) {
            res = noProblemSuit.get(0);
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
            return discard();
        }

        Card myMin = suit.get(0);
        if (suit.size() == 1) {
            return myMin;
        }

        Card myMax = suit.get(suit.size() - 1);
        Card topTrickCard = trick.topCard;

        // todo
        // 2nd  and 3rd hand
        if (myMin.compareInTrick(topTrickCard) > 0) {
            res = myMin;    // give an opponent a chance to take the trick
        } else {
            res = suit.getMaxLessThan(topTrickCard);
        }

        return res;
    }



}