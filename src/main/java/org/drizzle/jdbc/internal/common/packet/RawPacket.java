/*
 * Drizzle JDBC
 *
 * Copyright (C) 2009 Marcus Eriksson (krummas@gmail.com)
 * All rights reserved.
 *
 * Copyright (C) 2009 Sun Microsystems
 * All rights reserved.
 *
 * Use and distribution licensed under the BSD license.
 */
package org.drizzle.jdbc.internal.common.packet;

import org.drizzle.jdbc.internal.common.packet.buffer.ReadUtil;

import java.io.IOException;
import java.io.InputStream;


/**
 * Class to represent a raw packet as transferred over the wire. First we got 3 bytes specifying the actual length, then
 * one byte packet sequence number and then n bytes with user data.
 */
public final class RawPacket {

    static final RawPacket IOEXCEPTION_PILL = new RawPacket(null, -1);
    private final byte[] rawBytes;
    private final int packetSeq;

    /**
     * Get the next packet from the stream
     *
     * @param is the input stream to read the next packet from
     * @return The next packet from the stream, or NULL if the stream is closed
     * @throws java.io.IOException if an error occurs while reading data
     */
    static RawPacket nextPacket(final InputStream is) throws IOException {
        final int length = readLength(is);
        if (length == -1) {
            return null;
        }

        if (length < 0) {
            throw new IOException("Got negative packet size: " + length);
        }

        final int packetSeq = readPacketSeq(is);

        final byte[] rawBytes = new byte[length];

        final int nr = ReadUtil.safeRead(is, rawBytes);
        if (nr != length) {
            throw new IOException("EOF. Expected " + length + ", got " + nr);
        }

        return new RawPacket(rawBytes, packetSeq);
    }

    private RawPacket(final byte[] rawBytes, final int packetSeq) {
        this.rawBytes = rawBytes;
        this.packetSeq = packetSeq;
    }

    private static byte readPacketSeq(final InputStream reader) throws IOException {
        final int val = reader.read();
        if (val == -1) {
            throw new IOException("EOF");
        }

        return (byte) val;
    }

    private static int readLength(final InputStream reader) throws IOException {
        final byte[] lengthBuffer = new byte[3];

        final int nr = ReadUtil.safeRead(reader, lengthBuffer);
        if (nr == -1) {
            return -1;
        } else if (nr != 3) {
            throw new IOException("Incomplete read! Expected 3, got " + nr);
        }

        return (lengthBuffer[0] & 0xff) + ((lengthBuffer[1] & 0xff) << 8) + ((lengthBuffer[2] & 0xff) << 16);
    }

    /**
     * Get the raw bytes in the package
     *
     * @return an array with the payload of the package
     */
    public byte[] getRawBytes() {
        return rawBytes;
    }

    /**
     * Get the package sequence number
     *
     * @return the sequence number of the package
     */
    public int getPacketSeq() {
        return packetSeq;
    }
}
