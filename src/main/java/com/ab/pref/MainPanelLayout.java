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
 * Created: 2/23/25
 */
package com.ab.pref;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Config;
import com.ab.jpref.engine.Player;
import com.ab.jpref.engine.GameManager;
import com.ab.util.I18n;
import com.ab.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;

class MainPanelLayout {
    public static final boolean DEBUG = false;
    public static final JPrefConfig jPrefConfig = JPrefConfig.getInstance();
    final MainPanel mainPanel;

//    private final String BACK_FILENAME = "back-5.png";
    private final String BACK_FILENAME = "b4.png";
    //    private final String BACK_FILENAME = "back.png";
    private final DeckAttributes deckAttributes =
//            new DeckAttributes("alt-deck.png", 0, 5, true);
            new DeckAttributes("full-deck.png", 4, 46, false);
    private final int[] suitOrderInDeck = {3, 2, 1, 0};

    final BufferedImage sourceDeckImage, sourceBackImage;
    BufferedImage scaledDeckImage, scaledBackImage;
    final Image[][] cardImages = new Image[Card.Suit.values().length - 1][Card.Rank.values().length];
    final Metrics metrics = Metrics.getInstance();
    final HandVisualData[] handVisualData = new HandVisualData[4];
    int panelWidth, panelHeight;

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
        metrics.cardAspectRatio = h / w;
        sourceBackImage = loadImageCrop(BACK_FILENAME);

    }

    private BufferedImage loadImageCrop(String name) {
        BufferedImage image = Util.loadImage("cards/" + name);
        int w = image.getWidth();
        int h = image.getHeight();
        image = image.getSubimage(5, deckAttributes.yMargin, w - 10, h - 2 * deckAttributes.yMargin);
        return image;
    }

    Card getCard(Point point) {
        HandVisualData currentHandVisualData = null;
        int i = -1;
        for (Player p : GameManager.getInstance().getPlayers()) {
            ++i;
            if (p == mainPanel.currentPlayer) {
                currentHandVisualData = handVisualData[i];
                break;
            }
        }
        // convert click point to card
        Card card = null;
        for (int j = currentHandVisualData.cardPositions.size() - 1; j >= 0; --j) {
            Rectangle r = new Rectangle(currentHandVisualData.cardPositions.get(j));
            r.width = (int)metrics.cardW;
            r.height = (int)metrics.cardH;
            Logger.printf(DEBUG, "checking rect %s, %s\n", currentHandVisualData.allCards.get(j), r);
            if (r.contains(point)) {
                card = currentHandVisualData.allCards.get(j);
                break;
            }
        }
        return card;
    }

    private Image getCardImage(Card card) {
        return cardImages[card.getSuit().getValue()][card.getRank().ordinal()];
    }

    public void update() {
        metrics.recalculateSizes();
        int width = metrics.panelWidth;
        int height = metrics.panelHeight;
        Rectangle bounds;

        centerPanel(mainPanel.firstBidPanel);
        centerPanel(mainPanel.bidPanel);
        centerPanel(mainPanel.declareGamePanel);

        // to southwest
        bounds = mainPanel.buttonPanel.getBounds();
        bounds.x = width - mainPanel.buttonPanel.getPreferredSize().width - metrics.xMargin;
        bounds.y = height - mainPanel.buttonPanel.getPreferredSize().height - metrics.yMargin;
        mainPanel.buttonPanel.setBounds(bounds);

        bounds = mainPanel.menuPanel.getBounds();
        bounds.x = width - mainPanel.menuPanel.getPreferredSize().width - metrics.xMargin;
        bounds.y = height - mainPanel.menuPanel.getPreferredSize().height - metrics.yMargin;
        mainPanel.menuPanel.setBounds(bounds);


    }

    private void recalculateSizes() {
        synchronized (lock)
        {
            metrics.recalculateSizes();
            int newDeckWidth = (int) (metrics.cardW * 13);
            int newDeckHeight = (int) (metrics.cardH * deckAttributes.deckRows);
            scaledDeckImage = Util.scale(sourceDeckImage, newDeckWidth, newDeckHeight);
            Logger.printf(DEBUG, "scaled Deck %dx%d\n",
                    scaledDeckImage.getWidth(), scaledDeckImage.getHeight());

            for (int j = 0; j < Card.Suit.values().length - 1; ++j) {
                int yS = (int) (suitOrderInDeck[j] * metrics.cardH);
                for (int i = 1; i < Card.Rank.values().length; ++i) {
                    int col = Card.Rank.values()[i].getValue() - 1;
                    if (i == Card.Rank.values().length - 1) {
                        col = 0;
                    }
                    int xS = (int) (col * metrics.cardW);
                    Logger.printf(DEBUG, "%s src %dx%d- %dx%d\n",
                            new Card(Card.Suit.values()[j], Card.Rank.values()[i]).toString(),
                            xS, yS, (int) metrics.cardW, (int) metrics.cardH);
                    cardImages[j][i] = scaledDeckImage.getSubimage(xS, yS, (int) metrics.cardW, (int) metrics.cardH);
                }
            }

            scaledBackImage = Util.scale(sourceBackImage, (int) metrics.cardW, (int) metrics.cardH);

            if (metrics.horizontalLayout) {
//                int _w = (int) (metrics.cardW * metrics.wHand); // hand width
//                int _h = (int) metrics.cardH;           // hand height
//                int x = (int) (metrics.xLabel * metrics.cardW + 2 * metrics.xMargin);
                // bottom player:
//            int _x = (int)((metrics.panelWidth - wHand) / 2 - xLabel * metrics.cardW - metrics.xMargin);
                handVisualData[0] = new HandVisualData(metrics.xVisible, 0, MainPanel.Alignment.South);
                handVisualData[1] = new HandVisualData(metrics.xVisible, 0, MainPanel.Alignment.West);
                handVisualData[2] = new HandVisualData(metrics.xVisible, 0, MainPanel.Alignment.East);
//                x = (int) (metrics.panelWidth - _w - (metrics.xLabel * metrics.cardW)) / 2 + metrics.xMargin;
                // talon
                handVisualData[3] = new HandVisualData(metrics.xVisible, 0, MainPanel.Alignment.South);
            } else {
//                int dy = (int) (metrics.cardH * metrics.hHand);
//                int x = (metrics.panelWidth - (int) (metrics.cardW * metrics.wHand)) / 2 + metrics.xMargin;
                handVisualData[0] = new HandVisualData(metrics.xVisible, 0, MainPanel.Alignment.South);
                handVisualData[1] = new HandVisualData(0, metrics.yVisible, MainPanel.Alignment.West);
                handVisualData[2] = new HandVisualData(0, metrics.yVisible, MainPanel.Alignment.East);
                // talon
//                handVisualData[3] = new HandVisualData((int) metrics.cardW / 2, 0, MainPanel.Alignment.South);
                handVisualData[3] = new HandVisualData(metrics.xVisible, 0, MainPanel.Alignment.South);
            }
        }
    }

    void paintComponent(Graphics g) {
        synchronized (lock) {
            if (metrics.panelWidth != panelWidth ||
                    metrics.panelHeight != panelHeight) {
                panelWidth = metrics.panelWidth;
                panelHeight = metrics.panelHeight;
                recalculateSizes();
//            scorePanel._update();
            }

            Logger.printf(DEBUG, "paintComponent %dx%d - %dx%d\n",
                    metrics.panelWidth, metrics.panelHeight, panelWidth, panelHeight);

            Graphics2D g2d = (Graphics2D) g;

            g2d.setColor(jPrefConfig.bgColor.getColor());
//        g2d.setColor(Color.white);

            g2d.fillRect(0, 0, metrics.panelWidth, metrics.panelHeight);

            ArrayList<Player> players = new ArrayList<>(Arrays.asList(GameManager.getInstance().getPlayers()));
            for (int i = 0; i < players.size(); ++i) {
                handVisualData[i].player = players.get(i);
                handVisualData[i].allCards.clear();
                handVisualData[i].totalSuits = 0;
                for (CardList suit : handVisualData[i].player.getMySuits()) {
                    int size = suit.size();
                    if (size == 0) {
                        continue;
                    }
                    ++handVisualData[i].totalSuits;
                    handVisualData[i].allCards.addAll(suit);
                }
                handVisualData[i].showFaces = GameManager.getInstance().showCards(i);
                paintHand(g2d, handVisualData[i]);
//            break;
            }
            int i = 3;
            handVisualData[i].allCards = GameManager.getInstance().getTalonCards();
            handVisualData[i].showFaces = GameManager.getInstance().showCards(i);
            paintHand(g2d, handVisualData[i]);
            paintTrick(g2d);

/*
        if (GameManager.RoundStage.trickTaken.equals(GameManager.getState().getRoundState())) {
            scorePanel.paint(g2d);
        }
*/
        }
    }

    private void paintTrick(Graphics2D g) {
        Point[] positions = {
            new Point(-(int)(metrics.cardW * .7), (int)(metrics.cardH * .2)),
            new Point(-(int)(metrics.cardW * 1), -(int)(metrics.cardH * .2)),
            new Point(-(int)(metrics.cardW * .5), -(int)(metrics.cardH * .2))
        };
        GameManager.Trick trick = GameManager.getInstance().getTrick();
        int turn = trick.getStarted();
        CardList trickCards = trick.getTrickCards();
        if (trickCards.isEmpty()) {
            return;
        }
        Logger.printf(DEBUG, "trick turn %d, %s\n", trick.getTurn(), trickCards);
        try {
            for (Card card : trickCards) {
                Image im = getCardImage(card);
                int x = positions[turn].x + metrics.panelWidth / 2;
                int y = positions[turn].y + metrics.panelHeight / 2;
                Logger.printf(DEBUG, "trick %s %dx%d\n", card, x, y);
                g.drawImage(im, x, y, mainPanel);
                turn = ++turn % 3;
            }
        } catch (java.util.ConcurrentModificationException e) {
            // todo: fix it!
            // it happens when all players are bots and GameManager.TRICK_TIMEOUT = 0
            Logger.println(e.getMessage());
        }
    }

    private void paintHand(Graphics2D g, HandVisualData handVisualData) {
        int actualW = 0, actualH = 0;
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

        int x = 0, y = 0;
        int labelY = handVisualData.label.y;
        if (handVisualData.xVisible == 0) {
            // vertical
            cardH = (int) metrics.cardH;
            dy = (int) (metrics.cardH * metrics.yVisible);
            actualW = (int) metrics.cardW;
            y = metrics.yMargin + handVisualData.label.y + handVisualData.label.height;
            x = metrics.xMargin;
            if (handVisualData.alignment.equals(MainPanel.Alignment.South)) {
                // bottom & talon
                x = (metrics.panelWidth - actualW) / 2;
                y = metrics.panelHeight - cardH - metrics.yMargin;
            } else if (handVisualData.alignment.equals(MainPanel.Alignment.East)) {
                x = metrics.panelWidth - actualW - metrics.xMargin;
            }
        }
        if (handVisualData.yVisible == 0) {
            // horizontal
            cardW = (int) metrics.cardW;
            dx = (int) (metrics.cardW * metrics.xVisible);
            actualH = (int) metrics.cardH;
            y = metrics.yMargin;
            x = handVisualData.label.width + 2 * metrics.xMargin;
            if (handVisualData.alignment.equals(MainPanel.Alignment.South)) {
                // bottom & talon
                x = (metrics.panelWidth - actualW) / 2;
                if (handVisualData.player == null) {
                    x = (metrics.panelWidth - actualW) / 2;
                    y = metrics.yMargin;    // talon
                } else {
                    y = metrics.panelHeight - (int) metrics.cardH - metrics.yMargin;
//                    labelY = metrics.panelHeight - (int) metrics.cardH - metrics.yMargin;
                    labelY = (int)(metrics.panelHeight - metrics.yMargin - (1 + metrics.yLabel) * metrics.cardH / 2);
                }
                Logger.printf(DEBUG, "horiz center: %dx%d, panel %dx%d, %s\n",
                        x, y, metrics.panelWidth, metrics.panelHeight, handVisualData.player);
            } else if (handVisualData.alignment.equals(MainPanel.Alignment.East)) {
                x = metrics.panelWidth - actualW - handVisualData.label.width - 2 * metrics.xMargin;
            }
        }

        // label:
        if (handVisualData.player != null) {
            Player player = handVisualData.player;
            if (player == mainPanel.currentPlayer) {
                g.setColor(jPrefConfig.currentPlayerBGColor.getColor());
            } else {
                g.setColor(jPrefConfig.labelBGColor.getColor());
            }
            g.fillRect(handVisualData.label.x,
                    labelY,
                    handVisualData.label.width,
                    handVisualData.label.height);
            String text = player.getName();
            if (mainPanel.isStage(GameManager.RoundStage.bidding)) {
                Config.Bid bid = player.getBid();
                if (!Config.Bid.BID_UNDEFINED.equals(bid)) {
                    text += ": " + bid.getName();
                }
            } else {
                int tricks = player.getTricks();
                if (tricks > 0) {
                    text += ": " + tricks;
                }
            }
            FontMetrics fontMetrics = g.getFontMetrics(metrics.font);
            int width = fontMetrics.stringWidth(text);
            int height = fontMetrics.getHeight();
            int _x = handVisualData.label.x + (handVisualData.label.width - width) / 2;
            int _y = labelY + (handVisualData.label.height - height) / 2 + height;
            g.setColor(jPrefConfig.labelTextColor.getColor());
            g.setFont(metrics.font);
            g.drawString(text, _x, _y);
        }
        handVisualData.cardPositions.clear();
//g.setColor(Color.lightGray);
//g.fillRect(x, y, actualW, actualH);
        int i = 0;
        Card.Suit prevSuit = null;
        if (!handVisualData.allCards.isEmpty()) {
            prevSuit = handVisualData.allCards.get(0).getSuit();
        }
        int xOffset = (int) ((1 - handVisualData.xVisible + metrics.xSuitGap) * cardW);
        if (handVisualData.player == null) {
            // talon, quick & dirty
            xOffset = 0;
//            xOffset = (int) ((1 - handVisualData.xVisible) * cardW);
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
            Image im;
            if (handVisualData.player == null) {
                if (!mainPanel.isStage(GameManager.RoundStage.bidding)) {
                    if (i == totalCards - 1) {
                        show = true;
                    }
                }
            }
            if (show) {
                im = getCardImage(card);
            } else {
                im = scaledBackImage.getSubimage(0, 0, (int) metrics.cardW, (int) metrics.cardH);
            }
//            System.out.printf("size %dx%d\n", im.getWidth(null), im.getHeight(null));
            Point position = new Point(x, y);
            if (card == mainPanel.selectedCard) {
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
                Logger.printf(DEBUG, "clicked %s\n", card);
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
        Logger.printf(DEBUG, "%s positions %d:\n%s\n", name,
                handVisualData.cardPositions.size(), handVisualData.cardPositions);
    }

    private void centerPanel(JPanel p) {
        int width = metrics.panelWidth;
        int height = metrics.panelHeight;
        Rectangle bounds = p.getBounds();
        bounds.x = (width - p.getPreferredSize().width) / 2;
        bounds.y = (height - p.getPreferredSize().height) / 2;
        p.setBounds(bounds);
    }

    void showMessage(String text) {
        UIManager.put("OptionPane.minimumSize", new Dimension(panelWidth - 20,panelHeight / 5));
        String msg = "<html><b>" + I18n.m(text) + "</b></html>";
        JLabel label = new JLabel(msg);
        label.setFont(new Font("Arial", Font.BOLD, 40));
        JOptionPane.showMessageDialog(mainPanel, label,"Message", JOptionPane.WARNING_MESSAGE);
    }

    ////////////////////////////////////////////
    class HandVisualData {
        final double xVisible, yVisible;
        final MainPanel.Alignment alignment;
        final Rectangle label = new Rectangle();
        final java.util.List<Point> cardPositions = new ArrayList<>();
        CardList allCards = new CardList();
        int totalSuits;
        Player player;
        boolean showFaces;

        HandVisualData(double xVisible, double yVisible, MainPanel.Alignment alignment) {
            this.xVisible = xVisible;
            this.yVisible = yVisible;
            this.alignment = alignment;

            label.width = (int)(metrics.cardW * metrics.xLabel);
            label.height = (int)(metrics.cardH * metrics.yLabel);
            if (alignment == MainPanel.Alignment.South) {
                // bottom hand
                label.x = metrics.xMargin;
                label.y = (int)(metrics.panelHeight - metrics.yMargin - (1 + metrics.yLabel) * metrics.cardH / 2);
            } else {
                if (xVisible == 0) {
                    // horizontal
                    if (alignment.equals(MainPanel.Alignment.West)) {
                        label.x = metrics.xMargin;
                    } else {
                        label.x = (int)(metrics.panelWidth - metrics.xMargin - metrics.xLabel * metrics.cardW);
                    }
                    label.y = metrics.yMargin + (int)(metrics.cardH - label.height) / 2;
                } else {
                    // vertical
                    if (alignment.equals(MainPanel.Alignment.West)) {
                        label.x = metrics.xMargin;
                    } else {
                        label.x = (int)(metrics.panelWidth - metrics.xMargin - metrics.xLabel * metrics.cardW);
                    }
                    label.y = metrics.yMargin;
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
