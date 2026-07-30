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
 * Created: 7/27/2026 by claude.ai
 *
 */
package com.ab.jpref.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Convenience program (not a JUnit test, same spirit as {@link TestPosition}) that
 * pounds the live Swing GUI with randomized mouse/keyboard input -- like Android's
 * "monkey" tool -- and reports any exception it triggers. Needs a real, non-headless
 * display (a real X server or Xvfb).
 *
 * WARNING: this drives the mouse/keyboard via java.awt.Robot at the OS level. Run it
 * only against an isolated display (e.g. Xvfb) -- never against the display of a
 * desktop you or anyone else is actively using. Even with the app-window scoping
 * this class does for mouse actions, keyboard input goes to whatever window
 * currently has OS focus, window z-order can change at any moment, and some
 * window-manager shortcuts are global -- so on a real, in-use desktop this can and
 * will end up clicking/typing into unrelated applications.
 *
 * 0. Install Xvfb:
 *   sudo apt-get install -y xvfb
 *
 * 1. Start an isolated virtual display (skip if you're reusing an existing one — :99 is already up from our current
 *   run):
 *   Xvfb :99 -screen 0 1280x1024x24 &
 *
 * 2. Compile the app + test classes:
 *   mvn -pl jpref-engine,jpref-gui -am test-compile
 *
 * 3. Run the fuzzer against that display:
 *   DISPLAY=:99 java -cp "jpref-gui/target/classes:jpref-gui/target/test-classes:jpref-engine/target/classes" \
 *        com.ab.jpref.gui.GuiCrashFuzzer [durationSeconds] [seed]
   DISPLAY=:99 java -cp "jpref-gui/target/classes:jpref-gui/target/test-classes:jpref-engine/target/classes" \
        com.ab.jpref.gui.GuiCrashFuzzer 7200 > log
 *   - durationSeconds defaults to 180 if omitted, seed defaults to a random value.
 *   - Exits 0 if no crash was found in that time, 1 (with stack traces printed) if one was.
 *
 * Exits with status 1 and prints every captured stack trace if a crash was found,
 * 0 otherwise.
 */
public class GuiCrashFuzzer {
    private static final int[] KEYS = {
        KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE, KeyEvent.VK_SPACE, KeyEvent.VK_TAB,
        KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
        KeyEvent.VK_0, KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4,
        KeyEvent.VK_5, KeyEvent.VK_6, KeyEvent.VK_7, KeyEvent.VK_8, KeyEvent.VK_9,
    };

    private final List<Throwable> crashes = new CopyOnWriteArrayList<>();
    private final Random random;
    private final long durationMillis;
    private int actionsPerformed = 0;

    public static void main(String[] args) throws Exception {
        int seconds = args.length > 0 ? Integer.parseInt(args[0]) : 180;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : System.nanoTime();
        GuiCrashFuzzer fuzzer = new GuiCrashFuzzer(seconds * 1000L, seed);
        fuzzer.run();
        System.exit(fuzzer.crashes.isEmpty() ? 0 : 1);
    }

    public GuiCrashFuzzer(long durationMillis, long seed) {
        this.durationMillis = durationMillis;
        this.random = new Random(seed);
        System.out.println("GuiCrashFuzzer: seed=" + seed + " durationMs=" + durationMillis);
    }

    public void run() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException(
                "No display available: GuiCrashFuzzer needs a real X server or Xvfb");
        }

        installExceptionCapture();

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Main main = new Main(new String[0]);
                main.config().pauseBetweenTricks.set(0);
                main.config().pauseBetweenRounds.set(0);
                main.go();
            }
        });
        JFrame frame = waitForFrame();

        Robot robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(1);

        long deadline = System.currentTimeMillis() + durationMillis;
        while (System.currentTimeMillis() < deadline && crashes.isEmpty()) {
            try {
                performRandomAction(robot, frame);
            } catch (IllegalComponentStateException e) {
                // window was mid-move/dispose (e.g. a popup closed itself); just retry
            }
            actionsPerformed++;
        }

        report();
    }

    private JFrame waitForFrame() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (Main.mainFrame != null && Main.mainFrame.isShowing()) {
                return Main.mainFrame;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("main window never appeared");
    }

    // restrict clicks/drags to windows this app currently has on screen, so a stray
    // point never lands on the user's desktop or some unrelated application
    private List<Window> visibleAppWindows() {
        List<Window> windows = new ArrayList<>();
        for (Window w : Window.getWindows()) {
            if (w.isShowing()) {
                windows.add(w);
            }
        }
        return windows;
    }

    private void performRandomAction(Robot robot, JFrame frame) throws InterruptedException {
        List<Window> windows = visibleAppWindows();
        if (windows.isEmpty()) {
            return;
        }
        Window target = windows.get(random.nextInt(windows.size()));
        Point origin;
        Dimension size;
        try {
            origin = target.getLocationOnScreen();
            size = target.getSize();
        } catch (IllegalComponentStateException e) {
            return;
        }
        if (size.width <= 0 || size.height <= 0) {
            return;
        }

        int choice = random.nextInt(10);
        if (choice < 6) {
            click(robot, origin, size);
        } else if (choice < 8) {
            drag(robot, origin, size);
// keyboard may cause a mess in config
//        } else if (choice == 8) {
//            keyPress(robot);
        } else {
            maybeResize(frame);
        }
    }

    private Point randomPoint(Point origin, Dimension size) {
        int x = origin.x + random.nextInt(Math.max(1, size.width));
        int y = origin.y + random.nextInt(Math.max(1, size.height));
        return new Point(x, y);
    }

    private void click(Robot robot, Point origin, Dimension size) {
        Point p = randomPoint(origin, size);
        robot.mouseMove(p.x, p.y);
        robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
    }

    private void drag(Robot robot, Point origin, Dimension size) {
        Point from = randomPoint(origin, size);
        Point to = randomPoint(origin, size);
        robot.mouseMove(from.x, from.y);
        robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        int steps = 5 + random.nextInt(10);
        for (int i = 1; i <= steps; ++i) {
            int x = from.x + (to.x - from.x) * i / steps;
            int y = from.y + (to.y - from.y) * i / steps;
            robot.mouseMove(x, y);
        }
        robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
    }

    private void keyPress(Robot robot) {
        // keys go to whatever window currently owns OS focus, not whatever we last
        // clicked -- if that's not one of our own windows, sending a key could hit
        // an unrelated application. getFocusedWindow() only ever returns a window
        // owned by this JVM, so null means focus is elsewhere: skip.
        if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() == null) {
            return;
        }
        int key = KEYS[random.nextInt(KEYS.length)];
        robot.keyPress(key);
        robot.keyRelease(key);
    }

    private void maybeResize(JFrame frame) throws InterruptedException {
        int w = 400 + random.nextInt(1200);
        int h = 300 + random.nextInt(900);
        try {
            SwingUtilities.invokeAndWait(() -> frame.setSize(w, h));
        } catch (java.lang.reflect.InvocationTargetException e) {
            crashes.add(e.getCause() != null ? e.getCause() : e);
        }
    }

    /**
     * Swing's EventDispatchThread swallows exceptions thrown from listener callbacks:
     * it never reaches Thread.UncaughtExceptionHandler, it's only printed to stderr.
     * So the reliable cross-JDK way to detect a GUI crash is to watch stderr for a
     * stack trace, in addition to catching real uncaught exceptions on other threads
     * (e.g. the background game thread).
     */
    private void installExceptionCapture() {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            crashes.add(e);
            System.err.println("Uncaught exception on thread " + t.getName() + ":");
            e.printStackTrace();
        });

        PrintStream realErr = System.err;
        OutputStream sniffer = new OutputStream() {
            private final StringBuilder line = new StringBuilder();

            @Override
            public void write(int b) {
                realErr.write(b);
                if (b == '\n') {
                    checkLine(line.toString());
                    line.setLength(0);
                } else {
                    line.append((char) b);
                }
            }

            private void checkLine(String text) {
                if (text.contains("Exception occurred during event dispatching")
                        || text.matches("^Exception in thread .*")) {
                    crashes.add(new RuntimeException("stderr reported a crash: " + text));
                }
            }
        };
        System.setErr(new PrintStream(sniffer, true));
    }

    private void report() {
        System.out.println("GuiCrashFuzzer: performed " + actionsPerformed + " actions");
        if (crashes.isEmpty()) {
            System.out.println("GuiCrashFuzzer: no crash detected");
            return;
        }
        System.out.println("GuiCrashFuzzer: " + crashes.size() + " crash(es) detected:");
        for (Throwable t : crashes) {
            t.printStackTrace(System.out);
        }
    }
}
