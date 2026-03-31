package com.ab.jprefdoc;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

import com.ab.jpref.config.Config;
import com.ab.util.Logger;
import com.ab.util.Pair;
import com.ab.util.BidData;
import com.ab.util.Tuple;

/*
Утяцкий проделал большую работу, но представил ее в совершенно неудобоваримом виде.
Мне пришлось проверять каждую строку на предмет расклада мастей.
*/

public class Utyatsky {
    public static final boolean DEBUG_LOG = false;

    public static final String[] charMap = {
        "БК->4",
        "\u00A0-> ",
        "х->x", // utyatsky-4
        ", ->,",
        "пик\\S?->0",
        "треф\\S?->1",
        "бубн\\S?->2",
        "черв\\S?->3",

        // hexadecimal ASCII code
        "Т->E",
        "К->D",
        "Д->C",
        "В->B",
    };

    public static String translate(String src, String[] charMap) {
        for (String translate : charMap) {
            String[] parts = translate.split("->");
            String res = "";
            if (parts.length > 1) {
                res = parts[1];
            }
            src = src.replaceAll(parts[0], res);
        }
        return src;
    }

    void run(String dataFileName, List<Tuple<Object>> allBidData) throws IOException {
        String dataDirName = MainDoc.SRC_DIR_NAME;
        File f = new File(dataDirName, dataFileName);
        Logger.println(f.getAbsolutePath());
        Map<String, BidData> map = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(Files.newInputStream(f.toPath())))) {
            String inputLine;
            while ((inputLine = br.readLine()) != null) {
                Logger.println(DEBUG_LOG, inputLine);
                if (inputLine.isEmpty() || inputLine.startsWith("#")) {
                    continue;
                }
                String src = translate(inputLine.trim(), charMap);
                String[] parts = src.split("\\.|\t");
                Logger.println(DEBUG_LOG, src);
                String key = getKey(parts[1]);

                // "2,3";  "1", "2,3"
                List<String> handNums = new ArrayList<>(Arrays.asList(parts[2].split("  ")));
                ArrayList<String> drops = new ArrayList<>();
                List<String> bids = null;
                List<String> biddings = new ArrayList<>();
                int index = 3;
                if (parts.length >= 5) {
                    // - + space
                    biddings = new ArrayList<>(Arrays.asList(parts[3].split("  ")));
                    if (biddings.size() == 2 && biddings.get(0).equals(biddings.get(1))) {
                        biddings.remove(1);
                    }
                    if (biddings.size() == 1 && biddings.get(0).equals("+")) {
                        biddings.set(0, "");
                    }
                    // "2 2"
                    drops = new ArrayList<>(Arrays.asList(parts[4].split("  ")));
                    for (int i = 0; i < drops.size(); ++i) {
                        String drop = drops.get(i);
                        if (drop.startsWith("2 ")) {
                            String suit = drop.substring(drop.length() - 1);
                            drop = suit + "," + suit;
                            drops.remove(i);
                            drops.add(i, drop);
                        }
                    }
                    index = 5;
                } else {
                    // utyatsky-4
//                    totBiddings = 0;
                    biddings.add(" ");
                    drops.add("9 9");
                }

                // "8 0"
                bids = new ArrayList<>(Arrays.asList(parts[index].split("  ")));
                for (int i = 0; i < bids.size(); ++i) {
                    String bid = bids.get(i);
                    try {
                        // utyatsky-4
                        int b = Integer.parseInt(bid);
                        bid += "0";
                    } catch (NumberFormatException e) {
                        bid = bid.replaceAll("\\s", "");
                    }
                    bids.remove(i);
                    bids.add(i, bid);
                }

                int mask = 0x7;
                boolean biddingOn = false;
//                boolean same23 = false;
//                int start = 0, end = 2;
                String firstNum = handNums.get(0);
                if (handNums.size() == 1) {
                    biddingOn = biddings.size() > 1;
                    if (firstNum.trim().isEmpty() || firstNum.equalsIgnoreCase("любая")) {
                        mask = 0x7;
                    }
                    else if (firstNum.equals("1")) {
                        mask = 0x1;
                    } else {
                        // 2,3
                        mask = 0x6;
                    }
                }
                else if (handNums.size() == 2) {
                    if (firstNum.equals(handNums.get(1))) {
                        // must be 2,3
//                        biddingOn = biddings.size() > 1;
                        biddingOn = true;
                        handNums.remove(1);     // incorrect source
                        mask = 0x6;
                    } else if (firstNum.equals("1")) {
                        // 2nd - 2,3
                        mask = 0x7;
                    } else if (firstNum.equals("2")) {
                        // 2nd - 3
                        mask = 0x6;
                    }
                } else {
                    // 3 lines
                    mask = 0x7;
                }

                Logger.printf(DEBUG_LOG, "%s -> %s, %s, %s : biddingOn=%b\n",
                    key, handNums, biddings, drops, biddingOn);

                int nextBit = 1;
                int dropIndx = -1;
                int bidIndx = -1;
                for (int i = 0; i < 3; ++i) {
                    int current = nextBit;
                    nextBit <<= 1;
                    if ((current & mask) == 0) {
                        continue;
                    }

                    int _dropIndx = dropIndx;
                    int _bidIndx = bidIndx;
                    int totBiddings = 1;
                    if (biddingOn) {
                        totBiddings = 2;
                    } else {
                        if (++bidIndx >= bids.size()) {
                            --bidIndx;
                        }
                        if (++dropIndx >= drops.size()) {
                            --dropIndx;
                        }
                    }
                    for (int j = 0; j < totBiddings; ++j) {
                        String bidding = biddings.get(j);
                        if (++_bidIndx >= bids.size()) {
                            --_bidIndx;
                        }
                        String bid = bids.get(_bidIndx);

                        if (++_dropIndx >= drops.size()) {
                            --_dropIndx;
                        }
                        String drop = drops.get(_dropIndx);

                        try {
                            int _drop1 = Integer.parseInt(drop.substring(0, 1));
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                        }
                        int drop1 = Integer.parseInt(drop.substring(0, 1));
                        int drop2 = Integer.parseInt(drop.substring(2, 3));
                        int bid1 = Config.Bid.BID_PASS.getValue();
                        try {
                            bid1 = Integer.parseInt(bid);
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                        BidData.OneBid oneBid = new BidData.OneBid(bid1, drop1, drop2);

                        BidData bidData = map.get(key);
                        if (bidData == null) {
                            bidData = new BidData();
                            map.put(key, bidData);
                            allBidData.add(new Tuple<>(parts[0], key, bidData));
                        }
                        BidData.OneBid old = null, old2 = null;
                        if (bidding.trim().isEmpty() || bidding.charAt(0) == 0xa0) {
                            old = bidData.set(oneBid, i, false);
                            old2 = bidData.set(oneBid, i, true);
                        } else {
                            boolean _bidding = bidding.equals("+");
                            old = bidData.set(oneBid, i, _bidding);
                        }

                        Logger.printf(DEBUG_LOG, "%s -> hand %d: bidding %s, drop %s, declare %s",
                            key, i + 1, bidding, drop, bid);
                        if (old != null || old2 != null) {
                            Logger.printf(DEBUG_LOG, " !! duplicate");
                        }
                        Logger.println(DEBUG_LOG);
                    }
                }
            }
        }
        Logger.println("loaded");
    }

    private String getKey(String src) {
        src = src.trim().replaceAll("10", "A");    // hexadecimal ascii code
        String[] parts = src.split("\\s+");
        StringBuilder sb = new StringBuilder();
//        String sep = "";
        for (String part : parts) {
            Pair<String, Integer> pair = BidData.searchTricks(part, 0);
            sb.append(pair.first);
//            sep = "" + part.length();
//            part = part.replaceAll("x", "");
//            sb.append(sep).append(part);
        }
        return new String(sb);
    }
}