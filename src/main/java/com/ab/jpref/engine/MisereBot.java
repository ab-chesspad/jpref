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
 * Created: 4/6/25
 */
/*  Limitations/things to do:
1. Defenders are trying to shove only 1 trick, not as many tricks as possible.
2. Declarer needs to see if the misere can be caught to intercept and take the trick beforehand.
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.Hand;
import com.ab.jpref.config.Config;
import com.ab.util.Logger;
import com.ab.util.Util;

import java.util.*;

public class MisereBot extends Bot {
//    public static DeclarerDrop declarerDrop = DeclarerDrop.First;
    public static DeclarerDrop declarerDrop = DeclarerDrop.Last;
    public static boolean DEBUG_LOG = false;
    public static CardList debugDrop = null;

    static CommonDelayedDropData commonDelayedDropData;
    Bot realPlayer;
    Trick trick;
    List<CardList.ListData> holes = new LinkedList<>();
    private int elderhand;

    enum DeclarerDrop {
        First,
        Last,
        Random
    }

    public MisereBot(Player fictitiousBot, Bot realPlayer) {
        super(fictitiousBot);
        if (realPlayer == null) {
            return;
        }
        this.realPlayer = realPlayer;
        elderhand = leftHand.equals(realPlayer.myHand) ? 1 : 2;
        if (fictitiousBot != realPlayer) {
            HandResults handResults = this.dropForMisere();
            this.drop(handResults.dropped);
        }
    }

/*
    public MisereBot(String name, Collection<Card> cards) {
        super(name, cards);
    }
*/

    boolean evalMisere(int elderHand) {
        CardList badSuit = null;
        for (CardList cardList : myHand) {
            if (cardList.isEmpty()) {
                continue;
            }
            Card.Suit suit = cardList.first().getSuit();
            // todo: analyse elderHand
            boolean meStart = false;
            CardList.ListData listData = cardList.maxUnwantedTricks(leftHand.list(suit), rightHand.list(suit), meStart);
/*
            boolean meStart = elderHand == this.number;
            CardList.ListData listData = cardList.maxUnwantedTricks(leftHand.list(suit), rightHand.list(suit), meStart);
            if (meStart && listData.maxMeStart == 0) {
                meStart = false;
                listData = cardList.maxUnwantedTricks(leftHand.list(suit), rightHand.list(suit), meStart);
            }
*/
            int maxTricks = listData.maxMeStart + listData.maxTheyStart;
            if (maxTricks > 0) {
                badSuit = cardList;
            }
        }

        if (badSuit == null) {
            // let's go for it even if we have no good starting move
            return true;
        }

        // applying 'the rule of 7 cards'
        // https://gambiter.ru/pref/mizer-preferans.html
        // brute force
        Set<Card> mySet = new HashSet<>();
        Set<Card> talonCandidates = new HashSet<>(CardList.getDeck());
        for (CardList cardList : myHand) {
            mySet.addAll(cardList);
            talonCandidates.removeAll(cardList);
        }

        Logger.printf(DEBUG_LOG,"good from talon:\n");
        int goodCards = 0;
        for (Card card : talonCandidates) {
            Set<Card> probeHand = new HashSet<>(mySet);
            probeHand.add(card);
            for (Card c : mySet) {
                probeHand.remove(c);
                Bot bot = new Bot("test", probeHand);
                MisereBot probeBot = new MisereBot(bot, bot);
                HandResults handResults = probeBot.misereTricks(elderHand);
                Logger.printf(DEBUG_LOG, "probe %s -> %d tricks\n", bot.toColorString(), handResults.totalTricks);
                if (handResults.totalTricks == 0) {
                    ++goodCards;
                    Logger.printf(DEBUG_LOG, "talon %s, drop %s\n", card, c);
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

    HandResults dropForMisere() {
        HandResults handResults = new HandResults();
        handResults.expect = Integer.MAX_VALUE;
        if (debugDrop != null) {
            // todo! mockito
            handResults.dropped = debugDrop;
            return  handResults;
        }
        // optimistically we assume that there will be a way to turn elderHand to false
        int _elderHand = 2;
        CardList myList = new CardList();
        for (CardList cardList : myHand) {
            myList.addAll(cardList);
        }
        CardList cardList = new CardList(myList);
        Collections.sort(myList);
probes:
        for (Card card1 : myList) {
            cardList.remove(card1);
            Collections.sort(cardList);
            for (Card card2 : cardList) {
                Set<Card> probeHand = new HashSet<>(myList);
                probeHand.remove(card1);
                probeHand.remove(card2);
                Bot _bot = new Bot("test" + card1 + card2, probeHand);
                MisereBot bot = new MisereBot(_bot, _bot);
                HandResults _handResults = bot.misereTricks(_elderHand);
                Logger.printf(DEBUG_LOG, "probe drop %s, %s, %s, tricks %d, eval=%d\n",
                    card1, card2, bot.toColorString(), _handResults.totalTricks, _handResults.expect);
                if (handResults.expect > _handResults.expect) {
                    handResults = _handResults;
                    handResults.dropped.clear();
                    handResults.dropped.add(card1);
                    handResults.dropped.add(card2);
                    if (handResults.totalTricks == 0) {
                        break probes;
                    }
                }
            }
        }
        return handResults;
    }

    HandResults misereTricks(int elderHand) {
        HandResults handResults = new HandResults();
        handResults.expect = 0;
        for (CardList cardList : myHand) {
            if (cardList.isEmpty()) {
                continue;
            }
            Card.Suit suit = cardList.first().getSuit();
//            boolean meStart = elderHand == number;
            boolean meStart = false;
            CardList.ListData listData = cardList.maxUnwantedTricks(leftHand.list(suit), rightHand.list(suit), meStart);
            handResults.allListData[suit.getValue()] = listData;
//            int tricks = listData.maxMeStart + listData.maxTheyStart;   // only one can be > 0
            int tricks = listData.maxTheyStart;   // only one can be > 0
            handResults.expect += tricks * listData.misereEval;
            handResults.totalTricks += tricks;
        }
        return handResults;
    }

    public Card play(Trick trick) {
        this.trick = trick;
        // holes:
        // a. 7X - 89; 78Q - 9XJ; 789A - XJQK
        // b. 7X - 8J, 9; 78Q - 9KA, XJ; 78Q - 9XK, JA; 78K - 9QA, XJ
        // c. 8 - 7; etc.

        // find suits that can be caught at all
        for (Card.Suit suit : Card.Suit.values()) {
/* debug
            if (trick.number >= 5) {
                DEBUG_LOG = DEBUG_LOG;
            }
//*/
            CardList.ListData listData = maxUnwantedTricks(myHand.list(suit), leftHand.list(suit), rightHand.list(suit));
            if (listData.maxTheyStart > 0) {
                holes.add(listData);
                if (trick.startingSuit == null) {
                    // log it once per trick
                    Logger.printf(DEBUG_LOG, "hole %s\n", listData.thisSuit);
                }
            }
        }

        if (commonDelayedDropData.initHoles < 0) {
            commonDelayedDropData.initHoles = holes.size();
        }

//        Logger.printf(DEBUG_LOG, "holes %d\n", holes.size());
        if (Config.Bid.BID_MISERE.equals(realPlayer.bid)) {
            return declarerPlayMisere();
        }
        return defenderPlayMisere();
    }

    static CardList.ListData maxUnwantedTricks(CardList mySuit, CardList leftSuit, CardList rightSuit) {
        final CardList dummy = new CardList();
        Set<Card> cards = new HashSet<>(leftSuit);
        cards.addAll(rightSuit);
        CardList otherSuits = new CardList(cards);
        Collections.sort(otherSuits);
        CardList.ListData listData = mySuit.maxUnwantedTricks(dummy, otherSuits, 2);
        Card myMin = mySuit.first();
        if (listData.maxTheyStart == 1 &&
                myMin.compareInTrick(leftSuit.first()) < 0 && myMin.compareInTrick(rightSuit.first()) < 0 &&
                leftSuit.size() == 1 && rightSuit.size() >= 2 &&
                mySuit.get(1).compareInTrick(rightSuit.get(1)) < 0) {
            // e.g. 7X  8  9JQKA
            listData.maxTheyStart = 0;
        }
        return listData;
    }

    Card passElderhand(int elderhand, Card.Suit holeSuit, Hand leftHand, Hand rightHand) {
        // find suit to pass elderhand
        for (Card.Suit j : Card.Suit.values()) {
            if (j.equals(holeSuit) ||
                rightHand.list(j).isEmpty() || leftHand.list(j).isEmpty()) {
                continue;
            }
/*
            if (myHand.list(j].isEmpty() && myHand[holeSuitNum).size() > 1) {
                // check if they can tolerate my drop in myHand.list(holeSuitNum)
                CardList myHoleSuit = (CardList) myHand.list(holeSuitNum).clone();
                myHoleSuit.removeLast();
                CardList.ListData listData = maxUnwantedTricks(myHoleSuit, leftHand.list(holeSuitNum], rightHand[holeSuitNum));
                if (listData.maxTheyStart == 0) {
                    return null;
                }
            }
*/
            if (elderhand == 2 && rightHand.list(j).first().compareTo(leftHand.list(j).last()) < 0) {
                return rightHand.list(j).first();
            }
            if (elderhand == 1 && leftHand.list(j).first().compareTo(rightHand.list(j).last()) < 0) {
                return leftHand.list(j).first();
            }
        }
        return null;
    }

    MisereData defenderEvalDrop(CardList.ListData hole, boolean harmlessHoleMove,
                                Hand _leftHand, Hand _rightHand) {
        MisereData misereData = new MisereData(hole, myHand, _leftHand, _rightHand);
        Card.Suit suit = hole.suit;
        if (this.myHand.list(suit).isEmpty()) {
            return misereData;
        }
        Hand leftHand = _leftHand.clone();
        Hand rightHand = _rightHand.clone();
        Hand myHand = this.myHand.clone();

        Card myMin = myHand.list(suit).first();
        if (myMin.compareTo(rightHand.list(suit).first()) > 0 &&
                rightHand.list(suit).size() > leftHand.list(suit).size()) {
            // e.g. 9XJK  Q  78A
            misereData.needToDrop = 0;
            misereData.moveSuit = suit;
            return misereData;
        }

        Card.Rank myMax = myHand.list(suit).last().getRank();
        int rightDrop = rightHand.list(suit).getMinGreaterThan(myMax);
        if (rightDrop < 0 || rightDrop >= myHand.list(suit).size()) {
            misereData.needToDrop = 0;
            misereData.moveSuit = suit;
        } else {
            misereData.needToDrop = rightHand.list(suit).size() - rightDrop;
        }

        Card rightCard = null;
        CardList rightHoleSuit = (CardList) rightHand.list(suit).clone();
        int extraMove = 0;
        if (harmlessHoleMove) {
            ++extraMove;
            misereData.harmlessHoleMove = harmlessHoleMove;
        }

        if (misereData.needToDrop - extraMove == 0) {
            misereData.needToDrop = 0;
            misereData.moveSuit = suit;
            return misereData;
        }

        CardList myHoleSuit = (CardList) myHand.list(suit).clone();
        for (int i = 0; i < misereData.needToDrop; ++i) {
            rightHoleSuit.removeLast();
        }
        int elderhand = rightHoleSuit.isEmpty() ? 1 : 2;
        CardList.ListData _listData = myHoleSuit.maxUnwantedTricks(leftHand.list(suit), rightHoleSuit, elderhand);
        if (_listData.maxTheyStart == 0) {
            // dropping from right hand will not help anyway
            return misereData;
        }
        int elderhandPass = 0;
        Card.Suit dropSuit = null;
        for (Card.Suit i : Card.Suit.values()) {
            if (!rightHand.list(i).isEmpty() &&
                    rightHand.list(i).first().compareInTrick(leftHand.list(i).last()) < 0) {
                if (!myHand.list(i).isEmpty()) {
                    ++elderhandPass;
                } else if (harmlessHoleMove) {
                    // we can pass elderhand using hole suit
                    ++elderhandPass;
                    harmlessHoleMove = false;
                }
            }
            if (!i.equals(suit) && leftHand.list(i).size() - rightHand.list(i).size() > 0) {
                if (dropSuit == null) {
                    dropSuit = i;
                }
            }
        }
        if (this.elderhand == 2 && elderhandPass == 0) {
            // even if we drop cards from right there is no way to pass elderhand back to left
            misereData.dropSuit = dropSuit;
            return misereData;
        }

        myHoleSuit = myHand.list(suit);
        rightHoleSuit = rightHand.list(suit);
        if (harmlessHoleMove) {
            myHoleSuit.removeLast();
            rightCard = rightHoleSuit.removeLast();
            leftHand.list(suit).removeLast();
            --misereData.needToDrop;
        }
        for (Card.Suit i : Card.Suit.values()) {
            if (i.equals(suit) || leftHand.list(i).size() - rightHand.list(i).size() <= 0) {
                continue;
            }
            CardList mySuit = myHand.list(i);
            CardList leftSuit = leftHand.list(i);
            CardList rightSuit = rightHand.list(i);
            rightDrop = 0;
            while (!leftSuit.isEmpty()) {
                Card leftCard = leftSuit.removeLast();
                rightCard = null;
                boolean doCheck = false;
                if (mySuit.isEmpty()) {
                    myHoleSuit.removeLast();
                    ++misereData.meDrop;
                    doCheck = true;
                } else {
                    mySuit.removeLast();
                }
                if (rightSuit.isEmpty()) {
                    rightCard = rightHoleSuit.removeLast();
                    ++rightDrop;
                } else {
                    rightCard = rightSuit.removeLast();
                }

                CardList.ListData listData = maxUnwantedTricks(myHoleSuit, leftHand.list(suit), rightHoleSuit);
                if (doCheck && listData.maxTheyStart == 0 ||
                        leftCard.compareInTrick(rightCard) < 0 && rightSuit.isEmpty() && elderhandPass == 0) {
                    --misereData.meDrop;
                    --rightDrop;
                    break;
                }
            }
            if (rightDrop > 0) {
                misereData.dropSuit = i;
                if (misereData.moveSuit == null) {
                    misereData.moveSuit = i;
                }
                misereData.rightDrop += rightDrop;
                misereData.canDrop += rightDrop;
            }
        }
        if (misereData.canDrop < misereData.needToDrop) {
            misereData.moveSuit = null;
        } else if (this.elderhand == 2 && misereData.harmlessHoleMove &&
                leftHand.list(suit).last().compareInTrick(rightHand.list(suit).first()) < 0) {
            // all right hole cards are greater than left anyway
            misereData.moveSuit = suit;
        }
        return misereData;
    }

    // analyse delayed drop
    // drops from right.
    // for the sake of simplicity:
    // 1. 2 holes
    // 2. neutral suit - both defenders have 2 cards
    // 3. dropSuitNum on right - at least as many extra cards as neutral suit
    Card defenderEvalDrop(List<MisereData> allMisereData) {
        CardList drop = null;
        Card leftCard = null;
        Card rightCard = null;

//* debug
        if (trick.number >= 2) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/
        DelayedDropData delayedDropData = getDelayedDropData(allMisereData);
        if (delayedDropData == null || delayedDropData.dropSuit == null) {
            return null;
        }
        MisereData misereData = delayedDropData.misereData;
        Card.Suit harmlessHoleSuit = delayedDropData.harmlessHoleSuit;
        Card.Suit dropSuit = delayedDropData.dropSuit;
        Card.Suit neutralSuit = delayedDropData.neutralSuit;

        Card.Suit firstHole = delayedDropData.firstHole;
        Card.Suit secondHole = delayedDropData.secondHole;
        Hand leftHand = delayedDropData.leftHand;
        Hand rightHand = delayedDropData.rightHand;
        Hand myHand = delayedDropData.myHand;

        if (harmlessHoleSuit != null &&
                rightHand.list(harmlessHoleSuit).first().compareInTrick(leftHand.list(harmlessHoleSuit).last()) > 0) {
            myHand.list(harmlessHoleSuit).removeLast();
            leftCard = leftHand.list(harmlessHoleSuit).removeLast();
            rightCard = rightHand.list(harmlessHoleSuit).removeLast();
            misereData.moveSuit = harmlessHoleSuit;
        }

        int elderhand = this.elderhand;
        if (delayedDropData.reversed) {
            elderhand = 3 - elderhand;
        }
        Card.Suit passElderhandSuit = null;
        if (elderhand == 2 && rightHand.list(dropSuit).isEmpty() ||
                !leftHand.list(dropSuit).isEmpty() &&
                leftHand.list(dropSuit).last().compareInTrick(rightHand.list(dropSuit).first()) < 0) {
            // need to pass elderhand after depleting right cards
            if (secondHole != null && leftHand.list(secondHole).last().compareInTrick(rightHand.list(secondHole).first()) >= 0) {
                passElderhandSuit = secondHole;
            } else if (misereData.harmlessHoleMove &&
                rightHand.list(harmlessHoleSuit).first().compareInTrick(leftHand.list(harmlessHoleSuit).last()) < 0) {
                passElderhandSuit = harmlessHoleSuit;
            } else {
                return null;    // cannot do it
            }
        } else if (!realPlayer.myHand.list(dropSuit).isEmpty()) {
//            return  realPlayer.myHand.list(dropSuit).last();
            passElderhandSuit = dropSuit;
        } else {    // if (neutralSuitNum >= 0) {
//            passElderhandSuit = neutralSuitNum;
            return  realPlayer.myHand.list(neutralSuit).last();
        }

        if (elderhand == 2 && rightHand.list(dropSuit).isEmpty()) {
//            misereData.moveSuitNum = passElderhandSuit;
            return rightHand.list(passElderhandSuit).first();
        }
        while (!leftHand.list(dropSuit).isEmpty()) {
            Card _leftCard = leftHand.list(dropSuit).removeFirst();
            if (misereData.moveSuit == null) {
                misereData.moveSuit = dropSuit;
            }
            if (myHand.list(dropSuit).isEmpty()) {
                if (myHand.list(firstHole).isEmpty() && secondHole != null) {
                    myHand.list(secondHole).removeLast();
                } else {
                    myHand.list(firstHole).removeLast();
                }
            } else {
                myHand.list(dropSuit).removeLast();
            }

            Card _rightCard = null;
            if (rightHand.list(dropSuit).isEmpty()) {
                rightHand.list(neutralSuit).removeLast();
            } else {
                _rightCard = rightHand.list(dropSuit).removeLast();
            }
            if (leftCard == null) {
                leftCard = _leftCard;
            }
            if (rightCard == null) {
                rightCard = _rightCard;
            }
        }

//* debug
        if (trick.number >= 2) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/

        if (misereData.moveSuit != null) {
            if (elderhand == 1) {
                misereData.card = leftCard;
            } else {
                misereData.card = rightCard;
            }
        }

        return misereData.card;
    }

    MisereData defenderPlay1stHand(CardList.ListData hole) {
        Hand leftHand = this.leftHand.clone();
        Hand rightHand = this.rightHand.clone();
        Hand myHand = this.myHand.clone();
        boolean reversed = false;

        Card.Suit suit = hole.suit;
        int elderhand = this.elderhand;
        if (this.myHand.list(suit).last().compareInTrick(this.leftHand.list(suit).first()) < 0) {
            // reversed order
            reversed = true;
            elderhand = NUMBER_OF_PLAYERS - elderhand;
            leftHand = this.rightHand.clone();
            rightHand = this.leftHand.clone();
        }

        Card.Suit moveSuit = null;

        // 1. take tricks without losing holes
        CardList mySuit = (CardList) myHand.list(suit).clone();
        CardList leftSuit = (CardList) leftHand.list(suit).clone();
        CardList rightSuit = (CardList) rightHand.list(suit).clone();
        int maxUnwantedTricks = hole.maxTheyStart;
        int size = mySuit.size();
        Card theirMin = theirMin(suit);
        boolean theyHaveMin = theirMin.compareInTrick(mySuit.first()) < 0;
        while (maxUnwantedTricks > 0) {
            mySuit.removeLast();
            Card leftCard = leftSuit.removeLast();
            Card rightCard = rightSuit.removeLast();
            if (leftCard == null && rightCard == null) {
                break;
            }
            if (theyHaveMin && theirMin != leftCard && theirMin != rightCard) {
                continue;
            }
            CardList.ListData listData = maxUnwantedTricks(mySuit, leftSuit, rightSuit);
            maxUnwantedTricks = listData.maxTheyStart;
        }
        boolean harmlessSuitMove = false;
        if (size != mySuit.size() + 1) {
            harmlessSuitMove = true;
        }
//* debug
        if (trick.number >= 2) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/
        MisereData misereData = defenderEvalDrop(hole, harmlessSuitMove, leftHand, rightHand);
        misereData.reversed = reversed;
        if (misereData.needToDrop > misereData.canDrop) {
            return misereData;
        }
        if (moveSuit != null) {
            misereData.moveSuit = moveSuit;
        } else {
            moveSuit = misereData.moveSuit;
        }

//* debug
        if (trick.number >= 3) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/

        if (suit.equals(moveSuit)) {
            if (elderhand == 1) {
                if (leftHand.list(moveSuit).isEmpty() ||
                        !rightHand.list(moveSuit).isEmpty() &&
                        myHand.list(suit).first().compareInTrick(theirMin) < 0) {
                    misereData.card = passElderhand(elderhand, suit, leftHand, rightHand);
                } else {
                    Card myMin = myHand.list(moveSuit).first();
                    if (leftHand.list(moveSuit).size() == 1 ||
                            myMin.compareInTrick(leftHand.list(moveSuit).first()) > 0 &&
                            myMin.compareInTrick(rightHand.list(moveSuit).first()) > 0) {
                        misereData.card = leftHand.list(moveSuit).first();
                    } else {
                        misereData.card = leftHand.list(moveSuit).get(1);
//                        misereData.card = leftHand.list(moveSuit).last();
                    }
                }
            } else {
                misereData.card = rightHand.list(moveSuit).first();
            }
        } else if (moveSuit != null) {
            if (elderhand == 1 && (leftHand.list(moveSuit).isEmpty() || misereData.needToDrop == 0) ||
                    elderhand == 2 && rightHand.list(moveSuit).isEmpty()) {
                Card.Suit _suit = suit;
                if (harmlessSuitMove) {
                    _suit = null;
                }
                misereData.card = passElderhand(elderhand, _suit, leftHand, rightHand);
            } else if (elderhand == 1) {
                if (rightHand.list(moveSuit).size() == 1) {
                    misereData.card = leftHand.list(moveSuit).last();  // need to keep elderhand
                } else {
                    misereData.card = leftHand.list(moveSuit).first();
                }
            } else {
                // elderhand == 2
                if (leftHand.list(moveSuit).size() == 1 ||
                    rightHand.list(moveSuit).size() > 1) {
                    misereData.card = rightHand.list(moveSuit).last();  // need to keep elderhand
                } else {
                    misereData.card = rightHand.list(moveSuit).first();
                }
            }
        }
        return misereData;
    }

    Card defenderPlayMisere23Hand(List<CardList.ListData> holes) {
        Card.Suit trickSuit = trick.topCard.getSuit();
        CardList playerCardList = realPlayer.myHand.list(trickSuit);

//* debug
        if (trick.number >= 3) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/

        if (playerCardList.isEmpty()) {
            // drop
            if (holes.isEmpty() ||
                commonDelayedDropData.initHoles == 2 && commonDelayedDropData.dropSuit == null) {
                return realPlayer.anyCard();
            }
            Card.Suit holeSuit = holes.get(0).suit;
            if (holes.size() == 1) {
                if (commonDelayedDropData.neutralSuit != null &&
                        !realPlayer.myHand.list(commonDelayedDropData.neutralSuit).isEmpty()) {
                    return  realPlayer.myHand.list(commonDelayedDropData.neutralSuit).last();
                }
                return realPlayer.myHand.list(holeSuit).last();
            }
            // 2 holes
            List<MisereData> allMisereHoles = new LinkedList<>();
//* debug
            if (trick.number >= 3) {
                DEBUG_LOG = DEBUG_LOG;
            }
//*/
            for (CardList.ListData hole : holes) {
                Card myMin = myHand.list(holeSuit).first();
                CardList otherList = realPlayer.myHand.list(holeSuit);
                Card otherMin = otherList.first();
                if (otherList.size() == 1 &&
                        theirMin(holeSuit).compareInTrick(myMin) < 0 &&
                        otherMin.compareInTrick(myMin) > 0) {
                    return otherMin;
                }

                // todo: check harmlessSuitMove
                MisereData misereData = defenderEvalDrop(hole, false, leftHand, rightHand);
                allMisereHoles.add(misereData);
            }
            DelayedDropData delayedDropData = getDelayedDropData(allMisereHoles);
            if (delayedDropData.neutralSuit == null || realPlayer.myHand.list(delayedDropData.neutralSuit).isEmpty()) {
                return realPlayer.anyCard();
            }
            return realPlayer.myHand.list(delayedDropData.neutralSuit).last();
        }
        int declarerCardNum = (trick.declarerNum - trick.startedBy + NUMBER_OF_PLAYERS) % NUMBER_OF_PLAYERS;
        if (declarerCardNum <= trick.trickCards.size()) {
            // declarer played already
            Card declarerCard = trick.trickCards.get(declarerCardNum);
            if (trick.trickCards.size() == 1) {
                if (declarerCard.compareInTrick(leftHand.list(trick.startingSuit).first()) < 0 ||
                        declarerCard.compareInTrick(rightHand.list(trick.startingSuit).first()) < 0) {
                    return playerCardList.last();
                }
            }
            if (trick.topCard.compareInTrick(declarerCard) > 0) {
                // todo: more elaborate analysis
                // declarer passed this trick anyway
                return realPlayer.myHand.list(trickSuit).last();
            }
            // declarer's card higher than topCard
            if (trick.topCard.compareInTrick(playerCardList.first()) < 0) {
                // player's cards are all larger anyway
                return playerCardList.last();
            }
            // play with lesser than trick.topCard
            int passNum = playerCardList.getMaxLessThan(trick.topCard.getRank());
            return playerCardList.get(passNum);
        } else {
            // declarer has not played yet
            Card myMin = myHand.list(trickSuit).first();
            if (playerCardList.first().compareInTrick(myMin) > 0 ||
                    myMin.compareInTrick(trick.topCard) < 0 &&
                    myMin.compareInTrick(leftHand.list(trickSuit).first()) < 0) {
                // either player's or the other defender's cards are all larger
                return playerCardList.last();
            } else {
                // play with lesser than declalerMin
                int passNum = playerCardList.getMaxLessThan(trick.topCard.getRank());
                if (passNum < 0) {
                    boolean holeMove = false;
                    for (CardList.ListData hole : holes) {
                        if (trick.topCard.getSuit().equals(hole.suit)) {
                            holeMove = true;
                            break;
                        }
                    }
                    if (holeMove) {
                        return playerCardList.first();
                    }
                    return playerCardList.last();
                }
                return playerCardList.get(passNum);
            }
        }
    }

    Card defenderPlayMisere() {
        Card res = null;

        if (trick.startingSuit == null) {
            // playing elderhand
//* debug
            if (trick.number >= 2) {
                DEBUG_LOG = DEBUG_LOG;
            }
//*/
            List<MisereData> normalHoles = new LinkedList<>();
            List<MisereData> reverseHoles = new LinkedList<>();
            for (CardList.ListData hole : holes) {
                MisereData misereData = defenderPlay1stHand(hole);
                if (misereData.reversed) {
                    reverseHoles.add(misereData);
                } else {
                    normalHoles.add(misereData);
                }
                res = misereData.card;
                if (res != null) {
                    break;
                }
            }

            if (holes.isEmpty()) {
                Logger.printf("bot %d - clean misere\n", number);
            } else {
                if (res == null && !normalHoles.isEmpty()) {
                    res = defenderEvalDrop(normalHoles);
                }
                if (res == null && !reverseHoles.isEmpty()) {
                    res = defenderEvalDrop(reverseHoles);
                }
            }
        } else {
            res = defenderPlayMisere23Hand(holes);
        }
        if (res == null) {
            return realPlayer.anyCard();
        }
        return res;
    }

    Card declarerPlayMisere() {
        HandResults handResults = misereTricks(trick.getTurn());
//* debug
        if (trick.number >= 8) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/
        CardList.ListData bestListData = null;
        if (trick.startingSuit == null) {
            for (CardList.ListData listData : handResults.allListData) {
                if (listData == null) {
                    continue;
                }
                CardList cardList = listData.thisSuit;
                if (cardList.isEmpty()) {
                    continue;
                }
                if (bestListData == null || bestListData.maxMeStart > listData.maxMeStart) {
                    bestListData = listData;
                }
            }
            if (bestListData != null) {
                CardList cleanSuit = bestListData.thisSuit;
                Card.Suit s = bestListData.suit;
                int j = cleanSuit.getOptimalStart(leftHand.list(s), rightHand.list(s));
                return cleanSuit.get(j);
            }
        } else {
            Card topCard = trick.topCard;
            Card.Suit suit = topCard.getSuit();
            CardList cardList = myHand.list(suit);
            Card theirMin = theirMin(suit);
            if (cardList.size() == 1) {
                return cardList.first();
            } else if (cardList.isEmpty()) {
                return declarerDrop();
            } else if (theirMin == null) {
//                return cardList.last();
                return cardList.first();    // the last card might be in trick
            } else {
                int index = cardList.getMaxLessThan(topCard.getRank());
                boolean handsOk = cardList.last().compareInTrick(rightHand.list(suit).first()) > 0 &&
                        cardList.last().compareInTrick(leftHand.list(suit).first()) > 0 &&
                        leftHand.list(suit).size() != 1;
                if (index == 0) {
                    if (trick.trickCards.size() == 1 && handsOk ||
                        trick.trickCards.size() == 2 && theirMin.compareInTrick(cardList.get(1)) < 0) {
                        return cardList.get(1);     // need to keep min card
                    }
                }
                if (index >= 0) {
                    return cardList.get(index);
                }
                return cardList.last();     // have to take it
            }
        }
        return null;    // should not be here
    }

    Card declarerDrop() {
//* debug
        if (trick.number >= 4) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/
        Card res = null;
        Card card = null;
        if (holes.isEmpty()) {
            res = realPlayer.anyCard();
        } else if (holes.size() == 1) {
            res = holes.get(0).thisSuit.last();
        } else {
            if (trick.trickCards.size() == 1) {
probe:
                for (CardList.ListData hole : holes) {
                    if (card == null) {
                        if (declarerDrop == DeclarerDrop.First) {
                            card = this.myHand.list(hole.suit).last();
                        } else if (Util.nextRandInt(2) == 0 &&
                            (declarerDrop == DeclarerDrop.Random || GameManager.RELEASE)) {
                            card = this.myHand.list(hole.suit).last();
                        }
                    }
                    for (int i = 0; i < this.leftHand.list(hole.suit).size(); ++i) {
                        Card _card = this.leftHand.list(hole.suit).remove(i);
                        // todo: check harmlessSuitMove
                        MisereData misereData = defenderEvalDrop(hole, false, leftHand, rightHand);
                        this.leftHand.list(hole.suit).add(i, _card);
                        if (misereData.moveSuit != null) {
                            res = myHand.list(hole.suit).last();
                            break probe;
                        }
                    }
                }
            } else {
                int minDrop = Integer.MAX_VALUE;
probe:
                for (CardList.ListData hole : holes) {
                    for (int i = 0; i < this.myHand.list(hole.suit).size(); ++i) {
                        card = this.myHand.list(hole.suit).remove(i);
                        // todo: check harmlessSuitMove
                        MisereData misereData = defenderEvalDrop(hole, false, leftHand, rightHand);
                        this.myHand.list(hole.suit).add(i, card);
                        if (misereData.moveSuit == null) {
                            res = card;
                            if (declarerDrop == DeclarerDrop.First) {
                                break probe;
                            } else if (Util.nextRandInt(2) == 0 &&
                                    (declarerDrop == DeclarerDrop.Random || GameManager.RELEASE)) {
                                break probe;
                            }
                        } else if (minDrop > misereData.needToDrop) {
                            minDrop = misereData.needToDrop;
                            res = card;
                        }
                    }
                }
            }
        }
        if (res == null) {
            res = card;
        }
        if (res == null) {
            res = realPlayer.anyCard();
        }
        Logger.println("declarer drops " + res.toColorString());
        return res;
    }

    DelayedDropData getDelayedDropData(List<MisereData> allMisereData) {
        DelayedDropData delayedDropData = new DelayedDropData();
        int suitSum = 0;
        int reversedCount = 0;
        Card.Suit firstHole = null;
        Card.Suit secondHole = null;
        int neutralSuitNum = -1;
        Card.Suit dropSuit = null;
        boolean needToDrop = false;
//* debug
        if (trick.number >= 1) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/

        for (MisereData _misereData : allMisereData) {
            delayedDropData.misereData = _misereData;
            delayedDropData.leftHand = _misereData.leftHand;
            delayedDropData.rightHand = _misereData.rightHand;
            delayedDropData.myHand = _misereData.myHand.clone();
            if (_misereData.dropSuit != null) {
                if (firstHole == null) {
                    firstHole = _misereData.hole.suit;
                } else {
                    secondHole = _misereData.hole.suit;
                }
            } else {
                secondHole = _misereData.hole.suit;
            }
            if (_misereData.harmlessHoleMove) {
                delayedDropData.harmlessHoleSuit = _misereData.hole.suit;
            }
            suitSum += _misereData.hole.suit.getValue();
            if (dropSuit == null && _misereData.dropSuit != null) {
                dropSuit = _misereData.dropSuit;
                suitSum += dropSuit.getValue();
            }
            if (_misereData.reversed) {
                ++reversedCount;
            }
            if (_misereData.needToDrop > 0) {
                needToDrop = true;
            }
        }

        if (!commonDelayedDropData.initialized) {
            if (allMisereData.size() == 2) {
                neutralSuitNum = Card.Suit.SUM - suitSum;
                if (neutralSuitNum >= Card.TOTAL_SUITS) {
                    neutralSuitNum = -1;
                }
            }
            if (!needToDrop) {
                firstHole =
                secondHole =
                delayedDropData.firstHole =
                delayedDropData.secondHole =
                delayedDropData.harmlessHoleSuit =
                delayedDropData.neutralSuit =
                delayedDropData.dropSuit = null;
            }
            if (reversedCount == 2) {
                delayedDropData.reversed = true;
            } else if (reversedCount != 0) {
                return null;
            }
            if (delayedDropData.harmlessHoleSuit == firstHole) {
                delayedDropData.firstHole = firstHole;
                delayedDropData.secondHole = secondHole;
            } else {
                delayedDropData.firstHole = secondHole;
                delayedDropData.secondHole = firstHole;
            }
            commonDelayedDropData.dropSuit = dropSuit;
            if (neutralSuitNum >= 0) {
                commonDelayedDropData.neutralSuit = Card.Suit.values()[neutralSuitNum];
            }
            commonDelayedDropData.firstHole = firstHole;
            commonDelayedDropData.secondHole = secondHole;
            commonDelayedDropData.reversed = delayedDropData.reversed;
            commonDelayedDropData.initialized = true;
        }
        delayedDropData.dropSuit = commonDelayedDropData.dropSuit;
        delayedDropData.neutralSuit = commonDelayedDropData.neutralSuit;
        delayedDropData.reversed = commonDelayedDropData.reversed;
        delayedDropData.firstHole = commonDelayedDropData.firstHole;
        if (secondHole == null) {
            commonDelayedDropData.secondHole = null;    // eventually declarer drops one hole
        }
        delayedDropData.secondHole = commonDelayedDropData.secondHole;
        return delayedDropData;
    }

    static class DelayedDropData {
        MisereData misereData;
        Card.Suit harmlessHoleSuit;
        Card.Suit dropSuit;
        Card.Suit neutralSuit;
        Card.Suit firstHole;
        Card.Suit secondHole;
        Hand myHand;
        Hand leftHand = null;
        Hand rightHand = null;
        boolean reversed = false;
    }

    static class CommonDelayedDropData {
        boolean initialized = false; // to catch misère with delayed drop
        boolean reversed = false;    // to catch misère with delayed drop
        int initHoles = -1;          // for misère
        Card.Suit dropSuit;        // to catch misère with delayed drop
        Card.Suit neutralSuit;     // to catch misère with delayed drop
        Card.Suit firstHole;          // to catch misère with delayed drop
        Card.Suit secondHole;         // to catch misère with delayed drop
    }

    static class MisereData {
        final CardList.ListData hole;
        final Hand myHand;
        final Hand leftHand;
        final Hand rightHand;
        boolean reversed;
        Card card;      // card to play
        Card.Suit moveSuit;
        boolean harmlessHoleMove;
        int needToDrop = 0;
        int canDrop;
        Card.Suit dropSuit;
        int meDrop;
        int rightDrop;

        MisereData(CardList.ListData hole, Hand myHand, Hand leftHand, Hand rightHand) {
            this.hole = hole;
            this.myHand = myHand;
            this.leftHand = leftHand;
            this.rightHand = rightHand;
        }
    }
}
