package com.ab.jpref.engine;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
Утяцкий проделал большую работу, но представил ее в совершенно ужасном виде.
Мне пришлось проверять каждую строку на предмет расклада мастей.
*/
/*
                        bits
AKQJX9x             7   3
Расклад             4*7 12
--
Рука играющего      3   2
Наличие торговли    1   1
6
--
Снос                2*4 4
Заказ               5*5 5
 */

import com.ab.util.Logger;
import com.ab.util.Pair;

public class Utyatsky {
    public static boolean DEBUG_LOG = false;

    public static final String[] charMap = {
        "БК->4",
/*
        "Т->A",
        "К->K",
        "Д->Q",
        "В->J",
*/
        "х->x",
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

/*
        "10->X",
        "БК->-",
        "пик\\S?->♠",
        "треф\\S?->♣",
        "бубн\\S?->♦",
        "черв\\S?->♥",
*/
//        "([♠♣♦♥012])-> $1",
    };

    public static final String[] charMap4 = {
        " БК->4",
        "х->x",
        " пик\\S?->0",
        " треф\\S?->1",
        " бубн\\S?->2",
        " черв\\S?->3",
        "пас->59",

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

    void run(String dataFileName, List<Pair<String, BidData>> allBidData) throws IOException {
        String dataDirName = MainDoc.SRC_DIR_NAME;
        File f = new File(dataDirName, dataFileName);
        Logger.println(f.getAbsolutePath());
        Map<String, BidData> map = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
            String inputLine;
            while ((inputLine = br.readLine()) != null) {
                Logger.println(DEBUG_LOG, inputLine);
                if (inputLine.isEmpty() || inputLine.startsWith("#")) {
                    continue;
                }
                String src = translate(inputLine, charMap);
/*
                for (String translate : charMap) {
                    String[] parts = translate.split("->");
                    String res = "";
                    if (parts.length > 1) {
                        res = parts[1];
                    }
                    src = src.replaceAll(parts[0], res);
                }
*/
                String[] parts = src.split("\\.|\t");
                Logger.println(DEBUG_LOG, src);
                String key = getKey(parts[1]);

                // each int - drop1, drop2, bid
//                int[] params = new int[3];  // hand order

                List<String> handNums = new ArrayList<>(Arrays.asList(parts[2].split("  ")));
                List<String> biddings = new ArrayList<>(Arrays.asList(parts[3].split("  ")));
                ArrayList<String> drops = null;
                List<String> bids = null;
                if (parts.length >= 5) {
                    drops = new ArrayList<>(Arrays.asList(parts[4].split("  ")));
                    for (int i = 0; i < drops.size(); ++i) {
                        String drop = drops.get(i);
                        if (drop.startsWith("2")) {
                            String suit = drop.substring(drop.length() - 1);
                            drop = suit + "," + suit;
                            drops.remove(i);
                            drops.add(i, drop);
                        }
                    }

                    bids = new ArrayList<>(Arrays.asList(parts[5].split("  ")));
                    for (int i = 0; i < bids.size(); ++i) {
                        String bid = bids.get(i);
                        bid = bid.replaceAll("\\s", "");
                        bids.remove(i);
                        bids.add(i, bid);
                    }

                    if (biddings.size() == 2 && biddings.get(0).equals(biddings.get(1))) {
                        biddings.remove(1);
                    }
                    if (biddings.size() == 1 && biddings.get(0).equals("+")) {
                        biddings.set(0, "");
                    }
                }

                int mask = 0x7;
                boolean biddingOn = false;
                boolean same23 = false;
                int start = 0, end = 2;
                String firstNum = handNums.get(0);
                if (handNums.size() == 1) {
                    biddingOn = biddings.size() > 1;
                    if (firstNum.toLowerCase().equals("любая")) {
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

/*
                Logger.printf(DEBUG_LOG, "%s -> %s, %s, %s, %s : biddingOn=%b\n",
                    key, handNums, biddings, drops, bids, biddingOn);
/*/
                Logger.printf(DEBUG_LOG, "%s -> %s, %s, %s : biddingOn=%b\n",
                    key, handNums, biddings, drops, biddingOn);

//*/

                int nextBit = 1;
                int dropIndx = -1;
                int bidIndx = -1;
                for (int i = 0; i < 3; ++i) {
                    int current = nextBit;
                    nextBit <<= 1;
                    if ((current & mask) == 0) {
                        continue;
                    }
//                    if (++bidIndx >= bids.size()) {
//                        --bidIndx;
//                    }
//                    String bid = bids.get(bidIndx);

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

//                        Card.Suit drop1 = Card.Suit.fromCode(drop.charAt(0));
//                        Card.Suit drop2 = Card.Suit.fromCode(drop.charAt(2));
                        int drop1 = Integer.parseInt(drop.substring(0, 1));
                        int drop2 = Integer.parseInt(drop.substring(2, 3));
//                        int bid1 = Config.Bid.fromName(bid);
                        int bid1 = Integer.parseInt(bid);
                        BidData.OneBid oneBid = new BidData.OneBid(bid1, drop1, drop2);
//                        int pack = oneBid.pack();
//                        com.ab.jpref.engine.BidData.OneBid unpacked = new com.ab.jpref.engine.BidData.OneBid(pack);

                        BidData bidData = map.get(key);
                        if (bidData == null) {
                            bidData = new BidData();
                            map.put(key, bidData);
                            allBidData.add(new Pair<>(key, bidData));
                        }
                        BidData.OneBid old = null, old2 = null;
                        if (bidding.trim().isEmpty() || bidding.charAt(0) == 0xa0) {
                            old = bidData.set(oneBid, i, false);
                            old2 = bidData.set(oneBid, i, true);
                        } else {
                            boolean _bidding = bidding.equals("+");
                            old = bidData.set(oneBid, i, _bidding);
                        }
//                        allBidData.add(new Pair<>(key, bidData));

                        Logger.printf(DEBUG_LOG, "%s -> hand %d: bidding %s, drop %s, declare %s",
                            key, i + 1, bidding, drop, bid);
                        if (old != null || old2 != null) {
                            Logger.printf(" !! duplicate");
                        }
                        Logger.println(DEBUG_LOG);
                    }
                }
                Logger.println(DEBUG_LOG);
            }
        }
        Logger.println("loaded");
    }

/*
    void run4bidding(String dataFileName, Map<String, BidData> allBidData) throws IOException {
        String dataDirName = MainDoc.SRC_DIR_NAME;
        File f = new File(dataDirName, dataFileName);
        Logger.println(f.getAbsolutePath());

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
            String inputLine;
            while ((inputLine = br.readLine()) != null) {
                Logger.println(inputLine);
                if (inputLine.isEmpty() || inputLine.startsWith("#")) {
                    continue;
                }
                String src = translate(inputLine, charMap4);
                String[] parts = src.split("\\.|\t");
                Logger.println(DEBUG_LOG, src);
                String key = getKey(parts[1]);

                // each int - drop1, drop2, bid
//                int[] params = new int[3];  // hand order

                List<String> handNums = new ArrayList<>(Arrays.asList(parts[2].split("  ")));
                List<String> bids = new ArrayList<>(Arrays.asList(parts[3].split("  ")));

                for (int i = 0; i < bids.size(); ++i) {
                    String bid = bids.get(i);
                    if (bid.length() == 1) {
                        bid += "0";
                        bids.remove(i);
                        bids.add(i, bid);
                    }
                }

                for (int i = 0; i < handNums.size(); ++i) {
                    String handNum = handNums.get(i);
                    String bid;
                    if (i < bids.size()) {
                        bid = bids.get(i);
                    } else {
                        bid = bids.get(0);
                    }
                    int value = Integer.parseInt(bid);
                    int mask = 0x7;
                    if (handNum.equals("1")) {
                        mask = 0x1;
                    } else if (handNum.equals("2, 3")) {
                        mask = 0x6;
                    }
                    BidData.OneBid oneBid = new BidData.OneBid(value, -1, -1);
                    BidData bidData = allBidData.get(key);
                    if (bidData == null) {
                        bidData = new BidData();
                        allBidData.put(key, bidData);
                    }
                    int k = -1;
                    while (mask != 0) {
                        ++k;
                        int bit = mask & 1;
                        mask >>= 1;
                        if (bit == 0) {
                            continue;
                        }
                        bidData.set(oneBid, k, false);
                        bidData.set(oneBid, k, true);
                    }
                }

                Logger.printf( "%s -> %s, %s\n",
                    key, handNums, bids);
                Logger.println(DEBUG_LOG);
            }
        }
        Logger.println("loaded");
    }
*/

    private String getKey(String src) {
        src = src.replaceAll("10", "A");    // hexadecimal ascii code
        String[] parts = src.split("\\s+");
        StringBuilder sb = new StringBuilder();
        String sep = "";
        for (String part : parts) {
//            sep = "" + (8 - part.length());
            sep = "" + part.length();
            part = part.replaceAll("x", "");
            sb.append(sep).append(part);
//            sep = "|";
        }
        return new String(sb);
    }

/*
    private List<String> _getKeys(String src) {
        Pattern p = Pattern.compile("\\((.*?)\\)");
        String[] versions = src.split("\\(.\\)");
        Matcher m = p.matcher(src);
        if (m.find()) {
            String group = m.group(1);
            int start = m.start(1) - 1;
            int end = m.end(1) + 1;
            versions[0] = src.substring(0, start) + src.substring(end);
            versions[1] = src.substring(0, start - 1) + group + src.substring(end);
        }
        List<String> results = new LinkedList<>();

        for (String version : versions) {
            String[] parts = version.split("\\s+");
            StringBuilder sb = new StringBuilder();
            String sep = "";
            for (String part : parts) {
                sb.append(sep).append(part);
                sep = "|";
            }
            results.add(new String(sb));
        }
        return results;
    }
*/

/*
    private void generate() {
        String[] tests = {
            "AKX7 AKxx",
            "KQX987 KQxxxx",
            "QJX8 QJXx",
            "JX987 Jxxxx",
            "J987 Jxxx",
            "J87 xxx",
        };
        for (String src : tests) {
            String[] parts = src.split(" ");
            String curr = parts[0];
            String encoded = BidList.toBidString(curr);
            if (!parts[1].equals(encoded)) {
                Logger.printf(DEBUG_LOG, "%s: %s != %s\n", curr, encoded, parts[1]);
            }
            Logger.printf("for %s:\n", curr);
//            List<String> next = BidList.getStrings(BidList.HAND_LENGTH - curr.length(), curr);
            List<Map.Entry<String, Integer>> next = BidList.getStrings(0, curr);
            Logger.println(DEBUG_LOG, String.join("\n", next.toString()));
        }

        Logger.printf("total %d lists\n", BidList.count());
        List<Map.Entry<String, Integer>> first = BidList.getStrings();
//        Logger.println(String.join("\n", first));
        Logger.println(String.join(", ", first.toString()));


        Logger.println("done");
    }
*/

/*
    List<String> generateNext(List<String> prevs) {
        for (Card.Suit s : Card.Suit.values()) {

        }

    }
*/
}