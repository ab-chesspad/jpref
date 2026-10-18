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
    private final boolean strict;

    MisereCatch(boolean strict) {
        this.strict = strict;
    }

    // ‰ to catch every holding, indexed by the rank bitmap, 7 is bit 0;
    // free lead transfer, one carry, the defenders on lead: no card is known yet
    static int[] table() {
        MisereCatch misereCatch = new MisereCatch(false);
        int[] res = new int[1 << RANKS.length()];
        for (int d = 1; d < res.length; ++d) {
            res[d] = (int) Math.round(misereCatch.prob(d, 1)[1] * MisereBot.MAX_EVAL);
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
        double[] other = new double[3];
        for (int leader = L; leader <= R; ++leader) {
            for (int[] split : splits(d)) {
                if (caught(d, split[0], split[1], carry, strict ? leader : FREE)) {
                    other[leader] += weight(d, split[0]);
                }
            }
        }
        double lead = Math.max(other[L], other[R]);
        for (int c = 0; c < RANKS.length(); ++c) {
            if ((d & 1 << c) == 0) {
                continue;
            }
            lead = Math.min(lead, leadProb(d, c, carry));
        }
        return strict ? new double[] {lead, other[L], other[R]} : new double[] {lead, other[L]};
    }

    // I lead card, the rank index, from d
    double leadProb(int d, int card, int carry) {
        double p = 0;
        for (int[] split : splits(d)) {
            int[] hands = {d & ~(1 << card), split[0], split[1]};
            if (play(hands, D, 1, card, D, carry)) {
                p += weight(d, split[0]);
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
        int out = ~d & 0xff;
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
        int m = 8 - Integer.bitCount(d);
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
