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
    public static final boolean DEBUG_LOG = false;

    static final int MAX_EVAL = 1000;   // in ‰
    // https://summoning.ru/games/miser.shtml, recalculated by MisereCatch:
    // probabilities to get a trick in misère, by the rank bitmap of a suit
    static final int[] misereTable = MisereCatch.table();
    // ‰ of the declarer's expected tricks, by the rank bitmap of a suit, see MisereCatch
    static final int[] trickTable = MisereCatch.trickTable();

    // risks of drops for DeclarerDrop.Random end at the first one more than 30% over the previous
    static final double DROP_JUMP = 1.3;
    // when even the least risk of a drop is higher, likely caught anyway: one of all drops
    static final int HOPELESS_RISK = 500;
    // to bid misère: the deals played out, see getMisereValue()
    static final int MISERE_DEALS = 60;
    // within a quarter of Config.maxMoveTime, unless Bot.standingTimeLimited is off, at least these
    static final int MIN_MISERE_DEALS = 20;
    // the most tricks a caught deal counts: the open cards show the defenders the drop, the bots
    // guess it and let the declarer go sooner, 2.5 tricks a catch vs 1.6 in their games, see getMisereValue()
    static final int MISERE_CAUGHT_TRICKS = 3;
    // ‰, more expected tricks after the best drop of own 2 cards: no misère, the simulation is not run;
    // of 4000 random hands the simulation bid 56, the most 1188
    static final int MISERE_HOPELESS_TRICKS = 2000;
    // hopeless: a trick in a suit that may be caught tends to cost more, the declarer has to lead after it
    static final double PROBABLE_TRICK_FACTOR = 1.5;
    // hopeless: ‰, a suit of 2 or more cards without the 7 has no safe exit, the defenders duck under it
    // and give the declarer the lead again and again, 'паровоз'
    static final int NO_SEVEN_PENALTY = 500;
    // the 1st level, the original estimate: the median risk over the possible talons, each with its best
    // drop, is this or higher, no misère, the simulation is not run; the original bid at 400 (Утяцкий 7.6.5,
    // see TestMisereBook), as a filter: of 40000 random hands 650 skipped the simulation on 6283 of 7480
    // not hopeless and lost 6 of its 586 bids, 400 lost 66, ♠79Q ♣7X ♦J ♥79JQ in freeplay is 536
    static final int MISERE_BID_RISK = 650;
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

    // as chooseDrop() takes them
    @Override
    boolean likelyDrop(int risk, int minRisk) {
        return risk <= minRisk * DROP_JUMP;
    }

    @Override
    int dropRisk(CardSet dealtHand, CardSet dropped) {
        return getEval4Misere(dealtHand, dropped.getBitmap());
    }

    // ‰ of the expected tricks, a sure trick as is, a probable one by PROBABLE_TRICK_FACTOR,
    // plus NO_SEVEN_PENALTY per suit of 2 or more cards without the 7, see getDrop()
    int getTricks4Misere(CardSet cardSet) {
        return getTricks4Misere(cardSet, 0);
    }

    // gone: the dropped cards, out of play, the 7 is the lowest card in play
    int getTricks4Misere(CardSet cardSet, int gone) {
        double res = 0;
        int bitset = 0;
        while ((bitset = CardSet.bm4NextSuit(cardSet.getBitmap(), bitset)) != 0) {
            CardSet cards = new CardSet(bitset);
            int r = ranks(cards);
            int g = goneRanks(cards, gone);
            int[] eval = suitEval(cards, r, g);
            if (eval == null) {
                continue;   // clean
            }
            if (eval[0] == MAX_EVAL) {
                res += eval[1];
            } else {
                res += eval[1] * PROBABLE_TRICK_FACTOR;
            }
            int out = ~r & ~g & 0xff;
            if (cards.size() >= 2 && (out & -out) != 0 && (out & -out) < (r & -r)) {
                res += NO_SEVEN_PENALTY;
            }
        }
        return (int)Math.round(res);
    }

    // pa|b = 1 - (1-pa)(1-pb)
    int getEval4Misere(CardSet cardSet) {
        return getEval4Misere(cardSet, 0);
    }

    // gone: the dropped cards, out of play: ♠79Q with ♠A dropped is safer than ♠79QA, not riskier
    int getEval4Misere(CardSet cardSet, int gone) {
        double pab = 1;    // 1 - pa|b
        int bitset = 0;
        while ((bitset = CardSet.bm4NextSuit(cardSet.getBitmap(), bitset)) != 0) {
            CardSet cards = new CardSet(bitset);
            int[] eval = suitEval(cards, ranks(cards), goneRanks(cards, gone));
            if (eval == null) {
                continue;   // clean
            }
            pab *= 1 - (double)eval[0] / MAX_EVAL;
        }
        return (int)((1 - pab) * MAX_EVAL);
    }

    // the ranks of the dropped cards in the suit of cards
    private static int goneRanks(CardSet cards, int gone) {
        if (gone == 0) {
            return 0;
        }
        return ranks(new CardSet(gone).list(cards.first().getSuit()));
    }

    // {‰ to be caught, ‰ of the expected tricks} of a suit by the rank bitmaps of its cards
    // and of its dropped ones, null when clean
    private static int[] suitEval(CardSet cards, int r, int g) {
        if (g == 0) {
            if (cards.isClean4Misere()) {
                return null;
            }
            return new int[] {misereTable[r], trickTable[r]};
        }
        int[] res = goneEval(r, g);
        if (res[0] == 0 && res[1] == 0) {
            return null;
        }
        return res;
    }

    // with dropped cards, computed on demand, at most 3^8 suits
    private static final Map<Integer, int[]> goneEvals = new HashMap<>();
    private static final MisereCatch misereCatch = new MisereCatch(false);

    private static synchronized int[] goneEval(int r, int g) {
        int key = r | g << 8;
        if (MisereCatch.STORED != null && MisereCatch.STORED[0][key] >= 0) {
            return new int[] {MisereCatch.STORED[0][key], MisereCatch.STORED[1][key]};
        }
        int[] res = goneEvals.get(key);
        if (res == null) {
            res = new int[] {
                (int) Math.round(misereCatch.prob(r, g, 1)[1] * MAX_EVAL),
                (int) Math.round(misereCatch.expectedTricks(r, g) * MAX_EVAL)};
            goneEvals.put(key, res);
        }
        return res;
    }

    // elderHand relative to self, 0: own lead
    boolean evalMisere(int elderHand) {
        if (getEval4Misere(myHand) == 0) {
            return true;    // clean, the talon can be dropped
        }
        if (getMisereBidRisk() >= MISERE_BID_RISK || hopeless()) {
            return false;
        }
        // each bot asks on each of its bids, the same hand
        long key = (long) myHand.getBitmap() << 2 | elderHand;
        Double value = misereValues.get(key);
        if (value == null) {
            if (misereValues.size() >= NOP * 2) {
                misereValues.clear();
            }
            value = getMisereValue(elderHand);
            misereValues.put(key, value);
        }
        return value > 0;
    }

    private static final Map<Long, Double> misereValues = new HashMap<>();

    // ‰ to be caught, the median over the 231 possible talons of the risk after the best drop,
    // the dropped cards not counted as gone, as the original estimate took it
    int getMisereBidRisk() {
        int hand = myHand.getBitmap();
        int out = ~hand;
        int[] risks = new int[231];
        int n = 0;
        for (int t0 = CardSet.next(out, 0); t0 != 0; t0 = CardSet.next(out, t0)) {
            for (int t1 = CardSet.next(out, t0); t1 != 0; t1 = CardSet.next(out, t1)) {
                risks[n++] = bestDropRisk(hand | t0 | t1);
            }
        }
        Arrays.sort(risks, 0, n);
        return risks[n / 2];
    }

    // the least getEval4Misere() of the 12 cards without 2 of them, suit by suit: the suits are
    // independent, both drops from one suit or one from each of 2 suits, each suit at its least risk
    private int bestDropRisk(int bitmap) {
        int[][] suitRisks = new int[4][];
        int suits = 0;
        int bitset = 0;
        while ((bitset = CardSet.bm4NextSuit(bitmap, bitset)) != 0) {
            suitRisks[suits++] = suitDropRisks(bitset);
        }
        int best = Integer.MAX_VALUE;
        int[] drops = new int[suits];
        for (int a = 0; a < suits; ++a) {
            for (int b = a; b < suits; ++b) {
                Arrays.fill(drops, 0);
                ++drops[a];
                ++drops[b];
                double pab = 1;
                for (int i = 0; i < suits; ++i) {
                    int risk = suitRisks[i][drops[i]];
                    if (risk < 0) {
                        pab = -1;   // a suit of 1 card has no 2 to drop
                        break;
                    }
                    if (risk > 0) {
                        pab *= 1 - (double) risk / MAX_EVAL;
                    }
                }
                if (pab >= 0) {
                    best = Math.min(best, (int) ((1 - pab) * MAX_EVAL));
                }
            }
        }
        return best;
    }

    // by the rank bitmap of a suit's cards: its least risk after 0, 1, 2 of its cards dropped,
    // -1 when it has fewer cards
    private static final int[][] suitDropRisks = new int[1 << 8][];

    private static synchronized int[] suitDropRisks(int bitset) {
        int r = ranks(new CardSet(bitset));
        int[] res = suitDropRisks[r];
        if (res != null) {
            return res;
        }
        res = new int[] {suitRisk(bitset), -1, -1};
        for (int bit0 = CardSet.next(bitset, 0); bit0 != 0; bit0 = CardSet.next(bitset, bit0)) {
            int risk = suitRisk(bitset & ~bit0);
            if (res[1] < 0 || risk < res[1]) {
                res[1] = risk;
            }
            for (int bit1 = CardSet.next(bitset, bit0); bit1 != 0; bit1 = CardSet.next(bitset, bit1)) {
                risk = suitRisk(bitset & ~bit0 & ~bit1);
                if (res[2] < 0 || risk < res[2]) {
                    res[2] = risk;
                }
            }
        }
        suitDropRisks[r] = res;
        return res;
    }

    // ‰ to be caught in the suit, as getEval4Misere() takes it with no cards gone
    private static int suitRisk(int bitset) {
        if (bitset == 0) {
            return 0;
        }
        CardSet cards = new CardSet(bitset);
        int[] eval = suitEval(cards, ranks(cards), 0);
        return eval == null ? 0 : eval[0];
    }

    // even the best case, the talon dropped and then 2 own cards, leaves too many tricks
    private boolean hopeless() {
        int hand = myHand.getBitmap();
        for (int bit0 = CardSet.next(hand, 0); bit0 != 0; bit0 = CardSet.next(hand, bit0)) {
            for (int bit1 = CardSet.next(hand, bit0); bit1 != 0; bit1 = CardSet.next(hand, bit1)) {
                if (getTricks4Misere(new CardSet(hand & ~bit0 & ~bit1), bit0 | bit1) <= MISERE_HOPELESS_TRICKS) {
                    return false;
                }
            }
        }
        return true;
    }

    // the misère's expected score by its factor: made once, or a dump point for each trick taken,
    // at most MISERE_CAUGHT_TRICKS;
    // MISERE_DEALS deals, each a random talon with the least risky drop and the other hands at random,
    // played out with open cards. The per suit risk missed what Утяцкий 7.6.5 sees, see TestMisereBook:
    // a hand with long suits is caught rarely, but then the declarer cannot give the lead away,
    // ♠X ♣8 ♦789XJQA ♥7 took 7 tricks; 7A or 79Q are caught often, but for a trick or two.
    // Seeded by the hand: the same hand, the same bid
    double getMisereValue(int elderHand) {
        int hand = myHand.getBitmap();
        Random random = new Random(hand);
        List<Integer> unknown = new ArrayList<>();
        for (int bit = 1; bit != 0; bit <<= 1) {
            if ((hand & bit) == 0) {
                unknown.add(bit);
            }
        }
        int leader = (NOP - elderHand) % NOP;    // self 0, left 1, right 2
        OpenPlay openPlay = new OpenPlay();
        int made = 0;
        int tricks = 0;
        long deadLine = Long.MAX_VALUE;
        long hardDeadLine = Long.MAX_VALUE;
        if (standingTimeLimited) {
            // the minimum past the deadline, but not past a half: a phone is 25-60 times slower, 23 sec
            long now = System.currentTimeMillis();
            deadLine = now + gameManager().config().maxMoveTime.get() * 1000L / 4;
            hardDeadLine = now + gameManager().config().maxMoveTime.get() * 1000L / 2;
        }
        int i;
        for (i = 0; i < MISERE_DEALS; ++i) {
            long now = System.currentTimeMillis();
            if (i >= MIN_MISERE_DEALS && now >= deadLine || i > 0 && now >= hardDeadLine) {
                break;
            }
            Collections.shuffle(unknown, random);
            int[] h = new int[NOP];
            h[0] = leastRiskyDrop(hand | unknown.get(0) | unknown.get(1));
            for (int j = 2; j < unknown.size(); ++j) {
                h[1 + (j - 2) * 2 / (unknown.size() - 2)] |= unknown.get(j);
            }
            int t = openPlay.futureTricks(h, leader, new int[NOP], 0, 0, new int[0], 1, (1 << NOP) - 2);
            if (t == 0) {
                ++made;
            }
            tricks += Math.min(t, MISERE_CAUGHT_TRICKS);
        }
        return (double)(made - tricks) / i;
    }

    // the 12 cards without the 2 that leave the fewest expected tricks, then the least risk,
    // then the fewest discards, see discards()
    private int leastRiskyDrop(int bitmap) {
        int best = 0;
        int bestTricks = Integer.MAX_VALUE;
        int bestRisk = Integer.MAX_VALUE;
        int bestDiscards = Integer.MAX_VALUE;
        int bit0 = 0;
        while ((bit0 = CardSet.next(bitmap, bit0)) != 0) {
            int bit1 = bit0;
            while ((bit1 = CardSet.next(bitmap, bit1)) != 0) {
                CardSet cards = new CardSet(bitmap & ~bit0 & ~bit1);
                int tricks = getTricks4Misere(cards, bit0 | bit1);
                int risk = getEval4Misere(cards, bit0 | bit1);
                if (tricks > bestTricks || tricks == bestTricks && risk > bestRisk) {
                    continue;
                }
                int discards = discards(cards, bit0 | bit1);
                if (tricks < bestTricks || risk < bestRisk || discards < bestDiscards) {
                    best = cards.getBitmap();
                    bestTricks = tricks;
                    bestRisk = risk;
                    bestDiscards = discards;
                }
            }
        }
        return best;
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
                probe.value = getEval4Misere(myHand, bit0 | bit1);
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
            // apart no more: the fewest tricks, ♠7K ♣78XK ♦79X ♥8JK keeps ♥8 alone, not ♥8J;
            // ♠8 ♣7A ♦78XK ♥78J + ♣J ♠K keeps ♠8 alone, not ♠8K, see NO_SEVEN_PENALTY
            for (PlayerBid probe : playerBids) {
                CardSet rest = new CardSet(this.myHand);
                rest.remove(probe.drops);
                probe.value = getTricks4Misere(rest, probe.drops.getBitmap());
            }
            Collections.sort(playerBids, (b1, b2) -> b1.value - b2.value);
        }
        fewestDiscards(playerBids);
        if (declarerDrop == DeclarerDrop.Random && gameManager().getDeclarer() instanceof Bot) {
            // a bot declarer's own drop; the defenders guessing a human's drop take the least risk
            return chooseDrop(playerBids, hopeless);
        }
        return playerBids.get(0);
    }

    // on equal values only the drops with the fewest discards stay, sorted by value:
    // the per suit risk knows no discards, ♠79QA ♣79X ♦J ♥79JQ, ♦J ♣X and ♦J ♠A are equal,
    // ♣79 is clean as ♣79X, ♠79QA needs both ♠Q and ♠A discarded, ♠79Q only ♠Q
    private void fewestDiscards(List<PlayerBid> playerBids) {
        int[] discards = new int[playerBids.size()];
        for (int i = 0; i < discards.length; ++i) {
            CardSet rest = new CardSet(this.myHand);
            rest.remove(playerBids.get(i).drops);
            discards[i] = discards(rest, playerBids.get(i).drops.getBitmap());
        }
        List<PlayerBid> res = new ArrayList<>();
        for (int i = 0; i < discards.length; ) {
            int j = i;
            int min = Integer.MAX_VALUE;
            while (j < discards.length && playerBids.get(j).value == playerBids.get(i).value) {
                min = Math.min(min, discards[j++]);
            }
            for (int k = i; k < j; ++k) {
                if (discards[k] == min) {
                    res.add(playerBids.get(k));
                }
            }
            i = j;
        }
        playerBids.clear();
        playerBids.addAll(res);
    }

    // the cards to discard before every suit is clean, the highest ones first
    int discards(CardSet cardSet, int gone) {
        int res = 0;
        int bitset = 0;
        while ((bitset = CardSet.bm4NextSuit(cardSet.getBitmap(), bitset)) != 0) {
            CardSet cards = new CardSet(bitset);
            int g = goneRanks(cards, gone);
            while (!cards.isEmpty() && suitEval(cards, ranks(cards), g) != null) {
                cards.remove(cards.last());
                ++res;
            }
        }
        return res;
    }

    // sorted by risk: a clean one if any, otherwise one of the least risks up to the first jump;
    // hopeless, sorted by the expected tricks, see getDrop(): one within DROP_JUMP of the least,
    // the values grow slowly and the jumps between neighbours would take them all;
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
            if (trick.getNumber() == 0) {
                // the last to play under the declarer's card on top: a catch now whatever the drop,
                // no search; ♣79A on ♣8 ♣X, ♣A went with the guess of ♣Q kept, the phone's deadline
                // cut the probes before the drop ♣Q ♦9
                int duck = duckUnder(trick, candidates(trick));
                if (duck != 0) {
                    return Card.get(duck);
                }
            }
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

    // the defenders' cards are not known yet; no suit has a 100% clean lead: the shortest suit,
    // the one with more cards out on a tie (one defender less likely void)
    private Card declarerPlay(Trick trick) {
        HandResults handResults = misereTricks(trick.getTurn());
        CardSet.ListData bestListData = null;
        boolean anyClean = false;
        CardSet.ListData shortest = null;
        int shortestOthers = 0;
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
            Card.Suit s = listData.suit;
            CardSet others = new CardSet(leftHand.list(s), rightHand.list(s));
            if (cleanLead(cardList, others) != null) {
                anyClean = true;
            }
            if (shortest == null || shortest.thisSuit.size() > cardList.size() ||
                    shortest.thisSuit.size() == cardList.size() && shortestOthers < others.size()) {
                shortest = listData;
                shortestOthers = others.size();
            }
        }
        if (bestListData == null) {
            throw new RuntimeException("prog error");
        }
        if (!anyClean) {
            bestListData = shortest;
        }
        CardSet cleanSuit = bestListData.thisSuit;
        Card.Suit s = bestListData.suit;
        return cleanSuit.getOptimalStart(leftHand.list(s), rightHand.list(s));
    }

    // a lead that cannot catch the declarer in this suit whatever the split, null if none;
    // the highest one, a lower one stays for later. The worst split: all the others with one defender,
    // the other one discards and never has to go over
    static Card cleanLead(CardSet mine, CardSet others) {
        int myRanks = ranks(mine);
        int otherRanks = ranks(others);
        if (otherRanks == 0) {
            return null;    // nobody follows, the lead takes it
        }
        int myCards = mine.getBitmap();
        int bit = 0;
        while ((bit = CardSet.prev(myCards, bit)) != 0) {
            int lead = 1 << rankIndex(Card.get(bit));
            if ((otherRanks & (lead - 1)) != 0) {
                continue;   // a card under it
            }
            boolean clean = true;
            for (int over = otherRanks; over != 0; over &= over - 1) {
                // the defender goes over with any card, then leads the rest at will
                if (!survives(myRanks & ~lead, otherRanks & ~(over & -over))) {
                    clean = false;
                    break;
                }
            }
            if (clean) {
                return Card.get(bit);
            }
        }
        return null;
    }

    // rank bitmaps of one suit: the defender leads its cards in any order,
    // the declarer goes under each with the highest card that does
    private static boolean survives(int mine, int others) {
        if (mine == 0) {
            return true;
        }
        for (int h = others; h != 0; h &= h - 1) {
            int bit = h & -h;
            int under = mine & (bit - 1);
            if (under == 0) {
                return false;
            }
            if (!survives(mine & ~Integer.highestOneBit(under), others & ~bit)) {
                return false;
            }
        }
        return true;
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
    // later and the guess is tested elsewhere, a discard unblocks a possible hole, then the most tricks,
    // then a lead the declarer can be void in under fewer drops, then a catch in this very trick,
    // then the list's card:
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
        int candidates = candidates(trick);
        if (trick.getNumber() == 0) {
            // full hands: a search per guess takes too long for every card of the first trick,
            // only for the cards of the list's card run under its guess, the run can hide a card
            // the declarer kept: ♣89XJA against ♣7 with ♣QK dropped, ♣A lets ♣K go if ♣Q ♦X were
            candidates = sameRun(trick, candidates, listCard, guesses.get(0));
        }
        if (CardSet.size(candidates) < 2) {
            return listCard;
        }
        int n = CardSet.size(candidates);
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
        // a lead: the declarer's possible drops that leave him void in its suit, the lead gives him a discard,
        // all the drops counted, the guesses may miss the one he made, see voidDrops()
        int[] voids = new int[n];
        if (trick.isEmpty()) {
            for (int i = 0; i < n; ++i) {
                voids[i] = voidDrops(trick, CardSet.get(candidates, i).getSuit());
            }
        }
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
                if ((!trick.isEmpty() || inSuits(CardSet.get(candidates, i), holes) == 0) &&
                        takesNow(trick, guesses.get(g), CardSet.get(candidates, i))) {
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
        int listIndex = CardSet.indexOf(candidates, listCard);
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
                diff = holeScore(trick, CardSet.get(candidates, i), holes, someHoles) -
                    holeScore(trick, CardSet.get(candidates, best), holes, someHoles);
            }
            if (diff == 0) {
                // 'паровоз': the defenders take their tricks in the other suits first, caught the declarer
                // keeps only the losers: ♠78 ♣7K ♥79XJQ with ♦9 ♣A dropped, ♠X or ♣8 6 tricks, ♦7 1
                diff = totalTricks[i] - totalTricks[best];
            }
            if (diff == 0) {
                // the suits he holds go first, they test whether he kept the risky cards,
                // ♦7 lets ♣K go, after ♣8 ♣K has to stay or take the trick
                diff = voids[best] - voids[i];
            }
            if (diff == 0) {
                diff = now[i] - now[best];
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
            sb.append(String.format(" %s %d,%d/%d", CardSet.get(candidates, i), caught[i], now[i], listGuess[i]));
        }
        for (Card.Suit suit : Card.Suit.values()) {
            if (inSuits(suit, holes)) {
                sb.append(", hole ").append(suit.getCode());
            } else if (inSuits(suit, someHoles)) {
                sb.append(", hole? ").append(suit.getCode());
            }
        }
        Logger.printf("trick %d, drops guessed %d, caught guesses,now/tricks for the list's guess:%s -> %s, %,d msec\n",
            trick.getNumber(), complete, sb, CardSet.get(candidates, best), System.currentTimeMillis() - start);
        return CardSet.get(candidates, best);
    }

    // the declarer's drops not revealed yet that leave him void in the suit: the cards of the suit
    // he may hold all among them, every pair of his unrevealed cards counts the same
    int voidDrops(Trick trick, Card.Suit suit) {
        CardSet known = gameManager().declarerHand;
        int m = known.size();
        int d = m - TrickList.getInstance().declarerSize(trick);
        int k = known.list(suit).size();
        if (d <= 0 || k > d) {
            return k == 0 ? 1 : 0;
        }
        // C(m - k, d - k), d is 1 or 2
        return d - k == 0 ? 1 : m - k;
    }

    // the last to play, the declarer's card on top: the highest candidate under it in its suit, it keeps
    // the lower ones; 0 if none
    static int duckUnder(Trick trick, int candidates) {
        if (trick.size() != NOP - 1 || trick.getTop() != GameManager.getInstance().declarerNumber) {
            return 0;
        }
        Card top = trick.topCard;
        int res = 0;
        int bit = 0;
        while ((bit = CardSet.next(candidates, bit)) != 0) {
            Card card = Card.get(bit);
            if (card.getSuit().equals(top.getSuit()) && card.compareInTrick(top) < 0) {
                res = bit;
            }
        }
        return res;
    }

    // misère with the defenders' cards open: the declarer's plays, they tell his drop, see plausibleDrop()
    private static final List<DeclarerPlay> declarerPlays = new ArrayList<>();

    static void clearDeclarerPlays() {
        declarerPlays.clear();
    }

    // before the card leaves the declarer's hand
    static void declarerPlays(Trick trick, Card card) {
        GameManager gameManager = GameManager.getInstance();
        if (!Config.Bid.BID_MISERE.equals(trick.minBid) || !gameManager.cardsRevealed) {
            return;
        }
        declarerPlays.add(new DeclarerPlay(trick, card));
    }

    // a guess where the declarer, seeing the defenders' cards, played a card taking more tricks than another
    // one would: with ♦7J ♦7 on ♦8 leaves ♦J for 6 tricks, the declarer would rather take one with it now
    @Override
    boolean plausibleDrop(CardSet hand) {
        GameManager gameManager = gameManager();
        int played = gameManager.declarerPlayed.getBitmap();
        for (DeclarerPlay declarerPlay : declarerPlays) {
            int then = hand.getBitmap() | (played & ~declarerPlay.playedBefore);
            Boolean res = declarerPlay.plausible.get(then);
            if (res == null) {
                res = declarerPlay.plausible(then);
                declarerPlay.plausible.put(then, res);
            }
            if (!res) {
                return false;
            }
        }
        return true;
    }

    private static class DeclarerPlay {
        final int declarer;
        final int leader;
        final int k;
        final int[] cards = new int[NOP];   // the trick, then the declarer's card
        final int card;
        final int[] hands = new int[NOP];   // the defenders' ones
        final int playedBefore;             // GameManager.declarerPlayed
        // by the guess of the declarer's hand then
        final Map<Integer, Boolean> plausible = new HashMap<>();

        DeclarerPlay(Trick trick, Card card) {
            GameManager gameManager = GameManager.getInstance();
            declarer = gameManager.declarerNumber;
            leader = trick.getStartedBy();
            k = trick.size();
            for (int i = 0; i < k; ++i) {
                cards[i] = CardSet.bit(trick.getCard(i));
            }
            this.card = CardSet.bit(card);
            Player[] players = gameManager.getPlayers();
            for (int i = 0; i < NOP; ++i) {
                if (i != declarer) {
                    hands[i] = players[i].myHand.getBitmap();
                }
            }
            playedBefore = gameManager.declarerPlayed.getBitmap();
        }

        // hand: the declarer's then
        boolean plausible(int hand) {
            if ((hand & card) == 0) {
                return true;
            }
            int legal = hand;
            if (k > 0) {
                int follow = hand & AllPassEndgame.suitMaskOf(cards[0]);
                if (follow != 0) {
                    legal = follow;
                }
            }
            if (legal == card) {
                return true;    // no choice
            }
            int tricks = tricks(hand, card);
            for (int bit = CardSet.next(legal, 0); bit != 0; bit = CardSet.next(legal, bit)) {
                if (bit == card) {
                    continue;
                }
                if (k == NOP - 1 && !wins(card) && wins(bit)) {
                    continue;   // a misère declarer never takes a trick to lose fewer later
                }
                if (tricks(hand, bit) < tricks) {
                    return false;
                }
            }
            return true;
        }

        // the card tops the trick so far
        private boolean wins(int bit) {
            Card c = Card.get(bit);
            for (int i = 0; i < k; ++i) {
                Card other = Card.get(cards[i]);
                if (other.getSuit().equals(c.getSuit()) && other.compareInTrick(c) > 0) {
                    return false;
                }
            }
            return c.getSuit().equals(Card.get(cards[0]).getSuit());
        }

        // the declarer's tricks from this one on, the open cards
        private int tricks(int hand, int bit) {
            int[] h = hands.clone();
            h[declarer] = hand ^ bit;
            int[] c = cards.clone();
            c[k] = bit;
            int defenders = ((1 << NOP) - 1) & ~(1 << declarer);
            return seriesOpenPlay.futureTricks(h, leader, c, k + 1, 0, new int[0], 1 << declarer, defenders);
        }
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
        for (Card card : legal) {
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
