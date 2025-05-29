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
 * Created: 12/22/2024
 */

package com.ab.jpref.cards;

import com.ab.jpref.engine.Player;

import java.util.Objects;

public class Card implements Comparable<Card>, Player.Queueable {
    public static String ANSI_RED = "\u001B[31m";
    public static String ANSI_RESET = "\u001B[0m";

    static {
        boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().
            getInputArguments().toString().contains("jdwp");
        if (isDebug) {
//            ANSI_RED = ANSI_RESET = "";
        }
    }

    static int i = -1;
    public enum Suit {
        SPADE('♠', ++i),
        CLUB('♣', ++i),
        DIAMOND('♦', ++i),
        HEART('♥', ++i),
        NO_SUIT('-', ++i);

        public char getCode() {
            return code;
        }

        public int getValue() {
            return value;
        }

        private final char code;
        private final int value;

        Suit(char code, int value) {
            this.code = code;
            this.value = value;
        }

        @Override
        public String toString() {
/*  IntelliJ debugger does not handle ansi colors, so I make a special method for logging output
            if (this.equals(DIAMOND) || this.equals(HEART)) {
                return ANSI_RED + String.valueOf(code) + ANSI_RESET;
            }
//*/
            return String.valueOf(code);
        }

        public String toColorString() {
/*  IntelliJ debugger does not handle ansi colors, so I make a special method for logging output */
            if (this.equals(DIAMOND) || this.equals(HEART)) {
                return ANSI_RED + code + ANSI_RESET;
            }
            return String.valueOf(code);
        }

        static public Suit fromCode(char code) {
            for (Suit r : values()) {
                if (r.code == code) {
                    return r;
                }
            }
            throw new IllegalArgumentException(String.format("value for suit '%c' (0x%x)", code, (int)code));
        }

        static public Suit code(char unicode) {
            for (Suit r : values()) {
                if (r.code == unicode) {
                    return r;
                }
            }
            throw new IllegalArgumentException(String.valueOf(unicode));
        }
    }

    static int j = 5;
    public enum Rank {
        SIX("6", ++j),      // fictitious card to start all-pass
        SEVEN("7", ++j),
        EIGHT("8", ++j),
        NINE("9", ++j),
        TEN("X", ++j),
        JACK("J", ++j),
        QUEEN("Q", ++j),
        KING("K", ++j),
        ACE("A", ++j);

        private final String name;
        private final int value;

        Rank(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            return name;
        }

        static public Rank fromName(char name) {
            for (Rank r : values()) {
                if (r.name.equalsIgnoreCase("" + name)) {
                    return r;
                }
            }
            throw new IllegalArgumentException("" + name);
        }

        static public Rank fromName(String name) {
            for (Rank r : values()) {
                if (r.name.equalsIgnoreCase(name)) {
                    return r;
                }
            }
            throw new IllegalArgumentException(name);
        }

        static public Rank fromValue(int value) {
            return values()[value - SIX.value];
        }

        public int compare(Rank o) {
            // compare for sorting
            if (o == null) return 1;
            return this.value - o.value;
        }

    }

    private final Suit suit;
    private final Rank rank;

    public Card(Suit suit, Rank rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public Card(String cardName) {
        this.suit = Suit.fromCode(Character.toLowerCase(cardName.charAt(0)));
        this.rank = Rank.fromName(Character.toLowerCase(cardName.charAt(1)));
    }

    public Suit getSuit() {
        return suit;
    }

    public Rank getRank() {
        return rank;
    }

    public Card clone() {
        return new Card(this.getSuit(), this.getRank());
    }

    @Override
    public int compareTo(Card o) {
        // compare for sorting
        if (o == null) return 1;

        if (suit.compareTo(o.suit) == 0) {
            return rank.compareTo(o.rank);
        } else {
            return suit.compareTo(o.suit);
        }
    }

    public int compareInTrick(Card o) {
        if (o == null) {
            return 1;
        }
        if (suit.compareTo(o.suit) == 0) {
            return rank.compareTo(o.rank);
        } else {
            return 1;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        Card card = (Card) obj;

        return Objects.equals(card.rank, rank) &&
                Objects.equals(card.suit, suit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(suit, rank);
    }

    @Override
    public String toString() {
        return suit.toString() + rank.toString();
    }

    public String toColorString() {
        Suit suit = this.getSuit();
        String s;
        if (suit.equals(Suit.DIAMOND) || suit.equals(Suit.HEART)) {
            s = Card.ANSI_RED + suit.toString() + rank.toString() + Card.ANSI_RESET;
        } else {
            s = suit.toString() + rank.toString();
        }
        return s;
    }
}
