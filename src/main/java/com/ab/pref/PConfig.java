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
 * Copyright 2025 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 1/12/2025
 */
package com.ab.pref;

import com.ab.jpref.config.Config;

import java.awt.*;

public class PConfig extends Config {
    // update serialVersionUID every time another property is being added!
    private static final long serialVersionUID = 12L;

    public final Property<Rectangle> mainRectangle = new Property<>("", new Rectangle());
    public final Property<Rectangle> scoresPopupRectangle = new Property<>("", new Rectangle());
    public final ColorProperty bgColor = new ColorProperty("Table Color", "#007000");
    public final ColorProperty labelBGColor = new ColorProperty("","#ffff00");
    public final ColorProperty labelTextColor = new ColorProperty("","#008200");
    public final ColorProperty currentPlayerBGColor = new ColorProperty("", "#00ff00");
    public final Property<String> GUID = new Property<>("", null);

    protected static Object instance;

    public static PConfig getInstance() {
        if (instance == null) {
            instance = Config.unserialize();
        }
        if (instance == null) {
            instance = new PConfig();
        }
        return (PConfig)instance;
    }

    protected PConfig() {
        super();
    }

    public static class ColorProperty extends Property<String> {

        public ColorProperty(String name, String value) {
            super(name, value);
        }

        public ColorProperty(String name, Color color) {
            super(name, String.format("#%06X", color.getRGB() & 0xffffff));
        }

        public void setColor(Color color) {
            super.set(String.format("#%06X", color.getRGB() & 0xffffff));
        }

        public Color getColor() {
            return Color.decode(super.get());
        }

    }

}
