/*
MariaDB Client for Java

Copyright (c) 2012-2014 Monty Program Ab.
Copyright (c) 2015-2016 MariaDB Ab.

This library is free software; you can redistribute it and/or modify it under
the terms of the GNU Lesser General Public License as published by the Free
Software Foundation; either version 2.1 of the License, or (at your option)
any later version.

This library is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
for more details.

You should have received a copy of the GNU Lesser General Public License along
with this library; if not, write to Monty Program Ab info@montyprogram.com.

This particular MariaDB Client for Java file is work
derived from a Drizzle-JDBC. Drizzle-JDBC file which is covered by subject to
the following copyright and notice provisions:

Copyright (c) 2009-2011, Marcus Eriksson

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.

Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or
other materials provided with the distribution.

Neither the name of the driver nor the names of its contributors may not be
used to endorse or promote products derived from this software without specific
prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS  AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
*/

package org.mariadb.jdbc.internal.packet;

import org.mariadb.jdbc.internal.packet.dao.ColumnInformation;
import org.mariadb.jdbc.internal.packet.read.ReadPacketFetcher;
import org.mariadb.jdbc.internal.packet.result.ErrorPacket;
import org.mariadb.jdbc.internal.protocol.Protocol;
import org.mariadb.jdbc.internal.stream.PacketOutputStream;
import org.mariadb.jdbc.internal.util.ExceptionMapper;
import org.mariadb.jdbc.internal.util.buffer.Buffer;
import org.mariadb.jdbc.internal.util.dao.QueryException;
import org.mariadb.jdbc.internal.util.dao.ServerPrepareResult;

import java.io.IOException;
import java.io.UnsupportedEncodingException;


public class ComStmtPrepare {
    private final Protocol protocol;
    private final String sql;

    public ComStmtPrepare(Protocol protocol, String sql) {
        this.protocol = protocol;
        this.sql = sql;
    }

    /**
     * Send directly to socket the sql data.
     *
     * @param writer the writer
     * @throws IOException if connection error occur
     * @throws QueryException if packet max size is to big.
     */
    public void send(PacketOutputStream writer) throws IOException, QueryException {
        if (writer.isClosed()) throw new IOException("Stream has already closed");
        byte[] sqlBytes = sql.getBytes("UTF-8");
        int sqlLength = sqlBytes.length + 1;
        if (sqlLength > writer.getMaxAllowedPacket()) {
            throw new QueryException("Could not send query: max_allowed_packet=" + writer.getMaxAllowedPacket() + " but packet size is : "
                    + sqlLength, -1, ExceptionMapper.SqlStates.INTERRUPTED_EXCEPTION.getSqlState());
        }
        byte[] packetBuffer = new byte[sqlLength + 4];
        packetBuffer[0] = (byte) (sqlLength & 0xff);
        packetBuffer[1] = (byte) (sqlLength >>> 8);
        packetBuffer[2] = (byte) (sqlLength >>> 16);
        packetBuffer[3] = (byte) 0;
        packetBuffer[4] = Packet.COM_STMT_PREPARE;

        System.arraycopy(sqlBytes, 0, packetBuffer, 5, sqlLength - 1);
        writer.send(packetBuffer, sqlLength + 4);
    }

    /**
     * Send Prepare statement sub-command COM_MULTI (with 3 bytes length prefix).
     *
     * @param writer outputStream
     */
    public void sendComMulti(PacketOutputStream writer) {
        try {
            byte[] sqlBytes = sql.getBytes("UTF-8");
            int prepareLengthCommand = sqlBytes.length + 1;

            //prepare length
            writer.buffer.put((byte) (prepareLengthCommand & 0xff));
            writer.buffer.put((byte) (prepareLengthCommand >>> 8));
            writer.buffer.put((byte) (prepareLengthCommand >>> 16));

            //prepare subCommand
            writer.buffer.put(Packet.COM_STMT_PREPARE);
            writer.write(sqlBytes);
        } catch (UnsupportedEncodingException exception) {
            //cannot happen
        }
    }

    /**
     * Read COM_PREPARE_RESULT.
     *
     * @param packetFetcher inputStream
     * @return ServerPrepareResult prepare result
     * @throws IOException is connection has error
     * @throws QueryException if server answer with error.
     */
    public ServerPrepareResult read(ReadPacketFetcher packetFetcher) throws IOException, QueryException {
        Buffer buffer = packetFetcher.getReusableBuffer();
        byte firstByte = buffer.getByteAt(0);

        if (firstByte == Packet.ERROR) {
            ErrorPacket ep = new ErrorPacket(buffer);
            String message = ep.getMessage();
            throw new QueryException("Error preparing query: " + message
                    + "\nIf a parameter type cannot be identified (example 'select ? `field1` from dual'). "
                    + "Use CAST function to solve this problem (example 'select CAST(? as integer) `field1` from dual')",
                    ep.getErrorNumber(), ep.getSqlState());
        }

        if (firstByte == Packet.OK) {
                /* Prepared Statement OK */
            buffer.readByte(); /* skip field count */
            final int statementId = buffer.readInt();
            final int numColumns = buffer.readShort() & 0xffff;
            final int numParams = buffer.readShort() & 0xffff;
            buffer.readByte(); // reserved
            protocol.setHasWarnings(buffer.readShort() > 0);
            ColumnInformation[] params = new ColumnInformation[numParams];
            if (numParams > 0) {
                for (int i = 0; i < numParams; i++) {
                    params[i] = new ColumnInformation(packetFetcher.getPacket());
                }
                protocol.readEofPacket();
            }
            ColumnInformation[] columns = new ColumnInformation[numColumns];
            if (numColumns > 0) {
                for (int i = 0; i < numColumns; i++) {
                    columns[i] = new ColumnInformation(packetFetcher.getPacket());
                }
                protocol.readEofPacket();
            }
            ServerPrepareResult serverPrepareResult = new ServerPrepareResult(sql, statementId, columns, params, protocol);
            if (protocol.getOptions().cachePrepStmts && sql != null && sql.length() < protocol.getOptions().prepStmtCacheSqlLimit) {
                String key = new StringBuilder(protocol.getDatabase()).append("-").append(sql).toString();
                ServerPrepareResult cachedServerPrepareResult = protocol.addPrepareInCache(key, serverPrepareResult);
                return cachedServerPrepareResult != null ? cachedServerPrepareResult : serverPrepareResult;
            }
            return serverPrepareResult;
        } else {
            throw new QueryException("Unexpected packet returned by server, first byte " + firstByte);
        }
    }



}