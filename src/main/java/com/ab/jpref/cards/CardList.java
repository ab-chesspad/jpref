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
 * Created: 12/22/2024
 */

package com.ab.jpref.cards;

import com.ab.jpref.config.Config;
import com.ab.util.Couple;
import com.ab.util.Pair;
import com.ab.jpref.cards.Card.Suit;

import java.util.ArrayList;
import java.util.Set;

public class CardList extends ArrayList<Card> {

    public static CardList getDeck() {
        CardList cardList = new CardList();
        for (Suit suit : Suit.values()) {
            if (suit == Suit.NO_SUIT) {
                continue;
            }
            for (Card.Rank rank : Card.Rank.values()) {
                if (rank.equals(Card.Rank.SIX)) {
                    continue;
                }
                cardList.add(new Card(suit, rank));
            }
        }
        return cardList;
    }

    public static CardList[] clone(CardList[] them) {
        CardList[] newOne = new CardList[them.length];
        for (int i = 0; i < them.length; ++i) {
            newOne[i] = (CardList) them[i].clone();
        }
        return newOne;
    }

    public String toRankString() {
        StringBuilder sb = new StringBuilder();
        for (int i = this.size() - 1; i >= 0; --i) {
            sb.append(this.get(i).getRank().toString());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Suit suit = null;
        String sep = "";
        for (Card c : this) {
            Suit s = c.getSuit();
            if (!s.equals(suit)) {
                suit = s;
                sb.append(sep).append(suit);
                sep = " ";
            }
            sb.append(c.getRank());
        }
        return sb.toString();
    }

    public int getMaxLessThan(Card.Rank other) {
        if (other == null) {
            return 0;
        }
        int res = this.size() - 1;
        for (int i = 0; i < this.size(); ++i) {
            Card.Rank r = this.get(i).getRank();
            if (r.compare(other) < 0) {
                res = i;
            } else {
                break;
            }
        }
        return res;
    }

    public Card getMaxLessThan(Card other) {
        Card res = this.get(0);
        for (int i = 1; i < this.size(); ++i) {
            Card c = this.get(i);
            if (c.compareInTrick(other) < 0) {
                res = c;
            } else {
                break;
            }
        }
        return res;
    }

    // clone lists and remove duplicates from rightSuit
    void simplifyHands(ListData listData, CardList leftSuit, CardList rightSuit) {
        listData.leftSuit = (CardList)leftSuit.clone();
        listData.rightSuit = new CardList();
        for (Card card : rightSuit) {
            if (!listData.leftSuit.contains(card)) {
                listData.rightSuit.add(card);
            }
        }
        listData.thisSuit = (CardList)this.clone();
    }

    void calcMinTricks(ListData listData, CardList leftSuit, CardList rightSuit) {
        int[] order;
        boolean calcMeStart;
        if (listData.minMeStart < 0) {
            order = new int[] {0,1,2};
            listData.minMeStart = 0;
            calcMeStart = true;
        } else {
            order = new int[] {1,2,0};
            listData.minTheyStart = 0;
            calcMeStart = false;
        }
        simplifyHands(listData, leftSuit, rightSuit);

        CardList[] cardLists = new CardList[3];
        cardLists[0] = listData.thisSuit;
        cardLists[1] = listData.leftSuit;
        cardLists[2] = listData.rightSuit;

        boolean first = true;
        while (listData.thisSuit.size() > 0) {
            int n = order[0];
            Card.Rank firstCard = null;
            if (cardLists[n].size() > 0) {
                firstCard = cardLists[n].get(0).getRank();
                cardLists[n].remove(0);
            }
            boolean myTrick = true;

            for (int i = 1; i < cardLists.length; ++i) {
                int k = order[i];
                if (cardLists[k].size() == 0) {
                    continue;
                }

                int j;
                if (calcMeStart) {
                    if (cardLists[k].get(0).getRank().compare(firstCard) < 0) {
                        j = cardLists[k].getMaxLessThan(firstCard);
                    } else {
                        myTrick = false;
                        j = cardLists[k].size() - 1;
                    }
                } else {
                    if (cardLists[k].get(0).getRank().compare(firstCard) < 0) {
                        if (k == 0) {
                            myTrick = false;
                        }
                        j = cardLists[k].getMaxLessThan(firstCard);
                    } else {
                        j = cardLists[k].size() - 1;
                    }
                }
                cardLists[k].remove(j);
            }

            if (calcMeStart) {
                if (myTrick) {
                    ++listData.minMeStart;
                } else if (first) {
                    listData.good = true;
                }
            } else {
                if (myTrick) {
                    ++listData.minTheyStart;
                }
            }
            first = false;
        }
        listData.ok1stMove = listData.minMeStart == listData.minTheyStart;
    }

    // todo!
//            while ((myMax = suit.size() - 1) >= 0 && (theirMax = theirs.size() - 1) >= 0) {
    void _calcMaxTricks(ListData listData, CardList _leftSuit, CardList _rightSuit) {
        int[] order = new int[3];
        boolean calcMeStart;
        if (listData.maxMeStart < 0) {
            order[0] = 0;
            order[1] = 1;
            order[2] = 2;
            listData.maxMeStart = 0;
            calcMeStart = true;
        } else {
            order[0] = 1;
            order[1] = 2;
            order[2] = 0;
            listData.maxTheyStart = 0;
            calcMeStart = false;
        }
        // clone lists and remove duplicates from rightSuit
        CardList leftSuit = (CardList)_leftSuit.clone();
        CardList rightSuit = new CardList();
        for (Card card : _rightSuit) {
            if (!leftSuit.contains(card)) {
                rightSuit.add(card);
            }
        }
        CardList thisSuit = (CardList)this.clone();

        CardList[] cardLists = new CardList[3];
        cardLists[0] = thisSuit;
        cardLists[1] = leftSuit;
        cardLists[2] = rightSuit;

        while (thisSuit.size() > 0) {
            int n = order[0];
            int j = cardLists[n].size() - 1;
            Card.Rank firstCard = cardLists[n].get(j).getRank();
            cardLists[n].remove(j);

            for (int i = 1; i < 3; ++i) {
                int k = order[i];
                if (cardLists[k].size() == 0) {
                    continue;
                }
                if (firstCard.compare(cardLists[k].get(0).getRank()) < 0) {
                    j = cardLists[k].size() - 1;
                    if (k == 0 && !calcMeStart) {
                        ++listData.minTheyStart;
                    }
                } else {
                    j = cardLists[k].getMaxLessThan(firstCard);
                    if (n == 0) {
                        if (calcMeStart) {
                            ++listData.minMeStart;
                        }
                    }
                }
                cardLists[k].remove(j);
            }
        }
    //        Logger.printf("meStart %d, theyStart %d\n", listData.tricksMeStart, listData.tricksTheyStart);
    }

    public ListData getListData(CardList _leftSuit, CardList _rightSuit) {
        ListData listData = new ListData();
        if (this.isEmpty()) {
            listData.minTheyStart = listData.minMeStart = 0;
            listData.ok1stMove = false;
            return listData;
        }

        listData.suit = this.get(0).getSuit();
        calcMinTricks(listData, _leftSuit, _rightSuit);   // me start
        calcMinTricks(listData, _leftSuit, _rightSuit);   // they start
        return listData;
    }

    public static class ListData {
        public Suit suit;
        public CardList thisSuit, leftSuit, rightSuit;
        public int minMeStart = -1;     // tricks
        public int minTheyStart = -1;   // tricks
        public boolean good;        // for all-pass, includes smallest rank
        public boolean ok1stMove;   // minMeStart == minTheyStart

        public int maxMeStart = -1;     // tricks
        public int maxTheyStart = -1;   // tricks


        public int maxTricks;       // maximum we can have
        public int holes;           // is problematic when holes > 0
        public int distanceToTop = Config.MAX_DISTANCE_TO_TOP;
    }
}