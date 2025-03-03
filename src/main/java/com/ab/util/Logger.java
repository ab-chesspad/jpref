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
 * Created: 2/6/2025
 */
package com.ab.util;

import com.ab.jpref.config.Config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    public static final String LOG_EXT = ".log";
    private static String logFileName;
    private static PrintStream out;
    static String startDate;

    public static void set(PrintStream out) {
        Logger.out = out;
    }

    private static void setOutput() {
        if (System.out != out) {
            String date = new SimpleDateFormat("yyyy-MM-dd-").format(new Date());
            if (!date.equals(startDate)) {
                close();
                try {
                    out = getNewOutput(date);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                startDate = date;
            }
        }
    }

    private static PrintStream getNewOutput(String date) throws IOException {
        File dataDir = Config.getDataDirectory();
        File logDir = new File(dataDir.getAbsolutePath(), "logs");
        logDir.mkdir();
        final int[] lastNum = {0};
        logDir.list((file, name) -> {
            if (name.endsWith(LOG_EXT)) {
                name = name.substring(0, name.length() - LOG_EXT.length());
            }
            if (!name.startsWith(date)) {
                return false;
            }
            name = name.substring(date.length());
            int num = 0;
            try {
                num = Integer.parseInt(name);
            } catch (NumberFormatException e) {
                return false;
            }
            if (lastNum[0] < num) {
                lastNum[0] = num;
            }
            return false;
        });
        logFileName = logDir + File.separator + String.format("%s%03d%s", date, lastNum[0] + 1, LOG_EXT);
        PrintStream res = new PrintStream(logFileName, StandardCharsets.UTF_8.name());
        return res;
    }

    public static void close() {
        if (out != null && out != System.out) {
            out.close();
            out = null;
        }
    }

    public static String getLogFileName() {
        return logFileName;
    }

    public static void println() {
        printf("\n");
    }

    public static void println(String msg) {
        printf("%s\n", msg);
    }

    public static void printf(boolean debug, String format, Object... args) {
        if (debug) {
            printf(format, args);
        }
    }

    public static void printf(String format, Object... args) {
        setOutput();
        out.printf(format, args);
    }
}
