/**
 * avoid dependency on Java or android libraries
 */
package com.ab.util;

public class Point extends Pair<Integer, Integer> {

    public Point() {
        super(0, 0);
    }

    /**
     * Constructor for a Point.
     *
     * @param x  the first int the Point
     * @param y the second int the Point
     */
    public Point(int x, int y) {
        super(x, y);
    }

    public int getX() {
        return first;
    }

    public void setX(int value) {
        first = value;
    }

    public int getY() {
        return second;
    }

    public void setY(int value) {
        second = value;
    }

    public void set(int first, int second) {
        this.first = first;
        this.second = second;
    }
}
