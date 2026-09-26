/*  This file is part of DPref project.
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
 * Created: 7/13/2026
 *
 */
package com.ab.droid.jpref.util;

import android.util.Log;

import java.io.PrintStream;

public class DLogger extends com.ab.util.Logger {
    public final static String LOG_TAG = "DPREF";

    private static class Holder {
        static final DLogger instance = new DLogger();
    }

    public static DLogger getInstance() {
        return Holder.instance;
    }

    @Override
    protected void _printf(String format, Object... args) {
        String message = String.format(format, args);
        PrintStream out = logHolder.getLogStream();
        out.print(message);
        if (DEBUG_LOG && out != System.out) {
            Log.d(LOG_TAG, message);
        }
    }
}