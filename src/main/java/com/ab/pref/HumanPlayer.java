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
 * Copyright 2025 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 1/30/2025
 */
package com.ab.pref;

import com.ab.jpref.cards.Card;
import com.ab.jpref.config.Config;
import com.ab.jpref.engine.GameManager;
import com.ab.util.Logger;
import com.ab.util.Util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class HumanPlayer extends com.ab.jpref.engine.Player {
    public static final boolean DEBUG = false;

    private final BlockingQueue<Queueable> queue = new LinkedBlockingQueue<>();
    private final Clickable clickable;

    private boolean blocked;
    private boolean abort;

    public HumanPlayer(String name, Clickable clickable) {
        super(name);
        this.clickable = clickable;
    }

    public void abortThread() {
        abort = true;
        clearQueue();
    }

    @Override
    public void clearQueue() {
        accept(Config.Bid.BID_PASS);
        Util.sleep(10);
        while (queue.peek() != null) {
            queue.remove();
        }
    }

    @Override
    public void accept(Queueable q) {
        try {
            queue.put(q);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Queueable takeFromQueue() {
        try {
            if (abort) {
                throw new PrefExceptionRerun();
            }
            blocked = true;
            Queueable q = queue.take();
            blocked = false;
            if (abort) {
                throw new PrefExceptionRerun();
            }
            return q;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Config.Bid getBid(Config.Bid minBid, int turn) {
        Logger.printf(DEBUG, "human:%s -> %s\n", Thread.currentThread().getName(), GameManager.getState().getRoundStage());
        clickable.setSelectedPlayer(this);
        bid = (Config.Bid)takeFromQueue();
        Logger.printf(DEBUG, "human:%s bid %s\n", Thread.currentThread().getName(), bid.getName());
        return bid;
    }

    @Override
//    public RoundData declareRound(Config.Bid minBid, int turn, Config.Bid leftBid, Config.Bid rightBid) {
    public RoundData declareRound(Config.Bid minBid, int turn) {
        return null;
    }

    @Override
    public Card play(GameManager.Trick trick) {
        Logger.printf(DEBUG, "human:%s -> %s\n", Thread.currentThread().getName(), GameManager.getState().getRoundStage());
        clickable.setSelectedPlayer(this);
        Card card = (Card)takeFromQueue();
        return play(card, GameManager.getState().getDiscarded());
    }

    public boolean isOK2Play(Card card) {
        GameManager.Trick trick = GameManager.getInstance().getTrick();
        if (trick.getStartingSuit() == null) {
            return true;
        }
        if (card.getSuit().equals(trick.getStartingSuit())) {
            return true;
        }
        if (card.getSuit().equals(trick.getTrumpSuit())) {
            return true;
        }
        return mySuits[trick.getStartingSuit().getValue()].isEmpty();
    }

    public interface Clickable {
        void setSelectedPlayer(HumanPlayer humanPlayer);
    }

}
