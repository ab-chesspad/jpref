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
 * Created: 1/15/2025
 */
package com.ab.jpref.gui;

import static com.ab.jpref.cards.Card.TOTAL_RANKS;
import static com.ab.jpref.cards.Card.TOTAL_SUITS;
import static com.ab.jpref.ui.TableLayout.getInstance;

import com.ab.jpref.cards.Card;

import static com.ab.jpref.config.I18n.m;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Metrics;
import com.ab.jpref.engine.GameManager;
import com.ab.jpref.engine.Player;
import com.ab.jpref.gui.config.PConfig;
import com.ab.jpref.ui.TableLayout;
import com.ab.jpref.ui.Widget;
import com.ab.util.Couple;
import com.ab.util.Logger;
import com.ab.util.Pair;

import com.ab.jpref.ui.Host;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.util.List;
import java.util.ArrayList;

public class MainPanel extends JLayeredPane implements TableLayout.GUI<Graphics> {
    public static boolean DEBUG_LOG = false;

    private final Color LBL_BG_COLOR = Color.yellow;
    private final Color LBL_SELECTED_BG_COLOR = Color.green;

    private final PUtil pUtil = PUtil.getInstance();
    final Metrics metrics = Metrics.getInstance();

    final Host host;
    final BufferedImage[] suitImages = new BufferedImage[TOTAL_SUITS];
    BufferedImage sourceBackImage;

    final Image[][] cardImages = new Image[TOTAL_SUITS][TOTAL_RANKS];
    BufferedImage backImage;

    final BufferedImage sourceElderHandImage = pUtil.loadImage("buttons/hand.png");
    BufferedImage elderHandImage;

    int panelWidth = -1, panelHeight = -1;

    final List<Pair<Widget, JComponent>> widgets = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private TableLayout<Graphics> tableLayout() {
        return (TableLayout<Graphics>)TableLayout.getInstance();
    }

    public MainPanel(Host host) {
        this.host = host;
        this.setLayout(null);
        this.setOpaque(false);
        loadImages();
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                getInstance().onMouseClick(e.getX(), e.getY());
            }
        });
    }

    private void loadImages() {
        final int cardHeight = 204;
        final int[] suitStarts = {0, 215, 429, 644};
        BufferedImage sourceDeckImage = pUtil.loadImage("cards/deck.png");
        int suitWidth = sourceDeckImage.getWidth();
        for (int i = 0; i < suitImages.length; ++i) {
            // descending order
            int j = suitImages.length - 1 - i;
            suitImages[j] = sourceDeckImage.getSubimage(0, suitStarts[i], suitWidth, cardHeight);
        }

        metrics.setCardAspectRatio((double) cardHeight * 8 / suitWidth);
        sourceBackImage = pUtil.loadImage("cards/b5.jpg");
    }

    private Image getCardImage(Card card) {
        return cardImages[card.getSuit().getValue()][card.getRank().ordinal() - 1];
    }

    private void recalculateSizes() {
        metrics.recalculateSizes();
        if (metrics.cardW <= 0) {
            return;
        }
        if (panelWidth == metrics.panelWidth && panelHeight == metrics.panelHeight) {
            return;
        }
        panelWidth = metrics.panelWidth;
        panelHeight = metrics.panelHeight;

        int cardW = (int)metrics.cardW;
        int cardH = (int)metrics.cardH;
        for (int j = 0; j < TOTAL_SUITS; ++j) {
            BufferedImage scaledSuitImage = pUtil.scale(suitImages[j], cardW * 8, cardH);
            for (int i = 0; i < TOTAL_RANKS; ++i) {
                int col = Card.Rank.values()[i].getValue() - Card.Rank.SIX.getValue();
                int xS = col * cardW;
                try {
                    cardImages[j][i] = scaledSuitImage.getSubimage(xS, 0, cardW, cardH);
                } catch (RasterFormatException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        backImage = pUtil.scale(sourceBackImage, cardW, cardH);
        int size = (int)(cardW * metrics.wElderHand);
        elderHandImage = pUtil.scale(sourceElderHandImage, size, size);
    }

    @Override
    public void add(Widget widget) {
        JComponent view;
        String text = widget.getText();
        if (widget.getCommand() == null) {
            JLabel lbl = new JLabel();
            lbl.setOpaque(true);
//            lbl.setBackground(Color.yellow);
            lbl.setForeground(Color.red);
            lbl.setHorizontalAlignment(JLabel.CENTER);
            lbl.setText(text);
            view = lbl;
        } else {
            JButton b = new JButton();
            Image image = pUtil.loadImage(String.format("buttons/%s.png", widget.getCommand().toString()));
            if (image != null) {
                widget.setUserObject(image);
            }
            b.setHorizontalTextPosition(JButton.CENTER);
            b.setVerticalTextPosition(JButton.CENTER);
            b.addActionListener(actionEvent -> widget.onClick());
            view = b;
        }
        this.add(view, 0);
        widgets.add(new Pair<>(widget, view));
    }

    @Override
    public void update() {
        if (host == null) {
            return;
        }
        int fontSize = (int)(metrics.cardW * .3);
        Font font = new Font("Serif", Font.PLAIN, fontSize);
        for (Pair<Widget, JComponent> pair : widgets) {
            Widget widget = pair.first;
            JComponent jComponent = pair.second;
            if (!widget.isVisible()) {
                jComponent.setVisible(false);
                continue;
            }
            jComponent.setVisible(true);
            jComponent.setFont(font);
            if (widget.getColor() == Widget.RED_COLOR) {
                jComponent.setForeground(Color.red);
            } else {
                jComponent.setForeground(Color.black);
            }
            jComponent.setBounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
            String text = m(widget.getText());
            if (jComponent instanceof JLabel) {
                ((JLabel)jComponent).setText(text);
                Player p = getInstance().getCurrentPlayer();
                if (p == null || p.getNumber() != widget.getNumber()) {
                    jComponent.setBackground(LBL_BG_COLOR);
                } else {
                    jComponent.setBackground(LBL_SELECTED_BG_COLOR);
                }
            } else {
                jComponent.setEnabled(widget.isEnabled());
                Image image = (Image) widget.getUserObject();
                if (image != null) {
                    image = image.getScaledInstance(widget.getWidth(), widget.getHeight(), Image.SCALE_DEFAULT);
                    ((JButton)jComponent).setIcon(new ImageIcon(image));
                    int _fontSize = fontSize;
                    Font _font = new Font("Serif", Font.PLAIN, _fontSize);
                    jComponent.setFont(_font);
                }
                ((JButton)jComponent).setText(text);
            }
        }
        host.repaint();

        if ((getInstance()).getCurrentPlayer() == null) {
            // wait cursor
            this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        } else {
            // normal cursor
            this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
    }

    @Override
    public void showMessage(String text) {
        JDialog dialog = new JDialog(Main.mainFrame, m("Message"), true);
        int width = Main.mainFrame.getWidth();
        int height = Main.mainFrame.getHeight();
        dialog.setSize(width, height);
        dialog.setLocationRelativeTo(Main.mainFrame);
        dialog.setLayout(new BorderLayout());
        JEditorPane editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");
        Font font = new Font("Serif", Font.PLAIN, (int)metrics.fontSize / 2);
        editorPane.setFont(font);
        HTMLEditorKit kit = (HTMLEditorKit) editorPane.getEditorKit();
        kit.getStyleSheet().addRule(String.format("body { font-family: %s; font-size: %dpt; }",
            font.getFamily(), font.getSize()));
        editorPane.setText(text);
        editorPane.setCaretPosition(0);
        dialog.add(new JScrollPane(editorPane,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        JButton okButton = new JButton(m("Continue"));
        okButton.addActionListener(e -> dialog.dispose());
        dialog.add(okButton, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    @Override
    public String getUserComments() {
        JDialog dialog = new JDialog(Main.mainFrame, m("Comments"), true);
        int width = Main.mainFrame.getWidth() / 2;
        int height = Main.mainFrame.getHeight() / 2;
        dialog.setSize(width, height);
        dialog.setLocationRelativeTo(Main.mainFrame);
        dialog.setLayout(new BorderLayout());
        JTextArea textArea = new JTextArea(10, 30);
        textArea.setFont(new Font("Serif", Font.PLAIN, (int)metrics.fontSize / 2));
        dialog.add(new JScrollPane(textArea), BorderLayout.CENTER);
        JButton okButton = new JButton(m("OK"));
        okButton.addActionListener(e -> dialog.dispose());
        dialog.add(okButton, BorderLayout.SOUTH);
        dialog.setVisible(true);
        return textArea.getText();
    }

    @Override
    public void showLastTrick(CardList cards) {
        JDialog dialog = new JDialog(Main.mainFrame, null, true);
        dialog.setUndecorated(true);
        dialog.setLayout(new BorderLayout());

        int pw = (int)(metrics.cardW * 2);
        int ph = (int)(metrics.cardH * 2);
        JPanel trickPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                tableLayout().paintTrick(g, cards, getWidth() / 2, getHeight() / 2);
            }
        };
        trickPanel.setBackground(Color.green);
        trickPanel.setPreferredSize(new Dimension(pw, ph));
        dialog.add(trickPanel, BorderLayout.CENTER);

        JButton okButton = new JButton(m("Continue"));
        okButton.addActionListener(e -> dialog.dispose());
        dialog.add(okButton, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(Main.mainFrame);
        dialog.setVisible(true);
    }

    @Override
    public GameManager.RestartCommand showScores(boolean showButtons) {
        StatusPopup statusPopup = new StatusPopup(showButtons);
        return statusPopup.result;
    }

    @Override
    public int showOffer(int minTricks, int maxTricks) {
        OfferPopup offerPopup = new OfferPopup(minTricks, maxTricks);
        return offerPopup.result;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (g == null || getInstance() == null) {
            return;
        }

        recalculateSizes();
        g.setColor(PConfig.getInstance().bgColor.getColor());
        g.fillRect(0, 0, metrics.panelWidth, metrics.panelHeight);

        tableLayout().paint(g);
        Couple<Integer> elderHandLocation = getInstance().elderHandLocation;
        if (elderHandLocation.first != null) {
            g.drawImage(elderHandImage, elderHandLocation.first, elderHandLocation.second, this);
        }
    }

    @Override
    public void paint(Graphics g, Card card, int x, int y) {
        Logger.printf(DEBUG_LOG, "paint %s, %d, %d\n", card, x, y);
        Image image = getCardImage(card);
        g.drawImage(image, x, y, this);
    }

    @Override
    public void paintBack(Graphics g, int x, int y) {
        g.drawImage(backImage, x, y, this);
    }
}