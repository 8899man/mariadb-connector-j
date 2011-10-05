package org.skysql.jdbc;

import org.junit.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import static junit.framework.Assert.assertEquals;

public class CatalogTest extends BaseTest {
    static { Logger.getLogger("").setLevel(Level.FINEST); }

    public CatalogTest() {
    }

    @Test
    public void catalogTest() throws SQLException {
    	Statement stmt = connection.createStatement();
        stmt.executeUpdate("drop database if exists cattest1");
        stmt.executeUpdate("create database cattest1");
        connection.setCatalog("cattest1");
        assertEquals("cattest1", connection.getCatalog());
    }
    
    @Test(expected = SQLException.class)
    public void catalogTest2() throws SQLException {
        connection.setCatalog(null);
    }
    
    @Test(expected = SQLException.class)
    public void catalogTest3() throws SQLException {
        connection.setCatalog("Non-existent catalog");
    }  
    
    @Test(expected = SQLException.class)
    public void catalogTest4() throws SQLException {
        connection.setCatalog("");
    } 

    @Test
    public void catalogTest5() throws SQLException {
        MySQLConnection c = (MySQLConnection)connection;
        String[] weirdDbNames = new String[] {"abc 123","\"", "`"};
        for(String name : weirdDbNames) {
            Statement stmt = connection.createStatement();
            stmt.execute("drop database if exists " + c.quoteIdentifier(name));
            stmt.execute("create database " + c.quoteIdentifier(name));
            connection.setCatalog(name);
            assertEquals(name, connection.getCatalog());
            stmt.execute("drop database if exists " + c.quoteIdentifier(name));
            stmt.close();
        }
    }
}
