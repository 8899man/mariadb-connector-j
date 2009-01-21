package org.drizzle.jdbc;

import org.drizzle.jdbc.packet.buffer.WriteBuffer;
import org.drizzle.jdbc.packet.DrizzlePacket;

/**
 * Created by IntelliJ IDEA.
 * User: marcuse
 * Date: Jan 20, 2009
 * Time: 10:50:47 PM
 * To change this template use File | Settings | File Templates.
 */
public class SelectDBPacket implements DrizzlePacket {
    WriteBuffer buffer = new WriteBuffer();
    public SelectDBPacket(String database) {
        buffer.writeByte((byte)0x02);
        buffer.writeString(database);
    }
    public byte [] getBytes(byte commandNumber){
        return buffer.toByteArrayWithLength(commandNumber);
    }
}
