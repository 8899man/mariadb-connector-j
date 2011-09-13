/*
 * Drizzle-JDBC
 *
 * Copyright (c) 2009-2011, Marcus Eriksson
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided with the distribution.
 *  Neither the name of the driver nor the names of its contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.skysql.jdbc.internal.mysql;

import org.skysql.jdbc.internal.common.DataType;

import java.sql.Types;


/**
 * User: marcuse Date: Feb 23, 2009 Time: 10:42:02 PM
 */
public class MySQLType implements DataType {
    private final Type type;

    public MySQLType(final Type type) {
        this.type = type;
    }


    public int getSqlType() {
        return type.getSqlType();
    }

    public String getTypeName() {
        return type.name();
    }

    public Type getType() {
        return type;
    }


    public enum Type {
        OLDDECIMAL(java.sql.Types.DECIMAL), /* double, not used in newer code */
        TINYINT(java.sql.Types.SMALLINT),
        SMALLINT(java.sql.Types.SMALLINT),
        INTEGER(java.sql.Types.INTEGER),
        FLOAT(java.sql.Types.FLOAT),
        DOUBLE(java.sql.Types.DOUBLE),
        NULL(java.sql.Types.NULL),
        TIMESTAMP(java.sql.Types.TIMESTAMP),
        BIGINT(java.sql.Types.BIGINT),
        MEDIUMINT(java.sql.Types.INTEGER),
        DATETIME(java.sql.Types.DATE),
        DATE(java.sql.Types.DATE),
        TIME(java.sql.Types.TIME),
        YEAR(java.sql.Types.SMALLINT),
        BIT(java.sql.Types.BIT),
        VARCHAR(java.sql.Types.VARCHAR),
        DECIMAL(java.sql.Types.DECIMAL),
        TINYBLOB(java.sql.Types.VARBINARY),
        MEDIUMBLOB(java.sql.Types.VARBINARY),
        LONGBLOB(java.sql.Types.LONGVARBINARY),
        BLOB(java.sql.Types.LONGVARBINARY),
        CLOB(Types.LONGVARCHAR),
        CHAR(Types.CHAR);

        private final int sqlType;

        Type(final int sqlType) {
            this.sqlType = sqlType;
        }

        public int getSqlType() {
            return sqlType;
        }
    }

    public static MySQLType fromServer(final byte typeValue) {
        int type = (typeValue & 0xff);
        switch (type) {
            case 0:
                return new MySQLType(Type.OLDDECIMAL);
            case 1:
                return new MySQLType(Type.TINYINT);
            case 2:
                return new MySQLType(Type.SMALLINT);
            case 3:
                return new MySQLType(Type.INTEGER);
            case 4:
                return new MySQLType(Type.FLOAT);
            case 5:
                return new MySQLType(Type.DOUBLE);
            case 6:
                return new MySQLType(Type.NULL);
            case 7:
                return new MySQLType(Type.TIMESTAMP);
            case 8:
                return new MySQLType(Type.BIGINT);
            case 9:
                return new MySQLType(Type.MEDIUMINT);
            case 10:
                return new MySQLType(Type.DATE);
            case 11:
                return new MySQLType(Type.TIME);
            case 12:
                return new MySQLType(Type.DATETIME);
            case 13:
                return new MySQLType(Type.YEAR);
            case 14:
                return new MySQLType(Type.DATE);
            case 15:
                return new MySQLType(Type.VARCHAR);
            case 16:
                return new MySQLType(Type.BIT);
            case  246:
                return new MySQLType(Type.DECIMAL);
            case 247:
                /* ENUM */
                return new MySQLType(Type.VARCHAR);
            case 248:
                /* SET */
                return new MySQLType(Type.VARCHAR);
            case 249:
                return new MySQLType(Type.TINYBLOB);
            case 250:
                return new MySQLType(Type.MEDIUMBLOB);
            case 251:
                return new MySQLType(Type.LONGBLOB);
            case 252:
                return new MySQLType(Type.BLOB);
            case 253:
                return new MySQLType(Type.VARCHAR);
            case 254:
                return new MySQLType(Type.CHAR);
            case 255:
                /* Geometry actually */
                return new MySQLType(Type.BLOB);
            default:
                //throw new RuntimeException("unknown type : "+ Integer.toHexString(typeValue + 255));
                return new MySQLType(Type.VARCHAR);
        }
    }
}