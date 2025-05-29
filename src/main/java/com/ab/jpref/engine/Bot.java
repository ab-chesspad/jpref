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

    @Override
    public void declareRound(Config.Bid minBid, boolean elderHand) {
        HandResults handResults = null;
        if (Config.Bid.BID_MISERE.equals(minBid)) {
            Trick trick = new Trick();
            // elderHand??
            handResults = discardForMisere(trick);
        }
        // todo

        discard(handResults.discarded);
    }

    private Card discardAny(Trick trick) {
        CardList.ListData problemList = null;
        CardList.ListData noProblemList = null;
        Card res = null;

/* debug
        if (number == 0 && trick.number >= 1) {
            DEBUG = DEBUG;
        }
//*/

        for (int i = 0; i < mySuits.length; ++i) {
            CardList suit = mySuits[i];
            if (suit.isEmpty()) {
                continue;
            }
            if (theirMin(i) == null) {
                continue;
            }
            CardList.ListData listData = suit.getUnwantedTricks(leftSuits[i], rightSuits[i]);
            boolean ok1stMove = listData.maxMeStart == listData.maxTheyStart;
            if (ok1stMove) {
                // no problem
                // select the suit with the most unwanted tricks
                if (noProblemList == null || noProblemList.maxMeStart < listData.maxMeStart) {
                    noProblemList = listData;
                } else if (noProblemList.maxMeStart == listData.maxMeStart) {
                    if (noProblemList.thisSuit.size() > suit.size()) {
                        // select the shortest suit
                        noProblemList = listData;
                    } else if (noProblemList.thisSuit.last().getRank().compare(suit.last().getRank()) < 0) {
                        // select the suit with the card of the highest rank
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
            int suitNum = myMin.getSuit().getValue();

            boolean found = false;
            CardList suit = rightSuits[suitNum];
            if (probe.size() <= suit.size() && probe.last().compareTo(suit.get(probe.size() - 1)) > 0) {
                // ok to discard it
            } else if (!suit.isEmpty()) {
                if (myMin.compareInTrick(suit.last()) < 0 &&
                        myMin.compareInTrick(suit.first()) > 0) {
                    found = true;
                }
            }
            suit = leftSuits[suitNum];
            if (probe.size() <= suit.size() && probe.last().compareTo(suit.get(probe.size() - 1)) > 0) {
                // ok to discard it
            } else if (!suit.isEmpty()) {
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
            if (noProblemList != null) {
                if (noProblemList.thisSuit.last().compareInTrick(theirMin(noProblemList.suitNum)) < 0 &&
                        problemList != null && problemList.thisSuit.size() > 2) {
                    res = problemList.thisSuit.last();
                } else {
                    res = noProblemList.thisSuit.last();
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

/*
    @Override
    public void respondOnRoundDeclaration(Config.Bid bid, Trick trick) {
        if (Config.Bid.BID_MISERE.equals(bid)) {
            createPlanForMisere(trick);
            // todo
        }
        // todo
    }

    void createPlanForMisere(Trick trick) {
        GameManager gameManager = GameManager.getInstance();

        // 0. copy bots, create MisereBot
        int declarerNum = gameManager.declarer.number;
//        MisereBot misereBot = new MisereBot(gameManager.declarer);
        Player[] players = new Player[GameManager.NUMBER_OF_PLAYERS];
//        Set<Card> declarerCards = new HashSet<>(CardList.getDeck());
        for (int i = 0; i < players.length; ++i) {
            if (i != declarerNum) {
                players[i] = new Bot(gameManager.players[i]);
            }
        }
        Bot misereBot = new Bot("misere bot", GameManager.getInstance().declarer.getNumber());
        Set<Card> declarerCards = declarerCards();
        declarerCards.remove(trick.trickCards.get(0));
        misereBot.setHand(declarerCards);
        players[declarerNum] = misereBot;

        // 1. let's try to guess declarer's discards
        HandResults handResults = misereBot.discardForMisere(trick);
        misereBot.discard(handResults.discarded);



//        misereBot.declareRound(Config.Bid.BID_MISERE, trick.startedBy == declarerNum);
//        HandResults handResults = misereBot.misereTricks(trick.startedBy == declarerNum);

    }
*/

    @Override
    public Card play(Trick trick) {
        if (Config.Bid.BID_MISERE.equals(trick.minBid)) {
            return playMisere(trick);
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
        if (trick.number >= 6 && number == 2) {
            DEBUG = DEBUG;
        }
//*/
        // ♣8JQK ♦89 ♥79   ♠A ♣79 ♦XJKA ♥8   ♠J ♣XA ♦7 ♥XJQA 1 - must: ♠A, ♦X and ♦XJ?
        // ♠7 ♦9XK   ♣A ♥9JK   ♠9Q ♦8 ♥7
        // todo: test-2 plays ♠Q, but the best move is ♥7
        for (int i = 0; i < this.mySuits.length; ++i) {
            CardList suit = this.mySuits[i];
            if (suit.isEmpty()) {
                continue;
            }
            anycard = suit.last();
            int suitNum = suit.first().getSuit().getValue();
            if (theirMin(suitNum) == null) {
                continue;       // 'козырять' не надо
            }

            CardList.ListData listData = suit.getUnwantedTricks(leftSuits[i], rightSuits[i]);
            boolean ok1stMove = listData.maxMeStart == listData.maxTheyStart;
            if (ok1stMove) {
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
                        if (noProblemSuit.suitNum < 0 ||
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
                            if (suit1.isEmpty()) {
                                continue;
                            }
                            if (suit1.first().compareInTrick(theirMin(j)) < 0) {
                                continue; // no point to start with this suit, I'll have a chance to do it later
                            }

                            CardList.ListData listData1 =
                                suit1.maxUnwantedTricks(dummy, checkLists[j],2);
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
                        noProblemSuit.maxMeStart > listData.maxMeStart && noProblemSuitSize < suit.size()) {
                    if (!(listData.maxMeStart > 0 && listData.cardsLeft >= 0
                        && noProblemSuit.suitNum >= 0) || noProblemSuit.maxMeStart == 0) {
                        noProblemSuit = listData;
                    }
                } else if (listData.maxMeStart > 0 && suit.size() >= 4) {
                    noProblemSuit = listData;
                } else { // if (noProblemSuit.maxMeStart == listData.maxMeStart) {
                    // compare number of smaller cards
                    int prev = rightSuits[_suitNum].getMaxLessThan(mySuits[_suitNum].first().getRank());
                    int curr = rightSuits[suitNum].getMaxLessThan(mySuits[suitNum].first().getRank());
                    if (curr > prev) {
                        noProblemSuit = listData;
                    } else if (suit.size() > 1 && suit.first().compareInTrick(theirMin(suitNum)) < 0) {
                        noProblemSuit = listData;
                    } if (curr == prev && suit.size() > mySuits[_suitNum].size()) {
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
        if (suit.size() > 1) {
            res = suit.get(1);  // further check?
        } else {
            res = suit.first();
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

    Card theirMax(int suitNum) {
        Card res = null;
        int theirMax = 0;
        if (!rightSuits[suitNum].isEmpty()) {
            Card card = rightSuits[suitNum].last();
            int r = card.getRank().getValue();
            if (theirMax < r) {
                theirMax = r;
                res = card;
            }
        }
        if (!leftSuits[suitNum].isEmpty()) {
            Card card = leftSuits[suitNum].last();
            int r = card.getRank().getValue();
            if (theirMax < r) {
                theirMax = r;
                res = card;
            }
        }
        return res;
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
        if (trick.number >= 7 && number == 0) {
            DEBUG = DEBUG;
        }
//*/

        Card theirMin = theirMin(suitNum);
        if (topTrickCard != null && topTrickCard.compareInTrick(myMin) > 0 &&
            (totalCards() == 2 || theirMin == null || theirMin.compareInTrick(myMax) > 0)) {
            return suit.get(suit.getMaxLessThan(topTrickCard.getRank()));
        }
        CardList.ListData listData = suit.maxUnwantedTricks(leftSuits[suitNum], rightSuits[suitNum], 2);
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
            if (myMin.compareTo(theirMin) < 0) {
                res = myMin;
            } else {
                Card theirMax = theirMax(suitNum);
                int optimIndex = suit.getMaxLessThan(theirMax.getRank());
                if (optimIndex == -1) {
                    res = myMax;
                } else {
                    res = suit.get(optimIndex);
                }
            }
        } else {
            int left = leftSuits[suitNum].getMaxLessThan(myMin.getRank());
            if (suit.size() == 2 && left <= 2 && !rightSuits[suitNum].isEmpty()) {
                res = myMax;
            } else {
                if (suit.size() > 1) {
                    if (rightSuits[suitNum].size() > 2 &&
                            rightSuits[suitNum].getMaxLessThan(myMin.getRank()) == 0) {
                        // there is a sigle card less than my min
                        if (trick.number == 0) {
                            res = suit.first();
                        } else {
                            // better take this trick
                            res = suit.get(1);
                        }
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

    public Card playMisere(Trick trick) {
        if (Config.Bid.BID_MISERE.equals(this.bid)) {
            return declarerPlayMisere(trick);
        }
        return defenderPlayMisere(trick);
    }

    Card declarerPlayMisere(Trick trick) {
        HandResults handResults = misereTricks(trick.startingSuit == null);
        CardList cleanSuit = null;
        if (trick.startingSuit == null) {
            for (CardList.ListData listData : handResults.allListData) {
                CardList suit = mySuits[listData.suitNum];
                if (suit.isEmpty()) {
                    continue;
                }
                if (suit.size() == 1) {
                    return suit.last();
                }
                if (listData.maxMeStart == 0) {
                    cleanSuit = suit;
                }
            }
            if (cleanSuit != null) {
                return cleanSuit.last();
            }
            // todo: find move!
        } else {
            Card topCard = trick.topCard;
            CardList suit = mySuits[topCard.getSuit().getValue()];
            if (suit.isEmpty()) {
                // todo: discard for misere
            } else {
                return suit.get(suit.getMaxLessThan(topCard.getRank()));
            }
        }
        return null;
    }

    Card defenderPlayMisere(Trick trick) {
//        HandResults handResults = misereTricks(trick.startingSuit == null);
        Bot misereBot = new Bot("misereBot" + trick.declarerNum, trick.declarerNum);
        if ((trick.declarerNum + 1) % GameManager.NUMBER_OF_PLAYERS == number) {
            misereBot.mySuits = rightSuits;
            misereBot.leftSuits = mySuits;
            misereBot.rightSuits = leftSuits;
        } else {
            misereBot.mySuits = leftSuits;
            misereBot.leftSuits = rightSuits;
            misereBot.rightSuits = mySuits;
        }
        HandResults handResults = misereBot.discardForMisere(trick);
        misereBot.discard(handResults.discarded);
        if (trick.startingSuit == null) {
            // playing elderhand
/*
            // 0. find suits that can be caught at all
            List<CardList.ListData> holesTheyStart = new LinkedList<>();
            for (int i = 0; i < Card.Suit.values().length - 1; ++i) {
                CardList suit = misereBot.mySuits[i];
                CardList otherSuits = new CardList(misereBot.leftSuits[i]);
                otherSuits.addAll(misereBot.rightSuits[i]);
                Collections.sort(otherSuits);
                CardList.ListData listData = suit.maxUnwantedTricks(new CardList(), otherSuits, false);
                 if (listData.maxTheyStart > 0) {
                    holesTheyStart.add(listData);
                }
            }
*/
            // find suits that can be caught given the current hands
            // 1. 1x - 89; 78Q - 9xJ; 789A - xJQK
            // 2. 1x - 8J, 9; 78Q - 9KA, xJ; 78Q - 9xK, JA; 78K - 9QA, xJ
            // 3. 8 - 7; etc.
            Card anyCard = null;
            CardList dummy = new CardList();
            List<CardList.ListData> holes = new LinkedList<>();
            for (int i = 0; i < Card.Suit.values().length - 1; ++i) {
                CardList suit = misereBot.mySuits[i];
                if (suit.isEmpty()) {
                    continue;
                }
                anyCard = suit.first();
                CardList.ListData theyStartlistData = suit.maxUnwantedTricks(dummy, misereBot.rightSuits[i], false);
                if (theyStartlistData.maxTheyStart > 0) {
                    holes.add(theyStartlistData);
                } else if (!misereBot.leftSuits[i].isEmpty()) {
                    CardList.ListData meStartlistData =
                        suit.maxUnwantedTricks(misereBot.leftSuits[i], misereBot.rightSuits[i], true);
                    if (meStartlistData.maxMeStart > 0) {
                        holes.add(meStartlistData);
                    }
                }
            }
            Logger.printf("%d suit(s) with holes\n", holes.size());
            if (holes.isEmpty()) {
                // clean misère
                Logger.printf("bot %d - clean misere\n", number);
                return anyCard;
            }


        } else {
        }
        return null;
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
                HandResults handResults = new Bot("probe" + card + c, probeHand).misereTricks(meStart);
                if (handResults.totalTricks == 0) {
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

    HandResults misereTricks(boolean meStart) {
        HandResults handResults = new HandResults();
        int maxTricks = -1;
        int eval = 0;
        for (CardList suit : mySuits) {
            if (suit.isEmpty()) {
                continue;
            }
            int suitNum = suit.first().getSuit().getValue();
            CardList.ListData listData = suit.maxUnwantedTricks(leftSuits[suitNum], rightSuits[suitNum], meStart);
            handResults.allListData[suitNum] = listData;
            int tricks = listData.maxMeStart + listData.maxTheyStart;   // only one can be > 0
            if (maxTricks < tricks || eval < listData.misereEval) {
                eval = listData.misereEval;
                handResults.listData = listData;
                maxTricks = tricks;
            }
            handResults.totalTricks += tricks;
        }
        return handResults;
    }

    // select 2 cards to discard to minimize tricks and their probability
    HandResults discardForMisere(Trick trick) {
        // optimistically we assume that there will be a way to turn elderHand to false
        boolean _elderHand = false;
        CardList myHand = new CardList();
        for (CardList suit : mySuits) {
            myHand.addAll(suit);
        }
//        Card firstMoveCard = trick.trickCards.get(0);
        HandResults handResults = new HandResults();
        CardList probeSet1 = new CardList(myHand);
        Collections.sort(myHand);
probes:
        for (Card card1 : myHand) {
/*
            if (card1.equals(firstMoveCard)) {
                // this is MisereBot and declarer started trick with this card
                continue;
            }
*/
            probeSet1.remove(card1);
            Collections.sort(probeSet1);
            for (Card card2 : probeSet1) {
/*
                if (card2.equals(firstMoveCard)) {
                    // this is MisereBot and declarer started trick with this card
                    continue;
                }
*/
                Set<Card> probeHand = new HashSet<>(myHand);
                probeHand.remove(card1);
                probeHand.remove(card2);
                Bot bot = new Bot("test" + card1 + card2, probeHand);
                Logger.printf(DEBUG, "probe discard %s, %s, bot %s\n",
                    card1, card2, bot.toString());
                if (card1.toString().equals("♥Q") && card2.toString().equals("♥K")) {
                    int i = 0;
                }
                HandResults _handResults = bot.misereTricks(_elderHand);
                if (_handResults.totalTricks == 0 || handResults.eval > _handResults.listData.misereEval) {
                    handResults = _handResults;
                    handResults.eval = handResults.listData.misereEval;
                    handResults.discarded.clear();
                    handResults.discarded.add(card1);
                    handResults.discarded.add(card2);
                    Logger.printf(DEBUG, "discard %s, %s, tricks %d, eval=%d\n",
                        card1, card2, handResults.totalTricks, handResults.eval);
                    if (handResults.totalTricks == 0) {
                        break probes;
                    }
                }
            }
        }
        return handResults;
    }

    class HandResults {
        CardList discarded = new CardList();
        int totalTricks = 0;    // the best
        // the worst:
//        int eval = 0;
        int eval = Integer.MAX_VALUE;
        CardList.ListData listData = new CardList.ListData(null);
        CardList.ListData[] allListData = new CardList.ListData[Card.Suit.values().length - 1];
    }
}