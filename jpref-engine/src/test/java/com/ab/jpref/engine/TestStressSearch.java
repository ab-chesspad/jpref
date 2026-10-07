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
 * Created: 10/4/2026 by claude.ai
 *
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config.Bid;
import com.ab.util.Bidder;
import com.ab.util.Logger;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;

/*
 * Generator, not a test: searches for deals whose first-trick TrickList build is the most expensive,
 * candidates for TestMaxPoolCapacity.
 * 1. random deals, each with 6NT, 6 in the declarer's longest suit and misère, every leader;
 * 2. hill climbing from the heaviest ones: swap a card between two hands, keep it if the build grows.
 * Declarer is hand 0, the talon is the drop. Prints RANDOM-TOP, CLIMB and CLIMB-TOP lines.
 *
 * mvn test -Dtest=TestStressSearch -Ddeals=200 -Dclimb=200 -Dstarts=8 [-DnoMisere=true] [-Dseed=7]
 *   (remove @Ignore first; with misère a climbing step can take seconds, so this runs for hours)
 *   deals      random deals for step 1, default 150
 *   climb      hill-climbing steps per start, default 150
 *   starts     how many of the heaviest (contract, leader) combinations to climb from, default 6
 *   noMisere   skip misère
 *   seed       random seed, default 7
 * Large builds need a big heap, e.g. MAVEN_OPTS=-Xmx6g
 */
@Ignore("generator, runs for hours")
public class TestStressSearch extends BaseTest {
    static final Bid[] SUIT_BIDS = {Bid.BID_6S, Bid.BID_6C, Bid.BID_6D, Bid.BID_6H};

    static class Probe {
        CardList deck;      // 30 cards (3 hands) + 2 talon
        Bid bid;
        int leader;
        long positions, ms, pool;

        String describe() {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 3; ++j) {
                sb.append(new CardSet(deck.subList(j * 10, j * 10 + 10))).append("  ");
            }
            sb.append(new CardSet(deck.subList(30, 32)));
            return String.format("CAND positions %,d ms %,d pool %,d | %s leader %d | %s",
                positions, ms, pool, bid, leader, sb);
        }
    }

    GameManager gameManager;

    void measure(Probe p) {
        new TrickList();
        TrickList.DEBUG_IGNORE_DEADLINE = true;
        TrickList.maxPositions = 0;
        TrickList.maxListBuildTime = 0;
        TrickList.maxPoolCount = 0;
        CardSet[] hands = new CardSet[3];
        for (int j = 0; j < 3; ++j) {
            hands[j] = new CardSet(p.deck.subList(j * 10, j * 10 + 10));
        }
        Bot bot = Bid.BID_MISERE.equals(p.bid) ? new MisereBot(hands) : new ForTricksBot(hands);
        Trick trick = new Trick();
        trick.clear(p.leader);
        gameManager.setMinBid(p.bid);
        gameManager.declarerNumber = 0;
        gameManager.declarerHand = new CardSet(hands[0]);
        gameManager.initialDeclarerHand = new CardSet(hands[0]);
        trick.minBid = p.bid;
        trick.trumpSuit = p.bid.getTrump();
        trick.setNumber(0);
        Bot.playerBid = new Bidder.PlayerBid();
        Bot.targetBot = null;
        Bot.debugDrop = null;
        TrickList.getInstance().getCard(bot, trick);
        p.positions = TrickList.maxPositions;
        p.ms = TrickList.maxListBuildTime;
        p.pool = TrickList.maxPoolCount;
    }

    Bid longestSuitBid(CardList deck) {
        CardSet hand = new CardSet(deck.subList(0, 10));
        Bid best = SUIT_BIDS[0];
        int len = -1;
        for (Bid b : SUIT_BIDS) {
            int l = hand.list(b.getTrump()).size();
            if (l > len) {
                len = l;
                best = b;
            }
        }
        return best;
    }

    @Test
    public void search() {
        gameManager = new GameManager();
        gameManager.init(host);
        GameManager.DEBUG_LOG = false;
        int randomDeals = Integer.getInteger("deals", 150);
        int climbSteps = Integer.getInteger("climb", 150);
        int climbStarts = Integer.getInteger("starts", 6);
        Random rnd = new Random(Long.getLong("seed", 7));

        List<Probe> all = new ArrayList<>();
        for (int i = 0; i < randomDeals; ++i) {
            CardList deck = CardList.getDeck();
            Collections.shuffle(deck, rnd);
            Bid[] bids = Boolean.getBoolean("noMisere") ? new Bid[]{Bid.BID_6N, longestSuitBid(deck)}
                : new Bid[]{Bid.BID_6N, longestSuitBid(deck), Bid.BID_MISERE};
            for (Bid bid : bids) {
                for (int leader = 0; leader < 3; ++leader) {
                    Probe p = new Probe();
                    p.deck = deck;
                    p.bid = bid;
                    p.leader = leader;
                    measure(p);
                    all.add(p);
                }
            }
        }
        all.sort((a, b) -> Long.compare(b.positions, a.positions));
        Logger.printf("RANDOM-TOP\n");
        for (Probe p : all.subList(0, Math.min(10, all.size()))) {
            Logger.printf("%s\n", p.describe());
        }

        // hill-climb: swap a card between two of the 3 hands, keep it if the build gets heavier
        List<Probe> climbed = new ArrayList<>();
        Set<String> started = new HashSet<>();
        for (Probe start : all) {
            if (climbed.size() >= climbStarts) {
                break;
            }
            String key = start.bid + "/" + start.leader;
            if (!started.add(key)) {
                continue;   // one start per contract kind and leader, for variety
            }
            Probe cur = start;
            for (int s = 0; s < climbSteps; ++s) {
                CardList deck = new CardList(cur.deck);
                int h0 = rnd.nextInt(3), h1 = (h0 + 1 + rnd.nextInt(2)) % 3;
                int i0 = h0 * 10 + rnd.nextInt(10), i1 = h1 * 10 + rnd.nextInt(10);
                Card c = deck.get(i0);
                deck.set(i0, deck.get(i1));
                deck.set(i1, c);
                Probe p = new Probe();
                p.deck = deck;
                p.bid = cur.bid;
                p.leader = cur.leader;
                measure(p);
                if (p.positions > cur.positions) {
                    cur = p;
                }
            }
            climbed.add(cur);
            Logger.printf("CLIMB from %,d to %s\n", start.positions, cur.describe());
        }
        climbed.sort((a, b) -> Long.compare(b.positions, a.positions));
        Logger.printf("CLIMB-TOP\n");
        for (Probe p : climbed) {
            Logger.printf("%s\n", p.describe());
        }
    }
}
