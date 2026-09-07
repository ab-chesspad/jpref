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
 * Created: 12/22/2024
 *
 *
 * https://en.wikipedia.org/wiki/Preferans
 * terms:
 * move  - the process of a player placing the card on the table;
 * trick - as usual, the set of cards played by every player;
 * elder hand - the player on the left of the dealer, starts the bidding and is first to move;
 * declarer - the player that has made the highest bid and thus declaring the game;
 * defenders - the players that play against declarer;
 * round - the process of all the players have played all their cards;
 * game - the set of rounds until the end (until all players have the pre-agreed score in their pools);
 *
 */
package com.ab.jpref.config;

import com.ab.jpref.cards.Card;
import com.ab.jpref.engine.GameManager;
import com.ab.jpref.ui.Host;
import com.ab.util.Couple;
import com.ab.util.Point;
import com.ab.util.Util;

import java.io.*;
import java.util.Locale;

public abstract class Config implements Serializable {
    private static final long serialVersionUID = 11L;

    public static final String PROJECT_NAME = "JPref";
    public static final String VERSION = "0.1.1";

    public final Property<Boolean> release = new Property<>("", true);

    public transient GameManager.EventObserver eventObserver;
    public final Point mainPosition = new Point(0, 0);
    public final Couple<Integer> mainSize = new Couple<>(0, 0);
    public int insetsTop;

    public final Property<Selection<Couple<String>>> language =
        new Property<>("Language", true, new Selection<>(
            new Couple<>("English", "en"),
            new Couple<>("Русский", "ru"),
            new Couple<>("Олбанский", "ol")   // nerd's joke
        ));

    public enum GameType implements Selectable<GameType> {
        Miami,
//        Peter,    // todo
        ;
        @Override
        public GameType[] getAll() {
            return values();
        }
    }

    public final Property<Selection<GameType>> gameType =
        new Property<>("Game Type", new Selection<>(GameType.values()));

    public final Property<Integer> poolSize = new Property<>("Pool Size", 20);

    public enum WhistType implements Selectable<WhistType> {
        Redneck,
//        Gentleman,  // todo
        ;
        @Override
        public WhistType[] getAll() {
            return values();
        }
    }
    public final Property<Selection<WhistType>> whistType =
        new Property<>("Whist Type", new Selection<>(WhistType.values()));

    public enum MoveMethod implements Selectable<MoveMethod> {
        SingleClick,
        DoubleClick,
        Dragging,
        ;
        @Override
        public MoveMethod[] getAll() {
            return values();
        }
    }
    public final Property<Selection<MoveMethod>> moveMethod =
        new Property<>("Move Method", new Selection<>(MoveMethod.values()));

    public final Property<Integer> pauseBetweenTricks = new Property<>("Pause between tricks, msec", 500);
    public final Property<Integer> pauseBetweenMoves = new Property<>("Pause between moves, msec", 100);

    public final Property<Integer> pauseBetweenRounds = new Property<>("", 100);
//    public final Property<IntTriplet> animDelay = new Property<>("Animation Timeout", new IntTriplet(100, 1, 200));
//    public final Property<Integer> animDelay = new Property<>("", 100);

    public final Property<Integer> deleteLogsAfter = new Property<>("Delete logs after, days", 1);

    public static final int NOP = 3;    // Number of players
    public static final int ROUND_SIZE = 10;    // total tricks == initial hand size
    private static final String CONFIG_FILENAME = PROJECT_NAME + ".config";
    public static final char NO_TRUMP = '-';
    public final String GUID;

    protected transient Host host;

    protected Config(Host host) {
        this.host = host;
        GUID = java.util.UUID.randomUUID().toString();
    }

    public static Config unserialize(Host host) {
        Config config = null;
        try (FileInputStream fis = new FileInputStream(new File(host.getDataDirectory(), CONFIG_FILENAME));
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            config = (Config)ois.readObject();
            config.host = host;
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(e.getMessage());
        }
        return config;
    }

    public void serialize() {
        try (FileOutputStream fos = new FileOutputStream(new File(host.getDataDirectory(), CONFIG_FILENAME));
                ObjectOutputStream oot = new ObjectOutputStream(fos) ) {
            oot.writeObject(this);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public interface Queueable {}

    // not sure if I'll need it
    public static class IntTriplet implements Serializable {
        private int min, max, value;

        public IntTriplet(int value, int min, int max) {
            this.min = min;
            this.max = max;
            this.value = value;
        }

        public int getMin() {
            return min;
        }

        public void setMin(int min) {
            this.min = min;
        }

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    public static class Selection<T> implements Serializable {
        private int selected = 0;
        public final T[] values;

        public int getSelected() {
            return selected;
        }

        public void setSelected(int selected) {
            this.selected = selected;
        }

        @SafeVarargs
        public Selection(T... values) {
            this.values = values;
        }

        public T getSelectedValue() {
            return values[selected];
        }
    }

    public interface Selectable<T> {
        T[] getAll();
    }

    public static class Property<T> implements Serializable {
        // Field initializers run top-to-bottom in declaration order (JLS-guaranteed),
        // so this sequence number reproduces declaration order without relying on
        // reflection enumeration order (unspecified by the JLS, and not preserved by
        // ART) or a separately maintained list that has to be updated by hand
        // whenever a property is added.
        private static int nextOrder = 0;
        private final int order = nextOrder++;

        private final String label;
        private final boolean visual;
        private T value;

        public Property(String label, T value) {
            this.label = label;
            this.visual = false;
            this.value = value;
        }

        public Property(String label, boolean visual, T value) {
            this.visual = visual;
            this.label = label;
            this.value = value;
        }

        public int getOrder() {
            return order;
        }

        public String getLabel() {
            return label;
        }

        public boolean isVisual() {
            return visual;
        }

        public void set(T value) {
            this.value = value;
        }

        public T get() {
            return value;
        }
    }

    public enum Bid implements Comparable<Bid>, Queueable {
        BID_UNDEFINED(30, "?"),     // before actual bidding
        BID_HALF_WHIST(39, "Half"),
        BID_WHIST(40, "Whist"),
        BID_WHIST_LAYING(41, "Whist"),
        BID_WHIST_STANDING(42, "Whist"),

        BID_PASS(58, "Pass"),
        BID_WITHOUT_THREE(59, "Without 3"),
        BID_ALL_PASS(60, "All-pass"),
        BID_6S(61, "6♠"),
        BID_6C(62, "6♣"),
        BID_6D(63, "6♦"),
        BID_6H(64, "6♥"),
        BID_6N(65, "6" + NO_TRUMP),

        BID_7S(71, "7♠"),
        BID_7C(72, "7♣"),
        BID_7D(73, "7♦"),
        BID_7H(74, "7♥"),
        BID_7N(75, "7" + NO_TRUMP),

        BID_8S(81, "8♠"),
        BID_8C(82, "8♣"),
        BID_8D(83, "8♦"),
        BID_8H(84, "8♥"),
        BID_8N(85, "8" + NO_TRUMP),

        BID_MISERE(86, "Misère"),

        BID_9S(91, "9♠"),
        BID_9C(92, "9♣"),
        BID_9D(93, "9♦"),
        BID_9H(94, "9♥"),
        BID_9N(95, "9" + NO_TRUMP),

        BID_XS(101, "X♠"),
        BID_XC(102, "X♣"),
        BID_XD(103, "X♦"),
        BID_XH(104, "X♥"),
        BID_XN(105, "X" + NO_TRUMP),
        ;

        private final int value;
        private final String name;

        Bid(int value, String name) {
            this.value = value;
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public String getName() {
            return name;
        }

        public static Bid fromName(String name) {
            for (Bid r : values()) {
                if (r.name.equals(name)) {
                    return r;
                }
            }
            return null;
        }

        public static Bid fromValue(int value) {
            for (Bid r : values()) {
                if (r.value == value) {
                    return r;
                }
            }
            return null;
        }

        public Bid next() {
            return values()[this.ordinal() + 1];
        }

        public Bid prev() {
            return values()[this.ordinal() - 1];
        }

        public Card.Suit getTrump() {
            int suitNum = this.value % 10;
            if (suitNum == 0 || suitNum >= 5) {
                return null;
            }
            return Card.Suit.fromValue(suitNum - 1);
        }

        public int goal() {
            if (this.equals(BID_MISERE)) {
                return 0;
            }
            return this.value / 10;
        }

        public int defenderGoal() {
            if (this.equals(BID_MISERE)) {
                return 1;
            }
            int v = this.value / 10;
            switch (v) {
                case 6:
                    return 4;
                case 7:
                    return 2;
                default:
                    return 1;
            }
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    public enum OS {
        linux,
        mac,
        windows,
        unknown
    }

    public static OS getOS() {
        OS os = OS.unknown;
        String osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
        if (osName.contains("nux")) {
            os = OS.linux;
        } else if ((osName.contains("mac")) || (osName.contains("darwin"))) {
            os = OS.mac;
        } else if ((osName.startsWith("windows"))) {
            os = OS.windows;
        }
        return os;
    }

    public String info() {
        String[] command = {"cat", "/proc/cpuinfo"};
        String cpuInfo = "?";
        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = stdInput.readLine()) != null) {
                if (line.startsWith("model name")) {
                    int index = line.indexOf(": ");
                    cpuInfo = line.substring(index + 2);
                    break;
                }
            }
        } catch (IOException e) {
            // ignore
        }

        int cores = Runtime.getRuntime().availableProcessors();
        String res = String.format("%s, #=%d\n", cpuInfo, cores);
        res += String.format("totalMemory=%,dMB, freeMemory=%,dMB",
                Runtime.getRuntime().totalMemory() / 1000000,
                Runtime.getRuntime().freeMemory() / 1000000);
        return res;
    }

    static String ansi_head = "\u001B";
    static String ansi_tail = "m";
    static String ansi_reset = ansi_head + "[0" + ansi_tail;
    static String ansi_red = ansi_head + "[31" + ansi_tail;
    public static String ANSI_HEAD = ansi_head;
    public static String ANSI_TAIL = ansi_tail;
    public static String ANSI_RESET = ansi_reset;
    public static String ANSI_RED = ansi_red;

    public static void setNonColoredLog() {
        ANSI_HEAD = ANSI_TAIL = ANSI_RESET = ANSI_RED = "";
    }

}