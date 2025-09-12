package com.ab.jpref.engine;

//import com.ab.util.com.ab.jpref.engine.BidData;
import com.ab.util.Logger;
import com.ab.util.Pair;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/*
                        bits
AKQJX9x             7   3
Расклад             4*7 12
Рука играющего      3   2
Наличие торговли    1   1
Снос                4   2
Заказ               4*5 5
 */
public class MainDoc {
    public static boolean DEBUG_LOG = false;
    public static final String SRC_DIR_NAME = "../doc";
    public static final String OUTPUT_DIR_NAME = "../../src/main/resources/jpref";
    static final String TRICKS_FILE_NAME = "tricks";
    static final String BIDS_FILE_NAME = "utyatsky-12";
    static final String BIDS_4_FILE_NAME = "utyatsky-4";

    List<Pair<String, BidData>> allBidData = new ArrayList();
//    Map<String, BidData> allBidData = new TreeMap<>(new BidData.HandComparator());
    Map<String, BidData> bidData = new TreeMap<>(new BidData.HandComparator());
    List<Pair<String, Integer>> tricks = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        Logger.set(System.out);
        MainDoc mainDoc = new MainDoc();
/*
        if (args.length > 0) {
            mainDoc.unserialize(args[0]);
            mainDoc.serialize(RES_FILE_NAME + "-1");
        }
*/
        Utyatsky utyatsky = new Utyatsky();
//        utyatsky.run4bidding("utyatsky-4", mainDoc.bidData);

        utyatsky.run("utyatsky-1", mainDoc.allBidData);
        utyatsky.run("utyatsky-2", mainDoc.allBidData);
        mainDoc.complete();
        mainDoc.saveBids(BIDS_FILE_NAME);
        mainDoc.saveTricks(TRICKS_FILE_NAME);
//        mainDoc.save4(BIDS_4_FILE_NAME);
//        new com.ab.jpref.engine.MainDoc().run(args);
    }

    private void complete() throws IOException {
        tricks = loadTricks("tricks-src");

        for (Pair<String, BidData> bidPair : allBidData) {
//        for (Map.Entry<String, BidData> entry : allBidData.entrySet()) {
            int[] drops = new int[2];
            BidData bidData = bidPair.second;
            for (BidData.OneBid oneBid : bidData.allBids) {
                if (oneBid != null) {
                    System.arraycopy(oneBid.drops, 0, drops, 0, drops.length);
                    break;
                }
            }
            String key = bidPair.first;

            int totalTricks = 0;
            Pattern p0 = Pattern.compile("(\\d[BCDE]*)");
            Matcher m0 = p0.matcher(key);
            while (m0.find()) {
                String chunk = m0.group();
                Logger.printf(DEBUG_LOG, "%s\n", chunk);
                int suitTricks = 0;
                for (Pair<String, Integer> pair : tricks) {
                    String list = pair.first;
                    if (list.compareTo(chunk) < 0) {
                        break;
                    }
                    if (list.equals(chunk)) {
                        suitTricks = pair.second;
                        break;
                    }
                }
                totalTricks += suitTricks;
                if (totalTricks > 10) {
                    totalTricks = 10;
                    break;
                }
            }
            int bid = totalTricks * 10;  // using 0th index
            for (int i = 0; i < bidData.allBids.length; ++i) {
                BidData.OneBid oneBid = bidData.allBids[i];
                if (oneBid != null) {
                    continue;
                }
                oneBid = new BidData.OneBid(bid, drops[0], drops[1]);
                bidData.allBids[i] = oneBid;
            }
            Logger.printf("%s -> %s\n", bidPair.first, bidData);
        }
        Logger.println("done");

    }

    private List<Pair<String, Integer>> loadTricks(String dataFileName) throws IOException {
        final String[] charMap = {
            // hexadecimal ASCII code
            "A->E",
            "K->D",
            "Q->C",
            "J->B",
            "X->A",
        };

        List<Pair<String, Integer>> lists = new ArrayList<>();
        String dataDirName = MainDoc.SRC_DIR_NAME;
        File f = new File(dataDirName, dataFileName);
        Logger.println(DEBUG_LOG, f.getAbsolutePath());

        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(new FileInputStream(f)))) {
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
                lists.add(new Pair(key, Integer.parseInt(parts[1])));
            }
        }
        // descending order
        Collections.sort(lists, new Comparator<Pair<String, Integer>>() {
            @Override
            public int compare(Pair<String, Integer> p1, Pair<String, Integer> p2) {
                return p2.first.compareTo(p1.first);
            }
        });
        return lists;
    }

    public void unserialize(String fileName) {
        File f = new File(SRC_DIR_NAME, fileName);
        Logger.println(f.getAbsolutePath());

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(" -> ");
                String key = parts[0];
                String[] bidParts = parts[1].substring(1, parts[1].length() - 1).split(", |\\[|\\]");
                BidData bidData = new BidData();
                int i = -1;
                int j = -1;
                String[] p = new String[3];
                for (String s : bidParts) {
                    if (s.isEmpty()) {
                        continue;
                    }
                    p[++j] = s;
/*
                    if (j == p.length - 1) {
                        Card.Suit drop0 = Card.Suit.fromCode(p[0].charAt(0));
                        Card.Suit drop1 = Card.Suit.fromCode(p[1].charAt(0));
                        Config.Bid bid = Config.Bid.fromName(p[2]);
                        bidData.allBids[++i] = new BidData.OneBid(bid, drop0, drop1);
                        j = -1;
                    }
*/
                }
                allBidData.add(new Pair<>(key, bidData));
            }
        } catch (Exception e) {
            Logger.println(e.getMessage());
        }
    }

    public void saveBids(String fileName) {
        File f = new File(OUTPUT_DIR_NAME, fileName);
        try (PrintStream pr = new PrintStream(f, StandardCharsets.UTF_8.name())) {
            for (Pair<String, BidData> bidPair : allBidData) {
//            for (Map.Entry<String, BidData> entry : allBidData.entrySet()) {
                BidData bidData = bidPair.second;
                pr.printf("%s -> %s\n", bidPair.first, bidData);
            }
        } catch (Exception e) {
            Logger.println(e.getMessage());
        }
        Logger.println("done");
    }

/*
    public void save4(String fileName) {
        File f = new File(OUTPUT_DIR_NAME, fileName);
        try (PrintStream pr = new PrintStream(f, StandardCharsets.UTF_8.name())) {
            for (Map.Entry<String, BidData> entry : bidData.entrySet()) {
                BidData bidData = entry.getValue();
                pr.printf("%s -> %s\n", entry.getKey(), bidData);
            }
        } catch (Exception e) {
            Logger.println(e.getMessage());
        }
        Logger.println("done");
    }
*/

    public void saveTricks(String fileName) {
        File f = new File(OUTPUT_DIR_NAME, fileName);
        try (PrintStream pr = new PrintStream(f, StandardCharsets.UTF_8.name())) {
            for (Pair<String, Integer> pair : tricks) {
                pr.printf("%s\t%d\n", pair.first, pair.second);
            }
        } catch (Exception e) {
            Logger.println(e.getMessage());
        }
        Logger.println("done");
    }

/*
    private void run(String[] args) throws IOException {
        String dataFileName = DATA_FILE_NAME;
        File f = new File(dataFileName);
        Logger.println(f.getAbsolutePath());

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
            String inputLine;
            int prevLen = 9;
            while ((inputLine = br.readLine()) != null) {
                Logger.println(DEBUG_LOG, inputLine);
                if (inputLine.isEmpty() || inputLine.startsWith("#")) {
                    continue;
                }
                String[] parts = inputLine.split("\\s+");
                String strip = parts[0];
                int tricks = Integer.parseInt(parts[1]);
                int len = strip.length();
                Map<String, Integer> map;
                if (len != prevLen) {
                    map = new HashMap<>();
                    maps.add(map);
                    prevLen = len;
                } else {
                    map = maps.get(maps.size() - 1);
                }
                map.put(strip, tricks);
            }
            Logger.println(maps.toString());
        }
        Logger.println("loaded");
        generate();
        Logger.println("done");
    }

    private void generate() {
        int len = 9;
        for (Map map : maps) {

        }
    }
*/
}