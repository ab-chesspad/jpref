/**
 * avoid dependency on Java or android librarries
 */
package com.ab.util;

public class Point extends Pair<Integer, Integer> {
    /**
     * Constructor for a Point.
     *
     * @param x  the first int the Point
     * @param y the second int the Point
     */
    public Point(int x, int y) {
        super(x, y);
    }

}
