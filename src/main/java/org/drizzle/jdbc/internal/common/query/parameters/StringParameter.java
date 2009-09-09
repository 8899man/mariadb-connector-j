/*
 * Drizzle JDBC
 *
 * Copyright (C) 2009 Marcus Eriksson (krummas@gmail.com)
 * All rights reserved.
 *
 * Use and distribution licensed under the BSD license.
 */

package org.drizzle.jdbc.internal.common.query.parameters;

import static org.drizzle.jdbc.internal.common.Utils.sqlEscapeString;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

/**
 * User: marcuse Date: Feb 18, 2009 Time: 10:17:14 PM
 */
public class StringParameter implements ParameterHolder {
    private final byte[] byteRepresentation;

    public StringParameter(final String parameter) {
        final String tempParam = "\"" + sqlEscapeString(parameter) + "\"";
        try {
            this.byteRepresentation = tempParam.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("unsupp encoding: " + e.getMessage(), e);
        }
    }

    public void writeTo(final OutputStream os) throws IOException {
        for (final byte b : byteRepresentation) {
            os.write(b);
        }
    }

    public long length() {
        return byteRepresentation.length;
    }
}
