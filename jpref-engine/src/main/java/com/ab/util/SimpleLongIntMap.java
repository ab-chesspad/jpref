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
    public static final int KEY_MASK_LEN = 34;  // 3 hands + top
    public static final long KEY_MASK = (1L << KEY_MASK_LEN) - 1;
//    public static final int CAPACITY = 500009;     // prime number
    public static final int CAPACITY = 1000003;     // prime number
//    public static final int CAPACITY = 3000017;     // prime number
//    public static final long BUCKET_MARK = 1L << KEY_MASK_LEN;
    public static final int VALUE_SHIFT = KEY_MASK_LEN;

/*
    // indexes in TrickPool:
    private static int _value_mask_len = 0;
    static {
        int bit = TRICK_POOL_SIZE;
        while (bit != 0) {
            ++_value_mask_len;
            bit >>>= 1;
        }
    }
*/

    public static final int COLLISIONS_CAPACITY = 100000;

    public static final long NULL_KEY = 0;
    public static final int NULL_VALUE = 0;

    public static int maxSearchCount = 0;
    public static int maxSize = 0;
    public static int maxCollisions = 0;

    public int searches = 0;
    private int size = 0;

    final long[] keys;
    final int[] values;

    final long[] bucketsKeys;
    final int[] bucketsValues;
    private int lastBucketsIndex = 0;

    public SimpleLongIntMap() {
        int capacity = CAPACITY;
        keys = new long[capacity];
        values = new int[capacity];
        bucketsKeys = new long[COLLISIONS_CAPACITY];
        bucketsValues = new int[COLLISIONS_CAPACITY];
        clear();
    }

    public void put(long key, int value) {
        int index = hash(key);
        long mapKey = keys[index];
        if (mapKey == NULL_KEY) {
            keys[index] = key;
            values[index] = value;
            ++size;
            if (maxSize < size) {
                maxSize = size;
            }
            return;
        }
        // do not check if the key is there already
        ++lastBucketsIndex;
        if (maxCollisions < lastBucketsIndex) {
            maxCollisions = lastBucketsIndex;
        }
        bucketsKeys[lastBucketsIndex] = mapKey;
        bucketsValues[lastBucketsIndex] = values[index];
        keys[index] = key & KEY_MASK | ((long)lastBucketsIndex & 0x0ffffffffL) << VALUE_SHIFT;
        values[index] = value;
    }

    public int get(long key) {
        int index = hash(key);
        long mapKey = keys[index];
        if (mapKey == NULL_KEY) {
            return NULL_VALUE;
        }
        if ((mapKey & KEY_MASK) == key) {
            return values[index];
        }
        int search_count = 0;
        int bucketIndex = (int)((mapKey >>> VALUE_SHIFT) & 0x0ffffffffL);
        while (bucketIndex != 0) {
            ++searches;
            ++search_count;
            if (maxSearchCount < search_count) {
                maxSearchCount = search_count;
            }
            mapKey = bucketsKeys[bucketIndex];
            if ((mapKey & KEY_MASK) == key) {
                return bucketsValues[bucketIndex];
            }
            bucketIndex = (int)((mapKey >>> VALUE_SHIFT) & 0x0ffffffffL);
        }
        return NULL_VALUE;
    }

    public int size() {
        return size;
    }

    public int getCollisions() {
        return lastBucketsIndex;
    }

    public void clear() {
        if (size == 0) {
            return;
        }
        clear(keys);
        lastBucketsIndex = 0;
        size = 0;
        searches = 0;
    }

    public void clear(long[] keys) {
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
        searches = 0;
    }

    private int hash(long key) {
        int index = (int)(key % keys.length);
        return index;
    }

    public static void printStatistics() {
        printf("SimpleLongIntMap maxSize: %,d, collisions: %,d, searches: %,d\n",
            maxSize, maxCollisions, maxSearchCount);
    }
}
