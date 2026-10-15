package com.ab.jpref.engine;

import com.ab.jpref.cards.CardList;
import org.junit.Test;

import java.nio.file.*;
import java.util.*;

public class ScratchAll extends BaseTest {
    @Test
    public void all() throws Exception {
        String only = System.getProperty("only");
        for (String line : Files.readAllLines(Paths.get("../etc/tests/allpassplay"))) {
            if (!line.startsWith("deal:")) continue;
            String body = line.substring(5);
            String comment = body.contains("#") ? body.substring(body.indexOf('#') + 1).trim() : "";
            if (only != null && !comment.startsWith(only)) continue;
            if (body.contains("#")) body = body.substring(0, body.indexOf('#'));
            String[] parts = body.split("->");
            String expected = parts[1].trim();
            List<String> tokens = new ArrayList<>(Arrays.asList(parts[0].trim().split("\\s+")));
            int turn = Integer.parseInt(tokens.remove(tokens.size() - 1));
            CardList deck = new CardList();
            for (String t : tokens) deck.addAll(util.toCardList(t));
            GameManager gm = new GameManager();
            gm.init(host);
            GameManager.DEBUG_LOG = only != null;
            config.pauseBetweenRounds.set(0);
            Bot.targetBot = null;
            new TrickList();
            gm.getTrick().clear(turn);
            gm.elderHand = turn;
            gm.deal(deck);
            gm.playRoundAllPass();
            String actual = String.format("%d, %d, %d", gm.getPlayers()[0].getTricks(), gm.getPlayers()[1].getTricks(), gm.getPlayers()[2].getTricks());
            System.out.printf("CASE #%s expected %s actual %s %s%n", comment, expected, actual, expected.equals(actual) ? "ok" : "DIFF");
        }
    }
}
