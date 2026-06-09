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
 * Copyright (C) 2026 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 4/21/26
 *
 */

package com.ab.util;

import static com.ab.util.Logger.printf;

public class SimpleLongIntMap {
    // Keys are capped at 34 bits ((top:2)(bitmap:32)); CAPACITY must be > maxSize / 0.7
    private static final int CAPACITY = 500_003;  // prime number

    private static final long NULL_KEY = 0;
    private static final int NULL_VALUE = 0;

    private int size;
    private static int probes;    // total extra probes during get()
    private static int maxProbe;  // max probe depth for a single get()

    private final long[] keys = new long[CAPACITY];
    private final int[] values = new int[CAPACITY];

    public static int maxProbes = 0;
    public static int maxSize = 0;

    public SimpleLongIntMap() {
        clear();
    }

    public void put(long key, int value) {
        int index = (int)(key % CAPACITY);
        while (keys[index] != NULL_KEY) {
            if (++index == CAPACITY) index = 0;
        }
        keys[index] = key;
        values[index] = value;
        ++size;
    }

    public int get(long key) {
        int index = (int)(key % CAPACITY);
        int probe = 0;
        long k;
        while ((k = keys[index]) != NULL_KEY) {
            if (k == key) {
                return values[index];
            }
            if (++index == CAPACITY) {
                index = 0;
            }
            ++probe;
        }
        if (probe > maxProbe) {
            maxProbe = probe;
        }
        probes += probe;
        return NULL_VALUE;
    }

    public int size() {
        return size;
    }

    public void clear() {
        if (size == 0) {
            return;
        }
        printf("SimpleLongIntMap size: %,d\n", size);
        printf("  probes: %,d, maxProbe: %d\n", probes, maxProbe);

        if (maxProbes < probes) {
            maxProbes = probes;
        }
        if (maxSize < size) {
            maxSize = size;
        }

        zeroKeys();
        size = 0;
        probes = 0;
        maxProbe = 0;
    }

    public static void printStatistics() {
        printf("SimpleLongIntMap capacity: %,d, statistics:\n", CAPACITY);
        printf("  size: %,d, max probes: %,d, maxProbe: %d\n", maxSize, probes, maxProbe);
    }

    private void zeroKeys() {
        keys[0] = NULL_KEY;
        int initialized = 1;
        while (initialized < keys.length) {
            int len = initialized;
            if (2 * initialized > keys.length) {
                len = keys.length - initialized;
            }
            System.arraycopy(keys, 0, keys, initialized, len);
            initialized *= 2;
        }
    }
}
