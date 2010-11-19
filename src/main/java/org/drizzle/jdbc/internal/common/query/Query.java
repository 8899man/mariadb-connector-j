/*
 * Drizzle JDBC
 *
 * Copyright (C) 2009 Marcus Eriksson (krummas@gmail.com)
 * All rights reserved.
 *
 * Use and distribution licensed under the BSD license.
 */

package org.drizzle.jdbc.internal.common.query;

import org.drizzle.jdbc.internal.common.QueryException;

import java.io.IOException;
import java.io.OutputStream;

/**
 * . User: marcuse Date: Feb 20, 2009 Time: 10:48:45 PM
 */
public interface Query {
    int length() throws QueryException;

    void writeTo(OutputStream os) throws IOException, QueryException;

    String getQuery();

    QueryType getQueryType();

    void writeTo(OutputStream ostream, int offset, int packLength) throws IOException, QueryException;
}
