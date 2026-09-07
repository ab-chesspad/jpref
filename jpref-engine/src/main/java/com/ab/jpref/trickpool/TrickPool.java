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

public class TrickPool implements TrickList.TrickPool {
    public static boolean TRACE = TrickList.TRACE;

    // pool storage is split into fixed-size pages, allocated lazily. When the
    // page table runs out of room, it is extended (just pointers, cheap);
    // when an index falls in a not-yet-used page, that one page is allocated.
    // Already-stored entries are never copied, unlike growing a single array
    // with Arrays.copyOf.
    private static final int PAGE_BITS = 17;
    private static final int PAGE_SIZE = 1 << PAGE_BITS;
    private static final int PAGE_MASK = PAGE_SIZE - 1;

    private long[][] pages;
    private int[][] backRefPages;
    public int nextPoolIndex;

    public TrickPool() {
        this(DEFAULT_CAPACITY);
    }

    public TrickPool(int capacity) {
        int pageCount = Math.max(1, (capacity + PAGE_SIZE - 1) / PAGE_SIZE);
        pages = new long[pageCount][];
        pages[0] = new long[PAGE_SIZE];
        if (TRACE) {
            backRefPages = new int[pageCount][];
            backRefPages[0] = new int[PAGE_SIZE];
        }
    }

    @Override
    public void clear() {
        nextPoolIndex = 0;
    }

    @Override
    public int alloc(long trickData, int prevIndex) {
        ++nextPoolIndex;
        int page = nextPoolIndex >>> PAGE_BITS;
        int offset = nextPoolIndex & PAGE_MASK;
        if (page >= pages.length) {
            // extend the (small) page table itself; existing pages are untouched
            long[][] extendedPages = new long[page + 1][];
            System.arraycopy(pages, 0, extendedPages, 0, pages.length);
            pages = extendedPages;
            if (TRACE) {
                int[][] extendedBackRefPages = new int[page + 1][];
                System.arraycopy(backRefPages, 0, extendedBackRefPages, 0, backRefPages.length);
                backRefPages = extendedBackRefPages;
            }
        }
        if (pages[page] == null) {
            pages[page] = new long[PAGE_SIZE];
            if (TRACE) {
                backRefPages[page] = new int[PAGE_SIZE];
            }
        }
        pages[page][offset] = trickData;
        if (TRACE) {
            backRefPages[page][offset] = prevIndex;
        }
        return nextPoolIndex;
    }

    @Override
    public void set(int index, long trickData) {
        pages[index >>> PAGE_BITS][index & PAGE_MASK] = trickData;
    }

    @Override
    public long get(int index) {
        return pages[index >>> PAGE_BITS][index & PAGE_MASK];
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
        return pages.length * PAGE_SIZE;
    }
}
