/*  This file is part of DPref project.
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
 * Created: 3/13/2026
 *
 */
package com.ab.droid.jpref;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.util.DisplayMetrics;
import android.widget.RelativeLayout;

import android.widget.RelativeLayout.LayoutParams;

import com.ab.droid.jpref.config.DConfig;
import com.ab.droid.jpref.config.DMetrics;
import com.ab.droid.jpref.util.DLogger;
import com.ab.droid.jpref.util.DUtil;
import com.ab.jpref.config.Config;
import com.ab.jpref.config.I18n;
import com.ab.jpref.engine.GameManager;
import com.ab.jpref.engine.HumanPlayer;
import com.ab.jpref.engine.TrickList;
import com.ab.jpref.trickpool.TrickPool;
import com.ab.jpref.ui.Host;
import com.ab.jpref.ui.TableLayout;
import com.ab.util.Logger;
import com.ab.util.Util;

import static com.ab.jpref.config.Config.NOP;
import static com.ab.jpref.config.I18n.m;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class MainActivity extends AppCompatActivity implements Logger.LogHolder, Host {
    static final boolean release = true;
    static boolean DEBUG_LOG = true;
    public static boolean SHOW_ALL = true;
    public static final String LOG_EXT = ".log";
    public static final long LOG_THRESHOLD = 24 * 3600 * 1000;    // 1 day msec
    // deleteLogsAfter lives on PConfig (Swing-only) now, not the shared Config -
    // logCleanup() below still needs a value, so hardcode the same default
    // PConfig.deleteLogsAfter used to have. If Android should let the user
    // configure this too, give DConfig its own Property instead.
    private static final int DELETE_LOGS_AFTER_DAYS = 1;

    static {
        GameManager.RELEASE = release;
        if (release) {
            DEBUG_LOG = false;
            GameManager.BOTS[0] = false;
            GameManager.BOTS[1] = true;
            GameManager.BOTS[2] = true;
            SHOW_ALL = false;
            Logger.DEBUG_LOG = false;
        }
    }
    public static final long buildDate = BuildConfig.TIMESTAMP;

    private DConfig config;
    private DUtil dUtil;
    private DMetrics DMetrics;

    private DLogger logger;
    private String logFileName;
    private PrintStream logStream;
    private long logStartDate;
    TableLayout tableLayout;

    InputStream testInputStream;

    static RelativeLayout mainLayout;

    MainView mainView;
    private TrickList trickList;

    transient Context context;

    GameManager gameManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        context = this;
        super.onCreate(savedInstanceState);
        Logger.setHolder(this);
        installCrashLogger();

        // Show the window immediately - dUtil/config loading below is blocking
        // disk I/O + Java deserialization of the whole saved game state, which
        // used to run right here on the main thread (multi-second cold starts,
        // hundreds of skipped frames). Get a first frame drawn now, then load
        // in the background and finish setup in finishCreate() once it's done.
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        mainLayout = new RelativeLayout(context);
        LayoutParams rlp = new LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        this.setContentView(mainLayout, rlp);

        new Thread(() -> {
            DUtil loadedDUtil = new DUtil(this);
            DConfig loadedConfig = (DConfig) Config.unserialize(this);
            runOnUiThread(() -> finishCreate(loadedDUtil, loadedConfig));
        }, "LoadStateThread").start();
    }

    private void finishCreate(DUtil loadedDUtil, DConfig loadedConfig) {
        dUtil = loadedDUtil;
        config = loadedConfig;
        if (config == null) {
            config = new DConfig(this);
        }
        config.release.set(release);
        DMetrics = new DMetrics(this);
        new I18n(this);
        DConfig.setNonColoredLog();
        setTitle("");
        String version = DConfig.VERSION + " built " + new SimpleDateFormat("yyyy-MM-dd").format(this.buildDate());
        Logger.printf("%s %s, options 0x%x\n", DConfig.PROJECT_NAME, version, specialOption());
        Logger.println(String.format("running on Android SDK %d, %d cores\n",
        Build.VERSION.SDK_INT,
        Runtime.getRuntime().availableProcessors()));

        config.mainSize.first = 0;
        config.mainSize.second = 0;

        // don't call DMetrics.recalculateSizes() here - cardAspectRatio isn't set
        // until MainView's constructor (loadImages()) runs below, and calling it
        // early poisons cardH to 0 (cardAspectRatio==0 at this point); the later,
        // correct call from MainView's constructor then gets skipped by
        // recalculateSizes()'s "nothing changed" early-return since the screen
        // size is identical between the two calls.

        mainView = new MainView(this);
        tableLayout = new TableLayout(this, mainView);
        config.eventObserver = tableLayout;

/*
        try {
            testInputStream = getAssets().open("tests/fixedplay");
            Logger.println(testInputStream.toString());
        } catch (IOException e) {
            Logger.println(e.getMessage());
        }
//*/

        TrickList trickList;
        if (testInputStream == null) {
            gameManager = dUtil.getSerializable(GameManager.class);
            trickList = dUtil.getSerializable(TrickList.class);
        } else {
            gameManager = new GameManager();
            dUtil.register(gameManager);
            trickList = new TrickList();
            dUtil.register(trickList);
        }
        gameManager.init(this);
        trickList.init();

        new Thread(() -> {
            while (true) {
                try {
                    gameManager.runGame(testInputStream, 0);
//                    gameManager.runGame(null, 0);
                    Logger.println("game ended!");
                    testInputStream = null;
                } catch (HumanPlayer.PrefExceptionRerun e) {
                    // ignore
                } catch (Throwable t) {
                    t.printStackTrace(getLogStream());
                    getLogStream().flush();
                    if (mainView.showMessage(m("Program crash"), m("Submit log") + "?",
                            TableLayout.GUI.msgFlagOK | TableLayout.GUI.msgFlagCancel) == TableLayout.GUI.msgFlagOK) {
                        tableLayout.submitLog(null);
                    }
                    gameManager.setRoundStage(GameManager.RoundStage.dealing);
                }
            }
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        // pause to let TableLayout get main rectangle
    }

    @Override
    public void onConfigurationChanged(@NotNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (config == null || tableLayout == null) {
            // rotated before the async load in finishCreate() completed -
            // nothing built yet to update
            return;
        }
        int first = config.mainSize.first;
        int second = config.mainSize.second;
        config.mainSize.first = second;
        config.mainSize.second = first;
        tableLayout.update(null);
    }

    @Override
    public void onStop() {
        saveState();
        super.onStop();
    }

    boolean saved = false;
    private void saveState() {
        if (saved) {
            return;
        }
        if (dUtil == null || config == null) {
            // backgrounded before the async load in finishCreate() completed -
            // nothing loaded yet, so nothing to save
            return;
        }
        saved = true;
        new Thread(() -> {
            try {
                dUtil.serialize();
                config.serialize();
                closeLog();
                logCleanup();
            } finally {
                saved = false;
            }
        }, "SaveStateThread").start();
    }

    @Override
    public synchronized PrintStream getLogStream() {
        long today = new Date().getTime() / LOG_THRESHOLD;
        if (logStream == null || today != logStartDate) {
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

    // The log stream is buffered with autoFlush=false (see getNewOutput()'s
    // comment) for normal logging performance, but that means an uncaught
    // exception that kills the process can take the whole buffered trace down
    // with it, leaving only whatever fragment happened to already be flushed -
    // exactly what made a real crash hard to diagnose. Installing our own
    // handler lets us force the full trace to disk before the process
    // actually dies, then hand off to the platform's own handler so normal
    // crash/termination behavior is unchanged.
    private void installCrashLogger() {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                PrintStream logStream = getLogStream();
                logStream.println("FATAL EXCEPTION on thread " + thread.getName());
                throwable.printStackTrace(logStream);
                logStream.flush();
                closeLog();
            } catch (Throwable loggingFailure) {
                // don't let a logging failure block the real crash handling below
            }
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    @Override
    public Logger logger() {
        if (logger == null) {
            logger = new DLogger();
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
        // autoFlush=true on a raw FileOutputStream forces a synchronous flushed
        // disk write on every single Logger.printf() call - and dragging a card
        // fires several of those per touch-move event plus one per repaint frame
        // (see TableLayout.onMouseDragged/paintHand), all on the UI thread. That
        // showed up as the drag jerking: mostly-smooth touch handling broken up
        // by multi-hundred-ms to multi-second dead stretches with no events
        // processed at all, matching blocking I/O rather than a rendering issue.
        // Buffering avoids a disk write per log line; closeLog() (called from
        // saveState()/onStop(), and on day rollover above) still flushes it.
        return new PrintStream(new BufferedOutputStream(f), false, StandardCharsets.UTF_8.name());
    }

    private synchronized void closeLog() {
        if (logStream != null && logStream != System.out) {
            logStream.close();
            logStream = null;
        }
    }

    private void logCleanup() {
        String dataDir = this.getDataDirectory();
        File logDir = new File(dataDir, "logs");
        int deleteTimeout = DELETE_LOGS_AFTER_DAYS;
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
        PackageManager packageManager = this.getPackageManager();
        String packageName = this.getPackageName();
        PackageInfo packageInfo = null;
        try {
            packageInfo = packageManager.getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        String version = packageInfo.versionName;
        String dataDir = packageInfo.applicationInfo.dataDir;
        return dataDir;
    }

    @Override
    public void repaintAll() {
        mainView.update();
    }

    @Override
    public DMetrics getMetrics() {
        return DMetrics;
    }

    @Override
    public DConfig config() {
        return config;
    }

    @Override
    public void setConfig(Config config) {
        this.config = (DConfig)config;
    }

    @Override
    public Util getUtil() {
        return dUtil;
    }


    @Override
    public String getLogFileName() {
        return logFileName;
    }

    @Override
    public long buildDate() {
        return buildDate;
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

    @Override
    public Config.OS getOS() {
        return DConfig.getOS();
    }
}