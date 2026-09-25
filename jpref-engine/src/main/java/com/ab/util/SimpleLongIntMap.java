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
//    public static final int CAPACITY = 1000003;     // prime number
    // with alpha-beta pruning (see TrickList.PRUNE) the worst observed no-trump,
    // defender-leads-trick-1 case needs ~791,000 distinct positions; this keeps
    // load factor comfortably low (~0.26) so collision chains stay short
    public static final int CAPACITY = 3000017;     // prime number
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

    // collision chain storage is split into fixed-size chunks. When the
    // current chunk fills up, a new chunk is allocated and appended to the
    // (small) chunk table - already-stored entries are never copied, unlike
    // growing a single array with Arrays.copyOf.
    private static final int BUCKET_CHUNK_BITS = 17;
    private static final int BUCKET_CHUNK_SIZE = 1 << BUCKET_CHUNK_BITS;
    private static final int BUCKET_CHUNK_MASK = BUCKET_CHUNK_SIZE - 1;

    public static final long NULL_KEY = 0;
    public static final int NULL_VALUE = 0;

    public static int maxSearchCount = 0;
    public static int maxSize = 0;
    public static int maxCollisions = 0;

    // a thread-safe map is split into independent shards, each with its own
    // lock, so concurrent putIfAbsent() calls rarely contend for the same one.
    // A shard is selected by a multiplicative hash, independent of the
    // shard's own. A map that is not thread-safe has a single shard and never
    // locks.
    private static final int SHARD_BITS = 6;
    private final Shard[] shards;
    private final int shardShift;

    public SimpleLongIntMap() {
        this(false);
    }

    // threadSafe: putIfAbsent() may be called concurrently
    public SimpleLongIntMap(boolean threadSafe) {
        int shardBits = threadSafe ? SHARD_BITS : 0;
        shards = new Shard[1 << shardBits];
        shardShift = 64 - shardBits;
        for (int i = 0; i < shards.length; ++i) {
            shards[i] = new Shard(CAPACITY >>> shardBits);
        }
    }

    private Shard shard(long key) {
        if (shards.length == 1) {
            return shards[0];   // (x >>> 64) == x in java
        }
        return shards[(int)((key * 0x9E3779B97F4A7C15L) >>> shardShift)];
    }

    // not thread-safe
    public void put(long key, int value) {
        shard(key).put(key, value);
    }

    // not thread-safe
    public int get(long key) {
        return shard(key).get(key);
    }

    // returns the value already mapped to key, or puts value and returns NULL_VALUE;
    // thread-safe when the map was created thread-safe
    public int putIfAbsent(long key, int value) {
        Shard shard = shard(key);
        if (shards.length == 1) {
            return shard.putIfAbsent(key, value);
        }
        synchronized (shard) {
            return shard.putIfAbsent(key, value);
        }
    }

    public int size() {
        int size = 0;
        for (Shard shard : shards) {
            size += shard.size;
        }
        return size;
    }

    public int getCollisions() {
        int collisions = 0;
        for (Shard shard : shards) {
            collisions += shard.lastBucketsIndex;
        }
        return collisions;
    }

    public void clear() {
        for (Shard shard : shards) {
            shard.clear();
        }
    }

    private static final class Shard {
        private int searches = 0;
        private int size = 0;

        final long[] keys;
        final int[] values;

        private long[][] bucketsKeyChunks;
        private int[][] bucketsValueChunks;
        private int lastBucketsIndex = 0;

        Shard(int capacity) {
            keys = new long[capacity];
            values = new int[capacity];
            // collision chunks are allocated on first use, so many small
            // shards don't each allocate a chunk upfront
            bucketsKeyChunks = new long[0][];
            bucketsValueChunks = new int[0][];
            clear();
        }

        int putIfAbsent(long key, int value) {
            int res = get(key);
            if (res == NULL_VALUE) {
                put(key, value);
            }
            return res;
        }

        void put(long key, int value) {
            // every call is a genuinely new key (callers only put() after a get() miss),
            // so size grows by one on every call, not just when the slot is empty
            ++size;
            if (maxSize < size) {
                maxSize = size;
            }
            int index = hash(key);
            long mapKey = keys[index];
            if (mapKey == NULL_KEY) {
                keys[index] = key;
                values[index] = value;
                return;
            }
            // do not check if the key is there already
            ++lastBucketsIndex;
            int chunk = lastBucketsIndex >>> BUCKET_CHUNK_BITS;
            int offset = lastBucketsIndex & BUCKET_CHUNK_MASK;
            if (chunk >= bucketsKeyChunks.length) {
                // extend the chunk table itself (just pointers, cheap); existing chunks are untouched
                long[][] extendedKeyChunks = new long[chunk + 1][];
                int[][] extendedValueChunks = new int[chunk + 1][];
                System.arraycopy(bucketsKeyChunks, 0, extendedKeyChunks, 0, bucketsKeyChunks.length);
                System.arraycopy(bucketsValueChunks, 0, extendedValueChunks, 0, bucketsValueChunks.length);
                bucketsKeyChunks = extendedKeyChunks;
                bucketsValueChunks = extendedValueChunks;
            }
            if (bucketsKeyChunks[chunk] == null) {
                bucketsKeyChunks[chunk] = new long[BUCKET_CHUNK_SIZE];
                bucketsValueChunks[chunk] = new int[BUCKET_CHUNK_SIZE];
            }
            if (maxCollisions < lastBucketsIndex) {
                maxCollisions = lastBucketsIndex;
            }
            bucketsKeyChunks[chunk][offset] = mapKey;
            bucketsValueChunks[chunk][offset] = values[index];
            keys[index] = key & KEY_MASK | ((long)lastBucketsIndex & 0x0ffffffffL) << VALUE_SHIFT;
            values[index] = value;
        }

        int get(long key) {
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
                int chunk = bucketIndex >>> BUCKET_CHUNK_BITS;
                int offset = bucketIndex & BUCKET_CHUNK_MASK;
                mapKey = bucketsKeyChunks[chunk][offset];
                if ((mapKey & KEY_MASK) == key) {
                    return bucketsValueChunks[chunk][offset];
                }
                bucketIndex = (int)((mapKey >>> VALUE_SHIFT) & 0x0ffffffffL);
            }
            return NULL_VALUE;
        }

        void clear() {
            if (size == 0) {
                return;
            }
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
            lastBucketsIndex = 0;
            size = 0;
            searches = 0;
        }

        private int hash(long key) {
            // key < 0x3ffffffffL anyway
            int hash = (int)(key ^ key >>> 30) & Integer.MAX_VALUE;
            return hash % keys.length;
        }
    }

    public static void printStatistics() {
        printf("SimpleLongIntMap maxSize: %,d, collisions: %,d, searches: %,d\n",
            maxSize, maxCollisions, maxSearchCount);
    }
}
