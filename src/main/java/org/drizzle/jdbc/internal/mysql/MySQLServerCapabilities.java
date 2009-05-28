/*
 * Drizzle JDBC
 *
 * Copyright (C) 2009 Marcus Eriksson (krummas@gmail.com)
 * All rights reserved.
 *
 * Use and distribution licensed under the BSD license.
 */

package org.drizzle.jdbc.internal.mysql;

import java.util.EnumSet;
import java.util.Set;

/**
 * Represents what the server is capable of
 * <p/>
 * User: marcuse
 * Date: Feb 27, 2009
 * Time: 9:01:29 PM
 */
public enum MySQLServerCapabilities {
    LONG_PASSWORD((short) 1),       /* new more secure passwords */
    FOUND_ROWS((short) 2),       /* Found instead of affected rows */
    LONG_FLAG((short) 4),       /* Get all column flags */
    CONNECT_WITH_DB((short) 8),       /* One can specify db on connect */
    NO_SCHEMA((short) 16),      /* Don't allow database.table.column */
    COMPRESS((short) 32),      /* Can use compression protocol */
    ODBC((short) 64),      /* Odbc client */
    LOCAL_FILES((short) 128),     /* Can use LOAD DATA LOCAL */
    IGNORE_SPACE((short) 256),     /* Ignore spaces before '(' */
    CLIENT_PROTOCOL_41((short) 512),     /* New 4.1 protocol */
    CLIENT_INTERACTIVE((short) 1024),
    SSL((short) 2048),    /* Switch to SSL after handshake */
    IGNORE_SIGPIPE((short) 4096),    /* IGNORE sigpipes */
    TRANSACTIONS((short) 8192),
    RESERVED((short) 16384),   /* Old flag for 4.1 protocol  */
    SECURE_CONNECTION((short) 32768),  /* New 4.1 authentication */
    MULTI_STATEMENTS((short) (1L << 16)), /* Enable/disable multi-stmt support */
    MULTI_RESULTS((short) (1L << 17)); /* Enable/disable multi-results */

    private short bitmapFlag;

    MySQLServerCapabilities(short i) {
        this.bitmapFlag = i;
    }

    /**
     * creates an enum set of the server capabilities represented by i
     *
     * @param i the value from the server
     * @return an enum set containing the flags in i
     */
    public static Set<MySQLServerCapabilities> getServerCapabilitiesSet(short i) {
        Set<MySQLServerCapabilities> statusSet = EnumSet.noneOf(MySQLServerCapabilities.class);
        for (MySQLServerCapabilities value : MySQLServerCapabilities.values())
            if ((i & value.getBitmapFlag()) == value.getBitmapFlag())
                statusSet.add(value);
        return statusSet;
    }

    /**
     * the raw bit map flag from the server
     *
     * @return the raw map flag
     */
    public short getBitmapFlag() {
        return bitmapFlag;
    }

    /**
     * creates a bitmasked short from an enum set
     *
     * @param capabilities
     * @return
     */
    public static short fromSet(Set<MySQLServerCapabilities> capabilities) {
        short retVal = 0;
        for (MySQLServerCapabilities cap : capabilities) {
            retVal = (short) (retVal | cap.getBitmapFlag());
        }
        return retVal;
    }
}