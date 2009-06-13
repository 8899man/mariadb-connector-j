/*
 * Drizzle JDBC
 *
 * Copyright (C) 2009 Marcus Eriksson (krummas@gmail.com)
 * All rights reserved.
 *
 * Use and distribution licensed under the BSD license.
 */

package org.drizzle.jdbc.internal.common.packet.buffer;

import org.drizzle.jdbc.internal.common.packet.RawPacket;

import java.io.IOException;
import java.io.InputStream;

/**
 * .
 * User: marcuse
 * Date: Jan 16, 2009
 * Time: 8:27:38 PM
 */
public class ReadUtil {
    private static int readLength(InputStream reader) throws IOException {
        byte[] lengthBuffer = new byte[3];
        for (int i = 0; i < 3; i++)
            lengthBuffer[i] = (byte) reader.read();
        return (lengthBuffer[0] & 0xff) + (lengthBuffer[1] << 8) + (lengthBuffer[2] << 16);
    }

    private static byte readPacketSeq(InputStream reader) throws IOException {
        return (byte) reader.read();
    }

    public static byte getByteAt(InputStream reader, int i) throws IOException {
        reader.mark(i + 1);
        long skipped = reader.skip(i - 1);
        if(skipped != i-1) {
            throw new IOException("Could not skip the requested number of bytes.");
        }

        byte b = (byte) reader.read();
        reader.reset();
        return b;
    }

    public static boolean eofIsNext(InputStream reader) throws IOException {
        reader.mark(10);
        int length = readLength(reader);
        byte packetType = (byte) reader.read();
        reader.reset();
        return (packetType == (byte) 0xfe) && length < 9;
    }

    public static boolean eofIsNext(RawPacket rawPacket) {
        byte[] rawBytes = rawPacket.getRawBytes();
        return (rawBytes[0] == (byte) 0xfe && rawBytes.length < 9);

    }

    public static short readShort(byte[] bytes, int start) {
        if (bytes.length - start >= 2)
            return (short) ((bytes[start] & (short)0xff) + ((bytes[start + 1] & (short)0xff) << 8));
        return 0;
    }

    public static int read24bitword(byte[] bytes, int start) {
        return (bytes[start] & 0xff) + ((bytes[start + 1] & 0xff) << 8) + ((bytes[start + 2] & 0xff) << 16);
    }

    public static long readLong(byte[] bytes, int start) {
        return
                ((long)bytes[start] & 0xff) +
                        ((long)(bytes[start + 1] & 0xff) << 8) +
                        ((long)(bytes[start + 2] & 0xff) << 16) +
                        ((long)(bytes[start + 3] & 0xff) << 24) +
                        ((long)(bytes[start + 4] & 0xff) << 32) +
                        ((long)(bytes[start + 5] & 0xff) << 40) +
                        ((long)(bytes[start + 6] & 0xff) << 48) +
                        ((long)(bytes[start + 7] & 0xff) << 56);
    }

    public static LengthEncodedBytes getLengthEncodedBytes(byte[] rawBytes, int start) {
        return new LengthEncodedBytes(rawBytes, start);
    }

    public static LengthEncodedBinary getLengthEncodedBinary(byte[] rawBytes, int start) {
        return new LengthEncodedBinary(rawBytes, start);
    }
}