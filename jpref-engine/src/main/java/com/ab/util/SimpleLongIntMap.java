/*  This file is part of jpref-0417.
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
import static com.ab.jpref.engine.BaseTrick.TRICK_POOL_SIZE;

public class SimpleLongIntMap {
    public static final int KEY_MASK_LEN = 34;  // 3 hands + top
    public static final long KEY_MASK = (1L << KEY_MASK_LEN) - 1;
//    public static final int CAPACITY = 500009;     // prime number
    public static final int CAPACITY = 1000003;     // prime number
//    public static final int CAPACITY = 3000017;     // prime number
    private static int _msb = 0;
    static {
        int _mask = 1;
        while (_mask < CAPACITY) {
            _mask <<= 1;
            ++_msb;
        }
    }
//    public static final long BUCKET_MARK = 1L << KEY_MASK_LEN;
    public static final int VALUE_SHIFT = KEY_MASK_LEN;

    // indexes in TrickPool:
    private static int _value_mask_len = 0;
    static {
        int bit = TRICK_POOL_SIZE;
        while (bit != 0) {
            ++_value_mask_len;
            bit >>>= 1;
        }
    }

    public static final int COLLISIONS_CAPACITY = 100000;
    static {
        _msb = 0;
        int _mask = 1;
        while (_mask < COLLISIONS_CAPACITY) {
            _mask <<= 1;
            ++_msb;
        }
    }
    public static final long COLLISIONS_MASK = ((1L << _msb) - 1) << KEY_MASK_LEN;

    public static final long NULL_KEY = 0;
    public static final int NULL_VALUE = 0;

    public int searches = 0;
    public int maxSearchCount = 0;
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
            return;
        }
        // do not check if the key is there already
        ++lastBucketsIndex;
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
        printf("SimpleLongIntMap size: %,d\n", keys.length);
        printf("  size: %,d, collisions: %,d, searches: %,d\n", size, lastBucketsIndex, searches);
        int count0 = 0, count1 = 0, buckets = 0;
        for (int i = 0; i < keys.length; ++i) {
            if (keys[i] == NULL_KEY) {
                ++count0;
            } else if ((keys[i] & COLLISIONS_MASK) == 0) {
                ++count1;
            } else {
                ++buckets;
            }
        }
        printf("  empty: %,d, single: %,d, buckets: %,d, searchCount=%d\n",
            count0, count1, buckets, maxSearchCount);

        clear(keys);
//        clear(bucketsKeys);
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
}
