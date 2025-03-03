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

    // the whole deck minus mine and discarded
    public CardList getTheirs(Suit suit, Set<Card> discarded) {
        CardList theirs = new CardList();
        CardList deck = CardList.getDeck();
        for (Card card : deck) {
            if (!card.getSuit().equals(suit) || discarded.contains(card) || this.contains(card)) {
                continue;
            }
            theirs.add(card);
        }
        return theirs;
    }

    public ListData getListData(Set<Card> discarded) {
        ListData listData = new ListData();
        listData.size = this.size();
        if (this.size() == 0) {
            return listData;
        }
        listData.suit = this.get(0).getSuit();
        calculateHoles(listData, discarded);
        listData.maxTricks = getMaxTricks(discarded);
        return listData;
    }

    public void calculateHoles(ListData listData, Set<Card> discarded) {
        int gap = 0;
        int prevIndex = -1;
        int nextClean = Card.Rank.SEVEN.ordinal();
        Card.Rank possibleOk1stMove = null;
        int i = 0;
        for (Card.Rank rank : Card.Rank.values()) {
            if (rank.equals(Card.Rank.SIX)) {
                continue;
            }
            if (i >= this.size()) {
                break;
            }
            Card card = new Card(listData.suit, rank);
            if (discarded.contains(card)) {
                ++gap;
                ++nextClean;
                continue;
            }
            if (!card.equals(this.get(i))) {
                continue;
            }
            if (i == 1) {
                if (this.get(1).getRank().ordinal() - this.get(0).getRank().ordinal() <= gap + 1) {
                    listData.ok1stMove = true;
                } else if (listData.good) {
                    possibleOk1stMove = this.get(1).getRank();
                }
/*
            } else if (i == 2 && possibleOk1stMove != null) {
                // is 7KA ok1stMove?
                if (this.get(2).getRank().ordinal() - possibleOk1stMove.ordinal() <= gap + 1) {
                    listData.ok1stMove = true;
                }
//*/
            }
//            if (!card.equals(this.get(i))) {
//                continue;
//            }
            int ord = card.getRank().ordinal();
            if (ord <= nextClean) {
                gap = 0;
                // clean so far
                if (i == 0) {
                    listData.good = true;
                }
                nextClean += 2;
                prevIndex = ord;
            } else {
                int hole = ord - prevIndex - gap - 2;
//            System.out.printf("%s -> %d\n", card.getRank(), hole);
                if (hole > 0) {
                    listData.holes += hole;
                }
                prevIndex = ord;
                nextClean += 2;
            }
            ++i;
        }
        int distanceToTop = 0;
        int j = Card.Rank.ACE.ordinal();
        while (this.get(this.size() - 1).getRank().ordinal() < j) {
            Card c = new Card(this.get(0).getSuit(), Card.Rank.values()[j--]);
            if (discarded.contains(c)) {
                continue;
            }
            ++distanceToTop;
        }
//        for (int j = Card.Rank.ACE.ordinal(); j >= 0; --j) {
//            Card card =
//                    this.get(j);
//            if (discarded.)
//        }
        listData.distanceToTop = distanceToTop;
//        System.out.printf("%d\n", listData.distanceToTop);
    }

    // max we get if we start every trick
    public int getMaxTricks(Set<Card> discarded) {
        if (this.size() == 0) {
            return 0;
        }
        int myTricks = 0;
        int theirTricks = 0;
        CardList theirs = getTheirs(this.get(0).getSuit(), discarded);
        if (this.size() == 4) {
            // 2-0 - 47,37%
            // 3-0 - 21,05%
            // 4-0 - 8,67%, we ignore 4-0 distributions
            if (theirs.size() > 0) {
                theirs.remove(0);    // remove their lowest card,
            } else {
                theirTricks = 0;
            }
        }

        CardList suit = (CardList) this.clone();
        int myMax, theirMax;
        while ((myMax = suit.size() - 1) >= 0 && (theirMax = theirs.size() - 1) >= 0) {
            // me start every trick
            if (this.get(myMax).getRank().ordinal() > theirs.get(theirMax).getRank().ordinal()) {
                ++myTricks;
                suit.remove(myMax);        // my top rank
                theirs.remove(0);    // their bottom rank
            } else {
                ++theirTricks;
                // use their least rank greater than mine
                int j = theirMax - 1;
                while (j >= 0 &&
                        suit.get(myMax).getRank().ordinal() < theirs.get(j).getRank().ordinal()) {
                    --j;
                }
                suit.remove(myMax);          // my top rank
                theirs.remove(j + 1);  // their bottom greater than my top
            }
        }
        myTricks += suit.size();        // the remaining cards are my tricks
        theirTricks += theirs.size();   // the remaining cards are their tricks
        return myTricks;
    }

    // 1st pair:
    // how little we get 1. when we start, 2. when they start
    // 2nd pair:
    // good - list includes minimal card
    // problematic - tricks are not the same
    public Pair<Couple<Integer>, Couple<Boolean>> getMinTricks(Set<Card> discarded) {
        Pair<Couple<Integer>, Couple<Boolean>> res =
                new Pair<>(new Couple<>(0, 0), new Couple<>(false, false));
        if (this.size() == 0) {
            return res;
        }
        for (int turn : new int[]{1, 2}) {
            int myTricks = 0;
            int theirTricks = 0;
            CardList theirs = getTheirs(this.get(0).getSuit(), discarded);
            if (theirs.size() == 0) {
                res.second.first = true;
            } else {
                res.second.first = this.get(0).getRank().equals(theirs.get(0).getRank());
            }
            if (this.size() == 4) {
                // 2-0 - 47,37%
                // 3-0 - 21,05%
                // 4-0 - 8,67%, we ignore 4-0 distributions
                theirs.remove(theirs.size() - 1);    // remove their top card,
            }
            CardList suit = (CardList) this.clone();
            if (turn == 1) {
                while (suit.size() > 0 && theirs.size() > 0) {
                    // me start every trick
                    if (suit.get(0).getRank().compareTo(theirs.get(0).getRank()) < 0) {
                        ++theirTricks;
                        suit.remove(0);                     // my bottom rank
                        theirs.remove(theirs.size() - 1);   // their top rank
                    } else {
                        ++myTricks;
//                    suit.remove(myMax);        // my top rank
                        // use their least rank greater than mine
                        int j = 0;
                        while (j < theirs.size() &&
                                suit.get(0).getRank().compareTo(theirs.get(j).getRank()) > 0) {
                            ++j;
                        }
                        theirs.remove(j - 1);    // their top rank less than my bottom
                        suit.remove(0);                     // my bottom rank
                    }
                }
                myTricks += suit.size();        // the remaining cards are my tricks
                theirTricks += theirs.size();   // the remaining cards are their tricks
//                Couple<Integer> tricks = res.first;
//                tricks.first = myTricks;
                res.first.first = myTricks;

            } else {
                while (suit.size() > 0 && theirs.size() > 0) {
                    // they start every trick
                    if (suit.get(0).getRank().compareTo(theirs.get(0).getRank()) > 0) {
                        ++myTricks;
                        suit.remove(suit.size() - 1);   // my top rank
                        theirs.remove(0);               // their bottom rank
                    } else {
                        ++theirTricks;
//                    suit.remove(myMax);        // my top rank
                        // use my least rank greater than theirs
                        int j = 0;
                        while (j < suit.size() &&
                                suit.get(j).getRank().compareTo(theirs.get(0).getRank()) < 0) {
                            ++j;
                        }
                        suit.remove(j - 1);    // my top rank less than their bottom
                        theirs.remove(0);      // their bottom rank
                    }
                }
            }
//            myTricks += suit.size();        // the remaining cards are my tricks
            theirTricks += theirs.size();     // the remaining cards are their tricks
//            res.second = myTricks;
            res.first.second = myTricks;
            res.second.second = res.first.first == res.first.second;
        }
        return res;
    }

    public static class ListData {
        public Suit suit;
        public int size;            // list size
        public int maxTricks;       // maximum we can have
        public int holes;           // is problematic when holes > 0
        public boolean good;        // for all-pass, includes smallest rank
        public boolean ok1stMove;   // 1st move does not add tricks, есть плотность
        public int distanceToTop = Config.MAX_DISTANCE_TO_TOP;
    }
}