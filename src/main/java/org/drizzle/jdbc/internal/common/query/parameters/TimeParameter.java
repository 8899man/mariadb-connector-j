/*
 * Drizzle JDBC
 *
 * Copyright (C) 2009 Marcus Eriksson (krummas@gmail.com)
 * All rights reserved.
 *
 * Use and distribution licensed under the BSD license.
 */

package org.drizzle.jdbc.internal.common.query.parameters;

import org.drizzle.jdbc.internal.common.Utils;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 */
public class TimeParameter implements ParameterHolder {
    private final byte[] byteRepresentation;

    public TimeParameter(final long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        byteRepresentation = ("\""+sdf.format(new Date(timestamp))+"\"").getBytes();
    }

    public int writeTo(final OutputStream os, int offset, int maxWriteSize) throws IOException {
        int bytesToWrite = Math.min(byteRepresentation.length - offset, maxWriteSize);
        os.write(byteRepresentation, offset, bytesToWrite);
        return bytesToWrite;
    }
    
    public long length() {
        return byteRepresentation.length;
    }
}