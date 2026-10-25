package com.ab.jpref.engine;

import com.ab.jpref.config.Config;
import com.ab.jpref.ui.Host;
import com.ab.util.Logger;
import com.ab.util.Util;
import org.junit.Before;
import org.junit.BeforeClass;

public class BaseTest {
    protected static TestHost host;
    protected static TestConfig config;
    protected static TestUtil util;

    @BeforeClass
    public static void initClass() {
        Util.DEBUG_LOG = true;
        host = new TestHost();
        config = new TestConfig(host);
        config.maxMoveTime.set(20);
        util = new TestUtil(host);
    }

    // Bot statics survive between tests, and maven runs them all in one JVM, in an order
    // the IDE does not: e.g. a debugDrop left by testFixedPlay forces wrong drops in testFreePlay
    @Before
    public void resetBotStatics() {
        Bot.debugDrop = null;
        Bot.playerBid = null;
        MisereBot.declarerDrop = MisereBot.DeclarerDrop.First;      // тест results must not depend on chance
        ForTricksBot.declarerDrop = MisereBot.DeclarerDrop.First;   // тест results must not depend on chance
    }

    public static class TestConfig extends Config {
        protected TestConfig(TestHost host) {
            super(host);
            host.setConfig(this);
            Logger.setHolder(host);
        }
    }

    public static class TestUtil extends Util {
        protected TestUtil(Host host) {
            this.host = host;
        }
    }

    public static class TestHost implements Logger.LogHolder, Host {
        private Logger logger;

        @Override
        public Logger logger() {
            if (logger == null) {
                logger = new Logger();
            }
            return logger;
        }

        @Override
        public Config config() {
            return config;
        }

        @Override
        public Util getUtil() {
            return util;
        }
    }
}