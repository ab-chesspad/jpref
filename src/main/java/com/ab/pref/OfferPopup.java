/*  This file is part of jpref.
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

package com.ab.pref;

import com.ab.jpref.engine.Bot;
import com.ab.jpref.engine.GameManager;
import com.ab.jpref.engine.MisereBot;
import com.ab.jpref.engine.Player;
import com.ab.pref.MainPanel.Host;
import com.ab.pref.config.Metrics;
import com.ab.pref.config.PConfig;
import com.ab.pref.widgets.ButtonPanel;
import com.ab.pref.widgets.PButton;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.image.BufferedImage;

import static com.ab.jpref.config.Config.Bid;
import static com.ab.jpref.config.Config.ROUND_SIZE;
import static com.ab.jpref.config.I18n.m;

public class OfferPopup extends JDialog {
    final Host host;

    private final PUtil pUtil = PUtil.getInstance();
    final PConfig pConfig;
    final GameManager gameManager;
    final OfferPopup popupInstance;

    final BufferedImage lineImage = pUtil.loadImage("buttons/radio.png");
    final BufferedImage selectedLineImage = pUtil.loadImage("buttons/radio-sel.png");

    final JList<String> jList;

    public OfferPopup(Host host) {
        super(host.mainFrame(), false);
        this.host = host;
        pConfig = PConfig.getInstance();
        popupInstance = this;

        gameManager = GameManager.getInstance();
        Player[] players = gameManager.getPlayers();
        Player player0 = players[0];
        int theirTricks = players[1].getTricks() + players[2].getTricks();
        int tricksEstimate;
        if (Bot.trickList == null) {
            if (gameManager.getMinBid().equals(Bid.BID_MISERE)) {
                tricksEstimate = ROUND_SIZE - players[gameManager.declarerNumber].getTricks();
                if (Bot.targetBot instanceof MisereBot) {
                    ((MisereBot)Bot.targetBot).getHoles(gameManager.declarerNumber);
                    tricksEstimate -= ((MisereBot)Bot.targetBot).holes.size();
                }
            } else {
                tricksEstimate = 0;
            }
        } else {
            tricksEstimate = Bot.trickList.getEstimate();
        }

        final int minTricks;
        final int maxTricks;
        if (player0.getBid().equals(Bid.BID_MISERE)) {
            minTricks = tricksEstimate;
            maxTricks = ROUND_SIZE - theirTricks;
        } else if (player0.getBid().equals(Bid.BID_WHIST)) {
            int _minTricks = players[0].getTricks();
            if (players[1].getBid() == Bid.BID_PASS) {
                _minTricks += players[1].getTricks();
            } else {
                _minTricks += players[2].getTricks();
            }
            if (gameManager.getMinBid().equals(Bid.BID_MISERE)) {
                minTricks = tricksEstimate;
                maxTricks = ROUND_SIZE - players[gameManager.declarerNumber].getTricks();
            } else {
                minTricks = _minTricks;
                maxTricks = ROUND_SIZE - tricksEstimate;
            }
        } else {
            // for tricks play
            if (player0.getTricks() > 0) {
                minTricks = player0.getTricks();
            } else {
                minTricks = 0;
            }
            if (ROUND_SIZE - theirTricks < tricksEstimate) {
                maxTricks = ROUND_SIZE - theirTricks;
            } else {
                maxTricks = tricksEstimate;
            }
        }

        setTitle(m("Your Offer"));
        Rectangle mainRectangle = (Rectangle) PConfig.getInstance().mainRectangle.get().clone();
        mainRectangle.width /= 4;
        mainRectangle.height /= 2;
        setSize(mainRectangle.width, mainRectangle.height);

        setLocationRelativeTo(host.mainFrame());
        Font font = new Font("Serif", Font.PLAIN, (int) (Metrics.getInstance().cardW / 5));
        int size = font.getSize();
        BufferedImage scaledLineImage = pUtil.scale(lineImage, size, size);
        Icon lineIcon = new ImageIcon(scaledLineImage);
        BufferedImage scaledSelectdLineImage = pUtil.scale(selectedLineImage, size, size);
        Icon selectedLineIcon = new ImageIcon(scaledSelectdLineImage);

        // 0. top label
        JLabel jLab = new JLabel(m("Your Tricks"), SwingConstants.CENTER);
        jLab.setFont(font);
        jLab.setOpaque(true);
        add(jLab, BorderLayout.NORTH);

        // 1. bottom buttons
        ButtonPanel buttonPanel = new ButtonPanel(.8, .8,
            new PButton.ButtonHandler[][] {
                {new PButton.ButtonHandler(MainPanel.ButtonCommand.accept, buttonCommand -> accept()),
                    new PButton.ButtonHandler(MainPanel.ButtonCommand.cancel, buttonCommand -> cancel())
                }});
        buttonPanel.setFont(font);
        add(buttonPanel, BorderLayout.SOUTH);
        final PButton okButton = buttonPanel.getButton(MainPanel.ButtonCommand.accept);
        okButton.setEnabled(false);

        // 2. list of settings
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

        jList = new JList<>(values);
        jList.setCellRenderer((jList, value, index, isSelected, cellHasFocus) -> {
                JLabel jLabel;
                String text = m(value);
                if (isSelected) {
                    jLabel = new JLabel(text, selectedLineIcon, JLabel.LEFT);
                } else {
                    jLabel = new JLabel(text, lineIcon, JLabel.LEFT);
                }
                jLabel.setFont(font);
                jLabel.setOpaque(true);
                if (index > (ROUND_SIZE - minTricks) || index < (ROUND_SIZE - maxTricks)) {
                    jLabel.setEnabled(false);
                    jLabel.setForeground(Color.GRAY);
                }
            return jLabel;
        });
        jList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                if (!event.getValueIsAdjusting()) {
                    okButton.setEnabled(true);
                }
            }
        });
        JScrollPane scrollPane = new JScrollPane(jList);
        add(scrollPane, BorderLayout.CENTER);

        setVisible(true);
    }

    private void accept() {
        int others = jList.getSelectedIndex();
        int acceptedTricks = ROUND_SIZE - others;
        Player[] players = gameManager.getPlayers();
        players[0].setTricks(acceptedTricks);
        if (players[2].getBid() == Bid.BID_PASS) {
            players[1].setTricks(others);
        } else {
            players[2].setTricks(others);
        }
        popupInstance.dispose();
        for (Player player : gameManager.getPlayers()) {
            player.abortThread(GameManager.RestartCommand.offer);
        }
    }

    private void cancel() {
        popupInstance.dispose();
    }
}