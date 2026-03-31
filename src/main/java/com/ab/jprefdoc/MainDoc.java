package com.ab.jprefdoc;

import com.ab.util.Logger;
import com.ab.util.Pair;
import com.ab.util.BidData;
import com.ab.util.Tuple;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainDoc {
    public static final boolean DEBUG_LOG = false;
    public static final String SRC_DIR_NAME = "etc/doc";
    public static final String OUTPUT_DIR_NAME = "src/main/resources/jpref";
    static final String TRICKS_FILE_NAME = "tricks";
    static final String BIDS_FILE_NAME = "utyatsky-12";
//    static final String MAX_BIDS_FILE_NAME = "utyatsky-4";

//    List<Pair<String, BidData>> allBidData = new ArrayList();
    final List<Tuple<Object>> allBidData = new ArrayList<>();
//    List<Pair<String, BidData>> maxBidData = new ArrayList<>();
    List<Pair<String, int[]>> tricks = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        MainDoc mainDoc = new MainDoc();
        mainDoc.run();
    }

    void run() throws IOException {
        tricks = loadTricks("tricks-src");
        // BidData have loaded the old tricks already during initialization
        // so, quick and dirty
        BidData.tricks.clear();
        BidData.tricks.addAll(tricks);

        Utyatsky utyatsky = new Utyatsky();
//        utyatsky.run("utyatsky-4", maxBidData);
        utyatsky.run("utyatsky-1", allBidData);
        utyatsky.run("utyatsky-2", allBidData);
        complete();
        File f = new File(OUTPUT_DIR_NAME);
        f.mkdirs();
        save();
    }

    private void complete() {
//        List<Pair<String, BidData>> newBidData = new ArrayList();
        for (Tuple<Object> bidPair : allBidData) {
            int[] drops = new int[2];
            BidData bidData = (BidData) bidPair.getValue(2);
            boolean foundNullBid = false;
            int maxTricks = 0;
            int suitNum = -1;
            for (BidData.OneBid oneBid : bidData.allBids) {
                if (oneBid != null) {
                    int t = oneBid.bid / 10;
                    if (maxTricks < t) {
                        maxTricks = t;
                        suitNum = oneBid.bid % 10;
                    }
                    System.arraycopy(oneBid.drops, 0, drops, 0, drops.length);
                } else {
                    foundNullBid = true;
                }
            }
            if (!foundNullBid) {
                continue;
            }
            String key = bidPair.getValue(1).toString();

            int[] values = new int[3];
            Pattern p0 = Pattern.compile("(\\d[BCDE]*)");
            Matcher m0 = p0.matcher(key);
            while (m0.find()) {
                String chunk = m0.group();
                Logger.printf(DEBUG_LOG, "%s\n", chunk);
//                int suitTricks = 0;
                for (Pair<String, int[]> pair : tricks) {
                    String list = pair.first;
                    if (list.compareTo(chunk) < 0) {
                        break;
                    }
                    if (list.equals(chunk)) {
//                        suitTricks = pair.second;
                        int[] v = pair.second;
                        for (int i = 0; i < v.length; ++i) {
                            values[i] += v[i];
                        }
                        break;
                    }
                }
/*
                totalTricks += suitTricks;
                if (totalTricks > 10) {
                    totalTricks = 10;
                    break;
                }
*/
            }
            for (int i = 0; i < bidData.allBids.length; ++i) {
                BidData.OneBid oneBid = bidData.allBids[i];
                if (oneBid != null) {
                    continue;
                }
                int t = values[i % 3];
                if (t > maxTricks) {
                    t = maxTricks;  // sanity check
                }
                int bid = t * 10 + suitNum;
                oneBid = new BidData.OneBid(bid, drops[0], drops[1]);
                bidData.allBids[i] = oneBid;
            }
//            Logger.printf("%s -> %s\n", bidPair.first, bidData);
        }
        Logger.println("done");

    }

    private List<Pair<String, int[]>> loadTricks(String dataFileName) throws IOException {
        final String[] charMap = {
            // hexadecimal ASCII code
            "A->E",
            "K->D",
            "Q->C",
            "J->B",
            "X->A",
        };

        List<Pair<String, int[]>> tricks = new ArrayList<>();
        String dataDirName = MainDoc.SRC_DIR_NAME;
        File f = new File(dataDirName, dataFileName);
        Logger.println(DEBUG_LOG, f.getAbsolutePath());

        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(Files.newInputStream(f.toPath())))) {
            String line;
            while ((line = br.readLine()) != null) {
                Logger.println(DEBUG_LOG, line);
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                for (String translate : charMap) {
                    String[] parts = translate.split("->");
                    String res = "";
                    if (parts.length > 1) {
                        res = parts[1];
                    }
                    line = line.replaceAll(parts[0], res);
                }
                String[] parts = line.split("\\s+");
                int len = parts[0].length();
                String key = len + parts[0].replaceAll("x", "");
                int[] values = new int[3];
                int oldVal = -1;
                for (int i = 0; i < values.length; ++i) {
                    if (i < parts.length - 1) {
                        values[i] = Integer.parseInt(parts[i + 1]);
                        oldVal = values[i];
                    } else {
                        values[i] = oldVal;
                    }
                }
                tricks.add(new Pair<>(key, values));
            }
        }
        // descending order
        Collections.sort(tricks, (p1, p2) -> p2.first.compareTo(p1.first));
        return tricks;
    }

    public void save() {
//        saveBids(maxBidData, MAX_BIDS_FILE_NAME);
        saveBids(allBidData, BIDS_FILE_NAME);
        saveTricks(TRICKS_FILE_NAME);
    }

    void saveBids(List<Tuple<Object>> bidDataList, String fileName) {
        File f = new File(OUTPUT_DIR_NAME, fileName);
        try (PrintStream pr = new PrintStream(f, StandardCharsets.UTF_8.name())) {
            for (Tuple<Object> bidPair : bidDataList) {
                String lineNum = bidPair.getValue(0).toString();
                String key = bidPair.getValue(1).toString();
                BidData bidData = (BidData) bidPair.getValue(2);
                Logger.printf("%s: %s -> %s\n", lineNum, key, bidData);
                pr.printf("%s: %s -> %s\n", lineNum, key, bidData);
                List<String> parts = new ArrayList<>();
                Pattern p = Pattern.compile("\\d\\D*");
                Matcher m = p.matcher(key);
                while (m.find()) {
                    parts.add(m.group());
                }
                int free = 4 - parts.size();
                if (free == 0 || !key.endsWith("2")) {
                    continue;
                }
/*
                String last = parts.get(parts.size() - 1);
                int len = Integer.parseInt(last.substring(0, 1));
                last = last.substring(1);
                int avail = len - last.length();
*/

                // distribute avail cards between free suits


/*
                if (!key.endsWith("2") || k > 3) {
                    continue;
                }
*/
                // for hands ending with "  xx" add endings "  x  x"
                key = key.substring(0, key.length() - 1) + "11";
                for (BidData.OneBid oneBid : bidData.allBids) {
                    oneBid.drops[1] = oneBid.drops[0] + 1;
                }
                Logger.printf("%s: %s -> %s\n", lineNum + "-a", key, bidData);
                pr.printf("%s: %s -> %s\n", lineNum + "-a", key, bidData);
            }
        } catch (Exception e) {
            Logger.println("ERROR: " + e.getMessage());
        }
        Logger.println("done");
    }

    public void saveTricks(String fileName) {
        File f = new File(OUTPUT_DIR_NAME, fileName);
        try (PrintStream pr = new PrintStream(f, StandardCharsets.UTF_8.name())) {
            for (Pair<String, int[]> pair : tricks) {
//                pr.printf("%s\t%d\n", pair.first, pair.second);
                pr.printf("%s", pair.first);
                for (int v : pair.second) {
                    pr.printf("\t%s", v);
                }
                pr.println();
            }
        } catch (Exception e) {
            Logger.println(e.getMessage());
        }
        Logger.println("done");
    }
}