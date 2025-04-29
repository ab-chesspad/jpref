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

import java.util.*;

public class CardList extends ArrayList<Card> {
    static final int MAX_EVAL = 1000;   // in ‰
    // https://summoning.ru/games/miser.shtml
    // probabilities to get a trick on misère:
    private static final String[] misereTableSources = {
        "78XA 86",
        "78JA 86",
        "78Q 458",
        "78QK 310",
        "78QA 380",     //?
        "78QKA 210",
        "78K 790",
        "78KA 582",
        "79JA 86",
        "79Q 458",
        "79QK 310",
        "79QA 380",     //?
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

    public CardList() {
        super();
    }

    public CardList(CardList[] cardLists) {
        for (CardList suit : cardLists) {
            addAll(suit);
        }
    }

    public CardList(Collection<Card> cards) {
        addAll(cards);
    }

    public static CardList[] clone(CardList[] them) {
        CardList[] newOne = new CardList[them.length];
        for (int i = 0; i < them.length; ++i) {
            newOne[i] = (CardList) them[i].clone();
        }
        return newOne;
    }

    public static int totalCards(CardList[] cardLists) {
        int total = 0;
        for (CardList cardList : cardLists) {
            total += cardList.size();
        }
        return total;
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

    public Card first() {
        if (isEmpty()) {
            return null;
        }
        return get(0);
    }

    public Card last() {
        if (isEmpty()) {
            return null;
        }
        return get(size() - 1);
    }

    public void verifyDeck() {
        StringBuilder msg = new StringBuilder();
        final CardList deck = getDeck();
        Set<Card> set = new HashSet<>(this);

        Map<Card, Integer> counts = new HashMap<>();
        for (Card card : this) {
            set.add(card);
            Integer res = counts.get(card);
            int count = 0;
            if (res != null) {
                count = res;
            }
            counts.put(card, ++count);
        }

        msg.append("occurrences: ");
        String sep = "";
        boolean found = false;
        for (Map.Entry<Card, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == 1) {
                continue;
            }
            found = true;
            msg.append(sep).append(String.format("%s: %d", entry.getKey(), entry.getValue()));
            sep = ", ";
        }

        if (found) {
            throw new RuntimeException(msg.toString());
        }

        if (set.size() < deck.size()) {
            msg.delete(0, msg.length());
            msg.append("missing: ");
            sep = "";
            for (Card card : deck) {
                if (!set.contains(card)) {
                    msg.append(sep).append(card);
                    sep = ", ";
                }
            }
            throw new RuntimeException(msg.toString());
        }
    }

    public int getMaxLessThan(Card.Rank other) {
        if (other == null) {
            return 0;
        }
        int res = - 1;
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
        int res = -1;
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

/*
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
*/

    int getOptimalStart(CardList leftSuit, CardList rightSuit) {
        int res = 0;
        if (this.size() == 1) {
            return res;
        }
        Card.Rank min = this.first().getRank();
        Card.Rank min1 = null;
        if (this.size() > 1) {
            min1 = this.get(1).getRank();
        }
        Card.Rank max = this.last().getRank();
        if (leftSuit.size() > 1) {
            if (max.compare(leftSuit.last().getRank()) < 0) {
                if (min1.compare(leftSuit.first().getRank()) > 0) {
                    res = this.size() - 1;
                }
            }
        }
        if (rightSuit.size() > 1) {
            if (max.compare(rightSuit.last().getRank()) < 0) {
                if (min1.compare(rightSuit.first().getRank()) > 0) {
                    res = this.size() - 1;
                }
            }
        }
        return  res;
    }

    // clone lists and remove duplicates from leftSuit
    CardList[] simplifyHands(CardList _leftSuit, CardList _rightSuit) {
        CardList rightSuit = (CardList)_rightSuit.clone();
        CardList leftSuit = new CardList();
        for (Card card : _leftSuit) {
            if (!rightSuit.contains(card)) {
                leftSuit.add(card);
            }
        }
        CardList[] cardLists = new CardList[3];
        cardLists[0] = (CardList)this.clone();
        cardLists[1] = leftSuit;
        cardLists[2] = rightSuit;
        return cardLists;
    }

    public ListData maxUnwantedTricks(CardList leftSuit, CardList rightSuit, boolean meStart) {
        ListData listData = new ListData();
        if (this.isEmpty()) {
            return listData;
        }
        listData.suitNum = this.get(0).getSuit().getValue();

        CardList[] cardLists = simplifyHands(leftSuit, rightSuit);
        listData.good = true;
        Card.Rank myMin = this.get(0).getRank();
        if (listData.good && !leftSuit.isEmpty()) {
            listData.good = myMin.compare(leftSuit.get(0).getRank()) < 0;
        }
        if (listData.good && !rightSuit.isEmpty()) {
            listData.good = myMin.compare(rightSuit.get(0).getRank()) < 0;
        }

        int n = 0;
        if (!meStart) {
            n = 2;
        }
mainLoop:
        while (cardLists[n].size() > 0) {
            int j0 = 0;
            if (meStart) {
                j0 = cardLists[n].getOptimalStart(
                    cardLists[(n + 1) % cardLists.length], cardLists[(n + 2) % cardLists.length]);      // 8JQK should start with JQK
            }

            Card.Rank firstCard = cardLists[n].get(j0).getRank();
            cardLists[n].remove(j0);
            boolean myTrick = true;
            int empty = 0;
            for (int i = 1; i < cardLists.length; ++i) {
                int k = (n + i) % cardLists.length;
                if (cardLists[k].isEmpty()) {
                    if (k == 0) {
                        myTrick = false;
                    }
                    if (++empty >= 2) {
                        break mainLoop;
                    }
                    continue;
                }
                int j;
                if (meStart) {
                    if (cardLists[k].first().getRank().compare(firstCard) < 0) {
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

            if (meStart) {
                if (myTrick) {
                    ++listData.maxMeStart;
                }
            } else {
                if (myTrick) {
                    ++listData.maxTheyStart;
                }
            }
        }
        if (meStart) {
            listData.cardsLeft = cardLists[0].size() - cardLists[2].size();
        }
        return listData;
    }

    public ListData minWantedTricks(CardList leftSuit, CardList rightSuit, boolean meStart) {
        ListData listData = new ListData();
        if (this.isEmpty()) {
            return listData;
        }
/*
//        listData.suit = this.get(0).getSuit();
        listData.suitNum = this.get(0).getSuit().getValue();
        simplifyHands(listData, leftSuit, rightSuit);
        int[] order;
        if (meStart) {
            order = new int[] {0,1,2};
        } else {
            order = new int[] {2,1,0};
        }

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
                if (meStart) {
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

            if (meStart) {
                if (myTrick) {
                    ++listData.minMeStart;
                }
            } else {
                if (myTrick) {
                    ++listData.minTheyStart;
                }
            }
        }
*/
        return listData;
    }

    public ListData getUnwantedTricks(CardList leftSuit, CardList rightSuit) {
        ListData listData = maxUnwantedTricks(leftSuit, rightSuit, true);
        ListData listData1 = maxUnwantedTricks(leftSuit, rightSuit, false);
        // consolidate:
        listData.maxTheyStart = listData1.maxTheyStart;
        listData.ok1stMove = listData.maxMeStart == listData.maxTheyStart;
        if (listData.maxTheyStart > 0 || listData.maxMeStart > 0) {
            listData.misereEval = getEval4Misere();
        }
        return listData;
    }

/*
    public void misereTricks(ListData listData, CardList leftSuit, CardList rightSuit, boolean meStart) {
        int[] order;
        if (meStart) {
            order = new int[] {0,1,2};
        } else {
            order = new int[] {2,1,0};
        }

        CardList[] cardLists = new CardList[3];
        cardLists[0] = listData.thisSuit;
        cardLists[1] = listData.leftSuit;
        cardLists[2] = listData.rightSuit;

        boolean first = true;
        int n = order[0];
        while (cardLists[n].size() > 0) {
            int j = 0;
            if (meStart) {
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

                if (meStart) {
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

            if (meStart) {
                if (myTrick) {
                    ++listData.maxMeStart;
                } else if (first) {
                    listData.good = true;
                }
            } else {
                if (myTrick) {
                    ++listData.maxTheyStart;
                }
            }
            first = false;
        }
        listData.ok1stMove = listData.maxMeStart == listData.maxTheyStart;
        if (listData.maxTheyStart > 0 || listData.maxMeStart > 0) {
            listData.misereEval = getEval4Misere();
        }
    }
*/

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
        public int suitNum = -1;
        public CardList thisSuit;
        public int maxTheyStart;    // unwanted tricks
        public int maxMeStart;      // unwanted tricks
        public int cardsLeft;       // when I always start
        public boolean good;        // for all-pass, includes smallest rank
        public boolean ok1stMove;   // maxMeStart == maxTheyStart
        public int misereEval;

        public int minMeStart;      // wanted tricks
        public int minTheyStart;    // wanted tricks
    }
}