/**
 * @author : Alexander Bootman
 * @mailto : ab.jpref@gmail.com
 * @created : 3/3/25
 **/
package com.ab.jpref;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class Check {
    static final boolean DEBUG = false;
    final String urlString = "http://jpref.elementfx.com/check.php";
    final String fileRequest = "?f=%s";
    String dataDirName = "jpref-logs";

    public static void main(String[] args) throws IOException {
        new Check().run(args);
    }

    private void run(String[] args) throws IOException {
        if (args.length > 0) {
            dataDirName = args[0];
        }
        File dataDir = new File(dataDirName);
        String s = dataDir.getAbsolutePath();
        System.out.println(s);
        dataDir.mkdir();
        Set<String> localFiles = new HashSet<>();
        dataDir.list((file, name) -> {
            localFiles.add(name);
            return false;
        });

        List<String> remoteFiles = new LinkedList<>();
        URL url = new URL(urlString);
        URLConnection urlConnection = url.openConnection();
        BufferedReader br = new BufferedReader(
                new InputStreamReader(urlConnection.getInputStream()));
        String inputLine;
        while ((inputLine = br.readLine()) != null) {
            if (inputLine.isEmpty()) {
                continue;
            }
            if (!localFiles.contains(inputLine)) {
                System.out.println(inputLine);
                remoteFiles.add(inputLine);
            }
        }
        br.close();

        for (String remoteFile : remoteFiles) {
            FileOutputStream fos = new FileOutputStream(new File(dataDir, remoteFile));
            url = new URL(urlString + String.format(fileRequest, remoteFile));
            urlConnection = url.openConnection();
            br = new BufferedReader(
                    new InputStreamReader(urlConnection.getInputStream()));
            while ((inputLine = br.readLine()) != null) {
                System.out.println(inputLine);
                fos.write(inputLine.getBytes());
                fos.write("\n".getBytes());
            }
            fos.close();
            br.close();
        }
    }

}
