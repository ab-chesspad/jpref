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
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.jpref.config.Config.Bid;
import static com.ab.jpref.config.Config.ROUND_SIZE;
import com.ab.util.Bidder;
import com.ab.util.ScoreCalculator;
import com.ab.util.Bidder.PlayerBid;
import static com.ab.util.Logger.printf;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Bot extends Player implements Serializable {
    public static CardSet debugDrop = null;
    public static DeclarerDrop declarerDrop = DeclarerDrop.Random;

    enum DeclarerDrop {
        First,      // the least risk
        Random      // one of the least risks, the defenders cannot be sure of it, see chooseDrop()
    }

    // when declarer is Bot, it uses the same logic for declarer and defenders
    // so playerBid is the same and can be shared
    static PlayerBid playerBid;

    // the bots' open card searches, one at a time on the game thread: a series of searches
    // (playStanding(), ForTricksBot.robustCard()) keeps the memo from one search to the next
    static final OpenPlay seriesOpenPlay = new OpenPlay();

    // whist standing, see playStanding()
    static final int MIN_DEALS = 6;
    static final int MAX_DEALS = 40;
    // as few possible deals as this: every one of them, 40 random ones were noise; 6♠ ♠Q ♦7K ♥9Q
    // at trick 5, no trumps out, ♠Q 26/40 over ♥9 22/40, all the 252 deals: ♥9 145, ♠Q 138
    static final int EXACT_DEALS = 256;
    static boolean standingTimeLimited = true;      // by a quarter of Config.maxMoveTime, MIN_DEALS are searched anyway; tests: no limit
    static final int DEAL_TRIES = 50;       // a random deal can miss a void, try again
    // a declarer's hand with no trick to spare is played the other way: ♠XKA ♣79XA ♥A against 7♠,
    // 7.00 by Bidder, plays clubs at once to bring down the defenders' ♠Q; the trumps drawn first
    // say it is not there. The best of the tries when none has the margin
    static final double CONTRACT_MARGIN = 0.25;
    static final int CONTRACT_TRIES = 20;   // the declarer's hand worth his contract, otherwise the best one
    static final int WHIST_TRIES = 2000;    // the declarer's hands before the play, all the deals, see whistHands()
    static final int MIN_WHIST_TRIES = 200; // tried even past the deadline
    static final int BIG_GAME = 8;          // no half-whist: a single whister plays standing with sure tricks only, see standingHolds()
    static final int LONG_SUIT = 4;         // the passer invited to play standing keeps it, see playStanding()
    int longSuit;                           // its suit mask, 0: none
    int declarerLeads;                      // the suit masks the declarer led, see defenderRules()

    static Random standingRandom = new Random();    // tests: seeded
    // tests and an input file: >= 0, each search reseeds standingRandom from it, see reseed()
    static long standingSeed = -1;

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
        declarerLeads = 0;
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
        long start = System.currentTimeMillis();
        PlayerBid playerBid = forTricksBot.getMaxPlayerBid(minBid, elderHand);
        Bid bid = playerBid.toBid();
        printf("%s bid: %s, time %,d msec\n", this.getName(), bid, System.currentTimeMillis() - start);
        MisereBot misereBot = MisereBot.getInstance(this);
        start = System.currentTimeMillis();
        boolean misereOK = bid.compareTo(Bid.BID_XS) < 0 && misereBot.evalMisere(elderHand);
        printf("%s bid, misere %b, time %,d msec\n", this.getName(), misereOK, System.currentTimeMillis() - start);
        if (misereOK && misereBot.getEval4Misere(myHand) == 0 && cleanMisereAllPass()) {
            printf("%s bid, clean misère, all-pass pays more\n", this.getName());
            this.bid = Bid.BID_PASS;
            return Bid.BID_PASS;
        }
        if (misereOK) {
            this.bid = Bid.BID_MISERE;
            return Bid.BID_MISERE;
        }

        if (bid.compareTo(minBid) < 0 && alwaysBid6S(minBid)) {
            bid = minBid;
        } else if (bid.compareTo(minBid) < 0 && overbidOnAllPass(minBid, elderHand)) {
            bid = minBid;
        }
        if (bid.compareTo(minBid) >= 0) {
            bid = minBid;
        } else {
            bid = Bid.BID_PASS;
        }
        this.bid = bid;
        return bid;
    }

    // the high cards that always bid 6♠ on the first bid, any hand, 'сочетания фигур, при которых всегда
    // следует объявлять «6 пик»' (Утяцкий 7.4): A, A, A, A; AK, A, A; AK, A, KQ; AK, A, KJ10; AK, A, Qxxx;
    // AQJ, A, A; AK, AK. Each a different suit, a longer or higher holding does for a shorter one
    static final int ALWAYS_A = 0, ALWAYS_AK = 1, ALWAYS_KQ = 2, ALWAYS_KJX = 3, ALWAYS_QXXX = 4, ALWAYS_AQJ = 5;
    static final int MIN_QXXX = 4;
    static final int[][] ALWAYS_6S = {
        {ALWAYS_A, ALWAYS_A, ALWAYS_A, ALWAYS_A},
        {ALWAYS_AK, ALWAYS_A, ALWAYS_A},
        {ALWAYS_AK, ALWAYS_A, ALWAYS_KQ},
        {ALWAYS_AK, ALWAYS_A, ALWAYS_KJX},
        {ALWAYS_AK, ALWAYS_A, ALWAYS_QXXX},
        {ALWAYS_AQJ, ALWAYS_A, ALWAYS_A},
        {ALWAYS_AK, ALWAYS_AK},
    };

    boolean alwaysBid6S(Bid minBid) {
        if (!Bid.BID_6S.equals(minBid)) {
            return false;   // somebody bid a game already, or 7♠ after all-pass
        }
        int[] suits = new int[Card.Suit.values().length];
        for (Card.Suit suit : Card.Suit.values()) {
            suits[suit.getValue()] = (myHand.getBitmap() >>> (suit.getValue() * CardSet.SUIT_LIST_LENGTH)) &
                CardSet.SUIT_MASK;
        }
        for (int[] pattern : ALWAYS_6S) {
            if (matches(pattern, 0, suits, 0)) {
                return true;
            }
        }
        return false;
    }

    // the pattern's holdings from index on, each in one of the suits not used yet
    private static boolean matches(int[] pattern, int index, int[] suits, int used) {
        if (index == pattern.length) {
            return true;
        }
        for (int s = 0; s < suits.length; ++s) {
            if ((used & (1 << s)) == 0 && holds(pattern[index], suits[s]) &&
                    matches(pattern, index + 1, suits, used | 1 << s)) {
                return true;
            }
        }
        return false;
    }

    private static boolean holds(int holding, int cards) {
        final int ACE = 1 << 7, KING = 1 << 6, QUEEN = 1 << 5, JACK = 1 << 4, TEN = 1 << 3;
        switch (holding) {
            case ALWAYS_A:
                return (cards & ACE) != 0;
            case ALWAYS_AK:
                return (cards & (ACE | KING)) == (ACE | KING);
            case ALWAYS_KQ:
                return (cards & (KING | QUEEN)) == (KING | QUEEN);
            case ALWAYS_KJX:
                return (cards & (KING | JACK | TEN)) == (KING | JACK | TEN);
            case ALWAYS_QXXX:
                return (cards & QUEEN) != 0 && CardSet.size(cards) >= MIN_QXXX;
            case ALWAYS_AQJ:
                return (cards & (ACE | QUEEN | JACK)) == (ACE | QUEEN | JACK);
        }
        return false;
    }

    // a clean misère takes no trick on all-pass either: with both opponents passed already a pass
    // makes the all-pass sure, see ScoreCalculator.allPassValue() and misereValue().
    // The all-pass pays more from the price 2 on: 'на третьем круге Распасовки, имея на третьей руке
    // чистый Мизер, гораздо выгоднее спасовать' (Утяцкий 6.2)
    boolean cleanMisereAllPass() {
        Player[] players = gameManager().getPlayers();
        for (int i = 1; i < NOP; ++i) {
            if (!Bid.BID_PASS.equals(players[(this.number + i) % NOP].getBid())) {
                return false;
            }
        }
        int trickCost = ScoreCalculator.allPassTrickCost(gameManager().getAllPassFactor());
        return ScoreCalculator.allPassValue(0, trickCost) > ScoreCalculator.misereValue(0);
    }

    // to compare passing with declaring a game the hand does not quite hold
    static final int ALL_PASS_SAMPLES = 32;

    // the expected loss of a pass that leads to all-pass against the expected loss of declaring minBid,
    // 'лучше без двух, чем распасы'; ALL_PASS_SAMPLES deals of the unknown cards, seeded by the hand:
    // all-pass tricks: the unwanted tricks of each suit when they lead it,
    // the game: the tricks of the best drop and contract with the talon,
    // the chance of all-pass: the opponents yet to bid would pass too.
    // The values, see ScoreCalculator.allPassValue() and gameValue().
    boolean overbidOnAllPass(Bid minBid, int elderHand) {
        GameManager gameManager = gameManager();
        Player[] players = gameManager.getPlayers();
        int absElder = (this.number - elderHand + NOP) % NOP;
        List<Integer> waiting = new ArrayList<>();      // opponents yet to bid
        for (int i = 1; i < NOP; ++i) {
            int q = (this.number + i) % NOP;
            Bid b = players[q].getBid();
            if (Bid.BID_UNDEFINED.equals(b)) {
                waiting.add(q);
            } else if (!Bid.BID_PASS.equals(b)) {
                return false;   // somebody plays, no all-pass
            }
        }

        int hand = myHand.getBitmap();
        List<Integer> unknown = new ArrayList<>();
        for (int bit = 1; bit != 0; bit <<= 1) {
            if ((hand & bit) == 0) {
                unknown.add(bit);
            }
        }
        if (unknown.size() != 2 + 2 * ROUND_SIZE) {
            return false;
        }
        Random random = new Random(hand);
        Bidder bidder = Bidder.getInstance();
        int trickCost = ScoreCalculator.allPassTrickCost(gameManager.getAllPassFactor());
        int[][] deals = new int[ALL_PASS_SAMPLES][];  // talon, left, right
        double allPass = 0;
        for (int n = 0; n < ALL_PASS_SAMPLES; ++n) {
            Collections.shuffle(unknown, random);
            int talon = unknown.get(0) | unknown.get(1);
            int left = 0;
            int right = 0;
            for (int j = 2; j < 2 + ROUND_SIZE; ++j) {
                left |= unknown.get(j);
                right |= unknown.get(j + ROUND_SIZE);
            }
            deals[n] = new int[] {talon, left, right, unknown.get(0)};

            int tricks = 0;
            for (Card.Suit suit : Card.Suit.values()) {
                CardSet mine = myHand.list(suit);
                CardSet l = new CardSet(left).list(suit);
                CardSet r = new CardSet(right).list(suit);
                if (mine.isEmpty() || l.isEmpty() && r.isEmpty()) {
                    continue;
                }
                tricks += mine.getUnwantedTricks(l, r).maxTheyStart;
            }
            tricks = Math.min(tricks, ROUND_SIZE);
            allPass += ScoreCalculator.allPassValue(tricks, trickCost);
        }
        allPass /= ALL_PASS_SAMPLES;
        if (allPass >= 0) {
            return false;   // all-pass is no loss
        }

        double game = 0;
        int allPassDeals = 0;
        for (int[] deal : deals) {
            int talon = deal[0];
            int left = deal[1];
            int right = deal[2];
            int made = bidder.searchBid(new CardSet(hand | talon), minBid, elderHand).tricks;
            game += ScoreCalculator.gameValue(minBid, made);

            boolean nobody = true;
            for (int q : waiting) {
                int other = q == (this.number + 1) % NOP ? left : right;
                int qElder = (q - absElder + NOP) % NOP;
                CardSet cards = new CardSet(other | deal[3]);
                if (bidder.getBid(cards, minBid, qElder, 1).toBid().compareTo(minBid) >= 0) {
                    nobody = false;
                    break;
                }
            }
            if (nobody) {
                ++allPassDeals;
            }
        }
        game /= ALL_PASS_SAMPLES;
        double chance = (double) allPassDeals / ALL_PASS_SAMPLES;
        boolean overbid = game > chance * allPass;
        printf("%s all-pass %.1f, chance %.2f, %s %.1f%s\n", getName(), allPass, chance, minBid, game,
            overbid ? ", overbid" : "");
        return overbid;
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

    // whist or not by the expected score: the bot deals the cards it does not see as in playStanding()
    // and counts the defenders' tricks in each deal with open cards; player1 does not count
    // on player2's whist. On 6 and 7: player2 can say half-whist after player1 passed,
    // then player1 has the 2nd chance and whists alone; both can whist. On 8 and up only one whists.
    @Override
    public void respondOnDeclaration() {
        GameManager gameManager = gameManager();
        int player1 = (gameManager.declarerNumber + 1) % NOP;
        int player2 = (gameManager.declarerNumber + 2) % NOP;
        Bid minBid = gameManager.getMinBid();
        boolean withHalf = minBid.goal() <= 7;
        WhistScore score = new WhistScore(minBid, gameManager.config().whistType.get().getSelectedValue());
        long start = System.currentTimeMillis();
        List<int[]> deals = whistDeals();     // the tricks of player1 and player2
        int n = Math.max(1, deals.size());
        double alone = 0;       // whisting alone
        double both = 0;        // both whist, this bot's part
        double passer = 0;      // the other whists alone
        for (int[] deal : deals) {
            int tricks = deal[0] + deal[1];
            int own = number == player1 ? deal[0] : deal[1];
            alone += score.alone(tricks);
            both += score.both(own, tricks);
            passer += score.passer(tricks);
        }
        alone /= n;
        both /= n;
        passer /= n;

        // no move - no whist: on the lead without a good one the defender passes when both whist and
        // play standing; the single whister lays and plays both hands, it does not apply.
        // player1 leads with the declarer last, player2 with the declarer next
        boolean noMove = gameManager.elderHand == number &&
            goodLeads(myHand.getBitmap(), minBid.getTrump(), number == player1 ? 1 : 2) == 0;
        // no sure trick: without an ace and a trick in trumps the tricks of kings and queens, ♠QK ♦XQK,
        // are the open cards' finesses, the declarer guesses or ruffs them
        boolean noTrick = !hasSureTrick(myHand.getBitmap(), minBid.getTrump(), minBid.goal()) ||
            loneTrick(myHand.getBitmap(), minBid.getTrump(), minBid.goal());
        boolean noWhist = noMove || noTrick;
        Bid bid;
        if (this.number == player1) {
            // first or on the 2nd chance: player1 cannot count on player2's whist,
            // a pass gets nothing when the declarer makes his contract. No move counts only when
            // player2 can still whist too: not on the 2nd chance, not on 8 and up. On the 2nd chance
            // the single whister needs SECOND_CHANCE_TRICKS sure ones: ♠9A ♥789K whists against 7♠,
            // ♠89 ♣789JA does not, 2 small trumps and an ace the declarer ruffs
            boolean secondChance = Bid.BID_HALF_WHIST.equals(gameManager.getBid(player2));
            boolean bothCanWhist = withHalf && !secondChance;
            boolean weak = secondChance && sureTricks(myHand.getBitmap(), minBid.getTrump()) < SECOND_CHANCE_TRICKS;
            // a whist in every side suit, A or Kx, makes up for no sure trick on the 2nd chance:
            // ♠79QK ♣8K ♦XQ ♥XK against 7♦ needs 1 trick of 3 chances
            boolean everySide = secondChance && guardedSides(myHand.getBitmap(), minBid.getTrump()) >= SIDE_SUITS;
            bid = alone > score.noGame() && (everySide || !noTrick && !(noMove && bothCanWhist) && !weak) ?
                Bid.BID_WHIST : Bid.BID_PASS;
        } else if (gameManager.getBid(player1).isWhist()) {
            // player2 lets player1 whist alone
            bid = withHalf && both > passer && !noWhist ? Bid.BID_WHIST : Bid.BID_PASS;
        } else if (withHalf) {
            // the sure tricks that make the defenders' goal whist, the open cards' deals see the declarer's
            // voids ruff the aces too often: ♠7XA ♣K ♦8A ♥79XQ against 7♣ needs 2 of ♠A ♦A ♥Q
            boolean enough = sureTricks(myHand.getBitmap(), minBid.getTrump()) >= minBid.defenderGoal();
            bid = (alone > score.half() || enough) && !noTrick ? Bid.BID_WHIST : Bid.BID_HALF_WHIST;
        } else {
            bid = alone > score.noGame() && !noTrick ? Bid.BID_WHIST : Bid.BID_PASS;
        }
        if (GameManager.DEBUG_BOTH_WHIST && !minBid.equals(Bid.BID_MISERE)) {
            bid = Bid.BID_WHIST;
        }
        printf("%s whist, deals %d: alone %.2f, both %.2f, passer %.2f," +
                " half %.2f, no game %.2f -> %s, time %,d msec\n",
            getName(), deals.size(), alone, both, passer, score.half(), score.noGame(), bid,
            System.currentTimeMillis() - start);
        this.bid = bid;     // BID_WHIST: standing or laying is selected later, see playWhistLaying()
        halfWhist = bid.equals(Bid.BID_HALF_WHIST);
    }

    // the single whister selects, both whisters play standing anyway
    @Override
    public boolean playWhistLaying() {
        Bid minBid = gameManager().getMinBid();
        Card.Suit trump = minBid.getTrump();
        long start = System.currentTimeMillis();
        int position = (number - gameManager().declarerNumber + NOP) % NOP;
        boolean standing = GameManager.DEBUG_STANDING_WHIST ||
            somethingToHide(myHand.getBitmap(), trump, position) && standingHolds(myHand.getBitmap(), minBid) &&
            !scattered(myHand.getBitmap(), trump, position);
        bid = standing ? Bid.BID_WHIST_STANDING : Bid.BID_WHIST_LAYING;
        printf("%s whist %s, time %,d msec\n", getName(), standing ? "standing" : "laying",
            System.currentTimeMillis() - start);
        return !standing;
    }

    // a trick in every suit, A, Kx or Qxx, and no good lead: standing, the passer cannot tell which
    // cards to play to them, laying the whister plays the right ones, ♠8XK ♣7XK ♦XK ♥8A against 6♦
    static boolean scattered(int hand, Card.Suit trump, int position) {
        if (goodLeads(hand, trump, position) != 0) {
            return false;
        }
        for (Card.Suit suit : Card.Suit.values()) {
            int cards = (hand >>> (suit.getValue() * CardSet.SUIT_LIST_LENGTH)) & CardSet.SUIT_MASK;
            if (!sureTrick(cards)) {
                return false;
            }
        }
        return true;
    }

    // standing alone, the whister cannot see the passer's cards: in no-trump he has no clue what to
    // keep after a few tricks, ♠8 ♦9XQK ♥789XQ against 7-; on BIG_GAME and up he needs a sure trick,
    // A, Kx or Qxx, in trumps or in 2 suits at least, otherwise he lays
    static boolean standingHolds(int hand, Bid minBid) {
        Card.Suit trump = minBid.getTrump();
        if (trump == null) {
            return false;
        }
        if (minBid.goal() < BIG_GAME) {
            return true;
        }
        int sure = 0;
        for (Card.Suit suit : Card.Suit.values()) {
            int cards = (hand >>> (suit.getValue() * CardSet.SUIT_LIST_LENGTH)) & CardSet.SUIT_MASK;
            if (!sureTrick(cards)) {
                continue;
            }
            if (suit.equals(trump)) {
                return true;
            }
            ++sure;
        }
        return sure >= 2;
    }

    // an ace, KQJ, its J is a safe lead too, ♣8JQK against 7♠, or a trick in trumps, A, Kx or Qxx,
    // see respondOnDeclaration()
    // against LONG_SIDE_GOAL and up a side suit of LONG_SIDE cards or more has no sure trick, the declarer
    // is void: ♠8 ♣8XJKA ♦79X ♥8 against 9♠ whisted on the ♣A, the declarer had no club;
    // against 8 ♠7JQKA ♣J ♦7J ♥8K whists, Утяцкий #33
    static final int LONG_SIDE = 5;
    static final int LONG_SIDE_GOAL = 9;
    static boolean hasSureTrick(int hand, Card.Suit trump, int goal) {
        final int ACE = 1 << 7, KQJ = 7 << 4;
        for (Card.Suit suit : Card.Suit.values()) {
            int cards = (hand >>> (suit.getValue() * CardSet.SUIT_LIST_LENGTH)) & CardSet.SUIT_MASK;
            if (suit.equals(trump)) {
                if (sureTrick(cards)) {
                    return true;
                }
                continue;
            }
            if (goal >= LONG_SIDE_GOAL && CardSet.size(cards) >= LONG_SIDE) {
                continue;
            }
            if ((cards & ACE) != 0 || (cards & KQJ) == KQJ) {
                return true;
            }
        }
        return false;
    }

    // against LONG_SIDE_GOAL and up one sure trick with 1 or 2 small trumps is no whist: the declarer
    // holds the other trumps and makes voids, ♠89 ♣79XJ ♦J ♥8XA against 9♠ whisted on the ♥A,
    // the declarer dropped ♥79
    static boolean loneTrick(int hand, Card.Suit trump, int goal) {
        if (trump == null || goal < LONG_SIDE_GOAL) {
            return false;
        }
        int trumps = (hand >>> (trump.getValue() * CardSet.SUIT_LIST_LENGTH)) & CardSet.SUIT_MASK;
        int n = CardSet.size(trumps);
        return n >= 1 && n <= 2 && !sureTrick(trumps) && sureTricks(hand, trump) <= 1;
    }

    // the whister's sure tricks: a trick in trumps, A, Kx or Qxx; side A and K of AK, KQJ, Kxx
    static final int SECOND_CHANCE_TRICKS = 2;
    static int sureTricks(int hand, Card.Suit trump) {
        final int ACE = 1 << 7, KING = 1 << 6, KQJ = 7 << 4;
        int res = 0;
        for (Card.Suit suit : Card.Suit.values()) {
            int cards = (hand >>> (suit.getValue() * CardSet.SUIT_LIST_LENGTH)) & CardSet.SUIT_MASK;
            if (suit.equals(trump)) {
                if (sureTrick(cards)) {
                    ++res;
                }
                continue;
            }
            if ((cards & ACE) != 0) {
                ++res;
                if ((cards & KING) != 0) {
                    ++res;
                }
            } else if ((cards & KQJ) == KQJ || (cards & KING) != 0 && CardSet.size(cards) >= 3) {
                ++res;
            }
        }
        return res;
    }

    // the side suits with A or Kx, see respondOnDeclaration()
    static final int SIDE_SUITS = 3;
    static int guardedSides(int hand, Card.Suit trump) {
        final int ACE = 1 << 7, KING = 1 << 6;
        int res = 0;
        for (Card.Suit suit : Card.Suit.values()) {
            if (suit.equals(trump)) {
                continue;
            }
            int cards = (hand >>> (suit.getValue() * CardSet.SUIT_LIST_LENGTH)) & CardSet.SUIT_MASK;
            if ((cards & ACE) != 0 || (cards & KING) != 0 && CardSet.size(cards) >= 2) {
                ++res;
            }
        }
        return res;
    }

    // the ranks of a suit: A, Kx or Qxx
    static boolean sureTrick(int cards) {
        final int ACE = 1 << 7, KING = 1 << 6, QUEEN = 1 << 5;
        int n = CardSet.size(cards);
        return (cards & ACE) != 0 || (cards & KING) != 0 && n >= 2 || (cards & QUEEN) != 0 && n >= 3;
    }

    // laying shows the whister's hand to the declarer, standing keeps him guessing about
    // the trick in trumps without a spare trump, A, Kx or Qxx, with a side suit of 2 cards or less:
    // once it is out, the declarer's small card of that suit makes the honor ruff, ♥9K with ♣7J,
    // the declarer's ♣9 after ♣KA took ♥K instead of partner's ♣Q. With xxA or xxK a small trump
    // ruffs, nothing to hide. Long trumps, 789J, keep the declarer guessing how they split.
    // A system, XQA or 9JK, is the declarer's to hide, not the whister's. Not in trumps: nothing
    // to ruff with, see also standingHolds()
    // A bare ace takes its trick anyway, hidden only while the whister does not lead trumps, and his
    // other leads need a good one: from a weak short suit the declarer continues it and the ace
    // ruffs anyway, ♠8J ♣A ♦8X ♥789JA against 7♣ has none, its ♥A lead is as good laying.
    // Kx or Qxx laid, the declarer plays around it: ♠Q ♣79JQ ♦9JA ♥XK against 7♥ stands
    static final int SHORT_SIDE_SUIT = 2;
    static final int LONG_TRUMPS = 4;
    static boolean somethingToHide(int hand, Card.Suit trump, int position) {
        if (trump == null) {
            return false;
        }
        int trumps = (hand >>> (trump.getValue() * CardSet.SUIT_LIST_LENGTH)) & CardSet.SUIT_MASK;
        if (CardSet.size(trumps) >= LONG_TRUMPS) {
            return true;
        }
        int trick = trumpTrickCards(trumps);
        if (trick == 0 || CardSet.size(trumps) > trick) {
            return false;
        }
        if (trick == 1 && goodLeads(hand, trump, position) == 0) {
            return false;
        }
        for (Card.Suit suit : Card.Suit.values()) {
            if (suit.equals(trump)) {
                continue;
            }
            int cards = (hand >>> (suit.getValue() * CardSet.SUIT_LIST_LENGTH)) & CardSet.SUIT_MASK;
            if (CardSet.size(cards) <= SHORT_SIDE_SUIT) {
                return true;
            }
        }
        return false;
    }

    // the cards the trick in trumps takes: A 1, Kx 2, Qxx 3, 0 without one
    static int trumpTrickCards(int trumps) {
        final int ACE = 1 << 7, KING = 1 << 6, QUEEN = 1 << 5;
        int n = CardSet.size(trumps);
        if ((trumps & ACE) != 0) {
            return 1;
        }
        if ((trumps & KING) != 0 && n >= 2) {
            return 2;
        }
        if ((trumps & QUEEN) != 0 && n >= 3) {
            return 3;
        }
        return 0;
    }

    // a defender's opening lead that most probably gives the declarer no trick, not in trumps;
    // position 2, the declarer plays next: a small card, not the smallest, of the longest suit that
    // promises no trick to the leader (no A, Kx, Qxx); position 1, the declarer plays last:
    // the top of a short strong suit, AK, KQ or QJX with up to 3 cards, or Q of QJxx with a trick
    // in trumps, ♠Q ♣79JQ ♦9JA ♥XK against 7♥ leads ♣Q standing; either position: J of KQJ
    static int goodLeads(int hand, Card.Suit trump, int position) {
        final int ACE = 1 << 7, KING = 1 << 6, QUEEN = 1 << 5, JACK = 1 << 4;
        boolean trumpTrick = trump != null &&
            trumpTrickCards((hand >>> (trump.getValue() * CardSet.SUIT_LIST_LENGTH)) & CardSet.SUIT_MASK) != 0;
        int res = 0;
        int sequences = 0;
        int longest = 0;
        for (Card.Suit suit : Card.Suit.values()) {
            if (suit.equals(trump)) {
                continue;
            }
            int offset = suit.getValue() * CardSet.SUIT_LIST_LENGTH;
            int cards = (hand >>> offset) & CardSet.SUIT_MASK;
            int n = CardSet.size(cards);
            if ((cards & (KING | QUEEN | JACK)) == (KING | QUEEN | JACK)) {
                sequences |= JACK << offset;
            }
            if (position == 2) {
                if (n < 3 || n < longest || (cards & (ACE | KING | QUEEN)) != 0) {
                    continue;
                }
                if (n > longest) {
                    longest = n;
                    res = 0;
                }
                int rest = cards & ~Integer.lowestOneBit(cards);
                res |= Integer.lowestOneBit(rest) << offset;
            } else {
                if (n == 4 && trumpTrick && CardSet.msb(cards) == QUEEN && (cards & JACK) != 0) {
                    res |= QUEEN << offset;
                    continue;
                }
                if (n < 2 || n > 3) {
                    continue;
                }
                int top = CardSet.msb(cards);
                boolean strong = (cards & (top >>> 1)) != 0 && top >= KING ||
                    (cards & (top >>> 1)) != 0 && (cards & (top >>> 2)) != 0 && top == QUEEN;
                if (strong) {
                    res |= top << offset;
                }
            }
        }
        return res | sequences;
    }

    // the deals for respondOnDeclaration(): the tricks of player1 and player2 with open cards,
    // the defenders take all they can together
    // the random deals of a search depend on the player and the moment only: a human in a seat
    // does not use the random numbers a bot would, the other bots' searches stay the same
    void reseed(int trickNumber, int position) {
        if (standingSeed >= 0) {
            standingRandom = new Random(standingSeed * 1_000_003L + ((trickNumber + 1) * NOP + position) * NOP + number);
        }
    }

    private List<int[]> whistDeals() {
        reseed(-1, 0);
        GameManager gameManager = gameManager();
        int declarer = gameManager.declarerNumber;
        Bid minBid = gameManager.getMinBid();
        Card.Suit trump = minBid.getTrump();
        int trumpMask = trump == null ? 0 : CardSet.suitMask(trump);
        int defenders = ((1 << NOP) - 1) & ~(1 << declarer);
        int[] sizes = new int[NOP];
        int[] possible = possibleHands(sizes);
        List<int[]> res = new ArrayList<>();
        long deadLine = Long.MAX_VALUE;
        long dealDeadLine = Long.MAX_VALUE;
        long hardDeadLine = Long.MAX_VALUE;
        long dealHardDeadLine = Long.MAX_VALUE;
        if (standingTimeLimited) {
            // both defenders decide, one after the other: up to a half of Config.maxMoveTime each.
            // The declarer's hands are searched for a quarter of it and until MIN_DEALS of them declare
            // the contract, not past a half: a phone tried a few dozen in an eighth, its 6 deals were
            // mostly other contracts, ♠8 ♣8XJKA ♦79X ♥8 whisted against 9♠. The deals are played in the rest,
            // MIN_DEALS of them past a half of it
            long now = System.currentTimeMillis();
            long budget = gameManager.config().maxMoveTime.get() * 1000L / 2;
            dealDeadLine = now + budget / 4;
            dealHardDeadLine = now + budget / 2;
            hardDeadLine = now + budget;
        }
        List<int[]> hands = whistHands(possible, sizes, declarer, minBid,
            (declarer - gameManager.elderHand + NOP) % NOP, dealDeadLine, dealHardDeadLine);
        if (standingTimeLimited) {
            long now = System.currentTimeMillis();
            deadLine = now + Math.max(0, hardDeadLine - now) / 2;
        }
        for (int[] h : hands) {
            long now = System.currentTimeMillis();
            if (res.size() >= MIN_DEALS && now >= deadLine || !res.isEmpty() && now >= hardDeadLine) {
                break;
            }
            h[number] = myHand.getBitmap();
            int[] tricks = seriesOpenPlay.futureTricksByPlayer(Arrays.copyOf(h, NOP), gameManager.elderHand,
                new int[NOP], 0, trumpMask, new int[0], defenders, defenders);
            int[] deal = {tricks[(declarer + 1) % NOP], tricks[(declarer + 2) % NOP]};
            // a weaker declarer's hand, too few exact ones in the time: a phone's 9♠ deals were mostly 8♠,
            // their declarer is stronger by the tricks short, the defenders' tricks are fewer by them
            for (int k = h.length > NOP + 1 ? h[NOP + 1] : 0; k > 0 && deal[0] + deal[1] > 0; --k) {
                if (deal[0] >= deal[1]) {
                    --deal[0];
                } else {
                    --deal[1];
                }
            }
            res.add(deal);
        }
        return res;
    }

    // a defender's score for the round in whists by the contract's factor, as ScoreCalculator counts it:
    // a pool or dump point is worth 10 whists shared by all three, 20/3 off its owner, 10/3 to each other
    static class WhistScore {
        static final double SHARE = 10.0 / NOP;
        final int goal;
        final int defenderGoal;
        final boolean gentleman;    // the declarer down, the whister shares his tricks with the passer
        final boolean halfFine;

        WhistScore(Bid minBid, Config.WhistType whistType) {
            goal = minBid.goal();
            defenderGoal = minBid.defenderGoal();
            gentleman = !whistType.equals(Config.WhistType.Greedy);
            halfFine = whistType.equals(Config.WhistType.GentlemanHalf);
        }

        // the declarer's tricks short of his contract
        int down(int defenderTricks) {
            return defenderTricks - (ROUND_SIZE - goal);
        }

        // the declarer's pool point, or his dump points when down
        double declarer(int defenderTricks) {
            int down = down(defenderTricks);
            return down > 0 ? SHARE * down : -SHARE;
        }

        double fine(int deficit) {
            if (deficit <= 0) {
                return 0;
            }
            return 2 * SHARE * (halfFine ? deficit / 2.0 : deficit);
        }

        // both passed, the declarer gets his contract
        double noGame() {
            return -SHARE;
        }

        // player2's half-whist, player1 passed again
        double half() {
            return (ROUND_SIZE - goal) / 2 - SHARE;
        }

        double alone(int defenderTricks) {
            int down = down(defenderTricks);
            if (down > 0) {
                return (gentleman ? defenderTricks / 2.0 : defenderTricks) + down + declarer(defenderTricks);
            }
            return defenderTricks - fine(defenderGoal - defenderTricks) + declarer(defenderTricks);
        }

        double passer(int defenderTricks) {
            int down = down(defenderTricks);
            if (down > 0) {
                return (gentleman ? defenderTricks / 2.0 : 0) + down + declarer(defenderTricks);
            }
            return declarer(defenderTricks);
        }

        // both whist, each is responsible for a half of the defenders' goal
        double both(int own, int defenderTricks) {
            int down = down(defenderTricks);
            if (down > 0) {
                return own + down + declarer(defenderTricks);
            }
            double fine = defenderTricks < defenderGoal ? fine(defenderGoal / 2 - own) : 0;
            return own - fine + declarer(defenderTricks);
        }
    }

    @Override
    public Card play(Trick trick) {
        if (myHand.size() == 1) {
            return myHand.first();
        }
        if (trick.minBid == null) {
            return playAllPass(trick);
        }
        if (gameManager().standing) {
            return playStanding(trick);
        }
        if (trick.getNumber() == 0 && trick.isEmpty() && number == gameManager().declarerNumber &&
                !Bid.BID_MISERE.equals(trick.minBid)) {
            // the declarer leads first blind, the defenders' cards are laid after it: the closed cards
            // dealt as in the standing play. ♠QKA ♣KA ♥79XJQ against 8♥ leads ♥, ♠A lets a defender
            // void in ♠ ruff with ♥A and lead ♦ for the declarer to ruff, 7 tricks when ♠ split 4 - 0
            return playStanding(trick);
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

    // Whist standing: every player sees only their own cards, the bot does not know the other two hands.
    // It deals the cards it does not see at random, as far as it knows them: the voids shown, the talon
    // with the declarer, a declarer's hand worth his contract, and searches each deal with open cards,
    // OpenPlay. The goals are as with open cards: the card that makes the contract (the declarer)
    // or sets it (a defender) in the most deals, then the most (the fewest) declarer's tricks.
    Card playStanding(Trick trick) {
        return topOfSequence(trick, searchStanding(trick));
    }

    // a defender leads the top of his touching honors: the search keeps the lowest of the equivalent
    // cards, ♣J of ♣79JQ against 7♥; ♣Q tells partner the ♣J is with the leader, with ♣Kx he ducks
    Card topOfSequence(Trick trick, Card card) {
        if (trick.size() > 0 || number == gameManager().declarerNumber) {
            return card;
        }
        final int JACK = 1 << 4;
        int offset = card.getSuit().getValue() * CardSet.SUIT_LIST_LENGTH;
        int cards = (myHand.getBitmap() >>> offset) & CardSet.SUIT_MASK;
        int bit = CardSet.bit(card) >>> offset;
        int top = bit;
        while ((cards & (top << 1)) != 0) {
            top <<= 1;
        }
        if (top == bit || top < JACK) {
            return card;
        }
        return Card.get(top << offset);
    }

    Card searchStanding(Trick trick) {
        reseed(trick.getNumber(), trick.size());
        GameManager gameManager = gameManager();
        Player[] players = gameManager.getPlayers();
        if (myHand.size() == ROUND_SIZE) {
            longSuit = longSuit(myHand.getBitmap(), gameManager.getMinBid().getTrump());
        }
        if (trick.size() > 0 && trick.getStartedBy() == gameManager.declarerNumber) {
            declarerLeads |= CardSet.suitMask(trick.getStartingSuit());
        }
        int candidates = candidates(trick);
        if (CardSet.size(candidates) < 2) {
            return CardSet.first(candidates);
        }
        if (trick.size() == NOP - 1) {
            // the last to play who wins the trick wins it with his lowest winner: a higher card takes
            // every trick a lower one does later. The search over few deals is noisy here, ♣A of ♣QA
            // after ♣9 ♣X gave the declarer's singleton ♣K a trick
            int winners = 0;
            int bit = 0;
            while ((bit = CardSet.next(candidates, bit)) != 0) {
                Card card = Card.get(bit);
                Card top = trick.topCard;
                if (card.getSuit().equals(top.getSuit()) ? card.compareInTrick(top) > 0
                        : card.getSuit().equals(trick.getTrumpSuit())) {
                    winners |= bit;
                }
            }
            if (CardSet.size(winners) >= 2) {
                candidates &= ~winners | Integer.lowestOneBit(winners);
                if (CardSet.size(candidates) < 2) {
                    return CardSet.first(candidates);
                }
            }
        }
        int me = number;
        int declarer = gameManager.declarerNumber;
        int goal = gameManager.getMinBid().goal();
        Card.Suit trump = gameManager.getMinBid().getTrump();
        if (trick.number == 0 && trick.size() == 0 && me != declarer) {
            // the opening lead: the search cannot tell a lead that gives the declarer a trick in some deals
            // candidates() keeps the lowest of touching cards, ♣J for ♣JQ: a good lead counts as the
            // highest candidate of its suit not above it, topOfSequence() plays it back as ♣Q
            int goodBits = goodLeads(myHand.getBitmap(), trump, (me - declarer + NOP) % NOP);
            int good = 0;
            int bit = 0;
            while ((bit = CardSet.next(goodBits, bit)) != 0) {
                int below = candidates & CardSet.suitMask(Card.get(bit).getSuit()) & ((bit << 1) - 1);
                if (below != 0) {
                    good |= Integer.highestOneBit(below);
                }
            }
            if (good != 0) {
                candidates = good;
                if (CardSet.size(candidates) < 2) {
                    return CardSet.first(candidates);
                }
            }
        }
        if (me != declarer) {
            int rest = defenderRules(trick, candidates, declarer, trump);
            if (rest != candidates) {
                candidates = rest;
                if (CardSet.size(candidates) < 2) {
                    return CardSet.first(candidates);
                }
            }
        }
        if (me != declarer && !bid.isWhist() && longSuit != 0) {
            // invited to play standing: the passer keeps his long suit and leads it, its leads force
            // the declarer to ruff; ♠8XQ ♦8JA ♥79XQ against 6♣: discarding ♥7 on ♣K let it make,
            // a spade set it, and then ♥X led set it, ♠8 did not. Following suit or ruffing,
            // the candidates are all in one suit and stay; a good opening lead stays, see goodLeads().
            // A discard keeps 2 of it at least, the last one is no threat. With A and without K
            // it keeps it and leads another suit: ♦89XQA against 8♣, ♦X lets the declarer's ♦K take
            // a trick, ♦A sets it up, and the whister may be void and has to trump; ♠9 is safer.
            // The other suit: the A on top, ♠A of ♠JQA, or no K and Q to lead away from, ♠9 of ♠9X, not ♥8XK
            int keep = myHand.getBitmap() & longSuit & candidates;
            boolean lead = trick.size() == 0;
            int suit = myHand.getBitmap() & longSuit;
            int ace = Integer.highestOneBit(longSuit);
            if (lead && (suit & ace) != 0 && (suit & ace >>> 1) == 0) {
                int other = candidates & ~longSuit;
                int safe = safeOtherLeads(myHand.getBitmap() & ~longSuit & ~(trump == null ? 0 : CardSet.suitMask(trump))) & other;
                if (safe != 0) {
                    other = safe;
                }
                if (other != 0) {
                    candidates = other;
                    if (CardSet.size(candidates) < 2) {
                        return CardSet.first(candidates);
                    }
                }
            } else if (keep != 0 && keep != candidates && (lead || CardSet.size(suit) >= 2)) {
                // a long suit with its top honor alone, ♠789XK with ♠Q out, gives way to a suit with
                // touching honors, ♦QK: it sets up a trick and gives the declarer none, against 6♥
                int dense = lead && denseLeads(suit) == 0 ?
                    denseLeads(myHand.getBitmap() & ~longSuit & ~(trump == null ? 0 : CardSet.suitMask(trump))) & candidates : 0;
                if (dense != 0) {
                    return CardSet.first(dense);
                }
                // the hand, not the candidates: they keep one of equivalent cards, ♥9 for ♥9X
                int allowed = lead ? longSuitLead(myHand.getBitmap() & longSuit) : candidates & ~keep;
                candidates = allowed;
                if (CardSet.size(candidates) < 2) {
                    return CardSet.first(candidates);
                }
            }
        }
        if (me == declarer && trick.size() == 0 && trump != null) {
            // the declarer without trump control, 2 of the defenders' trumps above his second one,
            // leads a side suit of losers only to bring their trumps down, keeps his side winners: they draw
            // trumps themselves when in, his top card is a trick for them later; ♠JA ♣8X ♥789JA
            // against 7♥ with ♥XQK out, ♣8 led, not ♠A
            int suit = CardSet.suitMask(trump);
            int mine = myHand.getBitmap() & suit;
            int out = leftHand.getBitmap() | rightHand.getBitmap();
            int second = Integer.highestOneBit(mine & ~Integer.highestOneBit(mine));
            int above = out & suit & ~((second << 1) - 1);
            if (second != 0 && CardSet.size(above) >= 2) {
                int losers = 0;
                int bit = 0;
                while ((bit = CardSet.next(candidates & ~suit, bit)) != 0) {
                    // a suit of losers only: ♠J of ♠JA stays, it gives ♠Q a trick and sets up ♠K
                    int suitMask = CardSet.suitMask(Card.get(bit).getSuit());
                    int winners = 0;
                    int b = 0;
                    while ((b = CardSet.next(myHand.getBitmap() & suitMask, b)) != 0) {
                        if (isTop(b, out)) {
                            winners |= b;
                        }
                    }
                    if (winners == 0) {
                        losers |= bit;
                    }
                }
                if (losers != 0) {
                    candidates = losers;
                    if (CardSet.size(candidates) < 2) {
                        return CardSet.first(candidates);
                    }
                }
            }
        }
        if (me == declarer && trick.size() == 0 && trump != null) {
            // the declarer whose trumps all take tricks draws them first: open cards see no ruff
            // to lose, ♣Q led from ♠7JK ♣Q ♦89JQKA against 6♦ gives the lead away and lets a defender
            // ruff a spade with ♦7; ♦J draws ♦7 and ♦X. His trumps above all the outstanding ones,
            // as many as those, take every trick in trumps
            int suit = CardSet.suitMask(trump);
            int out = (leftHand.getBitmap() | rightHand.getBitmap()) & suit;
            if (out != 0) {
                int high = myHand.getBitmap() & suit & ~((Integer.highestOneBit(out) << 1) - 1);
                if (CardSet.size(high) >= CardSet.size(out)) {
                    int draw = candidates & high;
                    if (draw == 0) {
                        return CardSet.first(high);
                    }
                    candidates = draw;
                    if (CardSet.size(candidates) < 2) {
                        return CardSet.first(candidates);
                    }
                }
            }
        }
        int trumpMask = trump == null ? 0 : CardSet.suitMask(trump);
        int[] sizes = new int[NOP];
        int[] possible = possibleHands(sizes);

        int n = CardSet.size(candidates);
        int[] goals = new int[n];   // deals where the card makes (declarer) or sets (defender) the contract
        int[] total = new int[n];   // declarer's tricks over the deals
        double[] score = new double[n];     // the declarer's score over the deals, see declarerScore()
        int pastTricks = players[declarer].getTricks();
        int k = trick.size();
        int[] cards = new int[NOP];
        for (int i = 0; i < k; ++i) {
            cards[i] = CardSet.bit(trick.getCard(i));
        }
        // one memo for the move: the positions repeat for the candidates of a deal, some across the deals
        seriesOpenPlay.startSeries(trumpMask, 1 << declarer, 1 << declarer);
        long start = System.currentTimeMillis();
        long deadLine = Long.MAX_VALUE;
        if (standingTimeLimited) {
            deadLine = start + gameManager.config().maxMoveTime.get() * 1000L / 4;
        }
        List<int[]> all = allDealsStanding(possible, sizes, EXACT_DEALS);
        if (all != null && me != declarer) {
            all = worthContract(all, declarer, possible[declarer], goal, trump);
        }
        int maxDeals = all == null ? MAX_DEALS : all.size();
        int deals = 0;
        while (deals < maxDeals && (deals < MIN_DEALS || System.currentTimeMillis() < deadLine)) {
            int[] h;
            if (all != null) {
                h = all.get(deals).clone();
            } else {
                h = me == declarer ? dealStanding(possible, sizes) : dealForDefender(possible, sizes, declarer, goal, trump);
            }
            if (h == null) {
                break;      // no deal agrees with what the bot knows, should not happen
            }
            h[me] = players[me].myHand.getBitmap();
            int i = 0;
            int bit = 0;
            while ((bit = CardSet.next(candidates, bit)) != 0) {
                int[] hands = h.clone();
                hands[me] &= ~bit;
                int[] played = cards.clone();
                played[k] = bit;
                int tricks = pastTricks + seriesOpenPlay.countTricks(hands, trick.getStartedBy(), played, k + 1,
                    goal - pastTricks);
                if ((tricks >= goal) == (me == declarer)) {
                    ++goals[i];
                }
                score[i] += declarerScore(tricks, goal);
                total[i++] += tricks;
            }
            ++deals;
        }
        if (deals == 0) {
            return CardSet.first(candidates);
        }
        int best = 0;
        boolean discarding = me != declarer && trick.size() > 0 &&
            (myHand.getBitmap() & CardSet.suitMask(trick.getStartingSuit())) == 0;
        for (int i = 1; i < n; ++i) {
            int diff = goals[i] - goals[best];
            if (discarding && Math.abs(diff) <= Math.max(1, deals / 20) &&
                    Math.abs(total[i] - total[best]) * 4 <= deals) {
                // a defender's discard within 5% of the deals and a quarter trick of the best: a suit
                // that keeps its guard, then one that loses it, a strong suit last; ♥7 of ♥79K,
                // not ♦8 of ♦89Q or ♣8 of ♣8JQK on ♠K against 7♠, ♦8 set it in 4 of 40 deals, ♥7 in 2
                int rank = discardRank(CardSet.bit(CardSet.get(candidates, i)), trumpMask);
                int bestRank = discardRank(CardSet.bit(CardSet.get(candidates, best)), trumpMask);
                if (rank != bestRank) {
                    diff = rank - bestRank;
                }
            }
            if (me == declarer) {
                // the declarer by his score: a contract seldom made is played for the fewest tricks down,
                // 6♥ ♣8X ♦8JQ ♥9JQ with ♥XK out, ♥J made it in 1015 of 2000 deals, 3.09 tricks more,
                // ♦J in 812, 3.41; ♥J lost the trump control and a trick to the spade forces
                double d = score[i] - score[best];
                diff = Math.abs(d) < 1e-9 ? 0 : d > 0 ? 1 : -1;
            }
            if (diff == 0 && me != declarer && trick.size() == 0) {
                // a defender's lead setting it as often: not in trumps, the declarer ruffs the other
                // suits and keeps his trumps short; ♠89QK ♣89A ♦JQ against 7♦ ♦7XKA, ♦J and ♠Q
                // set it in 26 of 40 deals, ♦Q drew a trump for him, ♠K makes him ruff
                // the top trump is a trick of its own, led it draws one of his: ♠Q of ♠Q ♣A ♥XJQA
                // with ♠79X left against 7♠, not ♥A
                int bit = CardSet.bit(CardSet.get(candidates, i));
                int bestBit = CardSet.bit(CardSet.get(candidates, best));
                int out = leftHand.getBitmap() | rightHand.getBitmap();
                boolean trumpBit = (bit & trumpMask) != 0 && !isTop(bit, out);
                boolean trumpBest = (bestBit & trumpMask) != 0 && !isTop(bestBit, out);
                diff = Integer.compare(trumpBest ? 1 : 0, trumpBit ? 1 : 0);
            }
            if (diff == 0) {
                diff = me == declarer ? total[i] - total[best] : total[best] - total[i];
            }
            if (diff == 0 && me != declarer && trick.size() == 0) {
                // then the top card of its suit: it takes the trick or forces a trump, a lower one
                // gives the declarer's higher card a trick; ♦J of ♦JA with ♦QK gone, not ♠Q
                // under ♠A, ♠Q ♦JA against 7♥
                int out = leftHand.getBitmap() | rightHand.getBitmap();
                diff = Integer.compare(isTop(CardSet.bit(CardSet.get(candidates, i)), out) ? 1 : 0,
                    isTop(CardSet.bit(CardSet.get(candidates, best)), out) ? 1 : 0);
            }
            if (diff == 0 && me == declarer && trick.size() == 0) {
                // open cards see no ruff to fear, the closed cards do: the declarer draws trumps,
                // ♠QKA ♣KA ♥79XJQ against 8♥, ♠Q ♣K ♥9 all make it, ♥9 first
                int bit = CardSet.bit(CardSet.get(candidates, i));
                int bestBit = CardSet.bit(CardSet.get(candidates, best));
                diff = Integer.compare((bit & trumpMask) != 0 ? 1 : 0, (bestBit & trumpMask) != 0 ? 1 : 0);
            }
            if (diff > 0) {
                best = i;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; ++i) {
            sb.append(String.format(" %s %d/%.1f", CardSet.get(candidates, i), goals[i], (double) total[i] / deals));
            if (me == declarer) {
                sb.append(String.format("/%.2f", score[i] / deals));
            }
        }
        printf("trick %d, standing, deals %d, %s the contract/declarer's tricks:%s -> %s, %,d msec\n",
            trick.getNumber(), deals, me == declarer ? "make" : "set", sb, CardSet.get(candidates, best),
            System.currentTimeMillis() - start);
        return CardSet.get(candidates, best);
    }

    // the declarer's score for the round in whists of WhistScore's scale, both whisting: his pool
    // point less the defenders' whists, or his dump points, their whists and the consolation when down
    static double declarerScore(int tricks, int goal) {
        int defenderTricks = ROUND_SIZE - tricks;
        int down = goal - tricks;
        if (down <= 0) {
            return WhistScore.SHARE - defenderTricks / 2.0;
        }
        return -(WhistScore.SHARE * down + defenderTricks / 2.0 + down);
    }

    // a standing defender's candidates without the cards the search does not see the harm of: it plays
    // each deal with open cards and finds every guess later, so cashing a top card early costs it nothing.
    // On lead, no ace from 3 cards without the king while the king is out: ♣89A against
    // the declarer's ♣JK set his king up, 7♦ made; AK or AKQ are fine. From 4 cards the ace is cashed
    // before the declarer ruffs it, ♠A of ♠89XA ♣8XA ♥7A against 6♦. No small card led away from
    // an ace, it gives the declarer's king or queen a trick: ♣8 of ♣8XA, unless nothing else is left.
    // Not the declarer's suit,
    // not in trumps: he led it for a trick there, ♠7 from ♠JK against 6♦, ♠Q back gave him ♠K.
    // Following partner's card that
    // wins the trick, none higher in its suit: ♠A on partner's ♠K competes with him
    int defenderRules(Trick trick, int candidates, int declarer, Card.Suit trump) {
        int hand = myHand.getBitmap();
        int out = leftHand.getBitmap() | rightHand.getBitmap();
        int res = candidates;
        if (trick.size() == 0) {
            // a plain suit no one else can follow: its card takes a trick or forces the declarer's
            // trump, the defender keeps his own trump trick; ♣7 with every other club gone, not ♠Q
            // of ♠Q ♣7 ♦JA ♥XK against 7♥
            int alone = 0;
            for (Card.Suit suit : Card.Suit.values()) {
                int suitMask = CardSet.suitMask(suit);
                if (!suit.equals(trump) && (out & suitMask) == 0) {
                    alone |= candidates & suitMask;
                }
            }
            if (alone != 0) {
                return alone;
            }
            for (Card.Suit suit : Card.Suit.values()) {
                if (suit.equals(trump)) {
                    continue;
                }
                int suitMask = CardSet.suitMask(suit);
                int ace = Integer.highestOneBit(suitMask);
                int king = ace >>> 1;
                if ((hand & ace) != 0 && CardSet.size(hand & suitMask) == 3 && (out & king) != 0) {
                    res &= ~asAce(hand, out, suitMask);
                }
            }
            // the candidates keep the lowest of equivalent cards: ♦J stands for ♦JA with ♦QK gone,
            // it is the ace, not a small card under it
            int under = 0;
            for (Card.Suit suit : Card.Suit.values()) {
                int suitMask = CardSet.suitMask(suit);
                int ace = Integer.highestOneBit(suitMask);
                if (!suit.equals(trump) && (hand & ace) != 0) {
                    under |= suitMask & ~asAce(hand, out, suitMask);
                }
            }
            if ((res & ~under) != 0) {
                res &= ~under;
            }
            int trumpMask = trump == null ? 0 : CardSet.suitMask(trump);
            int other = res & ~(declarerLeads & ~trumpMask);
            if (other != 0) {
                res = other;
            }
        } else {
            int trumpMask = trump == null ? 0 : CardSet.suitMask(trump);
            if ((hand & CardSet.suitMask(trick.getStartingSuit())) == 0 && (candidates & trumpMask) == 0) {
                res = discard(res, declarer, trumpMask);
            }
            // following the declarer's lead under his card no one can beat: the lowest one, a higher
            // card is a trick later or a guard; ♠9Q under ♠A after ♠K against 7♠, ♠Q is the top trump
            // left, it gave the declarer his 8th trick. The partner's lead may need an unblock, not here
            int suitMask = CardSet.suitMask(trick.getStartingSuit());
            int follow = res & suitMask;
            if (follow != 0 && trick.getStartedBy() == declarer && trick.getTop() == declarer &&
                    trick.topCard.getSuit().equals(trick.getStartingSuit())) {
                int topBit = CardSet.bit(trick.topCard);
                int above = suitMask & ~((topBit << 1) - 1);
                if (((hand | out) & above) == 0) {
                    res = Integer.lowestOneBit(follow);
                }
            }
            int partner = (declarer + 1) % NOP == number ? (declarer + 2) % NOP : (declarer + 1) % NOP;
            if (trick.getTop() == partner) {
                int topBit = CardSet.bit(trick.topCard);
                int higher = CardSet.suitMask(trick.topCard.getSuit()) & ~((topBit << 1) - 1);
                boolean declarerPlayed = (declarer - trick.getStartedBy() + NOP) % NOP < trick.size();
                int declarerCards = (number + 1) % NOP == declarer ? leftHand.getBitmap() : rightHand.getBitmap();
                if (declarerPlayed || (declarerCards & higher) == 0) {
                    res &= ~higher;
                }
            }
        }
        return res == 0 ? candidates : res;
    }

    // a discard: 3 a suit the declarer cannot hold, nothing to guard there, ♣J of ♣JQK ♦Q with
    // the declarer out of clubs and ♦XJ left; 2 keeps the suit's top honor guarded, J by 3 cards,
    // Q by 2, K by 1, or the suit has none; 1 leaves it short of its guards or throws it;
    // 0 a suit with touching honors, a trick source to keep, ♣8JQK; a trump is not a discard, 2
    int discardRank(int bit, int trumpMask) {
        final int JACK = 1 << 4;
        if ((bit & trumpMask) != 0) {
            return 2;
        }
        Card.Suit suit = Card.get(bit).getSuit();
        int declarer = gameManager().declarerNumber;
        int declarerCards = (number + 1) % NOP == declarer ? leftHand.getBitmap() : rightHand.getBitmap();
        if ((declarerCards & CardSet.suitMask(suit)) == 0) {
            return 3;
        }
        int offset = suit.getValue() * CardSet.SUIT_LIST_LENGTH;
        int cards = (myHand.getBitmap() >>> offset) & CardSet.SUIT_MASK;
        if (denseLeads(myHand.getBitmap() & CardSet.suitMask(suit)) != 0) {
            return 0;
        }
        int top = Integer.highestOneBit(cards);
        if (top < JACK || top == JACK << 3) {
            return 2;   // no honor to guard, or the A needs none
        }
        int b = bit >>> offset;
        if (b == top) {
            return 1;
        }
        int need = top == JACK << 2 ? 1 : top == JACK << 1 ? 2 : 3;
        return CardSet.size(cards & ~top & ~b) >= need ? 2 : 1;
    }

    // no card someone else may hold is above it in its suit
    static boolean isTop(int bit, int out) {
        int suitMask = CardSet.suitMask(Card.get(bit).getSuit());
        return (out & suitMask & ~((bit << 1) - 1)) == 0;
    }

    // my cards of the suit equivalent to my ace: down from it, past the played ones,
    // up to a card someone else may hold
    static int asAce(int hand, int out, int suitMask) {
        int ace = Integer.highestOneBit(suitMask);
        int res = 0;
        for (int bit = ace; (bit & suitMask) != 0 && (bit & out) == 0; bit >>>= 1) {
            res |= bit & hand;
        }
        return res;
    }

    // a discard from Ax is the x: the ace stands alone, longer suits keep their guards against
    // the declarer's length, ♣8XA ♥JA against 6♦ with ♣9QK: ♣8 set up his ♣9, ♥J was idle;
    int discard(int candidates, int declarer, int trumpMask) {
        int hand = myHand.getBitmap();
        int res = 0;
        for (Card.Suit suit : Card.Suit.values()) {
            int suitMask = CardSet.suitMask(suit);
            int cards = hand & suitMask;
            int ace = Integer.highestOneBit(suitMask);
            if ((suitMask & trumpMask) == 0 && (cards & ace) != 0 && CardSet.size(cards) == 2) {
                res |= candidates & cards & ~ace;
            }
        }
        if (res != 0) {
            return res;
        }
        // then a suit the declarer showed out of: while he surely has a trump, one partner cannot hold,
        // or with a single card there and partner holding the rest of the suit, ♠A after his ruff
        // with ♠K out against 6♦; not ♥Q of ♥9Q with ♥8 out, a winner once his trumps are gone, 6♣.
        // The last card of it stays to the search: led, it forces his trump; ♣J ♦9XJA ♥K on ♠J
        // against 6♠, ♠Q his last trump, ♣J let him make 7, ♦9 kept him to 6
        boolean leftDeclarer = (number + 1) % NOP == declarer;
        int declarerCards = leftDeclarer ? leftHand.getBitmap() : rightHand.getBitmap();
        int partnerCards = leftDeclarer ? rightHand.getBitmap() : leftHand.getBitmap();
        boolean sureTrump = (declarerCards & trumpMask & ~partnerCards) != 0;
        for (Card.Suit suit : Card.Suit.values()) {
            int suitMask = CardSet.suitMask(suit);
            if ((suitMask & trumpMask) != 0 || (declarerCards & suitMask) != 0) {
                continue;
            }
            int cards = hand & suitMask;
            if (sureTrump ? CardSet.size(cards) >= 2 : CardSet.size(cards) == 1 && (partnerCards & suitMask) != 0) {
                res |= candidates & suitMask;
            }
        }
        return res == 0 ? candidates : res;
    }

    // the suit mask of the longest suit of the dealt hand, not in trumps, of LONG_SUIT cards or more;
    // 0 on a tie
    static int longSuit(int hand, Card.Suit trump) {
        int res = 0;
        int longest = LONG_SUIT - 1;
        for (Card.Suit suit : Card.Suit.values()) {
            if (suit.equals(trump)) {
                continue;
            }
            int n = CardSet.size(hand & CardSet.suitMask(suit));
            if (n > longest) {
                longest = n;
                res = CardSet.suitMask(suit);
            } else if (n == longest) {
                res = 0;
            }
        }
        return res;
    }

    // the long suit's lead: not the smallest card, the next one, or the top of the cards equivalent
    // to it, none the others can hold between them; ♥79XQ with ♥8 out leads ♥X, ♥9 is the same
    // and ♥7 stays to duck
    // the passer's lead outside his long suit: the A on top of a suit, else the suits without K and Q
    // the suits whose top card is Q or higher with the next one below it: the lowest card of
    // that top run, ♦Q of ♦8QK, the one the candidates keep; topOfSequence() leads its top
    static int denseLeads(int hand) {
        final int QUEEN = 1 << 5;
        int res = 0;
        for (Card.Suit suit : Card.Suit.values()) {
            int offset = suit.getValue() * CardSet.SUIT_LIST_LENGTH;
            int cards = (hand >>> offset) & CardSet.SUIT_MASK;
            int top = Integer.highestOneBit(cards);
            if (top < QUEEN || (cards & top >>> 1) == 0) {
                continue;
            }
            while ((cards & top >>> 1) != 0) {
                top >>>= 1;
            }
            res |= top << offset;
        }
        return res;
    }

    static int safeOtherLeads(int hand) {
        final int ACE = 1 << 7, KING = 1 << 6, QUEEN = 1 << 5;
        int aces = 0;
        int plain = 0;
        for (Card.Suit suit : Card.Suit.values()) {
            int offset = suit.getValue() * CardSet.SUIT_LIST_LENGTH;
            int cards = (hand >>> offset) & CardSet.SUIT_MASK;
            if ((cards & ACE) != 0) {
                aces |= ACE << offset;
            } else if (cards != 0 && (cards & (KING | QUEEN)) == 0) {
                plain |= cards << offset;
            }
        }
        return aces != 0 ? aces : plain;
    }

    private int longSuitLead(int keep) {
        if (CardSet.size(keep) < 2) {
            return keep;
        }
        int out = leftHand.getBitmap() | rightHand.getBitmap();
        int card = Integer.lowestOneBit(keep & ~Integer.lowestOneBit(keep));
        for (int next = card << 1; (next & longSuit) != 0 && (next & out) == 0; next <<= 1) {
            if ((next & keep) != 0) {
                card = next;
            }
        }
        return card;
    }

    // the cards each of the others can hold, their hand sizes into sizes
    private int[] possibleHands(int[] sizes) {
        GameManager gameManager = gameManager();
        Player[] players = gameManager.getPlayers();
        int declarer = gameManager.declarerNumber;
        int left = (number + 1) % NOP;
        int right = (number + 2) % NOP;
        int[] possible = new int[NOP];
        possible[left] = leftHand.getBitmap();
        possible[right] = rightHand.getBitmap();
        if (number != declarer) {
            // the talon went to the declarer, he kept it or dropped it
            int partner = left == declarer ? right : left;
            possible[partner] &= ~gameManager.talonShown.getBitmap();
        }
        sizes[left] = players[left].totalCards();
        sizes[right] = players[right].totalCards();
        return possible;
    }

    // a defender: the declarer's hand worth his contract, his cards played and the 2 dropped
    // ones with it, as Bidder estimates it
    static int[] dealForDefender(int[] possible, int[] sizes, int declarer, int goal, Card.Suit trump) {
        GameManager gameManager = GameManager.getInstance();
        int[] best = null;
        double bestEstimate = Double.NEGATIVE_INFINITY;
        for (int t = 0; t < CONTRACT_TRIES; ++t) {
            int[] h = dealStanding(possible, sizes);
            if (h == null) {
                return best;
            }
            bidderDrop(h, declarer, possible[declarer], trump);
            CardSet dealtHand = new CardSet(h[declarer]);
            dealtHand.add(gameManager.declarerPlayed);
            CardSet dealt = new CardSet(dealtHand);
            dealt.add(new CardSet(h[NOP]));     // the dropped ones
            double estimate = Bidder.getInstance().estimateTricks(dealtHand, dealt, trump);
            if (estimate >= goal + CONTRACT_MARGIN) {
                return h;
            }
            if (estimate > bestEstimate) {
                bestEstimate = estimate;
                best = h;
            }
        }
        return best;
    }

    // the declarer's 12 cards of a deal keep Bidder's drop for his trump, not a random one: the drop
    // makes voids and keeps the talon's ♣X under ♣Q, ♠JA ♣8XA ♦7X ♥789JA dropped ♦7X against 7♥;
    // unchanged when Bidder has none or it puts a card the declarer cannot hold in his hand
    static void bidderDrop(int[] h, int declarer, int possibleDeclarer, Card.Suit trump) {
        GameManager gameManager = GameManager.getInstance();
        int unplayed = h[declarer] | h[NOP];
        int dealt = unplayed | gameManager.declarerPlayed.getBitmap();
        int elderHand = (declarer - gameManager.elderHand + NOP) % NOP;
        CardSet drops = Bidder.getInstance().trumpDrops(new CardSet(dealt), trump, elderHand, unplayed);
        if (drops == null) {
            return;
        }
        int dropped = drops.getBitmap();
        if ((unplayed & ~dropped & ~possibleDeclarer) != 0) {
            return;
        }
        h[NOP] = dropped;
        h[declarer] = unplayed & ~dropped;
    }

    // every deal dealStanding() can make, in random order, cut short by a deadline they stay a uniform
    // sample; null when there are more than limit of them
    static List<int[]> allDealsStanding(int[] possible, int[] sizes, int limit) {
        int pool = 0;
        for (int i = 0; i < NOP; ++i) {
            pool |= possible[i];
        }
        int[] poolCards = new int[Integer.bitCount(pool)];
        int m = 0;
        int poolBit = 0;
        while ((poolBit = CardSet.next(pool, poolBit)) != 0) {
            poolCards[m++] = poolBit;
        }
        int dropped = m;
        for (int i = 0; i < NOP; ++i) {
            dropped -= sizes[i];
        }
        if (dropped < 0) {
            return null;
        }
        int[] room = Arrays.copyOf(sizes, NOP + 1);
        room[NOP] = dropped;
        int[] possibleAll = Arrays.copyOf(possible, NOP + 1);
        possibleAll[NOP] = pool;
        List<int[]> res = new ArrayList<>();
        int[] nodes = {limit * 64};
        if (!allDeals(poolCards, 0, possibleAll, room, new int[NOP + 1], res, limit, nodes)) {
            return null;
        }
        if (res.isEmpty()) {
            return null;
        }
        Collections.shuffle(res, standingRandom);
        return res;
    }

    // false when past the limit or the search is too long
    private static boolean allDeals(int[] poolCards, int i, int[] possible, int[] room, int[] h,
            List<int[]> res, int limit, int[] nodes) {
        if (--nodes[0] < 0) {
            return false;
        }
        if (i == poolCards.length) {
            if (res.size() >= limit) {
                return false;
            }
            res.add(h.clone());
            return true;
        }
        int bit = poolCards[i];
        for (int to = 0; to <= NOP; ++to) {
            if (room[to] == 0 || (possible[to] & bit) == 0) {
                continue;
            }
            --room[to];
            h[to] |= bit;
            boolean ok = allDeals(poolCards, i + 1, possible, room, h, res, limit, nodes);
            h[to] &= ~bit;
            ++room[to];
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    // a defender: the deals with the declarer's hand worth his contract as dealForDefender() wants it,
    // otherwise the ones with the best estimate
    static List<int[]> worthContract(List<int[]> all, int declarer, int possibleDeclarer, int goal,
            Card.Suit trump) {
        GameManager gameManager = GameManager.getInstance();
        // the deals with Bidder's drop, see bidderDrop(); all of them when none has it
        List<int[]> dropped = new ArrayList<>();
        for (int[] h : all) {
            int[] copy = h.clone();
            bidderDrop(copy, declarer, possibleDeclarer, trump);
            if (copy[NOP] == h[NOP]) {
                dropped.add(h);
            }
        }
        if (!dropped.isEmpty()) {
            all = dropped;
        }
        List<int[]> res = new ArrayList<>();
        double bestEstimate = Double.NEGATIVE_INFINITY;
        for (int[] h : all) {
            CardSet dealtHand = new CardSet(h[declarer]);
            dealtHand.add(gameManager.declarerPlayed);
            CardSet dealt = new CardSet(dealtHand);
            dealt.add(new CardSet(h[NOP]));
            double estimate = Math.min(goal, Bidder.getInstance().estimateTricks(dealtHand, dealt, trump));
            if (estimate > bestEstimate) {
                bestEstimate = estimate;
                res.clear();
            }
            if (estimate == bestEstimate) {
                res.add(h);
            }
        }
        return res;
    }

    // a defender deciding to whist, up to MAX_DEALS deals: the declarer's 12 cards declare his contract
    // as Bidder declares them for a bot, and the drop Bidder chooses; otherwise the closest ones.
    // Worth the contract was not enough: 6♠ is the lowest bid, 6♣ or 7♥ hands passed for it,
    // ♠KA ♥78QKA as 6♠, and the defenders saw it set easily. The tries are for all the deals at once,
    // up to WHIST_TRIES, MIN_WHIST_TRIES and MIN_DEALS of the contract at least unless past the hard deadline:
    // 4 hands of 20000 bid 7-, the closest ones are 6-
    static List<int[]> whistHands(int[] possible, int[] sizes, int declarer, Bid minBid, int elderHand,
            long deadLine, long hardDeadLine) {
        List<int[]> hands = new ArrayList<>();
        List<Integer> distances = new ArrayList<>();
        int exact = 0;
        for (int t = 0; t < WHIST_TRIES && exact < MAX_DEALS; ++t) {
            long now = System.currentTimeMillis();
            if (t >= MIN_WHIST_TRIES && exact >= MIN_DEALS && now >= deadLine ||
                    t >= MIN_DEALS && now >= hardDeadLine) {
                break;
            }
            int[] h = dealStanding(possible, sizes);
            if (h == null) {
                break;      // no deal agrees with what the bot knows, should not happen
            }
            CardSet hand = new CardSet(h[declarer] | h[NOP]);
            PlayerBid playerBid = Bidder.getInstance().getBid(hand, Bid.BID_6S, elderHand, 2);
            if (playerBid.drops.size() == 2) {
                h[NOP] = playerBid.drops.getBitmap();
                h[declarer] = hand.getBitmap() & ~h[NOP];
            }
            int distance = bidDistance(playerBid, minBid);
            if (distance == 0) {
                ++exact;
            }
            // a weaker hand in the contract's trump: its tricks short of the contract, see whistDeals()
            Bid bid = playerBid.toBid();
            h = Arrays.copyOf(h, NOP + 2);
            if (!bid.equals(Bid.BID_PASS) && bid.getTrump() == minBid.getTrump() && bid.goal() < minBid.goal()) {
                h[NOP + 1] = minBid.goal() - bid.goal();
            }
            hands.add(h);
            distances.add(distance);
        }
        // the closest first, in the order dealt
        Integer[] order = new Integer[hands.size()];
        for (int i = 0; i < order.length; ++i) {
            order[i] = i;
        }
        Arrays.sort(order, (i0, i1) -> Integer.compare(distances.get(i0), distances.get(i1)));
        List<int[]> res = new ArrayList<>();
        for (int i = 0; i < order.length && res.size() < MAX_DEALS; ++i) {
            if (exact >= MIN_DEALS && distances.get(order[i]) > 0) {
                break;
            }
            res.add(hands.get(order[i]));
        }
        return res;
    }

    // how far Bidder's bid is from the contract: 0 the same, then the tricks apart in the same trump,
    // a weaker hand after a stronger one the same tricks apart, another trump after them all, a pass
    // the farthest. A declarer may declare less than his hand is worth, weaker hands set the contract
    // too often: 8♠ hands filled up 32 of 40 deals against 9♠, ♠89 ♣79XJ ♦J ♥8XA whisted alone
    static int bidDistance(PlayerBid playerBid, Bid contract) {
        Bid bid = playerBid.toBid();
        if (bid.equals(Bid.BID_PASS)) {
            return Integer.MAX_VALUE - 1;
        }
        int diff = bid.goal() - contract.goal();
        int distance = diff >= 0 ? 2 * diff : -2 * diff + 1;
        if (bid.getTrump() != contract.getTrump()) {
            distance += 2 * ROUND_SIZE;
        }
        return distance;
    }

    // hands of the other two by their sizes from the cards each can hold, the rest in h[NOP],
    // the declarer's drop; the cards fewer players can hold go first, a card goes to a player
    // by his room left, about uniform
    static int[] dealStanding(int[] possible, int[] sizes) {
        int pool = 0;
        for (int i = 0; i < NOP; ++i) {
            pool |= possible[i];
        }
        int[] poolCards = new int[Integer.bitCount(pool)];
        int m = 0;
        int poolBit = 0;
        while ((poolBit = CardSet.next(pool, poolBit)) != 0) {
            poolCards[m++] = poolBit;
        }
        int dropped = m;
        for (int i = 0; i < NOP; ++i) {
            dropped -= sizes[i];
        }
        if (dropped < 0) {
            return null;
        }
        for (int t = 0; t < DEAL_TRIES; ++t) {
            // shuffle, then the cards with fewer owners first
            for (int i = m - 1; i > 0; --i) {
                int j = standingRandom.nextInt(i + 1);
                int tmp = poolCards[i];
                poolCards[i] = poolCards[j];
                poolCards[j] = tmp;
            }
            int[] order = new int[m];
            int o = 0;
            for (int count = 1; count <= NOP; ++count) {     // the other two and the drop
                for (int bit : poolCards) {
                    int owners = 0;
                    for (int i = 0; i < NOP; ++i) {
                        if (sizes[i] > 0 && (possible[i] & bit) != 0) {
                            ++owners;
                        }
                    }
                    if (dropped > 0) {
                        ++owners;
                    }
                    if (owners == count) {
                        order[o++] = bit;
                    }
                }
            }
            int[] h = new int[NOP + 1];
            int[] room = sizes.clone();
            int dropRoom = dropped;
            boolean ok = true;
            for (int bit : order) {
                int totalRoom = 0;
                for (int i = 0; i < NOP; ++i) {
                    if ((possible[i] & bit) != 0) {
                        totalRoom += room[i];
                    }
                }
                totalRoom += dropRoom;
                if (totalRoom == 0) {
                    ok = false;
                    break;
                }
                int r = standingRandom.nextInt(totalRoom);
                int to = NOP;
                for (int i = 0; i < NOP; ++i) {
                    if ((possible[i] & bit) != 0) {
                        if (r < room[i]) {
                            to = i;
                            break;
                        }
                        r -= room[i];
                    }
                }
                h[to] |= bit;
                if (to == NOP) {
                    --dropRoom;
                } else {
                    --room[to];
                }
            }
            if (ok) {
                return h;
            }
        }
        return null;
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
    int dropRisk(CardSet dealtHand, CardSet dropped) {
        return 0;
    }

    // a drop the declarer would choose from, see dropGuesses()
    boolean likelyDrop(int risk, int minRisk) {
        return false;
    }

    // the declarer can drop the card, see dropGuesses()
    boolean droppable(Card card) {
        return true;
    }

    // the declarer's hand now agrees with how he played, see dropGuesses()
    boolean plausibleDrop(CardSet hand) {
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
        // not the drops he would have misplayed with, unless all are such, checked when chosen, it takes time
        List<CardSet> implausible = new ArrayList<>();
        if (knownHand.contains(myHand) && myHand.size() == declarerSize) {
            // the list's guess is the drop of a bot declarer, a human one may have dropped otherwise
            if (gameManager.getDeclarer() instanceof Bot || plausibleDrop(myHand)) {
                res.add(new CardSet(myHand));
            } else {
                implausible.add(new CardSet(myHand));
            }
        }
        // the dropped cards not shown yet, 1 or 2
        List<CardSet> hands = new ArrayList<>();
        List<Integer> evals = new ArrayList<>();
        int cards = knownHand.getBitmap();
        for (int bit0 = CardSet.next(cards, 0); bit0 != 0; bit0 = CardSet.next(cards, bit0)) {
            if (!droppable(Card.get(bit0))) {
                continue;
            }
            // one card: bit1 == bit0 only, the pairs after it would leave the declarer a card short
            for (int bit1 = diff == 1 ? bit0 : CardSet.next(cards, bit0); bit1 != 0;
                    bit1 = diff == 1 ? 0 : CardSet.next(cards, bit1)) {
                if (!droppable(Card.get(bit1))) {
                    continue;
                }
                CardSet hand = new CardSet(cards & ~(bit0 | bit1));
                // evaluate the hand as it was after the drop
                CardSet dealtHand = new CardSet(hand);
                dealtHand.add(gameManager.declarerPlayed);
                int eval = dropRisk(dealtHand, new CardSet(bit0 | bit1));
                int k = evals.size();
                while (k > 0 && evals.get(k - 1) > eval) {
                    --k;
                }
                evals.add(k, eval);
                hands.add(k, hand);
            }
        }
        // first the likely drops: ♠79QA ♣79X ♦J ♥79JQ, ♦J ♠A dropped was not among the various ones,
        // ♦7 let him discard ♠Q
        for (int i = 0; i < hands.size() && res.size() < MIN_DROP_GUESSES; ++i) {
            if (!likelyDrop(evals.get(i), evals.get(0))) {
                break;
            }
            CardSet hand = hands.get(i);
            if (res.contains(hand) || implausible.contains(hand)) {
                continue;
            }
            if (!plausibleDrop(hand)) {
                implausible.add(hand);
                continue;
            }
            res.add(hand);
        }
        // then not the likely drops, the various ones, even illogical: every card the declarer can hold
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
                if (res.contains(hand) || implausible.contains(hand)) {
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
            if (!plausibleDrop(next)) {
                implausible.add(next);
                continue;
            }
            res.add(next);
            notDropped.remove(knownHand.diff(next));
            notKept.remove(next);
        }
        if (!implausible.isEmpty()) {
            printf("the declarer's plays rule out %d drops, kept %s\n", implausible.size(), res);
        }
        if (res.size() < 2) {
            // he misplayed under every guess, so any of them
            for (CardSet hand : implausible) {
                if (res.size() >= MIN_DROP_GUESSES) {
                    break;
                }
                res.add(hand);
            }
        }
        return res;
    }

    // legal cards of the defender to move, one of the cards with no other card between them
    int candidates(Trick trick) {
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
        int res = 0;
        Card prev = null;
        for (Card card : legal) {
            if (prev != null && prev.getSuit().equals(card.getSuit()) && !anyBetween(others, prev, card)) {
                continue;
            }
            res |= CardSet.bit(card);
            prev = card;
        }
        return res;
    }

    // the candidates equivalent to card under the guess, the declarer's hand
    int sameRun(Trick trick, int candidates, Card card, CardSet guess) {
        CardSet hand = gameManager().getPlayers()[trick.getTurn()].myHand;
        CardSet others = CardSet.union(guess, leftHand, rightHand, trick.cards2CardSet());
        others.remove(hand);
        int res = 0;
        int bit = 0;
        while ((bit = CardSet.next(candidates, bit)) != 0) {
            Card candidate = Card.get(bit);
            if (candidate.getSuit().equals(card.getSuit()) && !anyBetween(others, candidate, card)) {
                res |= bit;
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
        for (Card other : others.list(card0.getSuit())) {
            if (other.compareInTrick(card0) > 0 && other.compareInTrick(card1) < 0) {
                return true;
            }
        }
        return false;
    }

}