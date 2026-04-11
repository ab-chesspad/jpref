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
 * Created: 1/11/2025.
 */
package com.ab.pref;

import com.ab.jpref.config.Config;
import static com.ab.jpref.config.Config.NOP;
import com.ab.jpref.engine.GameManager;
import com.ab.jpref.engine.HumanPlayer;
import com.ab.pref.config.Metrics;
import com.ab.pref.config.PConfig;
import static com.ab.pref.config.PConfig.Host;
import com.ab.util.Logger;
import static com.ab.util.Util.currMethodName;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Main implements Logger.LogHolder, Host {
    static final boolean release = true;
    static boolean DEBUG_LOG = true;
    public static boolean SHOW_ALL = true;
    public static final String LOG_EXT = ".log";
    public static final long LOG_THRESHOLD = 24 * 3600 * 1000;    // 1 day msec
    static {
         PConfig.getInstance().release.set(release);
    }
    static {
        GameManager.RELEASE = release;
        if (release) {
            DEBUG_LOG = false;
            GameManager.BOTS[0] = false;
            GameManager.BOTS[1] = true;
            GameManager.BOTS[2] = true;
            SHOW_ALL = false;
        }
    }

    public static Container mainContainer;
    public static JFrame mainFrame;
    public static MainPanel mainPanel;

    static final PUtil pUtil = PUtil.getInstance();
    static Rectangle mainRectangle;
    static Insets insets;

    private String logFileName;
    private PrintStream logStream;
    private long logStartDate;

    String GUID;
    GameManager gameManager;
    Thread gameThread;

    static String testFileName = null;

    /**
     * @param args optional [fixed-games-file]
     */
    public static void main(String[] args) {

/* failed attempt to handle signal
        Signal.handle(new Signal("SIGINT"),  // SIGINT
                signal -> System.out.println("Interrupted by Ctrl+C"));
//*/

        //Schedule a job for the event-dispatching thread:
        //creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(() -> new Main(args));
    }

/* failed attempt to handle signal
    @Override
    protected void finalize() {
        System.out.println("finalize() 1");
//        saveConfig();
    }
//*/

    public Main(String[] args) {
        // we need it to upload log files
        GUID = PConfig.getInstance().GUID.get();
        if (GUID == null) {
            GUID = java.util.UUID.randomUUID().toString();
            PConfig.getInstance().GUID.set(GUID);
        }

/* until IntelliJ adds ansi colors handling to their debugger,
   output to System.out will be ugly and useless
//*/
        Logger.setHolder(this);
        String version = Config.VERSION + " built " + new SimpleDateFormat("yyyy-MM-dd").format(this.buildDate());
        Logger.printf("%s %s, options 0x%x\n", Config.PROJECT_NAME, version, specialOption());
        Logger.println(String.format("running on %s", pUtil.getOS()));

        if (args.length > 0) {
            testFileName = args[0];
            File f = new File(testFileName);
            Logger.println(f.getAbsolutePath());
        }

        GraphicsDevice mainGD = getGraphicsDevice();
        mainFrame = new JFrame(mainGD.getDefaultConfiguration());
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JFrame.setDefaultLookAndFeelDecorated(true);
        mainFrame.setBounds(mainRectangle);

        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("\nwindowClosing()");
                saveConfig();
            }
        });

/* failed attempt to handle signal
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    System.out.println("Shutting down ...");
                    Thread.sleep(200);
                    //some cleaning up code...
//                    saveConfig();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    e.printStackTrace();
                }
            }
        });
//*/

        mainFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                mainRectangle = ((JFrame)e.getSource()).getBounds();
                insets = mainFrame.getInsets();
                mainRectangle.height -= insets.top;
                Logger.printf(DEBUG_LOG,"main.%s -> %s, %s\n", currMethodName(), e, mainRectangle);
                PConfig.getInstance().mainRectangle.set(mainRectangle);
                Metrics.getInstance().recalculateSizes();
                setMainPanel();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                mainRectangle = ((JFrame)e.getSource()).getBounds();
                insets = mainFrame.getInsets();
                mainRectangle.height -= insets.top;
                Logger.printf(DEBUG_LOG,"main.%s -> %s, %s\n", currMethodName(), e, mainRectangle);
                PConfig.getInstance().mainRectangle.set(mainRectangle);
            }
        });

/* failed attempt to handle signal
        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(Window e) {

            }
        });
*/

        mainContainer = mainFrame.getContentPane();
        mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.X_AXIS));

        mainFrame.setState(Frame.NORMAL);
        mainFrame.setTitle(Config.PROJECT_NAME);
        mainFrame.setVisible(true);
    }

    private void saveConfig() {
        mainRectangle.height += insets.top;
        PConfig.getInstance().serialize();
        closeLog();
    }

    void setMainPanel() {
        if (mainPanel == null) {
            mainPanel = new MainPanel(this);
            mainContainer.add(mainPanel);
        }

        if (gameManager == null) {
            gameManager = new GameManager(PConfig.getInstance(), mainPanel);
            if (gameThread == null) {
                gameThread = new Thread(() -> {
                    try {
                        while (true) {
                            gameManager.runGame(testFileName, 0);
                            Logger.println("game ended!");
                        }
                    } catch (HumanPlayer.PrefExceptionRerun e) {
                        // ignore
                    }
                });
                gameThread.start();
                Logger.printf(DEBUG_LOG, "gameManager started\n");
            }
        }
    }

    private GraphicsDevice getGraphicsDevice() {
        GraphicsDevice mainGD = null;
        Rectangle fullScreen = new Rectangle(0, 0, 0, 10000);
        int x = 10000;
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (GraphicsDevice gd : ge.getScreenDevices()) {
            Logger.printf(DEBUG_LOG, "device:'%s' size=(%dx%d)\n",
                    gd.getIDstring(), gd.getDisplayMode().getWidth(), gd.getDisplayMode().getHeight());
            int thisX = gd.getDefaultConfiguration().getBounds().x;
            if (x > thisX) {
                x = thisX;
                mainGD = gd;    // leftmost display
            }

            if (fullScreen.width < gd.getDisplayMode().getWidth()) {
                fullScreen.width = gd.getDisplayMode().getWidth();
                fullScreen.height = gd.getDisplayMode().getHeight();
            }
        }

        Insets insets = new Insets(0,0,0,0);
        Logger.printf(DEBUG_LOG, "insets=(%dx%dx%dx%d)\n",
                insets.left, insets.right, insets.top, insets.bottom);
        mainRectangle = PConfig.getInstance().mainRectangle.get();
        if (mainRectangle.width == 0) {
            mainRectangle.width = (fullScreen.width - insets.left - insets.right) / 2;
            mainRectangle.height = (fullScreen.height - insets.top - insets.bottom) / 2;
            mainRectangle.x = (fullScreen.width - mainRectangle.width) / 2;
            mainRectangle.y = (fullScreen.height - mainRectangle.height) / 2;
        }
        return mainGD;
    }

    @Override
    public PrintStream getLogStream() {
        if (DEBUG_LOG || specialOption() != 0) {
            System.out.println("output to System.out");
            return System.out;
        }

        long today = new Date().getTime() / LOG_THRESHOLD;
        if (today != logStartDate) {
            closeLog();
            String date = new SimpleDateFormat("yyyy-MM-dd-").format(new Date());
            try {
                logStream = getNewOutput(date);
                logStartDate = today;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println("output to " + logStream);
        return logStream;
    }

    private PrintStream getNewOutput(String date) throws IOException {
        String dataDir = pUtil.getDataDirectory();
        File logDir = new File(dataDir, "logs");
        logDir.mkdir();
        int deleteTimeout = Config.getInstance().deleteLogsAfter.get();
        Calendar c = new GregorianCalendar();
        c.add(Calendar.HOUR, -24 * deleteTimeout);
        long threshold = c.getTimeInMillis();
        final int[] lastNum = {0};
        logDir.list((file, itemName) -> {
            String name = itemName;
            if (name.endsWith(LOG_EXT)) {
                name = name.substring(0, name.length() - LOG_EXT.length());
                File f = new File(file, itemName);
                long fileTS = f.lastModified();
                long diff = fileTS - threshold;
                if (diff < 0) {
                    f.delete();
                }
            }
            if (!name.startsWith(date)) {
                return false;
            }
            name = name.substring(date.length());
            int num;
            try {
                num = Integer.parseInt(name);
            } catch (NumberFormatException e) {
                return false;
            }
            if (lastNum[0] < num) {
                lastNum[0] = num;
            }
            return false;
        });
        logFileName = logDir + File.separator + String.format("%s%02d%s", date, lastNum[0] + 1, LOG_EXT);
        return new PrintStream(logFileName, StandardCharsets.UTF_8.name());
    }

    private void closeLog() {
        if (logStream != null && logStream != System.out) {
            logStream.close();
            logStream = null;
        }
    }

    @Override
    public void repaint() {
        mainContainer.validate();
        mainContainer.repaint();
    }

    @Override
    public JFrame mainFrame() {
        return mainFrame;
    }

    @Override
    public String getLogFileName() {
        return logFileName;
    }

    @Override
    public boolean testing() {
        return testFileName != null;
    }

    @Override
    public long buildDate() {
        try {
            File file = new File(GameManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (file.getAbsolutePath().endsWith(".jar")) {
                try {
                    ZipInputStream zis = new ZipInputStream(new FileInputStream(file.getAbsolutePath()));
                    ZipEntry entry = zis.getNextEntry();
                    return entry.getTime();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return file.lastModified();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int specialOption() {
        int res = 0;
        boolean allHuman = true;
        for (int i = 0; i < NOP; ++i) {
            if (GameManager.BOTS[i]) {
                allHuman = false;
                break;
            }
        }

        if (allHuman) {
            res |= Host.SPECIAL_OPTION_MANUAL;
        }
        if (SHOW_ALL || allHuman) {
            res |= Host.SPECIAL_OPTION_SHOW_CARDS;
        }
        return res;
    }
}