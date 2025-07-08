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
import com.ab.jpref.config.Config;
import com.ab.util.Logger;
import com.ab.util.Util;

import java.util.*;

public class MisereBot extends Bot {
//    public static DeclarerDrop declarerDrop = DeclarerDrop.First;
    public static DeclarerDrop declarerDrop = DeclarerDrop.Last;
    public static boolean DEBUG_LOG = false;
    public static CardList debugDrop = null;

    static DelayedMiserData delayedMiserData;

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
        elderhand = CardList.equals(leftSuits, realPlayer.mySuits) ? 1 : 2;
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

    boolean evalMisere(boolean meStart) {
        CardList badSuit = null;
        for (CardList suit : mySuits) {
            if (suit.isEmpty()) {
                continue;
            }
            int suitNum = suit.first().getSuit().getValue();
            CardList.ListData listData;
            listData = suit.maxUnwantedTricks(leftSuits[suitNum], rightSuits[suitNum], meStart);
            if (meStart && listData.maxMeStart == 0) {
                meStart = false;
                listData = suit.maxUnwantedTricks(leftSuits[suitNum], rightSuits[suitNum], meStart);
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

        Logger.printf(DEBUG_LOG,"good from talon:\n");
        int goodCards = 0;
        for (Card card : talonCandidates) {
            Set<Card> probeHand = new HashSet<>(myHand);
            probeHand.add(card);
            for (Card c : myHand) {
                probeHand.remove(c);
                Bot bot = new Bot("test", probeHand);
                MisereBot probeBot = new MisereBot(bot, bot);
                HandResults handResults = probeBot.misereTricks(meStart);
                if (handResults.totalTricks == 0) {
                    ++goodCards;
                    Logger.printf("talon %s, drop %s\n", card, c);
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
        boolean _elderHand = false;
        CardList myHand = new CardList();
        for (CardList suit : mySuits) {
            myHand.addAll(suit);
        }
        CardList probeSet1 = new CardList(myHand);
        Collections.sort(myHand);
probes:
        for (Card card1 : myHand) {
            probeSet1.remove(card1);
            Collections.sort(probeSet1);
            for (Card card2 : probeSet1) {
                Set<Card> probeHand = new HashSet<>(myHand);
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

    HandResults misereTricks(boolean meStart) {
        HandResults handResults = new HandResults();
        handResults.expect = 0;
        for (CardList suit : mySuits) {
            if (suit.isEmpty()) {
                continue;
            }
            int suitNum = suit.first().getSuit().getValue();
            CardList.ListData listData = suit.maxUnwantedTricks(leftSuits[suitNum], rightSuits[suitNum], meStart);
            handResults.allListData[suitNum] = listData;
            int tricks = listData.maxMeStart + listData.maxTheyStart;   // only one can be > 0
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
        for (int i = 0; i < Card.Suit.values().length - 1; ++i) {
/* debug
            if (trick.number >= 5) {
                DEBUG_LOG = DEBUG_LOG;
            }
//*/
            CardList.ListData listData = maxUnwantedTricks(mySuits[i], leftSuits[i], rightSuits[i]);
            if (listData.maxTheyStart > 0) {
                holes.add(listData);
                if (trick.startingSuit == null) {
                    // log it once per trick
                    Logger.printf(DEBUG_LOG, "hole %s\n", listData.thisSuit);
                }
            }
        }

        if (delayedMiserData.initHoles < 0) {
            delayedMiserData.initHoles = holes.size();
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

    Card passElderhand(int elderhand, int holeSuitNum, CardList[] leftSuits, CardList[] rightSuits) {
        // find suit to pass elderhand
        for (int j = 0; j < Card.Suit.values().length - 1; ++j) {
            if (j == holeSuitNum ||
                rightSuits[j].isEmpty() || leftSuits[j].isEmpty()) {
                continue;
            }
            if (elderhand == 2 && rightSuits[j].first().compareTo(leftSuits[j].last()) < 0) {
                return rightSuits[j].first();
            }
            if (elderhand == 1 && leftSuits[j].first().compareTo(rightSuits[j].last()) < 0) {
                return leftSuits[j].first();
            }
        }
        return null;
    }

    MisereData defenderEvalDrop(CardList.ListData hole, boolean harmlessHoleMove,
                                CardList[] _leftSuits, CardList[] _rightSuits) {
        MisereData misereData = new MisereData(hole, mySuits, _leftSuits, _rightSuits);
        int suitNum = hole.suitNum;
        if (this.mySuits[suitNum].isEmpty()) {
            return misereData;
        }
        CardList[] leftSuits = CardList.clone(_leftSuits);
        CardList[] rightSuits = CardList.clone(_rightSuits);
        CardList[] mySuits = CardList.clone(this.mySuits);

        Card myMin = mySuits[suitNum].first();
        if (myMin.compareTo(rightSuits[suitNum].first()) > 0 &&
                rightSuits[suitNum].size() > leftSuits[suitNum].size()) {
            // e.g. 9XJK  Q  78A
            misereData.needToDrop = 0;
            misereData.moveSuitNum = suitNum;
            return misereData;
        }

        Card.Rank myMax = mySuits[suitNum].last().getRank();
        int rightDrop = rightSuits[suitNum].getMinGreaterThan(myMax);
        if (rightDrop < 0 || rightDrop >= mySuits[suitNum].size()) {
            misereData.needToDrop = 0;
            misereData.moveSuitNum = suitNum;
        } else {
            misereData.needToDrop = rightSuits[suitNum].size() - rightDrop;
        }

        Card rightCard;
        CardList rightHoleSuit = (CardList) rightSuits[suitNum].clone();
        int extraMove = 0;
        if (harmlessHoleMove) {
            ++extraMove;
            misereData.harmlessHoleMove = harmlessHoleMove;
        }

        if (misereData.needToDrop - extraMove == 0) {
            misereData.needToDrop = 0;
            misereData.moveSuitNum = suitNum;
            return misereData;
        }

        CardList myHoleSuit = (CardList) mySuits[suitNum].clone();
        for (int i = 0; i < misereData.needToDrop; ++i) {
            rightHoleSuit.removeLast();
        }
        int elderhand = rightHoleSuit.isEmpty() ? 1 : 2;
        CardList.ListData _listData = myHoleSuit.maxUnwantedTricks(leftSuits[suitNum], rightHoleSuit, elderhand);
        if (_listData.maxTheyStart == 0) {
            // dropping from right hand will not help anyway
            return misereData;
        }
        int elderhandPass = 0;
        int dropSuitNum = -1;
        for (int i = 0; i < Card.Suit.values().length - 1; ++i) {
            if (!rightSuits[i].isEmpty() &&
                    rightSuits[i].first().compareInTrick(leftSuits[i].last()) < 0) {
                if (!mySuits[i].isEmpty()) {
                    ++elderhandPass;
                } else if (harmlessHoleMove) {
                    // we can pass elderhand using hole suit
                    ++elderhandPass;
                    harmlessHoleMove = false;
                }
            }
            if (i != suitNum && leftSuits[i].size() - rightSuits[i].size() > 0) {
                if (dropSuitNum == -1) {
                    dropSuitNum = i;
                }
            }
        }
        if (this.elderhand == 2 && elderhandPass == 0) {
            // even if we drop cards from right there is no way to pass elderhand back to left
            misereData.dropSuitNum = dropSuitNum;
            return misereData;
        }

/* debug
        if (trick.number >= 0 && suitNum == 3) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/
        myHoleSuit = mySuits[suitNum];
        rightHoleSuit = rightSuits[suitNum];
        if (harmlessHoleMove) {
            myHoleSuit.removeLast();
            rightHoleSuit.removeLast();
            leftSuits[suitNum].removeLast();
            --misereData.needToDrop;
        }
        for (int i = 0; i < Card.Suit.values().length - 1; ++i) {
            if (i == suitNum || leftSuits[i].size() - rightSuits[i].size() <= 0) {
                continue;
            }
            CardList mySuit = mySuits[i];
            CardList leftSuit = leftSuits[i];
            CardList rightSuit = rightSuits[i];
            rightDrop = 0;
            while (!leftSuit.isEmpty()) {
                Card leftCard = leftSuit.removeLast();
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

                CardList.ListData listData = maxUnwantedTricks(myHoleSuit, leftSuits[suitNum], rightHoleSuit);
                if (doCheck && listData.maxTheyStart == 0 ||
                        leftCard.compareInTrick(rightCard) < 0 && rightSuit.isEmpty() && elderhandPass == 0) {
                    --misereData.meDrop;
                    --rightDrop;
                    break;
                }
            }
            if (rightDrop > 0) {
                misereData.dropSuitNum = i;
                if (misereData.moveSuitNum == -1) {
                    misereData.moveSuitNum = i;
                }
                misereData.rightDrop += rightDrop;
                misereData.canDrop += rightDrop;
            }
        }
        if (misereData.canDrop < misereData.needToDrop) {
            misereData.moveSuitNum = -1;
        } else if (this.elderhand == 2 && misereData.harmlessHoleMove &&
                leftSuits[suitNum].last().compareInTrick(rightSuits[suitNum].first()) < 0) {
            // all right hole cards are greater than left anyway
            misereData.moveSuitNum = suitNum;
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

/* debug
        if (trick.number >= 2) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/
        DelayedDropData delayedDropData = getDelayedDropData(allMisereData);
        if (delayedDropData == null || delayedDropData.dropSuitNum == -1) {
            return null;
        }
        MisereData misereData = delayedDropData.misereData;
        int harmlessHoleSuit = delayedDropData.harmlessHoleSuit;
        int dropSuitNum = delayedDropData.dropSuitNum;
        int neutralSuitNum = delayedDropData.neutralSuitNum;

        int firstHole = delayedDropData.firstHole;
        int secondHole = delayedDropData.secondHole;
        CardList[] leftSuits = delayedDropData.leftSuits;
        CardList[] rightSuits = delayedDropData.rightSuits;
        CardList[] mySuits = delayedDropData.mySuits;

        if (harmlessHoleSuit >= 0 &&
                rightSuits[harmlessHoleSuit].first().compareInTrick(leftSuits[harmlessHoleSuit].last()) > 0) {
            mySuits[harmlessHoleSuit].removeLast();
            leftCard = leftSuits[harmlessHoleSuit].removeLast();
            rightCard = rightSuits[harmlessHoleSuit].removeLast();
            misereData.moveSuitNum = harmlessHoleSuit;
        }

        int elderhand = this.elderhand;
        if (delayedDropData.reversed) {
            elderhand = 3 - elderhand;
        }
        int passElderhandSuit = -1;
        if (elderhand == 2 && rightSuits[dropSuitNum].isEmpty() ||
                !leftSuits[dropSuitNum].isEmpty() &&
                leftSuits[dropSuitNum].last().compareInTrick(rightSuits[dropSuitNum].first()) < 0) {
            // need to pass elderhand after depleting right cards
            if (secondHole >= 0 && leftSuits[secondHole].last().compareInTrick(rightSuits[secondHole].first()) >= 0) {
                passElderhandSuit = secondHole;
            } else if (misereData.harmlessHoleMove &&
                rightSuits[harmlessHoleSuit].first().compareInTrick(leftSuits[harmlessHoleSuit].last()) < 0) {
                passElderhandSuit = harmlessHoleSuit;
            } else {
                return null;    // cannot do it
            }
        } else if (!realPlayer.mySuits[dropSuitNum].isEmpty()) {
//            return  realPlayer.mySuits[dropSuitNum].last();
            passElderhandSuit = dropSuitNum;
        } else {    // if (neutralSuitNum >= 0) {
            return  realPlayer.mySuits[neutralSuitNum].last();
        }

        if (elderhand == 2 && rightSuits[dropSuitNum].isEmpty()) {
            return rightSuits[passElderhandSuit].first();
        }
        while (!leftSuits[dropSuitNum].isEmpty()) {
            Card _leftCard = leftSuits[dropSuitNum].removeFirst();
            if (misereData.moveSuitNum == -1) {
                misereData.moveSuitNum = dropSuitNum;
            }
            if (mySuits[dropSuitNum].isEmpty()) {
                if (mySuits[firstHole].isEmpty() && secondHole >= 0) {
                    mySuits[secondHole].removeLast();
                } else {
                    mySuits[firstHole].removeLast();
                }
            } else {
                mySuits[dropSuitNum].removeLast();
            }

            Card _rightCard = null;
            if (rightSuits[dropSuitNum].isEmpty()) {
                rightSuits[neutralSuitNum].removeLast();
            } else {
                _rightCard = rightSuits[dropSuitNum].removeLast();
            }
            if (leftCard == null) {
                leftCard = _leftCard;
            }
            if (rightCard == null) {
                rightCard = _rightCard;
            }
        }

/* debug
        if (trick.number >= 2) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/

        if (misereData.moveSuitNum >= 0) {
            if (elderhand == 1) {
                misereData.card = leftCard;
            } else {
                misereData.card = rightCard;
            }
        }

        return misereData.card;
    }

    MisereData defenderPlay1stHand(CardList.ListData hole) {
        CardList[] leftSuits = this.leftSuits.clone();
        CardList[] rightSuits = this.rightSuits.clone();
        CardList[] mySuits = this.mySuits.clone();
        boolean reversed = false;

        int suitNum = hole.suitNum;
        int elderhand = this.elderhand;
        if (this.mySuits[suitNum].last().compareInTrick(this.leftSuits[suitNum].first()) < 0) {
            // reversed order
            reversed = true;
            elderhand = NUMBER_OF_PLAYERS - elderhand;
            leftSuits = this.rightSuits.clone();
            rightSuits = this.leftSuits.clone();
        }

        int moveSuitNum = -1;

        // 1. take tricks without losing holes
        CardList mySuit = (CardList) mySuits[suitNum].clone();
        CardList leftSuit = (CardList) leftSuits[suitNum].clone();
        CardList rightSuit = (CardList) rightSuits[suitNum].clone();
        int maxUnwantedTricks = hole.maxTheyStart;
        int size = mySuit.size();
        Card theirMin = theirMin(suitNum);
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

/* debug
        if (trick.number >= 2) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/
        MisereData misereData = defenderEvalDrop(hole, harmlessSuitMove, leftSuits, rightSuits);
        misereData.reversed = reversed;
        if (misereData.needToDrop > misereData.canDrop) {
            return misereData;
        }
        if (moveSuitNum >= 0) {
            misereData.moveSuitNum = moveSuitNum;
        } else {
            moveSuitNum = misereData.moveSuitNum;
        }

/* debug
        if (trick.number >= 5) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/

        if (moveSuitNum == suitNum) {
            if (elderhand == 1) {
                if (leftSuits[moveSuitNum].isEmpty() ||
                        !rightSuits[moveSuitNum].isEmpty() &&
                        mySuits[suitNum].first().compareInTrick(theirMin) < 0) {
                    misereData.card = passElderhand(elderhand, suitNum, leftSuits, rightSuits);
                } else {
                    Card myMin = mySuits[moveSuitNum].first();
                    if (leftSuits[moveSuitNum].size() == 1 ||
                            myMin.compareInTrick(leftSuits[moveSuitNum].first()) > 0 &&
                            myMin.compareInTrick(rightSuits[moveSuitNum].first()) > 0) {
                        misereData.card = leftSuits[moveSuitNum].first();
                    } else {
                        misereData.card = leftSuits[moveSuitNum].get(1);
                    }
                }
            } else {
                misereData.card = rightSuits[moveSuitNum].first();
            }
        } else if (moveSuitNum >= 0) {
            if (elderhand == 1 && (leftSuits[moveSuitNum].isEmpty() || misereData.needToDrop == 0) ||
                    elderhand == 2 && rightSuits[moveSuitNum].isEmpty()) {
                int _suitNum = suitNum;
                if (harmlessSuitMove) {
                    _suitNum = -1;
                }
                misereData.card = passElderhand(elderhand, _suitNum, leftSuits, rightSuits);
            } else if (elderhand == 1) {
                if (rightSuits[moveSuitNum].size() == 1) {
                    misereData.card = leftSuits[moveSuitNum].last();  // need to keep elderhand
                } else {
                    misereData.card = leftSuits[moveSuitNum].first();
                }
            } else {
                // elderhand == 2
                if (leftSuits[moveSuitNum].size() == 1 ||
                    rightSuits[moveSuitNum].size() > 1) {
                    misereData.card = rightSuits[moveSuitNum].last();  // need to keep elderhand
                } else {
                    misereData.card = rightSuits[moveSuitNum].first();
                }
            }
        }
        return misereData;
    }

    Card defenderPlayMisere23Hand(List<CardList.ListData> holes) {
        int trickSuitNum = trick.topCard.getSuit().getValue();
        CardList playerSuit = realPlayer.mySuits[trickSuitNum];

        if (playerSuit.isEmpty()) {
            // drop
            if (holes.isEmpty() ||
                    delayedMiserData.initHoles == 2 && delayedMiserData.dropSuitNum == -1) {
                return realPlayer.anyCard();
            }
            int holeSuitNum = holes.get(0).suitNum;
            if (holes.size() == 1) {
                if (delayedMiserData.neutralSuitNum >= 0 &&
                        !realPlayer.mySuits[delayedMiserData.neutralSuitNum].isEmpty()) {
                    return  realPlayer.mySuits[delayedMiserData.neutralSuitNum].last();
                }
                return realPlayer.mySuits[holeSuitNum].last();
            }
            // 2 holes
            List<MisereData> allMisereHoles = new LinkedList<>();
            for (CardList.ListData hole : holes) {
                // todo: check harmlessSuitMove
                MisereData misereData = defenderEvalDrop(hole, false, leftSuits, rightSuits);
                allMisereHoles.add(misereData);
            }
/* debug
            if (trick.number >= 1) {
                DEBUG_LOG = DEBUG_LOG;
            }
//*/
            DelayedDropData delayedDropData = getDelayedDropData(allMisereHoles);
            if (delayedDropData.neutralSuitNum == -1 || realPlayer.mySuits[delayedDropData.neutralSuitNum].isEmpty()) {
                return realPlayer.anyCard();
            }
            return realPlayer.mySuits[delayedDropData.neutralSuitNum].last();
        }
        int declarerCardNum = (trick.declarerNum - trick.startedBy + NUMBER_OF_PLAYERS) % NUMBER_OF_PLAYERS;
        if (declarerCardNum <= trick.trickCards.size()) {
            // declarer played already
            Card declarerCard = trick.trickCards.get(declarerCardNum);
            if (trick.topCard.compareInTrick(declarerCard) > 0) {
                // todo: more elaborate analysis
                // declarer passed this trick anyway
                return realPlayer.mySuits[trickSuitNum].last();
            }
            // declarer's card higher than topCard
            if (trick.topCard.compareInTrick(playerSuit.first()) < 0) {
                // player's cards are all larger anyway
                return playerSuit.last();
            }
            // play with lesser than trick.topCard
            int passNum = playerSuit.getMaxLessThan(trick.topCard.getRank());
            return playerSuit.get(passNum);
        } else {
            // declarer has not played yet
            Card myMin = mySuits[trickSuitNum].first();
            if (playerSuit.first().compareInTrick(myMin) > 0 ||
                    myMin.compareInTrick(trick.topCard) < 0 &&
                    myMin.compareInTrick(leftSuits[trickSuitNum].first()) < 0) {
                // either player's or the other defender's cards are all larger
                return playerSuit.last();
            } else {
                // play with lesser than declalerMin
                int passNum = playerSuit.getMaxLessThan(trick.topCard.getRank());
                if (passNum < 0) {
                    boolean holeMove = false;
                    for (CardList.ListData hole : holes) {
                        if (trick.topCard.getSuit().getValue() == hole.suitNum) {
                            holeMove = true;
                            break;
                        }
                    }
                    if (holeMove) {
                        return playerSuit.first();
                    }
                    return playerSuit.last();
                }
                return playerSuit.get(passNum);
            }
        }
    }

    Card defenderPlayMisere() {
        Card res = null;

        if (trick.startingSuit == null) {
            // playing elderhand
/* debug
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
        HandResults handResults = misereTricks(trick.startingSuit == null);
        CardList.ListData bestListData = null;
        if (trick.startingSuit == null) {
            for (int i = 0; i < mySuits.length; ++i) {
                CardList.ListData listData = handResults.allListData[i];
                if (listData == null) {
                    continue;
                }
                CardList suit = mySuits[i];
                if (suit.isEmpty()) {
                    continue;
                }
                if (bestListData == null || bestListData.maxMeStart > listData.maxMeStart) {
                    bestListData = listData;
                }
            }
            if (bestListData != null) {
                CardList cleanSuit = bestListData.thisSuit;
                int i = bestListData.suitNum;
                int j = cleanSuit.getOptimalStart(leftSuits[i], rightSuits[i]);
                return cleanSuit.get(j);
            }
        } else {
/* debug
            if (trick.number >= 6) {
                DEBUG_LOG = DEBUG_LOG;
            }
//*/
            Card topCard = trick.topCard;
            int suitNum = topCard.getSuit().getValue();
            CardList suit = mySuits[suitNum];
            Card theirMin = theirMin(suitNum);
            if (suit.size() == 1) {
                return suit.first();
            } else if (suit.isEmpty()) {
                return declarerDrop();
            } else if (theirMin == null) {
                return suit.first();    // the last card might be in trick
            } else {
                int index = suit.getMaxLessThan(topCard.getRank());
                boolean handsOk = suit.last().compareInTrick(rightSuits[suitNum].first()) > 0 &&
                        suit.last().compareInTrick(leftSuits[suitNum].first()) > 0 &&
                        leftSuits[suitNum].size() != 1;
                if (index == 0) {
                    if (trick.trickCards.size() == 1 && handsOk ||
                        trick.trickCards.size() == 2 && theirMin.compareInTrick(suit.get(1)) < 0) {
                        return suit.get(1);     // need to keep min card
                    }
                }
                if (index >= 0) {
                    return suit.get(index);
                }
                return suit.last();     // have to take it
            }
        }
        return null;    // should not be here
    }

    Card declarerDrop() {
/* debug
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
                            card = this.mySuits[hole.suitNum].last();
                        } else if (Util.nextRandInt(2) == 0 &&
                            (declarerDrop == DeclarerDrop.Random || GameManager.RELEASE)) {
                            card = this.mySuits[hole.suitNum].last();
                        }
                    }
                    for (int i = 0; i < this.leftSuits[hole.suitNum].size(); ++i) {
                        Card _card = this.leftSuits[hole.suitNum].remove(i);
                        // todo: check harmlessSuitMove
                        MisereData misereData = defenderEvalDrop(hole, false, leftSuits, rightSuits);
                        this.leftSuits[hole.suitNum].add(i, _card);
                        if (misereData.moveSuitNum > 0) {
                            res = mySuits[hole.suitNum].last();
                            break probe;
                        }
                    }
                }
            } else {
                int minDrop = Integer.MAX_VALUE;
probe:
                for (CardList.ListData hole : holes) {
                    for (int i = 0; i < this.mySuits[hole.suitNum].size(); ++i) {
                        card = this.mySuits[hole.suitNum].remove(i);
                        // todo: check harmlessSuitMove
                        MisereData misereData = defenderEvalDrop(hole, false, leftSuits, rightSuits);
                        this.mySuits[hole.suitNum].add(i, card);
                        if (misereData.moveSuitNum == -1) {
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
        int firstHole = -1;
        int secondHole = -1;
        int dropSuitNum = -1;
        int neutralSuitNum = -1;
        boolean needToDrop = false;
/* debug
        if (trick.number >= 1) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/

        for (MisereData _misereData : allMisereData) {
            delayedDropData.misereData = _misereData;
            delayedDropData.leftSuits = _misereData.leftSuits;
            delayedDropData.rightSuits = _misereData.rightSuits;
            delayedDropData.mySuits = CardList.clone(_misereData.mySuits);
            if (_misereData.dropSuitNum >= 0) {
                if (firstHole == -1) {
                    firstHole = _misereData.hole.suitNum;
                } else {
                    secondHole = _misereData.hole.suitNum;
                }
            } else {
                secondHole = _misereData.hole.suitNum;
            }
            if (_misereData.harmlessHoleMove) {
                delayedDropData.harmlessHoleSuit = _misereData.hole.suitNum;
            }
            suitSum += _misereData.hole.suitNum;
            if (dropSuitNum == -1 && _misereData.dropSuitNum != -1) {
                dropSuitNum = _misereData.dropSuitNum;
                suitSum += dropSuitNum;
            }
            if (_misereData.reversed) {
                ++reversedCount;
            }
            if (_misereData.needToDrop > 0) {
                needToDrop = true;
            }
        }

        if (!delayedMiserData.initialized) {
            if (allMisereData.size() == 2) {
                neutralSuitNum = Card.Suit.SUM - suitSum;
                if (neutralSuitNum >= Card.Suit.NO_SUIT.getValue()) {
                    neutralSuitNum = -1;
                }
            }
            if (!needToDrop) {
                firstHole =
                secondHole =
                delayedDropData.firstHole =
                delayedDropData.secondHole =
                delayedDropData.harmlessHoleSuit =
                delayedDropData.neutralSuitNum =
                delayedDropData.dropSuitNum = -1;
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
            delayedMiserData.dropSuitNum = dropSuitNum;
            delayedMiserData.neutralSuitNum = neutralSuitNum;
            delayedMiserData.firstHole = firstHole;
            delayedMiserData.secondHole = secondHole;
            delayedMiserData.reversed = delayedDropData.reversed;
            delayedMiserData.initialized = true;
        }
        delayedDropData.dropSuitNum = delayedMiserData.dropSuitNum;
        delayedDropData.neutralSuitNum = delayedMiserData.neutralSuitNum;
        delayedDropData.reversed = delayedMiserData.reversed;
        delayedDropData.firstHole = delayedMiserData.firstHole;
        if (secondHole == -1) {
            delayedMiserData.secondHole = -1;    // eventually declarer drops one hole
        }
        delayedDropData.secondHole = delayedMiserData.secondHole;
        return delayedDropData;
    }

    static class DelayedDropData {
        MisereData misereData;
        int harmlessHoleSuit = -1;
        int dropSuitNum = -1;
        int neutralSuitNum = -1;
        int firstHole = -1;
        int secondHole = -1;
        CardList[] mySuits;
        CardList[] leftSuits = null;
        CardList[] rightSuits = null;
        boolean reversed = false;
    }

    static class MisereData {
        final CardList.ListData hole;
        final CardList[] mySuits;
        final CardList[] leftSuits;
        final CardList[] rightSuits;
        boolean reversed;
        Card card;      // card to play
        int moveSuitNum = -1;
        boolean harmlessHoleMove;
        int needToDrop = 0;
        int canDrop;
        int dropSuitNum = -1;
        int meDrop;
        int rightDrop;

        MisereData(CardList.ListData hole, CardList[] mySuits, CardList[] leftSuits, CardList[] rightSuits) {
            this.hole = hole;
            this.mySuits = mySuits;
            this.leftSuits = leftSuits;
            this.rightSuits = rightSuits;
        }
    }

    static class DelayedMiserData {
        boolean initialized = false; // to catch misère with delayed drop
        boolean reversed = false;    // to catch misère with delayed drop
        int initHoles = -1;          // for misère
        int dropSuitNum = -1;        // to catch misère with delayed drop
        int neutralSuitNum = -1;     // to catch misère with delayed drop
        int firstHole = -1;          // to catch misère with delayed drop
        int secondHole = -1;         // to catch misère with delayed drop
    }
}
