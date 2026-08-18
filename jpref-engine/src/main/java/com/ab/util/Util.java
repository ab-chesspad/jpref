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
 * Created: 12/22/2024.
 */
package com.ab.util;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.config.Config;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Util {
    public static final String DATA_FILE_NAME = Config.PROJECT_NAME + ".state";
    public static final String DEAL_MARK = "deal:";

    private static Util instance;
    public static synchronized Util getInstance() {
        if (instance == null) {
            instance = new Util();
        }
        return instance;
    }

    public Util() {}

    private List<Serializable> serializables;

    public void register(Serializable serializable) {
        int i;
        for (i = 0; i < serializables.size(); ++i) {
            Serializable s = serializables.get(i);
            if (s.getClass().equals(serializable.getClass())) {
                break;
            }
        }
        if (i < serializables.size()) {
            serializables.remove(i);
        }
        serializables.add(serializable);
    }

    @SuppressWarnings("unchecked")
    public <T> T getSerializable(Class<T> claz) {
        for (Serializable serializable : serializables) {
            if (claz.isInstance(serializable)) {
                return (T)serializable;
            }
        }
        try {
            Serializable serializable = (Serializable)claz.getDeclaredConstructor().newInstance();
            serializables.add(serializable);
            return (T)serializable;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public void serialize(String dataDirectory) {
        try (FileOutputStream fos = new FileOutputStream(new File(dataDirectory, DATA_FILE_NAME));
                ObjectOutputStream oos = new ObjectOutputStream(fos) ) {
            oos.writeObject(serializables);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void unserialize(String dataDirectory) {
        try (FileInputStream fis = new FileInputStream(new File(dataDirectory, DATA_FILE_NAME));
                ObjectInputStream ois = new ObjectInputStream(fis)) {
            serializables = (List<Serializable>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(e.getMessage());
            serializables = new ArrayList<>();
        }
    }

    // return result file name
    public String submitLog(String filePath) {
        final String CrLf = "\r\n";
        final String url = "http://jpref.elementfx.com/upload.php";
        final String boundary = "---------------------------4664151417711";

        String res;
        OutputStream os = null;
        InputStream is = null;
        File f = new File(filePath);
        String fileName = f.getName();
        String GUID = Config.getInstance().GUID;
        String remoteFileName = GUID + "-" + fileName.substring(0, fileName.length() - 3) + "zip";
        System.out.printf("log %s, sending as %s\n", fileName, remoteFileName);
        try {
            byte[] fileData = getLogBytes(filePath);
            String message1 = "";
            message1 += "--" + boundary + CrLf;
            message1 += "Content-Disposition: form-data; name=\"uploadedfile\"; filename=\"" + remoteFileName + "\"" + CrLf;
            message1 += "Content-Type: text/plain" + CrLf;
            message1 += CrLf;

            // the file is sent between the messages in the multipart message.
            String message2 = "";
            message2 += CrLf + "--" + boundary + "--" + CrLf;

            URLConnection conn = new URI(url).toURL().openConnection();
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type",
                "multipart/form-data; boundary=" + boundary);
            // might not need to specify the content-length when sending chunked data.
            conn.setRequestProperty("Content-Length",
                String.valueOf((message1.length() + message2.length() + fileData.length)));

            os = conn.getOutputStream();
            os.write(message1.getBytes());
            // send the file body
            int index = 0;
            int size = 1024;
            do {
                if ((index + size) > fileData.length) {
                    size = fileData.length - index;
                }
                os.write(fileData, index, size);
                index += size;
            } while (index < fileData.length);
            os.write(message2.getBytes());
            os.flush();
            is = conn.getInputStream();

            char buff = 512;
            int len;
            byte[] data = new byte[buff];
            StringBuilder sb = new StringBuilder();
            do {
                len = is.read(data);
                if (len > 0) {
                    sb.append(new String(data, 0, len)).append("\n");
                }
            } while (len > 0);
            res = sb.toString();
            System.out.println(res);
            if (res.startsWith(GUID)) {
                res = res.substring(GUID.length() + 1);
            }
        } catch(IOException | URISyntaxException e) {
            res = e.toString();
        } finally {
            try {
                os.close();
                if (is == null) {
                    throw new IOException("log submission error");
                }
                is.close();
            } catch(IOException e){
                // ignore
                res = e.toString();
            }
        }
        return res;
    }

    private byte[] getLogBytes(String filePath) throws IOException {
        try (InputStream is = Files.newInputStream(Paths.get(filePath))) {
            byte[] fileData = new byte[is.available()];
            is.read(fileData);
            File f = new File(filePath);
            String fileName = f.getName();
            // --- Compress ---
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry(fileName));  // entry name = filename inside zip
                zos.write(fileData);
                zos.closeEntry();
            }
            return baos.toByteArray();
        }
    }

    public void getList(InputStream is, LineHandler lineHandler) throws IOException {
        final String[] charMap = {
            Card.ANSI_HEAD + ".*?" + Card.ANSI_TAIL + "->", // strip "\u001B.*?m"
        };
        getList(is, charMap, lineHandler);
    }

    public void getList(String filePath, LineHandler lineHandler) throws IOException {
        final String[] charMap = {
            Card.ANSI_HEAD + ".*?" + Card.ANSI_TAIL + "->", // strip "\u001B.*?m"
        };
        File f = new File(filePath);
        String s = f.getAbsolutePath();
        try (InputStream is = Files.newInputStream(Paths.get(filePath))) {
            getList(is, charMap, lineHandler);
        }
    }

    // charMap format "from->to"
    public void getList(InputStream is, String[] charMap, LineHandler lineHandler) throws IOException {
        try (BufferedReader reader =
                 new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                Logger.println(line);
                String[] parts = line.split(" -> ");
                String res = "...";
                if (parts.length >= 2) {
                    res = parts[1];
                }
                String src = parts[0];
                if (charMap != null) {
                    for (String replace : charMap) {
                        String[] translate = replace.split("->");
                        String result = "";
                        if (translate.length > 1) {
                            result = translate[1];
                        }
                        src = src.replaceAll(translate[0], result);
                    }
                }
                if (src.isEmpty()) {
                    continue;
                }
                String[] tokens = src.split(" ");
                List<String> strings = new ArrayList<>();
                for (String t : tokens) {
                    if (t.isEmpty()) {
                        continue;
                    }
                    strings.add(t);
                }
                lineHandler.handleLine(res, strings);
            }
        }
    }

    public CardList toCardList(String src) {
        CardList cards = new CardList();
        if (src.isEmpty()) {
            return cards;
        }
        final String suits = "♠♣♦♥";
        String suit = null;
        int i = 0;
        while (i < src.length()) {
            char ch = src.charAt(i);
            if (ch == ' ') {
                ++i;
                continue;
            }
            int suitIndex = suits.indexOf(ch);
            if (suitIndex >= 0) {
                suit = "" + Card.Suit.values()[suitIndex].getCode();
                ++i;
                continue;
            }
            if (suit != null) {
                String cardName = suit + src.charAt(i);
                cards.add(Card.fromName(cardName));
                ++i;
                continue;
            }

            if (i >= src.length() - 1) {
                break;
            }
            cards.add(Card.fromName(src.substring(i, i + 2)));
            i += 2;
        }
        return cards;
    }

    public interface LineHandler {
        void handleLine(String res, List<String> tokens);
    }

    public static synchronized void sleep(int timeout) {
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static String currMethodName() {
        int i = -1;
        while (!Thread.currentThread().getStackTrace()[++i].getMethodName().equals("currMethodName"));
        return Thread.currentThread().getStackTrace()[++i].getMethodName();
    }
}