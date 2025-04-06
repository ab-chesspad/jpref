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
import com.ab.jpref.engine.Player;
import com.ab.jpref.engine.Trick;
import com.ab.util.Logger;
import com.ab.util.Util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class HumanPlayer extends com.ab.jpref.engine.Player {
    public static final boolean DEBUG = false;

    private final BlockingQueue<Queueable> queue = new LinkedBlockingQueue<>();
    private final Clickable clickable;

    GameManager.RestartCommand restartCommand;

    public HumanPlayer(String name, Clickable clickable) {
        super(name);
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

    private Queueable takeFromQueue() throws Player.PrefExceptionRerun {
        try {
            if (restartCommand != null) {
                GameManager.RestartCommand _restartCommand = restartCommand;
                restartCommand = null;
                throw new PrefExceptionRerun(_restartCommand.name());   // a little ugly
            }
            Queueable q = queue.take();
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
    public Config.Bid getBid(Config.Bid minBid, boolean meStart) {
        Logger.printf(DEBUG, "human:%s -> %s\n", Thread.currentThread().getName(), GameManager.getState().getRoundStage());
        clickable.setSelectedPlayer(this);
        bid = (Config.Bid)takeFromQueue();
        Logger.printf(DEBUG, "human:%s bid %s\n", Thread.currentThread().getName(), bid.getName());
        return bid;
    }

    @Override
    public Config.Bid discard() {
        clickable.setSelectedPlayer(this);
        Queueable q = takeFromQueue();        // block
        if (Config.Bid.BID_WITHOUT_THREE.equals(q)) {
            bid = (Config.Bid)q;
        }
        return bid;
    }

    @Override
    public void declareRound(Config.Bid minBid, boolean elderHand) {
        Logger.printf(DEBUG, "human:%s -> %s\n", Thread.currentThread().getName(), GameManager.getState().getRoundStage());
        clickable.setSelectedPlayer(this);
        bid = (Config.Bid)takeFromQueue();
        Logger.printf(DEBUG, "human:%s bid %s\n", Thread.currentThread().getName(), bid.getName());
    }

    @Override
    public Card play(Trick trick) {
        Logger.printf(DEBUG, "human:%s -> %s\n", Thread.currentThread().getName(), GameManager.getState().getRoundStage());
        clickable.setSelectedPlayer(this);
        return (Card)takeFromQueue();
    }

    public boolean isOK2Play(Card card) {
        Trick trick = GameManager.getInstance().getTrick();
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
