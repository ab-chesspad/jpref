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
 * Copyright (C) 2025-2026 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 4/6/2025
 *
  Limitations/things to do:
    Defenders are trying to shove only 1 trick, not as many tricks as possible.
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import static com.ab.jpref.config.Config.ROUND_SIZE;
import com.ab.util.BidData.PlayerBid;
import com.ab.util.Logger;
import com.ab.util.Util;

import java.util.*;

public class MisereBot extends Bot {
//    public static DeclarerDrop declarerDrop = DeclarerDrop.First;
    public static DeclarerDrop declarerDrop = DeclarerDrop.Last;
    public static final boolean DEBUG_LOG = false;

    static final int MAX_EVAL = 1000;   // in ‰
    // https://summoning.ru/games/miser.shtml
    // probabilities to get a trick in misère:
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

    private final Util util = Util.getInstance();
    public final List<CardSet.ListData> holes = new ArrayList<>();

    MisereBot(CardSet... hands) {
        super(hands);
    }

    enum DeclarerDrop {
        First,
        Last,
        Random
    }

    public MisereBot(Player player) {
        super(player);
    }

    // If we care about a single declarer's trick, we cannot summate probabilities
    // return the highest one
    private int getEval4Misere(CardSet cardSet) {
        int eval = 0;
        Iterator<CardSet> suitIterator = cardSet.suitIterator();
        while(suitIterator.hasNext()) {
            CardSet cards = suitIterator.next();
            if (cards.isClean4Misere()) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (Card card : cards) {
                sb.append(card.getRank().toString());
            }
            Integer res = misereTable.get(sb.toString());
            if (res == null) {
                res = MAX_EVAL + cardSet.first().getRank().getValue() - Card.Rank.ACE.getValue();
            }
            if (eval < res) {
                eval = res;
            }
        }
        return eval;
    }

    // eval possible talon out of 22 remaining cards
    boolean evalMisere(int elderHand) {
        int eval = getEval4Misere(myHand);
        if (eval == 0) {
            return true;
        }
        CardSet talonCandidates = myHand.complement();
        CardSet myHand = this.myHand.clone();
        List<PlayerBid> playerBids = new ArrayList<>();
        for (Card card0 : talonCandidates) {
            myHand.add(card0);
            PlayerBid playerBid = new PlayerBid(Config.Bid.BID_MISERE);
            playerBid.value = Integer.MAX_VALUE;    // will be used as probability
            for (Card card1 : myHand) {
                myHand.remove(card1);
                eval = getEval4Misere(myHand);
                myHand.add(card1);
                if (playerBid.value > eval) {
                    playerBid.value = eval;
                    playerBid.drops.clear();
                    playerBid.drops.add(card0);
                    playerBid.drops.add(card1);
                    if (playerBid.value == 0) {
                        break;
                    }
                }
            }
            playerBids.add(playerBid);
            myHand.remove(card0);
        }
        Collections.sort(playerBids, (b1, b2) -> b1.value - b2.value);
        int index = playerBids.size() - 1;
        // rule of 7 cards
        // https://gambiter.ru/pref/mizer-preferans.html
        if (index > 6) {
            index = 6;
        }
        PlayerBid playerBid = playerBids.get(index);
        return playerBid.value < 500;
    }

    @Override
    PlayerBid getDrop(int elderHand) {
        PlayerBid playerBid = new PlayerBid(Config.Bid.BID_MISERE);
        if (debugDrop != null) {
            playerBid.drops = new CardSet(debugDrop);
            return playerBid;
        }
        int drop = this.myHand.size() - ROUND_SIZE;
        if (Bot.playerBid != null) {
            if (drop == 2) {
                return Bot.playerBid;
            }
            if (this.myHand.contains(Bot.playerBid.drops) &&
                this.myHand.equals(gameManager().declarerHand)) {
                return Bot.playerBid;
            }
        }

        if (drop == 0) {
            return playerBid;   // nothing to drop
        }
        Logger.printf(DEBUG_LOG, "getDrop(%d), %s\n", elderHand, this.myHand.toColorString());

        playerBid.value = Integer.MAX_VALUE;    // will be used as probability
        CardSet myHand = this.myHand;
        myHand = myHand.clone();
probes:
        for (Card card0 : myHand) {
            myHand.remove(card0);
            CardSet hand = myHand;
            if (drop == 1) {
                hand = new CardSet(card0);
            }
            for (Card card1 : hand) {
                myHand.remove(card1);
                int eval = getEval4Misere(myHand);
                Logger.printf(DEBUG_LOG, "%s, %s -> %s: %d\n",
                    card0, card1, myHand.toColorString(), eval);
                myHand.add(card1);
                if (playerBid.value > eval) {
                    playerBid.value = eval;
                    playerBid.drops.clear();
                    playerBid.drops.add(card0);
                    playerBid.drops.add(card1);
                    if (playerBid.value == 0) {
                        break probes;
                    }
                }
            }
            myHand.add(card0);
        }
        return playerBid;
    }

    public void getHoles(int declarerNum) {
        holes.clear();
        CardSet myHand = this.myHand.clone();
        CardSet leftHand = this.leftHand.clone();
        CardSet rightHand = this.rightHand.clone();
        Iterator<CardSet> suitIterator = myHand.suitIterator();
        while(suitIterator.hasNext()) {
            CardSet cardSet = suitIterator.next();
            Card.Suit suit = cardSet.first().getSuit();
            CardSet.ListData listData = cardSet.maxUnwantedTricks(leftHand.list(suit), rightHand.list(suit));
            if (listData.maxTheyStart > 0) {
                holes.add(listData);
                if (trick == null || trick.startingSuit == null) {
                    // log it once per trick
                    Logger.printf(DEBUG_LOG, "hole %s\n", listData.thisSuit);
                }
            }
        }
    }

    @Override
    public Card play(Trick trick) {
        Bot.trick = trick;

        getHoles(gameManager().declarerNumber);

        Card res;
        if (trick.getTurn() != gameManager().declarerNumber) {
            // defender
            if (trickList == null) {
                if (holes.isEmpty()) {
                    return gameManager().players[trick.getTurn()].anyCard(trick, false);
                }
                trickList = new TrickList(this, trick, myHand, leftHand, rightHand);
            }
            res = trickList.getCard(trick, myHand, leftHand, rightHand);

            if (res != null) {
                return res;
            }
            return this.anyCard(trick, false);
        }
        return declarerPlay();
    }

    HandResults misereTricks(int elderHand) {
        HandResults handResults = new HandResults();
        handResults.expect = 0;
        Iterator<CardSet> suitIterator = myHand.suitIterator();
        while (suitIterator.hasNext()) {
            CardSet cardList = suitIterator.next();
            Card.Suit suit = cardList.first().getSuit();
            boolean meStart = false;
            CardSet.ListData listData = cardList.maxUnwantedTricks(leftHand.list(suit), rightHand.list(suit), 2);
            handResults.allListData[suit.getValue()] = listData;
            int tricks = listData.maxTheyStart;   // only one can be > 0
            if (listData.maxMeStart > 0 || listData.maxTheyStart > 0) {
                listData.misereEval = getEval4Misere(listData.thisSuit);
            }
            handResults.expect += tricks * listData.misereEval;
            handResults.totalTricks += tricks;
        }
        return handResults;
    }

    private Card declarerPlay() {
        HandResults handResults = misereTricks(trick.getTurn());
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
                return cleanSuit.getOptimalStart(leftHand.list(s), rightHand.list(s));
            }
        } else {
            Card topCard = trick.topCard;
            Card.Suit suit = topCard.getSuit();
            CardSet cardSet = myHand.list(suit);
            Card theirMin = CardSet.min(leftHand.list(suit), rightHand.list(suit));
            // todo: fix this mess
            if (cardSet.size() == 1) {
                return cardSet.first();
            } else if (cardSet.isEmpty()) {
                return declarerDrop();
            } else if (theirMin == null) {
                return cardSet.first();    // the last card might be in trick
            } else if (trick.size() == 1 &&
                    (leftHand.list(suit).size() == 1 || rightHand.list(suit).isEmpty())) {
                Card cL = cardSet.prev(leftHand.list(suit).first());
                Card cT = cardSet.prev(topCard);
                if (cL != null && cL.compareInTrick(cT) < 0) {
                    cL = cT;
                }
                if (cL != null) {
                    return cL;
                } else {
                    return cardSet.anyCard(suit);
                }
            } else {
                Card card = cardSet.prev(topCard);
                Card myMax = cardSet.last();
                boolean handsOk = myMax.compareInTrick(rightHand.list(suit).first()) > 0 &&
                    myMax.compareInTrick(leftHand.list(suit).first()) > 0 &&
                    (leftHand.list(suit).size() != 1 || leftHand.first().compareInTrick(myHand.get(1)) > 0);
                if (card == cardSet.first()) {
                    if (trick.size() == 1 && handsOk ||
                            trick.size() == 2 && theirMin.compareInTrick(cardSet.get(1)) < 0) {
                        return cardSet.get(1);     // need to keep min card
                    }
                }
                if (card != null) {
                    return card;
                }
                return cardSet.last();     // have to take it
            }
        }
        return null;    // should not be here
    }

    Card declarerDrop() {
        CardList cardList = new CardList();
        if (holes.size() == 1) {
            return holes.get(0).thisSuit.last();
        }

        // find good drop
        CardSet[] hands = new CardSet[NOP];
        hands[0] = myHand;
        hands[1] = leftHand;
        hands[2] = rightHand;

        int need2Drop = 10;
        for (CardSet.ListData hole : holes) {
            Card.Suit holeSuit = hole.suit;
            CardSet myList = hands[0].list(holeSuit);
            Card myMin = myList.first();
            CardSet leftList = hands[1].list(holeSuit);
            Card leftMin = leftList.prev(myMin);
            CardSet rightList = hands[2].list(holeSuit);
            Card rightMin = rightList.prev(myMin);
            CardSet handToDrop;
            if (rightMin != null) {
                handToDrop = hands[1];
            } else if (leftMin != null) {
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
            return cardList.get(util.nextRandInt(cardList.size()));
        }
        return cardList.last();
    }

    private MisereBot getMisereBot(TrickList.TrickNode trickNode) {
        MisereBot misereBot = new MisereBot(trickNode.hands);
        Bot.trick = trickNode;

        int rightSize = misereBot.rightHand.size();
        if (trickNode.startingSuit != null) {
            ++rightSize;
        }
        if (misereBot.myHand.size() > rightSize) {
            int elderHand = (this.number - gameManager().elderHand + NOP) % NOP;  // relative to self
            PlayerBid playerBid = misereBot.getDrop(elderHand);
            misereBot.drop(playerBid.drops);
        }
        return misereBot;
    }

    @Override
    CardSet.CardIterator getIterator(TrickList.TrickNode trickNode) {
        int num = trickNode.getTurn();
        if (num == 0) {
            return playForTree(trickNode).iterator();
        }
        CardSet cardSet = trickNode.hands[num].list(trickNode.startingSuit);
        if (cardSet.isEmpty()) {
            cardSet = trickNode.hands[num].list();
        }
        int n1 = (num + 1) % NOP;
        int n2 = (num + 2) % NOP;
        CardSet others = CardSet.union(trickNode.hands[n1], trickNode.hands[n2]);
        others.add(trickNode.cards2CardSet());
        return cardSet.buildReverseIterator(others);
    }

    synchronized CardSet playForTree(TrickList.TrickNode trickNode) {
        MisereBot misereBot = getMisereBot(trickNode);
        Bot.trick = trickNode;
        misereBot.getHoles(0);
        Card card = misereBot.declarerPlay();
        if (!misereBot.myHand.contains(card)) {
            throw new RuntimeException(String.format("err: card %s does not belong to %s",
                card.toColorString(), misereBot.myHand.toColorString()));
        }
        return new CardSet(card);
    }

    @Override
    protected int compare(BaseTrick bestSoFar, BaseTrick probe, int turn) {
        int num = (probe.getStartedBy() + turn) % NOP;   // who played last
        int bestSoFarTricks = bestSoFar.getPastTricks() + bestSoFar.getFutureTricks();
        int probeTricks = probe.getPastTricks() + probe.getFutureTricks();
        int diff = bestSoFarTricks - probeTricks;
        if (num != 0) {
            return diff;
        }
        return -diff;
    }

    protected int compare(int oldTricks, int newTricks) {
        if (oldTricks == -1) {
            return newTricks;
        }
        return  newTricks - oldTricks;
    }
}