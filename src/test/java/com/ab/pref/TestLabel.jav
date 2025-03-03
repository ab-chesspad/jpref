import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;

public class TestLabel {
    public static Font font = new Font("Serif", Font.PLAIN, 40);

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new TestLabel(args);
            }
        });
    }

    public TestLabel(String[] args) {
        JFrame mainFrame = new JFrame();
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setBounds(30, 200, 600, 900);
        JPrefPanel jPanel = new JPrefPanel();
        Container mainContainer = mainFrame.getContentPane();
        mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.X_AXIS));
        mainFrame.add(jPanel);
        mainFrame.setState(Frame.NORMAL);
        mainFrame.setVisible(true);
    }

    public class JPrefPanel extends JPanel {
        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2d = (Graphics2D) g;
            Rectangle r;
//            r = new Rectangle(100, 40, 40, 300);
//            r = new Rectangle(100, 40, 40, 400);
//            r = new Rectangle(100, 40, 60, 400);
//            r = new Rectangle(100, 40, 80, 400);
            r = new Rectangle(100, 40, 80, 300);
//            r = new Rectangle(100, 40, 20, 400);
//            r = new Rectangle(100, 40, 40, 300);

            drawLabel(g2d, "a string", -Math.PI / 2, r);
            r.x += 200;
            drawLabel(g2d, "a string", Math.PI / 2, r);
        }

        private void drawLabel(Graphics g, String text, double rotation, Rectangle r) {
            final int AXIS_OFFSET = 20;
            final int TOP_BUFFER = 30; // where additional text is drawn

            int chartwidth, chartheight, chartX, chartY;

            chartheight = r.height - 2 * AXIS_OFFSET - TOP_BUFFER;
            chartY = r.height - AXIS_OFFSET;

            Graphics2D g2d = (Graphics2D)g.create();
            Rectangle bounds = this.getBounds();
            g2d.setFont(font);
            FontMetrics fontMetrics = g2d.getFontMetrics();
            int textHeight = fontMetrics.getHeight();
            int textWidth = fontMetrics.stringWidth(text);

            int x0 = r.x + r.width / 2;
            int y0 = r.y + r.height / 2;

            // axes
            g2d.drawLine( x0, bounds.y, x0, bounds.y + bounds.width);
            g2d.drawLine( bounds.x, y0, bounds.x + bounds.height, y0);

            g2d.drawRect(r.x, r.y, r.width, r.height);  // vertical

            Font original = g2d.getFont();

            Font font = new Font(null, original.getStyle(), original.getSize());
            AffineTransform affineTransform = new AffineTransform();
            affineTransform.rotate(rotation, 0, 0);
            Font rotatedFont = font.deriveFont(affineTransform);
            g2d.setFont(rotatedFont);
//            g2d.drawString(text, AXIS_OFFSET/2+3, chartY - chartheight/2);
            g2d.drawString(text, r.x + textHeight, y0 - textWidth / 2);
            g2d.setFont(original);


/*
            g2d.rotate(rotation, x0, y0);
            g2d.drawString(text, r.x, y0);
*/
        }
    }
}