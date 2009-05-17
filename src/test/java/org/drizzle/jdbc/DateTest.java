package org.drizzle.jdbc;

import org.junit.Test;
import org.drizzle.jdbc.internal.common.Utils;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Logger;
import java.util.logging.Level;

import static junit.framework.Assert.assertEquals;

/**
 * Created by IntelliJ IDEA.
 * User: marcuse
 * Date: Mar 1, 2009
 * Time: 10:01:23 PM
 * To change this template use File | Settings | File Templates.
 */
public class DateTest {
    static { Logger.getLogger("").setLevel(Level.OFF); }
    @Test
    public void dateTest() throws SQLException {
        try {
            Class.forName("org.drizzle.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Could not load driver");
        }
        Connection connection = DriverManager.getConnection("jdbc:drizzle://"+DriverTest.host+":4427/test_units_jdbc");

        Statement stmt = connection.createStatement();
        stmt.executeUpdate("drop table if exists date_test");
        stmt.executeUpdate("create table date_test (d_test date,dt_test datetime, t_test int)");
        Date date = Date.valueOf("2009-01-17");
        Timestamp timestamp = Timestamp.valueOf("2009-01-17 15:41:01");
        Time time = Time.valueOf("23:59:59");
        PreparedStatement ps = connection.prepareStatement("insert into date_test values (?,?,?)");
        ps.setDate(1,date);
        ps.setTimestamp(2,timestamp);
        ps.setTime(3,time);
        ps.executeUpdate();
        ResultSet rs = stmt.executeQuery("select * from date_test");
        assertEquals(true,rs.next());
        Date date2 = rs.getDate(1);
        Time time2=rs.getTime(3);
        Timestamp timestamp2=rs.getTimestamp(2);
        assertEquals(date.toString(), date2.toString());
        assertEquals(time.toString(), time2.toString());
        assertEquals(timestamp.toString(), timestamp2.toString());

    }

    @Test
    public void timePackTest() {
        for(int hours = 0;hours<24;hours++) {
            for(int minutes=0;minutes<60;minutes++) {
                for(int seconds = 0;seconds<60;seconds++) {
                    long millis = hours*60*60*1000 + minutes*60*1000 + seconds*1000;
                    int packed = Utils.packTime(millis);
                    long unPacked = Utils.unpackTime(packed);
                    assertEquals(millis, unPacked);
                }
            }
        }
    }
}
