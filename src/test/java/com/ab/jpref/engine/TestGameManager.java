/*
     Copyright (C) 2025	Alexander Bootman, alexbootman@gmail.com
 *
 * Created by Alexander Bootman on 1/25/2025.
 */
package com.ab.jpref.engine;

import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Config;
import com.ab.util.Logger;
import com.ab.util.Util;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class TestGameManager {
    static final Config config = Config.getInstance();
    GameManager gameManager;

    @Before
    public void initClass() {
        Logger.set(System.out);
        gameManager = new GameManager(config, null, playerFactory());
        GameManager.DEBUG = false;  // suppress thread status logginga
        config.sleepBetweenRounds.set(0);
    }

    private GameManager.PlayerFactory playerFactory() {
        return index -> new Bot("" + index);
    }

    // ♣8 ♥789   ♦78X ♥Q   ♠K ♦K ♥XJ   2
/*
    @Test
    public void testBidding() throws IOException {
        final String testFileName = "etc/tests/fixedplay";
        Util.getList(testFileName, (res, tokens) -> {
            int turn = Integer.parseInt(tokens.get(tokens.size() - 1));     // 0-based
            CardList deck = new CardList();
            for (String token : tokens) {
                if (token.endsWith(":")) {
                    continue;
                }
                deck.addAll(Util.toCardList(token));
            }
//                Logger.println(deck.toString());
            gameManager.deal(deck);
            Player player = gameManager.bidding(turn);
            String[] parts = res.split(" ");
            if (parts[0].equals(Config.Bid.BID_ALL_PASS.getName())) {
                if (player != null) {
                    Assert.assertNull(String.format("expected all-pass, but got %s", player.getName()), player);
                }
                return;
            }
            if ("...".equals(parts[0])) {
                return;
            }
            int declarer = Integer.parseInt(parts[0]);
            Config.Bid bid = Config.Bid.fromName(parts[1]);
            Player p = gameManager.getPlayers()[declarer];
            Assert.assertSame(String.format("wrond bidding winner %s, expected %s", player.getName(), p.getName()), p, player);
            Assert.assertEquals(String.format("wrond winning bid %s, expected %s", player.getBid(), bid), player.getBid(), bid);
        });

    }
*/

    @Test
    public void testAllPass() throws IOException {
        final String testFileName = "etc/tests/fixedplay";
//        gameManager.runGame(testFile, 0);
        Util.getList(testFileName,
                (res, tokens) -> {
                    if (!tokens.get(0).startsWith(GameManager.DEAL_MARK)) {
                        return;     // ignore
                    }
                    int turn = Integer.parseInt(tokens.get(tokens.size() - 1));     // 0-based
                    CardList deck = new CardList();
                    for (String token : tokens) {
                        if (token.endsWith(":")) {
                            continue;
                        }
                        deck.addAll(Util.toCardList(token));
                    }
                    testAllPass(deck, turn, res);
                });

        System.out.print("done\n");
    }

    private void testAllPass(CardList deck, int turn, String res) {
        gameManager.deal(deck);
        gameManager.getTrick().clear(turn);
        gameManager.playRoundAllPass();
        System.out.printf("1: %d, 2: %d, 3: %d\n"
                , gameManager.players[0].tricks
                , gameManager.players[1].tricks
                , gameManager.players[2].tricks
        );

    }

    @Test
    public void testEtudes() throws IOException {
        // https://www.gambler.ru/forum/index.php?s=3966c74ecd08ea375730d1fc88fe7392&showtopic=503067&st=10
        final String[] sources = {
            "5",
            "1", "2", "3", "4",
        };

        final String[] charMap = {
            "[\\s\t\\,—-]->",   // -,
            "Север|Запад|Восток|Юг|Снос|N|W|E|S|\\.gif->",
            "Т->A",
            "К->K",
            "Д->Q",
            "В->J",
            "10->X",
            "s->♠",
            "c->♣",
            "d->♦",
            "h->♥",
            "([♠♣♦♥012])-> $1",
        };

        for (String source : sources) {
            String testFileName = "etc/tests/" + source;
//        gameManager.runGame(testFile, 0);
            final int[] elderHand = {-1};
            List<CardList> cardLists = new LinkedList<>();
            cardLists.add(new CardList());
            Util.getList(testFileName, charMap,
                (res, tokens) -> {
                    for (String token : tokens) {
                        if (token.length() == 1) {
                            try {
                                elderHand[0] = Integer.parseInt(token);
                            } catch (NumberFormatException e) {
                                // ignore
//                                Logger.println(e.getMessage());
                            }
                            continue;
                        }
                        int size = 10;
                        if (cardLists.size() == 4) {
                            size = 2;   // talon
                        }
                        CardList cardList = cardLists.get(cardLists.size() - 1);
                        if (cardList.size() >= size) {
                            cardList = new CardList();
                            cardLists.add(cardList);
                        }
                        cardList.addAll(Util.toCardList(token));
                    }

//                int elderHand = Integer.parseInt(tokens.get(tokens.size() - 1));     // 0-based

                });

            CardList deck = new CardList();
            // order: North, East, South, Talon
            deck.addAll(cardLists.get(2));
/*  todo: it's either 1,0 or 0,1 depending on who the dealer is
            deck.addAll(cardLists.get(1));
            deck.addAll(cardLists.get(0));
/*/
            deck.addAll(cardLists.get(0));
            deck.addAll(cardLists.get(1));
//*/
            deck.addAll(cardLists.get(3));
            Logger.printf("%s, %d\n", deck.toString(), elderHand[0]);
            gameManager.deal(deck);
            gameManager.declarer = null;
            gameManager.declarer = gameManager.bidding(elderHand[0]);
            if (gameManager.declarer == null) {
                Logger.println("all pass");
            } else {
                gameManager.declarer.takeTalon(gameManager.getTalonCards());
                Config.Bid bid = gameManager.declarer.discard();
                gameManager.declarer.declareRound(gameManager.getMinBid(), elderHand[0] == 0);
                Logger.printf("declarer %s, round %s, %s\n",
                    gameManager.declarer.getName(), gameManager.declarer.getBid(), gameManager.declarer.toString());
            }
        }
    }

    private Player testPlayer(final BidHelper bidHelper, int i) {
/*
        return new Bot("" + i) {
            @Override
            public Config.Bid getBid(Config.Bid minBid, int turn) {
                Config.Bid bid = bidHelper.nextBid();
                if (!Config.Bid.BID_MISERE.equals(bid) && !Config.Bid.BID_PASS.equals(bid)) {
                    Assert.assertEquals(String.format("expected %s, actual %s", bid.getName(), minBid.getName()),
                            bid, minBid);
                }
                this.bid = bid;
                return bid;
            }
        };
*/
        return new Bot("" + i);
    }

/*
    @Test
    public void testBiddingSequence() {
        String[] sources = {
            "p, p, p,",
            "6♠, 6♣, 6♦ -> 6♥, 6-",
            "6♠, p, 6♣ -> 6♣, 6♦",
            "6♠, p, 6♣ -> 6♣",
            "6♠, 6♣, p -> 6♣",
            "6♠, 6♣, 6♦ -> 6♥",
            "6♠, m, p -> 9♠",
            "6♠, m, 9♠ -> 9♠, 9♣",
            "m, 9♠, p -> 9♠",
            "m, p, 9♠ -> 9♠",
            "m, p, 9♠ -> 9♠",
            "6♠, m, p -> 9♠",
            "6♠, m, 9♠ -> 9♠, 9♣",
        };

        for (String source : sources) {
            BidHelper bidHelper = new BidHelper();
            Pattern p = Pattern.compile("([6-9][♠♣♦♥\\-]|p|m)");
            Matcher m = p.matcher(source);
            while (m.find()) {
                String match = m.group();
                Logger.printf("%s,", match);
                Config.Bid bid;
                if ("p".equals(match)) {
                    bid = Config.Bid.BID_PASS;
                } else if ("m".equals(match)) {
                    bid = Config.Bid.BID_MISERE;
                } else {
                    bid = Config.Bid.fromName(match);
                }
                bidHelper.bids.add(bid);
            }
            Logger.println();

            gameManager.getPlayers()[0] = testPlayer(bidHelper, 0);
            gameManager.getPlayers()[1] = testPlayer(bidHelper, 1);
            gameManager.getPlayers()[2] = testPlayer(bidHelper, 2);
            Player declarer = gameManager.bidding(0);
            if (declarer == null) {
                Assert.assertEquals(Config.Bid.BID_ALL_PASS, gameManager.getMinBid());
                Logger.printf("final index %d, all-pass\n", bidHelper.index);
            } else {
                Assert.assertEquals("Bidding error",
                        bidHelper.bids.get(bidHelper.bids.size() - 1), declarer.getBid());
                Logger.printf("final index %d, declarer %s\n", bidHelper.index, declarer.getName());
            }
        }
    }
*/

    public static class BidHelper {
        final List<Config.Bid> bids = new LinkedList<>();
        int index = 0;

        public Config.Bid nextBid() {
            if (index >= bids.size()) {
                return Config.Bid.BID_PASS;
            }
            return bids.get(index++);
        }

        public Config.Bid getBid() {
            return bids.get(index);
        }
    }

    @Test
    public void testQue() throws InterruptedException {
        java.util.concurrent.BlockingQueue<Integer> que = new LinkedBlockingQueue<>();

        que.put(1);
        que.put(2);
        int res = que.take();
        System.out.println(res);
    }

}
