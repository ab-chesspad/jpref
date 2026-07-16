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
import com.ab.util.Couple;
import com.ab.jpref.gui.PUtil;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

public class PConfig extends Config {
    // update serialVersionUID every time a property is being added/changed!
    private static final long serialVersionUID = 9L;

    public final Property<Rectangle> mainRectangle = new Property<>("", new Rectangle());
    public final Property<Rectangle> scoresPopupRectangle = new Property<>("", new Rectangle());
    public final Property<Rectangle> settingsPopupRectangle = new Property<>("", new Rectangle());
    public final Property<Rectangle> helpPopupRectangle = new Property<>("", new Rectangle());

    public final ColorProperty bgColor = new ColorProperty("", "#007000");
    public final ColorProperty labelBGColor = new ColorProperty("","#ffff00");
    public final ColorProperty labelTextColor = new ColorProperty("","#008200");
    public final ColorProperty currentPlayerBGColor = new ColorProperty("", "#00ff00");

    static final PUtil util = PUtil.getInstance();

    public static PConfig getInstance() {
        if (instance == null) {
            instance = PConfig.unserialize();
        }
        if (instance == null) {
            instance = new PConfig();
        }
        return (PConfig)instance;
    }

    protected PConfig() {
        super();
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

        mainSize.first = mainRectangle.get().width;
        mainSize.second = mainRectangle.get().height;
    }

    public static PConfig unserialize() {
        return (PConfig)unserialize(util.getDataDirectory());
    }

    public void serialize() {
        serialize(util.getDataDirectory());
    }

    public static void refresh() {
        PConfig _instance = (PConfig)instance;
        instance = PConfig.unserialize();
        if (instance == null) {
            instance = new PConfig();
        }
        if (_instance != null) {
            // restore
            ((PConfig)instance).mainRectangle.set(_instance.mainRectangle.get());
            ((PConfig)instance).mainSize.first = _instance.mainRectangle.get().width;
            ((PConfig)instance).mainSize.second = _instance.mainRectangle.get().height;
            ((PConfig)instance).scoresPopupRectangle.set(_instance.scoresPopupRectangle.get());
            ((PConfig)instance).settingsPopupRectangle.set(_instance.settingsPopupRectangle.get());
            ((PConfig)instance).helpPopupRectangle.set(_instance.helpPopupRectangle.get());
        }
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