/*
     Copyright (C) 2024-2025	Alexander Bootman, alexbootman@gmail.com
 *
 * Created by Alexander Bootman on 12/22/2024.
 */
package com.ab.jpref.cards;

import com.ab.util.Logger;
import com.ab.util.Util;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class TestCardList {

    @BeforeClass
    public static void initClass() {
        Logger.set(System.out);
    }

    @Test
    @Ignore
    public void testVerifyDeck() {
        CardList deck = CardList.getDeck();
//*
        deck.add(new Card("♦X"));
        deck.add(new Card("♦A"));
        deck.add(new Card("♦A"));
//*/
        deck.remove(0);
        deck.remove(0);
        deck.remove(0);
        deck.verifyDeck();
    }


    @Test
    public void testListData() {
        String[] sources = {
            // suit, left, right, tricksTheyStart, tricksMeStart, good, ok1stMove
//            " 789JQKA 789xJQKA 0 0 0 0",    // empty
            "AK 7J 8xQ 2 2 0 1",            // плохая беспроблемная
            "8JQK 79 xA 0 1 0 0",           // плохая проблемная;
            "79Q 8XJKA 8XJKA 1 2 1 0",      // хорошая проблемная
            "A  J 1 1 0 1",                // плохая беспроблемная
            "89x 7JQKA 7JQKA 1 1 0 1",      // плохая беспроблемная.
            "78Q 9XJKA 9XJKA 1 1 1 1",      // хорошая беспроблемная
            "8JK xQA xQA 0 2 1 0",          // хорошая проблемная
            "8J xQKA xQKA 0 1 1 0",         // хорошая проблемная
            "789K xJQA xJQA 0 1 1 0",       // хорошая проблемная;
            "7XK 89JQA 89JQA 1 2 1 0",      // хорошая проблемная
            "8JK 9xQA 9xQA 1 2 1 0",        // хорошая проблемная
            "79J 8xQKA 8xQKA 0 2 1 0",      // хорошая проблемная
            "XA 789JQK 789JQK 2 2 0 1",     // плохая беспроблемная
            "8XQ 79JKA 79JKA 1 3 0 0",      // плохая проблемная; ??
            "8KA 79xJQ 79xJQ 2 3 0 0",      // плохая проблемная; todo: проблемная, учесть плотность
            "9XK 78JQA 78JQA 2 3 0 0",      // плохая проблемная;
            "9JK 78xQA 78xQA 2 3 0 0",      // плохая проблемная;
            "8XK 79JQA 79JQA 1 3 0 0",      // плохая проблемная. issue: if they start with 9, there is 2 my tricks
            "XQKA 79 8 1 2 0 0",            // плохая проблемная
            "8X 79JQKA 79JQKA 1 2 0 0",     // плохая проблемная;
            "78J 9xQKA 9xQKA 0 1 1 0",      // хорошая проблемная;

            "89 7xJQKA 7xJQKA 1 1 0 1",     // плохая беспроблемная.
            "QKA 789xJ 789xJ 3 3 0 1",      // плохая беспроблемная;
            "JQ 789xKA 789xKA 2 2 0 1",     // плохая беспроблемная;
            "xk 789A 789A 2 2 0 1",         // плохая беспроблемная;
            "XJK 789QA 789QA 3 3 0 1",      // плохая беспроблемная;

            "789 xJQKA xJQKA 0 0 1 1",      // хорошая беспроблемная;
            "89x JQKA JQKA 0 0 1 1",        // хорошая беспроблемная
            "79x JQKA JQKA 0 0 1 1",        // хорошая беспроблемная
            "8xJ QKA QKA 0 0 1 1",          // хорошая беспроблемная

            "XKA 789JQ 789JQ 3 3 0 1",      // плохая беспроблемная;
            "XQA JK JK 0 1 1 0",            // хорошая проблемная;
            "8A 79xJQK 79xJQK 1 2 0 0",     // плохая проблемная;

            "7KA 89xJQ 89xJQ 2 2 1 1",      // хорошая беспроблемная;
            "7QA 89xJK 89xJK 2 2 1 1",      // хорошая беспроблемная;
/*
            "7JK 789xJQKA 789xJQKA  2 1 0 1",    // хорошая проблемная;
            "89Q 7K 0 1 1 1",  // хорошая проблемная;
            "89Q 7A 0 1 1 1",  // хорошая проблемная;
            "79J K 0 1 0 2",   // хорошая проблемная;
            "79J  0 1 0 3",    // хорошая проблемная;
            "79Q  1 1 0 2",    // хорошая проблемная;
            "79A  3 1 0 0",    // хорошая проблемная.
            "79JA  1 1 0 0",   // хорошая проблемная.
            "89Q 7 0 1 1 2",   // хорошая проблемная;
            "79Q J 0 1 0 2",   // хорошая проблемная
*/
        };

        for (String source : sources) {
            System.out.printf("%s\n", source);
            String[] parts = source.split(" ");

            CardList[] cardLists = new CardList[3];
            for (int i = 0; i < cardLists.length; ++i) {
                String suit = parts[i]; // .replaceAll(".(?!$)", "$0s");
                if (!suit.isEmpty()) {
                    suit = "♠" + suit;
                }
                cardLists[i] = Util.toCardList(suit);
            }

            int i = 2;
            int tricksTheyStart = Integer.parseInt(parts[++i]);           // is problematic when holes > 0
            int tricksMeStart = Integer.parseInt(parts[++i]);           // is problematic when holes > 0
            boolean good = !parts[++i].equals("0");       // for all-pass, includes smallest rank
            boolean ok1stMove = !parts[++i].equals("0");  // 1st move does not add tricks, the list includes 2 smallest ranks
//            int distanceToTop = Integer.parseInt(parts[++i]);
            CardList.ListData listData = cardLists[0].getUnwantedTricks(cardLists[1], cardLists[2]);
            Assert.assertEquals("good", good, listData.good);
            Assert.assertEquals("tricksMeStart", tricksMeStart, listData.maxMeStart);
            Assert.assertEquals("tricksTheyStart", tricksTheyStart, listData.maxTheyStart);
            Assert.assertEquals("ok1stMove", ok1stMove, listData.ok1stMove);

//            Assert.assertEquals("distanceToTop", distanceToTop, listData.distanceToTop);
        }
    }

}
