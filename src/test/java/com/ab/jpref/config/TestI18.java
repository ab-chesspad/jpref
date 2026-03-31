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
 * Created: 2/28/2025
 */
package com.ab.jpref.config;

//import com.ab.pref.config.PConfig;
import com.ab.util.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Ignore
public class TestI18 {
    public static Config config;

    @Before
    public void initClass() {
        config = Config.getInstance();
    }

    @Test
    public void testMap() {
        String[] sources = {
//            "Hooray ?->ура!",
            "What would you like to do?->Что вы хотите делать?",
            "½ Whist->½ виста",
            "Without 3->без трех",
            "Without three->без трех",
            "Pass *2->пас *2",
            "Ann, Bob, Cat->Аня, Боря, Витя",
            "Player's Names->Имена игроков",
            "The file %s has been uploaded->Файл %s послан",
            "Misère -> мизер",
        };

        config.language.get().setSelected(1);  // russian
        I18n.refresh();
        for (String source : sources) {
            Logger.println(source);
            String[] parts = source.split("\\s*->\\s*");
            String text = parts[0];
            Pattern p0 = Pattern.compile("([^*]+)");
            Matcher m0 = p0.matcher(text);
            int start = 0;
            StringBuilder sb = new StringBuilder();
            while (m0.find()) {
                String chunk = m0.group();
                sb.append(text.substring(start, m0.start()));
                if (chunk.startsWith(" ")) {
                    sb.append(" ");
                }
                String trailing = "";
                if (chunk.endsWith(" ")) {
                    trailing = " ";
                }
                sb.append(I18n.m(chunk.trim())).append(trailing);
                start = m0.end();
            }
            sb.append(text.substring(start));
            String res = sb.toString();
            String expectedRes = parts[1];
            Assert.assertEquals(expectedRes, res);
        }
        Logger.println("done");
    }
}