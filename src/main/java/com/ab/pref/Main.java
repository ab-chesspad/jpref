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
 * Created: 1/11/2025.
 */
package com.ab.pref;

import com.ab.jpref.engine.Bot;
import com.ab.jpref.engine.GameManager;
import com.ab.util.I18n;
import com.ab.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.PrintStream;
import java.util.Timer;

public class Main {
    static final boolean DEBUG = false;
    public static boolean RELEASE = true;
    public static boolean SHOW_ALL = true;
    public static boolean ALL_BOTS = false;
    public static boolean ALL_HUMANS = false;
    static {
        GameManager.RELEASE = RELEASE;
/*
        if (RELEASE) {
            ALL_HUMANS = false;
            SHOW_ALL = false;
        }
*/
        if (ALL_HUMANS) {
            SHOW_ALL = true;
        }
        if (ALL_BOTS) {
            GameManager.TRICK_TIMEOUT = 0;
        }
    }

    final PConfig pConfig = PConfig.getInstance();
    String GUID;
    static Rectangle mainRectangle;
    static Insets insets;

    public static Container mainContainer;
    public static JFrame mainFrame;
    public static MainPanel mainPanel;

    GameManager gameManager;
    Thread gameThread;

    static String testFileName = null;

    /**
     * @param args optional [fixed-games-file]
     */
    public static void main(String[] args) {
        PrintStream out = System.out;   // output to System.out
        if (GameManager.RELEASE) {
//            out = null;         // output to files
        }
        System.out.println("output to " + out);
        Logger.set(out);

/*
        Signal.handle(new Signal("INT"),  // SIGINT
                signal -> System.out.println("Interrupted by Ctrl+C"));
*/

        //Schedule a job for the event-dispatching thread:
        //creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(() -> new Main(args));
    }

/*
    @Override
    protected void finalize() {
        System.out.println("finalize() 1");
        saveConfig();
    }
*/

    public Main(String[] args) {
        // we need it to upload log files
        GUID = pConfig.GUID.get();
        if (GUID == null) {
            GUID = java.util.UUID.randomUUID().toString();
            pConfig.GUID.set(GUID);
        }

        if (args.length > 0) {
            testFileName = args[0];
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

/*
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    Thread.sleep(200);
                    System.out.println("Shutting down ...");
                    //some cleaning up code...
                    saveConfig();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    e.printStackTrace();
                }
            }
        });
*/

        mainFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Logger.printf(DEBUG,"main.%s -> %s\n", com.ab.util.Util.currMethodName(), e);
                insets = mainFrame.getInsets();
                mainRectangle = ((JFrame)e.getSource()).getBounds();
                mainRectangle.height -= insets.top;
                pConfig.mainRectangle.set(mainRectangle);
                Metrics.getInstance().recalculateSizes();
                setMainPanel();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                Logger.printf(DEBUG, "%s -> %s\n", com.ab.util.Util.currMethodName(), e);
                mainRectangle = ((JFrame)e.getSource()).getBounds();
                pConfig.mainRectangle.set(mainRectangle);
            }
        });

/*
        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(Window e) {

            }
        });
*/

        mainContainer = mainFrame.getContentPane();
        mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.X_AXIS));

        mainFrame.setState(Frame.NORMAL);
        mainFrame.setVisible(true);
    }

    private void saveConfig() {
        mainRectangle.height += insets.top;
        pConfig.serialize();
    }

    void setMainPanel() {
        if (mainPanel == null) {
            mainPanel = new MainPanel();
            mainContainer.add(mainPanel);
        }

        if (gameManager == null) {
            String param = I18n.m(pConfig.playerNames.get());
            final String[] names = param.split(", ");
            gameManager = new GameManager(pConfig, mainPanel,
                    i -> {
//*
                        if (GameManager.TRICK_TIMEOUT == 0) {
                            return new Bot(names[i], i);
                        }
//*/
                        if (ALL_HUMANS || i == 0) {
                            return new HumanPlayer(names[i], mainPanel);
                        }
                        return new Bot(names[i], i);
                    });
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
                Logger.printf(DEBUG, "gameManager started\n");
            }
        }

    }

    private GraphicsDevice getGraphicsDevice() {
        GraphicsDevice mainGD = null;
        Rectangle fullScreen = new Rectangle(0, 0, 0, 10000);
        int x = 10000;
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (GraphicsDevice gd : ge.getScreenDevices()) {
            Logger.printf(DEBUG, "device:'%s' size=(%dx%d)\n",
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
        Logger.printf(DEBUG, "insets=(%dx%dx%dx%d\n)",
                insets.left, insets.right, insets.top, insets.bottom);
        mainRectangle = pConfig.mainRectangle.get();
        if (mainRectangle.width == 0) {
            mainRectangle.width = (fullScreen.width - insets.left - insets.right) / 2;
            mainRectangle.height = (fullScreen.height - insets.top - insets.bottom) / 2;
            mainRectangle.x = (fullScreen.width - mainRectangle.width) / 2;
            mainRectangle.y = (fullScreen.height - mainRectangle.height) / 2;
        }
/*     // debug
            mainRectangle.x = 10;
            mainRectangle.y = 30;
//*/

        return mainGD;
    }
}