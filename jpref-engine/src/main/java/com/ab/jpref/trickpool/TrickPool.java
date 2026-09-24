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

import com.ab.jpref.engine.TrickList;

import static com.ab.jpref.engine.TrickList.SINGLE_THREADED;
import static com.ab.jpref.engine.TrickList.TRACE;

import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class TrickPool implements TrickList.TrickPool {
    // pool storage is split into fixed-size pages. Page 0 is allocated
    // upfront (virtually every search needs it); the rest are allocated
    // lazily as needed, so a fresh pool doesn't spike memory before the
    // search even knows how much it'll use, which matters on Android. The
    // page table itself is fixed-size and never resized, so no thread can
    // ever observe it mid-growth. Each page is published through an
    // AtomicReferenceArray, so a lazily-created page is safely visible to any
    // thread that later reads it without get()/set() needing to synchronize:
    // get()/set() are only ever called with an index a prior alloc() already
    // returned, so that index's page is guaranteed to already exist.
    private static final int PAGE_BITS = 20;
    private static final int PAGE_SIZE = 1 << PAGE_BITS;
    private static final int PAGE_MASK = PAGE_SIZE - 1;
    private static final int PAGE_COUNT = 32;

    private AtomicReferenceArray<AtomicLongArray> atomicPages;
    private long[][] pages;
    private final int[][] backRefPages;
    public int nextPoolIndex;

    public TrickPool() {
        if (SINGLE_THREADED) {
            pages = new long[PAGE_COUNT][];
            pages[0] = new long[PAGE_SIZE];
        } else {
            atomicPages = new AtomicReferenceArray<>(PAGE_COUNT);
            atomicPages.set(0, new AtomicLongArray(PAGE_SIZE));
        }
        backRefPages = TRACE ? new int[PAGE_COUNT][] : null;
        if (TRACE) {
            backRefPages[0] = new int[PAGE_SIZE];
        }
    }

    @Override
    public void clear() {
        nextPoolIndex = 0;
    }

    private long[] page(int page) {
        if (pages[page] == null) {
            pages[page] = new long[PAGE_SIZE];
            if (TRACE) {
                backRefPages[page] = new int[PAGE_SIZE];
            }
        }
        return pages[page];
    }

    private AtomicLongArray atomicPage(int page) {
        AtomicLongArray p = atomicPages.get(page);
        if (p == null) {
            p = new AtomicLongArray(PAGE_SIZE);
            atomicPages.set(page, p);
            if (TRACE) {
                backRefPages[page] = new int[PAGE_SIZE];
            }
        }
        return p;
    }

    public int alloc(long trickData, int prevIndex) {
        ++nextPoolIndex;
        int pageIndex = nextPoolIndex >>> PAGE_BITS;
        int offset = nextPoolIndex & PAGE_MASK;
        if (SINGLE_THREADED) {
            page(pageIndex)[offset] = trickData;
        } else {
            atomicPage(pageIndex).set(offset, trickData);
        }
        if (TRACE) {
            backRefPages[pageIndex][offset] = prevIndex;
        }
        return nextPoolIndex;
    }

    @Override
    public synchronized int allocSync(long trickData, int prevIndex) {
        return alloc(trickData, prevIndex);
    }

    @Override
    public void set(int index, long trickData) {
        if (SINGLE_THREADED) {
            pages[index >>> PAGE_BITS][index & PAGE_MASK] = trickData;
        } else {
            atomicPages.get(index >>> PAGE_BITS).set(index & PAGE_MASK, trickData);
        }
    }

    @Override
    public long get(int index) {
        if (SINGLE_THREADED) {
            return pages[index >>> PAGE_BITS][index & PAGE_MASK];
        } else {
            return atomicPages.get(index >>> PAGE_BITS).get(index & PAGE_MASK);
        }
    }

    @Override
    public int getPrev(int index) {
        if (!TRACE) {
            throw new RuntimeException("invalid use of debugging option");
        }
        return backRefPages[index >>> PAGE_BITS][index & PAGE_MASK];
    }

    @Override
    public int size() {
        return nextPoolIndex;
    }

    @Override
    public int capacity() {
        return PAGE_COUNT * PAGE_SIZE;
    }
}
