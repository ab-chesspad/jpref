/*
     Copyright (C) 2024-2025	Alexander Bootman, alexbootman@gmail.com
 *
 * Created by Alexander Bootman on 12/22/2024.
 */
package com.ab.jpref.cards;

import com.ab.jpref.engine.GameManager;
import com.ab.util.Couple;
import com.ab.util.Logger;
import com.ab.util.Pair;
import com.ab.util.Util;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

public class TestCardList {

    @BeforeClass
    public static void initClass() {
        Logger.set(System.out);
    }

    @Test
    public void testListData() {
        String[] sources = {
            // suit, discarded, holes, good, ok1stMove, distanceToTop
            "  0 0 0 7",       // empty
            "78J  0 1 1 3",    // хорошая беспроблемная;

            "8JK 79 0 1 0 1",  // хорошая проблемная
            "89  1 0 1 5",     // плохая беспроблемная.
            "89x  1 0 1 4",    // плохая беспроблемная.
            "QKA  5 0 1 0",    // плохая беспроблемная;
            "JQ  4 0 1 2",     // плохая беспроблемная;
            "xk JQ 3 0 1 1",   // плохая беспроблемная;
            "9JK  2 0 0 1",    // плохая беспроблемная;
            "XJK  3 0 1 1",    // плохая беспроблемная;
            "9XK  3 0 1 1",    // плохая беспроблемная;

            "789  0 1 1 5",    // хорошая беспроблемная;
            "89x 7 0 1 1 4",   // хорошая беспроблемная
            "79x 8 0 1 1 4",   // хорошая беспроблемная
            "8xJ 79 0 1 1 3",  // хорошая беспроблемная
            "8JK 79 0 1 0 1",  // хорошая проблемная

            "8XK  2 0 0 1",    // плохая проблемная;
            "8KA  4 0 0 0",    // плохая проблемная;
            "XKA  4 0 0 0",    // плохая проблемная;
            "8X  1 0 0 4",     // плохая проблемная;
            "8XQ  1 0 0 2",    // плохая проблемная;
            "XQA  3 0 0 0",    // плохая проблемная;
            "8A  5 0 0 0",     // плохая проблемная;

            "7XK  2 1 0 1",    // хорошая проблемная;
            "7KA  4 1 0 0",    // хорошая проблемная;
            "7QA  3 1 0 0",    // хорошая проблемная;
            "7JK  2 1 0 1",    // хорошая проблемная;
            "89Q 7K 0 1 1 1",  // хорошая проблемная;
            "89Q 7A 0 1 1 1",  // хорошая проблемная;
            "79J K 0 1 0 2",   // хорошая проблемная;
            "79J  0 1 0 3",    // хорошая проблемная;
            "79Q  1 1 0 2",    // хорошая проблемная;
            "79A  3 1 0 0",    // хорошая проблемная.
            "79JA  1 1 0 0",   // хорошая проблемная.
            "89Q 7 0 1 1 2",   // хорошая проблемная;
            "79Q J 0 1 0 2",   // хорошая проблемная
        };

        for (String source : sources) {
            System.out.printf("%s\n", source);
            String[] parts = source.split(" ");
            String suit = parts[0].replaceAll(".(?!$)", "$0s");
            if (!suit.isEmpty()) {
                suit += "s";
            }
            String discarded = parts[1].replaceAll(".(?!$)", "$0s");
            if (!discarded.isEmpty()) {
                discarded += "s";
            }
            CardList cardList = Util.toCardList(suit);
            HashSet<Card> discardedSet = new HashSet<>(Util.toCardList(discarded));
            int holes = Integer.parseInt(parts[2]);           // is problematic when holes > 0
            boolean good = !parts[3].equals("0");       // for all-pass, includes smallest rank
            boolean ok1stMove = !parts[4].equals("0");  // 1st move does not add tricks, the list includes 2 smallest ranks
            int distanceToTop = Integer.parseInt(parts[5]);
            CardList.ListData listData = cardList.getListData(discardedSet);
            Assert.assertEquals("holes", holes, listData.holes);
            Assert.assertEquals("good", good, listData.good);
            Assert.assertEquals("ok1stMove", ok1stMove, listData.ok1stMove);
            Assert.assertEquals("distanceToTop", distanceToTop, listData.distanceToTop);
//            int tricksMeStart = Integer.parseInt(parts[1]);
//            int tricksTheyStart = Integer.parseInt(parts[2]);

        }
    }

    @Test
    public void testGetMinTricks() {
        String[] sources = {
        "A8 2 1",     // проблемная плохая;
        "QJ 2 2",     // беспроблемная плохая;
        "AKQ 3 3",    // беспроблемная плохая;
        "A97 2 1",    // проблемная хорошая.
        "8XQ 3 1",    // проблемная плохая;
        "79Q 2 1",    // проблемная хорошая;
        "79J 2 0",    // проблемная хорошая;
        "789 0 0",    // беспроблемная хорошая;
    };
    for (String source : sources) {
        System.out.printf("%s\n", source);
        String[] parts = source.split(" ");
        String suit = parts[0].replaceAll(".(?!$)", "$0s") + "s";
        CardList cardList = Util.toCardList(suit);
        Collections.sort(cardList);
        int tricksMeStart = Integer.parseInt(parts[1]);
        int tricksTheyStart = Integer.parseInt(parts[2]);

//        CardList.ListData listData = new CardList.ListData();
        Pair<Couple<Integer>, Couple<Boolean>> tricks = cardList.getMinTricks(new HashSet<Card>());
        Assert.assertEquals("invalid tricks me start", tricksMeStart, (int)tricks.first.first);
        Assert.assertEquals("invalid tricks they start", tricksTheyStart, (int)tricks.first.second);
    }
}

    @Test
    public void testRussian() throws IOException {
        String[] sources = {
"ТК, ТД, ТВ, Т8, Т7, К7, КД, Д7, Д8, Т10, К10, Д10, ДВ, В10, К9, Д9, К8, В8, В7, В9, 109, 107",
        };

        String fromChars = "ТКДВ1";
        String toChars =   "AKQJX";

        for (String source : sources) {
            String s1 = source.replaceAll("0", "");
            StringBuilder sb = new StringBuilder();
            for (char c : s1.toCharArray()) {
                int i = fromChars.indexOf("" + c);
                if (i < 0) {
                    sb.append(c);
                } else {
                    sb.append(toChars.charAt(i));
                }
            }
            System.out.println(sb);
        }
        System.out.println("done");
    }

}
