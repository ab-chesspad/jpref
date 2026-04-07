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
 * Copyright (C) 2025-2026 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 2/23/2025
 */
package com.ab.pref;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import com.ab.jpref.config.Config;
import com.ab.jpref.config.Config.Bid;
import com.ab.jpref.engine.Player;
import com.ab.jpref.engine.GameManager;
import com.ab.jpref.engine.GameManager.RoundStage;
import com.ab.jpref.engine.Trick;
import com.ab.pref.config.Metrics;
import com.ab.pref.config.PConfig;
import com.ab.jpref.config.I18n;
import com.ab.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

class MainPanelLayout {
    public static final boolean DEBUG_LOG = false;
    public static final int NOP = Config.NOP;   // Number of players
    private final PUtil pUtil = PUtil.getInstance();
    final MainPanel mainPanel;

    private final String BACK_FILENAME = "b4.png";
    private final DeckAttributes deckAttributes =
//            new DeckAttributes("alt-deck.png", 0, 5, true);
            new DeckAttributes("full-deck.png", 4, 46, false);
    private final int[] suitOrderInDeck = {3, 2, 1, 0};

    final BufferedImage sourceDeckImage, sourceBackImage;
    final Image[][] cardImages = new Image[Card.TOTAL_SUITS][Card.TOTAL_RANKS];
    final BufferedImage elderHandImage = pUtil.loadImage("buttons/hand.png");
    final Metrics metrics = Metrics.getInstance();
    final HandVisualData[] handVisualData = new HandVisualData[4];
    BufferedImage scaledDeckImage, scaledBackImage;
    GameManager gameManager;
    int panelWidth, panelHeight;
    final JLabel[] jLabels = new JLabel[4];

    public final Object lock = new Object();

    MainPanelLayout(MainPanel mainPanel) {
        this.mainPanel = mainPanel;
        mainPanel.setLayout(null);
        mainPanel.setOpaque(false);

        sourceDeckImage = loadImageCrop(deckAttributes.deckImageFile);
        int fullW = sourceDeckImage.getWidth(null);
        int fullH = sourceDeckImage.getHeight(null);
        double w = (double) fullW / 13;
        double h = (double) fullH / deckAttributes.deckRows;
        metrics.setCardAspectRatio(h / w);
        sourceBackImage = loadImageCrop(BACK_FILENAME);
        for (int i = 0; i < jLabels.length; ++i) {
            jLabels[i] = new JLabel();
            jLabels[i].setVisible(true);
            jLabels[i].setOpaque(true);
            mainPanel.add(jLabels[i]);
        }
    }

    private BufferedImage loadImageCrop(String name) {
        BufferedImage image = pUtil.loadImage("cards/" + name);
        int w = image.getWidth();
        int h = image.getHeight();
        image = image.getSubimage(5, deckAttributes.yMargin, w - 10, h - 2 * deckAttributes.yMargin);
        return image;
    }

    Card getCard(Point point) {
        HandVisualData currentHandVisualData =
            handVisualData[mainPanel.currentPlayer.getNumber()];
        // convert click point to card
        Card card = null;
        for (int j = currentHandVisualData.cardPositions.size() - 1; j >= 0; --j) {
            Rectangle r = new Rectangle(currentHandVisualData.cardPositions.get(j));
            r.width = (int)metrics.cardW;
            r.height = (int)metrics.cardH;
            Logger.printf(DEBUG_LOG, "checking rect %s, %s\n", currentHandVisualData.allCards.get(j), r);
            if (r.contains(point)) {
                card = currentHandVisualData.allCards.get(j);
                break;
            }
        }
        return card;
    }

    private Image getCardImage(Card card) {
        return cardImages[card.getSuit().getValue()][card.getRank().ordinal() - 1];
    }

    public void update() {
        gameManager = GameManager.getInstance();
        metrics.recalculateSizes();
        int width = metrics.panelWidth;
        int height = metrics.panelHeight;
        Rectangle bounds;

        mainPanel.trickPanel.setPreferredSize(new Dimension((int)(2 * metrics.cardW), (int)(2 *metrics.cardH)));
        centerPanel(mainPanel.trickPanel);
        mainPanel.trickPanel.setBackground(Color.darkGray);

        centerPanel(mainPanel.bidPanel);
        centerPanel(mainPanel.declareRoundPanel);
        centerPanel(mainPanel.dropPanel);
        centerPanel(mainPanel.whistSelectionPanel);
        centerPanel(mainPanel.whistOptionPanel);

        // to southwest
        bounds = mainPanel.buttonPanel.getBounds();
        bounds.x = width - mainPanel.buttonPanel.getPreferredSize().width - 2 * metrics.xMargin;
        bounds.y = height - mainPanel.buttonPanel.getPreferredSize().height - 2 * metrics.yMargin;
        mainPanel.buttonPanel.setBounds(bounds);

        bounds = mainPanel.menuPanel.getBounds();
        bounds.x = width - mainPanel.menuPanel.getPreferredSize().width - 2 * metrics.xMargin;
        bounds.y = height - mainPanel.menuPanel.getPreferredSize().height - 2 * metrics.yMargin;
        mainPanel.menuPanel.setBounds(bounds);

        recalculateSizes();
    }

    private void recalculateSizes() {
        synchronized (lock)
        {
            if (gameManager == null) {
                return;
            }
            metrics.recalculateSizes();
            if (metrics.cardW < 0) {
                return;
            }
            int cardW = (int)metrics.cardW;
            int cardH = (int)metrics.cardH;
            int newDeckWidth = cardW * 13;
            int newDeckHeight = cardH * deckAttributes.deckRows;
            scaledDeckImage = pUtil.scale(sourceDeckImage, newDeckWidth, newDeckHeight);
            Logger.printf(DEBUG_LOG, "scaled Deck %dx%d\n",
                    scaledDeckImage.getWidth(), scaledDeckImage.getHeight());

            for (int j = 0; j < Card.TOTAL_SUITS; ++j) {
                int yS = suitOrderInDeck[j] * cardH;
                for (int i = 0; i < Card.TOTAL_RANKS; ++i) {
                    int col = Card.Rank.values()[i].getValue() ;
                    if (i == Card.TOTAL_RANKS - 1) {
                        col = 0;
                    }
                    int xS = col * cardW;
                    try {
                        cardImages[j][i] = scaledDeckImage.getSubimage(xS, yS, cardW, cardH);
                    } catch (RasterFormatException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            scaledBackImage = pUtil.scale(sourceBackImage, (int) metrics.cardW, (int) metrics.cardH);
            handVisualData[0] = new HandVisualData(metrics.xVisible, 0, MainPanel.Alignment.South);
            handVisualData[1] = new HandVisualData(0, metrics.yVisible, MainPanel.Alignment.West);
            handVisualData[2] = new HandVisualData(0, metrics.yVisible, MainPanel.Alignment.East);
            // talon
            handVisualData[3] = new HandVisualData(metrics.xVisible, 0, MainPanel.Alignment.South);

            ArrayList<Player> players = new ArrayList<>(Arrays.asList(gameManager.getPlayers()));
            for (int i = 0; i < players.size(); ++i) {
                if (players.get(i) == null) {
                    return;
                }
                JLabel jLabel = jLabels[i];
                jLabel.setBounds(handVisualData[i].label);
                int size = metrics.font.getSize() * 2 / 3;
                jLabel.setFont(new Font("Serif", Font.PLAIN, size));
                int height = metrics.font.getSize();
                ImageIcon elderHandIcon = null;
                Player player = players.get(i);
                String text = player.getName();
                if (mainPanel.isStage(RoundStage.bidding) ||
                    mainPanel.isStage(RoundStage.showTalon) ||
                    mainPanel.isStage(RoundStage.selectWhistOption) ||
                    mainPanel.isStage(RoundStage.drop) ||
                    mainPanel.isStage(RoundStage.play.declareRound) ||
                    mainPanel.isStage(RoundStage.responseOnDeclaration)) {
                    Bid bid = player.getBid();
                    if (!Bid.BID_UNDEFINED.equals(bid)) {
                        text += ": " + I18n.m(bid.getName());
                    }
                    if (player.getNumber() == gameManager.elderHand) {
                        BufferedImage scaledImage = pUtil.scale(elderHandImage, height, height);
                        elderHandIcon = new ImageIcon(scaledImage);
                    }
                } else {
                    int tricks = player.getTricks();
                    if (tricks > 0) {
                        text += ": " + tricks;
                    }
                }
                jLabel.setText(text);

                if (player == mainPanel.currentPlayer) {
                    jLabel.setBackground(PConfig.getInstance().currentPlayerBGColor.getColor());
                } else {
                    jLabel.setBackground(PConfig.getInstance().labelBGColor.getColor());
                }
                jLabel.setIcon(elderHandIcon);

                handVisualData[i].player = player;
                handVisualData[i].hand = jLabel;
                handVisualData[i].allCards.clear();
                handVisualData[i].totalSuits = 0;
                try {
                    Iterator<CardSet> suitIterator = players.get(i).getMyHand().suitIterator();
                    while (suitIterator.hasNext()) {
                        CardSet cardList = suitIterator.next();
                        ++handVisualData[i].totalSuits;
                        handVisualData[i].allCards.addAll(cardList.toCardList());
                    }
                } catch (NullPointerException e) {
                    throw new RuntimeException(e);
                }
                handVisualData[i].showFaces = mainPanel.showCards(i);
            }
            int i = 3;
            handVisualData[i].allCards = gameManager.getTalonCards();
            handVisualData[i].showFaces = mainPanel.showCards(i);
            if (mainPanel.isStage(RoundStage.showTalon)) {
                handVisualData[i].showFaces = true;
            }
        }
    }

    synchronized void paintComponent(Graphics g) {
//        synchronized (lock)
        {
            if (metrics.panelWidth == 0 || gameManager == null) {
                return;
            }
            Graphics2D g2d = (Graphics2D) g;

            g2d.setColor(PConfig.getInstance().bgColor.getColor());
//        g2d.setColor(Color.white);
            g2d.fillRect(0, 0, metrics.panelWidth, metrics.panelHeight);

            ArrayList<Player> players = new ArrayList<>(Arrays.asList(gameManager.getPlayers()));
            for (int i = 0; i < players.size(); ++i) {
                if (handVisualData[i] != null && handVisualData[i].hand != null) {
                    paintHand(g2d, handVisualData[i]);
                }
            }
            int i = 3;
            if (handVisualData[i] != null) {
                paintHand(g2d, handVisualData[i]);
            }
            Trick trick = gameManager.getTrick();
            paintTrick(g2d, trick.cards2List());
        }
    }

    void paintTrick(Graphics2D g, CardList trickCards) {
        Point[] positions = {
            new Point(-(int)(metrics.cardW * .5), -(int)(metrics.cardH * .9)),
            new Point(-(int)(metrics.cardW * .5), -(int)(metrics.cardH * .25)),
            new Point(-(int)(metrics.cardW * .75), -(int)(metrics.cardH * .75)),
            new Point(-(int)(metrics.cardW * .25), -(int)(metrics.cardH * .7)),
        };
        if (trickCards.isEmpty()) {
            return;
        }

        int panelWidth = g.getClipBounds().width;
        int panelHeight = g.getClipBounds().height;
        try {
            int turn = 0;
            for (int j = 0; j < trickCards.size(); ++j) {
                Card card = trickCards.get(j);
                Image im = getCardImage(card);
                int i = turn + 1;
                if (j == 0 && trickCards.size() > NOP) {
                    i = 0;
                    --turn;
                }
                int x = positions[i].x + panelWidth / 2;
                int y = positions[i].y + panelHeight / 2;
                Logger.printf(DEBUG_LOG, "trick %s %d\n", card, i);
                g.drawImage(im, x, y, null);
                turn = ++turn % NOP;
            }
        } catch (java.util.ConcurrentModificationException e) {
            // todo: fix it!
            // it happens when all players are bots and GameManager.TRICK_TIMEOUT = 0
            Logger.println(e.getMessage());
        }
    }

    private void centerPanel(JPanel p) {
        int width = metrics.panelWidth;
        int height = metrics.panelHeight;
        Rectangle bounds = p.getBounds();
        bounds.x = (width - p.getPreferredSize().width) / 2;
        bounds.y = (height - p.getPreferredSize().height) / 2;
        p.setBounds(bounds);
    }

    // text should be translated already
    void showMessage(String text) {
//        UIManager.put("OptionPane.minimumSize", new Dimension(panelWidth - 20,panelHeight / 5));
        String msg = "<html><b>" + I18n.m(text) + "</b></html>";
        JLabel label = new JLabel(msg);
        int fontSize = panelHeight / 50;
        if (fontSize < 15) {
            fontSize = 15;
        }
        label.setFont(new Font("Arial", Font.BOLD, fontSize));
        JOptionPane.showMessageDialog(mainPanel, label,"Message", JOptionPane.WARNING_MESSAGE);
    }

    private synchronized void paintHand(Graphics2D g, HandVisualData handVisualData) {
        int actualW, actualH;
        int totalCards = handVisualData.allCards.size();
        int totalSuits = handVisualData.totalSuits;
        int cardW = 0, cardH = 0, dx = 0, dy = 0;

        if (handVisualData.showFaces && handVisualData.player != null) {
            // + gap between suits == handVisualData.dx / 2? todo!
            actualW = (int) ((totalSuits + (totalCards - totalSuits) * metrics.xVisible + (totalSuits - 1) * metrics.xSuitGap) * metrics.cardW);
            actualH = (int) ((totalSuits + (totalCards - totalSuits) * metrics.yVisible + (totalSuits - 1) * metrics.ySuitGap) * metrics.cardH);
        } else {
            actualW = (int) (((totalCards - 1) * metrics.xVisible + 1) * metrics.cardW);
            actualH = (int) (((totalCards - 1) * metrics.yVisible + 1) * metrics.cardH);
        }

        int x, y;

        if (handVisualData.alignment.equals(MainPanel.Alignment.South)) {
            // horizontal, bottom & talon
            cardW = (int) metrics.cardW;
            dx = (int) (metrics.cardW * metrics.xVisible);
            x = (metrics.panelWidth - actualW) / 2;
            if (handVisualData.player == null) {
                // talon
                y = metrics.yMargin;
            } else {
                // bottom
                y = metrics.panelHeight - (int) metrics.cardH - metrics.yMargin;
            }
            Logger.printf(DEBUG_LOG, "horiz center: %dx%d, panel %dx%d, %s\n",
                x, y, metrics.panelWidth, metrics.panelHeight, handVisualData.player);
        } else {
            // vertical, west, east
            cardH = (int) metrics.cardH;
            dy = (int) (metrics.cardH * metrics.yVisible);
            actualW = (int) metrics.cardW;
            y = metrics.yMargin;
            if (!metrics.horizontalLayout) {
                y += metrics.yMargin + handVisualData.label.y + handVisualData.label.height;
            }
            if (handVisualData.alignment.equals(MainPanel.Alignment.West)) {
                x = metrics.xMargin;
            } else {    // if (handVisualData.alignment.equals(MainPanel.Alignment.East)) {
                x = metrics.panelWidth - actualW - 2 * metrics.xMargin;
            }
        }
        handVisualData.cardPositions.clear();
        int i = 0;
        Card.Suit prevSuit = null;
        if (!handVisualData.allCards.isEmpty()) {
            prevSuit = handVisualData.allCards.get(0).getSuit();
        }
        int xOffset = (int) ((1 - handVisualData.xVisible + metrics.xSuitGap) * cardW);
        if (handVisualData.player == null) {
            // talon, quick & dirty
            xOffset = 0;
        }
        int yOffset = (int) ((1 - handVisualData.yVisible + metrics.ySuitGap) * cardH);
        for (Card card : handVisualData.allCards) {
            if (!card.getSuit().equals(prevSuit)) {
                prevSuit = card.getSuit();
                if (handVisualData.showFaces) {
                    x += xOffset;
                    y += yOffset;
                }
            }
            boolean show = handVisualData.showFaces;
            if (handVisualData.player == null) {
                if (!mainPanel.isStage(RoundStage.bidding)) {
                    if (i == totalCards - 1) {
                        show = true;
                    }
                }
            }
            Point position = new Point(x, y);
            if (mainPanel.selectedCards.contains(card)) {
                if (handVisualData.xVisible == 0) {
                    // vertical
                    if (x < metrics.panelWidth / 2) {
                        position.x += (int) (metrics.xSelected * metrics.cardW);
                    } else {
                        position.x -= (int) (metrics.xSelected * metrics.cardW);
                    }
                }
                if (handVisualData.yVisible == 0) {
                    // horisontal
                    if (y < metrics.panelHeight / 2) {
                        position.y += (int) (metrics.ySelected * metrics.cardH);
                    } else {
                        position.y -= (int) (metrics.ySelected * metrics.cardH);
                    }
                }
                Logger.printf(DEBUG_LOG, "clicked %s\n", card);
            }
            Image im;
            if (show) {
                im = getCardImage(card);
            } else {
                try {
                    im = scaledBackImage.getSubimage(0, 0, (int) metrics.cardW, (int) metrics.cardH);
                } catch (RasterFormatException e) {
                    Logger.printf(DEBUG_LOG, "scaledBackImage %dx%d, subimage %dx%d\n",
                        scaledBackImage.getWidth(), scaledDeckImage.getHeight(), (int) metrics.cardW, (int) metrics.cardH);
                    continue;   // skip it, happens when window is expanding
                }
            }
            g.drawImage(im, position.x, position.y, mainPanel);
            handVisualData.cardPositions.add(position);
            x += dx;
            y += dy;
            ++i;
        }
        String name = "";
        if (handVisualData.player != null) {
            name = handVisualData.player.getName();
        }
        if (handVisualData.player == null) {
            Logger.printf(DEBUG_LOG, "%s positions %d:\n%s\n", name,
                handVisualData.cardPositions.size(), handVisualData.cardPositions);
        }
    }

    ////////////////////////////////////////////
    class HandVisualData {
        double xVisible, yVisible;
        final MainPanel.Alignment alignment;
        final Rectangle label = new Rectangle();
        final java.util.List<Point> cardPositions = new ArrayList<>();
        CardList allCards = new CardList();
        int totalSuits;
        Player player;
        JLabel hand;
        boolean showFaces;

        HandVisualData(double xVisible, double yVisible, MainPanel.Alignment alignment) {
            this.xVisible = xVisible;
            this.yVisible = yVisible;
            this.alignment = alignment;

            label.width = (int)(metrics.cardW * metrics.xLabel);
            label.height = (int)(metrics.cardH * metrics.yLabel);
            if (alignment == MainPanel.Alignment.South) {
                this.xVisible = metrics.xVisible;
                this.yVisible = 0;
                // bottom hand
                label.x = metrics.xMargin;
                label.y = metrics.panelHeight - 2 * metrics.yMargin - label.height;
            } else {
                // vertical
                label.y = metrics.yMargin;
                if (alignment.equals(MainPanel.Alignment.West)) {
                    label.x = metrics.xMargin;
                    if (metrics.horizontalLayout) {
                        label.x += (int)(metrics.xMargin + metrics.cardW);
                    }
                } else {
                    label.x = metrics.panelWidth - 3 * metrics.xMargin - label.width;
                    if (metrics.horizontalLayout) {
                        label.x -= (int)(metrics.xMargin + metrics.cardW);
                    }
                }
            }
        }
    }

    static class DeckAttributes {
        final int yMargin;
        final String deckImageFile;
        final int deckRows;
        final boolean needBG;

        DeckAttributes(String deckImageFile, int deckRows, int yMargin, boolean needBG) {
            this.deckImageFile = deckImageFile;
            this.deckRows = deckRows;
            this.needBG = needBG;
            this.yMargin = yMargin;
        }
    }
}