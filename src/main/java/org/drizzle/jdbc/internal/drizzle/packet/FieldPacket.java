/*
 * Drizzle JDBC
 *
 * Copyright (C) 2009 Marcus Eriksson (krummas@gmail.com)
 * All rights reserved.
 *
 * Use and distribution licensed under the BSD license.
 */

package org.drizzle.jdbc.internal.drizzle.packet;

import org.drizzle.jdbc.internal.common.ColumnInformation;
import org.drizzle.jdbc.internal.common.packet.RawPacket;
import org.drizzle.jdbc.internal.common.packet.buffer.Reader;
import org.drizzle.jdbc.internal.common.queryresults.ColumnFlags;
import org.drizzle.jdbc.internal.drizzle.DrizzleColumnInformation;
import org.drizzle.jdbc.internal.drizzle.DrizzleType;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

/**
 * Creates column information from field packets User: marcuse Date: Jan 21, 2009 Time: 10:49:15 PM
 */
public class FieldPacket {
    /*
Bytes                      Name
-----                      ----
n (Length Coded String)    catalog
n (Length Coded String)    db
n (Length Coded String)    table
n (Length Coded String)    org_table
n (Length Coded String)    name
n (Length Coded String)    org_name
1                          (filler)
2                          charsetnr
4                          length
1                          type
2                          flags
1                          decimals
2                          (filler), always 0x00
n (Length Coded Binary)    default

    */

    public static ColumnInformation columnInformationFactory(final RawPacket rawPacket) throws IOException {
        final Reader reader = new Reader(rawPacket);
        return new DrizzleColumnInformation.Builder()
                .catalog(reader.getLengthEncodedString())
                .db(reader.getLengthEncodedString())
                .table(reader.getLengthEncodedString())
                .originalTable(reader.getLengthEncodedString())
                .name(reader.getLengthEncodedString())
                .originalName(reader.getLengthEncodedString())
                .skipMe(reader.skipBytes(1))
                .charsetNumber(reader.readShort())
                .length(reader.readInt())
                .type(DrizzleType.fromServer(reader.readByte()))
                .flags(parseFlags(reader.readShort()))
                .decimals(reader.readByte())
                .skipMe(reader.skipBytes(2))
                .build();
    }

    private static Set<ColumnFlags> parseFlags(final short i) {
        final Set<ColumnFlags> retFlags = EnumSet.noneOf(ColumnFlags.class);
        for (final ColumnFlags fieldFlag : ColumnFlags.values()) {
            if ((i & fieldFlag.flag()) == fieldFlag.flag()) {
                retFlags.add(fieldFlag);
            }
        }
        return retFlags;
    }
}
