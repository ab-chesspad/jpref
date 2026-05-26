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
 * Created: 8/11/2024
 */
package com.ab.util;

import java.io.Serializable;

public class Couple<T> implements Cloneable, Serializable {
    public T first;
    public T second;

    public Couple() {}

    public Couple(T first, T second) {
        this.first = first;
        this.second = second;
    }

    public Couple<T> clone() {
        return new Couple<>(first, second);
    }

    // possibly use for config
    @Override
    public String toString() {
        return first.toString();
    }

}