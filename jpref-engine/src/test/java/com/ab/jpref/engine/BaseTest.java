package com.ab.jpref.engine;

import com.ab.jpref.config.Config;
import com.ab.jpref.ui.Host;
import com.ab.util.Logger;
import com.ab.util.Util;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.PrintStream;

public class BaseTest {
    protected static TestHost host;
    protected static TestConfig config;
    protected static TestUtil util;

    @BeforeClass
    public static void initClass() {
        host = new TestHost();
        config = new TestConfig(host);
        util = new TestUtil(host);
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
        public PrintStream getLogStream() {
            return System.out;
        }

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