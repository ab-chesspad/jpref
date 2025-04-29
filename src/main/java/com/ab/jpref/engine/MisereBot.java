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
 * Created: 4/6/25
 */

package com.ab.jpref.engine;


import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.util.Logger;

import java.util.LinkedList;
import java.util.List;

// it replaces player[0] (humane) and finds the play plan for the other two
public class MisereBot extends Bot {
    public MisereBot(Player realPlayer) {
        super(realPlayer);
    }

    public void createPlan(Player left, Player right, boolean elderHand) {
        CardList plan = new CardList();
        // 1. guess discarded when the other players' cards are unknown:
        Bot.SuitResults suitResults = discardForMisere(elderHand);
        discard(suitResults.discarded);

        // 2. find suits that can be caught given current hands
        int firstMoveTricks = Integer.MAX_VALUE;
        int totalMeStart = 0, totalTheyStart = 0;
        CardList firstMoveSuit = null;
        List<CardList.ListData> holesMeStart = new LinkedList<>();
        List<CardList.ListData> holesTheyStart = new LinkedList<>();
        for (int i = 0; i < Card.Suit.values().length - 1; ++i) {
            CardList suit = mySuits[i];
            CardList.ListData listData = suit.getUnwantedTricks(this.leftSuits[i], this.rightSuits[i]);
            if (listData.ok1stMove) {
                if (firstMoveTricks > listData.maxMeStart) {
                    firstMoveTricks = listData.maxMeStart;
                    firstMoveSuit = suit;
                }
            }
            totalTheyStart += listData.maxTheyStart;
            totalMeStart += listData.maxMeStart;
            if (listData.maxMeStart > 0) {
                holesMeStart.add(listData);
            }
            if (listData.maxTheyStart > 0) {
                holesTheyStart.add(listData);
            }
        }
        if (holesMeStart.isEmpty() ||
            holesTheyStart.isEmpty() && (!elderHand || firstMoveSuit != null)) {
            // clean misère
            Logger.println("clean misere, no plan");
            return;
        }
        Logger.printf("%d suit(s) with holes\n", holesTheyStart.size());


    }
}
