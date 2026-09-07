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
 * Copyright (C) 2025-2026 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 1/12/2025
 */
package com.ab.jpref.gui.config;

import com.ab.jpref.config.Config;
import com.ab.jpref.ui.Host;
import com.ab.util.Couple;
import com.ab.jpref.gui.PUtil;

import java.awt.*;
import java.util.Locale;

public class PConfig extends Config {
    // update serialVersionUID every time a property is being added/changed!
    private static final long serialVersionUID = 10L;

    public final Property<Rectangle> scoresPopupRectangle = new Property<>("", new Rectangle());
    public final Property<Rectangle> settingsPopupRectangle = new Property<>("", new Rectangle());
    public final Property<Rectangle> helpPopupRectangle = new Property<>("", new Rectangle());
    public final Property<Rectangle> offerPopupRectangle = new Property<>("", new Rectangle());

    public final ColorProperty bgColor = new ColorProperty("", "#007000");
    public final ColorProperty labelBGColor = new ColorProperty("","#ffff00");
    public final ColorProperty labelTextColor = new ColorProperty("","#008200");
    public final ColorProperty currentPlayerBGColor = new ColorProperty("", "#00ff00");

    public PConfig(Host host) {
        super(host);
        Locale locale = Locale.getDefault();
        String lang = locale.getLanguage();
        int defaultLang = 0;
        for (int i = 0; i < this.language.get().values.length; ++i) {
            Couple<String> couple = this.language.get().values[i];
            if (lang.equals(couple.second)) {
                defaultLang = i;
                break;
            }
        }
        this.language.get().setSelected(defaultLang);
    }

    public PConfig refresh() {
        PConfig instance = (PConfig)unserialize(host);
        // restore
        instance.mainSize.first = this.mainSize.first;
        instance.mainSize.second = this.mainSize.second;
        instance.mainPosition.setX(this.mainPosition.getX());
        instance.mainPosition.setY(this.mainPosition.getY());
        instance.mainSize.second = this.mainSize.second;
        instance.scoresPopupRectangle.set(this.scoresPopupRectangle.get());
        instance.settingsPopupRectangle.set(this.settingsPopupRectangle.get());
        instance.helpPopupRectangle.set(this.helpPopupRectangle.get());
        host.setConfig(instance);
        return instance;
    }

    public static class ColorProperty extends Property<String> {

        public ColorProperty(String name, String value) {
            super(name, true, value);
        }

        public ColorProperty(String name, Color color) {
            super(name, true, String.format("#%06X", color.getRGB() & 0xffffff));
        }

        public void setColor(Color color) {
            super.set(String.format("#%06X", color.getRGB() & 0xffffff));
        }

        public Color getColor() {
            return Color.decode(super.get());
        }

    }
}