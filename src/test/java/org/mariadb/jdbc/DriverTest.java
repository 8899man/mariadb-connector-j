package org.mariadb.jdbc;

import org.junit.Assert;
import org.junit.Test;
import org.mariadb.jdbc.internal.common.RewriteParameterizedBatchHandlerFactory;
import org.mariadb.jdbc.internal.common.packet.RawPacket;
import org.mariadb.jdbc.internal.common.packet.buffer.WriteBuffer;

import java.io.*;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static junit.framework.Assert.*;
import static org.junit.Assert.assertFalse;


public class DriverTest extends BaseTest{
    static { Logger.getLogger("").setLevel(Level.OFF); }

    public DriverTest() throws SQLException {
       
    }

    @Test
    public void doQuery() throws SQLException{
        Statement stmt = connection.createStatement();
        try { stmt.execute("drop table t1"); } catch (Exception e) {}
        stmt.execute("create table t1 (id int not null primary key auto_increment, test varchar(20))");
        stmt.execute("insert into t1 (test) values ('hej1')");
        stmt.execute("insert into t1 (test) values ('hej2')");
        stmt.execute("insert into t1 (test) values ('hej3')");
        stmt.execute("insert into t1 (test) values (null)");
        ResultSet rs = stmt.executeQuery("select * from t1");
        for(int i=1;i<4;i++) {
            rs.next();
            assertEquals(String.valueOf(i),rs.getString(1));
            assertEquals("hej"+i,rs.getString("test"));
        }
        rs.next();
        assertEquals(null,rs.getString("test"));
    }
    @Test(expected = SQLException.class)
    public void askForBadColumnTest() throws SQLException{
        Statement stmt = connection.createStatement();
        try { stmt.execute("drop table t1"); } catch (Exception e) {}
        stmt.execute("create table t1 (id int not null primary key auto_increment, test varchar(20))");
        stmt.execute("insert into t1 (test) values ('hej1')");
        stmt.execute("insert into t1 (test) values ('hej2')");
        stmt.execute("insert into t1 (test) values ('hej3')");
        stmt.execute("insert into t1 (test) values (null)");
        ResultSet rs = stmt.executeQuery("select * from t1");
        rs.next();
        rs.getInt("non_existing_column");
    }
    @Test(expected = SQLException.class)
    public void askForBadColumnIndexTest() throws SQLException{

        Statement stmt = connection.createStatement();
        try { stmt.execute("drop table t1"); } catch (Exception e) {}
        stmt.execute("create table t1 (id int not null primary key auto_increment, test varchar(20))");
        stmt.execute("insert into t1 (test) values ('hej1')");
        stmt.execute("insert into t1 (test) values ('hej2')");
        stmt.execute("insert into t1 (test) values ('hej3')");
        stmt.execute("insert into t1 (test) values (null)");
        ResultSet rs = stmt.executeQuery("select * from t1");
        rs.next();
        rs.getInt(102);
    }

    @Test
    /* Accessing result set using  table.column */
    public void tableDotColumnInResultSet() throws SQLException {
        Statement stmt = connection.createStatement();
        try {
            stmt.execute("drop table tt1");
        } catch (Exception e) {}
        try {
            stmt.execute("drop table tt2");
        } catch (Exception e) {}
        stmt.execute("create table tt1 (id int , name varchar(20))");
        stmt.execute("create table tt2 (id int , name varchar(20))");
        stmt.execute("insert into tt1 values(1, 'one')");
        stmt.execute("insert into tt2 values(1, 'two')");
        ResultSet rs = stmt.executeQuery("select tt1.*, tt2.* from tt1, tt2 where tt1.id = tt2.id");
        rs.next();
        Assert.assertEquals(1, rs.getInt("tt1.id"));
        Assert.assertEquals(1, rs.getInt("tt2.id"));
        Assert.assertEquals("one", rs.getString("tt1.name"));
        Assert.assertEquals("two", rs.getString("tt2.name"));
    }
    @Test(expected = SQLException.class)
    public void badQuery() throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.executeQuery("whraoaooa");
    }

    @Test
    public void intOperations() {
        byte [] a = WriteBuffer.intToByteArray(56*256*256*256 + 11*256*256 + 77*256 + 99);
        assertEquals(a[0],99);
        assertEquals(a[1],77);
        assertEquals(a[2],11);
        assertEquals(a[3],56);
    }
    @Test
    public void preparedTest() throws SQLException {
        String query = "SELECT * FROM t1 WHERE test = ? and id = ?";
        PreparedStatement prepStmt = connection.prepareStatement(query);
        prepStmt.setString(1,"hej1");
        prepStmt.setInt(2,1);
        ResultSet results = prepStmt.executeQuery();
        String res = "";
        while(results.next()) {
            res=results.getString("test");
        }
        assertEquals("hej1",res);
        assertEquals(2, prepStmt.getParameterMetaData().getParameterCount());
    }

   
    @Test
    public void preparedTest2() throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.execute("DROP TABLE IF EXISTS prep_test");
        stmt.execute("CREATE TABLE prep_test (id int not null primary key auto_increment, test varchar(20)) engine=innodb");
        PreparedStatement prepStmt = connection.prepareStatement("insert into prep_test (test) values (?) ");
        for(int i=0;i<1000;i++) {
            prepStmt.setString(1,"mee");
            prepStmt.execute();
        }
    }

    @Test
    public void updateTest() throws SQLException {
        Statement stmt = connection.createStatement();
        try { stmt.execute("drop table t1"); } catch (Exception e) {}
        stmt.execute("create table t1 (id int not null primary key auto_increment, test varchar(20))");
        stmt.execute("insert into t1 (test) values ('hej1')");
        stmt.execute("insert into t1 (test) values ('hej2')");
        stmt.execute("insert into t1 (test) values ('hej3')");
        stmt.execute("insert into t1 (test) values (null)");

        String query = "UPDATE t1 SET test = ? where id = ?";
        PreparedStatement prepStmt = connection.prepareStatement(query);
        prepStmt.setString(1,"updated");
        prepStmt.setInt(2,3);
        int updateCount = prepStmt.executeUpdate();
        assertEquals(1,updateCount);
        String query2 = "SELECT * FROM t1 WHERE id=?";
        prepStmt =connection.prepareStatement(query2);
        prepStmt.setInt(1,3);
        ResultSet results = prepStmt.executeQuery();
        String result = "";
        while(results.next()) {
            result = results.getString("test");
        }
        assertEquals("updated",result);
    }
    
    @Test
    public void ralfTest() throws SQLException {
    	PreparedStatement ps = 
    			connection.prepareStatement(
    					"select serverrequ0_.id as id75_, serverrequ0_.date_time as date2_75_, serverrequ0_.name as name75_, serverrequ0_.server_request_type as server4_75_, serverrequ0_.response_code_storage_tag as response5_75_, serverrequ0_.storage_tag as storage6_75_, serverrequ0_.timeout as "+
    					" timeout75_, serverrequ0_.url as url75_ from ussd_server_request_tag serverrequ0_ where serverrequ0_.name='ß' limit ?"); 
    							
    							
    }
    @Test
    public void autoIncTest() throws SQLException {
        String query = "CREATE TABLE t2 (id int not null primary key auto_increment, test varchar(10))";
        Statement stmt = connection.createStatement();
        stmt.execute("DROP TABLE IF EXISTS t2");
        stmt.execute(query);
        stmt.execute("INSERT INTO t2 (test) VALUES ('aa')");
        ResultSet rs = stmt.getGeneratedKeys();
        if(rs.next()) {
            assertEquals(1,rs.getInt(1));
            assertEquals(1,rs.getInt("insert_id"));
        } else {
            throw new SQLException("Could not get generated keys");
        }
        stmt.execute("INSERT INTO t2 (test) VALUES ('aa')");
        rs = stmt.getGeneratedKeys();
        if(rs.next()) {
            assertEquals(2,rs.getInt(1));
            assertEquals(2,rs.getInt("insert_id"));
        } else {
            throw new SQLException("Could not get generated keys");
        }

    }
    @Test
    public void autoIncPrepStmtTest() throws SQLException {
        String query = "CREATE TABLE test_a_inc_prep_stmt (id int not null primary key auto_increment, test varchar(10))";
        Statement stmt = connection.createStatement();
        stmt.execute("DROP TABLE IF EXISTS test_a_inc_prep_stmt");
        stmt.execute(query);
        PreparedStatement ps = connection.prepareStatement("insert into test_a_inc_prep_stmt (test) values (?)");
        ps.setString(1,"test123");
        ps.execute();
        ResultSet rs = ps.getGeneratedKeys();
        assertTrue(rs.next());
        assertEquals(1,rs.getInt(1));
        assertEquals(1,rs.getInt("insert_id"));
    }    
    @Test
    public void transactionTest() throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.execute("DROP TABLE IF EXISTS t3");
        stmt.execute("CREATE TABLE t3 (id int not null primary key auto_increment, test varchar(20)) engine=innodb");
        connection.setAutoCommit(false);
        stmt.executeUpdate("INSERT INTO t3 (test) VALUES ('heja')");
        stmt.executeUpdate("INSERT INTO t3 (test) VALUES ('japp')");
        connection.commit();
        ResultSet rs = stmt.executeQuery("SELECT * FROM t3");
        assertEquals(true,rs.next());
        assertEquals("heja",rs.getString("test"));
        assertEquals(true,rs.next());
        assertEquals("japp",rs.getString("test"));
        assertEquals(false, rs.next());
        stmt.executeUpdate("INSERT INTO t3 (test) VALUES ('rollmeback')");
        ResultSet rsGen = stmt.getGeneratedKeys();
        rsGen.next();
        assertEquals(3,rsGen.getInt(1));
        connection.rollback();
        rs = stmt.executeQuery("SELECT * FROM t3 WHERE id=3");
        assertEquals(false,rs.next());
    }
    @Test
    public void savepointTest() throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.executeUpdate("drop table if exists t4");
        stmt.executeUpdate("create table t4 (id int not null primary key auto_increment, test varchar(20)) engine=innodb");
        connection.setAutoCommit(false);
        stmt.executeUpdate("INSERT INTO t4 (test) values('hej1')");
        stmt.executeUpdate("INSERT INTO t4 (test) values('hej2')");
        Savepoint savepoint = connection.setSavepoint("yep");
        stmt.executeUpdate("INSERT INTO t4 (test)  values('hej3')");
        stmt.executeUpdate("INSERT INTO t4 (test) values('hej4')");
        connection.rollback(savepoint);
        stmt.executeUpdate("INSERT INTO t4 (test) values('hej5')");
        stmt.executeUpdate("INSERT INTO t4 (test) values('hej6')");
        connection.commit();
        ResultSet rs = stmt.executeQuery("SELECT * FROM t4");
        assertEquals(true, rs.next());
        assertEquals("hej1",rs.getString(2));
        assertEquals(true, rs.next());
        assertEquals("hej2",rs.getString(2));
        assertEquals(true, rs.next());
        assertEquals("hej5",rs.getString(2));
        assertEquals(true, rs.next());
        assertEquals("hej6",rs.getString(2));
        assertEquals(false,rs.next());
    }
    @Test
    public void isolationLevel() throws SQLException {
        connection.setTransactionIsolation(connection.TRANSACTION_READ_UNCOMMITTED);
        assertEquals(connection.TRANSACTION_READ_UNCOMMITTED,connection.getTransactionIsolation());
        connection.setTransactionIsolation(connection.TRANSACTION_READ_COMMITTED);
        assertEquals(connection.TRANSACTION_READ_COMMITTED,connection.getTransactionIsolation());
        connection.setTransactionIsolation(connection.TRANSACTION_SERIALIZABLE);
        assertEquals(connection.TRANSACTION_SERIALIZABLE,connection.getTransactionIsolation());
        connection.setTransactionIsolation(connection.TRANSACTION_REPEATABLE_READ);
        assertEquals(connection.TRANSACTION_REPEATABLE_READ,connection.getTransactionIsolation());
    }

    @Test
    public void isValidTest() throws SQLException {
        assertEquals(true,connection.isValid(0));
    }



    @Test
    public void testConnectNoDB() throws Exception{
        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306?user=root");
        connection.close();
    }

    @Test
    public void testConnectorJURL() {
        JDBCUrl url = JDBCUrl.parse("jdbc:mysql://localhost/test");
        assertEquals("localhost", url.getHostname());
        assertEquals("test", url.getDatabase());
        assertEquals(3306,url.getPort());

        url = JDBCUrl.parse("jdbc:mysql://localhost:3307/test");
        assertEquals("localhost", url.getHostname());
        assertEquals("test", url.getDatabase());
        assertEquals(3307,url.getPort());

    }

    @Test
    public void testEscapes() throws SQLException {
        String query = "select * from t1 where test = ?";
        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1,"hej\"");
        ResultSet rs = stmt.executeQuery();
        assertEquals(false,rs.next());
    }

    @Test
    public void testPreparedWithNull() throws SQLException {
        String query = "insert into t1 (test) values (null)";
        PreparedStatement pstmt = connection.prepareStatement(query);
        pstmt.execute();
        query = "select * from t1 where test is ?";
        pstmt = connection.prepareStatement(query);
        pstmt.setNull(1,1);
        ResultSet rs = pstmt.executeQuery();
        assertEquals(true,rs.next());
        assertEquals(null,rs.getString("test"));
        assertEquals(true,rs.wasNull());
    }

    @Test
    public void connectFailover() throws SQLException {
        Connection c  = DriverManager.getConnection("jdbc:mysql://localhost:3306,localhost:3307/test?user=root");
        MySQLConnection my=(MySQLConnection) c;
        System.out.println(""+my.getPort());
        ResultSet rs = c.createStatement().executeQuery("select 1");
        rs.next();
        assertEquals(rs.getInt(1), 1);
    }
    @Test
    public void batchTest() throws SQLException {
        connection.createStatement().execute("drop table if exists test_batch");
        connection.createStatement().execute("create table test_batch (id int not null primary key auto_increment, test varchar(10))");
        PreparedStatement ps = connection.prepareStatement("insert into test_batch values (null, ?)");
        ps.setString(1, "aaa");
        ps.addBatch();
        ps.setString(1, "bbb");
        ps.addBatch();
        ps.setString(1, "ccc");
        ps.addBatch();
        int [] a = ps.executeBatch();
        for(int c : a ) assertEquals(1,c);
        ps.setString(1, "aaa");
        ps.addBatch();
        ps.setString(1, "bbb");
        ps.addBatch();
        ps.setString(1, "ccc");
        ps.addBatch();
         a = ps.executeBatch();
        for(int c : a ) assertEquals(1,c);
        ResultSet rs = connection.createStatement().executeQuery("select * from test_batch");
        assertEquals(true,rs.next());
        assertEquals("aaa",rs.getString(2));
        assertEquals(true,rs.next());
        assertEquals("bbb",rs.getString(2));
        assertEquals(true,rs.next());
        assertEquals("ccc",rs.getString(2));

    }
    @Test
    public void batchTestStmt() throws SQLException {
        connection.createStatement().execute("drop table if exists test_batch2");
        connection.createStatement().execute("create table test_batch2 (id int not null primary key auto_increment, test varchar(10))");
        Statement stmt = connection.createStatement();
        stmt.addBatch("insert into test_batch2 values (null, 'hej1')");
        stmt.addBatch("insert into test_batch2 values (null, 'hej2')");
        stmt.addBatch("insert into test_batch2 values (null, 'hej3')");
        stmt.addBatch("insert into test_batch2 values (null, 'hej4')");
        stmt.executeBatch();
        ResultSet rs = connection.createStatement().executeQuery("select * from test_batch2");
        for(int i=1;i<=4;i++) {
            assertEquals(true,rs.next());
            assertEquals(i, rs.getInt(1));
            assertEquals("hej"+i,rs.getString(2));
        }
        assertEquals(false,rs.next());

    }

    @Test
    public void floatingNumbersTest() throws SQLException {
        connection.createStatement().execute("drop table if exists test_float");
        connection.createStatement().execute("create table test_float (id int not null primary key auto_increment, a float )");

        PreparedStatement ps = connection.prepareStatement("insert into test_float (a) values (?)");
        ps.setDouble(1,3.99);
        ps.executeUpdate();
        ResultSet rs = connection.createStatement().executeQuery("select a from test_float");
        assertEquals(true,rs.next());
        assertEquals((float)3.99, rs.getFloat(1));
        assertEquals((float)3.99, rs.getFloat("a"));
        assertEquals(false,rs.next());
    }


    @Test
    public void manyColumnsTest() throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.execute("drop table if exists test_many_columns");
        String query = "create table test_many_columns (a0 int primary key not null";
        for(int i=1;i<1000;i++) {
            query+=",a"+i+" int";
        }
        query+=")";
        stmt.execute(query);
        query="insert into test_many_columns values (0";
        for(int i=1;i<1000;i++) {
            query+=","+i;
        }
        query+=")";
        stmt.execute(query);
        ResultSet rs = stmt.executeQuery("select * from test_many_columns");

        assertEquals(true,rs.next());
       
        for(int i=0;i<1000;i++) {
            assertEquals(rs.getInt("a"+i),i);
        }

    }

    @Test
    public void bigAutoIncTest() throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.execute("drop table if exists test_big_autoinc2");
        stmt.execute("create table test_big_autoinc2 (id int not null primary key auto_increment, test varchar(10))");
        stmt.execute("alter table test_big_autoinc2 auto_increment = 1000");
        stmt.execute("insert into test_big_autoinc2 values (null, 'hej')");
        ResultSet rsGen = stmt.getGeneratedKeys();
        assertEquals(true,rsGen.next());
        assertEquals(1000,rsGen.getInt(1));
        stmt.execute("alter table test_big_autoinc2 auto_increment = "+Short.MAX_VALUE);
        stmt.execute("insert into test_big_autoinc2 values (null, 'hej')");
        rsGen = stmt.getGeneratedKeys();
        assertEquals(true,rsGen.next());
        assertEquals(Short.MAX_VALUE,rsGen.getInt(1));
        stmt.execute("alter table test_big_autoinc2 auto_increment = "+Integer.MAX_VALUE);
        stmt.execute("insert into test_big_autoinc2 values (null, 'hej')");
        rsGen = stmt.getGeneratedKeys();
        assertEquals(true,rsGen.next());
        assertEquals(Integer.MAX_VALUE,rsGen.getInt(1));
    }

    @Test
    public void bigUpdateCountTest() throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.execute("drop table if exists test_big_update");
        stmt.execute("create table test_big_update (id int primary key not null, updateme int)");
        for(int i=0;i<4000;i++) {
            stmt.execute("insert into test_big_update values ("+i+","+i+")");
        }
        ResultSet rs = stmt.executeQuery("select count(*) from test_big_update");
        assertEquals(true,rs.next());
        assertEquals(4000,rs.getInt(1));
        int updateCount = stmt.executeUpdate("update test_big_update set updateme=updateme+1");
        assertEquals(4000,updateCount);
    }

    //@Test
    public void testBinlogDumping() throws SQLException {
        assertEquals(true, connection.isWrapperFor(ReplicationConnection.class));

        ReplicationConnection rc = connection.unwrap(ReplicationConnection.class);
        List<RawPacket> rpList = rc.startBinlogDump(891,"mysqld-bin.000001");
        for(RawPacket rp : rpList) {
            for(byte b:rp.getByteBuffer().array()) {
                System.out.printf("%x ",b);
            }
            System.out.printf("\n");
        }
    }
    
    @Test
    public void testCharacterStreams() throws SQLException, IOException {
        connection.createStatement().execute("drop table if exists streamtest");
        connection.createStatement().execute("create table streamtest (id int not null primary key, strm text)");
        PreparedStatement stmt = connection.prepareStatement("insert into streamtest (id, strm) values (?,?)");
        stmt.setInt(1,2);
        String toInsert = "abcdefgh\njklmn\"";
        Reader reader = new StringReader(toInsert);
        stmt.setCharacterStream(2, reader);
        stmt.execute();
        ResultSet rs = connection.createStatement().executeQuery("select * from streamtest");
        rs.next();
        Reader rdr = rs.getCharacterStream("strm");
        StringBuilder sb = new StringBuilder();
        int ch;
        while((ch = rdr.read()) != -1) {
            sb.append((char)ch);
        }
        assertEquals(sb.toString(),(toInsert));
        rdr = rs.getCharacterStream(2);
        sb = new StringBuilder();
        
        while((ch = rdr.read()) != -1) {
            sb.append((char)ch);
        }
        assertEquals(sb.toString(),(toInsert));
        InputStream is = rs.getAsciiStream("strm");
        sb = new StringBuilder();

        while((ch = is.read()) != -1) {
            sb.append((char)ch);
        }
        assertEquals(sb.toString(),(toInsert));
        is = rs.getUnicodeStream("strm");
        sb = new StringBuilder();

        while((ch = is.read()) != -1) {
            sb.append((char)ch);
        }
        assertEquals(sb.toString(),(toInsert));
    }
    @Test
    public void testCharacterStreamWithLength() throws SQLException, IOException {
        connection.createStatement().execute("drop table if exists streamtest2");
        connection.createStatement().execute("create table streamtest2 (id int primary key not null, strm text)");
        PreparedStatement stmt = connection.prepareStatement("insert into streamtest2 (id, strm) values (?,?)");
        stmt.setInt(1,2);
        String toInsert = "abcdefgh\njklmn\"";
        Reader reader = new StringReader(toInsert);
        stmt.setCharacterStream(2, reader, 5);
        stmt.execute();
        ResultSet rs = connection.createStatement().executeQuery("select * from streamtest2");
        rs.next();
        Reader rdr = rs.getCharacterStream("strm");
        StringBuilder sb = new StringBuilder();
        int ch;
        while((ch = rdr.read()) != -1) {
            sb.append((char)ch);
        }
        assertEquals(sb.toString(),toInsert.substring(0,5));
    }

    @Test
    public void testBlob() throws SQLException, IOException {
        connection.createStatement().execute("drop table if exists blobtest");
        connection.createStatement().execute("create table blobtest (id int not null primary key, strm blob)");
        PreparedStatement stmt = connection.prepareStatement("insert into blobtest (id, strm) values (?,?)");
        byte [] theBlob = {1,2,3,4,5,6};
        InputStream stream = new ByteArrayInputStream(theBlob);
        stmt.setInt(1,1);
        stmt.setBlob(2,stream);
        stmt.execute();
        ResultSet rs = connection.createStatement().executeQuery("select * from blobtest");
        rs.next();
        InputStream readStuff = rs.getBlob("strm").getBinaryStream();
        int ch;
        int pos=0;
        while((ch = readStuff.read())!=-1) {
            assertEquals(theBlob[pos++],ch);
        }

        readStuff = rs.getBinaryStream("strm");

        pos=0;
        while((ch = readStuff.read())!=-1) {
            assertEquals(theBlob[pos++],ch);
        }
    }
   @Test
    public void testBlobWithLength() throws SQLException, IOException {
        connection.createStatement().execute("drop table if exists blobtest");
        connection.createStatement().execute("create table blobtest (id int not null primary key, strm blob)");
        PreparedStatement stmt = connection.prepareStatement("insert into blobtest (id, strm) values (?,?)");
        byte [] theBlob = {1,2,3,4,5,6};
        InputStream stream = new ByteArrayInputStream(theBlob);
        stmt.setInt(1,1);
        stmt.setBlob(2,stream,4);
        stmt.execute();
        ResultSet rs = connection.createStatement().executeQuery("select * from blobtest");
        rs.next();
        InputStream readStuff = rs.getBlob("strm").getBinaryStream();
        int ch;
        int pos=0;
        while((ch = readStuff.read())!=-1) {
            assertEquals(theBlob[pos++],ch);
        }
    }
    @Test
    public void testClobWithLength() throws SQLException, IOException {
        connection.createStatement().execute("drop table if exists clobtest");
        connection.createStatement().execute("create table clobtest (id int not null primary key, strm text)");
        PreparedStatement stmt = connection.prepareStatement("insert into clobtest (id, strm) values (?,?)");
        String clob = "clob";
        stmt.setInt(1,1);
        stmt.setClob(2, new StringReader(clob));
        stmt.execute();
        ResultSet rs = connection.createStatement().executeQuery("select * from clobtest");
        rs.next();
        Reader readStuff = rs.getClob("strm").getCharacterStream();
        int ch;
        int pos=0;
        char[] a = new char[4];
        readStuff.read(a);
        Assert.assertEquals(new String(a), clob);
    }

    @Test
    public void  testClob2() throws SQLException, IOException {
        connection.createStatement().execute("drop table if exists clobtest");
        connection.createStatement().execute("create table clobtest (id int not null primary key, strm text)");
        PreparedStatement stmt = connection.prepareStatement("insert into clobtest (id, strm) values (?,?)");
        Clob clob = connection.createClob();
        OutputStream ostream = clob.setAsciiStream(1);
        byte[] bytes = "hello".getBytes();
        ostream.write(bytes);
        stmt.setInt(1,1);
        stmt.setClob(2,clob);
        stmt.execute();
        ResultSet rs = connection.createStatement().executeQuery("select * from clobtest");
        rs.next();
        Object o = rs.getObject(2);
        assertTrue(o instanceof String);
        String s = rs.getString(2);
        assertTrue(s.equals("hello"));
    }
    @Test
    public void testEmptyResultSet() throws SQLException {
        connection.createStatement().execute("drop table if exists emptytest");
        connection.createStatement().execute("create table emptytest (id int)");
        Statement stmt = connection.createStatement();
        assertEquals(true,stmt.execute("SELECT * FROM emptytest"));
        assertEquals(false,stmt.getResultSet().next());
    }
    @Test
    public void testLongColName() throws SQLException {
        connection.createStatement().execute("drop table if exists longcol");
        DatabaseMetaData dbmd = connection.getMetaData();
        String str="";
        for(int i =0;i<dbmd.getMaxColumnNameLength();i++) {
            str+="x";   
        }
        connection.createStatement().execute("create table longcol ("+str+" int not null primary key)");
        connection.createStatement().execute("insert into longcol values (1)");
        ResultSet rs = connection.createStatement().executeQuery("select * from longcol");
        assertEquals(true,rs.next());
        assertEquals(1, rs.getInt(1));
        assertEquals(1,rs.getInt(str));
    }

    @Test(expected = SQLException.class)
    public void testBadParamlist() throws SQLException {
        PreparedStatement ps = null;
        ps = connection.prepareStatement("insert into blah values (?)");
        ps.execute();
    }

    @Test
    public void setobjectTest() throws SQLException, IOException, ClassNotFoundException {
        connection.createStatement().execute("drop table if exists objecttest");
        connection.createStatement().execute(
                "create table objecttest (int_test int primary key not null, string_test varchar(30), timestamp_test timestamp, serial_test blob)");
        PreparedStatement ps = connection.prepareStatement("insert into objecttest values (?,?,?,?)");
        ps.setObject(1, 5);
        ps.setObject(2, "aaa");
        ps.setObject(3, Timestamp.valueOf("2009-01-17 15:41:01"));
        ps.setObject(4, new SerializableClass("testing",8));
        ps.execute();

        ResultSet rs = connection.createStatement().executeQuery("select * from objecttest");
        assertEquals(true,rs.next());
        Object theInt = rs.getObject(1);
        assertTrue(theInt instanceof Integer);
        Object theInt2 = rs.getObject("int_test");
        assertTrue(theInt2 instanceof Integer);
        Object theString = rs.getObject(2);
        assertTrue(theString instanceof String);
        Object theTimestamp = rs.getObject(3);
        assertTrue(theTimestamp instanceof Timestamp);
        Object theBlob = rs.getObject(4);

        byte [] rawBytes = rs.getBytes(4);
        ByteArrayInputStream bais = new ByteArrayInputStream(rawBytes);
        ObjectInputStream ois = new ObjectInputStream(bais);
        SerializableClass sc = (SerializableClass)ois.readObject();

        assertEquals(sc.getVal(), "testing");
        assertEquals(sc.getVal2(), 8);
        rawBytes = rs.getBytes("serial_test");
        bais = new ByteArrayInputStream(rawBytes);
        ois = new ObjectInputStream(bais);
        sc = (SerializableClass)ois.readObject();

        assertEquals(sc.getVal(), "testing");
        assertEquals(sc.getVal2(), 8);
    }
    @Test
    public void binTest() throws SQLException, IOException {
        connection.createStatement().execute("drop table if exists bintest");
        connection.createStatement().execute(
                "create table bintest (id int not null primary key auto_increment, bin1 varbinary(300), bin2 varbinary(300))");
        byte [] allBytes = new byte[256];
        for(int i=0;i<256;i++) {
            allBytes[i]=(byte) (i&0xff);
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(allBytes);
        PreparedStatement ps = connection.prepareStatement("insert into bintest (bin1,bin2) values (?,?)");
        ps.setBytes(1,allBytes);
        ps.setBinaryStream(2, bais);
        ps.execute();

        ResultSet rs = connection.createStatement().executeQuery("select bin1,bin2 from bintest");
        assertTrue(rs.next());
        Blob blob = rs.getBlob(1);
        InputStream is = rs.getBinaryStream(1);

        for(int i=0;i<256;i++) {
            int read = is.read();
            assertEquals(i,read);
        }
        is = rs.getBinaryStream(2);

        for(int i=0;i<256;i++) {
            int read = is.read();
            assertEquals(i,read);
        }

    }

    /*
     Test with different APIs that generated keys work. Also test that any name in generatedKeys.getXXX(String name)
     can be passed and is equivalent to generatedKeys.getXXX(1). This might not be 100% compliant, but is a simple
     and effective solution for MySQL that does not does not support more than a single autogenerated value.
    */
    @Test
    public void generatedKeys() throws Exception {
      connection.createStatement().execute("drop table if exists genkeys");
      connection.createStatement().execute(
              "CREATE TABLE genkeys ("
                       + "priKey INT NOT NULL AUTO_INCREMENT, "
                       + "dataField VARCHAR(64), PRIMARY KEY (priKey))");

      Statement st = connection.createStatement();
      st.executeUpdate("insert into genkeys(dataField) values('a')", Statement.RETURN_GENERATED_KEYS);
      ResultSet rs = st.getGeneratedKeys();
      assertEquals(rs.getInt(1), 1);
      assertEquals(rs.getInt("priKey"), 1);
      assertEquals(rs.getInt("foo"),1);
      int indexes[] = {1,2,3};
      st.executeUpdate("insert into genkeys(dataField) values('b')", indexes);
      rs = st.getGeneratedKeys();
      assertEquals(rs.getInt(1), 2);
      try {
        assertEquals(rs.getInt(2), 2);
        assertFalse("should never get here", true);
      } catch(SQLException e) {
          // eat
      }

      String columnNames[] = {"priKey", "Alice", "Bob"};
      st.executeUpdate("insert into genkeys(dataField) values('c')", columnNames);
      rs = st.getGeneratedKeys();
      for(int i=0; i < 3; i++)  {
          assertEquals(rs.getInt(columnNames[i]), 3);
      }
    }

    @Test
    public void binTest2() throws SQLException, IOException {
        connection.createStatement().execute("drop table if exists bintest2");

        if(connection.getMetaData().getDatabaseProductName().toLowerCase().equals("mysql")) {
            connection.createStatement().execute(
                "create table bintest2 (bin1 longblob) engine=innodb");
        } else {
            connection.createStatement().execute(
                "create table bintest2 (id int not null primary key auto_increment, bin1 blob)");            
        }

        byte [] buf=new byte[1000000];
        for(int i=0;i<1000000;i++) {
            buf[i]=(byte)i;
        }
        InputStream is = new ByteArrayInputStream(buf);
        PreparedStatement ps = connection.prepareStatement("insert into bintest2 (bin1) values (?)");
        ps.setBinaryStream(1, is);
        ps.execute();
        ps = connection.prepareStatement("insert into bintest2 (bin1) values (?)");
        is = new ByteArrayInputStream(buf);
        ps.setBinaryStream(1, is);
        ps.execute();
        ResultSet rs = connection.createStatement().executeQuery("select bin1 from bintest2");
        assertEquals(true,rs.next());
        byte [] buf2 = rs.getBytes(1);
        for(int i=0;i<1000000;i++) {
            assertEquals((byte)i,buf2[i]);
        }

        assertEquals(true,rs.next());
        buf2 = rs.getBytes(1);
        for(int i=0;i<1000000;i++) {
            assertEquals((byte)i,buf2[i]);
        }
        assertEquals(false,rs.next());
    }
    @Test(expected=SQLIntegrityConstraintViolationException.class)
    public void testException1() throws SQLException {
        connection.createStatement().execute("drop table if exists extest");
        connection.createStatement().execute(
                "create table extest (id int not null primary key)");
        connection.createStatement().execute("insert into extest values (1)");
        connection.createStatement().execute("insert into extest values (1)");
    }

    @Test
    public void testExceptionDivByZero() throws SQLException {
        ResultSet rs = connection.createStatement().executeQuery("select 1/0");
        assertEquals(rs.next(),true);
        assertEquals(null, rs.getString(1));
    }
    @Test(expected = SQLSyntaxErrorException.class)
    public void testSyntaxError() throws SQLException {
        connection.createStatement().executeQuery("create asdf b");
    }

    @Test
    public void testRewriteBatchHandler() throws SQLException {
        connection.createStatement().execute("drop table if exists rewritetest");
        connection.createStatement().execute(
                "create table rewritetest (id int not null primary key, a varchar(10), b int) engine=innodb");

        if(connection.isWrapperFor(MySQLConnection.class)) {
            MySQLConnection dc = connection.unwrap(MySQLConnection.class);
            dc.setBatchQueryHandlerFactory(new RewriteParameterizedBatchHandlerFactory());
        }

        PreparedStatement ps = connection.prepareStatement("insert into rewritetest values (?,?,?)");
        for(int i = 0;i<10000;i++) {
            ps.setInt(1,i);
            ps.setString(2,"bbb"+i);
            ps.setInt(3,30+i);
            ps.addBatch();
        }
        ps.executeBatch();
        ResultSet rs = connection.createStatement().executeQuery("select * from rewritetest");
        int i = 0;
        while(rs.next()) {
            assertEquals(i++, rs.getInt("id"));
        }
        assertEquals(10000,i);
    }
    @Test
    public void testRewriteBatchHandlerWithDupKey() throws SQLException {
        connection.createStatement().execute("drop table if exists rewritetest2");
        connection.createStatement().execute(
                "create table rewritetest2 (id int not null primary key, a varchar(10), b int) engine=innodb");
                
        if(connection.isWrapperFor(MySQLConnection.class)) {
            MySQLConnection dc = connection.unwrap(MySQLConnection.class);
            dc.setBatchQueryHandlerFactory(new RewriteParameterizedBatchHandlerFactory());
        }

        long startTime = System.currentTimeMillis();
        PreparedStatement ps = connection.prepareStatement("insert into rewritetest2 values (?,?,?) on duplicate key update a=values(a)");
        for(int i = 0;i<10000;i++) {
            ps.setInt(1,0);
            ps.setString(2,"bbb"+i);
            ps.setInt(3,30+i);
            ps.addBatch();
        }
        ps.executeBatch();
        System.out.println("time: "+(System.currentTimeMillis() - startTime));
        ResultSet rs = connection.createStatement().executeQuery("select * from rewritetest2");
        int i = 0;
        while(rs.next()) {
            assertEquals(i++, rs.getInt("id"));
        }
        assertEquals(1,i);
    }

    @Test
    public void testPreparedStatementsWithComments() throws SQLException {
        connection.createStatement().execute("drop table if exists commentPreparedStatements");
        connection.createStatement().execute(
                        "create table commentPreparedStatements (id int not null primary key auto_increment, a varchar(10))");

        String query = "INSERT INTO commentPreparedStatements (a) VALUES (?) # ?";
        PreparedStatement pstmt = connection.prepareStatement(query);
        pstmt.setString(1,"yeah");
        pstmt.execute();
    }
    @Test
    public void testPreparedStatementsWithQuotes() throws SQLException {
        connection.createStatement().execute("drop table if exists quotesPreparedStatements");
        connection.createStatement().execute(
                        "create table quotesPreparedStatements (id int not null primary key auto_increment, a varchar(10))");

        String query = "INSERT INTO quotesPreparedStatements (a) VALUES ('hellooo?') # ?";
        PreparedStatement pstmt = connection.prepareStatement(query);

        pstmt.execute();
    }

    @Test
    public void bigDecimalTest() throws SQLException {
        BigDecimal bd = BigDecimal.TEN;
        connection.createStatement().execute("drop table if exists bigdectest");
        connection.createStatement().execute(
                        "create table bigdectest (id int not null primary key auto_increment, bd decimal) engine=innodb");
        PreparedStatement ps = connection.prepareStatement("insert into bigdectest (bd) values (?)");
        ps.setBigDecimal(1,bd);
        ps.execute();

        ResultSet rs=connection.createStatement().executeQuery("select bd from bigdectest");
        assertTrue(rs.next());
        Object bb = rs.getObject(1);
        assertEquals(bd, bb);
        BigDecimal bigD = rs.getBigDecimal(1);
        BigDecimal bigD2 = rs.getBigDecimal("bd");
        assertEquals(bd,bigD);
        assertEquals(bd,bigD2);
        bigD = rs.getBigDecimal("bd");
        assertEquals(bd,bigD);
    }


   
    @Test
    public void byteTest() throws SQLException {
        connection.createStatement().execute("drop table if exists bytetest");
        connection.createStatement().execute(
                        "create table bytetest (id int not null primary key auto_increment, a int) engine=innodb");
        PreparedStatement ps = connection.prepareStatement("insert into bytetest (a) values (?)");
        ps.setByte(1,Byte.MAX_VALUE);
        ps.execute();
        ResultSet rs=connection.createStatement().executeQuery("select a from bytetest");
        assertTrue(rs.next());

        Byte bc = rs.getByte(1);
        Byte bc2 = rs.getByte("a");

        assertTrue(Byte.MAX_VALUE == bc);
        assertEquals(bc2, bc);


    }


    @Test
    public void shortTest() throws SQLException {
        connection.createStatement().execute("drop table if exists shorttest");
        connection.createStatement().execute(
                        "create table shorttest (id int not null primary key auto_increment,a int) engine=innodb");
        PreparedStatement ps = connection.prepareStatement("insert into shorttest (a) values (?)");
        ps.setShort(1,Short.MAX_VALUE);
        ps.execute();
        ResultSet rs=connection.createStatement().executeQuery("select a from shorttest");
        assertTrue(rs.next());

        Short bc = rs.getShort(1);
        Short bc2 = rs.getShort("a");

        assertTrue(Short.MAX_VALUE == bc);
        assertEquals(bc2, bc);


    }
   @Test
    public void doubleTest() throws SQLException {
        connection.createStatement().execute("drop table if exists doubletest");
        connection.createStatement().execute(
                        "create table doubletest (id int not null primary key auto_increment,a double) engine=innodb");
        PreparedStatement ps = connection.prepareStatement("insert into doubletest (a) values (?)");
        double d = 1.5;
        ps.setDouble(1,d);
        ps.execute();
        ResultSet rs=connection.createStatement().executeQuery("select a from doubletest");
        assertTrue(rs.next());
        Object b = rs.getObject(1);
        assertEquals(b.getClass(),Double.class);
        Double bc = rs.getDouble(1);
        Double bc2 = rs.getDouble("a");

        assertTrue(d == bc);
        assertEquals(bc2, bc);


    }


    @Test
    public void testResultSetPositions() throws SQLException {
        connection.createStatement().execute("drop table if exists ressetpos");
        connection.createStatement().execute(
                        "create table ressetpos (i int not null primary key) engine=innodb");
        connection.createStatement().execute("insert into ressetpos values (1),(2),(3),(4)");

        ResultSet rs =connection.createStatement().executeQuery("select * from ressetpos");
        assertTrue(rs.isBeforeFirst());
        rs.next();
        assertTrue(!rs.isBeforeFirst());
        assertTrue(rs.isFirst());
        rs.beforeFirst();
        assertTrue(rs.isBeforeFirst());
        while(rs.next());
        assertTrue(rs.isAfterLast());
        rs.absolute(4);
        assertTrue(!rs.isAfterLast());
        rs.absolute(2);
        assertEquals(2,rs.getInt(1));
        rs.relative(2);
        assertEquals(4,rs.getInt(1));
        assertTrue(!rs.next());
        rs.previous();
        assertEquals(4,rs.getInt(1));
        rs.relative(-3);
        assertEquals(1,rs.getInt(1));
        assertEquals(false,rs.relative(-1));
        assertEquals(1,rs.getInt(1));
        rs.last();
        assertEquals(4,rs.getInt(1));
        assertEquals(4,rs.getRow());
        assertTrue(rs.isLast());
        rs.first();
        assertEquals(1,rs.getInt(1));
        assertEquals(1,rs.getRow());
        rs.absolute(-1);
        assertEquals(4,rs.getRow());
        assertEquals(4,rs.getInt(1));
    }

    @Test(expected = SQLException.class)
    public void findColumnTest() throws SQLException {
        ResultSet rs = connection.createStatement().executeQuery("select 1 as 'hej'");
        assertEquals(1,rs.findColumn("hej"));

        rs.findColumn("nope");

    }
    @Test
    public void getStatementTest() throws SQLException {
        ResultSet rs = connection.createStatement().executeQuery("select 1 as 'hej'");
        Statement stmt = rs.getStatement();
    }
    @Test
    public void getUrlTest() throws SQLException {
        ResultSet rs = connection.createStatement().executeQuery("select 'http://mariadb.org' as url");
        rs.next();
        URL url = rs.getURL(1);
        assertEquals("http://mariadb.org",url.toString());
        url = rs.getURL("url");
        assertEquals("http://mariadb.org",url.toString());

    }
    @Test(expected = SQLException.class)
    public void getUrlFailTest() throws SQLException {
        ResultSet rs = connection.createStatement().executeQuery("select 'asdf' as url");
        rs.next();
        URL url = rs.getURL(1);
        

    }
    @Test(expected = SQLException.class)
    public void getUrlFailTest2() throws SQLException {
        ResultSet rs = connection.createStatement().executeQuery("select 'asdf' as url");
        rs.next();
        URL url = rs.getURL("url");


    }
    @Test
    public void setNull() throws SQLException {
        PreparedStatement ps = connection.prepareStatement("insert blabla (?)");
        ps.setString(1,null);
    }

    @Test
    public void testBug501452() throws SQLException {
        Connection conn = connection;
        if(conn.isWrapperFor(MySQLConnection.class)) {
            MySQLConnection dc = conn.unwrap(MySQLConnection.class);
            dc.setBatchQueryHandlerFactory(new RewriteParameterizedBatchHandlerFactory());
        }
        Statement stmt = conn.createStatement();
        stmt.executeUpdate("drop table if exists bug501452");
        stmt.executeUpdate("CREATE TABLE bug501452 (id int not null primary key, value varchar(20))");
        stmt.close();
        PreparedStatement ps=conn.prepareStatement("insert into bug501452 (id,value) values (?,?)");
        ps.setObject(1, 1);
        ps.setObject(2, "value for 1");
        ps.addBatch();

        ps.executeBatch();

        ps.setObject(1, 2);
        ps.setObject(2, "value for 2");
        ps.addBatch();

        ps.executeBatch();

        connection.commit();

    }

    @Test
    public void testBug525946() throws SQLException {
        Connection conn = connection;
        assertTrue(conn.getAutoCommit());
        conn.setAutoCommit(false);
        assertFalse(conn.getAutoCommit());
    }
    @Test
    public void testUpdateCount() throws SQLException {
        Connection conn = connection;
        Statement stmt = conn.createStatement();
        stmt.execute("select 1") ;
        System.out.println(stmt.getUpdateCount());
    }

    @Test
    public void testSetObject() throws SQLException {
        Connection conn = connection;
        Statement stmt = conn.createStatement();
        stmt.executeUpdate("drop table if exists test_setobjectconv");
        stmt.executeUpdate("CREATE TABLE test_setobjectconv (id int not null primary key auto_increment, v1 varchar(40), v2 varchar(40))");
        stmt.close();
        PreparedStatement ps = conn.prepareStatement("insert into test_setobjectconv values (null, ?, ?)");
        ps.setObject(1,"2009-01-01 00:00:00", Types.TIMESTAMP);
        ps.setObject(2, "33", Types.DOUBLE);
        ps.execute();
    }

    @Test
    public void testBit() throws SQLException {

        Connection conn = connection;
        conn.createStatement().execute("drop table if exists bittest");
        conn.createStatement().execute("create table bittest(id int not null primary key auto_increment, b int)");
        PreparedStatement stmt = conn.prepareStatement("insert into bittest values(null, ?)");
        stmt.setBoolean(1, true);
        stmt.execute();
        stmt.setBoolean(1, false);
        stmt.execute();

        ResultSet rs = conn.createStatement().executeQuery("select * from bittest");
        Assert.assertTrue(rs.next());
        Assert.assertTrue(rs.getBoolean("b"));
        Assert.assertTrue(rs.next());
        assertFalse(rs.getBoolean("b"));
        assertFalse(rs.next());
    }

    @Test
    public void testConnectWithDB() throws SQLException {

        Connection conn = connection;
        try {
            conn.createStatement().executeUpdate("drop database test_testdrop");
        } catch (Exception e) {}
        conn = DriverManager.getConnection("jdbc:mysql:thin://localhost:3306/test_testdrop?createDB=true&user=root");
        DatabaseMetaData dbmd = conn.getMetaData();
        ResultSet rs = dbmd.getSchemas();
        boolean foundDb = false;
        while(rs.next()) {
            if(rs.getString("table_schem").equals("test_testdrop")) foundDb = true;
        }
        assertTrue(foundDb);


    }

     @Test
    public void testError() throws SQLException {
        if(!checkMaxAllowedPacket("testError"))
            return;
        Connection conn = connection;
        try {
            char arr[] = new char[16*1024*1024-1];
            Arrays.fill(arr,'a');
            ResultSet rs = conn.createStatement().executeQuery("select '" + new String(arr) + "'");
            rs.next();
            System.out.println(rs.getString(1).length());
        } finally {
            conn.close();
        }
    }


    @Test
    public void StreamingResult() throws SQLException {
        Statement st = connection.createStatement();

        st.execute("drop table if exists streamingtest");
        st.execute("create table streamingtest(val varchar(20))");

        for(int i = 0; i< 100; i++) {
            st.execute("insert into streamingtest values('aaaaaaaaaaaaaaaaaa')");
        }
        st.setFetchSize(Integer.MIN_VALUE);
        ResultSet rs = st.executeQuery("select * from streamingtest");
        rs.next();
        rs.close();
        Statement st2  = connection.createStatement();
        ResultSet rs2 = st2.executeQuery("select * from streamingtest");
        rs2.next();
        rs.close();
    }

    // Test if driver works with sql_mode= NO_BACKSLASH_ESCAPES
    @Test
    public void NoBackslashEscapes() throws SQLException {
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("select @@global.sql_mode");
        rs.next();
        String originalSqlMode = rs.getString(1);
        st.execute("set @@global.sql_mode = '" + originalSqlMode + ",NO_BACKSLASH_ESCAPES'");

        try {
            st.execute("drop table if exists testBlob2");
            st.execute("create table testBlob2(a blob)");
            Connection newConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306/test?user=root");
            try {
                PreparedStatement preparedStatement =
                        newConnection.prepareStatement("insert into testBlob2(a) values(?)");
                byte[] b = new byte[255];
                for(byte i= -128; i < 127; i++) {
                    b[i+128] = i;
                }
                MySQLBlob blob = new MySQLBlob(b);
                preparedStatement.setBlob(1, blob);
                int affectedRows = preparedStatement.executeUpdate();
                Assert.assertEquals(affectedRows, 1);
            } finally {
                newConnection.close();
            }
        }
        finally {
            st.execute("set @@global.sql_mode='" + originalSqlMode+ "'");
        }
    }

 // Test if driver works with sql_mode= NO_BACKSLASH_ESCAPES
    @Test
    public void NoBackslashEscapes2() throws SQLException {
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("select @@global.sql_mode");
        rs.next();
        String originalSqlMode = rs.getString(1);
        st.execute("set @@global.sql_mode = '" + originalSqlMode + ",NO_BACKSLASH_ESCAPES'");

        try {
            st.execute("drop table if exists testString2");
            st.execute("create table testString2(a varchar(10))");
            Connection newConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306/test?user=root");
            try {
                PreparedStatement preparedStatement =
                        newConnection.prepareStatement("insert into testString2(a) values(?)");
                preparedStatement.setString(1, "'\\");
                int affectedRows = preparedStatement.executeUpdate();
                Assert.assertEquals(affectedRows, 1);
                preparedStatement.close();
                preparedStatement =
                        newConnection.prepareStatement("select * from testString2");
                rs = preparedStatement.executeQuery();
                rs.next();
                String out = rs.getString(1);
                assertEquals(out, "'\\");
                Statement st2  = newConnection.createStatement();
                rs = st2.executeQuery("select 'a\\b\\c'");
                rs.next();
                assertEquals("a\\b\\c",rs.getString(1));

            } finally {
                newConnection.close();
            }
        }
        finally {
            st.execute("set @@global.sql_mode='" + originalSqlMode+ "'");
        }
    }

    // Test if driver works with sql_mode= ANSI_QUOTES
    @Test
    public void AnsiQuotes() throws SQLException {
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("select @@global.sql_mode");
        rs.next();
        String originalSqlMode = rs.getString(1);
        st.execute("set @@global.sql_mode = '" + originalSqlMode + ",ANSI_QUOTES'");

        try {
            st.execute("drop table if exists testBlob2");
            st.execute("create table testBlob2(a blob)");

            Connection newConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306/test?user=root");
            try {
                PreparedStatement preparedStatement =
                        newConnection.prepareStatement("insert into testBlob2(a) values(?)");
                byte[] b = new byte[255];
                for(byte i= -128; i < 127; i++) {
                    b[i+128] = i;
                }
                MySQLBlob blob = new MySQLBlob(b);
                preparedStatement.setBlob(1, blob);
                int affectedRows = preparedStatement.executeUpdate();
                Assert.assertEquals(affectedRows, 1);
            } finally {
                newConnection.close();
            }
        }
        finally {
            st.execute("set @@global.sql_mode='" + originalSqlMode+ "'");
        }
    }

    @Test
    public void unsignedTest() throws Exception {
        Statement st = connection.createStatement();
        st.execute("drop table if exists unsignedtest");
        st.execute("create table unsignedtest(a int unsigned)");
        st.execute("insert into unsignedtest values(4294967295)");
        ResultSet rs = st.executeQuery("select * from unsignedtest");
        rs.next();
        Object o = rs.getLong("unsignedtest.a");
    }

    @Test
    public void autoreconnect() throws Exception {
       Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306?user=root&autoReconnect=true");
       ResultSet rs= c.createStatement().executeQuery("select connection_id()");
       rs.next();
       long connectionId = rs.getLong(1);
       rs.close();

       c.createStatement().execute("set wait_timeout=1");
       Thread.sleep(3000);

       Exception caughtException = null;
       boolean success = false;
       for (int i=0; i < 2; i++) {
           try {
               rs = c.createStatement().executeQuery("select 1");
               rs.close();
               success = true;
               break;
           } catch (Exception e) {
              caughtException = e;
           }
       }
       assertTrue(success);
       rs = c.createStatement().executeQuery("select connection_id()");
       rs.next();
       long connectionId2 = rs.getLong(1);
       assertNotSame(connectionId, connectionId2);
    }

    @Test
    public void useSSL()  throws Exception {
        Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306?user=root");
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery("select @@have_openssl");
        rs.next();
        boolean have_openssl = rs.getString(1).equals("YES");
        c.close();
        if(!have_openssl) {
            System.out.println("test 'useSSL' skipped  due to server variable have_openssl != 'YES'");
            return;
        }
        c = DriverManager.getConnection("jdbc:mysql://localhost:3306?user=root&useSSL=1&trustServerCertificate=1");
        c.createStatement().execute("select 1");
        c.close();

    }
}