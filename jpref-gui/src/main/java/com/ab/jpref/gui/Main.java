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
 * Created: 1/11/2025.
 */
package com.ab.jpref.gui;

import com.ab.jpref.config.Config;
import com.ab.jpref.config.I18n;
import com.ab.jpref.engine.GameManager;
import com.ab.jpref.engine.HumanPlayer;
import com.ab.jpref.engine.Player;
import com.ab.jpref.engine.TrickList;
import com.ab.jpref.config.Metrics;
import com.ab.jpref.gui.config.PConfig;
import static com.ab.jpref.gui.config.PConfig.NOP;

import com.ab.jpref.gui.config.SettingsPopup;
import com.ab.jpref.trickpool.TrickPool;
import com.ab.jpref.ui.Host;
import com.ab.jpref.ui.TableLayout;
import com.ab.util.Logger;
import com.ab.util.Util;

import static com.ab.util.Util.currMethodName;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
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
    static boolean DEBUG_LOG = false;
    public static boolean SHOW_ALL = true;
    public static final double MAGIC_ASPECT_RATIO = 1109d / 1297d;
    static {
        GameManager.RELEASE = release;
        if (release) {
            DEBUG_LOG = false;
            Logger.DEBUG_LOG = false;
            GameManager.BOTS[0] = false;
            GameManager.BOTS[1] = true;
            GameManager.BOTS[2] = true;
            SHOW_ALL = false;
        }
    }

    private PConfig config;
    private final PUtil pUtil;
    private final Metrics metrics;

    private Logger logger;
    private String logFileName;
    private PrintStream logStream;
    private long logStartDate;
    private final TableLayout tableLayout;

    private InputStream testInputStream;

    public static JFrame mainFrame;
    private final Container mainContainer;

    private Rectangle mainRectangle = new Rectangle();
    private Insets insets;

    private final GameManager gameManager;

    /**
     * @param args optional [fixed-games-file]
     */
    public static void main(String[] args) {
        //Schedule a job for the event-dispatching thread:
        //creating and showing this application's GUI.
        SwingUtilities.invokeLater(() -> {
            Main main = new Main(args);
            main.go();
        });
    }

    public Main(String[] args) {
        Logger.setHolder(this);
        pUtil = new PUtil(this);
        config = (PConfig)Config.unserialize(this);
        if (config == null) {
            config = new PConfig(this);
        }
        config.release.set(release);
        metrics = new Metrics(this);
        new I18n(this);

        String version = PConfig.VERSION + " built " + new SimpleDateFormat("yyyy-MM-dd").format(this.buildDate());
        Logger.printf("%s %s, options 0x%x\n", PConfig.PROJECT_NAME, version, specialOption());
        Logger.println(String.format("running on %s, %d cores\n", PConfig.getOS(),
            Runtime.getRuntime().availableProcessors()));

        if (args.length > 0) {
            String testFileName = args[0];
            File f = new File(testFileName);
            Logger.println(f.getAbsolutePath());
            try {
                testInputStream = new FileInputStream(testFileName);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        GraphicsDevice mainGD = getGraphicsDevice();
        mainFrame = new JFrame(mainGD.getDefaultConfiguration());
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveConfig();
            }
        });

        mainFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                mainRectangle = ((JFrame)e.getSource()).getBounds();
                insets = mainFrame.getInsets();
                config.insetsTop = insets.top;
                Logger.printf(DEBUG_LOG,"main.%s -> %s, %s\n", currMethodName(), e, mainRectangle);
                config.mainPosition.setX(mainRectangle.x);
                config.mainPosition.setY(mainRectangle.y);
                config.mainSize.first = mainRectangle.width;
                config.mainSize.second = mainRectangle.height - insets.top;
                tableLayout.update(null);
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                mainRectangle = ((JFrame)e.getSource()).getBounds();
                insets = mainFrame.getInsets();
                config.insetsTop = insets.top;
                Logger.printf(DEBUG_LOG,"main.%s -> %s, %s\n", currMethodName(), e, mainRectangle);
                config.mainPosition.setX(mainRectangle.x);
                config.mainPosition.setY(mainRectangle.y);
                config.mainSize.first = mainRectangle.width;
                config.mainSize.second = mainRectangle.height - insets.top;
            }
        });
        JFrame.setDefaultLookAndFeelDecorated(true);
        mainFrame.setBounds(mainRectangle);
        Logger.printf(DEBUG_LOG, "Main() %s\n", mainRectangle.toString());
        config.mainSize.first = mainRectangle.width;
        config.mainSize.second = mainRectangle.height;
        metrics.recalculateSizes();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("Shutting down ...");
                Thread.sleep(50);
                saveConfig();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }
        }));

        mainContainer = mainFrame.getContentPane();
        mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.X_AXIS));

        mainFrame.setState(Frame.NORMAL);
        mainFrame.setTitle(PConfig.PROJECT_NAME);
        mainFrame.setVisible(true);

        MainPanel mainPanel = new MainPanel(this);
        mainContainer.add(mainPanel);
        tableLayout = new TableLayout(this, mainPanel);
        config.eventObserver = tableLayout;
        TrickList trickList;
        if (testInputStream == null) {
            gameManager = pUtil.getSerializable(GameManager.class);
            trickList = pUtil.getSerializable(TrickList.class);
        } else {
            gameManager = new GameManager();
            pUtil.register(gameManager);
            trickList = new TrickList();
            pUtil.register(trickList);
        }
        gameManager.init(this);
        trickList.init(new TrickPool());
    }

    public void go() {
        new Thread(() -> {
            try {
                while (true) {
                    gameManager.runGame(testInputStream, 0);
                    Logger.println("game ended!");
                    for (Player p : gameManager.getPlayers()) {
                        p.clearHistory();
                    }
                }
            } catch (HumanPlayer.PrefExceptionRerun e) {
                // ignore
            }
        }).start();
    }

    boolean saved = false;

    private synchronized void saveConfig() {
        if (saved) {
            return;
        }
        saved = true;
        pUtil.serialize();
        config.serialize();
        closeLog();
        logCleanup();
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
        mainRectangle.x = config.mainPosition.getX();
        mainRectangle.y = config.mainPosition.getY();
        mainRectangle.width = config.mainSize.first;
        mainRectangle.height = config.mainSize.second + config.insetsTop;
        if (mainRectangle.width == 0) {
            int w = fullScreen.width - insets.left - insets.right;
            int h = fullScreen.height - insets.top - insets.bottom;
            if ( h > w) {
                w /= 2;
                h = (int)(w * MAGIC_ASPECT_RATIO);
            } else {
                h = h * 2 / 3;
                w = (int)(h / MAGIC_ASPECT_RATIO);
            }
            mainRectangle.width = w;
            mainRectangle.height = h;
            mainRectangle.x = (fullScreen.width - mainRectangle.width) / 2;
            mainRectangle.y = (fullScreen.height - mainRectangle.height) / 2;
        }
        Logger.printf(DEBUG_LOG, "getGraphicsDevice() %s\n", mainRectangle.toString());
        return mainGD;
    }

    @Override
    public PrintStream getLogStream() {
        long today = new Date().getTime() / LOG_THRESHOLD;
        if (today != logStartDate) {
            closeLog();
            String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            try {
                logStream = getNewOutput(date);
                logStartDate = today;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return logStream;
    }

    @Override
    public Logger logger() {
        if (logger == null) {
            logger = new Logger();
        }
        return logger;
    }

    private PrintStream getNewOutput(String date) throws IOException {
        String dataDir = this.getDataDirectory();
        File logDir = new File(dataDir, "logs");
        logDir.mkdir();
        logFileName = logDir + File.separator + String.format("%s%s", date, LOG_EXT);
        FileOutputStream f = new FileOutputStream(logFileName, true);
        System.out.println("output to " + logFileName);
        return new PrintStream(f, true, StandardCharsets.UTF_8.name());
    }

    private void closeLog() {
        if (logStream != null && logStream != System.out) {
            logStream.close();
            logStream = null;
        }
    }

    private void logCleanup() {
        String dataDir = this.getDataDirectory();
        File logDir = new File(dataDir, "logs");
        int deleteTimeout = config.deleteLogsAfter.get();
        Calendar c = new GregorianCalendar();
        c.add(Calendar.HOUR, -24 * deleteTimeout);
        long threshold = c.getTimeInMillis();
        logDir.list((file, itemName) -> {
            if (itemName.endsWith(LOG_EXT)) {
                File f = new File(file, itemName);
                long fileTS = f.lastModified();
                long diff = fileTS - threshold;
                if (diff < 0) {
                    f.delete();
                }
            }
            return false;
        });
    }

    @Override
    public String getDataDirectory() {
        Config.OS os = Config.getOS();
        File file;
        if (os == Config.OS.windows) {
            String userHome = System.getProperty("user.home");
            file = new File(userHome, Config.PROJECT_NAME);
            if (!file.exists()) {
                file.mkdirs();
            }
        } else {
            try {
                file = new File(GameManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                file = new File(file.getParent());
                file.mkdirs();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return file.getAbsolutePath();
    }

    @Override
    public void repaintAll() {
        mainContainer.validate();
        mainContainer.repaint();
    }

    @Override
    public Metrics getMetrics() {
        return metrics;
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public void setConfig(Config config) {
        this.config = (PConfig)config;
    }

    @Override
    public Util getUtil() {
        return pUtil;
    }

    @Override
    public String getLogFileName() {
        return logFileName;
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

    @Override
    public void updateSettings() {
        new SettingsPopup(this);
    }
}