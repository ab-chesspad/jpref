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
 * Created: 2/6/2025
 */
package com.ab.util;

import java.io.PrintStream;

public class Logger {
    private static LogHolder logHolder = new LogHolder() {};
    private static PrintStream out;

    public static void setHolder(LogHolder logHolder) {
        Logger.logHolder = logHolder;
    }

    private static PrintStream getOutput() {
        if (out == null) {
            out = logHolder.getLogStream();
        }
        return out;
    }

    public static void println() {
        printf("\n");
    }

    public static void println(boolean debug) {
        printf(debug, "\n");
    }

    public static void println(Object msg) {
        printf("%s\n", msg.toString());
    }

    public static void println(boolean debug, Object msg) {
        printf(debug, "%s\n", msg.toString());
    }

    public static void printf(boolean debug, String format, Object... args) {
        if (debug) {
            printf(format, args);
        }
    }

    public static void printf(String format, Object... args) {
        PrintStream out = getOutput();
        out.printf(format, args);
    }

    public static void println(int msg) {
        printf("%d\n", msg);
    }

    public static void println(boolean debug, int msg) {
        printf(debug, "%d\n", msg);
    }

    public interface LogHolder {
        default PrintStream getLogStream() {return System.out; }
    }
}