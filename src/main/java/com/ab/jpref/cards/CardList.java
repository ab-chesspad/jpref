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

import java.util.*;

public class CardList extends ArrayList<Card> {
    static final int MAX_EVAL = 1000;   // in ‰
    // https://summoning.ru/games/miser.shtml
    // probabilities to get a trick on misère:
    private static final String[] misereTableSources = {
        "789A 86",
        "78XA 86",
        "78JA 86",
        "78Q 458",
        "78QK 310",
        "78QA 380",     //?
        "78QKA 210",
        "78K 790",
        "78KA 582",
        "79XA 86",
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
        "89Х 800",  //?
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
        for (Card.Suit suit : Card.Suit.values()) {
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
        for (CardList cardList : cardLists) {
            addAll(cardList);
        }
    }

    public CardList(Collection<Card> cards) {
        addAll(cards);
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
        return toColorString(false);
    }

    public String toColorString() {
        return toColorString(true);
    }

    public String toColorString(boolean color) {
        StringBuilder sb = new StringBuilder();
        Card.Suit suit = null;
        String sep = "";
        for (Card c : this) {
            Card.Suit s = c.getSuit();
            if (!s.equals(suit)) {
                suit = s;
                if (color) {
                    if (s.equals(Card.Suit.DIAMOND) || s.equals(Card.Suit.HEART)) {
                        sb.append(Card.ANSI_RED);
                    } else {
                        sb.append(Card.ANSI_RESET);
                    }
                }
                sb.append(sep).append(suit.toString());
                sep = " ";
            }
            sb.append(c.getRank());
        }
        if (color) {
            sb.append(Card.ANSI_RESET);
        }
        return sb.toString();
    }

    boolean equals(CardList that) {
        if (this.size() != that.size()) {
            return false;
        }
        for (int i = 0; i < this.size(); ++i) {
            if (!this.get(i).equals(that.get(i))) {
                return false;
            }
        }
        return true;
    }

    public Card first() {
        if (isEmpty()) {
            return null;
        }
        return get(0);
    }

    public Card removeFirst() {
        if (isEmpty()) {
            return null;
        }
        return remove(0);
    }

    public Card last() {
        if (isEmpty()) {
            return null;
        }
        return get(size() - 1);
    }

    public Card removeLast() {
        if (isEmpty()) {
            return null;
        }
        return remove(size() - 1);
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

    public static Card getMin(CardList leftSuit, CardList rightSuit) {
        Card res = null;
        if (!rightSuit.isEmpty()) {
            res = rightSuit.first();
        }
        if (!leftSuit.isEmpty()) {
            Card card = leftSuit.first();
            if (res == null || card.compareInTrick(res) < 0) {
                res = card;
            }
        }
        return res;
    }

    public int getOptimalStart(CardList leftSuit, CardList rightSuit) {
        if (this.isEmpty()) {
            return -1;
        }
        if (this.size() == 1) {
            return 0;
        }

        if (this.first().compareInTrick(getMin(leftSuit, rightSuit)) > 0 ) {
            return 0;
        }
        Card myNext = get(1);
        int next = getMaxLessThan(myNext.getRank());
        if (next == 0) {
            if (leftSuit.size() > 1 && rightSuit.size() > 1 ||
                    leftSuit.size() == 1 && myNext.compareInTrick(leftSuit.first()) < 0 ||
                    rightSuit.size() == 1 && myNext.compareInTrick(rightSuit.first()) < 0) {
                ++next;     // preserve min
            } else {
                // todo
                next = next;
            }
        }
        return next;
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

    public ListData maxUnwantedTricks(CardList leftSuit, CardList rightSuit, int elderHand) {
        ListData listData = new ListData(this);
        listData.cardsLeft = 0;
        if (this.isEmpty()) {
            return listData;
        }
        listData.suit = this.first().getSuit();

        CardList[] cardLists = simplifyHands(leftSuit, rightSuit);
        listData.good = true;
        Card.Rank myMinRank = this.first().getRank();
        if (listData.good && !leftSuit.isEmpty()) {
            listData.good = myMinRank.compare(leftSuit.first().getRank()) < 0;
        }
        if (listData.good && !rightSuit.isEmpty()) {
            listData.good = myMinRank.compare(rightSuit.first().getRank()) < 0;
        }

        int n = elderHand;
mainLoop:
        while (cardLists[n].size() > 0) {
            int k = (n + 1) % cardLists.length;
            int r = (n + 2) % cardLists.length;
            int j0 = cardLists[n].getOptimalStart(cardLists[k], cardLists[r]);
            Card topCard = cardLists[n].remove(j0);
            int top = n;
            int empty = 0;
            for (int i = 1; i < cardLists.length; ++i) {
                k = (n + i) % cardLists.length;
                int left = (n + i + 1) % cardLists.length;
                boolean lastTrickCard = i == cardLists.length - 1;
                if (cardLists[k].isEmpty()) {
                    if (++empty >= 2) {
                        if (k == 0) {
                            ++listData.cardsLeft;
                        }
                        break mainLoop;
                    }
                    continue;
                }
                int j;
                if (lastTrickCard) {
                    if (topCard.compareTo(cardLists[k].first()) < 0) {
                        j = cardLists[k].size() - 1;    // will take it anyway
                    } else {
                        if (top == 0 || k == 0) {
                            // yield it
                            j = cardLists[k].getMaxLessThan(topCard.getRank());
                        } else {
                            j = cardLists[k].size() - 1;    // ok to grab it
                        }
                    }
                } else {
                    // left hand did not play yet
                    if (top == 0) {
                        // self played top
                        if (topCard.compareTo(cardLists[k].first()) < 0 ||
                                topCard.compareTo(cardLists[left].first()) < 0) {
                            j = cardLists[k].size() - 1;    // ok to grab it
                        } else {
                            // both are smaller than top
                            j = cardLists[k].getMaxLessThan(topCard.getRank());
                        }
                    } else {
                        // self has not played yet
                        int jLeft = Integer.MAX_VALUE;
                        int jTop = Integer.MAX_VALUE;
                        Card myMin = cardLists[k].first();
                        if (k == 0) {
                            // self playing
                            if (myMin.compareTo(cardLists[left].first()) < 0) {
                                jLeft = cardLists[k].getMaxLessThan(cardLists[left].first().getRank());
                            }
                            if (myMin.compareTo(topCard) < 0) {
                                jTop = cardLists[k].getMaxLessThan(topCard.getRank());
                            }
                            j = cardLists[k].size() - 1;
                            if (j > jLeft) {
                                j = jLeft;
                            }
                            if (j > jTop) {
                                j = jTop;
                            }
                        } else {
                            if (myMin.compareTo(cardLists[left].first()) < 0) {
                                // compare to self
                                jLeft = cardLists[k].getMaxLessThan(cardLists[left].first().getRank());
                            }
                            if (myMin.compareTo(topCard) < 0) {
                                jTop = cardLists[k].getMaxLessThan(topCard.getRank());
                            }
                            j = cardLists[k].size() - 1;    // ok to grab it
                            if (j > jTop) {
                                j = jTop;
                            }
                            if (j > jLeft) {
                                j = jLeft;
                            }
                        }
                    }
                }
                Card card = cardLists[k].remove(j);
                if (card.compareTo(topCard) > 0) {
                    topCard = card;
                    top = k;
                }
            }

            if (top == 0) {
                if (elderHand == 0) {
                    ++listData.maxMeStart;
                } else {
                    ++listData.maxTheyStart;
                }
            }
            n = top;
        }

        if (n == 0) {
            listData.cardsLeft += cardLists[0].size();
        }
        if (listData.maxMeStart > 0 || listData.maxTheyStart > 0) {
            listData.misereEval = getEval4Misere();
        }
        return listData;
    }

    public ListData maxUnwantedTricks(CardList leftSuit, CardList rightSuit, boolean meStart) {
        ListData listData = new ListData(this);
        if (this.isEmpty()) {
            return listData;
        }
        listData.suit = this.first().getSuit();

        CardList[] cardLists = simplifyHands(leftSuit, rightSuit);
        listData.good = true;
        Card.Rank myMin = this.first().getRank();
        if (listData.good && !leftSuit.isEmpty()) {
            listData.good = myMin.compare(leftSuit.first().getRank()) < 0;
        }
        if (listData.good && !rightSuit.isEmpty()) {
            listData.good = myMin.compare(rightSuit.first().getRank()) < 0;
        }

        boolean keepDoing = true;
        boolean firstMove = true;
mainLoop:
        while (keepDoing) {
            int n = 0;
            if (!meStart) {
                n = 2;
            }
            if (cardLists[n].isEmpty()) {
                break;
            }
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
                    if (cardLists[k].first().getRank().compare(firstCard) < 0) {
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
                } else if (firstMove) {
                    meStart = false;
                }
            }
            firstMove = false;
        }

        if (meStart) {
            listData.cardsLeft = cardLists[0].size() - cardLists[2].size();
        }
        if (listData.maxMeStart + listData.maxTheyStart > 0) {
            listData.misereEval = getEval4Misere();
        }
        return listData;
    }

    public ListData getUnwantedTricks(CardList leftSuit, CardList rightSuit) {
        ListData listData = maxUnwantedTricks(leftSuit, rightSuit, 0);
        ListData listData1 = maxUnwantedTricks(leftSuit, rightSuit, 2);
        // consolidate:
        listData.maxTheyStart = listData1.maxTheyStart;
        return listData;
    }

    public ListData minWantedTricks(CardList leftSuit, CardList rightSuit, int elderHand) {
        ListData listData = new ListData(this);
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
            return MAX_EVAL + this.first().getRank().getValue() - Card.Rank.ACE.getValue();
        }
        return res;
    }

    public static class ListData {
        public Card.Suit suit;
        public CardList thisSuit;
        public int maxTheyStart;    // unwanted tricks
        public int maxMeStart;      // unwanted tricks
        public int maxRightStart;    // misere, unwanted tricks
        public int maxLeftStart;      // misere, unwanted tricks
        public int cardsLeft = -1;  // when I always start
        public boolean good;        // for all-pass, includes smallest rank
        public int misereEval = 0;

        public int minMeStart;      // wanted tricks
        public int minTheyStart;    // wanted tricks

        public ListData(CardList thisSuit) {
            this.thisSuit = thisSuit;
        }
    }
}