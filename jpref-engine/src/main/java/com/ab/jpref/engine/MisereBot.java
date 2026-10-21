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

    @Override
    int dropRisk(CardSet dealtHand) {
        return getEval4Misere(dealtHand);
    }

    // pa + pb, see getDrop()
    int getRiskSum4Misere(CardSet cardSet) {
        int res = 0;
        int bitset = 0;
        while ((bitset = CardSet.bm4NextSuit(cardSet.getBitmap(), bitset)) != 0) {
            CardSet cards = new CardSet(bitset);
            if (cards.isClean4Misere()) {
                continue;
            }
            res += misereTable[ranks(cards)];
        }
        return res;
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
        boolean hopeless = playerBids.get(0).value > HOPELESS_RISK;
        if (hopeless) {
            // the risk to be caught in any suit is close to MAX_EVAL for most drops and tells them
            // apart no more: the sum of the suits' risks, how many suits can catch, ♠78 ♣78KA ♦79J ♥7XJ:
            // ♥XJ 582, ♣KA 667, ♠7 ♥X 50 + 582 + 649 - not 582, 667, 860
            for (PlayerBid probe : playerBids) {
                CardSet rest = new CardSet(this.myHand);
                rest.remove(probe.drops);
                probe.value = getRiskSum4Misere(rest);
            }
            Collections.sort(playerBids, (b1, b2) -> b1.value - b2.value);
        }
        if (declarerDrop == DeclarerDrop.Random && gameManager().getDeclarer() instanceof Bot) {
            // a bot declarer's own drop; the defenders guessing a human's drop take the least risk
            return chooseDrop(playerBids, hopeless);
        }
        return playerBids.get(0);
    }

    // sorted by risk: a clean one if any, otherwise one of the least risks up to the first jump;
    // hopeless, sorted by the sums of the suits' risks, see getDrop(): one within DROP_JUMP of the least,
    // the sums grow slowly and the jumps between neighbours would take them all;
    // a lower risk more likely, the weight is 1 / risk:
    // 249 249 280 | 1000 ... -> one of the first three, 35%, 35%, 31%
    static PlayerBid chooseDrop(List<PlayerBid> playerBids, boolean hopeless) {
        PlayerBid best = playerBids.get(0);
        if (best.value == 0) {
            return best;
        }
        int n = 1;
        while (n < playerBids.size() && playerBids.get(n).value <=
                (hopeless ? best.value : playerBids.get(n - 1).value) * DROP_JUMP) {
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
    // Each of various drops, see dropGuesses(), is searched with open cards, so under each the defenders know
    // where to catch and their counts are only an upper bound: a card catching under fewer
    // drops is out, but equal counts say nothing. Then the card that catches under the list's
    // guess, then, while the drop is unknown, a suit that is a hole under every drop is kept for
    // later and the guess is tested elsewhere, a discard unblocks a possible hole, then a catch
    // in this very trick, then the most tricks, then the list's card:
    // ♠9 ♣7XJQK ♦79XQ ♥KX, ♠ ♥ or ♣ catch it - not ♣8XA, then it is a guess between ♠9 and ♥X;
    // ♠ first, if dropped then ♥X goes, but ♣ still catches
    @Override
    Card robustCard(Trick trick, Card listCard) {
        GameManager gameManager = gameManager();
        if (trick.getTurn() == gameManager.declarerNumber) {
            return listCard;
        }
        TrickList trickList = TrickList.getInstance();
        List<CardSet> guesses = dropGuesses(trick);
        if (guesses.size() < 2) {
            return listCard;
        }
        CardList candidates = candidates(trick);
        if (trick.getNumber() == 0) {
            // full hands: a search per guess takes too long for every card of the first trick,
            // only for the cards of the list's card run under its guess, the run can hide a card
            // the declarer kept: ♣89XJA against ♣7 with ♣QK dropped, ♣A lets ♣K go if ♣Q ♦X were
            candidates = sameRun(trick, candidates, listCard, guesses.get(0));
        }
        if (candidates.size() < 2) {
            return listCard;
        }
        int n = candidates.size();
        // the guesses caught, every guess counts the same: a human declarer's drop can be
        // any, the risks of getDrop() do not tell it
        int[] caught = new int[n];
        // then the declarer's tricks under all the guesses: ♠7X ♣7 ♥78J ♣KA dropped, ♠9 or ♣8 catch,
        // ♣8 first takes another trick when the declarer kept ♣K or ♣A
        int[] totalTricks = new int[n];
        // the guesses where the declarer has to take this very trick, the open cards do not
        // know the drop, a catch now does not need it: ♣8 ducked with ♣7, ♣9 is in case of ♣K;
        // not a lead in a hole under every guess, it is sure later, a catch now spends its card
        int[] now = new int[n];
        int holes = holeSuits(guesses, true);
        int someHoles = holeSuits(guesses, false);
        int[] listGuess = null;     // declarer's tricks under the list's guess, it is the first one
        int tricks = getTricks();
        int complete = 0;
        long start = System.currentTimeMillis();
        int remaining = guesses.size();     // the guesses not searched yet
        // in the order of dropGuesses(), within the move's deadline, set in TrickList.getCard()
        for (int g = 0; g < guesses.size(); ++g) {
            int[] res = null;
            if (g == 0) {
                res = trickList.builtResults(trick, guesses.get(g), candidates);    // searched by the list
            }
            if (res == null) {
                res = trickList.evalCards(trick, guesses.get(g), candidates);
            }
            if (res == null) {
                break;      // deadline, the last guesses are left out
            }
            if (complete++ == 0) {
                listGuess = res;
            }
            for (int i = 0; i < n; ++i) {
                // one more trick, the ones taken already count for every card
                if (res[i] > 0) {
                    ++caught[i];
                }
                totalTricks[i] += tricks + res[i];
                if ((!trick.isEmpty() || inSuits(candidates.get(i), holes) == 0) &&
                        takesNow(trick, guesses.get(g), candidates.get(i))) {
                    ++now[i];
                }
            }
            --remaining;
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
            // the list's guess is the drop against a bot declarer, otherwise still the best one guess
            int diff = (listGuess[i] > 0 ? 1 : 0) - (listGuess[best] > 0 ? 1 : 0);
            if (diff == 0) {
                diff = holeScore(trick, candidates.get(i), holes, someHoles) -
                    holeScore(trick, candidates.get(best), holes, someHoles);
            }
            if (diff == 0) {
                diff = now[i] - now[best];
            }
            if (diff == 0) {
                diff = totalTricks[i] - totalTricks[best];
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
            sb.append(String.format(" %s %d,%d/%d", candidates.get(i), caught[i], now[i], listGuess[i]));
        }
        for (Card.Suit suit : Card.Suit.values()) {
            if (inSuits(suit, holes)) {
                sb.append(", hole ").append(suit.getCode());
            } else if (inSuits(suit, someHoles)) {
                sb.append(", hole? ").append(suit.getCode());
            }
        }
        Logger.printf("trick %d, drops guessed %d, caught guesses,now/tricks for the list's guess:%s -> %s, %,d msec\n",
            trick.getNumber(), complete, sb, candidates.get(best), System.currentTimeMillis() - start);
        return candidates.get(best);
    }

    // the declarer takes this trick with the card played, whatever he plays next
    private boolean takesNow(Trick trick, CardSet guess, Card card) {
        CardList played = trick.cards2List();
        played.add(card);
        return takesNow(trick.getStartedBy(), played, guess);
    }

    private boolean takesNow(int startedBy, CardList played, CardSet guess) {
        GameManager gameManager = gameManager();
        if (played.size() == NOP) {
            int top = 0;
            for (int i = 1; i < NOP; ++i) {
                Card card = played.get(i);
                if (card.getSuit().equals(played.get(top).getSuit()) && card.compareInTrick(played.get(top)) > 0) {
                    top = i;
                }
            }
            return (startedBy + top) % NOP == gameManager.declarerNumber;
        }
        int p = (startedBy + played.size()) % NOP;
        boolean declarer = p == gameManager.declarerNumber;
        CardSet hand = declarer ? guess : gameManager.getPlayers()[p].myHand;
        CardSet legal = hand.list(played.get(0).getSuit());
        if (legal.isEmpty()) {
            legal = hand;
        }
        for (Card card : legal.toCardList()) {
            played.add(card);
            boolean takes = takesNow(startedBy, played, guess);
            played.remove(played.size() - 1);
            if (takes != declarer) {
                return takes;   // the declarer escapes or a defender makes him take it
            }
        }
        return declarer;
    }

    // suits where the declarer has a hole under every guess, or under some guess, as a bitmap by suit value
    private int holeSuits(List<CardSet> guesses, boolean every) {
        int res = 0;
        for (Card.Suit suit : Card.Suit.values()) {
            boolean hole = every;
            for (CardSet hand : guesses) {
                CardSet cards = hand.list(suit);
                boolean h = !cards.isEmpty() && cards.maxUnwantedTricks(
                        leftHand.list(suit), rightHand.list(suit)).maxTheyStart > 0;
                if (h != every) {
                    hole = h;
                    break;
                }
            }
            if (hole) {
                res |= 1 << suit.getValue();
            }
        }
        return res;
    }

    // a lead or a card to follow: a hole under every guess is kept for later, -1;
    // a discard where the declarer may have a hole: a card over all of the declarer's
    // goes first, +1, when it blocks the other defender's leads under them, a lower one stays, -1:
    // ♠XQ ♣89XJ ♦KA on ♥, ♦K against ♦7XJ with ♦89Q, not ♠Q against ♠9J with ♠K, not ♣8 against ♣7Q
    private int holeScore(Trick trick, Card card, int holes, int someHoles) {
        Card.Suit suit = card.getSuit();
        if (trick.isEmpty() || suit.equals(trick.getStartingSuit())) {
            return -inSuits(card, holes);
        }
        if (!inSuits(suit, someHoles)) {
            return 0;
        }
        GameManager gameManager = gameManager();
        // not empty, the guesses are out of it
        Card declarerTop = gameManager.declarerHand.list(suit).last();
        if (declarerTop.compareInTrick(card) > 0) {
            return -1;
        }
        int partner = NOP - trick.getTurn() - gameManager.declarerNumber;  // 0 + 1 + 2
        CardSet partnerCards = gameManager.getPlayers()[partner].myHand.list(suit);
        if (!partnerCards.isEmpty() && partnerCards.first().compareInTrick(declarerTop) < 0) {
            return 1;
        }
        return 0;
    }

    private static boolean inSuits(Card.Suit suit, int suits) {
        return (suits & (1 << suit.getValue())) != 0;
    }

    private static int inSuits(Card card, int suits) {
        return inSuits(card.getSuit(), suits) ? 1 : 0;
    }
 }
