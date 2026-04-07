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
 * Copyright (C) 2025-2026 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 12/22/2024.
 */
package com.ab.util;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Config;
import com.ab.jpref.engine.GameManager;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Util {
    public static final String PROJECT_NAME = Config.PROJECT_NAME;
    public static final String DEAL_MARK = "deal:";

    public enum OS {
        linux,
        mac,
        windows,
        unknown
    }
    final OS os = getOS();

    public static final Random myRand = new Random();

    private static Util instance;

    public static Util getInstance() {
        if (instance == null) {
            instance = new Util();
        }
        return instance;
    }

    protected Util() {}

    public OS getOS() {
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

    public InputStream openInputStream(String path) throws FileNotFoundException {
        String dir = getDataDirectory();
        return new FileInputStream(new File(dir, path));
    }

    public OutputStream openOutputStream(String path) throws FileNotFoundException {
        String dir = getDataDirectory();
        return new FileOutputStream(new File(dir, path));
    }

    public String getDataDirectory() {
        throw new RuntimeException("stub!");
    }

/*
    public String getDataDirectory() {
        String parent;
        if (os == Util.OS.windows) {
            String userHome = System.getProperty("user.home");
            File f = new File(userHome, Config.PROJECT_NAME);
            f.mkdirs();
            parent = f.getAbsolutePath();
        } else {
            try {
                parent = new File(GameManager.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        File dir = new File(parent);
        dir.mkdirs();
        return dir.getAbsolutePath();
    }
*/
/*
    public String getDataDirectory() {
        return getDataFile().getAbsolutePath();
    }

    public File getDataFile() {
        File file;
        if (os == Util.OS.windows) {
            String userHome = System.getProperty("user.home");
            file = new File(userHome, PROJECT_NAME);
            if (!file.exists()) {
                file.mkdirs();
            }
        } else {
            try {
                file = new File(GameManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                file = new File(file.getParent());
                file.mkdirs();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return file;
    }
*/

    public void getList(String filePath, LineHandler lineHandler) throws IOException {
        final String[] charMap = {
            Card.ANSI_HEAD + ".*?" + Card.ANSI_TAIL + "->", // strip "\u001B.*?m"
        };
        getList(filePath, charMap, lineHandler);
    }

    // charMap format "from->to"
    public void getList(String filePath, String[] charMap, LineHandler lineHandler) throws IOException {
        File f = new File(filePath);
        String s = f.getAbsolutePath();
        try (BufferedReader reader =
                 new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(filePath))))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                Logger.println(line);
                String[] parts = line.split(" -> ");
                String res = "...";
                if (parts.length >= 2) {
                    res = parts[1];
                }
                String src = parts[0];
                if (charMap != null) {
                    for (String replace : charMap) {
                        String[] translate = replace.split("->");
                        String result = "";
                        if (translate.length > 1) {
                            result = translate[1];
                        }
                        src = src.replaceAll(translate[0], result);
                    }
                }
                if (src.isEmpty()) {
                    continue;
                }
                String[] tokens = src.split(" ");
                List<String> strings = new ArrayList<>();
                for (String t : tokens) {
                    if (t.isEmpty()) {
                        continue;
                    }
                    strings.add(t);
                }
                lineHandler.handleLine(res, strings);
            }
        }
    }

    public CardList toCardList(String src) {
        CardList cards = new CardList();
        if (src.isEmpty()) {
            return cards;
        }
        final String suits = "♠♣♦♥";
        String suit = null;
        int i = 0;
        while (i < src.length()) {
            char ch = src.charAt(i);
            if (ch == ' ') {
                ++i;
                continue;
            }
            int suitIndex = suits.indexOf(ch);
            if (suitIndex >= 0) {
                suit = "" + Card.Suit.values()[suitIndex].getCode();
                ++i;
                continue;
            }
            if (suit != null) {
                String cardName = suit + src.charAt(i);
                cards.add(Card.fromName(cardName));
                ++i;
                continue;
            }

            if (i >= src.length() - 1) {
                break;
            }
            cards.add(Card.fromName(src.substring(i, i + 2)));
            i += 2;
        }
        return cards;
    }

    public interface LineHandler {
        void handleLine(String res, List<String> tokens);
    }

    public int nextRandInt(int max) {
        return myRand.nextInt(max);
    }

    public void sleep(int timeout) {
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static String currMethodName() {
        return Thread.currentThread().getStackTrace()[2].getMethodName();
    }
}