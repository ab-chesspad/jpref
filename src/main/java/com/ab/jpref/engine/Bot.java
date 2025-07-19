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
import com.ab.jpref.cards.Hand;
import com.ab.jpref.config.Config;

import java.util.*;

public class Bot extends Player {
    protected final int number;

    public Bot(String name, int number) {
        super(name);
        this.number = number;
    }

    public Bot(Player realPlayer) {
        super(realPlayer);
        if (realPlayer != null) {
            this.number = realPlayer.getNumber();
        } else {
            this.number = 0;
        }
    }

    public Bot(String name, Collection<Card> cards) {
        super(name, cards);
        number = 0;
    }

    @Override
    public int getNumber() {
        return number;
    }

/*
    public Bot(Bot other) {
        super(other.name);
        CardList otherHand = new CardList(players[1].rightHand);
        Bot probeBot = new Bot("probe misere", hisCards);

    }
*/

    public MisereBot getMisereBot(Player fictitiousBot, Bot realPlayer) {
        return new MisereBot(fictitiousBot, realPlayer);
    }

    @Override
    public void declareRound(Config.Bid minBid, int elderHand) {
        HandResults handResults = null;
        if (Config.Bid.BID_MISERE.equals(minBid)) {
            MisereBot misereBot = getMisereBot(this, this);
            handResults = misereBot.dropForMisere();
        }
        // todo

        drop(handResults.dropped);
    }

    private Card drop4AllPass(Trick trick) {
        CardList.ListData problemList = null;
        CardList.ListData noProblemList = null;
        Card res = null;

//* debug
        if (trick.number >= 4) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/
        for (CardList cardList : myHand) {
            if (cardList.isEmpty()) {
                continue;
            }
            Card.Suit suit = cardList.first().getSuit();
            if (theirMin(suit) == null) {
                continue;
            }
            CardList.ListData listData = cardList.getUnwantedTricks(leftHand.list(suit), rightHand.list(suit));
            if (listData.maxMeStart == listData.maxTheyStart) {
                // ok1stMove, no problem
                // select the cardList with the most unwanted tricks
                if (noProblemList == null || noProblemList.maxMeStart < listData.maxMeStart) {
                    noProblemList = listData;
                } else if (noProblemList.maxMeStart == listData.maxMeStart) {
                    if (noProblemList.thisSuit.size() > cardList.size()) {
                        // select the shortest cardList
                        noProblemList = listData;
                    } else if (noProblemList.thisSuit.last().getRank().compare(cardList.last().getRank()) < 0) {
                        // select the cardList with the card of the highest rank
                        noProblemList = listData;
                    }
                }
                continue;
            } else {
                // problems
                // quick and dirty:
                if (problemList == null || problemList.maxMeStart < listData.maxMeStart) {
                    problemList = listData;
                }
            }
        }

        CardList probe = null;
        if (noProblemList != null) {
            probe = noProblemList.thisSuit;
        } else if (problemList != null) {
            probe = problemList.thisSuit;
        }

        if (probe != null && probe.size() == 2) {
            // looking for 'пистолет':
            Card myMin = probe.first();
            Card.Suit suit = myMin.getSuit();

            boolean found = false;
            CardList cardList = rightHand.list(suit);
            if (probe.size() <= cardList.size() && probe.last().compareTo(cardList.get(probe.size() - 1)) > 0) {
                // ok to drop it
            } else if (!cardList.isEmpty()) {
                if (myMin.compareInTrick(cardList.last()) < 0 &&
                        myMin.compareInTrick(cardList.first()) > 0) {
                    found = true;
                }
            }
            cardList = leftHand.list(suit);
            if (probe.size() <= cardList.size() && probe.last().compareTo(cardList.get(probe.size() - 1)) > 0) {
                // ok to drop it
            } else if (!cardList.isEmpty()) {
                if (myMin.compareInTrick(cardList.last()) < 0 &&
                        myMin.compareInTrick(cardList.first()) > 0) {
                    found = true;
                }
            }
            if (found) {
                // drop any other:
                for (CardList _suit : myHand) {
                    if (!_suit.isEmpty() && _suit != probe) {
                        res = _suit.last();
                        break;
                    }
                }
            }
        }

        if (res == null) {
            if (noProblemList != null) {
                if (noProblemList.thisSuit.last().compareInTrick(theirMin(noProblemList.suit)) < 0 &&
                        problemList != null && problemList.thisSuit.size() > 2) {
                    res = problemList.thisSuit.last();
                } else {
                    res = noProblemList.thisSuit.last();
                }
            } else if (problemList != null) {
                res = problemList.thisSuit.last();
            } else {
                // any:
                for (CardList cardList : myHand) {
                    if (!cardList.isEmpty()) {
                        res = cardList.last();
                        break;
                    }
                }
            }
        }
        return res;
    }

    @Override
    public Card play(Trick trick) {
        if (Config.Bid.BID_MISERE.equals(trick.minBid)) {
            MisereBot misereBot;
            if (Config.Bid.BID_MISERE.equals(this.bid)) {
                misereBot = getMisereBot(this, this);
            } else {
                Player fictitiousBot = GameManager.getInstance().getDeclarerFor(this.number);
                misereBot = getMisereBot(fictitiousBot, this);
            }
            return misereBot.play(trick);
        }
        // todo
        return playAllPass(trick);
    }

    Card playAllPass1stHand(Trick trick) {
        Card problems = null;
        Card anycard = null;
        CardList.ListData noProblemSuit = new CardList.ListData(null);
        noProblemSuit.maxMeStart = Integer.MAX_VALUE;
/* debug
        if (trick.number >= 6) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/
        // ♣8JQK ♦89 ♥79   ♠A ♣79 ♦XJKA ♥8   ♠J ♣XA ♦7 ♥XJQA 1 - must: ♠A, ♦X and ♦XJ?
        // ♠7 ♦9XK   ♣A ♥9JK   ♠9Q ♦8 ♥7
        // todo: test-2 plays ♠Q, but the best move is ♥7
        for (CardList cardList : this.myHand) {
            if (cardList.isEmpty()) {
                continue;
            }
            Card.Suit suit = cardList.first().getSuit();
            anycard = cardList.last();
            if (theirMin(suit) == null) {
                continue;       // 'козырять' не надо
            }

            CardList.ListData listData = cardList.getUnwantedTricks(leftHand.list(suit), rightHand.list(suit));
            boolean ok1stMove = listData.maxMeStart == listData.maxTheyStart;
            if (ok1stMove) {
                // no problems
                Card.Suit _suit = null;
                int noProblemSuitSize = 0;
                if (noProblemSuit.suit != null) {
                    _suit = noProblemSuit.suit;
                    noProblemSuitSize = myHand.list(_suit).size();
                }

                if (listData.maxMeStart == 0) {
                    Hand checkHand = null;
                    if (leftHand.list(suit).isEmpty()) {
                        checkHand = rightHand;
                    } else if (rightHand.list(suit).isEmpty()) {
                        checkHand = leftHand;
                    }

                    if (checkHand == null) {
                        if (noProblemSuit.suit == null ||
                                noProblemSuit.cardsLeft > listData.cardsLeft) {
                            noProblemSuit = listData;
                        }
                    } else {
                        CardList dummy = new CardList();
                        for (Card.Suit checkSuit : Card.Suit.values()) {
                            if (checkSuit.equals(suit)) {
                                continue;
                            }
                            CardList cardList1 = this.myHand.list(checkSuit);
                            if (cardList1.isEmpty()) {
                                continue;
                            }
                            if (cardList1.first().compareInTrick(theirMin(checkSuit)) < 0) {
                                continue; // no point to start with this cardList, I'll have a chance to do it later
                            }

                            CardList.ListData listData1 =
                                cardList1.maxUnwantedTricks(dummy, checkHand.list(checkSuit),2);
                            if (listData1.maxTheyStart > 0) {
                                //?? I hope I will shove cardList.first() with the next trick
                                if (cardList1.size() > 1 && cardList1.first().compareInTrick(theirMin(checkSuit)) < 0) {
                                    return cardList1.get(1);
                                }
                                if (cardList1.size() > 1 && cardList1.getMaxLessThan(cardList1.get(1).getRank()) == 0) {
                                    return cardList1.get(1);
                                }
                                return cardList1.last();
                            }
                        }
                        // todo: check if she can shove a trick to the 3rd player, and that one can get me
                        return cardList.first();
                    }
                } else if (noProblemSuit.maxMeStart == 0 ||
                        noProblemSuit.maxMeStart > listData.maxMeStart && noProblemSuitSize < cardList.size()) {
                    if (!(listData.maxMeStart > 0 && listData.cardsLeft >= 0
                        && noProblemSuit.suit != null) || noProblemSuit.maxMeStart == 0) {
                        noProblemSuit = listData;
                    }
                } else if (listData.maxMeStart > 0 && cardList.size() >= 4) {
                    noProblemSuit = listData;
                } else { // if (noProblemSuit.maxMeStart == listData.maxMeStart) {
                    // compare number of smaller cards
                    int prev = rightHand.list(_suit).getMaxLessThan(myHand.list(_suit).first().getRank());
                    int curr = rightHand.list(suit).getMaxLessThan(myHand.list(suit).first().getRank());
                    if (curr > prev) {
                        noProblemSuit = listData;
                    } else if (cardList.size() > 1 && cardList.first().compareInTrick(theirMin(suit)) < 0) {
                        noProblemSuit = listData;
                    } if (curr == prev && cardList.size() > myHand.list(_suit).size()) {
                        noProblemSuit = listData;
                    }
                }
            } else {
                // problems
                Card myMax = absMax(suit);
                if (myMax != null) {
                    return myMax;
                }

                if (cardList.size() > 1) {
                    if (!listData.good && listData.maxMeStart > listData.maxTheyStart) {
                        if (rightHand.list(suit).size() > 2 &&
                            rightHand.list(suit).getMaxLessThan(cardList.first().getRank()) == 0) {
                            // there is a sigle card less than my min, so better take this trick
                            problems = cardList.get(1);
                        } else {
                            problems = cardList.first();     // give them a chance to take it
                        }
                    } else {
                        problems = cardList.get(1);     // ?? second from bottom
                    }
                } else {
                    problems = cardList.first();
                }
            }
        }

        Card res;
        if (noProblemSuit.suit != null) {
            Card.Suit suit = noProblemSuit.suit;
            CardList cardList = myHand.list(suit);
        if (cardList.size() > 1) {
            res = cardList.get(1);  // further check?
        } else {
            res = cardList.first();
        }

        } else if (problems != null) {
            res = problems;
        } else {
            res = anycard;
        }
        return res;
    }

    Card absMax(Card.Suit suit) {
        CardList cardList = myHand.list(suit);

        Card.Rank theirMax = Card.Rank.SIX;
        if (!rightHand.list(suit).isEmpty()) {
            Card.Rank r = rightHand.list(suit).last().getRank();
            if (theirMax.compare((r)) < 0) {
                theirMax = r;
            }
        }
        if (!leftHand.list(suit).isEmpty()) {
            Card.Rank r = leftHand.list(suit).last().getRank();
            if (theirMax.compare((r)) < 0) {
                theirMax = r;
            }
        }
        if (cardList.first().getRank().compare(theirMax) > 0) {
            // all my cards are greater than theirs
            return cardList.last();
        }
        return null;
    }

    Card theirMax(Card.Suit suit) {
        Card res = null;
        int theirMax = 0;
        if (!rightHand.list(suit).isEmpty()) {
            Card card = rightHand.list(suit).last();
            int r = card.getRank().getValue();
            if (theirMax < r) {
                theirMax = r;
                res = card;
            }
        }
        if (!leftHand.list(suit).isEmpty()) {
            Card card = leftHand.list(suit).last();
            int r = card.getRank().getValue();
            if (theirMax < r) {
                theirMax = r;
                res = card;
            }
        }
        return res;
    }

    Card theirMin(Card.Suit suit) {
        return CardList.getMin(leftHand.list(suit), rightHand.list(suit));
    }

    // https://review-pref.ru/school/147/111/
    // all-pass: https://review-pref.ru/school/145/17/
    public Card playAllPass(Trick trick) {
        Card res = null;
        if (trick.startingSuit == null) {
            return playAllPass1stHand(trick);
        }

//        int suitNum = trick.startingSuit.getValue();
        Card.Suit suit = trick.startingSuit;
        CardList cardList = this.myHand.list(suit);
        if (cardList.isEmpty()) {
            return drop4AllPass(trick);
        }
        Card myMin = cardList.first();
        if (cardList.size() == 1) {
            return myMin;
        }

        Card myMax = cardList.last();
        Card topTrickCard = trick.topCard;
//* debug
        if (trick.number >= 0 && number == 1) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/

        Card theirMin = theirMin(suit);
        if (topTrickCard != null && topTrickCard.compareInTrick(myMin) > 0 &&
            (totalCards() == 2 || theirMin == null || theirMin.compareInTrick(myMax) > 0)) {
            return cardList.get(cardList.getMaxLessThan(topTrickCard.getRank()));
        }
        CardList.ListData listData = cardList.maxUnwantedTricks(leftHand.list(suit), rightHand.list(suit), 2);
        if (topTrickCard != null && listData.maxTheyStart == 0) {
            int ind = cardList.getMaxLessThan(topTrickCard.getRank());
            if (ind > 0) {
                return cardList.get(ind);
            }
            // if i == 0 then only one my card is smaller, so let's keep it
            if (cardList.size() > 1) {
                if (rightHand.list(suit).size() > 2 &&
                        rightHand.list(suit).getMaxLessThan(myMax.getRank()) == 0) {
                    // there is a sigle card less than my max, let's risk it
                    return myMin;
                }
                return cardList.get(1);
            }
            return myMax;     // all my cards are greater than theirs in the trick
        }

        if (trick.trickCards.size() == 2) {
            // 3rd hand
            if (myMin.compareInTrick(trick.topCard) > 0 ||
                    myMax.compareInTrick(trick.topCard) < 0) {
                // all my cards are either greater than trick or less than trick
                return myMax;
            }
            if (myMin.compareInTrick(trick.topCard) > 0) {
                // all my cards are greater than trick
                return myMax;
            }
            // ♣8JKA ♥J   ♣7Q ♦79X   ♣9X ♥89X
            //              ^          ^   -> 3rd hand, intercept with ♣A, then ♥J and ♣8 ?!
        }

        // 2nd  and 3rd hand
        if (cardList.size() == 2) {
            if (!rightHand.list(suit).isEmpty()) {
                // XK - Q -> X, XK - A -> X
                //                       me:
                // ♦7X ♥7X   ♠JQ ♦9 ♥9   ♦8A ♥8A
                //                  ^
                if (rightHand.list(suit).size() == 1 ||
                        myMin.compareInTrick(theirMin(suit)) > 0 && myMax.compareInTrick(topTrickCard) > 0) {

                    if (rightHand.list(suit).size() > 2 &&
                            rightHand.list(suit).getMaxLessThan(myMin.getRank()) == 0) {
                            // there is a sigle card less than my min, better take this trick
                        return cardList.get(1);
                    }
                    return myMin;
                }
                return myMax;
            }
            if (!leftHand.list(suit).isEmpty()) {
                // XK - Q -> X, XK - A -> X
                if (leftHand.list(suit).size() == 1) {
                    return myMin;
                }
                // XQ - JA -> Q, XQ - JKA -> Q, XQ - JK -> Q
                return myMax;
            }

        }

        // todo
        // 2nd  and 3rd hand
        if (myMin.compareInTrick(topTrickCard) > 0) {
            if (myMin.compareTo(theirMin) < 0) {
                res = myMin;
            } else {
                Card theirMax = theirMax(suit);
                if (theirMax == null) {
                    return myHand.list(suit).first();   // they do not have this suit
                }
                int optimIndex = cardList.getMaxLessThan(theirMax.getRank());
                if (optimIndex == -1) {
                    res = myMax;
                } else {
                    res = cardList.get(optimIndex);
                }
            }
        } else {
            int left = leftHand.list(suit).getMaxLessThan(myMin.getRank());
            if (cardList.size() == 2 && left <= 2 && !rightHand.list(suit).isEmpty()) {
                res = myMax;
            } else {
                if (cardList.size() > 1) {
                    if (rightHand.list(suit).size() > 2 &&
                            rightHand.list(suit).getMaxLessThan(myMin.getRank()) == 0) {
                        // there is a sigle card less than my min
                        if (trick.number == 0) {
                            res = cardList.first();
                        } else {
                            // better take this trick
                            res = cardList.get(1);
                        }
                    }
                }
                if (res == null) {
                    // ♣8JKA ♥J   ♠J ♣7Q ♦79   ♣9X ♥789   -> ♣Q ♣X, should take it, play ♠J and ♣8
                    // instead she plays ♣J, then ♣7 ♣9 ♣8 and gets 4 tricks
                    // on the other hand, for ♣8JKA ♥J   ♠J ♣9Q ♦79   ♣7X ♥789 it does not work
                    res = cardList.get(cardList.getMaxLessThan(topTrickCard.getRank()));
                }
            }
        }

        return res;
    }

/*
    public Card playMisere(Trick trick) {
        if (Config.Bid.BID_MISERE.equals(this.bid)) {
            MisereBot misereBot = new MisereBot(this, this);
            return misereBot.declarerPlayMisere(trick);
        }
        Player fictitiousBot = GameManager.getInstance().getDeclarerFor(this.number);
        MisereBot misereBot = new MisereBot(fictitiousBot, this);
        return misereBot.defenderPlayMisere(trick);
    }
*/

    @Override
    public Config.Bid getBid(Config.Bid minBid, int elderHand) {
        Config.Bid bid = getMaxBid(elderHand);
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

    protected Config.Bid getMaxBid(int elderHand) {
//        boolean _meStart = true;     // game declarer mostly starts tricks
        Card.Suit longestSuit = null;    // no trump
        int maxLen = 0;
        int totalTricks = 0;
        for (CardList cardList : myHand) {
            if (cardList.isEmpty()) {
                continue;
            }
            Card.Suit suit = cardList.first().getSuit();
//            int suitNum = cardList.first().getSuit().getValue();
            CardList.ListData listData = cardList.minWantedTricks(leftHand.list(suit), rightHand.list(suit), elderHand);
            totalTricks += listData.minMeStart + listData.minTheyStart;
            if (cardList.size() > 3 && maxLen < cardList.size()) {
                maxLen = cardList.size();
                longestSuit = cardList.first().getSuit();
            }
        }
        Config.Bid bid = null;
        if (totalTricks < 10) {
            if (evalMisere(elderHand)) {
                bid = Config.Bid.BID_MISERE;
            } else if (totalTricks < 6) {
                // todo: compare all-pass loss vs minimal bid
                bid = Config.Bid.BID_PASS;
            }
        }
        if (bid == null) {
            // convert totalTricks and longestSuit to bid
            char code = Config.NO_TRUMP;
            if (longestSuit != null) {
                code = longestSuit.getCode();
            }
            bid = Config.Bid.fromName("" + totalTricks + code);
            bid = Config.Bid.fromName("" + totalTricks + longestSuit.getCode());
        }
        return bid;
    }

    boolean evalMisere(int elderHand) {
        MisereBot misereBot = getMisereBot(this, this);
        return misereBot.evalMisere(elderHand);
    }

    static class HandResults {
        CardList dropped = new CardList();
        int totalTricks = 0;    // the best
        int expect = Integer.MAX_VALUE;
        CardList.ListData[] allListData = new CardList.ListData[Card.TOTAL_SUITS];
    }
}