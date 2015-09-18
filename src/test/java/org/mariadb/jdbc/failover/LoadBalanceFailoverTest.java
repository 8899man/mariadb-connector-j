package org.mariadb.jdbc.failover;

import org.junit.*;
import org.mariadb.jdbc.HostAddress;
import org.mariadb.jdbc.JDBCUrl;
import org.mariadb.jdbc.internal.mysql.Protocol;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * exemple mvn test  -DdefaultLoadbalanceUrl=jdbc:mysql:loadbalance//localhost:3306,localhost:3307/test?user=root
 */
public class LoadBalanceFailoverTest extends BaseMultiHostTest {
    private Connection connection;

    @Before
    public void init() throws SQLException {
        currentType = TestType.LOADBALANCE;
        initialUrl = initialLoadbalanceUrl;
        proxyUrl = proxyLoadbalanceUrl;
        Assume.assumeTrue(initialLoadbalanceUrl != null);
        connection = null;
    }

    @After
    public void after() throws SQLException {
        assureProxy();
        assureBlackList(connection);
        if (connection != null) connection.close();
    }

    @Test
    public void randomConnection() throws Throwable {
        Assume.assumeTrue(initialLoadbalanceUrl.contains("loadbalance"));
        Map<String, MutableInt> connectionMap = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            connection = getNewConnection(false);
            int serverId = getServerId(connection);
            log.debug("master server found " + serverId);
            MutableInt count = connectionMap.get(String.valueOf(serverId));
            if (count == null) {
                connectionMap.put(String.valueOf(serverId), new MutableInt());
            } else {
                count.increment();
            }
            connection.close();
        }

        Assert.assertTrue(connectionMap.size() >= 2);
        for (String key : connectionMap.keySet()) {
            Integer connectionCount = connectionMap.get(key).get();
            log.debug(" ++++ Server " + key + " : " + connectionCount + " connections ");
            Assert.assertTrue(connectionCount > 1);
        }
        log.debug("randomConnection OK");
    }


    class MutableInt {
        int value = 1; // note that we start at 1 since we're counting

        public void increment() {
            ++value;
        }

        public int get() {
            return value;
        }
    }

}
