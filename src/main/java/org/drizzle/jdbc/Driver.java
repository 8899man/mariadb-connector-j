package org.drizzle.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.drizzle.jdbc.internal.DrizzleProtocol;
import org.drizzle.jdbc.internal.QueryException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverPropertyInfo;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: marcuse
 * Date: Jan 14, 2009
 * Time: 7:46:09 AM
 */
public class Driver implements java.sql.Driver {
    private String hostname;
    private int port;
    private String username;
    private String password;
    private String database;
    private static final Logger log = LoggerFactory.getLogger(Driver.class);
    static {
        try {
            DriverManager.registerDriver(new Driver());
        } catch (SQLException e) {
            throw new RuntimeException("Could not register driver",e);
        }
    }

    public Connection connect(String url, Properties info) throws SQLException {
        // TODO: handle the properties!
        // TODO: define what props we support!

        log.debug("Connecting to: {} ",url);
        this.parseUrl(url);

        try {
            return new DrizzleConnection(new DrizzleProtocol(this.hostname,this.port,this.database,this.username,this.password));
        } catch (QueryException e) {
            throw new SQLException("Could not connect",e);
        }
    }

    /**
     * Syntax for connection url is:
     * jdbc:drizzle://username:password@host:port/database
     * @param url the url to parse
     * @throws SQLException if the connection string is bad
     */
    private void parseUrl(String url) throws SQLException {
        Pattern p = Pattern.compile("^jdbc:drizzle://((\\w+)(:(\\w+))?@)?([^/:]+)(:(\\d+))?(/(\\w+))?");
        Matcher m=p.matcher(url);
        if(m.find()) {
            this.username = m.group(2);
            log.debug("found username: {}",username);
            this.password = m.group(4);
            log.debug("found password: {}",password);
            this.hostname = m.group(5);
            log.debug("Found hostname: {}",hostname);

            if(m.group(7) != null) {
                this.port = Integer.parseInt(m.group(7));
                log.debug("Found port: {}",port);

            } else {
                this.port=4427;
            }
            this.database = m.group(9);
            log.debug("Found database: {}",database);

        } else {
            log.debug("Could not parse connection string");
            throw new SQLException("Could not parse connection string...");
        }
    }

    public boolean acceptsURL(String url) throws SQLException {
        return url.startsWith("jdbc:drizzle://");
    }

    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
    }

    public int getMajorVersion() {
        return 0;
    }

    public int getMinorVersion() {
        return 0;
    }

    public boolean jdbcCompliant() {
        return false;
    }
}
