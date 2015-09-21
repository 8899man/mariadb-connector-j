package org.mariadb.jdbc;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TimezoneDaylightSavingTimeTest extends BaseTest {

    private Locale previousFormatLocale;
    private TimeZone previousTimeZone;
    private TimeZone utcTimeZone;
    private SimpleDateFormat utcDateFormatISO8601;
    private SimpleDateFormat utcDateFormatSimple;
    private TimeZone istanbulTimeZone;
    private TimeZone parisTimeZone;

    @Before
    public void setUp() throws SQLException {

        Statement st = null;
        try {
            st = connection.createStatement();
            ResultSet rs = st.executeQuery("SELECT count(*) from mysql.time_zone");
            rs.next();
            if (rs.getInt(1) == 0) {
                ResultSet rs2 = st.executeQuery("SELECT DATABASE()");
                rs2.next();
                String currentDatabase = rs2.getString(1);
                st.execute("USE mysql");

                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                importSQL(connection, classLoader.getResourceAsStream("timezone_posix.sql"));
                st.execute("USE " + currentDatabase);
            }
            st.execute("drop table if exists timestamptest");
            st.execute("create table timestamptest (id int not null primary key auto_increment, tm timestamp)");

        } finally {
            if (st != null) st.close();
        }

        //Save the previous FORMAT locate so we can restore it later
        previousFormatLocale = Locale.getDefault();
        //Save the previous timezone so we can restore it later
        previousTimeZone = TimeZone.getDefault();

        //I have tried to represent all times written in the code in the UTC time zone
        utcTimeZone = TimeZone.getTimeZone("UTC");

        //For this test case I choose the Istanbul timezone because it show the fault in a good way.
        istanbulTimeZone = TimeZone.getTimeZone("Europe/Istanbul");
        TimeZone.setDefault(istanbulTimeZone);

        parisTimeZone = TimeZone.getTimeZone("Europe/Paris");

        //Use a date formatter for UTC timezone in ISO 8601 so users in different
        //timezones can compare the test results easier.
        utcDateFormatISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        utcDateFormatISO8601.setTimeZone(utcTimeZone);

        utcDateFormatSimple = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        utcDateFormatSimple.setTimeZone(utcTimeZone);

    }



    public static void importSQL(Connection conn, InputStream in) throws SQLException {
        Scanner s = new Scanner(in);
        s.useDelimiter("(;(\r)?\n)|(--\n)");
        Statement st = null;
        try {
            st = conn.createStatement();
            while (s.hasNext()) {
                String line = s.next();
                if (line.startsWith("/*!") && line.endsWith("*/")) {
                    int i = line.indexOf(' ');
                    line = line.substring(i + 1, line.length() - " */".length());
                }
                if (line.trim().length() > 0) st.execute(line);
            }
        } finally {
            if (st != null) st.close();
        }
    }

    @After
    public void tearDown() {
        //Reset the FORMAT locate so other test cases are not disturbed.
        if (previousFormatLocale != null) Locale.setDefault(previousFormatLocale);
        //Reset the timezone so so other test cases are not disturbed.
        if (previousTimeZone != null) TimeZone.setDefault(previousTimeZone);
    }


    /**
     * This method is designed to correctly insert a timestamp in the database. It manually forces
     * the session to be in UTC and transfers the timestamp as a String to minimize the impact the
     * JDBC code may have over it.
     */
    private void insertTimestampInWithUTCSessionTimeZone(Calendar timestamp) throws SQLException {
        //Totally reset of the connection to be sure everything is clean
        connection.close();

        before(); //Force a new Connection (may be replaced by a getConnection() method if CONJ-112 is implemented
        Statement statement = connection.createStatement();

        setSessionTimeZone(connection, "+00:00");

        //Use the "wrong" way to insert a timestamp: as a string. This is done to avoid possible bugs
        //in the PreparedStatement.setTimestamp(int parameterIndex, Timestamp x) method and to make
        //sure the value is a correct UTC time.
        String _timestamp = utcDateFormatSimple.format(new Date(timestamp.getTimeInMillis()));
        statement.execute("insert into timestamptest values(null, '"
                + _timestamp + "')");

        //Totally reset of the connection to be sure everything is clean
        statement.close();
        connection.close();
        before();
    }

    @Test
    public void testGetTimestampWhenDaylightSavingRemovesHour() throws SQLException {
        Calendar _0015 = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        _0015.clear();
        _0015.set(2014, 2, 30, 0, 15, 0);
        String _0015String = utcDateFormatISO8601.format(new Date(_0015.getTimeInMillis()));

        insertTimestampInWithUTCSessionTimeZone(_0015);

        Statement statement = connection.createStatement();
        setSessionTimeZone(connection, istanbulTimeZone, _0015);

        //Verify with ResultSet.getTimestamp() that it is correct
        ResultSet rs = statement.executeQuery("select * from timestamptest");

        assertTrue(rs.next());
        Timestamp timestamp = rs.getTimestamp("tm");
        String _timestampString = utcDateFormatISO8601.format(timestamp);
        assertEquals(_0015String, _timestampString);
    }

    @Test
    public void testGetTimestampWithoutDaylightSavingIssue() throws SQLException {
        Calendar _0115 = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        _0115.clear();
        _0115.set(2014, 2, 30, 1, 15, 0);
        String _0115String = utcDateFormatISO8601.format(new Date(_0115.getTimeInMillis()));

        insertTimestampInWithUTCSessionTimeZone(_0115);

        Statement statement = connection.createStatement();
        setSessionTimeZone(connection, istanbulTimeZone, _0115);

        //Verify with ResultSet.getTimestamp() that it is correct
        ResultSet rs = statement.executeQuery("select * from timestamptest");

        assertTrue(rs.next());
        Timestamp timestamp = rs.getTimestamp("tm");
        assertEquals(_0115String, utcDateFormatISO8601.format(timestamp));
    }

    /**
     * Return the session timezone in a "+02:00" or "-05:00" format.
     */
    private String getSessionTimeZone() throws SQLException {
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(
                "select CONVERT(timediff(now(),convert_tz(now(),@@session.time_zone,'+00:00')), CHAR)");

        resultSet.next();

        String timeZoneString = resultSet.getString(1);

        timeZoneString = timeZoneString.replaceAll(":00$", "");

        if (!timeZoneString.startsWith("-")) {
            timeZoneString = "+" + timeZoneString;
        }
        statement.close();

        return timeZoneString;
    }

    private void setSessionTimeZone(Connection connection, String timeZone) throws SQLException {
        Statement statement = connection.createStatement();
        statement.execute("set @@session.time_zone = '" + timeZone + "'");
        statement.close();
    }

    private void setSessionTimeZone(Connection connection, TimeZone timeZone, Calendar cal) throws SQLException {
        //int offsetInMs = timeZone.getOffset(System.currentTimeMillis());
        int offsetInMs = timeZone.getOffset(cal.get(Calendar.ERA), cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.DAY_OF_WEEK), cal.get(Calendar.MILLISECOND));
        int offsetInHours = offsetInMs / 3600 / 1000;

        boolean leadingZero = false;
        if (Math.abs(offsetInHours) < 10) {
            //Add a leading 0
            leadingZero = true;
        }

        boolean positive = true;
        if (offsetInHours < 0) {
            positive = false;
        }

        String offsetString = (positive ? "+" : "-") +
                (leadingZero ? "0" : "") +
                Math.abs(offsetInHours) +
                ":00";

        setSessionTimeZone(connection, offsetString);
    }


    @Test
    public void testTimeStamp() throws SQLException {
        TimeZone.setDefault(parisTimeZone);
        setSessionTimeZone(connection, "Europe/Paris");
        Timestamp currentTimeParis = new Timestamp(System.currentTimeMillis()); //timestamp timezone to parisTimeZone like server
        PreparedStatement st = connection.prepareStatement("SELECT ?");
        st.setTimestamp(1, currentTimeParis);
        ResultSet rs = st.executeQuery();
        rs.next();
        assertEquals(rs.getTimestamp(1), currentTimeParis);
    }

    @Test
    public void testTimeStampUTC() throws SQLException {
        TimeZone.setDefault(parisTimeZone);
        setSessionTimeZone(connection, "+00:00");
        Timestamp currentTimeParis = new Timestamp(System.currentTimeMillis()); //timestamp timezone to parisTimeZone like server
        PreparedStatement st = connection.prepareStatement("SELECT ?");
        st.setTimestamp(1, currentTimeParis);
        ResultSet rs = st.executeQuery();
        rs.next();
        assertEquals(rs.getTimestamp(1), currentTimeParis);
    }

    @Test
    public void testTimeStampUTCNow() throws SQLException {
        TimeZone.setDefault(parisTimeZone);
        setSessionTimeZone(connection, "+00:00");
        Timestamp currentTimeParis = new Timestamp(System.currentTimeMillis()); //timestamp timezone to parisTimeZone like server
        PreparedStatement st = connection.prepareStatement("SELECT NOW()");
        ResultSet rs = st.executeQuery();
        rs.next();
        int offset = parisTimeZone.getOffset(System.currentTimeMillis());
        long timeDifference = currentTimeParis.getTime() - offset - rs.getTimestamp(1).getTime();

        assertTrue(timeDifference < 1000); // must have less than one second difference
    }


    @Test
    public void testTimeStampOffsetNowUseServer() throws SQLException {
        TimeZone.setDefault(parisTimeZone);
        setConnection("&serverTimezone=Europe/Paris");
        Timestamp currentTimeParis = new Timestamp(System.currentTimeMillis()); //timestamp timezone to parisTimeZone like server
        PreparedStatement st = connection.prepareStatement("SELECT NOW()");
        ResultSet rs = st.executeQuery();
        rs.next();
        int offset = parisTimeZone.getOffset(System.currentTimeMillis());
        long timeDifference = currentTimeParis.getTime() - offset - rs.getTimestamp(1).getTime();
        assertTrue(timeDifference < 1000); // must have less than one second difference
    }

    @Test
    public void testDayLight() throws SQLException {
        TimeZone.setDefault(parisTimeZone);
        setConnection("&serverTimezone=Europe/Paris");
        Statement st = connection.createStatement();
        st.executeQuery("DROP TABLE IF EXISTS daylight");
        st.executeQuery("CREATE TABLE daylight(id int, tt TIMESTAMP(6))");

        Calendar quarterBeforeChangingHour = Calendar.getInstance(TimeZone.getTimeZone("utc"));
        quarterBeforeChangingHour.clear();
        quarterBeforeChangingHour.set(2015, 2, 29, 0, 45, 0);
        int offsetBefore = parisTimeZone.getOffset(quarterBeforeChangingHour.getTimeInMillis());
        Assert.assertEquals(offsetBefore, 3600000);

        Calendar quarterAfterChangingHour = Calendar.getInstance(TimeZone.getTimeZone("utc"));
        quarterAfterChangingHour.clear();
        quarterAfterChangingHour.set(2015, 2, 29, 1, 15, 0);
        int offsetAfter = parisTimeZone.getOffset(quarterAfterChangingHour.getTimeInMillis());
        Assert.assertEquals(offsetAfter, 7200000);


        PreparedStatement pst = connection.prepareStatement("INSERT INTO daylight VALUES (?, ?)");
        pst.setInt(1, 1);
        pst.setTimestamp(2, new Timestamp(quarterBeforeChangingHour.getTimeInMillis()));
        pst.addBatch();
        pst.setInt(1, 2);
        pst.setTimestamp(2, new Timestamp(quarterAfterChangingHour.getTimeInMillis()));
        pst.addBatch();
        pst.setInt(1, 3);
        pst.setString(2, "2015-03-29 02:15:00");
        pst.addBatch();
        try {
            pst.executeBatch();
        } catch (SQLException e) {
            assertTrue(e.getMessage().startsWith("Incorrect datetime value"));
        }

        ResultSet rs = st.executeQuery("SELECT * from daylight");
        rs.next();
        assertEquals(rs.getTimestamp(2).getTime(), quarterBeforeChangingHour.getTimeInMillis());
        rs.next();
        assertEquals(rs.getTimestamp(2).getTime(), quarterAfterChangingHour.getTimeInMillis());
        assertFalse(rs.next());
    }

    @Test
    public void testDayLightWithClientTimeZoneDifferent() throws SQLException {
        TimeZone.setDefault(parisTimeZone);
        setConnection("&serverTimezone=UTC");
        Statement st = connection.createStatement();
        st.executeQuery("DROP TABLE IF EXISTS daylight");
        st.executeQuery("CREATE TABLE daylight(id int, tt TIMESTAMP(6))");

        Calendar quarterBeforeChangingHour = Calendar.getInstance(TimeZone.getTimeZone("utc"));
        quarterBeforeChangingHour.clear();
        quarterBeforeChangingHour.set(2015, 2, 29, 0, 45, 0);
        int offsetBefore = parisTimeZone.getOffset(quarterBeforeChangingHour.getTimeInMillis());
        Assert.assertEquals(offsetBefore, 3600000);

        Calendar quarterAfterChangingHour = Calendar.getInstance(TimeZone.getTimeZone("utc"));
        quarterAfterChangingHour.clear();
        quarterAfterChangingHour.set(2015, 2, 29, 1, 15, 0);
        int offsetAfter = parisTimeZone.getOffset(quarterAfterChangingHour.getTimeInMillis());
        Assert.assertEquals(offsetAfter, 7200000);

        PreparedStatement pst = connection.prepareStatement("INSERT INTO daylight VALUES (?, ?)");
        pst.setInt(1, 1);
        pst.setTimestamp(2, new Timestamp(quarterBeforeChangingHour.getTimeInMillis()));
        pst.addBatch();
        pst.setInt(1, 2);
        pst.setTimestamp(2, new Timestamp(quarterAfterChangingHour.getTimeInMillis()));
        pst.addBatch();
        pst.executeBatch();

        //test with text protocol
        ResultSet rs = st.executeQuery("SELECT * from daylight");
        rs.next();
        Timestamp tBefore = rs.getTimestamp(2);
        assertEquals(tBefore.toString(), "2015-03-29 01:45:00.0");
        rs.next();
        Timestamp tAfter = rs.getTimestamp(2);
        assertEquals(tAfter.toString(), "2015-03-29 03:15:00.0");

        //test with binary protocol
        pst = connection.prepareStatement("SELECT * from daylight where id = ?");
        pst.setInt(1, 1);
        pst.addBatch();
        rs = pst.executeQuery();
        rs.next();
        tBefore = rs.getTimestamp(2);
        assertEquals(tBefore.toString(), "2015-03-29 01:45:00.0");

        pst.setInt(1, 2);
        pst.addBatch();
        rs = pst.executeQuery();
        rs.next();
        tAfter = rs.getTimestamp(2);
        assertEquals(tAfter.toString(), "2015-03-29 03:15:00.0");
    }


}