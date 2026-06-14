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
 * Trick pool used to avoid excessive memory fragmentation
 */

package com.ab.jpref.trickpool;

import com.ab.jpref.engine.TrickList;

public class TrickPool implements TrickList.TrickPool {

    private final long[] trickPool;
    public int nextPoolIndex;

    public TrickPool() {
        this(DEFAULT_CAPACITY);
    }

    public TrickPool(int capacity) {
        trickPool = new long[capacity];
    }

    @Override
    public void clear() {
        nextPoolIndex = 0;
    }

    @Override
    public int alloc(long trickData) {
        if (nextPoolIndex >= trickPool.length) {
            throw new RuntimeException("exceeded trick pool size " + trickPool.length);
        }
        trickPool[++nextPoolIndex] = trickData;
        return nextPoolIndex;
    }

    @Override
    public void set(int index, long trickData) {
        trickPool[index] = trickData;
    }

    @Override
    public long get(int index) {
        return trickPool[index];
    }

    @Override
    public int size() {
        return nextPoolIndex;
    }

    @Override
    public int capacity() {
        return trickPool.length;
    }
}
