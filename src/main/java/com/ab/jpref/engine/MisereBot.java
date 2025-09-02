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
 *
  Limitations/things to do:
Defenders are trying to shove only 1 trick, not as many tricks as possible.
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.util.Logger;
import com.ab.util.Util;

import java.util.*;

public class MisereBot extends Bot implements TrickTree.Declarer {
//    public static DeclarerDrop declarerDrop = DeclarerDrop.First;
    public static DeclarerDrop declarerDrop = DeclarerDrop.Last;
    public static boolean DEBUG_LOG = false;
    public static CardList debugMoves = null;
    public static CardList debugDrop = null;

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

    static TrickTree trickTree = null;

    final GameManager gameManager = GameManager.getInstance();
    final List<CardSet.ListData> holes = new LinkedList<>();
    Bot realPlayer;
    Trick trick;

    public MisereBot() {
        super("probe", 0);
    }

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
        this.number = 0;
        this.realPlayer = realPlayer;
        if (fictitiousBot != realPlayer) {
            HandResults handResults = this.dropForMisere();
            this.drop(handResults.dropped);
        }
    }

    // eval possible talon out of 22 remaining cards
    boolean evalMisere(int elderHand) {
        CardSet badSuit = null;
        Iterator<CardSet> listIterator = myHand.listIterator();
        while (listIterator.hasNext()) {
            CardSet cardSet = listIterator.next();
            Card.Suit suit = cardSet.first().getSuit();
            // todo: analyse elderHand
            boolean meStart = false;
            CardSet.ListData listData = cardSet.maxUnwantedTricks(leftHand.list(suit), rightHand.list(suit), meStart);
            int maxTricks = listData.maxMeStart + listData.maxTheyStart;
            if (maxTricks > 0) {
                badSuit = cardSet;
            }
        }

        if (badSuit == null) {
            // let's go for it even if we have no good starting move
            return true;
        }

        // applying 'the rule of 7 cards'
        // https://gambiter.ru/pref/mizer-preferans.html
        // brute force
        CardSet talonCandidates = CardSet.getDeck();
        talonCandidates.remove(myHand);

        Logger.println(DEBUG_LOG, "probing for misere:");
        int goodCards = 0;
        for (Card card0 : talonCandidates) {
            // add a card from talonCandidates
            CardSet probeHand = myHand.clone();
            probeHand.add(card0);
            for (Card card1 : probeHand) {
                if (card1.equals(card0)) {
                    continue;
                }
                // remove any other card
                probeHand.remove(card1);
                Bot bot = new Bot(probeHand);
                MisereBot probeBot = new MisereBot(bot, bot);
                HandResults handResults = probeBot.misereTricks(elderHand);
                Logger.printf(DEBUG_LOG, "probe %s -> %d tricks\n", bot.toColorString(), handResults.totalTricks);
                if (handResults.totalTricks == 0) {
                    ++goodCards;
                    Logger.printf(DEBUG_LOG, "good: talon %s, drop %s, good %d\n", card0, card1, goodCards);
                    if (goodCards >= 7) {
                        return true;
                    }
                    break;
                }
                probeHand.add(card1);
            }
        }
        return false;
    }

    void getHoles(int declarerNum) {
        holes.clear();
        CardSet myHand = this.myHand.clone();
        CardSet leftHand = this.leftHand.clone();
        CardSet rightHand = this.rightHand.clone();
/*
        if (trick.trickCards.size() == 1 && trick.getTurn() == declarerNum) {
            myHand.add(trick.topCard);
        }
*/
        if (trick.trickCards.size() == 1) {
            int turn = (trick.getTurn() + 2) % NUMBER_OF_PLAYERS;   // who has played
            if (turn == declarerNum) {
                myHand.add(trick.topCard);
            } else if (turn == (declarerNum + 2) % NUMBER_OF_PLAYERS) {
                rightHand.add(trick.topCard);
            }
        }
        Iterator<CardSet> cardListIterator = myHand.listIterator();
        while(cardListIterator.hasNext()) {
            CardSet cardList = cardListIterator.next();
            Card.Suit suit = cardList.first().getSuit();
            CardSet.ListData listData = cardList.maxUnwantedTricks(leftHand.list(suit), rightHand.list(suit));
            if (listData.maxTheyStart > 0) {
                holes.add(listData);
                if (trick.startingSuit == null) {
                    // log it once per trick
                    Logger.printf(DEBUG_LOG, "hole %s\n", listData.thisSuit);
                }
            }
        }
    }

    public Card play(Trick trick) {
        this.trick = trick;
        getHoles(gameManager.declarer.getNumber());

        Card res;
        if (realPlayer.number != gameManager.declarer.getNumber()) {
            // defender
            if (trickTree == null) {
                if (holes.isEmpty()) {
                    return realPlayer.anyCard(trick.startingSuit);
                }
                trickTree = new TrickTree(this, trick, myHand, leftHand, rightHand);
            }
            res = trickTree.getCard(trick, myHand, leftHand, rightHand);

            if (res != null) {
                return res;
            }
            CardSet cardList = new CardSet();
            if (trick.startingSuit != null) {
                cardList = realPlayer.myHand.list(trick.startingSuit);
            }
            if (cardList.isEmpty()) {
                return realPlayer.anyCard();
            }
            int indx = cardList.getMaxLessThan(trick.topCard);
            if (indx < 0) {
                indx = 0;
            }
            return cardList.get(indx);
        }
        return declarerPlayMisere();
    }

    HandResults dropForMisere() {
        HandResults handResults = new HandResults();
        handResults.expect = Integer.MAX_VALUE;
        if (debugDrop != null) {
            handResults.dropped = debugDrop;
            return handResults;
        }
        // optimistically assume that there will be a way to pass turn to another palyer
        int _elderHand = 2;
        CardSet dropCandidates = gameManager.declarerHand.clone();
        CardSet myHand = gameManager.initialDeclarerHand.clone();

probes:
        for (Card card0 : dropCandidates) {
            dropCandidates.remove(card0);
            for (Card card1 : dropCandidates) {
                CardSet mySet = myHand.clone(); //??
                mySet.remove(card0);
                mySet.remove(card1);
                Bot _bot = new Bot(mySet);
                MisereBot bot = new MisereBot(_bot, _bot);
                HandResults _handResults = bot.misereTricks(_elderHand);
                Logger.printf(DEBUG_LOG, "probe drop %s, %s, %s, tricks %d, eval=%d\n",
                    card0, card1, bot.toColorString(), _handResults.totalTricks, _handResults.expect);
                if (handResults.expect > _handResults.expect) {
                    handResults = _handResults;
                    handResults.dropped.clear();
                    handResults.dropped.add(card0);
                    handResults.dropped.add(card1);
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
        Iterator<CardSet> listIterator = myHand.listIterator();
        while (listIterator.hasNext()) {
            CardSet cardList = listIterator.next();
            Card.Suit suit = cardList.first().getSuit();
//            boolean meStart = elderHand == number;
            boolean meStart = false;
            CardSet.ListData listData = cardList.maxUnwantedTricks(leftHand.list(suit), rightHand.list(suit), meStart);
            handResults.allListData[suit.getValue()] = listData;
//            int tricks = listData.maxMeStart + listData.maxTheyStart;   // only one can be > 0
            int tricks = listData.maxTheyStart;   // only one can be > 0
            if (listData.maxMeStart > 0 || listData.maxTheyStart > 0) {
                listData.misereEval = getEval4Misere(listData.thisSuit);
            }
            handResults.expect += tricks * listData.misereEval;
            handResults.totalTricks += tricks;
        }
        return handResults;
    }

    private Card declarerPlayMisere() {
        HandResults handResults = misereTricks(trick.getTurn());
/* debug
        if ("♠789 ♣8 ♥8 ".equals(this.toString()) && trick.number >= 4) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/
        CardSet.ListData bestListData = null;
        if (trick.startingSuit == null) {
            for (CardSet.ListData listData : handResults.allListData) {
                if (listData == null) {
                    continue;
                }
                CardSet cardList = listData.thisSuit;
                if (cardList.isEmpty()) {
                    continue;
                }
                for (CardSet.ListData hole: holes) {
                    if (hole.suit.equals(listData.suit) && listData.thisSuit.size() == 1) {
                        return listData.thisSuit.first();
                    }
                }
                if (bestListData == null || bestListData.maxMeStart > listData.maxMeStart) {
                    bestListData = listData;
                }
            }
            if (bestListData != null) {
                CardSet cleanSuit = bestListData.thisSuit;
                Card.Suit s = bestListData.suit;
                int j = cleanSuit.getOptimalStart(leftHand.list(s), rightHand.list(s));
                return cleanSuit.get(j);
            }
        } else {
            Card topCard = trick.topCard;
            Card.Suit suit = topCard.getSuit();
            CardSet cardList = myHand.list(suit);
            Card theirMin = theirMin(suit);
            if (cardList.size() == 1) {
                return cardList.first();
            } else if (cardList.isEmpty()) {
                return declarerDrop();
            } else if (theirMin == null) {
                return cardList.first();    // the last card might be in trick
            } else {
                int index = cardList.getMaxLessThan(topCard);
                Card myMax = cardList.last();
                boolean handsOk = myMax.compareInTrick(rightHand.list(suit).first()) > 0 &&
                    myMax.compareInTrick(leftHand.list(suit).first()) > 0 &&
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
/* debug
        if ("♠789 ♣8 ♥8 ".equals(this.toString()) && trick.number >= 5) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/
        CardList cardList = new CardList();
        if (holes.size() == 1) {
            return holes.get(0).thisSuit.last();
        }

        // find good drop
        CardSet[] hands = new CardSet[NUMBER_OF_PLAYERS];
        hands[0] = myHand;
        hands[1] = leftHand;
        hands[2] = rightHand;

        int need2Drop = 10;
        for (CardSet.ListData hole : holes) {
            Card.Suit holeSuit = hole.suit;
            CardSet myList = hands[0].list(holeSuit);
            Card myMin = myList.first();
            CardSet leftList = hands[1].list(holeSuit);
            int leftMin = leftList.getMaxLessThan(myMin);
            CardSet rightList = hands[2].list(holeSuit);
            int rightMin = rightList.getMaxLessThan(myMin);
            CardSet handToDrop;
            if (rightMin >= 0) {
                handToDrop = hands[1];
            } else if (leftMin >= 0) {
                handToDrop = hands[2];
            } else {
                continue;
            }
            int _need2Drop = handToDrop.list(holeSuit).size() - myList.size() + 1;
            if (need2Drop > _need2Drop) {
                need2Drop = _need2Drop;
                cardList.clear();
                cardList.add(myList.last());
            } else if (need2Drop == _need2Drop) {
                cardList.add(myList.last());
            }
        }

        if (cardList.isEmpty()) {
            return anyCard();
        }
        if (declarerDrop == DeclarerDrop.First) {
            return cardList.first();
        }
        if (declarerDrop == DeclarerDrop.Random || GameManager.RELEASE) {
            return cardList.get(Util.nextRandInt(cardList.size()));
        }
        return cardList.last();
    }

    private int getEval4Misere(CardSet cardSet) {
        StringBuilder sb = new StringBuilder();
        for (Card card : cardSet) {
            sb.append(card.getRank().toString());
        }
        Integer res = misereTable.get(sb.toString());
        if (res == null) {
            return MAX_EVAL + cardSet.first().getRank().getValue() - Card.Rank.ACE.getValue();
        }
        return res;
    }

    @Override
    public Card playForTree(TrickTree.TrickNode trickNode) {
        MisereBot misereBot = new MisereBot();
        misereBot.myHand = trickNode.hands[0].clone();
        misereBot.leftHand = trickNode.hands[1].clone();
        misereBot.rightHand = trickNode.hands[2].clone();
        misereBot.trick = trickNode;

        int rightSize = misereBot.rightHand.size();
        if (trickNode.startingSuit != null) {
            ++rightSize;
        }
        if (misereBot.myHand.size() > rightSize) {
            HandResults handResults = misereBot.dropForMisere();
            misereBot.drop(handResults.dropped);
        }

/*
        if (CardSet.toString(trickNode.hands).equals("♠789A ♣89X ♦7  ♠XJ ♣7J ♦9Q ♥9X  ♠QK ♣QKA ♦8K") &&
            trickNode.toString().equals("♦J")) {
            DEBUG_LOG = DEBUG_LOG;
        }
//*/
        misereBot.getHoles(0);
        Card card = misereBot.declarerPlayMisere();
        return card;
    }

    @Override
    public boolean keepDetails(TrickTree.TrickNode trickNode) {
        if (trickNode.siblingIndex == 0) {
            return true;
        }
        TrickTree.TrickNode prev = trickNode.parent.children.get(trickNode.siblingIndex - 1);
        Card.Suit suit = trickNode.startingSuit;
        for (CardSet.ListData hole : holes) {
            if (!suit.equals(hole.suit)) {
                continue;
            }
            if (!prev.trickCards.get(0).equals(trickNode.trickCards.get(0))) {
                return true;    // starting cards are different
            }
            if (prev.top != trickNode.top) {
                // position: ♠7 ♣89 ♦7X   ♣7 ♦9Q ♥JQ   ♣KA ♦8JK, 2
                // ♦879 and ♦87Q -> different results
                return true;    // top cards are different
            }
        }
        return false;
    }

    @Override
    public CardSet refineDrop(CardSet hand) {
        MisereBot _misereBot = new MisereBot();
        _misereBot.myHand = hand.clone();
        Bot.HandResults handResults = _misereBot.dropForMisere();
        _misereBot.myHand.remove(handResults.dropped);
        return _misereBot.myHand;
    }

    @Override
    public boolean stopTreeBuild(TrickTree.TrickNode trickNode) {
        return trickNode.top == 0;
    }
}