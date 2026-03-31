/*  This file is part of JPref.
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
package com.ab.pref;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.jpref.engine.GameManager;
import com.ab.jpref.engine.Player;
import com.ab.jpref.engine.Trick;
import com.ab.util.BidData;
import com.ab.util.Logger;
import com.ab.util.Util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class HumanPlayer extends Player {
    public static final boolean DEBUG_LOG = false;

    private final Util util = Util.getInstance();
    private final BlockingQueue<Config.Queueable> queue = new LinkedBlockingQueue<>();
    final GameManager.EventObserver clickable;

    GameManager.RestartCommand restartCommand;
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
    public void abortThread(GameManager.RestartCommand restartCommand) {
        this.restartCommand = restartCommand;
        clearQueue();
    }

    @Override
    public void clear() {
        super.clear();
        restartCommand = null;
    }

    @Override
    public void clearQueue() {
        accept(Config.Bid.BID_PASS);
        util.sleep(10);
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
            if (restartCommand != null) {
                GameManager.RestartCommand _restartCommand = restartCommand;
                restartCommand = null;
                throw new PrefExceptionRerun(_restartCommand.name());   // a little ugly
            }
            Logger.printf(DEBUG_LOG, "human blocking:%s -> %s\n", Thread.currentThread().getName(), GameManager.getState().getRoundStage());
            Config.Queueable q = queue.take();
            Logger.printf(DEBUG_LOG, "human unblock:%s bid %s\n", Thread.currentThread().getName(), q.toString());
            if (restartCommand != null) {
                GameManager.RestartCommand _restartCommand = restartCommand;
                restartCommand = null;
                throw new PrefExceptionRerun(_restartCommand.name());   // a little ugly
            }
            return q;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Config.Bid getBid(Config.Bid minBid, int elderHand) {
        clickable.setSelectedPlayer(this);
        bid = (Config.Bid)takeFromQueue();
        return bid;
    }

    public void drop(CardSet drop) {
        super.drop(drop);
        this.drop = drop.clone();
    }

    @Override
    public Config.Bid drop() {
        clickable.setSelectedPlayer(this);
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
            clickable.setSelectedPlayer(this);
            this.bid = (Config.Bid) takeFromQueue();
        }
        playerBid.setBid(this.bid);
        playerBid.drops = this.drop;
    }

    @Override
    public void respondOnDeclaration() {
        // todo: whist or half-whist or pass
        clickable.setSelectedPlayer(this);
        Config.Queueable q = takeFromQueue();        // block
        this.bid = (Config.Bid)q;
    }

    @Override
    public boolean playWhistLaying() {
        clickable.setSelectedPlayer(this);
        Config.Queueable q = takeFromQueue();        // block
        Config.Bid bid = (Config.Bid)q;
        return bid.equals(Config.Bid.BID_WHIST_LAYING);
    }

    @Override
    public Card play(Trick trick) {
        clickable.setSelectedPlayer(this);
        Config.Queueable q = takeFromQueue();
        if (!(q instanceof Card)) {
            Logger.println(q.toString());
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