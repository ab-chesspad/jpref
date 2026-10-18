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
import com.ab.util.Bidder.PlayerBid;
import com.ab.util.Logger;

import java.util.*;

public class MisereBot extends Bot {
//    public static DeclarerDrop declarerDrop = DeclarerDrop.First;
    public static DeclarerDrop declarerDrop = DeclarerDrop.Random;
    public static final boolean DEBUG_LOG = false;

    static final int MAX_EVAL = 1000;   // in ‰
    // https://summoning.ru/games/miser.shtml, recalculated by MisereCatch:
    // probabilities to get a trick in misère, by the rank bitmap of a suit
    static final int[] misereTable = MisereCatch.table();

    enum DeclarerDrop {
        First,      // the least risk
        Random      // one of the least risks, the defenders cannot be sure of it, see chooseDrop()
    }
    // risks of drops for DeclarerDrop.Random end at the first one more than 30% over the previous
    static final double DROP_JUMP = 1.3;
    // when even the least risk of a drop is higher, likely caught anyway: one of all drops
    static final int HOPELESS_RISK = 500;
    private static final Random random = new Random();

    private static MisereBot instance;

    public static MisereBot getInstance() {
        if (instance == null) {
            instance = new MisereBot();
        }
        return instance;
    }

    public static MisereBot getInstance(Player player) {
        MisereBot instance = getInstance();
        instance.init(player);
        return instance;
    }

    public static MisereBot getInstance(CardSet... hands) {
        MisereBot instance = getInstance();
        instance.init(hands);
        return instance;
    }

    static final int MAX_DROP_GUESSES = 4;  // see robustCard()

    private MisereBot() {
        this.bid = Config.Bid.BID_MISERE;
    }

    // not the singleton, for tests that need a second solver
    MisereBot(CardSet... hands) {
        this();
        init(hands);
    }

    @Override
    public void clear() {
        super.clear();
        this.bid = Config.Bid.BID_MISERE;
    }

    // pa|b = 1 - (1-pa)(1-pb)
    int getEval4Misere(CardSet cardSet) {
        double pab = 1;    // 1 - pa|b
        int bitset = 0;
        while ((bitset = CardSet.bm4NextSuit(cardSet.getBitmap(), bitset)) != 0) {
            CardSet cards = new CardSet(bitset);
            if (cards.isClean4Misere()) {
                continue;
            }
            pab *= 1 - (double)misereTable[ranks(cards)] / MAX_EVAL;
        }
        return (int)((1 - pab) * MAX_EVAL);
    }

    // possible?
    // https://www.gamblergames.com/forum/index.php?showtopic=502338
    // https://www.gamblergames.com/forum/index.php?showtopic=501784&st=690
    // eval possible talon out of 22 remaining cards
    boolean evalMisere(int elderHand) {
        int eval = getEval4Misere(myHand);
        if (eval == 0) {
            return true;
        }
        CardSet talonCandidates = myHand.complement();
        CardSet myHand = new CardSet(this.myHand);
        List<PlayerBid> playerBids = new ArrayList<>();
        int bit0 = 0;
        while ((bit0 = CardSet.next(talonCandidates.getBitmap(), bit0)) != 0) {
            Card card0 = Card.get(bit0);
            myHand.add(card0);
            PlayerBid playerBid = new PlayerBid(Config.Bid.BID_MISERE);
            playerBid.value = Integer.MAX_VALUE;    // will be used as probability

            int bit1 = 0;
            while ((bit1 = CardSet.next(myHand.getBitmap(), bit1)) != 0) {
                Card card1 = Card.get(bit1);
                if (card0.equals(card1)) {
                    continue;
                }
                myHand.remove(card1);
                eval = getEval4Misere(myHand);
                myHand.add(card1);
                if (playerBid.value > eval) {
                    playerBid.value = eval;
                    playerBid.drops.clear();
                    playerBid.drops.add(card0);
                    if (playerBid.value == 0) {
                        break;
                    }
                }
            }
            playerBids.add(playerBid);
            myHand.remove(card0);
        }
        Collections.sort(playerBids, (b1, b2) -> b1.value - b2.value);
        // rule of 7 cards
        // https://gambiter.ru/pref/mizer-preferans.html
        int index = 6;
        PlayerBid playerBid = playerBids.get(index);
        return playerBid.value < 500;
    }

    @Override
    PlayerBid getDrop(int elderHand, int nDrops, Trick trick) {
        PlayerBid playerBid = new PlayerBid(Config.Bid.BID_MISERE);
        if (debugDrop != null) {
            playerBid.drops = new CardSet(debugDrop);
            return playerBid;
        }
        if (Bot.playerBid != null) {
            if (nDrops == 2) {
                return Bot.playerBid;
            }
            if (this.myHand.contains(Bot.playerBid.drops) &&
                this.myHand.equals(gameManager().declarerHand)) {
                return Bot.playerBid;
            }
        }

        if (nDrops == 0) {
            return playerBid;   // nothing to drop
        }
        Logger.printf(DEBUG_LOG, "getDrop(%d), %s\n", elderHand, this.myHand.toColorString());

        // every pair once, value is the probability to be caught
        List<PlayerBid> playerBids = new ArrayList<>();
        CardSet myHand = new CardSet(this.myHand);
        int bitmap = myHand.getBitmap();
        int bit0 = 0;
        while ((bit0 = CardSet.next(bitmap, bit0)) != 0) {
            Card card0 = Card.get(bit0);
            myHand.remove(card0);
            int bit1 = bit0;
            while ((bit1 = CardSet.next(bitmap, bit1)) != 0) {
                Card card1 = Card.get(bit1);
                myHand.remove(card1);
                PlayerBid probe = new PlayerBid(Config.Bid.BID_MISERE);
                probe.value = getEval4Misere(myHand);
                probe.drops.add(card0);
                probe.drops.add(card1);
                Logger.printf(DEBUG_LOG, "%s, %s -> %s: %d\n",
                    card0, card1, myHand.toColorString(), probe.value);
                playerBids.add(probe);
                myHand.add(card1);
            }
            myHand.add(card0);
        }
        // the sort is stable: on a tie the first pair in card order, as First always took
        Collections.sort(playerBids, (b1, b2) -> b1.value - b2.value);
        if (declarerDrop == DeclarerDrop.Random && gameManager().getDeclarer() instanceof Bot) {
            // a bot declarer's own drop; the defenders guessing a human's drop take the least risk
            return chooseDrop(playerBids);
        }
        return playerBids.get(0);
    }

    // sorted by risk: a clean one if any, otherwise one of the least risks up to the first jump,
    // or one of all when the least is over HOPELESS_RISK; a lower risk more likely, the weight is 1 / risk:
    // 249 249 280 | 1000 ... -> one of the first three, 35%, 35%, 31%
    static PlayerBid chooseDrop(List<PlayerBid> playerBids) {
        PlayerBid best = playerBids.get(0);
        if (best.value == 0) {
            return best;
        }
        int n = 1;
        if (best.value > HOPELESS_RISK) {
            n = playerBids.size();
        }
        while (n < playerBids.size() && playerBids.get(n).value <= playerBids.get(n - 1).value * DROP_JUMP) {
            ++n;
        }
        double total = 0;
        for (int i = 0; i < n; ++i) {
            total += 1.0 / playerBids.get(i).value;
        }
        double r = random.nextDouble() * total;
        for (int i = 0; i < n - 1; ++i) {
            r -= 1.0 / playerBids.get(i).value;
            if (r < 0) {
                return playerBids.get(i);
            }
        }
        return playerBids.get(n - 1);
    }

    @Override
    public Card play(Trick trick) {
        if (trick.getNumber() == 0 && trick.isEmpty() &&
            trick.getTurn() == gameManager().declarerNumber) {
            return declarerPlay(trick);
        } else {
            // create and use trick list
            Card res = TrickList.getInstance().getCard(this, trick);
            if (res != null) {
                return res;
            }
            return this.anyCard(trick, true);
        }
    }

    HandResults misereTricks(int elderHand) {
        HandResults handResults = new HandResults();
        handResults.expect = 0;
        int bitset = 0;
        while ((bitset = CardSet.bm4NextSuit(myHand.getBitmap(), bitset)) != 0) {
            // todo: use bitmaps only
            CardSet cardSet = new CardSet(bitset);
            Card.Suit suit = cardSet.first().getSuit();
            CardSet.ListData listData = cardSet.maxUnwantedTricks(leftHand.list(suit), rightHand.list(suit), 2);
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

    private Card declarerPlay(Trick trick) {
        HandResults handResults = misereTricks(trick.getTurn());
        CardSet.ListData bestListData = null;
        for (CardSet.ListData listData : handResults.allListData) {
            if (listData == null) {
                continue;
            }
            CardSet cardList = listData.thisSuit;
            if (cardList.isEmpty()) {
                continue;
            }
            if (bestListData == null || bestListData.maxMeStart > listData.maxMeStart) {
                bestListData = listData;
            }
        }
        if (bestListData == null) {
            throw new RuntimeException("prog error");
        }
        CardSet cleanSuit = bestListData.thisSuit;
        Card.Suit s = bestListData.suit;
        return cleanSuit.getOptimalStart(leftHand.list(s), rightHand.list(s));
    }

    private static int rankIndex(Card card) {
        return card.getRank().getValue() - Card.Rank.SEVEN.getValue();
    }

    // the rank bitmap of one suit, 7 is bit 0
    private static int ranks(CardSet suitCards) {
        int res = 0;
        int bit = 0;
        while ((bit = CardSet.next(suitCards.getBitmap(), bit)) != 0) {
            res |= 1 << rankIndex(Card.get(bit));
        }
        return res;
    }

    @Override
    long bm4Iteration(TrickList.TrickNode trickNode) {
        final int[] bitmaps = new int[NOP];
        int num = trickNode.getTurn();
        bitmaps[0] = trickNode.hands[num].list(trickNode.startingSuit).getBitmap();
        if (bitmaps[0] == 0) {
            bitmaps[0] = trickNode.hands[num].list().getBitmap();
        }
        bitmaps[1] = trickNode.hands[(num + 1) % NOP].getBitmap();
        bitmaps[2] = trickNode.hands[(num + 2) % NOP].getBitmap();
        for (int i = 0; i < trickNode.size(); ++i) {
            Card card = trickNode.getCard(i);
            int bit = 1 << CardSet.offset(card);
            int n = (i + 3 - trickNode.size()) % NOP;
            bitmaps[n] |= bit;
        }
        // declarer and defenders alike: only cards with no outstanding card between them are equivalent.
        // CardSet.bm4build() (friend/foe) is for play for tricks, in misère it drops the defenders'
        // discards that catch the declarer, e.g. the 1st test in fixedplay
        int others = bitmaps[1] | bitmaps[2];
        return ((long)CardSet.bm4buildBackward(bitmaps[0], others) & 0x0ffffffffL | BACKWARD_FLAG);
    }

    @Override
    protected int compare(long bestSoFarTrickData, long probeTrickData, int turn) {
        int num = (BaseTrick.getStartedBy(probeTrickData) + turn) % NOP;   // who played last
        int bestSoFarPastTricks = BaseTrick.getPastTricks(bestSoFarTrickData);
        int bestSoFarFutureTricks = BaseTrick.getFutureTricks(bestSoFarTrickData);
        int bestSoFarTricks = bestSoFarPastTricks + bestSoFarFutureTricks;

        int probePastTricks = BaseTrick.getPastTricks(probeTrickData);
        int probeFutureTricks = BaseTrick.getFutureTricks(probeTrickData);
        int probeTricks = probePastTricks + probeFutureTricks;

        // misère: declarer wants the least tricks, opposite to ForTricksBot.compare()
        int diff = 10 * (probeTricks - bestSoFarTricks);
        if (diff != 0) {
            if (num == 0) {
                return diff;
            }
            return -diff;
        }
        diff = probePastTricks - bestSoFarTricks;
        if (diff != 0) {
            if (num == 0) {
                return diff;
            }
            return -diff;
        }
        int _bestSoFarTop = BaseTrick.getTop(bestSoFarTrickData);
        int _probeTop = BaseTrick.getTop(probeTrickData);
        if (_bestSoFarTop == 0 && _probeTop != 0 ||
            _bestSoFarTop != 0 && _probeTop == 0) {
            return diff;
        } else {
            // todo
        }
        return diff;
    }

    // Misère: the defenders do not know the drop, the list is built for one guess of it;
    // against a bot declarer it is the bot's own drop, the defenders would guess it the same way.
    // Each likely drop is searched with open cards, so under each of them the defenders know
    // where to catch and their counts are only an upper bound: a card catching under fewer
    // drops is out, but equal counts say nothing. Then, while the drop is unknown, a suit that
    // is a hole under every drop is kept for later and the guess is tested elsewhere, then the
    // card that catches under the list's guess, with the most tricks, then the list's card:
    // ♠9 ♣7XJQK ♦79XQ ♥KX, ♠ ♥ or ♣ catch it - not ♣8XA, then it is a guess between ♠9 and ♥X;
    // ♠ first, if dropped then ♥X goes, but ♣ still catches
    @Override
    Card robustCard(Trick trick, Card listCard) {
        GameManager gameManager = gameManager();
        if (trick.getTurn() == gameManager.declarerNumber) {
            return listCard;
        }
        if (trick.getNumber() == 0) {
            return listCard;    // full hands: a search per guess takes too long for the first trick
        }
        TrickList trickList = TrickList.getInstance();
        List<CardSet> guesses = dropGuesses(trick);
        if (guesses.size() < 2) {
            return listCard;
        }
        CardList candidates = candidates(trick);
        if (candidates.size() < 2) {
            return listCard;
        }
        int n = candidates.size();
        // a guess counts by the declarer's chance to stay clean with it: the declarer drops
        // the least risk, ♠79K ♣79Q ♦789 ♥7QK: ♥QK 90%, ♠K ♥K 95%, ♣Q ♥K 98% are 10:5:2
        int[] caught = new int[n];
        // then the declarer's tricks by the same weights: ♠7X ♣7 ♥78J ♣KA dropped, ♠9 or ♣8 catch,
        // ♣8 first takes another trick when the declarer kept ♣K or ♣A
        long[] weightedTricks = new long[n];
        int[] listGuess = null;     // declarer's tricks under the list's guess, it is the first one
        int tricks = getTricks();
        int complete = 0;
        long start = System.currentTimeMillis();
        int[] weights = new int[guesses.size()];
        for (int g = 0; g < weights.length; ++g) {
            CardSet dealtHand = new CardSet(guesses.get(g));
            dealtHand.add(gameManager.declarerPlayed);
            weights[g] = MAX_EVAL - getEval4Misere(dealtHand) + 1;    // a sure catch still counts
        }
        // the list's guess is what getDrop() drops, no other drop is more likely
        for (int g = 1; g < weights.length; ++g) {
            weights[0] = Math.max(weights[0], weights[g]);
        }
        // the heaviest first, the list's guess is the heaviest
        List<Integer> order = new ArrayList<>();
        int remaining = 0;     // weight of the guesses not searched yet
        for (int g = 0; g < weights.length; ++g) {
            order.add(g);
            remaining += weights[g];
        }
        Collections.sort(order.subList(1, order.size()), (g1, g2) -> weights[g2] - weights[g1]);  // List.sort needs API 24
        // within the move's deadline, set in TrickList.getCard()
        for (int g : order) {
            int[] res = null;
            if (g == 0) {
                res = trickList.builtResults(trick, guesses.get(g), candidates);    // searched by the list
            }
            if (res == null) {
                res = trickList.evalCards(trick, guesses.get(g), candidates);
            }
            if (res == null) {
                break;      // deadline, the less likely guesses are left out
            }
            if (complete++ == 0) {
                listGuess = res;
            }
            for (int i = 0; i < n; ++i) {
                if (tricks + res[i] > 0) {
                    caught[i] += weights[g];
                }
                weightedTricks[i] += (long) weights[g] * (tricks + res[i]);
            }
            remaining -= weights[g];
            if (decided(caught, remaining)) {
                break;      // the rest cannot change the card
            }
        }
        if (complete == 0) {
            return listCard;
        }
        int maxCaught = 0;
        for (int i = 0; i < n; ++i) {
            maxCaught = Math.max(maxCaught, caught[i]);
        }
        int holes = holeSuits(guesses);
        int listIndex = candidates.indexOf(listCard);
        int best = -1;
        for (int i = 0; i < n; ++i) {
            if (caught[i] < maxCaught) {
                continue;
            }
            if (best < 0) {
                best = i;
                continue;
            }
            int diff = inSuits(candidates.get(best), holes) - inSuits(candidates.get(i), holes);
            if (diff == 0) {
                diff = Long.compare(weightedTricks[i], weightedTricks[best]);
            }
            if (diff == 0) {
                diff = (tricks + listGuess[i] > 0 ? 1 : 0) - (tricks + listGuess[best] > 0 ? 1 : 0);
            }
            if (diff == 0) {
                diff = listGuess[i] - listGuess[best];
            }
            if (diff == 0 && i == listIndex) {
                diff = 1;
            }
            if (diff > 0) {
                best = i;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; ++i) {
            sb.append(String.format(" %s %d/%d", candidates.get(i), caught[i], listGuess[i]));
        }
        for (Card.Suit suit : Card.Suit.values()) {
            if (inSuits(suit, holes)) {
                sb.append(", hole ").append(suit.getCode());
            }
        }
        Logger.printf("trick %d, drops guessed %d, caught ‰ weighted/tricks for the list's guess:%s -> %s, %,d msec\n",
            trick.getNumber(), complete, sb, candidates.get(best), System.currentTimeMillis() - start);
        return candidates.get(best);
    }

    // one card leads the caught weights by more than the guesses left can add to any other;
    // on a tie the tie-breaks need all the guesses
    private static boolean decided(int[] caught, int remaining) {
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

    // suits where the declarer has a hole under every guess, as a bitmap by suit value
    private int holeSuits(List<CardSet> guesses) {
        int res = 0;
        for (Card.Suit suit : Card.Suit.values()) {
            boolean hole = true;
            for (CardSet hand : guesses) {
                CardSet cards = hand.list(suit);
                if (cards.isEmpty() || cards.maxUnwantedTricks(
                        leftHand.list(suit), rightHand.list(suit)).maxTheyStart == 0) {
                    hole = false;
                    break;
                }
            }
            if (hole) {
                res |= 1 << suit.getValue();
            }
        }
        return res;
    }

    private static boolean inSuits(Card.Suit suit, int suits) {
        return (suits & (1 << suit.getValue())) != 0;
    }

    private static int inSuits(Card card, int suits) {
        return inSuits(card.getSuit(), suits) ? 1 : 0;
    }

    // the declarer's hands for the likely drops, the list's guess first, then the best ones for misère
    private List<CardSet> dropGuesses(Trick trick) {
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
            for (int j = diff == 1 ? i : i + 1; j < cards.size(); ++j) {
                CardSet hand = new CardSet(knownHand);
                hand.remove(cards.get(i));
                hand.remove(cards.get(j));
                // evaluate the hand as it was after the drop
                CardSet dealtHand = new CardSet(hand);
                dealtHand.add(gameManager.declarerPlayed);
                int eval = getEval4Misere(dealtHand);
                int k = evals.size();
                while (k > 0 && evals.get(k - 1) > eval) {
                    --k;
                }
                evals.add(k, eval);
                hands.add(k, hand);
            }
        }
        for (CardSet hand : hands) {
            if (res.size() >= MAX_DROP_GUESSES) {
                break;
            }
            if (!res.contains(hand)) {
                res.add(hand);
            }
        }
        return res;
    }

    // legal cards of the defender to move, one of the cards with no other card between them
    private CardList candidates(Trick trick) {
        GameManager gameManager = gameManager();
        CardSet hand = gameManager.getPlayers()[trick.getTurn()].myHand;
        CardSet legal = hand.list(trick.getStartingSuit());
        if (legal.isEmpty()) {
            legal = hand;
        }
        CardSet others = CardSet.union(gameManager.declarerHand, leftHand, rightHand,
            trick.cards2CardSet());
        others.remove(hand);
        CardList res = new CardList();
        Card prev = null;
        for (Card card : legal.toCardList()) {
            if (prev != null && prev.getSuit().equals(card.getSuit())) {
                CardSet between = others.list(card.getSuit());
                boolean equivalent = true;
                for (Card other : between.toCardList()) {
                    if (other.compareInTrick(prev) > 0 && other.compareInTrick(card) < 0) {
                        equivalent = false;
                        break;
                    }
                }
                if (equivalent) {
                    continue;
                }
            }
            res.add(card);
            prev = card;
        }
        return res;
    }
}
