package com.ab.pref;

import com.ab.jpref.engine.Player;
import com.ab.util.Logger;

import java.awt.*;

public class ScorePanel {
    static final int leftPoints = Player.PlayerPoints.leftPoints.ordinal();
    static final int rightPoints = Player.PlayerPoints.rightPoints.ordinal();
    static final int poolPoints = Player.PlayerPoints.poolPoints.ordinal();
    static final int dumpPoints = Player.PlayerPoints.dumpPoints.ordinal();

    static final Color lineColor = Color.black;
    static final Color poolSizeColor = Color.decode("#008000");
    static final int strokeWidth = 2;
    static final double panelSizeFactor = .7;       // relative to panel size
    static final double centerCircleYOffset = .4;   // relative to cardW size
    static final double centerCircleRadius = 1;     // relative to cardW size
    static final double scoreFontSize = .5;         // relative to cardW size

    static final int South = MainPanel.Alignment.South.ordinal(),
        West = MainPanel.Alignment.West.ordinal(),
        East = MainPanel.Alignment.East.ordinal();

    private final Metrics metrics = Metrics.getInstance();
    private final JPrefConfig jPrefConfig = JPrefConfig.getInstance();
    Font scoreFont = new Font("Serif", Font.PLAIN, (int)(metrics.cardW * scoreFontSize));


    final private ScorePosition[] scorePositions;
    private Graphics2D g2d;
    private int x0, x1, y0, y1, width, height;
    double angle, tan;
    private int textHeight;

    public ScorePanel() {
        scorePositions = new ScorePosition[3];
        for (int i = 0; i < scorePositions.length; ++i) {
            scorePositions[i] = new ScorePosition(i);
        }

    }

    public void pUpdate() {
        width = (int) (metrics.panelWidth * panelSizeFactor);
        height = (int) (metrics.panelHeight * panelSizeFactor);
        x0 = (metrics.panelWidth - width) / 2;
        x1 = x0 + width;
        y0 = (metrics.panelHeight - height) / 2;
        y1 = y0 + height;

        Point circleLoc = new Point(width / 2, (int) (height * centerCircleYOffset));

        angle = Math.atan2(height - circleLoc.y, circleLoc.x);
        tan = Math.tan(angle);

        int panelHeight = (int) (metrics.cardW * scoreFontSize);
        textHeight = panelHeight + metrics.yMargin;
        int h = panelHeight * 2;    // panel height
        int dx = (int) ((double) h / tan);   // x-diff between bottom and top
        Rectangle[] labelBounds = new Rectangle[Player.PlayerPoints.total.ordinal()];
        for (int i = 0; i < labelBounds.length; ++i) {
            labelBounds[i] = new Rectangle();
        }

        // ------------------------------------------------
        //              South panel
        // ------------------------------------------------
        // lowest part, left and right points
        labelBounds[leftPoints].y =
                labelBounds[rightPoints].y = height;
        labelBounds[leftPoints].x = (int) ((double) textHeight / tan);
        labelBounds[rightPoints].x = width / 2 + strokeWidth + metrics.xMargin;
        labelBounds[leftPoints].width =
                labelBounds[rightPoints].width = width / 2
                        - labelBounds[leftPoints].x - metrics.xMargin;
        labelBounds[leftPoints].height =
                labelBounds[rightPoints].height =
                        labelBounds[poolPoints].height =
                                labelBounds[dumpPoints].height = h;

        labelBounds[poolPoints].y = height - h;
        labelBounds[poolPoints].x = (int) ((textHeight + h) / tan);
        labelBounds[poolPoints].width = width
                - 2 * (scorePositions[South].screenData[poolPoints].xStart) - metrics.xMargin;

        labelBounds[dumpPoints].y = height - 2 * h;
        labelBounds[dumpPoints].x = (int) ((textHeight + 2 * h) / tan);
        labelBounds[dumpPoints].width = width
                - 2 * (scorePositions[South].screenData[dumpPoints].xStart) - metrics.xMargin;


/*
        labelBounds[leftPoints].y =
        labelBounds[rightPoints].y = height;
        scorePositions[South].screenData[leftPoints].xStart = (int)((double)textHeight / tan);
        scorePositions[South].screenData[rightPoints].xStart = width / 2 + strokeWidth + metrics.xMargin;
        scorePositions[South].screenData[leftPoints].width =
                scorePositions[South].screenData[rightPoints].width = width / 2
                        - scorePositions[South].screenData[leftPoints].xStart - metrics.xMargin;
*/

        // pool points
        scorePositions[South].screenData[poolPoints].yStart = height - h;
        scorePositions[South].screenData[poolPoints].xStart = (int)((textHeight + h) / tan);
        scorePositions[South].screenData[poolPoints].width = width
                - 2 * (scorePositions[South].screenData[poolPoints].xStart) - metrics.xMargin;

        // dump points
        scorePositions[South].screenData[dumpPoints].yStart = height - 2 * h;
        scorePositions[South].screenData[dumpPoints].xStart = (int)((textHeight + 2 * h) / tan);
        scorePositions[South].screenData[dumpPoints].width = width
                - 2 * (scorePositions[South].screenData[dumpPoints].xStart) - metrics.xMargin;

        // ------------------------------------------------
        //              East panel
        // ------------------------------------------------
        // rightmost part, bottom and top (left and right) points
        scorePositions[East].screenData[rightPoints].yStart = metrics.yMargin;
        scorePositions[East].screenData[rightPoints].xStart =
                scorePositions[East].screenData[leftPoints].xStart = width - textHeight - metrics.xMargin;
        scorePositions[East].screenData[leftPoints].yStart = (height - h) / 2 + metrics.yMargin;
        scorePositions[East].screenData[rightPoints].width = (height - h) / 2
                - scorePositions[East].screenData[rightPoints].yStart - metrics.xMargin;
        scorePositions[East].screenData[leftPoints].width = (height - h) / 2;

        scorePositions[East].screenData[poolPoints].yStart = metrics.yMargin;
        scorePositions[East].screenData[poolPoints].xStart = width - dx - textHeight - metrics.xMargin;
        scorePositions[East].screenData[poolPoints].width = height - 2 * dx
                - scorePositions[East].screenData[poolPoints].yStart - 2 * metrics.xMargin;

        scorePositions[East].screenData[dumpPoints].yStart = metrics.yMargin;
        scorePositions[East].screenData[dumpPoints].xStart = width - 2 * dx - textHeight - metrics.xMargin;
        scorePositions[East].screenData[dumpPoints].width = height - h - 2 * dx
                - scorePositions[East].screenData[dumpPoints].yStart - metrics.xMargin;

    }


    public void paint(Graphics g) {
        this.g2d = (Graphics2D)g;
//        g2d.setColor(Color.white);
//        g2d.fillRect(0, 0, width, height);
        fillRect(g2d, Color.white, 0, 0, width, height);
        paintLines();
//        paintScores(g2d);
        drawArea(g2d, South);
    }

    private void paintLines() {
        g2d.setStroke(new BasicStroke(strokeWidth));
        g2d.setColor(lineColor);

        Point circleLoc = new Point(width / 2, (int)(height * centerCircleYOffset));

        // vertical line
        drawLine(width / 2, 0, circleLoc.x, circleLoc.y);

        // draw diagonals to centerLoc and paint center circle over
        drawLine(width / 2, 0, circleLoc.x, circleLoc.y);
        drawLine(0, height, circleLoc.x, circleLoc.y);
        drawLine(width, height, circleLoc.x, circleLoc.y);

        // center circle with pool size number:
        int circleRadius = (int)(metrics.cardW * centerCircleRadius);
        fillOval(Color.black, circleLoc.x - circleRadius, circleLoc.y - circleRadius, 2 * circleRadius, 2 * circleRadius);
        int innerRadius = (int)(metrics.cardW * centerCircleRadius - strokeWidth);
        fillOval(Color.white, circleLoc.x - innerRadius, circleLoc.y - innerRadius, 2 * innerRadius, 2 * innerRadius);

        // pool size number:
        Font font = new Font("Serif", Font.PLAIN, (int)(metrics.cardW));
        g2d.setFont(font);
//        g2d.setColor(poolSizeColor);
        String text = "" + jPrefConfig.poolSize.get();
        FontMetrics fontMetrics = g2d.getFontMetrics(font);
        int _width = fontMetrics.stringWidth(text);
        int _height = fontMetrics.getHeight();
        int x = circleLoc.x - _width / 2 - metrics.xMargin;
        int y = circleLoc.y + _height / 4 + metrics.yMargin;
        drawString(g2d, poolSizeColor, text, x, y);

//        g2d.setColor(lineColor);
        int panelHeight = (int)(metrics.cardW * scoreFontSize);
//        FontMetrics fontMetrics = new Canvas().getFontMetrics(scoreFont);
        textHeight = panelHeight + metrics.yMargin;
        int h = panelHeight * 2;    // panel height
        int dx = (int)((double)h / tan);   // x-diff between bottom and top

        // ------------------------------------------------
        //              South panel
        // ------------------------------------------------
        // lowest part, left and right points
        drawLine(dx, height - h, width - dx, height - h);       // south area, horizontal
        drawLine(width / 2, height - h, width / 2, height);     // south area, vertical
        // dump points
        int _dx = (int)((double)h * 2 / tan);   // x-diff between bottom and top
        drawLine(_dx, height - 2 * h, width - _dx, height - 2 * h);       // south area, horizontal

        // ------------------------------------------------
        //              West panel
        // ------------------------------------------------
        // leftmost part, top and bottom (left and right) points
        drawLine(dx, 0, dx, height - h);       // west area, vertical line between whists and pool
        drawLine(0, (height - h) / 2, dx, (height - h) / 2);     // west area, horizontal
        drawLine(2 * dx, 0, 2 * dx, height - 2 * h);       // west area, vertical line between pool and dump

        // ------------------------------------------------
        //              West panel
        // ------------------------------------------------
        // leftmost part, top and bottom (left and right) points
/*
        scorePositions[West].screenData[leftPoints].yStart = metrics.xMargin;
        scorePositions[West].screenData[leftPoints].xStart =
                scorePositions[West].screenData[rightPoints].xStart = 0;
        scorePositions[West].screenData[rightPoints].yStart = (height - h) / 2 + metrics.yMargin;
        scorePositions[West].screenData[rightPoints].width =
                scorePositions[West].screenData[leftPoints].width = (height - h) / 2
                        - scorePositions[West].screenData[leftPoints].yStart - metrics.xMargin;

        scorePositions[West].screenData[poolPoints].yStart = metrics.yMargin;
        scorePositions[West].screenData[poolPoints].xStart = dx;
        scorePositions[West].screenData[poolPoints].width = height - 2 * dx
                - scorePositions[West].screenData[poolPoints].yStart - metrics.xMargin;

        scorePositions[West].screenData[dumpPoints].yStart = metrics.yMargin;
        scorePositions[West].screenData[dumpPoints].xStart = 2 * dx;
        scorePositions[West].screenData[dumpPoints].width = height - h - 2 * dx
                - scorePositions[West].screenData[dumpPoints].yStart - metrics.xMargin;
*/

        // ------------------------------------------------
        //              East panel
        // ------------------------------------------------
        // rightmost part, bottom and top (left and right) points
        drawLine(width - dx, 0, width - dx, height - h);       // East area, vertical line between whists and pool
        drawLine(width, (height - h) / 2, width - dx, (height - h) / 2);     // East area, horizontal
        drawLine(width - 2 * dx, 0, width - 2 * dx, height - 2 * h);       // East area, vertical line between pool and dump

    }

    private void fillRect(Graphics2D g2d, Color color, int x, int y, int width, int height) {
        g2d.setColor(color);
        g2d.fillRect(x + this.x0, y + this.y0, width, height);
    }

    private void drawLine(int x0, int y0, int x1, int y1) {
        g2d.setColor(lineColor);
        g2d.drawLine(x0 + this.x0, y0 + this.y0,
                x1 + this.x0, y1 + this.y0);
    }

    private void fillOval(Color color, int x0, int y0, int xD, int yD) {
        g2d.setColor(color);
        g2d.fillOval(x0 + this.x0, y0 + this.y0, xD, yD);
    }

    private void drawLabel(Graphics2D g2d, String text, double rotation, Rectangle r) {
        g2d = (Graphics2D)g2d.create();
        g2d.setColor(Color.black);
        FontMetrics fontMetrics = g2d.getFontMetrics();
        int textHeight = fontMetrics.getHeight();
        int textWidth = fontMetrics.stringWidth(text);

        int x0 = r.x + r.width / 2;
        int y0 = r.y + r.height / 2;

        g2d.drawRect(r.x, r.y, r.width, r.height);  // vertical

//            g2d.drawString(text, x0 - textWidth / 2, y0 + fontMetrics.getDescent());       // not rotated!

        g2d.rotate(rotation, x0, y0);
        g2d.drawString(text,x0 - r.height / 2, y0 + fontMetrics.getDescent());
//        g2d.drawString(text,x0, y0 + fontMetrics.getDescent());
        g2d.dispose();
    }

    private void drawString(Graphics2D g2d, Color color, String text, int x, int y) {
        g2d.setColor(color);
        g2d.drawString(text, x + this.x0 + metrics.xMargin, y + this.y0 - metrics.yMargin);
    }

    private void drawScores(Graphics2D g2d, ScreenData screenData, String text, Color color) {
        int w = screenData.width;
        int h = textHeight;
//        Graphics2D g2d = (Graphics2D) this.g2d.create();
//*
        if (screenData.location == South) {
            Rectangle r = new Rectangle(screenData.xStart + this.x0,
                    screenData.yStart + this.y0 - metrics.yMargin,
                    screenData.width, textHeight);
            g2d.setColor(color);
            g2d.fillRect(r.x, r.y - textHeight, r.width, r.height);
            g2d.setColor(Color.black);
            g2d.drawString(text, r.x, r.y);
//        } else if (screenData.location == West) {
        } else {
            Rectangle r = new Rectangle(screenData.xStart + this.x0,
                    screenData.yStart + this.y0 - metrics.yMargin,
                    textHeight, screenData.width);
            g2d.setColor(color);
            g2d.fillRect(r.x, r.y, r.width, r.height);
            drawLabel(g2d, text, screenData.rotation, r);

            int _x0 = screenData.xStart + screenData.width / 2;
            int _y0 = screenData.yStart +  textHeight / 2;

            Logger.printf("west, %d x %d, w=%d, r=%3.2f\n",
                    screenData.xStart, screenData.yStart, screenData.width, screenData.rotation);
/*
            fillRect(g2d, color,
                    screenData.xStart,
                    screenData.yStart,
                    textHeight,
                    screenData.width);
*/
/*
            g2d.rotate(screenData.rotation,
                _x0 + this.x0,
                _y0 + this.y0 + screenData.width / 2);
            drawString(g2d, Color.black, text,
                    screenData.xStart - screenData.width / 2,
                    screenData.yStart + screenData.width / 2);
*/

        }
        g2d.dispose();
//*/
    }

    private void drawArea(Graphics2D g2d, int location) {
/*
        Player player = GameManager.getInstance().getPlayers()[location];
        int[] totals = new int[Player.PlayerPoints.total.ordinal()];
//        Player.RoundResults[] rrs = new Player.RoundResults[Player.PlayerPoints.total.ordinal()]];
//        Player.RoundResults rr = new Player.RoundResults();
        StringBuilder[] sbs = new StringBuilder[Player.PlayerPoints.total.ordinal()];
        String[] seps = new String[Player.PlayerPoints.total.ordinal()];
        Player.RoundResults rr = new Player.RoundResults();
        List<Player.RoundResults> history = player.getHistory();
        for (Player.RoundResults r : history) {
            for (int j = 0; j < sbs.length; ++j) {
                if (sbs[j] == null) {
                    sbs[j] = new StringBuilder();
                    seps[j] = "";
                } else {
                    sbs[j].append(seps[j]).append(rr.getPoints(j));
                    seps[j] = ".";
                }
                rr.setPoints(j, rr.getPoints(j) + r.getPoints(j));
            }
        }

        for (int j = 0; j < sbs.length; ++j) {
            Logger.printf("%s + %d\n", sbs[j].toString(), rr.getPoints(j));
        }
*/

        String text = "123.23.456.789";
        drawScores(g2d, scorePositions[location].screenData[leftPoints], text, Color.decode("#ff0000"));
        drawScores(g2d, scorePositions[location].screenData[rightPoints], text, Color.decode("#00ff00"));
        drawScores(g2d, scorePositions[location].screenData[poolPoints], text, Color.decode("#ff00ff"));
        drawScores(g2d, scorePositions[location].screenData[dumpPoints], text, Color.decode("#ffff00"));
    }

    public void _update() {
        width = (int)(metrics.panelWidth * panelSizeFactor);
        height = (int)(metrics.panelHeight * panelSizeFactor);
        x0 = (metrics.panelWidth - width) / 2;
        x1 = x0 + width;
        y0 = (metrics.panelHeight - height) / 2;
        y1 = y0 + height;

        Point circleLoc = new Point(width / 2, (int)(height * centerCircleYOffset));

        angle = Math.atan2(height - circleLoc.y, circleLoc.x);
        tan = Math.tan(angle);
    }

    public void _paint(Graphics2D g2d) {
/*
        this.g2d = g2d;

        fillRect(g2d, Color.white, 0, 0, width, height);

        String text = "123.456.789";
        g2d.setStroke(new BasicStroke(strokeWidth));

        Point circleLoc = new Point(width / 2, (int)(height * centerCircleYOffset));

        // vertical line
        drawLine(width / 2, 0, circleLoc.x, circleLoc.y);

        // draw diagonals to centerLoc and paint center circle over
        drawLine(width / 2, 0, circleLoc.x, circleLoc.y);
        drawLine(0, height, circleLoc.x, circleLoc.y);
        drawLine(width, height, circleLoc.x, circleLoc.y);

        // center circle with pool size number:
        int circleRadius = (int)(metrics.cardW * centerCircleRadius);
        fillOval(lineColor, circleLoc.x - circleRadius, circleLoc.y - circleRadius, 2 * circleRadius, 2 * circleRadius);
        int innerRadius = (int)(metrics.cardW * centerCircleRadius - strokeWidth);
        fillOval(Color.white, circleLoc.x - innerRadius, circleLoc.y - innerRadius, 2 * innerRadius, 2 * innerRadius);

        // pool size number:
        Font font = new Font("Serif", Font.PLAIN, (int)(metrics.cardW));
        text = "" + jPrefConfig.poolSize.get();
        FontMetrics fontMetrics = g2d.getFontMetrics(font);
        int _width = fontMetrics.stringWidth(text);
        int _height = fontMetrics.getHeight();
        int x = circleLoc.x - _width / 2 - metrics.xMargin;
        int y = circleLoc.y + _height / 4 + metrics.yMargin;
        g2d.setFont(font);
        drawString(g2d, poolSizeColor, text, x, y);

        // score panels
        Font scoreFont = new Font("Serif", Font.PLAIN, (int)(metrics.cardW * scoreFontSize));
        g2d.setFont(scoreFont);
        fontMetrics = g2d.getFontMetrics(scoreFont);
        textHeight = fontMetrics.getHeight() + metrics.yMargin;
        int h = fontMetrics.getHeight() * 2;    // panel height
        int dx = (int)((double)h / tan);   // x-diff between bottom and top

        // ------------------------------------------------
        //              South panel
        // ------------------------------------------------
        // lowest part, left and right points
        drawLine(dx, height - h, width - dx, height - h);       // south area, horizontal
        drawLine(width / 2, height - h, width / 2, height);     // south area, vertical

        scorePositions[South].leftPoints.yStart =
        scorePositions[South].rightPoints.yStart = height;
        scorePositions[South].leftPoints.xStart = (int)((double)textHeight / tan);
        scorePositions[South].rightPoints.xStart = width / 2 + strokeWidth + metrics.xMargin;
        scorePositions[South].leftPoints.width =
        scorePositions[South].rightPoints.width = width / 2
                - scorePositions[South].leftPoints.xStart - metrics.xMargin;

        // pool points
        scorePositions[South].poolPoints.yStart = height - h;
        scorePositions[South].poolPoints.xStart = (int)((textHeight + h) / tan);
        scorePositions[South].poolPoints.width = width
                - 2 * (scorePositions[South].poolPoints.xStart) - metrics.xMargin;

        // dump points
        int _dx = (int)((double)h * 2 / tan);   // x-diff between bottom and top
        drawLine(_dx, height - 2 * h, width - _dx, height - 2 * h);       // south area, horizontal

        scorePositions[South].dumpPoints.yStart = height - 2 * h;
        scorePositions[South].dumpPoints.xStart = (int)((textHeight + 2 * h) / tan);
        scorePositions[South].dumpPoints.width = width
                - 2 * (scorePositions[South].dumpPoints.xStart) - metrics.xMargin;
*/

        drawArea(g2d, South);

/*
        // ------------------------------------------------
        //              West panel
        // ------------------------------------------------
        // leftmost part, top and bottom (left and right) points
        drawLine(dx, 0, dx, height - h);       // west area, vertical
        drawLine(0, (height - h) / 2, dx, (height - h) / 2);     // west area, horizontal

        scorePositions[West].leftPoints.yStart = metrics.xMargin;
        scorePositions[West].leftPoints.xStart =
        scorePositions[West].rightPoints.xStart = 0;
        scorePositions[West].rightPoints.yStart = (height - h) / 2 + metrics.yMargin;
        scorePositions[West].rightPoints.width =
        scorePositions[West].leftPoints.width = (height - h) / 2
                - scorePositions[West].leftPoints.yStart - metrics.xMargin;
//        scorePositions[West].rightPoints.width = height / 2
//                - scorePositions[West].leftPoints.yStart - metrics.xMargin;

        scorePositions[West].poolPoints.yStart = metrics.yMargin;
        scorePositions[West].poolPoints.xStart = dx;
        scorePositions[West].poolPoints.width = height - 2 * dx
                        - scorePositions[West].poolPoints.yStart - metrics.xMargin;

        drawLine(2 * dx, 0, 2 * dx, height - 2 * h);       // west area, vertical

        scorePositions[West].dumpPoints.yStart = metrics.yMargin;
        scorePositions[West].dumpPoints.xStart = 2 * dx;
        scorePositions[West].dumpPoints.width = height - h - 2 * dx
                - scorePositions[West].dumpPoints.yStart - metrics.xMargin;
*/
        drawArea(g2d, West);

/*
        // ------------------------------------------------
        //              East panel
        // ------------------------------------------------
        // rightmost part, top and bottom (left and right) points
        drawLine(width - dx, 0, width - dx, height - h);       // East area, vertical
        drawLine(width, (height - h) / 2, width - dx, (height - h) / 2);     // East area, horizontal

        scorePositions[East].rightPoints.yStart = metrics.yMargin;
        scorePositions[East].rightPoints.xStart =
        scorePositions[East].leftPoints.xStart = width - textHeight - metrics.xMargin;
//        scorePositions[East].leftPoints.yStart = (height - h) / 2 + metrics.yMargin;
        scorePositions[East].leftPoints.yStart = (height - h) / 2 + metrics.yMargin;
        scorePositions[East].rightPoints.width = (height - h) / 2
                - scorePositions[East].rightPoints.yStart - metrics.xMargin;
        scorePositions[East].leftPoints.width = (height - h) / 2;
//                        - metrics.xMargin;
//        scorePositions[East].rightPoints.width = height / 2
//                - scorePositions[East].leftPoints.yStart - metrics.xMargin;

        scorePositions[East].poolPoints.yStart = metrics.yMargin;
        scorePositions[East].poolPoints.xStart = width - dx - textHeight - metrics.xMargin;
        scorePositions[East].poolPoints.width = height - 2 * dx
                - scorePositions[East].poolPoints.yStart - 2 * metrics.xMargin;

        drawLine(width - 2 * dx, 0, width - 2 * dx, height - 2 * h);       // East area, vertical

        scorePositions[East].dumpPoints.yStart = metrics.yMargin;
        scorePositions[East].dumpPoints.xStart = width - 2 * dx - textHeight - metrics.xMargin;
        scorePositions[East].dumpPoints.width = height - h - 2 * dx
                - scorePositions[East].dumpPoints.yStart - metrics.xMargin;
*/

        drawArea(g2d, East);

//        int xWest = center.x - w / 2 + dx;
//        int xEast = center.x + w / 2 - dx;
//        y = center.y + h / 2 - height;
//        int _dx = (int)((double)lineHeight / tan);
//


/*



        g2d.setColor(lineColor);



        g2d.drawLine(xWest, center.y - h / 2, xWest, y);        // West area, vertical
        g2d.drawLine(xWest - dx, center.y - lineHeight, xWest, center.y - lineHeight);     // West area, horizontal

        scorePositions[West].leftPoints.yStart =
        scorePositions[West].poolPoints.yStart =
        scorePositions[West].dumpPoints.yStart = center.y - h / 2 + metrics.yMargin;
        scorePositions[West].leftPoints.xStart = 
        scorePositions[West].rightPoints.xStart = center.x - w / 2 + metrics.xMargin;
        scorePositions[West].rightPoints.yStart = center.y - lineHeight + metrics.yMargin;
        scorePositions[West].leftPoints.width = h / 2 - lineHeight - 2 * metrics.xMargin;
        scorePositions[West].rightPoints.width = h / 2 - metrics.yMargin - lineHeight;
center.x - w / 2 -
        scorePositions[West].poolPoints.xStart = xWest + metrics.xMargin;
        scorePositions[West].poolPoints.width = h - height - _dx;
        scorePositions[West].dumpPoints.width =
                scorePositions[West].poolPoints.width - height;


        g2d.drawLine(xEast, center.y - h / 2, xEast, y);        // East area, vertical
        g2d.drawLine(xEast, center.y, xEast + dx, center.y);    // East area, horizontal

        // pools:
        int southPanelHeight = fontMetrics.getHeight() * 4;
        dx = (int)((double)southPanelHeight / Math.tan(angle));
        xWest = center.x - w / 2 + dx;
        xEast = center.x + w / 2 - dx;
        y = center.y + h / 2 - southPanelHeight;

        g2d.drawLine(xWest, y, xEast, y);                        // South area, horizontal

        g2d.drawLine(xWest, center.y - h / 2, xWest, y);      // West area, vertical
        g2d.drawLine(xEast, center.y - h / 2, xEast, y);      // East area, vertical

        // South score
        height = fontMetrics.getHeight() + metrics.yMargin;
        dx = (int)((double)height / Math.tan(angle));


        // pool
        g2d.setColor(Color.decode("#f0f0d0"));
        g2d.fillRect(
                scorePositions[South].poolPoints.xStart,
                scorePositions[South].poolPoints.yStart - lineHeight,
                scorePositions[South].poolPoints.width,
                lineHeight);
        g2d.setColor(Color.black);

        g2d.drawString(text,
                scorePositions[South].poolPoints.xStart,
                scorePositions[South].poolPoints.yStart);

        // dump
        g2d.setColor(Color.decode("#f0f0f0"));
        g2d.fillRect(
                scorePositions[South].dumpPoints.xStart,
                scorePositions[South].dumpPoints.yStart - lineHeight,
                scorePositions[South].dumpPoints.width,
                lineHeight);
        g2d.setColor(Color.black);

        g2d.drawString(text,
                scorePositions[South].dumpPoints.xStart,
                scorePositions[South].dumpPoints.yStart);

        // west:
        g2d.setColor(Color.decode("#d0d0d0"));
        g2d.fillRect(
                scorePositions[West].leftPoints.xStart,
                scorePositions[West].leftPoints.yStart,
                lineHeight,
                scorePositions[West].leftPoints.width);

        g2d.setColor(Color.decode("#d0f0d0"));
        g2d.fillRect(
                scorePositions[West].rightPoints.xStart,
                scorePositions[West].rightPoints.yStart,
                lineHeight,
                scorePositions[West].rightPoints.width);

        g2d.setColor(Color.decode("#d0d0d0"));

        g2d.fillRect(
                scorePositions[West].dumpPoints.xStart,
                scorePositions[West].dumpPoints.yStart,
                lineHeight,
                scorePositions[West].dumpPoints.width);

        g2d.setColor(Color.black);


        // pool center.y + h / 2 - height
//        g2d.drawString(text,
////                xWest + metrics.xMargin, center.y + h / 2 - southPanelHeight);
//            scorePositions[JPrefPanel.Alignment.South.ordinal()].points.xStart,
//            scorePositions[JPrefPanel.Alignment.South.ordinal()].points.yStart);

        // West score panel
//        dx = (int) (height * Math.tan(angle));
//        int dy = (int) (height);
////        g2d.fillRect(center.x - w / 2, center.y - h / 2, w, h);
//        g2d.drawLine(center.x - w / 2 + dx, center.y + h / 2 - height,
//                center.x + w / 2 - dx, center.y + h / 2 - height );

//        for ( int i = 0; i < GameManager.getInstance().getPlayers().length; ++i) {
//            paintScore(GameManager.getInstance().getPlayers()[i], scorePositions[i];
//        }


//        FontMetrics fontMetrics = g.getFontMetrics(metrics.font);
//        int width = fontMetrics.stringWidth(text);
//        int height = fontMetrics.getHeight();
//        int _x = handVisualData.label.x + (handVisualData.label.width - width) / 2;
//        int _y = handVisualData.label.y + (handVisualData.label.height - height) / 2 + height;
//        g.setColor(jPrefConfig.playerDataTextColor.getColor());
//        g.setFont(font);
//        g.drawString(text, _x, _y);

*/
    }

    private static class ScreenData {
        final int location;
        double rotation;
//        final PLabel pLabel;

        int xStart, yStart, width;

        ScreenData(int location) {
            this.location = location;
            rotation = 0;
//            pLabel = new PLabel(rotation);
            if (location == West) {
                rotation = Math.PI / 2;
            } else if (location == East) {
                rotation = -Math.PI / 2;
            }
        }
    }

    private static class ScorePosition {
        final ScreenData[] screenData = new ScreenData[Player.PlayerPoints.total.ordinal()];

        ScorePosition(int location) {
            double rotation = 0;
            if (location == West) {
                rotation = Math.PI / 2;
            } else if (location == East) {
                rotation = -Math.PI / 2;
            }

            for (int i = 0; i < screenData.length; ++i) {
                screenData[i] = new ScreenData(location);
            }
        }
    }
}
