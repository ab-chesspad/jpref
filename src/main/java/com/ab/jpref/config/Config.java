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
/**
 * https://en.wikipedia.org/wiki/Preferans
 * terms:
 * move  - the process of a player placing the card on the table;
 * trick - as usual, the set of cards played by every player;
 * elder hand - the player on the left of the dealer, starts the bidding and is first to move;
 * declarer - the player that has made the highest bid and thus declaring the game;
 * round - the process of all the players have played all their cards;
 * game - the set of rounds until the end (until all players have the pre-agreed score in their pools);
 *
 */
package com.ab.jpref.config;

import com.ab.jpref.engine.Player;
import com.ab.pref.Main;
import com.ab.util.I18n;
import com.ab.util.Logger;
import com.ab.util.Util;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.*;
import java.net.URISyntaxException;

public class Config implements Serializable {
    public final Property<IntTriplet> animDelay = new Property<>("Animation Time", new IntTriplet(100,1,200));
    public final Property<String> gameType = new Property<>("Game Type", "Miami");
    public final Property<Integer> poolSize = new Property<>("Pool Length", 10);
    public final Property<String> playerNames = new Property<>("Player's Names", "Ann, Bob, Cat");
    public final Property<Integer> sleepBetweenRounds = new Property<>("After Round Timeout", 100);

    public static final String PROJECT_NAME = "jpref";
    private static final String CONFIG_FILENAME = PROJECT_NAME + ".config";

    public enum Bid implements Comparable<Bid>, Player.Queueable {
        BID_WHIST(40, "whist"),
        BID_HALF_WHIST(41, "half"),
        BID_WITHOUT_THREE(42, "xx"),

        BID_UNDEFINED(50, "---"),     // before actual bidding
        BID_ALL_PASS(55, "all-pass"),
        BID_PASS(60, "pass"),
        BID_6S(61, "6♠"),
        BID_6C(62, "6♣"),
        BID_6D(63, "6♦"),
        BID_6H(64, "6♥"),
        BID_6N(65, "6-"),
        
        BID_7S(71, "7♠"),
        BID_7C(72, "7♣"),
        BID_7D(73, "7♦"),
        BID_7H(74, "7♥"),
        BID_7N(75, "7-"),

        BID_8S(81, "8♠"),
        BID_8C(82, "8♣"),
        BID_8D(83, "8♦"),
        BID_8H(84, "8♥"),
        BID_8N(85, "8-"),

        BID_MISERE(86, "misere"),

        BID_9S(91, "9♠"),
        BID_9C(92, "9♣"),
        BID_9D(93, "9♦"),
        BID_9H(94, "9♥"),
        BID_9N(95, "9-"),

        BID_XS(101, "10♠"),
        BID_XC(102, "10♣"),
        BID_XD(103, "10♦"),
        BID_XH(104, "10♥"),
        BID_XN(105, "10-"),
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

        static public Bid fromName(String name) {
            for (Bid r : values()) {
                if (r.name.equals(name)) {
                    return r;
                }
            }
            return null;
        }

        static public Bid fromValue(int value) {
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

/*
        @Override
        public int compareTo(Bid o) {
            // compare for sorting
            if (o == null) return 1;

            return 0;
        }
*/
    }

    public static final int MAX_DISTANCE_TO_TOP = 7;

    final protected PropertyChangeSupport changes = new PropertyChangeSupport(this);
    final static Util.OS os = Util.getOS();

    protected static Object instance;

    public static Config getInstance() {
        if (instance == null) {
            instance = Config.unserialize();
        }
        if (instance == null) {
            instance = new Config();
        }
        return (Config)instance;
    }

/*
    public static class InstanceHolder {
        protected static Object instance = Config.unserialize();
    }

    public static Config getInstance() {
        if (InstanceHolder.instance == null) {
            InstanceHolder.instance = new Config();
        }
        return (Config)InstanceHolder.instance;
    }
*/

    protected Config() {}

    public void addPropertyChangeListener(PropertyChangeListener l) {
        changes.addPropertyChangeListener(l);
    }

    public static File getDataDirectory() {
        String parent;
        if (os == Util.OS.windows) {
            String userHome = System.getProperty("user.home");
            File f = new File(userHome, PROJECT_NAME);
            f.mkdirs();
            parent = f.getAbsolutePath();
        } else {
            try {
                parent = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        File dir = new File(parent);
        dir.mkdirs();
        return dir;
    }

    protected static Object unserialize() {
        Object object = null;
        File dir = getDataDirectory();
        File configFile = new File(dir, CONFIG_FILENAME);
        try (FileInputStream file = new FileInputStream(configFile);
             ObjectInputStream in = new ObjectInputStream(file)) {
            object = in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            Logger.println(e.getMessage());
        }
        return object;
    }

    public void serialize() {
        File dir = getDataDirectory();
        File configFile = new File(dir, CONFIG_FILENAME);
        try (FileOutputStream file = new FileOutputStream(configFile);
             ObjectOutputStream out = new ObjectOutputStream(file)) {
            out.writeObject(this);
        } catch (IOException e) {
            Logger.println(e.getStackTrace().toString());
        }
    }

    // not sure that I'll need it
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

    public static class Property<T> implements Serializable {
        private final String label;
        private T value;

        public Property(String label, T value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() {
            return I18n.m(label);
        }

        public void set(T value) {
            this.value = value;
        }

        public T get() {
            return value;
        }
    }

}
