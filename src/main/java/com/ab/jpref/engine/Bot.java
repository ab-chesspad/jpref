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
 * Created: 12/22/2024.
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config.Bid;
import com.ab.util.BidData;
import com.ab.util.BidData.PlayerBid;

import java.util.*;

public class Bot extends Player {
    public static CardList debugDrop = null;

    // when declarer is Bot, it uses the same logic as mimic BotFse
    // so trickList and playerBid are the same and can be shared
    public static TrickList trickList = null;
    static PlayerBid playerBid;

    public static Bot targetBot;  // either forTricksBot or misereBot
    static Trick trick;

    public Bot(int number) {
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

    public Bot(CardSet cards) {
        super(cards);
        number = 0;
    }

    protected Bot(CardSet... hands) {
        this.myHand = hands[0].clone();
        if (hands.length > 1) {
            this.leftHand = hands[1].clone();
            this.rightHand = hands[2].clone();
        }
    }

    @Override
    public void clear() {
        super.clear();
        playerBid = null;
        trickList = null;
        targetBot = null;
        trick = null;
        debugDrop = null;
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
        ForTricksBot forTricksBot = new ForTricksBot(this);
        MisereBot misereBot = new MisereBot(this);
        PlayerBid playerBid = forTricksBot.getMaxPlayerBid(minBid, elderHand);
        Bid bid = playerBid.toBid();
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

    // stab to be overridden in MisereBot and ForTricksBot
    PlayerBid getDrop(int elderHand) {
        throw new RuntimeException("stub!");
    }

    // minimax criteria, stab to be overridden in MisereBot and ForTricksBot
    protected int compare(BaseTrick bestSoFar, BaseTrick probe, int index) {
        throw new RuntimeException("stub!");
    }

    CardSet.CardIterator getIterator(TrickList.TrickNode trickNode) {
        throw new RuntimeException("stub!");
    }

    // 12 cards
    @Override
    public void declareRound(Bid minBid, int elderHand) {
        elderHand = (this.number - elderHand + NOP) % NOP;  // relative to self
        if (Bid.BID_MISERE.equals(minBid)) {
            targetBot = new MisereBot(this);
        } else {
            targetBot = new ForTricksBot(this);
        }
        BidData.PlayerBid playerBid = targetBot.getDrop(elderHand);
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
                bid = Bid.BID_WHIST;
            } else {
                bid = Bid.BID_PASS;
            }
        } else {
            Bid otherBid = gameManager().getBid(player1);
            if (otherBid.equals(Bid.BID_WHIST)) {
                bid = Bid.BID_PASS;
            } else {
                bid = Bid.BID_WHIST;
            }
        }
        this.bid = bid;
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
            // when declarer is human; todo: verify declaration!
            Player declarer = gameManager().getDeclarerForDefender();
            Card current = null;
            if (trick.getStartedBy() == gameManager().declarerNumber && trick.size() == 1) {
                current = trick.topCard;
            }
            declarer.myHand.add(current);
            declarer.declareRound(gameManager().minBid, trick.getStartedBy());
            declarer.myHand.remove(current);
            targetBot.myHand.remove(current);
        }
        return targetBot.play(trick);
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

        Iterator<CardSet> suitIterator = myHand.suitIterator();
        while (suitIterator.hasNext()) {
            CardSet cardSet = suitIterator.next();
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
                        if (noProblemSuit.suit == null ||
                                noProblemSuit.cardsLeft > listData.cardsLeft) {
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
                            if (cardSet1.first().compareInTrick(theirMin) < 0) {
                                continue; // no point to start with this cardSet, I'll have a chance to do it later
                            }

                            CardSet.ListData listData1 =
                                cardSet1.maxUnwantedTricks(dummy, checkHand.list(checkSuit),2);
                            if (listData1.maxTheyStart > 0) {
                                //?? I hope I will shove cardSet.first() with the next trick
                                theirMin = CardSet.min(leftHand.list(checkSuit), rightHand.list(checkSuit));
                                if (cardSet1.size() > 1 && cardSet1.first().compareInTrick(_theirMin) < 0) {
                                    return cardSet1.get(1);
                                }
                                if (cardSet1.size() > 1 && cardSet1.prevIndex(cardSet1.get(1)) == 0) {
                                    return cardSet1.get(1);
                                }
                                return cardSet1.last();
                            }
                        }
                        // todo: check if she can shove a trick to the 3rd player, and that one can get me
                        return cardSet.first();
                    }
                } else if (noProblemSuit.maxMeStart == 0 ||
                        noProblemSuit.maxMeStart > listData.maxMeStart && noProblemSuitSize < cardSet.size()) {
                    if (!(listData.maxMeStart > 0 && listData.cardsLeft >= 0
                        && noProblemSuit.suit != null) || noProblemSuit.maxMeStart == 0) {
                        noProblemSuit = listData;
                    }
                } else if (listData.maxMeStart > 0 && cardSet.size() >= 4) {
                    noProblemSuit = listData;
                } else { // if (noProblemSuit.maxMeStart == listData.maxMeStart) {
                    // compare number of smaller cards
                    Card prev = rightHand.list(_suit).prev(myHand.list(_suit).first());
                    Card curr = rightHand.list(suit).prev(myHand.list(suit).first());
                    if (curr != null && curr.compareInTrick(prev) > 0) {
                        noProblemSuit = listData;
                    } else if (cardSet.size() > 1 && cardSet.first().compareInTrick(theirMin) < 0) {
                        noProblemSuit = listData;
                    } if (curr == prev && cardSet.size() > myHand.list(_suit).size()) {
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
                            // there is a sigle card less than my min, so better take this trick
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
            if (cardList.size() > 1 &&
                    (rightHand.list(suit).size() > 1 || leftHand.list(suit).size() > 1)) {
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

    private Card drop4AllPass(Trick trick) {
        CardSet.ListData problemList = null;
        CardSet.ListData noProblemList = null;
        Card res;

        Iterator<CardSet> suitIterator = myHand.suitIterator();
        while (suitIterator.hasNext()) {
            CardSet cardList = suitIterator.next();
            Card.Suit suit = cardList.first().getSuit();
            Card theirMin = CardSet.min(leftHand.list(suit), rightHand.list(suit));
            if (theirMin == null) {
                continue;
            }
            CardSet.ListData listData = cardList.getUnwantedTricks(leftHand.list(suit), rightHand.list(suit));
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
                Iterator<CardSet> suitIterator1 = myHand.suitIterator(probe.first().getSuit());
                if (suitIterator1.hasNext()) {
                    CardSet cardList1 = suitIterator1.next();
                    return cardList1.last();
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
        Card res = null;
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
                    // there is a sigle card less than my max, let's risk it
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
            if (myMin.compareInTrick(trick.topCard) > 0) {
                // all my cards are greater than trick
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
                            // there is a sigle card less than my min, better take this trick
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
                if (cardSet.size() > 1) {
                    if (rightHand.list(suit).size() > 2 &&
                            rightHand.list(suit).prevIndex(myMin) == 0) {
                        // there is a sigle card less than my min
                        if (trick.getNumber() == 0) {
                            res = cardSet.first();
                        } else {
                            // better take this trick
                            res = cardSet.get(1);
                        }
                    }
                }
                if (res == null) {
                    // ♣8JKA ♥J   ♠J ♣7Q ♦79   ♣9X ♥789   -> ♣Q ♣X, should take it, play ♠J and ♣8
                    // instead she plays ♣J, then ♣7 ♣9 ♣8 and gets 4 tricks
                    // on the other hand, for ♣8JKA ♥J   ♠J ♣9Q ♦79   ♣7X ♥789 it does not work
                    res = cardSet.prev(topTrickCard);
                }
            }
        }

        return res;
    }

    static class HandResults {
        final CardSet.ListData[] allListData = new CardSet.ListData[Card.TOTAL_SUITS];
        int totalTricks = 0;    // the best
        int expect = Integer.MAX_VALUE;
    }
}