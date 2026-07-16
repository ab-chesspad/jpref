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
 * Created: 24 Jun 2026
 */
package com.ab.jpref.ui;

import static com.ab.jpref.engine.GameManager.RoundStage;
import static com.ab.jpref.ui.TableLayout.ButtonCommand;

import java.util.Iterator;

public class ButtonPanel extends Widget implements Iterable<Widget> {
    static final int xGap = 2;
    static final int yGap = 2;

    private final RoundStage roundStage;
    private final double scaleW, scaleH;
    private final Widget[][] widgets;

    private final int columns;
    private final int rows;

//    private final double scaleW, scaleH;

    public ButtonPanel(RoundStage roundStage, double scaleW, double scaleH, ButtonHandler[][] handlers) {
        this.roundStage = roundStage;
        this.scaleW = scaleW;
        this.scaleH = scaleH;

//    public ButtonPanel(double scaleW, double scaleH, ButtonHandler[][] handlers) {
        rows = handlers.length;
        columns = handlers[0].length;
        widgets = new Widget[rows][columns];
        for (int j = 0; j < rows; ++j) {
            ButtonHandler[] row = handlers[j];
            widgets[j] = new Widget[row.length];
            for (int i = 0; i < columns; ++i) {
                ButtonHandler handler = row[i];
                if (handler == null) {
                    continue;
                }
                Widget widget = new Widget(handler.buttonCommand, handler.buttonListener);
                widget.setVisible(false);
                widget.setEnabled(true);
                widgets[j][i] = widget;
            }
        }
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        int w = (width - xGap * (columns - 1)) / columns;
        int h = (height - yGap * (rows - 1)) / rows;

        int _y = y;
        for (int j = 0; j < rows; ++j) {
            Widget[] row = widgets[j];
            int _x = x;
            for (int i = 0; i < columns; ++i) {
                Widget widget = row[i];
                if (widget != null) {
                    widget.setBounds(_x, _y, w, h);
                }
                _x += xGap + w;
            }
            _y += yGap + h;
        }
    }

    public void setVisible(boolean visible) {
        for( Widget widget : this) {
            widget.setVisible(visible);
        }
        this.visible = visible;
    }

    public int getColumnCount() {
        return columns;
    }

    public int getRowCount() {
        return rows;
    }

    public double getScaleW() {
        return scaleW;
    }

    public double getScaleH() {
        return scaleH;
    }

    public RoundStage getRoundStage() {
        return roundStage;
    }

    public Widget getWidget(ButtonCommand command) {
        for( Widget widget : this) {
            if (command.equals(widget.getCommand())) {
                return widget;
            }
        }
        return null;
    }

    public Widget getWidget(int row, int column) {
        return widgets[row][column];
    }

    @Override
    public Iterator<Widget> iterator() {
        final int[] j = { -1 };
        final int[] i = { widgets[0].length };
        return new Iterator<Widget>() {
            @Override
            public boolean hasNext() {
                do {
                    if (++i[0] >= widgets[0].length) {
                        if (++j[0] >= widgets.length) {
                            return false;
                        }
                        i[0] = 0;
                    }
                } while (widgets[j[0]][i[0]] == null);
                return true;
            }

            @Override
            public Widget next() {
                return widgets[j[0]][i[0]];
            }
        };
    }

    public static class ButtonHandler {
        public final ButtonCommand buttonCommand;
        public final ButtonListener buttonListener;

        public ButtonHandler(ButtonCommand buttonCommand, ButtonListener buttonListener) {
            this.buttonCommand = buttonCommand;
            this.buttonListener = buttonListener;
        }
    }

}