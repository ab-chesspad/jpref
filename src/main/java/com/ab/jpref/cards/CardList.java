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

import com.ab.jpref.cards.Card.Suit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CardList extends ArrayList<Card> {
    static final int MAX_EVAL = 1000;   // in ‰
    // https://summoning.ru/games/miser.shtml
    // probabilities to get a trick on misère:
    private static final String[] misereTableSources = {
        "78XA 86",
        "78JA 86",
        "78Q 458",
        "78QK 310",
        "78QA 320",     //?
        "78QKA 210",
        "78K 790",
        "78KA 582",
        "79JA 86",
        "79Q 458",
        "79QK 310",
        "79QKA 210",
        "79K 790",
        "79KA 582",
        "7X 460",
        "7XJ 667",
        "7XJQ 737",
        "7XJQK 737",
        "7XJQKA 474",
        "7J 843",
        "8 119",
        "89 668",
        "9 627",
        "X 700",
    };
    static final Map<String, Integer> misereTable = new HashMap<>();
    static {
        for (String source : misereTableSources) {
            String[] parts = source.split(" ");
            misereTable.put(parts[0], Integer.parseInt(parts[1]));
        }
    }

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

    public int getMinGreaterThan(Card.Rank other) {
        if (other == null) {
            return 0;
        }
        int res = 0;
        for (int i = this.size() - 1; i >= 0 ; --i) {
            Card.Rank r = this.get(i).getRank();
            if (r.compare(other) > 0) {
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

    int getOptimalStart(ListData listData) {
        int res = 0;
        if (listData.thisSuit.size() == 1) {
            return res;
        }
        Card.Rank min = listData.thisSuit.get(0).getRank();
        Card.Rank min1 = null;
        if (listData.thisSuit.size() > 1) {
            min1 = listData.thisSuit.get(1).getRank();
        }
        Card.Rank max = listData.thisSuit.get(listData.thisSuit.size() - 1).getRank();
        if (listData.leftSuit.size() > 1) {
            if (max.compare(listData.leftSuit.get(listData.leftSuit.size() - 1).getRank()) < 0) {
                if (min1.compare(listData.leftSuit.get(0).getRank()) > 0) {
                    res = listData.thisSuit.size() - 1;
                }
            }
        }
        if (listData.rightSuit.size() > 1) {
            if (max.compare(listData.rightSuit.get(listData.rightSuit.size() - 1).getRank()) < 0) {
                if (min1.compare(listData.rightSuit.get(0).getRank()) > 0) {
                    res = listData.thisSuit.size() - 1;
                }
            }
        }
        return  res;
    }

    public void calcMinTricks(ListData listData, CardList leftSuit, CardList rightSuit) {
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
        int n = order[0];
        while (cardLists[n].size() > 0) {
            int j = 0;
            if (calcMeStart) {
                j = getOptimalStart(listData);      // 8JQK should start with JQK
            }
            Card.Rank firstCard = cardLists[n].get(j).getRank();
            cardLists[n].remove(j);
            boolean myTrick = true;
            for (int i = 1; i < cardLists.length; ++i) {
                int k = order[i];
                if (cardLists[k].size() == 0) {
                    if (k == 0) {
                        myTrick = false;
                    }
                    continue;
                }

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
                        firstCard = cardLists[k].get(j).getRank();
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
        listData.thisSuit = (CardList)this.clone();
        if (listData.minTheyStart > 0 || listData.minMeStart > 0) {
            listData.misereEval = getEval4Misere();
        }
    }

    public void calcMaxTricks(ListData listData, CardList leftSuit, CardList rightSuit) {
        int[] order;
        boolean calcMeStart;
        if (listData.maxMeStart < 0) {
            order = new int[] {0,1,2};
            listData.maxMeStart = 0;
            calcMeStart = true;
        } else {
            order = new int[] {1,2,0};
            listData.maxTheyStart = 0;
            calcMeStart = false;
        }
        simplifyHands(listData, leftSuit, rightSuit);

        CardList[] cardLists = new CardList[3];
        cardLists[0] = listData.thisSuit;
        cardLists[1] = listData.leftSuit;
        cardLists[2] = listData.rightSuit;

        int n = order[0];
        while (cardLists[n].size() > 0) {
            int j = cardLists[n].size() - 1;
            Card.Rank firstCard = cardLists[n].get(j).getRank();
            cardLists[n].remove(j);
            boolean myTrick = true;
            for (int i = 1; i < cardLists.length; ++i) {
                int k = order[i];
                if (cardLists[k].size() == 0) {
                    if (k == 0) {
                        myTrick = false;
                    }
                    continue;
                }

                int m = cardLists[k].size() - 1;
                if (calcMeStart) {
                    if (cardLists[k].get(m).getRank().compare(firstCard) < 0) {
                        j = 0;
                    } else {
                        j = cardLists[k].getMinGreaterThan(firstCard);
                        firstCard = cardLists[k].get(j).getRank();
                        myTrick = false;
                    }
                } else {
                    if (cardLists[k].get(m).getRank().compare(firstCard) < 0) {
                        if (k == 0) {
                            myTrick = false;
                        }
                        j = 0;
                    } else {
                        j = cardLists[k].getMinGreaterThan(firstCard);
                        firstCard = cardLists[k].get(j).getRank();
                    }
                }
                cardLists[k].remove(j);
            }

            if (calcMeStart) {
                if (myTrick) {
                    ++listData.maxMeStart;
                }
            } else {
                if (myTrick) {
                    ++listData.maxTheyStart;
                }
            }
        }
        listData.thisSuit = (CardList)this.clone();
    }

    public ListData getListData(CardList leftSuit, CardList rightSuit) {
        ListData listData = new ListData();
        if (this.isEmpty()) {
            listData.minTheyStart = listData.minMeStart = 0;
            listData.ok1stMove = false;
            listData.maxTheyStart = listData.maxMeStart = 0;
            return listData;
        }

        listData.suit = this.get(0).getSuit();
        calcMinTricks(listData, leftSuit, rightSuit);   // me start
        calcMinTricks(listData, leftSuit, rightSuit);   // they start
        calcMaxTricks(listData, leftSuit, rightSuit);   // me start
        calcMaxTricks(listData, leftSuit, rightSuit);   // they start
        return listData;
    }

    private int getEval4Misere() {
        StringBuilder sb = new StringBuilder();
        for (Card card : this) {
            sb.append(card.getRank().toString());
        }
        Integer res = misereTable.get(sb.toString());
        if (res == null) {
            return MAX_EVAL + this.get(0).getRank().getValue() - Card.Rank.ACE.getValue();
        }
        return res;
    }

    public static class ListData {
        public Suit suit;
        public CardList thisSuit, leftSuit, rightSuit;
        public int minMeStart = -1;     // tricks
        public int minTheyStart = -1;   // tricks
        public boolean good;        // for all-pass, includes smallest rank
        public boolean ok1stMove;   // minMeStart == minTheyStart
        public int misereEval;

        public int maxMeStart = -1;     // tricks
        public int maxTheyStart = -1;   // tricks

    }
}