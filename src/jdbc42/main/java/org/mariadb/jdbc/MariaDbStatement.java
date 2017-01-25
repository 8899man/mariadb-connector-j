/*
MariaDB Client for Java

Copyright (c) 2012-2014 Monty Program Ab.

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

Copyright (c) 2009-2011, Marcus Eriksson, Trond Norbye, Stephane Giron

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

package org.mariadb.jdbc;

import org.mariadb.jdbc.internal.queryresults.CmdInformation;

import org.mariadb.jdbc.internal.queryresults.Results;
import org.mariadb.jdbc.internal.util.ExceptionMapper;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

/**
 * Add JDBC42 addition that are not compatible with java 7 jre.
 * (large Batch)
 */
public class MariaDbStatement extends BaseStatement implements Statement {

    /**
     * Constructor.
     * @param connection current connection
     * @param resultSetScrollType result set scroll type
     */
    public MariaDbStatement(MariaDbConnection connection, int resultSetScrollType) {
        super(connection, resultSetScrollType);
        this.results = new Results(this);

    }

    protected BatchUpdateException executeLargeBatchExceptionEpilogue(SQLException sqle, CmdInformation cmdInformation, int size) {
        sqle = handleFailoverAndTimeout(sqle);
        long[] ret;
        if (cmdInformation == null) {
            ret = new long[size];
            Arrays.fill(ret, Statement.EXECUTE_FAILED);
        } else ret = cmdInformation.getLargeUpdateCounts();

        sqle = ExceptionMapper.getException(sqle, connection, this, getQueryTimeout() != 0);
        logger.error("error executing query", sqle);

        return new BatchUpdateException(sqle.getMessage(), sqle.getSQLState(), sqle.getErrorCode(), ret, sqle);
    }


    @Override
    public long[] executeLargeBatch() throws SQLException {
        checkClose();
        int size;
        if (batchQueries == null || (size = batchQueries.size()) == 0) return new long[0];

        lock.lock();
        try {
            internalBatchExecution(size);
            return results.getCmdInformation().getLargeUpdateCounts();

        } catch (SQLException initialSqlEx) {
            throw executeLargeBatchExceptionEpilogue(initialSqlEx, results.getCmdInformation(), size);
        } finally {
            executeBatchEpilogue();
            lock.unlock();
        }
    }

}
