/*  This file is part of jpref.
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
 * Copyright (C) 2026 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 2/14/26
 *
 */

package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Config;
import com.ab.util.Logger;
import com.ab.util.Util;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestTrick {
    public static final int NOP = Config.NOP;
    static final Config config = Config.getInstance();
    static final Util util = Util.getInstance();
    static GameManager gameManager;

    @Before
    public void initClass() {
        gameManager = new GameManager(config, null);
        GameManager.DEBUG_LOG = false;      // suppress thread status logginga
        config.pauseBetweenRounds.set(0);
    }

    @Test
    public void testTrick() {
        String[] sources = {
            // trick : declarerTricks forecast
            "♥K8A : 10 10",
        };
        BaseTrick bt = new BaseTrick();
        Card c0 = bt.get(0);
        bt.add(Card.fromName("♦A"));
        c0 = bt.get(0);
        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split("\\s+(:|->)\\s+");
            CardList cards = util.toCardList(parts[0]);
            String[] _parts = parts[1].split("\\s+");
            int pastTricks = Integer.parseInt(_parts[0]);
            int futureTricks = Integer.parseInt(_parts[1]);
            Trick trick = new Trick();
            BaseTrick baseTrick = new BaseTrick(trick);
            Card lastCard = null;
            for (Card c : cards) {
                trick.add(c);
                Card.Suit s = trick.getStartingSuit();
                Assert.assertEquals(c.getSuit(), s);
                baseTrick.add(c);
                lastCard = baseTrick.get(baseTrick.size() - 1);
                Assert.assertEquals(c, lastCard);
            }
            Assert.assertEquals(trick.toString(), parts[0]);
            Assert.assertEquals(trick.toString(), baseTrick.toString());
            baseTrick = new BaseTrick(trick);
//            Assert.assertEquals(0, baseTrick.getNumber());
//            String s = baseTrick.toColorString();
            baseTrick.setDone();
            Assert.assertTrue(baseTrick.isDone());
            baseTrick.clearDone();
            baseTrick.setFutureTricks(futureTricks);
            Assert.assertEquals(futureTricks, baseTrick.getFutureTricks());
            if (futureTricks < 8) {
                baseTrick.updateFutureTricks(2);
                Assert.assertEquals(futureTricks, baseTrick.getFutureTricks() - 2);
            }

            baseTrick.setPastTricks(pastTricks);
            Assert.assertEquals(pastTricks, baseTrick.getPastTricks());
            baseTrick.updatePastTricks(-1);
            Assert.assertEquals(pastTricks, baseTrick.getPastTricks() + 1);
            Assert.assertEquals(trick.toString(), baseTrick.toString());
            System.out.println(baseTrick.toColorString());
            Card c = baseTrick.removeLast();
            Assert.assertEquals(lastCard, c);
            System.out.println(baseTrick.toColorString());
            c = baseTrick.removeLast();
            System.out.println(baseTrick.toColorString());
            Assert.assertFalse(baseTrick.isDone());
/*
            for (int n = 0; n < 9; ++n) {
                baseTrick.setNumber(n);
                Assert.assertFalse(baseTrick.isDone());
                Assert.assertEquals(n, baseTrick.getNumber());
                baseTrick.incrementNumber();
                Assert.assertEquals(n + 1, baseTrick.getNumber());
                baseTrick.decrementNumber();
                Assert.assertEquals(n, baseTrick.getNumber());
            }
*/
        }
    }


}
