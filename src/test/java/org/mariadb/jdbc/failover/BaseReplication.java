package org.mariadb.jdbc.failover;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseReplication extends BaseMultiHostTest {

    @Test
    public void testWriteOnMaster() throws SQLException {
        Connection connection = null;
        try {
            connection = getNewConnection(false);
            Statement stmt = connection.createStatement();
            stmt.execute("drop table  if exists auroraMultiNode" + jobId);
            stmt.execute("create table auroraMultiNode" + jobId + " (id int not null primary key auto_increment, test VARCHAR(10))");
            stmt.execute("drop table  if exists auroraMultiNode" + jobId);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    @Test
    public void failoverSlaveToMaster() throws Throwable {
        Connection connection = null;
        try {
            connection = getNewConnection("&retriesAllDown=3&connectTimeout=1000&socketTimeout=1000", true);
            int masterServerId = getServerId(connection);
            connection.setReadOnly(true);
            int slaveServerId = getServerId(connection);
            Assert.assertFalse(masterServerId == slaveServerId);
            stopProxy(slaveServerId);
            connection.createStatement().execute("SELECT 1");
            int currentServerId = getServerId(connection);

            Assert.assertTrue(masterServerId == currentServerId);
            Assert.assertFalse(connection.isReadOnly());
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    @Test
    public void failoverDuringSlaveSetReadOnly() throws Throwable {
        Connection connection = null;
        try {
            connection = getNewConnection("&socketTimeout=3000", true);
            connection.setReadOnly(true);
            int slaveServerId = getServerId(connection);

            stopProxy(slaveServerId, 2000);
            connection.setReadOnly(false);
            int masterServerId = getServerId(connection);

            Assert.assertFalse(slaveServerId == masterServerId);
            Assert.assertFalse(connection.isReadOnly());
        } finally {
            Thread.sleep(2500); //for not interfering with other tests
            if (connection != null) {
                connection.close();
            }
        }
    }

    @Test()
    public void failoverSlaveAndMasterWithoutAutoConnect() throws Throwable {
        Connection connection = null;
        try {
            connection = getNewConnection("&retriesAllDown=3&connectTimeout=1000&socketTimeout=1000", true);
            int masterServerId = getServerId(connection);
            connection.setReadOnly(true);
            int firstSlaveId = getServerId(connection);

            stopProxy(masterServerId);
            stopProxy(firstSlaveId);

            try {
                //will connect to second slave that isn't stopped
                connection.createStatement().executeQuery("SELECT CONNECTION_ID()");
            } catch (SQLException e) {
                Assert.fail();
            }
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    @Test
    public void reconnectSlaveAndMasterWithAutoConnect() throws Throwable {
        Connection connection = null;
        try {
            connection = getNewConnection("&retriesAllDown=3&connectTimeout=1000&socketTimeout=1000", true);

            //search actual server_id for master and slave
            int masterServerId = getServerId(connection);

            connection.setReadOnly(true);

            int firstSlaveId = getServerId(connection);

            stopProxy(masterServerId);
            stopProxy(firstSlaveId);

            //must reconnect to the second slave without error
            connection.createStatement().execute("SELECT 1");
            int currentSlaveId = getServerId(connection);
            Assert.assertTrue(currentSlaveId != firstSlaveId);
            Assert.assertTrue(currentSlaveId != masterServerId);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }


    @Test
    public void failoverMasterWithAutoConnect() throws Throwable {
        Connection connection = null;
        try {
            connection = getNewConnection("&retriesAllDown=3&connectTimeout=1000&socketTimeout=1000", true);
            int masterServerId = getServerId(connection);

            stopProxy(masterServerId, 250);
            //with autoreconnect, the connection must reconnect automatically
            int currentServerId = getServerId(connection);

            Assert.assertTrue(currentServerId == masterServerId);
            Assert.assertFalse(connection.isReadOnly());
        } finally {
            Thread.sleep(500); //for not interfering with other tests
            if (connection != null) {
                connection.close();
            }
        }
    }

    @Test
    public void writeToSlaveAfterFailover() throws Throwable {
        Connection connection = null;
        try {
            connection = getNewConnection("&retriesAllDown=3&connectTimeout=1000&socketTimeout=1000", true);
            //if super user can write on slave
            Assume.assumeTrue(!hasSuperPrivilege(connection, "writeToSlaveAfterFailover"));
            Statement st = connection.createStatement();
            st.execute("drop table  if exists writeToSlave" + jobId);
            st.execute("create table writeToSlave" + jobId + " (id int not null primary key , amount int not null) ENGINE = InnoDB");
            st.execute("insert into writeToSlave" + jobId + " (id, amount) VALUE (1 , 100)");

            int masterServerId = getServerId(connection);

            stopProxy(masterServerId);
            try {
                st.execute("insert into writeToSlave" + jobId + " (id, amount) VALUE (2 , 100)");
                Assert.fail();
            } catch (SQLException e) {
                //normal exception
                restartProxy(masterServerId);
                st = connection.createStatement();
                st.execute("drop table  if exists writeToSlave" + jobId);
            }
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }


    @Test()
    public void checkNoSwitchConnectionDuringTransaction() throws Throwable {
        Connection connection = null;
        try {
            connection = getNewConnection("&retriesAllDown=3&connectTimeout=1000&socketTimeout=1000", false);
            Statement st = connection.createStatement();

            st.execute("drop table  if exists multinodeTransaction2_" + jobId);
            st.execute("create table multinodeTransaction2_" + jobId + " (id int not null primary key , amount int not null) "
                    + "ENGINE = InnoDB");
            connection.setAutoCommit(false);
            st.execute("insert into multinodeTransaction2_" + jobId + " (id, amount) VALUE (1 , 100)");

            try {
                //in transaction, so must trow an error
                connection.setReadOnly(true);
                Assert.fail();
            } catch (SQLException e) {
                //normal exception
                connection.setReadOnly(false);
                st.execute("drop table  if exists multinodeTransaction2_" + jobId);
            }
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }


    @Test
    public void randomConnection() throws Throwable {
        Connection connection = null;
        Map<String, MutableInt> connectionMap = new HashMap<>();
        int masterId = -1;
        for (int i = 0; i < 20; i++) {
            try {
                connection = getNewConnection(false);
                int serverId = getServerId(connection);
                if (i > 0) {
                    Assert.assertTrue(masterId == serverId);
                }
                masterId = serverId;
                connection.setReadOnly(true);
                int replicaId = getServerId(connection);
                MutableInt count = connectionMap.get(String.valueOf(replicaId));
                if (count == null) {
                    connectionMap.put(String.valueOf(replicaId), new MutableInt());
                } else {
                    count.increment();
                }
            } finally {
                if (connection != null) {
                    if (connection != null) {
                        connection.close();
                    }
                }
            }
        }

        Assert.assertTrue(connectionMap.size() >= 2);
        for (String key : connectionMap.keySet()) {
            Integer connectionCount = connectionMap.get(key).get();
            Assert.assertTrue(connectionCount > 1);
        }

    }

    class MutableInt {

        private int value = 1; // note that we start at 1 since we're counting

        public void increment() {
            ++value;
        }

        public int get() {
            return value;
        }
    }

    @Test
    public void closeWhenInReconnectionLoop() throws Throwable {
        Connection connection = null;
        try {
            connection = getNewConnection("&connectTimeout=1000&socketTimeout=1000", true);
            connection.setReadOnly(true);
            int serverId = getServerId(connection);
            stopProxy(serverId);

            //trigger the failover, so a failover thread is launched
            Statement stmt = connection.createStatement();
            stmt.execute("SELECT 1");
            //launch connection close
            new CloseConnection(connection).run();
        } finally {
            if (connection != null) {
                if (connection != null) {
                    connection.close();
                }
            }
        }

    }

    protected class CloseConnection implements Runnable {
        Connection connection;

        public CloseConnection(Connection connection) {
            this.connection = connection;
        }

        public void run() {
            try {
                Thread.sleep(2400); // wait that slave reconnection loop is launched
                connection.close();
            } catch (Throwable e) {
                e.printStackTrace();
                Assert.fail();
            }
        }
    }


    @Test
    public void relaunchWithoutErrorWhenAutocommit() throws Throwable {
        Connection connection = null;
        try {
            connection = getNewConnection("&connectTimeout=1000&socketTimeout=1000", true);
            Statement st = connection.createStatement();
            int masterServerId = getServerId(connection);
            long startTime = System.currentTimeMillis();
            stopProxy(masterServerId, 10000);
            try {
                st.execute("SELECT 1");
                if (System.currentTimeMillis() - startTime < 10 * 1000) {
                    Assert.fail("Auto-reconnection must have been done after 10000ms but was " + (System.currentTimeMillis() - startTime));
                }
            } catch (SQLException e) {
                Assert.fail("must not have thrown error");
            }
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    @Test
    public void relaunchWithErrorWhenInTransaction() throws Throwable {
        Connection connection = null;
        try {
            connection = getNewConnection("&connectTimeout=1000&socketTimeout=1000", true);
            Statement st = connection.createStatement();
            st.execute("drop table if exists baseReplicationTransaction" + jobId);
            st.execute("create table baseReplicationTransaction" + jobId + " (id int not null primary key auto_increment, test VARCHAR(10))");

            connection.setAutoCommit(false);
            st.execute("INSERT INTO baseReplicationTransaction" + jobId + "(test) VALUES ('test')");
            int masterServerId = getServerId(connection);
            st.execute("SELECT 1");
            long startTime = System.currentTimeMillis();;
            stopProxy(masterServerId, 2000);
            try {
                st.execute("SELECT 1");
                if (System.currentTimeMillis() - startTime < 2000) {
                    Assert.fail("Auto-reconnection must have been done after 2000ms but was " + (System.currentTimeMillis() - startTime));
                }
                Assert.fail("must not have thrown error");
            } catch (SQLException e) {
                Assert.assertEquals("error type not normal after " + (System.currentTimeMillis() - startTime) + "ms", "25S03", e.getSQLState());
            }
            st.execute("drop table if exists baseReplicationTransaction" + jobId);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    @Test
    public void failoverRelaunchedWhenSelect() throws Throwable {
        Connection connection = null;
        try {
            connection = getNewConnection("&connectTimeout=1000&socketTimeout=1000&retriesAllDown=3", true);
            Statement st = connection.createStatement();

            final int masterServerId = getServerId(connection);
            st.execute("drop table if exists selectFailover" + jobId);
            st.execute("create table selectFailover" + jobId + " (id int not null primary key , amount int not null) "
                    + "ENGINE = InnoDB");
            stopProxy(masterServerId, 2);
            try {
                st.execute("SELECT * from selectFailover" + jobId);
            } catch (SQLException e) {
                Assert.fail("must not have thrown error");
            }

            stopProxy(masterServerId, 2);
            try {
                st.execute("INSERT INTO selectFailover" + jobId + " VALUES (1,2)");
                Assert.fail("not have thrown error !");
            } catch (SQLException e) {
                Assert.assertEquals("error type not normal", "25S03", e.getSQLState());
            }
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }


    @Test
    public void failoverRelaunchedWhenInTransaction() throws Throwable {
        Connection connection = null;
        try {
            connection = getNewConnection("&connectTimeout=1000&socketTimeout=1000&retriesAllDown=3", true);
            Statement st = connection.createStatement();

            final int masterServerId = getServerId(connection);
            st.execute("drop table if exists selectFailover" + jobId);
            st.execute("create table selectFailover" + jobId + " (id int not null primary key , amount int not null) "
                    + "ENGINE = InnoDB");
            connection.setAutoCommit(false);
            st.execute("INSERT INTO selectFailover" + jobId + " VALUES (0,0)");
            stopProxy(masterServerId, 2);
            try {
                st.execute("SELECT * from selectFailover" + jobId);
                Assert.fail("not have thrown error !");
            } catch (SQLException e) {
                Assert.assertEquals("error type not normal", "25S03", e.getSQLState());
            }

            stopProxy(masterServerId, 2);
            try {
                st.execute("INSERT INTO selectFailover" + jobId + " VALUES (1,2)");
                Assert.fail("not have thrown error !");
            } catch (SQLException e) {
                Assert.assertEquals("error type not normal", "25S03", e.getSQLState());
            }
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

}
