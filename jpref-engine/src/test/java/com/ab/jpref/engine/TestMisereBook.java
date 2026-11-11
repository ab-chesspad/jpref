package com.ab.jpref.engine;

import com.ab.jpref.cards.CardSet;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.ab.util.Logger.printf;

// MisereBot.evalMisere() against Утяцкий, 7.6.5, see etc/tests/misere-book; reports, does not fail
@Ignore("one-time test Utyatsky's results")
public class TestMisereBook extends BaseTest {
    static final String SUITS = "♠♣♦♥";
    static final String RANKS = "789XJQKA";
    static final String VERDICTS = "+=-";

    @Test
    public void testBookRecommendations() throws IOException {
        int[] declared = new int[VERDICTS.length()];
        int[] total = new int[VERDICTS.length()];
        int[] rowsAgree = new int[VERDICTS.length()];
        int[] rows = new int[VERDICTS.length()];
        for (String line : Files.readAllLines(Paths.get("../etc/tests/misere-book"), StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s*\\|\\s*");
            String verdict = parts[3];
            int v = VERDICTS.indexOf(verdict);
            List<List<String>> holdings = holdings(parts[1]);
            int n = 0;
            int yes = 0;
            int minValue = Integer.MAX_VALUE;     // % of the misère factor, see MisereBot.getMisereValue()
            int maxValue = Integer.MIN_VALUE;
            String sample = null;       // the worst hand
            for (List<String> holding : holdings) {
                int size = 0;
                for (String suit : holding) {
                    size += suit.length();
                }
                for (List<String> others : others(parts[2], 4 - holding.size(), 10 - size)) {
                    List<String> suits = new ArrayList<>(holding);
                    suits.addAll(others);
                    String hand = hand(suits);
                    CardSet cards = new CardSet(util.toCardList(hand));
                    if (cards.size() != 10) {
                        throw new IllegalArgumentException(line + ": " + hand);
                    }
                    // own lead: elder hand relative to self 0; other's or any lead: the defenders lead
                    int elderHand = parts[0].split(" ")[1].equals("own") ? 0 : 1;
                    MisereBot misereBot = MisereBot.getInstance(cards, new CardSet(), new CardSet());
                    int value = (int) Math.round(misereBot.getMisereValue(elderHand) * 100);
                    ++n;
                    if (misereBot.evalMisere(elderHand)) {
                        ++yes;
                    }
                    maxValue = Math.max(maxValue, value);
                    if (value <= minValue) {
                        minValue = value;
                        sample = hand;
                    }
                }
            }
            declared[v] += yes;
            total[v] += n;
            ++rows[v];
            boolean agree = verdict.equals("+") ? yes == n : verdict.equals("-") ? yes == 0 : true;
            if (agree) {
                ++rowsAgree[v];
            }
            printf("RESULT %s %-5s %-34s %-24s book %s bot %3d/%-3d value %4d..%-4d %s%s\n",
                parts[0].split(" ")[0], parts[0].split(" ")[1], parts[1], parts[2], verdict, yes, n,
                minValue, maxValue, agree ? "" : "DIFF ", sample);
        }
        for (int v = 0; v < VERDICTS.length(); ++v) {
            printf("SUMMARY book %s: rows %d, rows agree %d, hands declared %d of %d\n",
                VERDICTS.charAt(v), rows[v], rowsAgree[v], declared[v], total[v]);
        }
    }

    // every combination of the catchable suits' alternatives
    static List<List<String>> holdings(String spec) {
        List<List<String>> res = new ArrayList<>();
        res.add(new ArrayList<>());
        for (String suit : spec.split("\\s*/\\s*")) {
            List<String> alternatives = new ArrayList<>();
            for (String alt : suit.split("\\s*;\\s*")) {
                alternatives.addAll(expand(alt));
            }
            List<List<String>> next = new ArrayList<>();
            for (List<String> prefix : res) {
                for (String alt : alternatives) {
                    List<String> l = new ArrayList<>(prefix);
                    l.add(alt);
                    next.add(l);
                }
            }
            res = next;
        }
        return res;
    }

    // 89(XJ) -> 89, 8X, 8J
    static List<String> expand(String alt) {
        List<String> res = new ArrayList<>();
        int i = alt.indexOf('(');
        if (i < 0) {
            res.add(alt);
            return res;
        }
        String head = alt.substring(0, i);
        res.add(head);
        for (char c : alt.substring(i + 1, alt.indexOf(')')).toCharArray()) {
            res.add(head.substring(0, head.length() - 1) + c);
        }
        return res;
    }

    // the other suits, their ranks; nSuits suits, nCards cards in all
    static List<List<String>> others(String spec, int nSuits, int nCards) {
        List<List<String>> res = new ArrayList<>();
        if (spec.equals("any")) {
            for (int[] lengths : splits(nSuits, nCards, Math.min(nCards, RANKS.length()))) {
                List<String> suits = new ArrayList<>();
                for (int len : lengths) {
                    suits.add(clean(len, false));
                }
                res.add(suits);
                if (lengths[0] >= 4) {
                    suits = new ArrayList<>(suits);
                    suits.set(0, clean(lengths[0], true));
                    res.add(suits);
                }
            }
            return res;
        }
        if (spec.equals("*")) {
            List<String> suits = new ArrayList<>();
            suits.add(clean(nCards, false));
            res.add(suits);
            return res;
        }
        for (String dist : spec.split("\\s*;\\s*")) {
            List<String> suits = new ArrayList<>();
            for (String len : dist.split("-")) {
                boolean ace = len.endsWith("a");
                suits.add(clean(Integer.parseInt(len.replaceAll("[an]", "")), ace));
            }
            if (suits.size() != nSuits) {
                throw new IllegalArgumentException(spec + ": " + nSuits + " suits");
            }
            res.add(suits);
        }
        return res;
    }

    // the lowest cards, with the ace: the lowest and the ace
    static String clean(int len, boolean ace) {
        if (len == 0) {
            return "";
        }
        if (ace) {
            return RANKS.substring(0, len - 1) + "A";
        }
        return RANKS.substring(0, len);
    }

    // non-increasing lengths, each at most max
    static List<int[]> splits(int nSuits, int nCards, int max) {
        List<int[]> res = new ArrayList<>();
        if (nSuits == 1) {
            if (nCards <= max) {
                res.add(new int[] {nCards});
            }
            return res;
        }
        for (int first = Math.min(nCards, max); first >= 0; --first) {
            for (int[] rest : splits(nSuits - 1, nCards - first, first)) {
                int[] l = new int[nSuits];
                l[0] = first;
                System.arraycopy(rest, 0, l, 1, rest.length);
                res.add(l);
            }
        }
        return res;
    }

    static String hand(List<String> suits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < suits.size(); ++i) {
            if (suits.get(i).isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(SUITS.charAt(i)).append(suits.get(i));
        }
        return sb.toString();
    }
}
