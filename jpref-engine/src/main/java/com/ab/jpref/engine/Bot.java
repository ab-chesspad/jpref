/*  This file is part of JPref project.
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
 * Created: 12/22/2024.
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.jpref.config.Config.Bid;
import com.ab.util.Bidder;
import com.ab.util.Bidder.PlayerBid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Bot extends Player implements Serializable {
    public static CardList debugDrop = null;

    // when declarer is Bot, it uses the same logic for declarer and defenders
    // so playerBid is the same and can be shared
    static PlayerBid playerBid;

    public transient Bot targetBot;  // either forTricksBot or misereBot

    public Bot(int number) {
        this.number = number;
    }

    public Bot(Player realPlayer) {
        init(realPlayer);
        if (realPlayer instanceof Bot) {
            // the declarer's avatar keeps its own drop, the defenders must not guess it
            targetBot = ((Bot) realPlayer).targetBot;
        }
    }

    // for subclasses that init() themselves, e.g. the ForTricksBot and MisereBot singletons;
    // without it an implicit super() picks Bot(CardSet...) with no hands
    protected Bot() {}

    protected Bot(CardSet... hands) {
        this.myHand.set(hands[0]);
        if (hands.length > 1) {
            this.leftHand.set(hands[1]);
            this.rightHand.set(hands[2]);
        }
    }

    @Override
    public void clear() {
        super.clear();
        playerBid = null;
        if (targetBot != null) {
            ForTricksBot.getInstance().clear();
            MisereBot.getInstance().clear();
        }
        targetBot = null;
    }

    @Override
    public void setTricks(int tricks) {
        super.setTricks(tricks);
        if (gameManager().declarerNumber == this.number &&
            this.getClass().equals(Bot.class) && targetBot != null) {
            targetBot.setTricks(tricks);
        }
    }

    @Override
    public Bid getBid(Bid minBid, int elderHand) {
        elderHand = (this.number - elderHand + NOP) % NOP;  // relative to self
        ForTricksBot forTricksBot = ForTricksBot.getInstance(this);
        PlayerBid playerBid = forTricksBot.getMaxPlayerBid(minBid, elderHand);
        Bid bid = playerBid.toBid();
        MisereBot misereBot = MisereBot.getInstance(this);
        if (bid.compareTo(Bid.BID_XS) < 0 && misereBot.evalMisere(elderHand)) {
            this.bid = Bid.BID_MISERE;
            return Bid.BID_MISERE;
        }
        if (bid.compareTo(minBid) >= 0) {
            bid = minBid;
        } else {
            bid = Bid.BID_PASS;
        }
        this.bid = bid;
        return bid;
    }

    @Override
    public void acknowledge() {}         // do nothing

    // stab to be overridden in MisereBot and ForTricksBot
    PlayerBid getDrop(int elderHand, int nDrops, Trick trick) {
        throw new RuntimeException("stub!");
    }

    // minimax criteria, stub to be overridden in MisereBot and ForTricksBot
    protected int compare(long bestSoFarTrickData, long probeTrickData, int index) {
        throw new RuntimeException("stub!");
    }

    public static final long BACKWARD_FLAG = 0x100000000L;

    long bm4Iteration(TrickList.TrickNode trickNode) {
        throw new RuntimeException("stub!");
    }

    @Override
    public Config.Bid drop() {
        return bid;
    }

    // 12 cards
    @Override
    public void declareRound(Bid minBid, int _elderHand, Trick trick) {
        int elderHand = (this.number - _elderHand + NOP) % NOP;  // relative to self
        if (Bid.BID_MISERE.equals(minBid)) {
            targetBot = MisereBot.getInstance(this);
        } else {
            targetBot = ForTricksBot.getInstance(this);
        }
        Bidder.PlayerBid playerBid = targetBot.getDrop(elderHand, 2, trick);
        Bot.playerBid = playerBid;
        this.bid = targetBot.getBid();
        drop(playerBid.drops);
        targetBot.drop(playerBid.drops);
    }

    @Override
    public void respondOnDeclaration() {
        Bid bid;
        int player1 = (gameManager().declarerNumber + 1) % NOP;
        int player2 = (gameManager().declarerNumber + 2) % NOP;
        // avoid playing with closed cards!
        if (this.number == player1) {
            Bid otherBid = gameManager().getBid(player2);
            if (otherBid.equals(Bid.BID_HALF_WHIST)) {
                bid = Bid.BID_WHIST_LAYING;
            } else {
                bid = Bid.BID_PASS;
            }
        } else {
            Bid otherBid = gameManager().getBid(player1);
            if (otherBid.equals(Bid.BID_WHIST)) {
                bid = Bid.BID_PASS;
            } else {
                bid = Bid.BID_WHIST_LAYING;
            }
        }
        this.bid = bid;
        halfWhist = bid.equals(Bid.BID_HALF_WHIST);
    }

    @Override
    public Card play(Trick trick) {
        if (myHand.size() == 1) {
            return myHand.first();
        }
        if (trick.minBid == null) {
            return playAllPass(trick);
        }
        if (targetBot == null) {
            // a defender: a bot declarer's target knows the declarer's hand
            Player declarer = gameManager().getDeclarer();
            if (declarer instanceof Bot && ((Bot) declarer).targetBot != null) {
                targetBot = ((Bot) declarer).targetBot;
            } else {
                // the other defender has guessed the drop already, a new guess would replace
                // the shared target's hand, and its list would not match it anymore
                for (Player player : gameManager().getPlayers()) {
                    if (player != this && player instanceof Bot && ((Bot) player).targetBot != null) {
                        targetBot = ((Bot) player).targetBot;
                    }
                }
            }
            if (targetBot == null) {
                // when declarer is human; todo: verify declaration!
                Bot fictitiousBot = (Bot) gameManager().getDeclarerForDefender();
                fictitiousBot.declareRound(gameManager().getMinBid(), trick.getStartedBy(), trick);
                targetBot = fictitiousBot.targetBot;
            }
        }
        return targetBot.play(trick);
    }

    // the card for this trick when the list's card relies on what the player cannot know,
    // see MisereBot.robustCard(); the list's card by default
    Card robustCard(Trick trick, Card listCard) {
        return listCard;
    }

    Card playAllPass1stHand(Trick trick) {
        Card problems = null;
        Card anycard = null;
        CardSet.ListData noProblemSuit = new CardSet.ListData(null);
        noProblemSuit.maxMeStart = Integer.MAX_VALUE;
        // ♣78A ♦XK ♥7  ♠8 ♦789A ♥9  ♠9JQ ♣9 ♥8J
        //  ^ why not?
        // ♣8JQK ♦89 ♥79   ♠A ♣79 ♦XJKA ♥8   ♠J ♣XA ♦7 ♥XJQA 1 - must: ♠A, ♦X and ♦XJ?
        // ♠7 ♦9XK   ♣A ♥9JK   ♠9Q ♦8 ♥7
        // todo: test-2 plays ♠Q, but the best move is ♥7

        int bitset = 0;
        while ((bitset = CardSet.bm4NextSuit(myHand.getBitmap(), bitset)) != 0) {
            // todo: use bitmaps only
            CardSet cardSet = new CardSet(bitset);
            Card.Suit suit = cardSet.first().getSuit();
            anycard = cardSet.last();
            Card theirMin = CardSet.min(leftHand.list(suit), rightHand.list(suit));
            if (theirMin == null) {
                continue;       // 'козырять' не надо
            }

            CardSet.ListData listData = cardSet.getUnwantedTricks(leftHand.list(suit), rightHand.list(suit));
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
                    CardSet checkHand = null;
                    if (leftHand.list(suit).isEmpty()) {
                        checkHand = rightHand;
                    } else if (rightHand.list(suit).isEmpty()) {
                        checkHand = leftHand;
                    }

                    if (checkHand == null) {
                        // a suit with unavoidable tricks stays the choice, the one
                        // without tricks is kept as an exit for later,
                        // unless the suit with tricks has an exit of its own
                        if (noProblemSuit.suit == null ||
                                noProblemSuit.maxMeStart == 0 &&
                                noProblemSuit.cardsLeft > listData.cardsLeft ||
                                noProblemSuit.maxMeStart > 0 && exitSuit(noProblemSuit.suit)) {
                            noProblemSuit = listData;
                        }
                    } else {
                        CardSet dummy = new CardSet();
                        for (Card.Suit checkSuit : Card.Suit.values()) {
                            if (checkSuit.equals(suit)) {
                                continue;
                            }
                            CardSet cardSet1 = this.myHand.list(checkSuit);
                            if (cardSet1.isEmpty()) {
                                continue;
                            }
                            Card _theirMin = CardSet.min(leftHand.list(checkSuit), rightHand.list(checkSuit));
                            if (cardSet1.first().compareInTrick(_theirMin) < 0) {
                                continue; // no point to start with this cardSet, I'll have a chance to do it later
                            }

                            CardSet.ListData listData1 =
                                cardSet1.maxUnwantedTricks(dummy, checkHand.list(checkSuit),2);
                            if (listData1.maxTheyStart > 0) {
                                //?? I hope I will shove cardSet.first() with the next trick
                                if (cardSet1.size() > 1 && cardSet1.prevIndex(cardSet1.get(1)) == 0) {
                                    return cardSet1.get(1);
                                }
                                return cardSet1.last();
                            }
                        }
                        // todo: check if she can shove a trick to the 3rd player, and that one can get me
                        return cardSet.first();
                    }
                } else if (noProblemSuit.suit != null && noProblemSuit.maxMeStart == 0 && exitSuit(suit)) {
                    // the zero-trick suit stays the choice
                } else if (noProblemSuit.maxMeStart == 0 ||
                        noProblemSuit.maxMeStart > listData.maxMeStart && noProblemSuitSize < cardSet.size()) {
                    if (!(listData.maxMeStart > 0 && listData.cardsLeft >= 0
                        && noProblemSuit.suit != null) || noProblemSuit.maxMeStart == 0) {
                        noProblemSuit = listData;
                    }
                } else if (listData.maxMeStart > 0 && cardSet.size() >= 4) {
                    noProblemSuit = listData;
                } else { // if (noProblemSuit.maxMeStart == listData.maxMeStart) {
                    // compare the highest unseen cards below my lowest, by rank: the suits differ
                    Card prev = unseenBelow(_suit);
                    Card curr = unseenBelow(suit);
                    // a suit with my lowest card below all theirs is kept as an exit for later
                    if (curr != null && (prev == null || curr.getRank().compare(prev.getRank()) > 0)) {
                        noProblemSuit = listData;
                    } else if (curr == null && prev == null && cardSet.size() > myHand.list(_suit).size()) {
                        noProblemSuit = listData;
                    }
                }
            } else {
                // problems
                Card myMax = absMax(suit);
                if (myMax != null) {
                    return myMax;
                }

                if (cardSet.size() > 1) {
                    if (!listData.good && listData.maxMeStart > listData.maxTheyStart) {
                        if (rightHand.list(suit).size() > 2 &&
                                rightHand.list(suit).prevIndex(cardSet.first()) == 0) {
                            // there is a single card less than my min, so better take this trick
                            problems = cardSet.get(1);
                        } else {
                            problems = cardSet.first();     // give them a chance to take it
                        }
                    } else {
                        problems = cardSet.get(1);     // ?? second from bottom
                    }
                } else {
                    problems = cardSet.first();
                }
            }
        }

        Card res;
        if (noProblemSuit.suit != null) {
            Card.Suit suit = noProblemSuit.suit;
            CardSet cardList = myHand.list(suit);
            CardSet theirs = new CardSet(leftHand.list(suit), rightHand.list(suit));
            if (cardList.size() > 1 &&
                    (rightHand.list(suit).size() > 1 || leftHand.list(suit).size() > 1) &&
                    theirs.prevIndex(cardList.get(1)) <= 0) {
                // the second card only with at most one unseen card below it, ♥7QA - ♥89XJK leads ♥7
                res = cardList.get(1);
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

    // a few cards left: solve all the possible splits of the unseen cards
    private Card playAllPassEndgame(Trick trick) {
        int myCount = myHand.size();
        if (trick.getNumber() < 2 || myCount > AllPassEndgame.MAX_CARDS || myCount < 2) {
            return null;    // the talon tricks, too many cards, or nothing to choose
        }
        return new AllPassEndgame().bestCard(myHand.getBitmap(), leftHand.getBitmap(), rightHand.getBitmap(), trick);
    }

    // the top card whose discard saves the most unwanted tricks, whoever starts the suit;
    // of equal savings not a nearly safe one, then the one from the shorter suit, then the higher one.
    // null when none saves any
    // two cards, they hold cards below and above my lowest: dropping my top card does not help,
    // it can go under their higher cards and my lowest can be forced anyway, ♣9K - ♣7JA keeps ♣K.
    // Not when my top card is above all theirs, it is a guaranteed trick, ♣QA - ♣789XJK drops ♣A.
    // In 3+ cards the low ones cannot all go under, ♦89Q - ♦7XJKA drops ♦Q
    private boolean keepTop(CardSet cardSet, CardSet theirs) {
        Card myMin = cardSet.first();
        // a card below my lowest and a card above my top (then also above my lowest)
        return cardSet.size() == 2 && theirs.prev(myMin) != null && theirs.next(cardSet.last()) != null;
    }

    private Card bestSavingDiscard() {
        Card res = null;
        boolean resGuaranteed = false;
        int bestSaving = -1;
        int bitset = 0;
        while ((bitset = CardSet.bm4NextSuit(myHand.getBitmap(), bitset)) != 0) {
            CardSet cardSet = new CardSet(bitset);
            Card.Suit suit = cardSet.first().getSuit();
            CardSet left = leftHand.list(suit);
            CardSet right = rightHand.list(suit);
            if (CardSet.min(left, right) == null) {
                continue;
            }
            CardSet theirs = new CardSet(left, right);
            if (keepTop(cardSet, theirs)) {
                continue;
            }
            CardSet.ListData full = cardSet.getUnwantedTricks(left, right);
            CardSet rest = new CardSet(cardSet);
            Card top = rest.last();
            rest.remove(top);
            int saving = full.maxMeStart + full.maxTheyStart;
            if (!rest.isEmpty()) {
                CardSet.ListData less = rest.getUnwantedTricks(left, right);
                saving -= less.maxMeStart + less.maxTheyStart;
            }
            // a top card above all theirs is a guaranteed trick, dropping it is never wasted, ♠9JQ - ♠8
            boolean guaranteed = theirs.next(top) == null;
            if (saving <= 0 && !guaranteed) {
                continue;
            }
            boolean better = saving > bestSaving;
            if (!better && saving == bestSaving && res != null && guaranteed != resGuaranteed) {
                better = guaranteed;
            } else if (!better && saving == bestSaving && res != null) {
                int shorter = myHand.list(res.getSuit()).size() - cardSet.size();
                // a top card with at most one unseen card below is nearly safe, ♥79 - ♥8XJQA
                boolean topSafe = new CardSet(left, right).prevIndex(top) <= 0;
                boolean resSafe = new CardSet(leftHand.list(res.getSuit()), rightHand.list(res.getSuit()))
                    .prevIndex(res) <= 0;
                if (topSafe != resSafe) {
                    better = resSafe;
                } else {
                    better = shorter > 0 || shorter == 0 && top.getRank().compare(res.getRank()) > 0;
                }
            }
            if (better) {
                bestSaving = saving;
                res = top;
                resGuaranteed = guaranteed;
            }
        }
        return res;
    }

    private Card drop4AllPass(Trick trick) {
        Card saving = bestSavingDiscard();
        if (saving != null) {
            return saving;
        }
        CardSet.ListData problemList = null;
        CardSet.ListData noProblemList = null;
        Card res;

        int bitset = 0;
        while ((bitset = CardSet.bm4NextSuit(myHand.getBitmap(), bitset)) != 0) {
            // todo: use bitmaps only
            CardSet cardSet = new CardSet(bitset);
            Card.Suit suit = cardSet.first().getSuit();
            Card theirMin = CardSet.min(leftHand.list(suit), rightHand.list(suit));
            if (theirMin == null) {
                continue;
            }
            CardSet.ListData listData = cardSet.getUnwantedTricks(leftHand.list(suit), rightHand.list(suit));
            if (listData.maxMeStart == listData.maxTheyStart) {
                // ok1stMove, no problem
                // select the cardSet1 with the most unwanted tricks
                if (noProblemList == null || noProblemList.maxMeStart < listData.maxMeStart) {
                    noProblemList = listData;
                } else if (noProblemList.maxMeStart == listData.maxMeStart) {
                    if (noProblemList.thisSuit.size() > cardSet.size()) {
                        // select the shortest cardSet1
                        noProblemList = listData;
                    } else if (noProblemList.thisSuit.last().getRank().compare(cardSet.last().getRank()) < 0) {
                        // select the cardSet1 with the card of the highest rank
                        noProblemList = listData;
                    }
                }
            } else {
                // problems
                // quick and dirty:
                if (problemList == null || problemList.maxMeStart < listData.maxMeStart) {
                    problemList = listData;
                }
            }
        }

        CardSet probe = null;
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
            CardSet cardList = rightHand.list(suit);
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
                int bitmap = myHand.getBitmap() & ~probe.getBitmap();
                Card card = CardSet.last(bitmap);
                if (card != null) {
                    return card;
                }
            }
        }

        if (noProblemList != null) {
            Card theirMin = CardSet.min(leftHand.list(noProblemList.suit), rightHand.list(noProblemList.suit));
            if (noProblemList.thisSuit.last().compareInTrick(theirMin) < 0 &&
                problemList != null && problemList.thisSuit.size() > 2) {
                res = problemList.thisSuit.last();
            } else {
                res = noProblemList.thisSuit.last();
            }
        } else if (problemList != null) {
            res = problemList.thisSuit.last();
        } else {
            // any:
            return myHand.anyCard();
        }
        return res;
    }

    // the top card of the lowest group of my cards with no unseen card between them
    private Card lowestGroupTop(CardSet cardSet) {
        Card.Suit suit = cardSet.first().getSuit();
        CardSet theirs = new CardSet(leftHand.list(suit), rightHand.list(suit));
        Card res = cardSet.first();
        Card next;
        while ((next = cardSet.next(res)) != null && theirs.prev(next) == theirs.prev(res)) {
            res = next;
        }
        return res;
    }

    // my lowest card is below all theirs: when they lead the suit I play the higher cards first
    // and keep the lowest as an exit, so it is better not to lead it, ♠7J - ♠89XQK
    private boolean exitSuit(Card.Suit suit) {
        Card theirMin = CardSet.min(leftHand.list(suit), rightHand.list(suit));
        return myHand.list(suit).first().compareInTrick(theirMin) < 0;
    }

    // the highest unseen card of the suit below my lowest one
    private Card unseenBelow(Card.Suit suit) {
        CardSet theirs = new CardSet(leftHand.list(suit), rightHand.list(suit));
        return theirs.prev(myHand.list(suit).first());
    }

    Card absMax(Card.Suit suit) {
        CardSet cardList = myHand.list(suit);

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

    // https://review-pref.ru/school/147/111/
    // all-pass: https://review-pref.ru/school/145/17/
    public Card playAllPass(Trick trick) {
        Card endgame = playAllPassEndgame(trick);
        if (endgame != null) {
            return endgame;
        }
        Card res;
        if (trick.startingSuit == null) {
            return playAllPass1stHand(trick);
        }

        // 2nd and 3rd hand
        Card.Suit suit = trick.startingSuit;
        CardSet cardSet = this.myHand.list(suit);
        if (cardSet.isEmpty()) {
            return drop4AllPass(trick);
        }
        Card myMin = cardSet.first();
        if (cardSet.size() == 1) {
            return myMin;
        }

        Card myMax = cardSet.last();
        Card topTrickCard = trick.topCard;

        Card theirMin = CardSet.min(leftHand.list(suit), rightHand.list(suit));
        if (topTrickCard == null && theirMin != null && myMin.compareInTrick(theirMin) > 0) {
            // 1st in a talon trick, they can go under my lowest: the top of my lowest group,
            // the cards with no unseen card between them are equal, ♠9JK - ♠78QA, talon ♠X -> ♠J
            return lowestGroupTop(cardSet);
        }
        if (topTrickCard == null && theirMin != null && cardSet.size() > 1) {
            // 1st in a talon trick, my lowest card is the lowest: keep it as an exit and play the next one
            // if at most one unseen card is below it and they hold enough cards to follow,
            // ♥79J - ♥8XKA, talon ♥Q -> ♥9
            CardSet theirs = new CardSet(leftHand.list(suit), rightHand.list(suit));
            Card second = cardSet.get(1);
            if (theirs.prevIndex(second) <= 0 && theirs.size() >= 3) {
                return second;
            }
        }
        if (topTrickCard != null && topTrickCard.compareInTrick(myMin) > 0 &&
            (totalCards() == 2 || theirMin == null || theirMin.compareInTrick(myMax) > 0)) {
            return cardSet.prev(topTrickCard);
        }
        CardSet.ListData listData = cardSet.maxUnwantedTricks(leftHand.list(suit), rightHand.list(suit), 2);
        if (topTrickCard != null && listData.maxTheyStart == 0) {
            Card card = cardSet.prev(topTrickCard);
            if (card != null) {
                return card;
            }
            // if i == 0 then only one my card is smaller, so let's keep it
            if (cardSet.size() > 1) {
                if (rightHand.list(suit).size() > 2 &&
                        rightHand.list(suit).prevIndex(myMax) == 0) {
                    // there is a single card less than my max, let's risk it
                    return myMin;
                }
                return cardSet.get(1);
            }
            return myMax;     // all my cards are greater than theirs in the trick
        }

        if (trick.size() == 2) {
            // 3rd hand
            if (myMin.compareInTrick(trick.topCard) > 0 ||
                    myMax.compareInTrick(trick.topCard) < 0) {
                // all my cards are either greater than trick or less than trick
                return myMax;
            }
            // ♣8JKA ♥J   ♣7Q ♦79X   ♣9X ♥89X
            //              ^          ^   -> 3rd hand, intercept with ♣A, then ♥J and ♣8 ?!
        }

        // 2nd  and 3rd hand
        if (cardSet.size() == 2) {
            if (!rightHand.list(suit).isEmpty()) {
                // XK - Q -> X, XK - A -> X
                //                       me:
                // ♦7X ♥7X   ♠JQ ♦9 ♥9   ♦8A ♥8A
                //                  ^
                theirMin = CardSet.min(leftHand.list(suit), rightHand.list(suit));
                if (rightHand.list(suit).size() == 1 ||
                        myMin.compareInTrick(theirMin) > 0 && myMax.compareInTrick(topTrickCard) > 0) {

                    if (rightHand.list(suit).size() > 2 &&
                            rightHand.list(suit).prevIndex(myMin) == 0) {
                            // there is a single card less than my min, better take this trick
                        return cardSet.get(1);
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

        // 2nd  and 3rd hand
        if (myMin.compareInTrick(topTrickCard) > 0) {
            if (myMin.compareTo(theirMin) < 0) {
                res = myMin;
            } else {
                Card theirMax = CardSet.max(leftHand.list(suit), rightHand.list(suit));
                if (theirMax == null) {
                    return myHand.list(suit).first();   // they do not have this suit
                }
                res = cardSet.prev(theirMax);
                if (res == null) {
                    res = myMax;
                }
            }
        } else {
            int left = leftHand.list(suit).prevIndex(myMin);
            if (cardSet.size() == 2 && left <= 2 && !rightHand.list(suit).isEmpty()) {
                res = myMax;
            } else {
                // go under the top card even with a single unseen card below my lowest:
                // taking the trick now is certain, my lowest may still avoid one later, ♦9JQ - ♦7KA, ♦8 ♦X -> ♦9
                // ♣8JKA ♥J   ♠J ♣7Q ♦79   ♣9X ♥789   -> ♣Q ♣X, should take it, play ♠J and ♣8
                // instead she plays ♣J, then ♣7 ♣9 ♣8 and gets 4 tricks
                // on the other hand, for ♣8JKA ♥J   ♠J ♣9Q ♦79   ♣7X ♥789 it does not work
                res = cardSet.prev(topTrickCard);
            }
        }

        return res;
    }

    public final Random myRand = new Random();

    public int nextRandInt(int max) {
        return myRand.nextInt(max);
    }


    static class HandResults {
        final CardSet.ListData[] allListData = new CardSet.ListData[Card.TOTAL_SUITS];
        int totalTricks = 0;    // the best
        int expect = Integer.MAX_VALUE;
    }

    static final int MIN_DROP_GUESSES = 4;  // see dropGuesses()
    static final int MAX_DROP_GUESSES = 8;

    // the risk of the declarer's hand after a drop, the lower ones are the likelier drops,
    // a tie-break in dropGuesses()
    int dropRisk(CardSet dealtHand) {
        return 0;
    }

    // the declarer can drop the card, see dropGuesses()
    boolean droppable(Card card) {
        return true;
    }

    // one card leads the counts of guesses by more than the guesses left can add to any other;
    // on a tie the tie-breaks need all the guesses
    static boolean decided(int[] caught, int remaining) {
        int first = -1;
        int second = -1;
        for (int c : caught) {
            if (c > first) {
                second = first;
                first = c;
            } else if (c > second) {
                second = c;
            }
        }
        return first - second > remaining;
    }

    // the declarer's hands for various drops, the list's guess first
    List<CardSet> dropGuesses(Trick trick) {
        GameManager gameManager = gameManager();
        List<CardSet> res = new ArrayList<>();
        CardSet knownHand = new CardSet(gameManager.declarerHand);
        int declarerSize = TrickList.getInstance().declarerSize(trick);
        int diff = knownHand.size() - declarerSize;
        if (diff <= 0) {
            return res;     // all known
        }
        if (knownHand.contains(myHand) && myHand.size() == declarerSize) {
            res.add(new CardSet(myHand));
        }
        // the dropped cards not shown yet, 1 or 2
        List<CardSet> hands = new ArrayList<>();
        List<Integer> evals = new ArrayList<>();
        CardList cards = knownHand.toCardList();
        for (int i = 0; i < cards.size(); ++i) {
            if (!droppable(cards.get(i))) {
                continue;
            }
            // one card: j == i only, the pairs after it would leave the declarer a card short
            int end = diff == 1 ? i + 1 : cards.size();
            for (int j = diff == 1 ? i : i + 1; j < end; ++j) {
                if (!droppable(cards.get(j))) {
                    continue;
                }
                CardSet hand = new CardSet(knownHand);
                hand.remove(cards.get(i));
                hand.remove(cards.get(j));
                // evaluate the hand as it was after the drop
                CardSet dealtHand = new CardSet(hand);
                dealtHand.add(gameManager.declarerPlayed);
                int eval = dropRisk(dealtHand);
                int k = evals.size();
                while (k > 0 && evals.get(k - 1) > eval) {
                    --k;
                }
                evals.add(k, eval);
                hands.add(k, hand);
            }
        }
        // not the likely drops, the various ones, even illogical: every card the declarer can hold
        // is dropped under some guess and kept under some other, the least risks can all drop the
        // same card: ♣QK, ♣K ♠7, ♣K ♦J... and miss the declarer that kept ♣K, dropped ♣Q ♦X;
        // each next guess covers the most cards not covered yet, on a tie the least risk
        CardSet notDropped = new CardSet(knownHand);
        CardSet notKept = new CardSet(knownHand);
        for (CardSet hand : res) {
            notDropped.remove(knownHand.diff(hand));
            notKept.remove(hand);
        }
        while (res.size() < MAX_DROP_GUESSES) {
            CardSet next = null;
            int maxCovered = 0;
            for (CardSet hand : hands) {
                if (res.contains(hand)) {
                    continue;
                }
                int covered = knownHand.diff(hand).intersection(notDropped).size() +
                    hand.intersection(notKept).size();
                if (covered > maxCovered || next == null && res.size() < MIN_DROP_GUESSES) {
                    next = hand;
                    maxCovered = covered;
                }
            }
            if (next == null) {
                break;      // all covered, at least MIN_DROP_GUESSES, or no more hands
            }
            res.add(next);
            notDropped.remove(knownHand.diff(next));
            notKept.remove(next);
        }
        return res;
    }

    // legal cards of the defender to move, one of the cards with no other card between them
    CardList candidates(Trick trick) {
        GameManager gameManager = gameManager();
        CardSet hand = gameManager.getPlayers()[trick.getTurn()].myHand;
        CardSet legal = hand.list(trick.getStartingSuit());
        if (legal.isEmpty() && trick.getStartingSuit() != null) {
            legal = hand.list(trick.getTrumpSuit());    // void: a trump has to be played
        }
        if (legal.isEmpty()) {
            legal = hand;
        }
        CardSet others = CardSet.union(gameManager.declarerHand, leftHand, rightHand,
            trick.cards2CardSet());
        others.remove(hand);
        CardList res = new CardList();
        Card prev = null;
        for (Card card : legal.toCardList()) {
            if (prev != null && prev.getSuit().equals(card.getSuit()) && !anyBetween(others, prev, card)) {
                continue;
            }
            res.add(card);
            prev = card;
        }
        return res;
    }

    // the candidates equivalent to card under the guess, the declarer's hand
    CardList sameRun(Trick trick, CardList candidates, Card card, CardSet guess) {
        CardSet hand = gameManager().getPlayers()[trick.getTurn()].myHand;
        CardSet others = CardSet.union(guess, leftHand, rightHand, trick.cards2CardSet());
        others.remove(hand);
        CardList res = new CardList();
        for (Card candidate : candidates) {
            if (candidate.getSuit().equals(card.getSuit()) && !anyBetween(others, candidate, card)) {
                res.add(candidate);
            }
        }
        return res;
    }

    // a card of others between two cards of the same suit
    static boolean anyBetween(CardSet others, Card card0, Card card1) {
        if (card0.compareInTrick(card1) > 0) {
            Card card = card0;
            card0 = card1;
            card1 = card;
        }
        for (Card other : others.list(card0.getSuit()).toCardList()) {
            if (other.compareInTrick(card0) > 0 && other.compareInTrick(card1) < 0) {
                return true;
            }
        }
        return false;
    }

}