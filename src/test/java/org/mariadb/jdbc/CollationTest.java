package org.mariadb.jdbc;

import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CollationTest extends BaseTest {
    /**
     * Tables Initialisation.
     * @throws SQLException exception
     */
    @BeforeClass()
    public static void initClass() throws SQLException {
        createTable("emojiTest", "id int unsigned, field longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }

    /**
     * Conj-92 and CONJ-118.
     *
     * @throws SQLException exception
     */
    @Test
    public void emoji() throws SQLException {
        Connection connection = null;
        try {
            connection = setConnection("&useUnicode=yes&useConfigs=maxPerformance");
            String sqlForCharset = "select @@character_set_server";
            ResultSet rs = connection.createStatement().executeQuery(sqlForCharset);
            assertTrue(rs.next());
            final String serverCharacterSet = rs.getString(1);
            sqlForCharset = "select @@character_set_client";
            rs = connection.createStatement().executeQuery(sqlForCharset);
            assertTrue(rs.next());
            String clientCharacterSet = rs.getString(1);
            if ("utf8mb4".equalsIgnoreCase(serverCharacterSet)) {
                assertTrue(serverCharacterSet.equalsIgnoreCase(clientCharacterSet));
            } else {
                connection.createStatement().execute("SET NAMES utf8mb4");
            }
            PreparedStatement ps = connection.prepareStatement("INSERT INTO emojiTest (id, field) VALUES (1, ?)");
            byte[] emoji = new byte[]{(byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0x84};
            ps.setBytes(1, emoji);
            ps.execute();
            ps = connection.prepareStatement("SELECT field FROM emojiTest");
            rs = ps.executeQuery();
            assertTrue(rs.next());
            // compare to the Java representation of UTF32
            assertEquals("\uD83D\uDE04", rs.getString(1));
        } finally {
            connection.close();
        }
    }
}
