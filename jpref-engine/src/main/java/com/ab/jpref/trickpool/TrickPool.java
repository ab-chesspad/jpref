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
 * Created: 5/31/26
 *
 * Trick pool helps to avoid excessive memory fragmentation on Android
 */

package com.ab.jpref.trickpool;

import com.ab.jpref.engine.BaseTrick;
import com.ab.jpref.engine.TrickList;

import static com.ab.jpref.engine.TrickList.MULTI_THREADED;
import static com.ab.jpref.engine.TrickList.TRACE;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class TrickPool implements TrickList.TrickPool {
    // the pool is split into THREAD_POOLS independent sub-pools, one per
    // search thread, so alloc() never needs to synchronize: each thread only
    // ever allocates from its own sub-pool. A pool index is
    // (threadNum << THREAD_SHIFT) | localIndex, so get()/set() can locate the
    // entry from any thread. localIndex 0 is never allocated, so index 0
    // still means "none".
    //
    // Each sub-pool's storage is split into fixed-size pages. Page 0 of
    // sub-pool 0 is allocated upfront (virtually every search needs it); the
    // rest are allocated lazily as needed, so a fresh pool doesn't spike
    // memory before the search even knows how much it'll use, which matters
    // on Android. The page tables themselves are fixed-size and never
    // resized, so no thread can ever observe one mid-growth.
    //
    // Entry data is kept in plain arrays: an entry is written only by its
    // owning thread, and never changes after set() stores its final (done)
    // data. Other threads read an entry only after isDone() returned true for
    // it (directly, or for an entry whose data led to it), so in multithreaded
    // mode the single cross-thread handoff is a per-entry done bit: set()
    // publishes it with a release write (lazySet) after the plain data write,
    // isDone() reads it with an acquire read. That makes the entry data, and
    // everything its owner wrote before it (including newly created pages),
    // visible to the reader. Each done bitmap word covers entries of a single
    // sub-pool, so it only ever has one writer and needs no CAS.
    private static final int THREAD_SHIFT = 24;         // BaseTrick index keeps 28 bits
    private static final int LOCAL_MASK = (1 << THREAD_SHIFT) - 1;
    private static final int PAGE_BITS = 20;
    private static final int PAGE_SIZE = 1 << PAGE_BITS;
    private static final int PAGE_MASK = PAGE_SIZE - 1;
    private static final int PAGE_COUNT = 1 << (THREAD_SHIFT - PAGE_BITS);
    private static final int DONE_WORD_BITS = 5;        // 32 done bits per int

    private final long[][][] pages;
    private final AtomicIntegerArray[][] donePages;
    private final int[][][] backRefPages;
    private final int[] nextPoolIndex = new int[THREAD_POOLS];

    public TrickPool() {
        pages = new long[THREAD_POOLS][PAGE_COUNT][];
        donePages = MULTI_THREADED ? new AtomicIntegerArray[THREAD_POOLS][PAGE_COUNT] : null;
        backRefPages = TRACE ? new int[THREAD_POOLS][PAGE_COUNT][] : null;
        page(0, 0);
    }

    @Override
    public void clear() {
        Arrays.fill(nextPoolIndex, 0);
    }

    private long[] page(int threadNum, int page) {
        long[][] threadPages = pages[threadNum];
        if (threadPages[page] == null) {
            threadPages[page] = new long[PAGE_SIZE];
            if (MULTI_THREADED) {
                donePages[threadNum][page] = new AtomicIntegerArray(PAGE_SIZE >>> DONE_WORD_BITS);
            }
            if (TRACE) {
                backRefPages[threadNum][page] = new int[PAGE_SIZE];
            }
        }
        return threadPages[page];
    }

    @Override
    public int alloc(long trickData, int threadNum, int prevIndex) {
        int localIndex = ++nextPoolIndex[threadNum];
        if (localIndex > LOCAL_MASK) {
            throw new RuntimeException("trick pool " + threadNum + " overflow");
        }
        int pageIndex = localIndex >>> PAGE_BITS;
        int offset = localIndex & PAGE_MASK;
        page(threadNum, pageIndex)[offset] = trickData;
        if (MULTI_THREADED && ((offset & ((1 << DONE_WORD_BITS) - 1)) == 0 || localIndex == 1)) {
            // entries are allocated sequentially, so the whole word belongs to
            // entries not yet allocated in this search; reset done bits left
            // over from a previous one. localIndex 0 is never allocated, so
            // the first word is reset on localIndex 1
            donePages[threadNum][pageIndex].lazySet(offset >>> DONE_WORD_BITS, 0);
        }
        if (TRACE) {
            backRefPages[threadNum][pageIndex][offset] = prevIndex;
        }
        return threadNum << THREAD_SHIFT | localIndex;
    }

    // stores the final data, entry must not change afterwards
    @Override
    public void set(int index, long trickData) {
        int threadNum = index >>> THREAD_SHIFT;
        int localIndex = index & LOCAL_MASK;
        int pageIndex = localIndex >>> PAGE_BITS;
        int offset = localIndex & PAGE_MASK;
        pages[threadNum][pageIndex][offset] = trickData;
        if (MULTI_THREADED) {
            AtomicIntegerArray done = donePages[threadNum][pageIndex];
            int word = offset >>> DONE_WORD_BITS;
            done.lazySet(word, done.get(word) | 1 << (offset & ((1 << DONE_WORD_BITS) - 1)));
        }
    }

    @Override
    public boolean isDone(int index) {
        int threadNum = index >>> THREAD_SHIFT;
        int localIndex = index & LOCAL_MASK;
        int pageIndex = localIndex >>> PAGE_BITS;
        int offset = localIndex & PAGE_MASK;
        if (MULTI_THREADED) {
            int bits = donePages[threadNum][pageIndex].get(offset >>> DONE_WORD_BITS);
            return (bits & 1 << (offset & ((1 << DONE_WORD_BITS) - 1))) != 0;
        }
        return BaseTrick.isDone(pages[threadNum][pageIndex][offset]);
    }

    @Override
    public long get(int index) {
        int localIndex = index & LOCAL_MASK;
        return pages[index >>> THREAD_SHIFT][localIndex >>> PAGE_BITS][localIndex & PAGE_MASK];
    }

    @Override
    public int getPrev(int index) {
        if (!TRACE) {
            throw new RuntimeException("invalid use of debugging option");
        }
        int localIndex = index & LOCAL_MASK;
        return backRefPages[index >>> THREAD_SHIFT][localIndex >>> PAGE_BITS][localIndex & PAGE_MASK];
    }

    @Override
    public int size() {
        int size = 0;
        for (int n : nextPoolIndex) {
            size += n;
        }
        return size;
    }

    @Override
    public int capacity() {
        return THREAD_POOLS * PAGE_COUNT * PAGE_SIZE;
    }
}
