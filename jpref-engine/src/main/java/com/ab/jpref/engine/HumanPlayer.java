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
 * Created: 1/30/2025
 */
package com.ab.jpref.engine;

import static com.ab.jpref.engine.GameManager.RestartCommand;
import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.util.BidData;
import com.ab.util.Logger;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import static java.lang.Thread.currentThread;

public class HumanPlayer extends Player {
    public static final boolean DEBUG_LOG = false;

    private final BlockingQueue<Config.Queueable> queue = new LinkedBlockingQueue<>();
    final GameManager.EventObserver clickable;

    private CardSet drop;

    public HumanPlayer(int number, GameManager.EventObserver clickable) {
        this.number = number;
        this.clickable = clickable;
    }

    protected HumanPlayer(Player other, GameManager.EventObserver clickable) {
        super(other);
        this.number = other.getNumber();
        this.clickable = clickable;
    }

    @Override
    public void abortThread(RestartCommand restartCommand) {
        clearQueue();
        accept(restartCommand);
    }

    @Override
    public void clear() {
        super.clear();
    }

    public void clearQueue() {
        while (queue.peek() != null) {
            queue.remove();
        }
    }

    @Override
    public void accept(Config.Queueable q) {
        try {
            queue.put(q);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Config.Queueable takeFromQueue() throws Player.PrefExceptionRerun {
        try {
            Logger.printf(DEBUG_LOG, "human blocking:%s\n", currentThread().getName());
            Config.Queueable q = queue.take();
            Logger.printf(DEBUG_LOG, "human unblock:%s got %s\n", currentThread().getName(), q);
            if (q instanceof RestartCommand) {
                throw new PrefExceptionRerun(((RestartCommand)q).name());   // a little ugly
            }
            return q;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Config.Bid getBid(Config.Bid minBid, int elderHand) {
        clickable.setCurrentPlayer(this);
        Config.Queueable q = takeFromQueue();
        if (q instanceof Config.Bid) {
            bid = (Config.Bid)q;
            return bid;
        }
        return Config.Bid.BID_6S;
    }

    // wait for mouse click, needed when talon is shown
    @Override
    public void acknowledge() {
        clickable.setCurrentPlayer(this);
        Config.Queueable q = takeFromQueue();
        Logger.printf(DEBUG_LOG, "view acknowledged, %s", q.toString());
    }

    public void drop(CardSet drop) {
        super.drop(drop);
        this.drop = new CardSet(drop);
    }

    @Override
    public Config.Bid drop() {
        clickable.setCurrentPlayer(this);
        Config.Queueable q = takeFromQueue();        // block
        if (Config.Bid.BID_WITHOUT_THREE.equals(q)) {
            bid = (Config.Bid)q;
        }
        return bid;
    }

    @Override
    public void declareRound(Config.Bid minBid, int elderHand) {
        BidData.PlayerBid playerBid = new BidData.PlayerBid();
        if (!minBid.equals(Config.Bid.BID_MISERE)) {
            clickable.setCurrentPlayer(this);
            this.bid = (Config.Bid) takeFromQueue();
        }
        playerBid.setBid(this.bid);
        playerBid.drops = this.drop;
    }

    @Override
    public void respondOnDeclaration() {
        // todo: whist or half-whist or pass
        clickable.setCurrentPlayer(this);
        Config.Queueable q = takeFromQueue();        // block
        this.bid = (Config.Bid)q;
    }

    @Override
    public boolean playWhistLaying() {
        clickable.setCurrentPlayer(this);
        Config.Queueable q = takeFromQueue();        // block
        Config.Bid bid = (Config.Bid)q;
        return bid.equals(Config.Bid.BID_WHIST_LAYING);
    }

    @Override
    public Card play(Trick trick) {
        clickable.setCurrentPlayer(this);
        Config.Queueable q = takeFromQueue();
        if (!(q instanceof Card)) {
            Logger.println(q);
            return Card.fromValue(1);  // dummy
        }
        return (Card)q;
    }

    public boolean isOK2Play(Card card) {
        Trick trick = GameManager.getInstance().getTrick();
        if (trick.getStartingSuit() == null) {
            return true;
        }
        if (card.getSuit().equals(trick.getStartingSuit())) {
            return true;
        }
        if (!myHand.list(trick.getStartingSuit()).isEmpty()) {
            return false;
        }
        Card.Suit trumpSuit = trick.getTrumpSuit();
        if (card.getSuit().equals(trumpSuit)) {
            return true;
        }
        return trumpSuit == null || myHand.list(trumpSuit).isEmpty();
    }
}