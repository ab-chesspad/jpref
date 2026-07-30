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
 * Created: 3/6/26
 *
 */

package com.ab.jpref.gui;

import com.ab.jpref.config.Metrics;
import com.ab.jpref.engine.GameManager;
import com.ab.jpref.gui.config.PConfig;
import com.ab.jpref.ui.TableLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static com.ab.jpref.config.Config.ROUND_SIZE;
import static com.ab.jpref.config.I18n.m;

public class OfferPopup extends JDialog {
    private final PUtil pUtil = PUtil.getInstance();
    final GameManager gameManager;
    final OfferPopup popupInstance;

    final BufferedImage lineImage = pUtil.loadImage("buttons/radio.png");
    final BufferedImage selectedLineImage = pUtil.loadImage("buttons/radio-sel.png");

    final JList<String> jList;
    int selectedIndex = -1;

    JButton acceptButton = null;
    int result = -1;

    public OfferPopup(int minTricks, int maxTricks) {
        super(Main.mainFrame, true);
        popupInstance = this;

        gameManager = GameManager.getInstance();
        setTitle(m("Your Offer"));
        Rectangle mainRectangle = new Rectangle();
        mainRectangle.width = PConfig.getInstance().mainSize.first;
        mainRectangle.height = PConfig.getInstance().mainSize.second;
        mainRectangle.width /= 2;
        mainRectangle.height /= 2;
        setSize(mainRectangle.width, mainRectangle.height);

        setLocationRelativeTo(Main.mainFrame);
        Font font = new Font("Serif", Font.PLAIN, (int) (Metrics.getInstance().cardW / 5));
        int size = font.getSize();
        BufferedImage scaledLineImage = pUtil.scale(lineImage, size, size);
        Icon lineIcon = new ImageIcon(scaledLineImage);
        BufferedImage scaledSelectedLineImage = pUtil.scale(selectedLineImage, size, size);
        Icon selectedLineIcon = new ImageIcon(scaledSelectedLineImage);

        // 0. top label
        JLabel jLab = new JLabel(m("Your Tricks"), SwingConstants.CENTER);
        jLab.setFont(font);
        jLab.setOpaque(true);
        add(jLab, BorderLayout.NORTH);

        // 1. list of settings
        final String[] values = {
            "10",
            "9",
            "8",
            "7",
            "6",
            "5",
            "4",
            "3",
            "2",
            "1",
            "0",
        };

        final JLabel[] jLabels = new JLabel[values.length];
        jList = new JList<>(values);
        jList.setCellRenderer((jList, value, index, isSelected, cellHasFocus) -> {
            String text = m(value);
            JLabel jLabel = jLabels[index];
            if (jLabel == null) {
                jLabel = new JLabel(text, lineIcon, JLabel.LEFT);
                jLabels[index] = jLabel;
                jLabel.setFont(font);
                jLabel.setOpaque(true);
            }
            if (index > (ROUND_SIZE - minTricks) || index < (ROUND_SIZE - maxTricks)) {
                jLabel.setEnabled(false);
                jLabel.setForeground(Color.GRAY);
            } else if (isSelected) {
                jLabel.setIcon(selectedLineIcon);
                jLabel.setForeground(Color.RED);
            } else {
                jLabel.setIcon(lineIcon);
                jLabel.setForeground(Color.BLACK);
            }
            return jLabel;
        });
        jList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                int index = jList.getSelectedIndex();
                if (index >= (ROUND_SIZE - maxTricks) && index <= (ROUND_SIZE - minTricks)) {
                    selectedIndex = index;
                    acceptButton.setEnabled(true);
                } else {
                    jList.setSelectedIndex(selectedIndex);
                }
            }
        });
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.add(jList);
        add(centerPanel, BorderLayout.CENTER);

        // 2. bottom buttons
        JPanel jPanel = new JPanel();
        acceptButton = new JButton(m(TableLayout.ButtonCommand.accept.getName()));
        acceptButton.setEnabled(false);
        acceptButton.addActionListener(actionEvent -> {
            popupInstance.dispose();
            result = ROUND_SIZE - jList.getSelectedIndex();
        });
        jPanel.add(acceptButton);
        JButton cancelButton = new JButton(m(TableLayout.ButtonCommand.cancel.getName()));
        cancelButton.addActionListener(actionEvent -> {
            popupInstance.dispose();
            result = -1;
        });
        jPanel.add(cancelButton);
        add(jPanel, BorderLayout.SOUTH);
        setVisible(true);
    }
}