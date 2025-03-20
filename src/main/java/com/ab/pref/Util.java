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
 * Created: 2/15/2025
 */
package com.ab.pref;

import javax.imageio.ImageIO;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URLConnection;
import java.net.URL;

public class Util {
    public static boolean DEBUG = true;

    public static BufferedImage loadImage(String path) {
        BufferedImage image = null;
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try {
            InputStream is = classloader.getResourceAsStream(path);
            image = ImageIO.read(is);
        } catch (Exception e) {
            // ignore
//            throw new RuntimeException(e);
        }
        return image;
    }

    public static BufferedImage scale(BufferedImage original, int newWidth, int newHeight) {
        if (newWidth < 10 || newHeight < 10) {
            return original;    // quick & dirty
        }
        BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        double scale = (double) newWidth / original.getWidth(null);
        double scaleH = (double) newHeight / original.getHeight(null);
        AffineTransform scaleInstance = AffineTransform.getScaleInstance(scale, scale);
        AffineTransformOp scaleOp
                = new AffineTransformOp(scaleInstance, AffineTransformOp.TYPE_BILINEAR);
        scaleOp.filter(original, scaledImage);
        return scaledImage;
    }

    public static String submitLog(String filePath) {
        final String CrLf = "\r\n";
        final String url = "http://jpref.elementfx.com/upload.php";
        final String boundary = "---------------------------4664151417711";

        String res;
        OutputStream os = null;
        InputStream is = null;
        File f = new File(filePath);
        String fileName = f.getName();
        String GUID = PConfig.getInstance().GUID.get();
        String remoteFileName = GUID + "-" + fileName;
        System.out.printf("log %s, sending as %s\n", fileName, remoteFileName);

        try (InputStream input = new FileInputStream(filePath)) {
            byte[] fileData= new byte[input.available()];
            input.read(fileData);
//            System.out.println("url:" + url);
            String message1 = "";
            message1 += "--" + boundary + CrLf;
            message1 += "Content-Disposition: form-data; name=\"uploadedfile\"; filename=\"" + remoteFileName + "\"" + CrLf;
            message1 += "Content-Type: text/plain" + CrLf;
            message1 += CrLf;

            // the file is sent between the messages in the multipart message.

            String message2 = "";
            message2 += CrLf + "--" + boundary + "--" + CrLf;

            URLConnection conn = new URL(url).openConnection();
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + boundary);
            // might not need to specify the content-length when sending chunked data.
            conn.setRequestProperty("Content-Length",
                    String.valueOf((message1.length() + message2.length() + fileData.length)));

//            System.out.println("open os");
            os = conn.getOutputStream();
//            System.out.println(message1);
            os.write(message1.getBytes());
            // SEND THE file body
            int index = 0;
            int size = 1024;
            do {
//                System.out.println("write:" + index);
                if ((index + size) > fileData.length) {
                    size = fileData.length - index;
                }
                os.write(fileData, index, size);
                index += size;
            } while (index < fileData.length);
//            System.out.println("written:" + index);
            os.write(message2.getBytes());
            os.flush();
//            System.out.println("open is");
            is = conn.getInputStream();

            char buff = 512;
            int len;
            byte[] data = new byte[buff];
            StringBuilder sb = new StringBuilder();
            do {
//                System.out.println("READ");
                len = is.read(data);
                if (len > 0) {
                    sb.append(new String(data, 0, len)).append("\n");
//                    System.out.println(new String(data, 0, len));
                }
            } while (len > 0);
            res = sb.toString();
//            System.out.println("DONE");
        } catch(IOException e) {
//            throw new RuntimeException(e);
            res = e.toString();
        } finally {
//            System.out.println("Close connection");
            try {
                os.close();
                is.close();
            } catch(IOException e){
//                throw new RuntimeException(e);
                // ignore
            }
        }
        return res;
    }
}
