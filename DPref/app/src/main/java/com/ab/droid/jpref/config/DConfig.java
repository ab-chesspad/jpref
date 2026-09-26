/*  This file is part of DPref project.
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
 * Created: 2/1/26
 */
package com.ab.droid.jpref.config;

import com.ab.droid.jpref.util.DUtil;
import com.ab.jpref.config.Config;
import com.ab.jpref.ui.Host;
import com.ab.util.Couple;

import android.graphics.Color;

import java.util.Locale;

public class DConfig extends Config {
    // update serialVersionUID every time a property is being added/changed!
    private static final long serialVersionUID = 13L;

    public final ColorProperty bgColor = new ColorProperty("", "#00A000");

    public DConfig(Host host) {
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

    public DConfig refresh() {
        DConfig instance = (DConfig)unserialize(host);
        // restore
        instance.mainSize.first = this.mainSize.first;
        instance.mainSize.second = this.mainSize.second;
        instance.mainPosition.setX(this.mainPosition.getX());
        instance.mainPosition.setY(this.mainPosition.getY());
        instance.mainSize.second = this.mainSize.second;
        host.setConfig(instance);
        return instance;
    }

    public static OS getOS() {
        return OS.android;
    }

    public static class ColorProperty extends Property<String> {

        public ColorProperty(String name, String value) {
            super(name, true, value);
        }

        public ColorProperty(String name, int color) {
            super(name, true, String.format("#%06X", color));
        }

        public void setColor(Color color) {
            super.set(String.format("#%06X", color));
        }

        public int getColor() {
            int c = Color.parseColor(super.get());
            return c;
        }

    }
}
