package org.drizzle.jdbc.internal.packet;

/**
 * Created by IntelliJ IDEA.
 * User: marcuse
 * Date: Jan 21, 2009
 * Time: 9:44:54 PM
 * To change this template use File | Settings | File Templates.
 */
public interface DrizzlePacket {
    public byte [] toBytes(byte queryNumber);
}
