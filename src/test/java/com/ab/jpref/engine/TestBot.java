/*
     Copyright (C) 2025-2026	Alexander Bootman, alexbootman@gmail.com
 *
 * Created by Alexander Bootman on 12/22/2024.
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.util.BidData;
import com.ab.util.Logger;
import com.ab.util.Util;
import org.junit.*;

import java.io.IOException;

import static com.ab.util.Logger.println;
import static com.ab.util.Util.currMethodName;

public class TestBot {
    public static final int NOP = Config.NOP;
    static final Config config = Config.getInstance();
    static final Util util = Util.getInstance();
    static GameManager gameManager;

    @BeforeClass
    public static void initClass() {
        gameManager = new GameManager(config, null, playerFactory());
        GameManager.DEBUG_LOG = false;  // suppress thread status logginga
    }

    @Before
    public void initTest() {
        Bot.targetBot = null;
        Bot.trickList = null;

    }

    private static GameManager.PlayerFactory playerFactory() {
        return new GameManager.PlayerFactory() {
            @Override
            public Player[] getPlayers() {
                Player[] players = new Player[NOP];
                for (int i = 0; i < NOP; ++i) {
                    players[i] = new Bot(i);
                }
                return players;
            }

            @Override
            public Player[] avatars4Round() {
                Player[] players = gameManager.getPlayers();
                return players;
            }
        };
    }

    @Test
    public void testGetMaxBid() throws IOException {
        println("running: " + currMethodName());
        GameManager.DEBUG_LOG = false;  // suppress thread status logging
        util.getList("etc/tests/get-max-bid",
            (res, tokens) -> {
                CardSet cards = new CardSet();
                int i;
                for (i = 0; cards.size() < 10; ++i) {
                    cards.add(util.toCardList(tokens.get(i)));
                }
                int turn = Integer.parseInt(tokens.get(i++));
                Config.Bid minBid = Config.Bid.fromName(tokens.get(i++));
                String[] parts = res.split(" ");
                Config.Bid expectedBid = Config.Bid.fromName(parts[0]);
                Config.Bid bid = Config.Bid.BID_PASS;
                if (expectedBid.equals(Config.Bid.BID_MISERE)) {
                    MisereBot player = new MisereBot(cards);
                    if (player.evalMisere(turn)) {
                        bid = Config.Bid.BID_MISERE;
                    }
                } else {
                    ForTricksBot player = new ForTricksBot(cards);
                    BidData.PlayerBid playerBid = player.getMaxPlayerBid(minBid, turn);
                    bid = playerBid.toBid();
                }
                Assert.assertEquals(expectedBid, bid);
            });
    }

    @Test
    public void testDeclareGame() throws IOException {
        println("running: " + currMethodName());
        util.getList("etc/tests/declare-game",
            (res, tokens) -> {
                String[] parts = res.split(", ");
                Card drop0 = Card.fromName(parts[0]);
                Card drop1 = Card.fromName(parts[1]);
                Config.Bid expectedBid = Config.Bid.fromName(parts[2]);

                int _elderHand = 0;
                CardList hand = new CardList();
                Config.Bid minBid = Config.Bid.BID_PASS;
                for (String token : tokens) {
                    try {
                        _elderHand = Integer.parseInt(token);
                        continue;
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                    Config.Bid bid = Config.Bid.fromName(token);
                    if (bid != null) {
                        minBid = bid;
                        continue;
                    }
                    hand.addAll(util.toCardList(token));
                }
                Assert.assertEquals(12, hand.size());
                Bot.playerBid = null;
                Bot bot = new Bot(0);
                bot.clear();
                CardSet cardSet = new CardSet(hand);
                bot.myHand = cardSet.clone();
                bot.bid = minBid;
                bot.declareRound(minBid, _elderHand);
                Config.Bid bid = bot.getBid();
                Assert.assertEquals(expectedBid, bid);
                if (!bid.equals(Config.Bid.BID_PASS)) {
                    CardSet drops = cardSet.diff(bot.myHand);
                    Assert.assertTrue(String.format("%s not found", drop0.toColorString()), drops.contains(drop0));
                    Assert.assertTrue(String.format("%s not found", drop1.toColorString()), drops.contains(drop1));
                }
            });
    }

    @Test
    @Ignore("test MisereBot later")
    public void testMove() throws IOException {
        println("running: " + currMethodName());
        String[] sources = {
            // hands, bid, 1st move, 0th player tricks
            "♦79XQ ♥7Q  ♠7J ♣7 ♥9JK  ♠9XQA ♣A ♦J : Misère : ♥J ♦J -> ♥Q",
            "♦79XQ ♥7  ♠7J ♣7 ♥9K  ♠9XQA ♣A : Misère : . -> ♥7",
        };

        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split("\\s+(:|->)\\s+");
            CardList cards = util.toCardList(parts[0]);
            int size = cards.size() / NOP;
            CardSet[] hands = new CardSet[NOP];
            int i = NOP;
            int k = cards.size();
            hands[--i] = new CardSet(cards.subList(k - size, k));
            k -= size;
            hands[--i] = new CardSet(cards.subList(k - size, k));
            k -= size;
            hands[--i] = new CardSet(cards.subList(0, k));  // the rest
            Config.Bid bid = Config.Bid.fromName(parts[1]);
            gameManager.minBid = bid;

            Trick trick = new Trick();
            trick.minBid = bid;
            trick.trumpSuit = bid.getTrump();
            trick.setNumber(10 - size);
            if (parts[2] != ".") {
                CardList trickCards = util.toCardList(parts[2]);
                for (Card card : trickCards) {
                    for (int j = 0; j < NOP; ++j) {
                        if (hands[j].remove(card)) {
                            if (trick.isEmpty()) {
                                trick.setStartedBy(j);
                            }
                            trick.add(card);
                        }
                    }
                }
            }

            Bot targetBot;
            if (bid.equals(Config.Bid.BID_MISERE)) {
                targetBot = new MisereBot(hands);
            } else {
                targetBot = new ForTricksBot(hands);
            }
            targetBot.trick = trick;
            Card card = targetBot.play(trick);
            Card expected = Card.fromName(parts[3]);
            Assert.assertEquals("wrong card", expected, card);

/*
            int j = 0;
            Player p = gameManager.players[j];
            p.myHand = hands[j];
            p.leftHand = hands[(j + 1) % NOP];
            p.rightHand = hands[(j + 2) % NOP];

            gameManager.players[0].setBid(bid);
            gameManager.players[1].setBid(Config.Bid.BID_PASS);
            gameManager.players[2].setBid(Config.Bid.BID_WHIST);
            Card card = gameManager.players[trick.getTurn()].play(trick);

            Card expected = Card.fromName(parts[3]);
*/

/*
            Bot.playerBid = new BidData.PlayerBid();
            ForTricksBot forTricksBot = new ForTricksBot(hands);
            forTricksBot.trick = trick;
            forTricksBot.refineDrop(hands);

            int expectdTricks = Integer.parseInt(parts[3]);
            TrickList trickList = new TrickList(forTricksBot, trick, hands);
            int tricks = trickList.root.getFutureTricks();
            Assert.assertEquals("tricks", expectdTricks, tricks);
            Card card = trickList.getCard(trick, hands);
*/

            Logger.println("ok");

        }
    }
}