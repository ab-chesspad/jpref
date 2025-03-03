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
 * Created: 2/28/2025
 */
package com.ab.pref;

import com.ab.util.Logger;
import org.junit.Test;

public class TestRegex {

    @Test
    public void testHead() {
        String s = "<html>\n" +
                "<head><meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\"></head><body>\n" +
                "<font size=\"6\"><b>JPref V 0.0.0.1</b></font><br/><br/>\n" +
                "Целью этого проекта является создание программы, играющей в";
        String t = s.replaceAll("<head.*?head>", "");
        Logger.println(t);
    }
}
