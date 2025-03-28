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
 * Created: 1/25/2025
 */
package com.ab.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;

public class I18n {
    private static I18n instance;

    private String iso639_1_2002_code;
    private LanguageMap languageMap;

    private static I18n getInstance() {
        if (instance == null) {
            instance = new I18n();
        }
        return instance;
    }

    private I18n() {
        Locale locale = Locale.getDefault();
        loadLanguageMap(locale.getLanguage());
    }

    // using alpha-2 code
    private I18n(String iso639_1_2002_code) {
        if (!iso639_1_2002_code.equals(this.iso639_1_2002_code)) {
            loadLanguageMap(iso639_1_2002_code);
        }
    }

    public static String m(String text) {
        String res;
        I18n instance = getInstance();
        if ((res = instance.languageMap.get(text)) == null || res.isEmpty()) {
            res = text;
        }
        return res;
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
                String[] parts = line.split("\\s*->\\s*");
                languageMap.put(parts[0], parts[1]);
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
                sb.append(line).append("<br/>").append("\r");
            }
        } catch (Exception e) {
            System.out.printf("error loading resource %s\n", path);
        }
        return sb.toString();
    }

    static class LanguageMap extends HashMap<String, String> {
    }
}
