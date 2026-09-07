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
 * Created: 6/19/26
 *
 */

package com.ab.jpref.ui;

import com.ab.jpref.config.Config;
import com.ab.jpref.config.Metrics;
import com.ab.util.Util;

public interface Host {
    int SPECIAL_OPTION_SHOW_CARDS = 0x1;
    int SPECIAL_OPTION_MANUAL = 0x1;

    default int specialOption() {return 0;}
    default long buildDate() {return 0;}
    default Metrics getMetrics() {return null;}
    Config config();
    default void setConfig(Config config) {};
    Util getUtil();
    default String getLogFileName() {return null;}
    default void repaintAll() {}
    default void updateSettings() {}
    default String getDataDirectory(){return null;}

    // Some Android versions/devices don't reliably dispatch touches (or draw
    // order) by z-order/elevation for overlapping sibling views, letting a tap
    // on a disabled overlay button fall through to whatever's positioned
    // underneath it. Hosts affected by this should force the covered panel's
    // buttons disabled while the menu overlay is shown; unaffected hosts
    // (desktop's proper Z-ordered JLayeredPane, and newer Android versions)
    // don't need it and shouldn't have their buttons disabled needlessly.
    default boolean needsMenuOverlapWorkaround() {
        return false;
    }
}