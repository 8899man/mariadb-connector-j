/*
 * Drizzle JDBC
 *
 * Copyright (C) 2009 Marcus Eriksson (krummas@gmail.com)
 * All rights reserved.
 *
 * Use and distribution licensed under the BSD license.
 */

package org.drizzle.jdbc;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a jdbc url.
 * <p/>
 * User: marcuse Date: Apr 21, 2009 Time: 9:32:34 AM
 */
public class JDBCUrl {
    private final DBType dbType;
    private final String username;
    private final String password;
    private final String hostname;
    private final int port;
    private final String database;

    public enum DBType {
        DRIZZLE, MYSQL
    }


    public JDBCUrl(final String url) throws SQLException {
        final Pattern p = Pattern.compile("^jdbc:(drizzle|mysql:thin)://((\\w+)(:(\\w+))?@)?([^/:]+)(:(\\d+))?(/(\\w+))?");
        final Matcher m = p.matcher(url);
        if (m.find()) {
            if (m.group(1).equals("mysql:thin")) {
                this.dbType = DBType.MYSQL;
            } else {
                this.dbType = DBType.DRIZZLE;
            }

            this.username = (m.group(3) == null ? "" : m.group(3));
            this.password = (m.group(5) == null ? "" : m.group(5));
            this.hostname = (m.group(6) == null ? "" : m.group(6));
            if (m.group(8) != null) {
                this.port = Integer.parseInt(m.group(8));
            } else {
                if (this.dbType == DBType.DRIZZLE) {
                    this.port = 3306;
                } else {
                    this.port = 3306;
                }
            }
            this.database = m.group(10);
        } else {
            throw new SQLException("Could not parse connection string: "+url);
        }
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public DBType getDBType() {
        return this.dbType;
    }

}