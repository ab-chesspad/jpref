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
 * Created: 1/2025/2025
 */
package com.ab.jpref.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

// todo: rethink language change
public class I18n {
    public static int maxPhraseLength = 50;
    private static I18n instance;

    private String iso639_1_2002_code;
    private LanguageMap languageMap;

    public static I18n getInstance() {
        if (instance == null) {
            instance = new I18n();
        }
        return instance;
    }

    private I18n() {
        instance = this;
        refresh();
    }

    public static void refresh() {
        String lang = Config.getInstance().language.get().getSelectedValue().second;
        instance.loadLanguageMap(lang);
    }

    public String getLang() {
        return iso639_1_2002_code;
    }

    public String translate(String text) {
        String res;
        if ((res = languageMap.get(text.toLowerCase())) == null || res.isEmpty()) {
            res = text;
        }
        return res;
    }

    public static String m(String text) {
        I18n instance = getInstance();
        return instance.translate(text);
    }

    private void loadLanguageMap(String iso639_1_2002_code) {
        this.iso639_1_2002_code = iso639_1_2002_code;
        languageMap = new LanguageMap();
        if ("en".equals(iso639_1_2002_code)) {
            return;
        }
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try (InputStream is = classloader.getResourceAsStream(String.format("i18n/%s/map", iso639_1_2002_code));
                BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\\s*->\\s*");
                if (parts.length < 2) {
                    // sanity check
                    continue;
                }
                if (maxPhraseLength < parts[0].length()) {
                    maxPhraseLength = parts[0].length();
                }
                if (maxPhraseLength < parts[1].length()) {
                    maxPhraseLength = parts[1].length();
                }
                languageMap.put(parts[0].toLowerCase(), parts[1]);
            }
        } catch (Exception e) {
            System.out.printf("error loading i18n file for %s\n", iso639_1_2002_code);
        }
    }

    public static String loadString(String path) {
//        Logger.println(path);
        StringBuilder sb = new StringBuilder();
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        path = String.format("i18n/%s/%s", getInstance().iso639_1_2002_code, path);
        try (InputStream is = classloader.getResourceAsStream(path);
                BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(" ");
//                sb.append(line).append(" \r");
            }
        } catch (Exception e) {
            System.out.printf("error loading resource %s\n", path);
        }
        return sb.toString();
    }

    static class LanguageMap extends HashMap<String, String> {}
}