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

    protected MisereBot misereBot;

    public Bot(String name, int number) {
        super(name);
        this.number = number;
    }

    public Bot(Player realPlayer) {
        super(realPlayer);
        this.number = realPlayer.getNumber();
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
        CardList otherHand = new CardList(players[1].rightSuits);
        Bot probeBot = new Bot("probe misere", hisCards);

    }
*/

/*
    // to play with open cards
    public void merge(Bot other) {
        boolean left = this.number < other.number;
        int suitNum = -1;
        for (CardList suit : other.mySuits) {
            ++suitNum;
            for (Card card : suit) {
                if (left) {
                    rightSuits[suitNum].remove(card);
                } else {
                    leftSuits[suitNum].remove(card);
                }
            }
            if (left) {
                leftSuits[suitNum] = (CardList) other.mySuits[suitNum].clone();
            } else {
                rightSuits[suitNum] = (CardList) other.mySuits[suitNum].clone();
            }
        }
    }
*/

    @Override
    public void declareRound(Config.Bid minBid, boolean elderHand) {
        SuitResults suitResults = null;
        if (Config.Bid.BID_MISERE.equals(minBid)) {
            suitResults = discardForMisere(elderHand);
        }
        // todo

        discard(suitResults.discarded);
    }

    private Card discardAny(Trick trick) {
        CardList.ListData problemList = null;
        CardList.ListData worstNoProblemList = null;
        Card res = null;

/* debug
        if (number == 0 && trick.number >= 5) {
            DEBUG = DEBUG;
        }
//*/

        for (int i = 0; i < mySuits.length; ++i) {
            CardList suit = mySuits[i];
            if (suit.isEmpty()) {
                continue;
            }
            CardList.ListData listData = suit.getUnwantedTricks(leftSuits[i], rightSuits[i]);
            if (listData.ok1stMove) {
                // no problem
                // select the suit with the most unwanted tricks
                if (worstNoProblemList == null || worstNoProblemList.maxMeStart < listData.maxMeStart) {
                    worstNoProblemList = listData;
                    worstNoProblemList.thisSuit = suit;
                } else if (worstNoProblemList.maxMeStart == listData.maxMeStart) {
                    if (worstNoProblemList.thisSuit.size() > suit.size()) {
                        // select the shortest suit
                        worstNoProblemList = listData;
                        worstNoProblemList.thisSuit = suit;
                    } else if (worstNoProblemList.thisSuit.last().getRank().compare(suit.last().getRank()) < 0) {
                        // select the suit with the card of the highest rank
                        worstNoProblemList = listData;
                        worstNoProblemList.thisSuit = suit;
                    }
                }
                continue;
            } else {
                // problems
                // quick and dirty:
                if (problemList == null || problemList.maxMeStart < listData.maxMeStart) {
                    problemList = listData;
                    problemList.thisSuit = suit;
                }
            }
        }

        CardList probe = null;
        if (worstNoProblemList != null) {
            probe = worstNoProblemList.thisSuit;
        } else if (problemList != null) {
            probe = problemList.thisSuit;
        }

        if (probe != null && probe.size() == 2) {
            // looking for 'пистолет':
            Card myMin = probe.first();
            int suitNum = myMin.getSuit().getValue();

            boolean found = false;
            CardList suit = rightSuits[suitNum];
            if (!suit.isEmpty()) {
                if (myMin.compareInTrick(suit.last()) < 0 &&
                        myMin.compareInTrick(suit.first()) > 0) {
                    found = true;
                }
            }
            suit = leftSuits[suitNum];
            if (!suit.isEmpty()) {
                if (myMin.compareInTrick(suit.last()) < 0 &&
                        myMin.compareInTrick(suit.first()) > 0) {
                    found = true;
                }
            }
            if (found) {
                // discard any other:
                for (CardList _suit : mySuits) {
                    if (!_suit.isEmpty() && _suit != probe) {
                        res = _suit.last();
                        break;
                    }
                }
            }
        }

        if (res == null) {
            if (worstNoProblemList != null) {
                if (worstNoProblemList.thisSuit.last().compareInTrick(theirMin(worstNoProblemList.suitNum)) < 0 &&
                        problemList != null && problemList.thisSuit.size() > 2) {
                    res = problemList.thisSuit.last();
                } else {
                    res = worstNoProblemList.thisSuit.last();
                }
            } else if (problemList != null) {
                res = problemList.thisSuit.last();
            } else {
                // any:
                for (CardList suit : mySuits) {
                    if (!suit.isEmpty()) {
                        res = suit.last();
                        break;
                    }
                }
            }
        }
        return res;
    }

    @Override
    public void respondOnRoundDeclaration(Config.Bid bid, int elderHand) {
        if (Config.Bid.BID_MISERE.equals(bid)) {
            Player[] players = GameManager.getInstance().players;
            // kludgy way to avoid dependencies on pref package
            int botNum = -1, humanNum = -1;
            for (int i = 0; i < players.length; ++i) {
                if (i == number) {
                    continue;
                }
                if (players[i] instanceof Bot) {
                    botNum = i;
                } else {
                    humanNum = i;
                }
            }
            if (humanNum == -1) {
                // test mode
                humanNum = 0;
            }
            MisereBot misereBot = new MisereBot(players[humanNum]);
            int k = (humanNum + 1) % players.length;
            Player left = players[k];
            if (k == humanNum) {
                left = misereBot;
            }
            k = (humanNum + 2) % players.length;
            Player right = players[k];
            if (k == humanNum) {
                right = misereBot;
            }
            misereBot.createPlan(left, right, elderHand == misereBot.number);
            // todo
        }
        // todo
    }

    @Override
    public Card play(Trick trick) {
        // todo
        return playAllPass(trick);
    }

    Card playAllPass1stHand(Trick trick) {
        Card problems = null;
        Card anycard = null;
        CardList.ListData noProblemSuit = new CardList.ListData();
        noProblemSuit.maxMeStart = Integer.MAX_VALUE;
/* debug
        if (number == 1 && trick.number >= 3) {
            DEBUG = DEBUG;
        }
//*/
        // ♣8JQK ♦89 ♥79   ♠A ♣79 ♦XJKA ♥8   ♠J ♣XA ♦7 ♥XJQA 1 - must: ♠A, ♦X and ♦XJ?
        // ♠7 ♦9XK   ♣A ♥9JK   ♠9Q ♦8 ♥7
        // todo                    ^ would be the best move, but it plays ♠Q
        for (int i = 0; i < this.mySuits.length; ++i) {
            CardList suit = this.mySuits[i];
            if (suit.isEmpty()) {
                continue;
            }
            anycard = suit.last();
            int suitNum = suit.first().getSuit().getValue();
            // 'козырять' не надо
            if (leftSuits[suitNum].isEmpty() && rightSuits[suitNum].isEmpty()) {
                continue;
            }

            CardList.ListData listData = suit.getUnwantedTricks(leftSuits[i], rightSuits[i]);
            if (listData.ok1stMove) {
                // no problems
                int _suitNum = -1;
                int noProblemSuitSize = 0;
                if (noProblemSuit.suitNum != -1) {
                    _suitNum = noProblemSuit.suitNum;
                    noProblemSuitSize = mySuits[_suitNum].size();
                }

                if (listData.maxMeStart == 0) {
                    CardList[] checkLists = null;
                    if (leftSuits[suitNum].isEmpty()) {
                        checkLists = rightSuits;
                    } else if (rightSuits[suitNum].isEmpty()) {
                        checkLists = leftSuits;
                    }

                    if (checkLists == null) {
                        if (noProblemSuit.maxMeStart == Integer.MAX_VALUE ||
                                noProblemSuit.cardsLeft > listData.cardsLeft) {
                            noProblemSuit = listData;
                        }
                    } else {
                        CardList dummy = new CardList();
                        for (int j = 0; j < checkLists.length; ++j) {
                            if (j == suitNum) {
                                continue;
                            }
                            CardList suit1 = this.mySuits[j];
                            CardList.ListData listData1 =
                                suit1.maxUnwantedTricks(dummy, checkLists[j],false);
                            if (listData1.maxTheyStart > 0) {
                                //?? I hope I will will shove suit.first() with the next trick
                                if (suit1.size() > 1 && suit1.first().compareInTrick(theirMin(j)) < 0) {
                                    return suit1.get(1);
                                }
                                if (suit1.size() > 1 && suit1.getMaxLessThan(suit1.get(1).getRank()) == 0) {
                                    return suit1.get(1);
                                }
                                return suit1.last();
                            }
                        }
                        // todo: check if she can shove a trick to the 3rd player, and that one can get me
                        return suit.first();
                    }
                } else if (noProblemSuit.maxMeStart == 0 ||
                        noProblemSuit.maxMeStart > listData.maxMeStart && noProblemSuitSize < suit.size() ||
                        suit.size() >= 4) {
                    if (!(listData.ok1stMove && listData.maxMeStart > 0 && listData.cardsLeft >= 0 &&
                           noProblemSuit.ok1stMove && noProblemSuit.maxTheyStart == 0)) {
                        noProblemSuit = listData;
                    }
                } else { // if (noProblemSuit.maxMeStart == listData.maxMeStart) {
                    // compare number of smaller cards
                    int prev = rightSuits[_suitNum].getMaxLessThan(mySuits[_suitNum].first().getRank());
                    int curr = rightSuits[suitNum].getMaxLessThan(mySuits[suitNum].first().getRank());
                    if (curr > prev) {
                        noProblemSuit = listData;
                    } else if (suit.size() > 1 && suit.first().compareInTrick(theirMin(suitNum)) < 0) {
                        noProblemSuit = listData;
                    }
                }
            } else {
                // problems
                Card myMax = absMax(suitNum);
                if (myMax != null) {
                    return myMax;
                }

                if (suit.size() > 1) {
                    if (!listData.good && listData.maxMeStart > listData.maxTheyStart) {
                        if (rightSuits[suitNum].size() > 2 &&
                            rightSuits[suitNum].getMaxLessThan(suit.first().getRank()) == 0) {
                            // there is a sigle card less than my min, so better take this trick
                            problems = suit.get(1);
                        } else {
                            problems = suit.first();     // give them a chance to take it
                        }
                    } else {
                        problems = suit.get(1);     // ?? second from bottom
                    }
                } else {
                    problems = suit.first();
                }
            }
        }

        Card res;
        if (noProblemSuit.suitNum != -1) {
            int suitNum = noProblemSuit.suitNum;
            CardList suit = mySuits[suitNum];
            if (suit.size() > 1 && theirMin(suitNum).compareInTrick(suit.first()) < 0) {
                res = suit.get(1);  // further check?
            } else {
                res = mySuits[noProblemSuit.suitNum].last();
            }
        } else if (problems != null) {
            res = problems;
        } else {
            res = anycard;
        }
        return res;
    }

    Card absMax(int suitNum) {
        CardList suit = mySuits[suitNum];

        Card.Rank theirMax = Card.Rank.SIX;
        if (!rightSuits[suitNum].isEmpty()) {
            Card.Rank r = rightSuits[suitNum].last().getRank();
            if (theirMax.compare((r)) < 0) {
                theirMax = r;
            }
        }
        if (!leftSuits[suitNum].isEmpty()) {
            Card.Rank r = leftSuits[suitNum].last().getRank();
            if (theirMax.compare((r)) < 0) {
                theirMax = r;
            }
        }
        if (suit.first().getRank().compare(theirMax) > 0) {
            // all my cards are greater than theirs
            return suit.last();
        }
        return null;
    }

    Card theirMin(int suitNum) {
        Card res = null;
        int theirMin = Integer.MAX_VALUE;
        if (!rightSuits[suitNum].isEmpty()) {
            Card card = rightSuits[suitNum].first();
            int r = card.getRank().getValue();
            if (theirMin > r) {
                theirMin = r;
                res = card;
            }
        }
        if (!leftSuits[suitNum].isEmpty()) {
            Card card = leftSuits[suitNum].first();
            int r = card.getRank().getValue();
            if (theirMin > r) {
                res = card;
            }
        }
        return res;
    }

    // https://review-pref.ru/school/147/111/
    // all-pass: https://review-pref.ru/school/145/17/
    public Card playAllPass(Trick trick) {
        Card res = null;
        if (trick.startingSuit == null) {
            return playAllPass1stHand(trick);
        }

        int suitNum = trick.startingSuit.getValue();
        CardList suit = this.mySuits[suitNum];
        if (suit.isEmpty()) {
            return discardAny(trick);
        }

        Card myMin = suit.first();
        if (suit.size() == 1) {
            return myMin;
        }

        Card myMax = suit.last();
        Card topTrickCard = trick.topCard;
/* debug
        if (number == 0 && trick.number >= 5) {
            DEBUG = DEBUG;
        }
//*/

        Card theirMin = theirMin(suitNum);
        if (topTrickCard != null && topTrickCard.compareInTrick(myMin) > 0 &&
            (totalCards() == 2 || theirMin == null || theirMin.compareInTrick(myMax) > 0)) {
            return myMin;
        }
        CardList.ListData listData = suit.maxUnwantedTricks(leftSuits[suitNum], rightSuits[suitNum], false);
        if (topTrickCard != null && listData.maxTheyStart == 0) {
            int ind = suit.getMaxLessThan(topTrickCard.getRank());
            if (ind > 0) {
                return suit.get(ind);
            }
            // if i == 0 then only one my card is smaller, so let's keep it
            if (suit.size() > 1) {
                if (rightSuits[suitNum].size() > 2 &&
                        rightSuits[suitNum].getMaxLessThan(myMax.getRank()) == 0) {
                    // there is a sigle card less than my max, let's risk it
                    return myMin;
                }
                return suit.get(1);
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
        if (suit.size() == 2) {
            if (!rightSuits[suitNum].isEmpty()) {
                // XK - Q -> X, XK - A -> X
                //                       me:
                // ♦7X ♥7X   ♠JQ ♦9 ♥9   ♦8A ♥8A
                //                  ^
                if (rightSuits[suitNum].size() == 1 ||
                        myMin.compareInTrick(theirMin(suitNum)) > 0 && myMax.compareInTrick(topTrickCard) > 0) {

                    if (rightSuits[suitNum].size() > 2 &&
                            rightSuits[suitNum].getMaxLessThan(myMin.getRank()) == 0) {
                            // there is a sigle card less than my min, better take this trick
                        return suit.get(1);
                    }
                    return myMin;
                }
                return myMax;
            }
            if (!leftSuits[suitNum].isEmpty()) {
                // XK - Q -> X, XK - A -> X
                if (leftSuits[suitNum].size() == 1) {
                    return myMin;
                }
                // XQ - JA -> Q, XQ - JKA -> Q, XQ - JK -> Q
                return myMax;
            }

        }

        // todo
        // 2nd  and 3rd hand
        if (myMin.compareInTrick(topTrickCard) > 0) {
            Card leftMin = null;
            if (!leftSuits[suitNum].isEmpty()) {
                leftMin = leftSuits[suitNum].first();
            }
            Card rightMin = null;
            if (!rightSuits[suitNum].isEmpty()) {
                rightMin = rightSuits[suitNum].first();
            }
            if (leftMin == null && rightMin == null ||
                myMin.compareInTrick(leftMin) < 0 || myMin.compareInTrick(rightMin) < 0) {
                res = myMin;    // give an opponent a chance to take the trick
            } else {
                res = myMax;
            }
        } else {
            int left = leftSuits[suitNum].getMaxLessThan(myMin.getRank());
            if (suit.size() == 2 && left <= 2 && !rightSuits[suitNum].isEmpty()) {
                res = myMax;
            } else {
                if (suit.size() > 1) {
                    if (rightSuits[suitNum].size() > 2 &&
                            rightSuits[suitNum].getMaxLessThan(myMin.getRank()) == 0) {
                        // there is a sigle card less than my min, better take this trick
                        res = suit.get(1);
                    }
                }
                if (res == null) {
                    // ♣8JKA ♥J   ♠J ♣7Q ♦79   ♣9X ♥789   -> ♣Q ♣X, should take it, play ♠J and ♣8
                    // instead she plays ♣J, then ♣7 ♣9 ♣8 and gets 4 tricks
                    // on the other hand, for ♣8JKA ♥J   ♠J ♣9Q ♦79   ♣7X ♥789 it does not work
                    res = suit.get(suit.getMaxLessThan(topTrickCard.getRank()));
                }
            }
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
            int suitNum = suit.first().getSuit().getValue();
            CardList.ListData listData = suit.minWantedTricks(leftSuits[suitNum], rightSuits[suitNum], meStart);
            totalTricks += listData.minMeStart + listData.minTheyStart;
            if (suit.size() > 3 && maxLen < suit.size()) {
                maxLen = suit.size();
                longestSuit = suit.first().getSuit();
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
            bid = Config.Bid.fromName("" + totalTricks + longestSuit.getCode());
        }
        return bid;
    }

    boolean evalMisere(boolean meStart) {
        int maxMeStart = 0, maxTheyStart = 0;
        CardList badSuit = null;
        for (CardList suit : mySuits) {
            if (suit.isEmpty()) {
                continue;
            }
            int suitNum = suit.first().getSuit().getValue();
            CardList.ListData listData;
            listData = suit.maxUnwantedTricks(leftSuits[suitNum], rightSuits[suitNum], meStart);
            maxMeStart += listData.maxMeStart;
            if (meStart && listData.maxMeStart == 0) {
                meStart = false;
                listData = suit.maxUnwantedTricks(leftSuits[suitNum], rightSuits[suitNum], meStart);
                maxTheyStart += listData.maxTheyStart;
            }
            int maxTricks = listData.maxMeStart + listData.maxTheyStart;
            if (maxTricks > 0) {
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
            int suitNum = suit.first().getSuit().getValue();
            CardList.ListData listData = suit.maxUnwantedTricks(leftSuits[suitNum], rightSuits[suitNum], meStart);
            int tricks = listData.maxMeStart + listData.maxTheyStart;
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