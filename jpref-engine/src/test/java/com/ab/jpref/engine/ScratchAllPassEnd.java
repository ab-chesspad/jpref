package com.ab.jpref.engine;

import com.ab.jpref.cards.Card;
import com.ab.jpref.cards.CardList;
import com.ab.jpref.cards.CardSet;
import org.junit.Test;

import static com.ab.util.Logger.printf;

// scratch: the all-pass endgame choice, -Dmine -Dleft -Dright -Dtrick -Dstarted -Dnumber
public class ScratchAllPassEnd extends BaseTest {
    @Test
    public void test() {
        new GameManager().init(host);
        CardSet mine = new CardSet(util.toCardList(System.getProperty("mine", "♠A ♣7A")));
        CardSet left = new CardSet(util.toCardList(System.getProperty("left", "♣JQ ♦QA ♥A")));
        CardSet right = new CardSet(util.toCardList(System.getProperty("right", "♣JQ ♦QA ♥A")));
        Trick trick = new Trick();
        trick.clear(Integer.getInteger("started", 0));
        trick.setNumber(Integer.getInteger("number", 7));
        CardList played = util.toCardList(System.getProperty("trick", "♣X"));
        for (Card c : played) {
            trick.add(c);
        }
        printf("RESULT %s\n", new AllPassEndgame().bestCard(mine.getBitmap(), left.getBitmap(), right.getBitmap(), trick));
    }
}
