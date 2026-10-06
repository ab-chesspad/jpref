package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import static com.ab.jpref.config.Config.NOP;
import static com.ab.jpref.config.Config.ROUND_SIZE;
import com.ab.jpref.config.Config.Bid;
import com.ab.util.Bidder;
import com.ab.util.Logger;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.util.Arrays;

import static com.ab.util.Logger.printf;
import static com.ab.util.Logger.println;
import static com.ab.util.Util.DEAL_MARK;
import static com.ab.util.Util.currMethodName;

public class TestTrickList extends BaseTest {
    static GameManager gameManager;
    static TrickList trickList;

    static class TrickListBot extends Bot {
        TrickListBot(int number) {
            super(number);
        }

        TrickListBot(Player realPlayer) {
            super(realPlayer);
        }

        CardSet actualDrops = new CardSet();    // for diagnostics
        int bmDrops0;       // drop candidates, trump excluded
        int drop0, drop1;   // current pair, single bits, drop0 < drop1

        // advance to the next pair after a round; 0 when all pairs are done
        public int nextDrop() {
            drop1 = CardSet.next(bmDrops0, drop1);
            if (drop1 == 0) {
                drop0 = CardSet.next(bmDrops0, drop0);
                drop1 = CardSet.next(bmDrops0, drop0);
                if (drop1 == 0) {
                    drop0 = 0;      // drop0 was the last candidate
                }
            }
            return drop0;
        }

        @Override
        public void declareRound(Bid minBid, int _elderHand, Trick trick) {
            int elderHand = (this.number - _elderHand + NOP) % NOP;  // relative to self
            moveIndex = 0;
            Bot targetBot;
            if (Bid.BID_MISERE.equals(minBid)) {
                targetBot = new MisereBot(this);
            } else {
                targetBot = new ForTricksBot(this);
            }
            Bidder.PlayerBid playerBid = targetBot.getDrop(elderHand, 2, trick);
            Bot.playerBid = playerBid;
            this.bid = targetBot.getBid();
            if (playerBid.value < 61 || naturalDrop) {
                bmDrops0 = drop0 = drop1 = 0;   // no iteration, nextDrop() returns 0
            } else {
                if (drop0 == 0) {       // first round of this deal
                    CardSet candidates = new CardSet(myHand);
                    Card.Suit trump = this.bid.getTrump();
                    if (trump != null) {
                        candidates.remove(myHand.list(trump));
                    }
                    bmDrops0 = candidates.getBitmap();
                    drop0 = CardSet.next(bmDrops0, 0);
                    drop1 = CardSet.next(bmDrops0, drop0);
                }
                playerBid.drops.setBitmap(drop0 | drop1);
            }
Logger.printf("*** drop: %s ***\n", playerBid.drops);
            actualDrops = new CardSet(playerBid.drops);
            drop(playerBid.drops);              // always 10 cards after this
            realBot = null;
            if (guessDrops) {
                if (declarerSolves) {
                    realBot = targetBot;
                    targetBot.drop(playerBid.drops);
                }
                // like a human declarer: defenders guess the drops themselves,
                // see Bot.play() -> getDeclarerForDefender()
                Bot.targetBot = null;
                Bot.playerBid = null;
                return;
            }
            Bot.targetBot = targetBot;
            targetBot.drop(playerBid.drops);
        }

        @Override
        public Card play(Trick trick) {
            // with guessDrops Bot.targetBot holds the defenders' guess, not the declarer's real hand
            if (!iterMoves && !guessDrops || this.number != gameManager().declarerNumber) {
                return super.play(trick);
            }
            int k = moveIndex++;
            if (k == 0) {
                declarerMoves.clear();
            }
            Card card;
            if (!iterMoves && realBot != null) {
                card = realPlay(trick);
            } else {
                CardList legal = legalCards(trick);
                if (k >= forcedDepth) {
                    choices[k] = 0;
                }
                options[k] = legal.size();
                card = legal.get(choices[k]);
            }
            declarerMoves.add(card);
            return card;
        }

        // declarer plays with its own solver, defenders keep their guess in Bot.targetBot
        Card realPlay(Trick trick) {
            Player[] players = gameManager().getPlayers();
            realBot.myHand = new CardSet(myHand);
            realBot.leftHand = new CardSet(players[(number + 1) % NOP].myHand);
            realBot.rightHand = new CardSet(players[(number + 2) % NOP].myHand);
            realBot.tricks = this.tricks;
            Bot defendersBot = Bot.targetBot;
            Bot.targetBot = realBot;
            try {
                return realBot.play(trick);
            } finally {
                Bot.targetBot = defendersBot;
            }
        }

        Bot realBot;        // declarer's own solver when the defenders guess the drops
        // baseline: the declarer drops as the bot decides
        static boolean naturalDrop;
        // with guessDrops and without iterMoves the declarer plays with realBot
        static boolean declarerSolves;
        static final CardList declarerMoves = new CardList();     // for diagnostics

        // defenders do not know the actual drops, declarer plays legalCards()
        static boolean guessDrops;

        // declarer move tree, static because avatars4Round() replaces the players with copies
        static boolean iterMoves;
        static final int[] choices = new int[ROUND_SIZE];  // card index at each declarer move
        static final int[] options = new int[ROUND_SIZE];  // number of legal cards at each move
        static int moveIndex;       // declarer move in the current round
        static int forcedDepth;     // moves below it replay choices[]

        static void resetMoves() {
            moveIndex = 0;
            forcedDepth = 0;
        }

        // advance to the next path after a round; false when the tree is done
        static boolean nextMoves() {
            int depth = moveIndex;
            moveIndex = 0;
            for (int k = depth - 1; k >= 0; --k) {
                if (choices[k] + 1 < options[k]) {
                    ++choices[k];
                    forcedDepth = k + 1;
                    return true;
                }
            }
            forcedDepth = 0;
            return false;
        }

        CardList legalCards(Trick trick) {
            CardSet cards = myHand;
            if (trick.startingSuit != null) {
                if (!myHand.list(trick.startingSuit).isEmpty()) {
                    cards = myHand.list(trick.startingSuit);
                } else if (trick.trumpSuit != null && !myHand.list(trick.trumpSuit).isEmpty()) {
                    cards = myHand.list(trick.trumpSuit);
                }
            }
            // per suit try only the smallest and the largest card,
            // just one of them when no outstanding card is between them
            CardSet outstanding = new CardSet();
            for (Player p : gameManager().getPlayers()) {
                if (p.number != this.number) {
                    outstanding.add(p.myHand);
                }
            }
            for (int i = 0; i < trick.size(); ++i) {
                outstanding.add(trick.getCard(i));
            }
            CardList res = new CardList();
            int bitset = 0;
            while ((bitset = CardSet.bm4NextSuit(cards.getBitmap(), bitset)) != 0) {
                CardSet suitCards = new CardSet(bitset);
                Card low = suitCards.first();
                Card high = suitCards.last();
                res.add(low);
                if (hasBetween(outstanding, low, high)) {
                    res.add(high);
                }
            }
            return res;
        }

        static boolean hasBetween(CardSet cards, Card low, Card high) {
            for (Card card : cards.list(low.getSuit()).toCardList()) {
                if (card.compareInTrick(low) > 0 && high.compareInTrick(card) > 0) {
                    return true;
                }
            }
            return false;
        }
    }

    static class TrickListGameManager extends GameManager {
        @Override
        protected Bot newBot(int number) {
            return new TrickListBot(number);
        }

        @Override
        protected Bot newBot(Player realPlayer) {
            return new TrickListBot(realPlayer);
        }
    }

    @Before
    public void init() {
        trickList = new TrickList();
        gameManager = new TrickListGameManager();
        gameManager.init(host);
        GameManager.DEBUG_LOG = false;      // suppress thread status logginga
        config.pauseBetweenRounds.set(0);
        config.pauseBetweenMoves.set(0);
        config.pauseBetweenTricks.set(0);
    }

    private InputStream getInputStream(String testFileName) {
        String path = "../etc/tests/" + testFileName;
        File f = new File(path);
        Logger.println(f.getAbsolutePath());
        try {
            return new FileInputStream(path);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    // do not check results, just verify that the program does not crash
    @Test
    @Ignore("extremely long test!")
    public void testAllMoves() throws IOException {
        println("running: " + currMethodName());
        playAll(true, false, false);
    }

    // declarer actually drops every pair, defenders guess the drops as usual
    @Test
    public void testAllDrops() throws IOException {
        println("running: " + currMethodName());
        Assume.assumeTrue(Boolean.getBoolean("slow"));    // slow test, run with -Dslow=true
        playAll(false, true, false);
    }

    // declarer drops every pair and plays it with its own solver, defenders guess the drops;
    // fails if some drop gets a better result than the bot's own drop
    @Test
    public void testUnexpectedDrops() throws IOException {
        println("running: " + currMethodName());
        Assume.assumeTrue(Boolean.getBoolean("slow"));    // slow test, run with -Dslow=true
        playAll(false, true, true);
    }

    // as above, but in deals marked "iter" the declarer also tries every move
    @Test
    @Ignore("extremely long test!")
    public void testUnexpectedMoves() throws IOException {
        println("running: " + currMethodName());
        playAll(true, true, true);
    }

    // iterMoves: also iterate declarer moves in deals marked "iter"
    // guessDrops: defenders do not know the actual drops
    // checkOutcome: compare every round with the bot's own drop and play, fail if declarer does better
    private void playAll(boolean iterMoves, boolean guessDrops, boolean checkOutcome) throws IOException {
        final int[] count = {0};
        final java.util.List<String> betterOutcomes = new java.util.ArrayList<>();
        final InputStream testInputStream = getInputStream("freeplay");
        gameManager.testInputStream = testInputStream;    // just to avoid duplicate line print

        util.getList(testInputStream,
            (res, tokens) -> {
                if (!tokens.get(0).startsWith(DEAL_MARK)) {
                    return;     // ignore
                }
                int _elderHand = Integer.parseInt(tokens.get(tokens.size() - 1));     // 0-based
                String[] resParts0 = res.split("\\s+:\\s+|\\s+#\\s+");
                String[] resParts = resParts0[0].split("\\s+|#");
                boolean iter = Arrays.asList(resParts).contains("iter");
                Config.Bid expectedBid = Config.Bid.fromName(resParts[0]);
                if (expectedBid.equals(Config.Bid.BID_ALL_PASS)) {
                    return;     // no declarer, no drops to iterate
                }
                boolean misere = expectedBid.equals(Config.Bid.BID_MISERE);
                String dealLine = toInputLine(tokens, res);
                CardList _deck = new CardList();
                for (String token : tokens) {
                    if (token.endsWith(":")) {
                        continue;
                    }
                    _deck.addAll(util.toCardList(token));
                }
                _deck.verifyDeck();
                CardList _talonCards = new CardList(_deck.subList(30, 32));
                // bidding gives the contract to the same hand in every rotation,
                // so comparing outcomes once per deal is enough
                int rotations = checkOutcome ? 1 : NOP;
                for (int declarerNum = 0; declarerNum < rotations; ++declarerNum) {
                    int elderHand = (_elderHand + declarerNum) % NOP;
                    CardList deck = new CardList();
                    for (int j = 0; j < NOP; ++j) {
                        int k = 10 * ((j - declarerNum + NOP) % NOP);
                        deck.addAll(_deck.subList(k, k + 10));
                    }
                    deck.addAll(new CardList(_talonCards));

                    int baseline = 0;
                    String baselineDrop = null;
                    if (checkOutcome) {
                        // the bot's own drop and play, defenders know the drop
                        TrickListBot.naturalDrop = true;
                        TrickListBot.guessDrops = false;
                        TrickListBot.iterMoves = false;
                        TrickListBot bot = playRound(deck, elderHand, expectedBid, _talonCards, dealLine, declarerNum);
                        baseline = gameManager.getDeclarer().getTricks();
                        baselineDrop = bot.actualDrops.toString();
                        printf("declarer #%d: baseline drop %s, %d tricks\n", declarerNum, baselineDrop, baseline);
                        TrickListBot.naturalDrop = false;
                    }

                    TrickListBot.guessDrops = guessDrops;
                    TrickListBot.declarerSolves = checkOutcome;
                    TrickListBot.iterMoves = iterMoves && iter;
                    TrickListBot.resetMoves();
                    TrickListBot bot;
                    int best = baseline;
                    String bestPlay = null;
                    int betterCount = 0;
                    do {
                        bot = playRound(deck, elderHand, expectedBid, _talonCards, dealLine, declarerNum);
                        if (checkOutcome) {
                            int tricks = gameManager.getDeclarer().getTricks();
                            if (misere ? tricks < baseline : tricks > baseline) {
                                ++betterCount;
                                if (misere ? tricks < best : tricks > best) {
                                    best = tricks;
                                    bestPlay = String.format("drop %s, declarer moves %s",
                                        bot.actualDrops, TrickListBot.declarerMoves);
                                }
                            }
                        }
                    } while (TrickListBot.iterMoves && TrickListBot.nextMoves() || bot.nextDrop() != 0);

                    if (bestPlay != null) {
                        String msg = String.format("%s\n# declarer #%d: baseline drop %s -> %d tricks, " +
                                "%d better plays, best %d tricks: %s",
                            dealLine, declarerNum, baselineDrop, baseline, betterCount, best, bestPlay);
                        println("better outcome: " + msg);
                        betterOutcomes.add(msg);
                    }
                }
                ++count[0];
            });
        TrickListBot.guessDrops = false;
        TrickListBot.declarerSolves = false;
        TrickListBot.iterMoves = false;
        printf("done %d deals\n", count[0]);
        if (!betterOutcomes.isEmpty()) {
            Assert.fail(String.format("%d declarers can do better than the bot:\n%s",
                betterOutcomes.size(), String.join("\n", betterOutcomes)));
        }
    }

    // the line as in etc/tests/freeplay: hands and talon separated by 2 spaces, without "deal:"
    private String toInputLine(java.util.List<String> tokens, String res) {
        StringBuilder sb = new StringBuilder();
        int cards = 0;
        String sep = "";
        for (String token : tokens.subList(0, tokens.size() - 1)) {
            if (token.endsWith(":")) {
                continue;
            }
            sb.append(sep).append(token);
            cards += util.toCardList(token).size();
            sep = cards % 10 == 0 ? "  " : " ";
        }
        return String.format("%s  %s -> %s", sb, tokens.get(tokens.size() - 1), res);
    }

    private TrickListBot playRound(CardList deck, int elderHand, Config.Bid expectedBid,
                                   CardList talonCards, String dealLine, int declarerNum) {
        gameManager.deal(deck);
        gameManager.elderHand = elderHand;
        gameManager.setMinBid(Config.Bid.BID_6S);     // the previous deal left its bid here
        gameManager.prepareTest(-1, Config.Bid.BID_6S, null);
        Assert.assertNotNull("no declarer", gameManager.getDeclarer());
        gameManager.setMinBid(expectedBid);
        gameManager.roundStage = GameManager.RoundStage.declareRound;
        Bot.targetBot = null;
        printf("declarer #%d: %s\n", declarerNum, gameManager.initialDeclarerHand);
        TrickListBot bot = (TrickListBot)gameManager.getPlayers()[gameManager.declarerNumber];
        bot.myHand.add(talonCards);
        // with talon, as defenders know it
        gameManager.declarerHand = new CardSet(bot.myHand);
        gameManager.initialDeclarerHand = new CardSet(bot.myHand);
        bot.setBid(expectedBid);
        try {
            gameManager.playRound(deck);
        } catch (RuntimeException | AssertionError e) {
            // paste the 1st line into etc/tests/freeplay to reproduce
            String msg = String.format("%s\n# declarer #%d, drop %s", dealLine, declarerNum, bot.actualDrops);
            println(msg);
            throw new AssertionError(msg, e);
        }
        for (Player p : gameManager.getPlayers()) {
            p.clearHistory();
        }
        return bot;
    }

    @Test
    public void testTrickList() {
        String[] sources = {
            // hands : [elderhand] bid -> tricks
            "♠K ♣A ♦7XKA ♥JKA  ♣78Q ♦9JQ ♥8XQ  ♠7XJ ♣9XJK ♦8 ♥7 : 2 7♦ -> 7",
            "♠K ♣A ♦7XKA ♥JKA  ♣78Q ♦9JQ ♥8XQ  ♠7XJ ♣9XJK ♦8 ♥7 : 1 7♦ -> 7",
            "♦XA ♥7JQK  ♠K ♦78QK ♥X  ♠9 ♣K ♦9 ♥89A : 6♣ -> 3",
//            "♠79 ♥XJ  ♠JQ ♦A ♥K  ♠K ♦K ♥QA : 6♥ -> 0",  // [♠7JK, ♦K ♥X ♦A, ♠9Q ♥Q, ♥AJK]
//            "♠89XKA ♣7X ♦JK  ♠Q ♣9J ♦78X ♥JKA  ♠7J ♣A ♦9QA ♥79Q : 6♠ -> 5",
        };

        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split("\\s+(:|->)\\s+");
            CardList cards = util.toCardList(parts[0]);
            int size = cards.size() / NOP;
            CardSet[] hands = new CardSet[NOP];

            for (int j = 0; j < NOP; ++j) {
                int k = j * size;
                hands[j] = new CardSet(cards.subList(k, k + size));
            }
            ForTricksBot forTricksBot = new ForTricksBot(hands);
            String[] parts1 = parts[1].split("\\s+");
            Bid bid = Bid.fromName(parts1[parts1.length - 1]);
            int elderhand = 0;
            if (parts1.length > 1) {
                elderhand = Integer.parseInt(parts1[0]);
            }
            int expectedTricks = Integer.parseInt(parts[2]);
            Trick trick = new Trick();
            trick.clear(elderhand);
            gameManager.setMinBid(bid);
            gameManager.declarerNumber = 0;
            gameManager.declarerHand = new CardSet(hands[0]);
            trick.minBid = bid;
            trick.trumpSuit = bid.getTrump();
            trick.setNumber(10 - size);
            Bot.playerBid = new Bidder.PlayerBid();
            Card card = trickList.getCard(forTricksBot, trick);
            int tricks = trickList.getEstimate();
            Assert.assertEquals("tricks", expectedTricks, tricks);
        }
    }

}