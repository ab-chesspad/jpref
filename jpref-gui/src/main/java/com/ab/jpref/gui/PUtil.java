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
 * Created: 2/15/2025
 */
package com.ab.jpref.gui;

import com.ab.jpref.engine.GameManager;
import com.ab.util.Util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URISyntaxException;

public class PUtil extends Util {
    private static PUtil instance;

    public static PUtil getInstance() {
        if (instance == null) {
            instance = new PUtil();
        }
        return instance;
    }

    @Override
    public String getDataDirectory() {
        OS os = getOS();
        File file;
        if (os == Util.OS.windows) {
            String userHome = System.getProperty("user.home");
            file = new File(userHome, PROJECT_NAME);
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

    public BufferedImage loadImage(String path) {
        BufferedImage image = null;
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try {
            InputStream is = classloader.getResourceAsStream(path);
            image = ImageIO.read(is);
        } catch (Exception e) {
            // ignore
        }
        return image;
    }

    public BufferedImage scale(BufferedImage original, int newWidth, int newHeight) {
        if (newWidth < 10 || newHeight < 10) {
            return original;    // quick & dirty
        }
        BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaledImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return scaledImage;
    }
}