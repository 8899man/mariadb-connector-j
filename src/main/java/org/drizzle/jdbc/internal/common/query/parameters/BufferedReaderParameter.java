/*
 * Drizzle JDBC
 *
 * Copyright (C) 2009 Marcus Eriksson (krummas@gmail.com)
 * All rights reserved.
 *
 * Use and distribution licensed under the BSD license.
 */

package org.drizzle.jdbc.internal.common.query.parameters;

import static org.drizzle.jdbc.internal.common.Utils.needsEscaping;
import org.drizzle.jdbc.internal.common.Utils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;

/**
 * . User: marcuse Date: Feb 27, 2009 Time: 9:53:04 PM
 */
public class BufferedReaderParameter implements ParameterHolder {
    private final int length;
    private final byte[] byteRepresentation;

    public BufferedReaderParameter(final Reader reader) throws IOException {
        byte b;
        byte[] tempByteRepresentation = new byte[1000];
        int pos = 0;
        tempByteRepresentation[pos++] = (byte) '"';
        while ((b = (byte) reader.read()) != -1) {
            if (pos > tempByteRepresentation.length - 2) { //need two places in worst case
                tempByteRepresentation = Utils.copyWithLength(tempByteRepresentation, tempByteRepresentation.length * 2);
            }
            if (needsEscaping(b)) {
                tempByteRepresentation[pos++] = '\\';
            }
            tempByteRepresentation[pos++] = b;
        }
        tempByteRepresentation[pos++] = (byte) '"';
        length = pos;
        byteRepresentation = tempByteRepresentation;
    }

    public int writeTo(final OutputStream os,int offset, int maxWriteSize) throws IOException {
        int bytesToWrite = Math.min(length - offset, maxWriteSize);
        os.write(byteRepresentation, offset, bytesToWrite);
        return bytesToWrite;

    }

    public long length() {
        return length;
    }
}
