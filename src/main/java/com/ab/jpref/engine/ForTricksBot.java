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
 * Created: 9/4/25
 */

package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;

public class ForTricksBot extends Bot implements TrickTree.Declarer {
    final GameManager gameManager = GameManager.getInstance();
    Bot realPlayer;
    Trick trick;

    public ForTricksBot() {
        super("probe", 0);
    }

    public ForTricksBot(Player fictitiousBot, Bot realPlayer) {
        super(fictitiousBot);
        if (realPlayer == null) {
            return;
        }
        this.number = 0;
        this.realPlayer = realPlayer;
        if (fictitiousBot != realPlayer) {
            HandResults handResults = this.dropForRound();
            this.drop(handResults.dropped);
        }
    }

    public Config.Bid getMaxBid(Config.Bid minBid, int elderHand) {
        return BidData.getBid(myHand, minBid, elderHand).toBid();
    }

    HandResults dropForRound() {
        HandResults handResults = new HandResults();
        return handResults;
    }

    @Override
    public Card playForTree(TrickTree.TrickNode trickNode) {
        return null;
    }

    @Override
    public boolean keepDetails(TrickTree.TrickNode trickNode) {
        return false;
    }

    @Override
    public CardSet refineDrop(CardSet hand) {
        return null;
    }

    @Override
    public boolean stopTreeBuild(TrickTree.TrickNode trickNode) {
        return false;
    }
}
