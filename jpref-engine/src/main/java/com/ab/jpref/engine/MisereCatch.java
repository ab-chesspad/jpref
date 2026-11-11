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
 * Created: 10/15/2026
 */
package com.ab.jpref.engine;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Misère catch probabilities for one suit, the declarer's cards against all the splits
 * of the other cards of the suit between the defenders, 10 cards each out of 20 unknown,
 * none of the suit dropped. Each split is played out with open cards, the defenders pass
 * the lead freely, either of them leads any card of the suit after any trick.
 * lead: the declarer leads first, blind, any card of the suit or none.
 * carry: once a defender may throw away one card of the suit (пронос).
 * strict (-s): no free lead transfer, the winner of a trick leads next or the suit is not led any more;
 * other: the left or the right defender leads first, lead: the declarer leads first, if he does not lead
 * the suit, the worse of the two defenders on lead.
 * usage: MisereCatch [-s] [-v] 7J 79Q ...  -v lists the caught splits, no carry
 */
public class MisereCatch {
    static final String RANKS = "789XJQKA";
    static final int D = 0, L = 1, R = 2;
    static final int FREE = 3;      // either defender leads
    static final int HAND_SIZE = 10, UNKNOWN = 20;

    private final Map<Integer, Boolean> memo = new HashMap<>();
    private final Map<Integer, Integer> defendMemo = new HashMap<>();
    private final Map<Integer, Integer> declarerLeadMemo = new HashMap<>();
    private final boolean strict;

    MisereCatch(boolean strict) {
        this.strict = strict;
    }

    // ‰ to catch every holding, indexed by the rank bitmap, 7 is bit 0;
    // free lead transfer, one carry, the defenders on lead: no card is known yet
    static int[] table() {
        int[] res = new int[1 << RANKS.length()];
        if (STORED != null) {
            for (int d = 1; d < res.length; ++d) {
                res[d] = STORED[0][d];
            }
            return res;
        }
        MisereCatch misereCatch = new MisereCatch(false);
        for (int d = 1; d < res.length; ++d) {
            res[d] = (int) Math.round(misereCatch.prob(d, 1)[1] * MisereBot.MAX_EVAL);
        }
        return res;
    }

    // the evaluations precomputed by jpref-data, a slow phone took 10+ sec to compute them on demand:
    // {‰ to be caught, ‰ of the expected tricks} by r | g << 8, the rank bitmaps of the declarer's
    // cards and of the dropped ones, -1 when not stored; null without the resource
    public static final String STORED_RESOURCE = "jpref/misere-suits";
    static final int[][] STORED = load();

    private static int[][] load() {
        ClassLoader classloader = MisereCatch.class.getClassLoader();
        try (InputStream is = classloader.getResourceAsStream(STORED_RESOURCE)) {
            if (is == null) {
                return null;
            }
            int[][] res = new int[2][1 << 16];
            Arrays.fill(res[0], -1);
            Arrays.fill(res[1], -1);
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                String[] tokens = line.trim().split(" ");
                if (tokens.length != 4) {
                    continue;
                }
                int key = Integer.parseInt(tokens[0]) | Integer.parseInt(tokens[1]) << 8;
                res[0][key] = Integer.parseInt(tokens[2]);
                res[1][key] = Integer.parseInt(tokens[3]);
            }
            return res;
        } catch (Exception e) {
            return null;
        }
    }

    // every holding r of the declarer, every dropped g of the other ranks: r g ‰caught ‰tricks,
    // g = 0 as in table() and trickTable()
    public static void save(PrintStream pr) {
        MisereCatch misereCatch = new MisereCatch(false);
        for (int r = 1; r < 1 << RANKS.length(); ++r) {
            pr.printf("%d %d %d %d\n", r, 0,
                Math.round(misereCatch.prob(r, 1)[1] * MisereBot.MAX_EVAL),
                Math.round(misereCatch.expectedTricks(r) * MisereBot.MAX_EVAL));
            int rest = ~r & 0xff;
            for (int g = rest; g != 0; g = (g - 1) & rest) {
                pr.printf("%d %d %d %d\n", r, g,
                    Math.round(misereCatch.prob(r, g, 1)[1] * MisereBot.MAX_EVAL),
                    Math.round(misereCatch.expectedTricks(r, g) * MisereBot.MAX_EVAL));
            }
        }
    }

    // ‰ of the declarer's expected tricks for every holding, the same model as table(),
    // the defenders take as many tricks as they can, the declarer may lead the suit after winning a trick
    static int[] trickTable() {
        int[] res = new int[1 << RANKS.length()];
        if (STORED != null) {
            for (int d = 1; d < res.length; ++d) {
                res[d] = STORED[1][d];
            }
            return res;
        }
        MisereCatch misereCatch = new MisereCatch(false);
        for (int d = 1; d < res.length; ++d) {
            res[d] = (int) Math.round(misereCatch.expectedTricks(d) * MisereBot.MAX_EVAL);
        }
        return res;
    }

    public static void main(String[] args) {
        boolean verbose = false;
        boolean strict = false;
        for (String arg : args) {
            if (arg.equals("-v")) {
                verbose = true;
                continue;
            }
            if (arg.equals("-s")) {
                strict = true;
                continue;
            }
            MisereCatch misereCatch = new MisereCatch(strict);
            int d = bitmap(arg);
            if (verbose) {
                misereCatch.show(d);
                continue;
            }
            double[] p0 = misereCatch.prob(d, 0);
            double[] p1 = misereCatch.prob(d, 1);
            if (strict) {
                System.out.printf("%-8s lead %6.1f other L %6.1f R %6.1f   with carry: lead %6.1f other L %6.1f R %6.1f\n",
                    arg, p0[0] * 1000, p0[1] * 1000, p0[2] * 1000, p1[0] * 1000, p1[1] * 1000, p1[2] * 1000);
            } else {
                System.out.printf("%-8s lead %6.1f other %6.1f   with carry: lead %6.1f other %6.1f\n",
                    arg, p0[0] * 1000, p0[1] * 1000, p1[0] * 1000, p1[1] * 1000);
            }
        }
    }

    static int bitmap(String cards) {
        int res = 0;
        for (char c : cards.toCharArray()) {
            res |= 1 << RANKS.indexOf(c);
        }
        return res;
    }

    static String toString(int bitmap) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < RANKS.length(); ++i) {
            if ((bitmap & 1 << i) != 0) {
                sb.append(RANKS.charAt(i));
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    // {lead, other}, strict: {lead, other with L on lead, other with R on lead}
    double[] prob(int d, int carry) {
        return prob(d, 0, carry);
    }

    // gone: the ranks out of play, the declarer's drop, the defenders do not hold them
    double[] prob(int d, int gone, int carry) {
        double[] other = new double[3];
        for (int leader = L; leader <= R; ++leader) {
            for (int[] split : splits(d, gone)) {
                if (caught(d, split[0], split[1], carry, strict ? leader : FREE)) {
                    other[leader] += weight(d, gone, split[0]);
                }
            }
        }
        double lead = Math.max(other[L], other[R]);
        for (int c = 0; c < RANKS.length(); ++c) {
            if ((d & 1 << c) == 0) {
                continue;
            }
            lead = Math.min(lead, leadProb(d, gone, c, carry));
        }
        return strict ? new double[] {lead, other[L], other[R]} : new double[] {lead, other[L]};
    }

    // I lead card, the rank index, from d
    double leadProb(int d, int gone, int card, int carry) {
        double p = 0;
        for (int[] split : splits(d, gone)) {
            int[] hands = {d & ~(1 << card), split[0], split[1]};
            if (play(hands, D, 1, card, D, carry)) {
                p += weight(d, gone, split[0]);
            }
        }
        return p;
    }

    // the caught splits with the defenders' first lead
    void show(int d) {
        double total = 0;
        for (int[] split : splits(d)) {
            int l = split[0], r = split[1];
            if (!caught(d, l, r, 0, strict ? L : FREE)) {
                continue;
            }
            double w = weight(d, l);
            total += w;
            System.out.printf("L %-7s R %-7s p=%6.2f‰  %s\n", toString(l), toString(r), w * 1000, firstLead(d, l, r));
        }
        System.out.printf("total %.1f‰\n", total * 1000);
    }

    private String firstLead(int d, int l, int r) {
        int[] hands = {d, l, r};
        for (int leader = L; leader <= (strict ? L : R); ++leader) {
            for (int c = 0; c < RANKS.length(); ++c) {
                if ((hands[leader] & 1 << c) != 0 && trick(hands, leader, c, 0)) {
                    return (leader == L ? "L" : "R") + " leads " + RANKS.charAt(c);
                }
            }
        }
        return "carry";
    }

    // the left defender's hands, the right one gets the rest
    static int[][] splits(int d) {
        return splits(d, 0);
    }

    static int[][] splits(int d, int gone) {
        int out = ~d & ~gone & 0xff;
        int n = 1 << Integer.bitCount(out);
        int[][] res = new int[n][];
        int i = 0;
        // all the subsets of out
        for (int l = out; ; l = (l - 1) & out) {
            res[i++] = new int[] {l, out & ~l};
            if (l == 0) {
                break;
            }
        }
        return res;
    }

    // probability of one exact split
    static double weight(int d, int l) {
        return weight(d, 0, l);
    }

    static double weight(int d, int gone, int l) {
        int m = 8 - Integer.bitCount(d | gone);
        int j = Integer.bitCount(l);
        return comb(UNKNOWN - m, HAND_SIZE - j) / comb(UNKNOWN, HAND_SIZE);
    }

    static double comb(int n, int k) {
        if (k < 0 || k > n) {
            return 0;
        }
        double res = 1;
        for (int i = 1; i <= k; ++i) {
            res = res * (n - k + i) / i;
        }
        return res;
    }

    // the declarer's tricks over all the splits, with one carry
    double expectedTricks(int d) {
        return expectedTricks(d, 0);
    }

    double expectedTricks(int d, int gone) {
        double res = 0;
        for (int[] split : splits(d, gone)) {
            res += weight(d, gone, split[0]) * defend(d, split[0], split[1], 1);
        }
        return res;
    }

    // the most tricks the defenders can give, they lead any card from either hand, carry one or stop
    private int defend(int d, int l, int r, int carry) {
        if (d == 0) {
            return 0;
        }
        int key = d | l << 8 | r << 16 | carry << 24;
        Integer res = defendMemo.get(key);
        if (res != null) {
            return res;
        }
        int best = 0;
        int[] hands = {d, l, r};
        for (int p = L; p <= R; ++p) {
            for (int c = 0; c < RANKS.length(); ++c) {
                if ((hands[p] & 1 << c) == 0) {
                    continue;
                }
                int[] h = hands.clone();
                h[p] &= ~(1 << c);
                best = Math.max(best, tricks(h, p, 1, c, p, carry));
                if (carry > 0) {
                    best = Math.max(best, defend(h[D], h[L], h[R], 0));
                }
            }
        }
        defendMemo.put(key, best);
        return best;
    }

    // the declarer on lead leads the suit or another one
    private int declarerLead(int d, int l, int r, int carry) {
        int key = d | l << 8 | r << 16 | carry << 24;
        Integer res = declarerLeadMemo.get(key);
        if (res != null) {
            return res;
        }
        int best = defend(d, l, r, carry);
        for (int c = 0; c < RANKS.length(); ++c) {
            if ((d & 1 << c) != 0) {
                best = Math.min(best, tricks(new int[] {d & ~(1 << c), l, r}, D, 1, c, D, carry));
            }
        }
        declarerLeadMemo.put(key, best);
        return best;
    }

    // k cards played to the trick, top is the highest so far, played by topP
    private int tricks(int[] hands, int leader, int k, int top, int topP, int carry) {
        if (k == 3) {
            if (topP == D) {
                return 1 + declarerLead(hands[D], hands[L], hands[R], carry);
            }
            return defend(hands[D], hands[L], hands[R], carry);
        }
        int p = (leader + k) % 3;
        if (hands[p] == 0) {
            return tricks(hands, leader, k + 1, top, topP, carry);   // a discard
        }
        int best = p == D ? Integer.MAX_VALUE : 0;
        for (int c = 0; c < RANKS.length(); ++c) {
            if ((hands[p] & 1 << c) == 0) {
                continue;
            }
            int[] h = hands.clone();
            h[p] &= ~(1 << c);
            int v = c > top ? tricks(h, leader, k + 1, c, p, carry) : tricks(h, leader, k + 1, top, topP, carry);
            best = p == D ? Math.min(best, v) : Math.max(best, v);
        }
        return best;
    }

    // the defenders to lead, they choose what and, unless strict, who
    boolean caught(int d, int l, int r, int carry, int onLead) {
        if (d == 0) {
            return false;
        }
        int key = d | l << 8 | r << 16 | carry << 24 | onLead << 25;
        Boolean res = memo.get(key);
        if (res != null) {
            return res;
        }
        res = false;
        int[] hands = {d, l, r};
search:
        for (int leader = L; leader <= R; ++leader) {
            if (onLead != FREE && leader != onLead) {
                continue;
            }
            for (int c = 0; c < RANKS.length(); ++c) {
                if ((hands[leader] & 1 << c) != 0 && trick(hands, leader, c, carry)) {
                    res = true;
                    break search;
                }
            }
        }
        if (!res && carry > 0) {
carry:
            for (int p = L; p <= R; ++p) {
                for (int c = 0; c < RANKS.length(); ++c) {
                    if ((hands[p] & 1 << c) != 0) {
                        int[] h = hands.clone();
                        h[p] &= ~(1 << c);
                        if (caught(h[D], h[L], h[R], 0, onLead)) {
                            res = true;
                            break carry;
                        }
                    }
                }
            }
        }
        memo.put(key, res);
        return res;
    }

    private boolean trick(int[] hands, int leader, int card, int carry) {
        int[] h = hands.clone();
        h[leader] &= ~(1 << card);
        return play(h, leader, 1, card, leader, carry);
    }

    // k cards played to the trick, top is the highest so far, played by topP
    private boolean play(int[] hands, int leader, int k, int top, int topP, int carry) {
        if (k == 3) {
            return topP == D || caught(hands[D], hands[L], hands[R], carry, strict ? topP : FREE);
        }
        int p = (leader + k) % 3;
        if (hands[p] == 0) {
            return play(hands, leader, k + 1, top, topP, carry);   // a discard
        }
        for (int c = 0; c < RANKS.length(); ++c) {
            if ((hands[p] & 1 << c) == 0) {
                continue;
            }
            int[] h = hands.clone();
            h[p] &= ~(1 << c);
            boolean v = c > top ? play(h, leader, k + 1, c, p, carry) : play(h, leader, k + 1, top, topP, carry);
            if (p == D && !v) {
                return false;   // the declarer escapes
            }
            if (p != D && v) {
                return true;    // the defenders catch
            }
        }
        return p == D;
    }
}
