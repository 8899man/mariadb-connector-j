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

package org.mariadb.jdbc.internal.protocol;

import org.mariadb.jdbc.MariaDbConnection;
import org.mariadb.jdbc.UrlParser;
import org.mariadb.jdbc.internal.packet.ComStmtLongData;
import org.mariadb.jdbc.internal.packet.ComStmtPrepare;
import org.mariadb.jdbc.internal.packet.ComStmtExecute;
import org.mariadb.jdbc.internal.packet.ComExecute;

import org.mariadb.jdbc.internal.packet.result.*;
import org.mariadb.jdbc.internal.packet.send.*;
import org.mariadb.jdbc.internal.queryresults.*;
import org.mariadb.jdbc.internal.queryresults.resultset.MariaSelectResultSet;
import org.mariadb.jdbc.internal.stream.MaxAllowedPacketException;
import org.mariadb.jdbc.internal.stream.PacketOutputStream;
import org.mariadb.jdbc.internal.util.BulkStatus;
import org.mariadb.jdbc.internal.util.ExceptionMapper;
import org.mariadb.jdbc.internal.util.dao.QueryException;
import org.mariadb.jdbc.internal.util.constant.ServerStatus;
import org.mariadb.jdbc.internal.util.buffer.Buffer;
import org.mariadb.jdbc.internal.packet.Packet;
import org.mariadb.jdbc.internal.packet.dao.parameters.ParameterHolder;
import org.mariadb.jdbc.internal.packet.dao.ColumnInformation;
import org.mariadb.jdbc.internal.MariaDbType;
import org.mariadb.jdbc.internal.util.dao.ServerPrepareResult;
import org.mariadb.jdbc.LocalInfileInterceptor;

import java.io.*;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.locks.ReentrantLock;

import static org.mariadb.jdbc.internal.util.ExceptionMapper.SqlStates.CONNECTION_EXCEPTION;
import static org.mariadb.jdbc.internal.util.ExceptionMapper.SqlStates.INTERRUPTED_EXCEPTION;
import static org.mariadb.jdbc.internal.util.ExceptionMapper.SqlStates.FEATURE_NOT_SUPPORTED;


public class AbstractQueryProtocol extends AbstractConnectProtocol implements Protocol {

    private int transactionIsolationLevel = 0;
    private InputStream localInfileInputStream;
    private int maxRows;  /* max rows returned by a statement */

    /**
     * Get a protocol instance.
     *
     * @param urlParser connection URL infos
     * @param lock      the lock for thread synchronisation
     */

    public AbstractQueryProtocol(final UrlParser urlParser, final ReentrantLock lock) {
        super(urlParser, lock);
    }

    public void executeQuery(final String sql) throws QueryException {
        executeQuery(isMasterConnection(), new SingleExecutionResult(null, 0, false, false), sql, ResultSet.TYPE_FORWARD_ONLY);
    }

    /**
     * Execute query directly to outputStream.
     *
     * @param mustExecuteOnMaster was intended to be launched on master connection
     * @param executionResult result
     * @param sql the query to executeInternal
     * @param resultSetScrollType resultSetScrollType
     * @throws QueryException exception
     */
    @Override
    public void executeQuery(boolean mustExecuteOnMaster, ExecutionResult executionResult,
                             final String sql, int resultSetScrollType) throws QueryException {
        cmdPrologue();
        try {

            ComExecute.sendDirect(writer, sql.getBytes("UTF-8"));
            getResult(executionResult, resultSetScrollType, false, true);

        } catch (QueryException queryException) {
            throw addQueryInfo(sql, queryException);
        } catch (IOException e) {
            throw new QueryException("Could not send query: " + e.getMessage(), -1, CONNECTION_EXCEPTION.getSqlState(), e);
        }

    }

    /**
     * Execute a unique clientPrepareQuery.
     *
     * @param mustExecuteOnMaster was intended to be launched on master connection
     * @param executionResult result
     * @param queryParts query part
     * @param parameters parameters
     * @param resultSetScrollType resultsetScroll type
     * @throws QueryException exception
     */
    public void executeQuery(boolean mustExecuteOnMaster, ExecutionResult executionResult,
                             final List<byte[]> queryParts, ParameterHolder[] parameters,
                             int resultSetScrollType) throws QueryException {
        cmdPrologue();
        try {

            ComExecute.send(writer, queryParts, parameters);
            getResult(executionResult, resultSetScrollType, false, true);

        } catch (QueryException queryException) {
            throwErrorWithQuery(writer.buffer, queryException);
        } catch (MaxAllowedPacketException e) {
            if (e.isMustReconnect()) connect();
            throw new QueryException("Could not send query: " + e.getMessage(), -1, INTERRUPTED_EXCEPTION.getSqlState(), e);
        } catch (IOException e) {
            throw new QueryException("Could not send query: " + e.getMessage(), -1, CONNECTION_EXCEPTION.getSqlState(), e);
        } finally {
            writer.releaseBufferIfNotLogging();
        }
    }

    /**
     * Specific execution for batch rewrite that has specific query for memory.
     *
     * @param mustExecuteOnMaster was intended to be launched on master connection
     * @param executionResult result
     * @param queryParts query part
     * @param parameters parameters
     * @param resultSetScrollType resultsetScroll type
     * @param isRewritable is rewritable flag
     * @throws QueryException exception
     */
    public void executeQuery(boolean mustExecuteOnMaster, ExecutionResult executionResult,
                             final List<byte[]> queryParts, ParameterHolder[] parameters,
                             int resultSetScrollType, boolean isRewritable) throws QueryException {
        cmdPrologue();
        try {

            ComExecute.sendRewrite(writer, queryParts, parameters);
            getResult(executionResult, resultSetScrollType, false, true);

        } catch (QueryException queryException) {
            throwErrorWithQuery(writer.buffer, queryException);
        } catch (MaxAllowedPacketException e) {
            if (e.isMustReconnect()) connect();
            throw new QueryException("Could not send query: " + e.getMessage(), -1, INTERRUPTED_EXCEPTION.getSqlState(), e);
        } catch (IOException e) {
            throw new QueryException("Could not send query: " + e.getMessage(), -1, CONNECTION_EXCEPTION.getSqlState(), e);
        } finally {
            writer.releaseBufferIfNotLogging();
        }
    }

    /**
     * Execute clientPrepareQuery batch.
     *
     * @param mustExecuteOnMaster was intended to be launched on master connection
     * @param executionResult result
     * @param queryParts query part
     * @param parametersList List of parameters
     * @param resultSetScrollType resultsetScroll type
     * @throws QueryException exception
     */
    public void executeBatchBulk(boolean mustExecuteOnMaster, ExecutionResult executionResult, final List<byte[]> queryParts,
                             final List<ParameterHolder[]> parametersList, int resultSetScrollType) throws QueryException {
        cmdPrologue();
        new AbstractBulkSend(this, writer, executionResult, queryParts, parametersList, resultSetScrollType) {

            @Override
            public void sendCmd(PacketOutputStream writer, ExecutionResult executionResult, List<byte[]> queryParts,
                                List<ParameterHolder[]> parametersList, List<String> queries, int paramCount, BulkStatus status,
                                ServerPrepareResult serverPrepareResult)
                    throws QueryException, IOException {
                ParameterHolder[] parameters = parametersList.get(status.sendCmdCounter);
                ComExecute.sendSubCmd(writer, queryParts, parameters, paramCount);
            }

            @Override
            public QueryException handleResultException(QueryException qex, ExecutionResult executionResult, List<byte[]> queryParts,
                                                        List<ParameterHolder[]> parametersList, List<String> queries, int currentCounter,
                                                        BulkStatus status, int paramCount, ServerPrepareResult serverPrepareResult)
                    throws QueryException {
                ParameterHolder[] parameters = parametersList.get(currentCounter + status.sendCmdCounter - status.sendSubCmdCounter);
                String sql = new String(queryParts.get(0));
                for (int i = 0; i < paramCount; i++) sql += parameters[i].toString() + new String(queryParts.get(i + 1));
                return addQueryInfo(sql, qex);
            }

            @Override
            public int getParamCount() {
                return queryParts.size() - 1;
            }

            @Override
            public int getTotalExecutionNumber() {
                return parametersList.size();
            }

        }.executeBatch(false);

    }

    /**
     * Execute list of queries not rewritable.
     *
     * @param mustExecuteOnMaster was intended to be launched on master connection
     * @param queries list of queries
     * @param resultSetScrollType resultSetScrollType
     * @throws QueryException exception
     */
    public void executeBatch(boolean mustExecuteOnMaster, ExecutionResult executionResult, final List<String> queries, int resultSetScrollType)
            throws QueryException {
        cmdPrologue();

        new AbstractBulkSend(this, writer, executionResult, queries, resultSetScrollType) {

            @Override
            public void sendCmd(PacketOutputStream writer, ExecutionResult executionResult, List<byte[]> queryParts,
                                List<ParameterHolder[]> parametersList, List<String> queries, int paramCount, BulkStatus status,
                                ServerPrepareResult serverPrepareResult)
                    throws QueryException, IOException {
                String sql = queries.get(status.sendCmdCounter);
                ComExecute.sendDirect(writer, sql.getBytes("UTF-8"));
            }

            @Override
            public QueryException handleResultException(QueryException qex, ExecutionResult executionResult, List<byte[]> queryParts,
                                                        List<ParameterHolder[]> parametersList, List<String> queries, int currentCounter,
                                                        BulkStatus status, int paramCount, ServerPrepareResult serverPrepareResult)
                    throws QueryException {
                String sql = queries.get(currentCounter + status.sendCmdCounter - status.sendSubCmdCounter);
                return addQueryInfo(sql, qex);
            }

            @Override
            public int getParamCount() {
                return -1;
            }

            @Override
            public int getTotalExecutionNumber() {
                return queries.size();
            }

        }.executeBatch(false);

    }

    /**
     * Write to buffer current saved cmd.
     * @param status bulk status infos
     * @throws QueryException if these command only is bigger than max_allowed_packet.
     */
    public void writeSavedSubCmd(BulkStatus status) throws QueryException {
        if (status.lastSubCommand != null) {
            writer.write(status.lastSubCommand, 0, status.lastSubCommand.length);
            if (!writer.checkCurrentPacketAllowedSize()) {
                //one sub-command is bigger than max packet Size
                throw new QueryException("max_allowed_packet=" + writer.getMaxAllowedPacket() + ". stream size "
                        + status.lastSubCommand.length + " is > to max_allowed_packet");
            }

            status.lastSubCommand = null;
            status.sendSubCmdCounter++;
            status.sendCmdCounter++;
        }
    }


    /**
     * Prepare query on server side.
     * Will permit to know the parameter number of the query, and permit to send only the data on next results.
     *
     * For failover, two additional information are in the resultset object :
     * - current connection : Since server maintain a state of this prepare statement, all query will be executed on this particular connection.
     * - executeOnMaster : state of current connection when creating this prepareStatement (if was on master, will only be executed on master.
     * If was on a slave, can be execute temporary on master, but we keep this flag,
     * so when a slave is connected back to relaunch this query on slave)
     *
     * @param sql the query
     * @param executeOnMaster state of current connection when creating this prepareStatement
     * @return a ServerPrepareResult object that contain prepare result information.
     * @throws QueryException if any error occur on connection.
     */
    @Override
    public ServerPrepareResult prepare(String sql, boolean executeOnMaster) throws QueryException {
        cmdPrologue();
        lock.lock();
        try {
            if (options.cachePrepStmts) {
                String key = new StringBuilder(database).append("-").append(sql).toString();
                ServerPrepareResult pr = serverPrepareStatementCache.get(key);
                if (pr != null && pr.incrementShareCounter()) {
                    return pr;
                }
            }
            writer.startPacket(0, true);
            ComStmtPrepare comStmtPrepare = new ComStmtPrepare(this, sql);
            comStmtPrepare.send(writer);
            return comStmtPrepare.read(packetFetcher);
        } catch (IOException e) {
            throw new QueryException(e.getMessage(), -1, CONNECTION_EXCEPTION.getSqlState(),
                    e);
        } finally {
            lock.unlock();
        }
    }


    /**
     * Specific execution for batch allowMultipleQueries that has specific query for memory.
     *
     * @param mustExecuteOnMaster was intended to be launched on master connection
     * @param executionResult result
     * @param queryParts query part
     * @param parameterList parameters
     * @param resultSetScrollType resultsetScroll type
     * @throws QueryException exception
     */
    public void executeBatchMultiple(boolean mustExecuteOnMaster, ExecutionResult executionResult,
                                     final List<byte[]> queryParts, List<ParameterHolder[]> parameterList,
                                     int resultSetScrollType) throws QueryException {
        cmdPrologue();

        ParameterHolder[] parameters;
        int paramCount = queryParts.size() - 1;
        int currentIndex = 0;
        int totalParameterList = parameterList.size();

        try {

            do {
                parameters = parameterList.get(currentIndex++);
                byte[] firstPart = queryParts.get(0);

                //calculate static length for packet splitting
                int staticLength = 1;
                for (int i = 0; i < queryParts.size(); i++) staticLength += queryParts.get(i).length;

                currentIndex = ComExecute.sendMultiple(writer, queryParts, parameters, firstPart, currentIndex, paramCount, parameterList,
                        staticLength);
                getResult(executionResult, resultSetScrollType, false, true);
            } while (currentIndex < totalParameterList);

        } catch (QueryException queryException) {
            throwErrorWithQuery(writer.buffer, queryException);
        } catch (MaxAllowedPacketException e) {
            if (e.isMustReconnect()) connect();
            throw new QueryException("Could not send query: " + e.getMessage(), -1, INTERRUPTED_EXCEPTION.getSqlState(), e);
        } catch (IOException e) {
            throw new QueryException("Could not send query: " + e.getMessage(), -1, CONNECTION_EXCEPTION.getSqlState(), e);
        } finally {
            writer.releaseBufferIfNotLogging();
        }

    }

    /**
     * Execute list of queries.
     * This method is used when using text batch statement and using rewriting (allowMultiQueries || rewriteBatchedStatements).
     * queries will be send to server according to max_allowed_packet size.
     *
     * @param mustExecuteOnMaster was intended to be launched on master connection
     * @param queries list of queryes
     * @param resultSetScrollType resultSetScrollType
     * @throws QueryException exception
     */
    public void executeBatchMultiple(boolean mustExecuteOnMaster, ExecutionResult executionResult, List<String> queries, int resultSetScrollType)
            throws QueryException {
        cmdPrologue();
        String firstSql = null;
        int currentIndex = 0;
        int totalQueries = queries.size();
        QueryException exception = null;
        do {

            try {

                firstSql = queries.get(currentIndex++);
                if (totalQueries == 1) {
                    ComExecute.sendDirect(writer, firstSql.getBytes("UTF-8"));
                } else {
                    currentIndex = ComExecute.sendMultiple(writer, firstSql, queries, currentIndex);
                }
                getResult(executionResult, resultSetScrollType, false, true);

            } catch (QueryException queryException) {
                addQueryInfo(firstSql, queryException);
                if (!getOptions().continueBatchOnError) throw queryException;
                if (exception == null) exception = queryException;
            } catch (MaxAllowedPacketException e) {
                if (e.isMustReconnect()) connect();
                throw new QueryException("Could not send query: " + e.getMessage(), -1, INTERRUPTED_EXCEPTION.getSqlState(), e);
            } catch (IOException e) {
                throw new QueryException("Could not send query: " + e.getMessage(), -1, CONNECTION_EXCEPTION.getSqlState(), e);
            } finally {
                writer.releaseBufferIfNotLogging();
            }

        } while (currentIndex < totalQueries);

        if (exception != null) throw exception;
    }

    /**
     * Specific execution for batch rewrite that has specific query for memory.
     *
     * @param mustExecuteOnMaster was intended to be launched on master connection
     * @param executionResult result
     * @param queryParts query part
     * @param parameterList parameters
     * @param resultSetScrollType resultsetScroll type
     * @param rewriteValues is rewritable flag
     * @throws QueryException exception
     */
    public void executeBatchRewrite(boolean mustExecuteOnMaster, ExecutionResult executionResult,
                                    final List<byte[]> queryParts, List<ParameterHolder[]> parameterList,
                                    int resultSetScrollType, boolean rewriteValues) throws QueryException {
        cmdPrologue();
        ParameterHolder[] parameters;
        int paramCount = queryParts.size() - 3;
        int currentIndex = 0;
        int totalParameterList = parameterList.size();

        try {

            do {
                parameters = parameterList.get(currentIndex++);
                currentIndex = ComExecute.sendRewrite(writer, queryParts, parameters, currentIndex, paramCount, parameterList, rewriteValues);
                getResult(executionResult, resultSetScrollType, false, true);

            } while (currentIndex < totalParameterList);

        } catch (QueryException queryException) {
            throwErrorWithQuery(writer.buffer, queryException);
        } catch (MaxAllowedPacketException e) {
            if (e.isMustReconnect()) connect();
            throw new QueryException("Could not send query: " + e.getMessage(), -1, INTERRUPTED_EXCEPTION.getSqlState(), e);
        } catch (IOException e) {
            throw new QueryException("Could not send query: " + e.getMessage(), -1, CONNECTION_EXCEPTION.getSqlState(), e);
        } finally {
            writer.releaseBufferIfNotLogging();
        }
    }

    /**
     * Prepare query if needed, and execute send all executes in one packet (or more if &gt; max_allowed_packet).
     *
     * @param mustExecuteOnMaster must normally be executed on master connection
     * @param executionResult results
     * @param sql query
     * @param parameters parameters
     * @param resultSetScrollType  result scroll type
     * @return server prepare result
     * @throws QueryException if server return error, of connection fail
     */
    @Override
    public ServerPrepareResult prepareAndExecuteComMulti(boolean mustExecuteOnMaster, ExecutionResult executionResult,
                                                         String sql, ParameterHolder[] parameters,
                                                         int resultSetScrollType) throws QueryException {
        cmdPrologue();
        BulkStatus status = new BulkStatus();

        try {
            int parameterNb = parameters.length;
            ServerPrepareResult serverPrepareResult = getPrepareResultFromCacheIfNeeded(null, sql);
            int statementId = (serverPrepareResult == null) ? -1 : serverPrepareResult.getStatementId();
            int subCmdEndPosition;
            int sendLongDataCounter = 0;
            boolean isParamSubCmd = true;
            QueryException exception = null;
            boolean containCmd;

            //com multi init packet
            do {
                writer.startPacket(0, true);
                writer.buffer.put((byte) 0xfe);
                containCmd = false;
                //add prepare sub-command
                if (statementId == -1) new ComStmtPrepare(this, sql).sendComMulti(writer);

                if (status.lastSubCommand != null) {
                    writeSavedSubCmd(status);
                    if (isParamSubCmd) sendLongDataCounter++;
                    containCmd = true;
                }

                //send long data
                if (isParamSubCmd) {
                    for (int i = sendLongDataCounter; i < parameterNb; i++) {
                        if (parameters[i].isLongData()) {

                            //add execute sub command
                            subCmdEndPosition = ComStmtLongData.sendComMulti(writer, statementId, (short) i, parameters[i], status);

                            if (!writer.checkCurrentPacketAllowedSize()) {
                                status.lastSubCommand = new byte[subCmdEndPosition - status.subCmdInitialPosition];
                                //packet size > max_allowed_size -> need to send packet now without last command, and recreate new packet for
                                //additional data.
                                System.arraycopy(writer.buffer.array(), status.subCmdInitialPosition, status.lastSubCommand, 0,
                                        subCmdEndPosition - status.subCmdInitialPosition);
                                writer.buffer.position(status.subCmdInitialPosition);
                                if (isParamSubCmd) writer.releaseBufferIfNotLogging();
                                isParamSubCmd = true;
                                continue;
                            }
                            containCmd = true;
                        }
                        sendLongDataCounter++;
                    }
                }

                if (status.lastSubCommand == null) {
                    isParamSubCmd = false;

                    //add execute sub command
                    subCmdEndPosition = ComStmtExecute.sendComMulti(statementId, parameters, parameterNb, new MariaDbType[parameterNb], writer,
                            status);

                    if (!writer.checkCurrentPacketAllowedSize()) {
                        status.lastSubCommand = new byte[subCmdEndPosition - status.subCmdInitialPosition];
                        //packet size > max_allowed_size -> need to send packet now without last command, and recreate new packet for additional data.
                        System.arraycopy(writer.buffer.array(), status.subCmdInitialPosition, status.lastSubCommand, 0,
                                subCmdEndPosition - status.subCmdInitialPosition);
                        writer.buffer.position(status.subCmdInitialPosition);
                        continue;
                    }
                    containCmd = true;
                }

                if (containCmd) writer.finishPacketWithoutRelease();

                try {
                    if (statementId == -1) {
                        serverPrepareResult = new ComStmtPrepare(this, sql).read(packetFetcher);
                        if (serverPrepareResult != null) statementId = serverPrepareResult.getStatementId();
                    }
                } catch (QueryException qex) {
                    exception = qex;
                }

                try {
                    if (!isParamSubCmd) getResult(executionResult, resultSetScrollType, true, true);
                } catch (QueryException qex) {
                    if (exception == null) exception = throwErrorWithQuery(parameters, qex, serverPrepareResult);
                }

                if (exception != null) throw exception;
            } while (sendLongDataCounter < parameterNb || status.lastSubCommand != null);

            if (exception != null) throw exception;
            return serverPrepareResult;
        } catch (IOException e) {
            throw new QueryException(e.getMessage(), -1, CONNECTION_EXCEPTION.getSqlState(), e);
        } finally {
            writer.releaseBufferIfNotLogging();
        }
    }

    /**
     * Execute Prepare if needed, and execute COM_STMT_EXECUTE queries in batch using COM_MULTI.
     *
     * @param mustExecuteOnMaster must normally be executed on master connection
     * @param serverPrepareResult prepare result. can be null if not prepared.
     * @param executionResult execution results
     * @param sql sql query if needed to be prepared
     * @param parametersList parameter list
     * @param resultSetScrollType result scroll type
     * @return Prepare result
     * @throws QueryException if parameter error or connection error occur.
     */
    public ServerPrepareResult prepareAndExecutesComMulti(boolean mustExecuteOnMaster, ServerPrepareResult serverPrepareResult,
                                                          ExecutionResult executionResult, String sql,
                                                          final List<ParameterHolder[]> parametersList, int resultSetScrollType)
            throws QueryException {
        cmdPrologue();
        return new AbstractBulkSend(this, writer, executionResult, serverPrepareResult, parametersList, resultSetScrollType, true, sql) {
            @Override
            public void sendCmd(PacketOutputStream writer, ExecutionResult executionResult, List<byte[]> queryParts,
                                List<ParameterHolder[]> parametersList, List<String> queries, int paramCount, BulkStatus status,
                                ServerPrepareResult serverPrepareResult)
                    throws QueryException, IOException {
                ParameterHolder[] parameters = parametersList.get(status.sendCmdCounter);
                ComStmtExecute.sendComMulti(statementId, parameters, parametersList.get(0).length, parameterTypeHeader, writer, status);
            }

            @Override
            public QueryException handleResultException(QueryException qex, ExecutionResult executionResult, List<byte[]> queryParts,
                                                        List<ParameterHolder[]> parametersList, List<String> queries, int currentCounter,
                                                        BulkStatus status, int paramCount, ServerPrepareResult serverPrepareResult)
                    throws QueryException {
                return throwErrorWithQuery(parametersList, qex, serverPrepareResult);
            }

            @Override
            public int getParamCount() {
                return getServerPrepareResult() == null ? parametersList.get(0).length : getServerPrepareResult().getParameters().length;
            }

            @Override
            public int getTotalExecutionNumber() {
                return parametersList.size();
            }

        }.executeBatch(true);

    }

    /**
     * Execute a query that is already prepared.
     * @param mustExecuteOnMaster must execute on master
     * @param serverPrepareResult prepare result
     * @param executionResult execution result
     * @param parameters parameters
     * @param resultSetScrollType scroll type.
     * @throws QueryException exception
     */
    @Override
    public void executePreparedQuery(boolean mustExecuteOnMaster, ServerPrepareResult serverPrepareResult, ExecutionResult executionResult,
                                     ParameterHolder[] parameters, int resultSetScrollType)
            throws QueryException {
        cmdPrologue();
        try {
            int parameterCount = serverPrepareResult.getParameters().length;
            //send binary data in a separate stream
            for (int i = 0; i < parameterCount; i++) {
                if (parameters[i].isLongData()) {
                    new ComStmtLongData().send(writer, serverPrepareResult.getStatementId(), (short) i, parameters[i]);
                }
            }
            //send execute query
            new ComStmtExecute(serverPrepareResult.getStatementId(), parameters,
                    parameterCount, serverPrepareResult.getParameterTypeHeader())
                    .send(writer);
            getResult(executionResult, resultSetScrollType, true, true);

        } catch (QueryException qex) {
            throw throwErrorWithQuery(parameters, qex, serverPrepareResult);
        } catch (MaxAllowedPacketException e) {
            if (e.isMustReconnect()) connect();
            throw new QueryException("Could not send query: " + e.getMessage(), -1, INTERRUPTED_EXCEPTION.getSqlState(), e);
        } catch (IOException e) {
            throw new QueryException("Could not send query: " + e.getMessage(), -1, CONNECTION_EXCEPTION.getSqlState(), e);
        }
    }

    /**
     * Execute queries already prepared.
     * @param mustExecuteOnMaster must execute on master
     * @param serverPrepareResult prepare result
     * @param executionResult execution result
     * @param parametersList parameters
     * @param resultSetScrollType scroll type.
     * @throws QueryException exception
     */
    @Override
    public void executePreparedQuery(boolean mustExecuteOnMaster, final ServerPrepareResult serverPrepareResult, ExecutionResult executionResult,
                                     final List<ParameterHolder[]> parametersList, int resultSetScrollType)
            throws QueryException {
        cmdPrologue();
        new AbstractBulkSend(this, writer, executionResult, serverPrepareResult, parametersList, resultSetScrollType, false, null) {
            @Override
            public void sendCmd(PacketOutputStream writer, ExecutionResult executionResult, List<byte[]> queryParts,
                                List<ParameterHolder[]> parametersList, List<String> queries, int paramCount, BulkStatus status,
                                ServerPrepareResult serverPrepareResult)
                    throws QueryException, IOException {
                ParameterHolder[] parameters = parametersList.get(status.sendCmdCounter);
                ComStmtExecute.writeCmd(serverPrepareResult.getStatementId(), parameters,
                        paramCount, serverPrepareResult.getParameterTypeHeader(), writer);
            }

            @Override
            public QueryException handleResultException(QueryException qex, ExecutionResult executionResult, List<byte[]> queryParts,
                                                        List<ParameterHolder[]> parametersList, List<String> queries, int currentCounter,
                                                        BulkStatus status, int paramCount, ServerPrepareResult serverPrepareResult)
                    throws QueryException {
                return throwErrorWithQuery(parametersList.get(currentCounter + status.sendCmdCounter - status.sendSubCmdCounter), qex,
                        serverPrepareResult);
            }

            @Override
            public int getParamCount() {
                return serverPrepareResult.getParameters().length;
            }

            @Override
            public int getTotalExecutionNumber() {
                return parametersList.size();
            }


        }.executeBatch(false);

    }


    /**
     * Rollback transaction.
     */
    public void rollback() throws QueryException {
        cmdPrologue();
        lock.lock();
        try {
            if (inTransaction()) {
                executeQuery("ROLLBACK");
            }
        } catch (Exception e) {
            /* eat exception */
        } finally {
            lock.unlock();
        }
    }

    /**
     * Force release of prepare statement that are not used.
     * This method will be call when adding a new preparestatement in cache, so the packet can be send to server without
     * problem.
     *
     * @param statementId prepared statement Id to remove.
     * @throws QueryException if connection exception.
     */
    public void forceReleasePrepareStatement(int statementId) throws QueryException {
        cmdPrologue();
        lock.lock();
        try {
            checkClose();
            final SendClosePrepareStatementPacket packet = new SendClosePrepareStatementPacket(statementId);
            try {
                packet.send(writer);
            } catch (IOException e) {
                throw new QueryException("Could not send query: " + e.getMessage(), -1, CONNECTION_EXCEPTION.getSqlState(), e);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean ping() throws QueryException {
        cmdPrologue();
        lock.lock();
        try {
            final SendPingPacket pingPacket = new SendPingPacket();
            try {
                pingPacket.send(writer);
                Buffer buffer = packetFetcher.getReusableBuffer();
                return buffer.getByteAt(0) == Packet.OK;
            } catch (IOException e) {
                throw new QueryException("Could not ping: " + e.getMessage(), -1, CONNECTION_EXCEPTION.getSqlState(), e);
            }
        } finally {
            lock.unlock();
        }
    }


    @Override
    public void setCatalog(final String database) throws QueryException {
        cmdPrologue();
        lock.lock();
        try {
            final SendChangeDbPacket packet = new SendChangeDbPacket(database);
            packet.send(writer);
            final Buffer buffer = packetFetcher.getReusableBuffer();
            if (buffer.getByteAt(0) == Packet.ERROR) {
                final ErrorPacket ep = new ErrorPacket(buffer);
                throw new QueryException("Could not select database '" + database + "' : " + ep.getMessage(),
                        ep.getErrorNumber(), ep.getSqlState());
            }
            this.database = database;
        } catch (IOException e) {
            throw new QueryException("Could not select database '" + database + "' :" + e.getMessage(), -1, CONNECTION_EXCEPTION.getSqlState(), e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cancels the current query - clones the current protocol and executes a query using the new connection.
     *
     * @throws QueryException never thrown
     * @throws IOException    if Host is not responding
     */
    @Override
    public void cancelCurrentQuery() throws QueryException, IOException {
        MasterProtocol copiedProtocol = new MasterProtocol(urlParser, new ReentrantLock());
        copiedProtocol.setHostAddress(getHostAddress());
        copiedProtocol.connect();
        //no lock, because there is already a query running that possessed the lock.
        copiedProtocol.executeQuery("KILL QUERY " + serverThreadId);
        copiedProtocol.close();
    }

    private void sendLocalFile(ExecutionResult executionResult, String fileName) throws IOException, QueryException {
        // Server request the local file (LOCAL DATA LOCAL INFILE)
        // We do accept general URLs, too. If the localInfileStream is
        // set, use that.
        int seq = 2;
        InputStream is;
        writer.setCompressSeqNo(2);
        if (localInfileInputStream == null) {

            if (!getUrlParser().getOptions().allowLocalInfile) {
                writer.writeEmptyPacket(seq++);
                packetFetcher.getReusableBuffer();
                throw new QueryException(
                        "Usage of LOCAL INFILE is disabled. To use it enable it via the connection property allowLocalInfile=true",
                        -1, FEATURE_NOT_SUPPORTED.getSqlState());
            }

            //validate all defined interceptors
            ServiceLoader<LocalInfileInterceptor> loader = ServiceLoader.load(LocalInfileInterceptor.class);
            for (LocalInfileInterceptor interceptor : loader) {
                if (!interceptor.validate(fileName)) {
                    writer.writeEmptyPacket(seq++);
                    packetFetcher.getReusableBuffer();
                    throw new QueryException("LOCAL DATA LOCAL INFILE request to send local file named \""
                            + fileName + "\" not validated by interceptor \"" + interceptor.getClass().getName()
                            + "\"");
                }
            }

            try {
                URL url = new URL(fileName);
                is = url.openStream();
            } catch (IOException ioe) {
                try {
                    is = new FileInputStream(fileName);
                } catch (FileNotFoundException f) {
                    writer.writeEmptyPacket(seq++);
                    packetFetcher.getReusableBuffer();
                    throw new QueryException("Could not send file : " + f.getMessage(), -1, "22000", f);
                }
            }
        } else {
            is = localInfileInputStream;
            localInfileInputStream = null;
        }
        writer.sendFile(is, seq);
        is.close();
        getResult(executionResult, ResultSet.TYPE_FORWARD_ONLY, false, true);
    }

    private ServerPrepareResult getPrepareResultFromCacheIfNeeded(ServerPrepareResult serverPrepareResult, String sql)
            throws UnsupportedEncodingException {
        if (serverPrepareResult == null) {
            if (options.cachePrepStmts) {
                String key = new StringBuilder(database).append("-").append(sql).toString();
                serverPrepareResult = serverPrepareStatementCache.get(key);
                if (serverPrepareResult != null && !serverPrepareResult.incrementShareCounter()) {
                    //in cache but been de-allocated
                    return null;
                }
            }
        }
        return serverPrepareResult;
    }

    @Override
    public boolean getAutocommit() {
        lock.lock();
        try {
            return ((serverStatus & ServerStatus.AUTOCOMMIT) != 0);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean inTransaction() {
        return ((serverStatus & ServerStatus.IN_TRANSACTION) != 0);
    }


    @Override
    public boolean hasMoreResults() {
        return moreResults;
    }

    public void closeExplicit() {
        this.explicitClosed = true;
        close();
    }


    /**
     * Deallocate prepare statement if not used anymore.
     * @param serverPrepareResult allocation result
     * @throws QueryException if deallocation failed.
     */
    @Override
    public void releasePrepareStatement(ServerPrepareResult serverPrepareResult) throws QueryException {
        //If prepared cache is enable, the ServerPrepareResult can be shared in many PrepStatement,
        //so synchronised use count indicator will be decrement.
        serverPrepareResult.decrementShareCounter();

        //deallocate from server if not cached
        if (serverPrepareResult.canBeDeallocate()) {
            forceReleasePrepareStatement(serverPrepareResult.getStatementId());
        }
    }

    @Override
    public void getMoreResults(ExecutionResult executionResult) throws QueryException {
        if (!hasMoreResults()) {
            return;
        }
        getResult(executionResult, ResultSet.TYPE_FORWARD_ONLY,
                (activeStreamingResult != null) ? activeStreamingResult.isBinaryEncoded() : moreResultsTypeBinary, false);
    }

    /**
     * Set max row retuen by a statement.
     *
     * @param max row number max value
     */
    public void setInternalMaxRows(int max) {
        if (maxRows != max) maxRows = max;
    }

    public int getMaxRows() {
        return maxRows;
    }

    @Override
    public void setMaxRows(int max) throws QueryException {
        if (maxRows != max) {
            if (max == 0) {
                executeQuery("set @@SQL_SELECT_LIMIT=DEFAULT");
            } else {
                executeQuery("set @@SQL_SELECT_LIMIT=" + max);
            }
            maxRows = max;
        }
    }

    @Override
    public void setLocalInfileInputStream(InputStream inputStream) {
        this.localInfileInputStream = inputStream;
    }

    /**
     * Returns the connection timeout in milliseconds.
     *
     * @return the connection timeout in milliseconds.
     * @throws SocketException if there is an error in the underlying protocol, such as a TCP error.
     */
    @Override
    public int getTimeout() throws SocketException {
        return this.socket.getSoTimeout();
    }

    /**
     * Sets the connection timeout.
     *
     * @param timeout the timeout, in milliseconds
     * @throws SocketException if there is an error in the underlying protocol, such as a TCP error.
     */
    @Override
    public void setTimeout(int timeout) throws SocketException {
        lock.lock();
        try {
            this.getOptions().socketTimeout = timeout;
            this.socket.setSoTimeout(timeout);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set transaction isolation.
     *
     * @param level transaction level.
     * @throws QueryException if transaction level is unknown
     */
    public void setTransactionIsolation(final int level) throws QueryException {
        cmdPrologue();
        lock.lock();
        try {
            String query = "SET SESSION TRANSACTION ISOLATION LEVEL";
            switch (level) {
                case Connection.TRANSACTION_READ_UNCOMMITTED:
                    query += " READ UNCOMMITTED";
                    break;
                case Connection.TRANSACTION_READ_COMMITTED:
                    query += " READ COMMITTED";
                    break;
                case Connection.TRANSACTION_REPEATABLE_READ:
                    query += " REPEATABLE READ";
                    break;
                case Connection.TRANSACTION_SERIALIZABLE:
                    query += " SERIALIZABLE";
                    break;
                default:
                    throw new QueryException("Unsupported transaction isolation level");
            }
            executeQuery(query);
            transactionIsolationLevel = level;
        } finally {
            lock.unlock();
        }
    }

    public int getTransactionIsolationLevel() {
        return transactionIsolationLevel;
    }

    private void checkClose() throws QueryException {
        if (!this.connected) throw new QueryException("Connection is close", 1220, "08000");
    }

    /**
     * Close active result.
     * @throws SQLException if socket error.
     */
    public void fetchActiveStreamingResult() throws SQLException {
        if (activeStreamingResult != null) {
            activeStreamingResult.fetchAllStreaming();
        }
    }

    private QueryException addQueryInfo(String sql, QueryException queryException) {
        if (getOptions().dumpQueriesOnException || queryException.getErrorCode() == 1064) {
            if (options.maxQuerySizeToLog > 0 && sql.length() > options.maxQuerySizeToLog - 3) {
                sql = sql.substring(0, options.maxQuerySizeToLog - 3) + "...";
            }
            queryException.setMessage(queryException.getMessage() + "\nQuery is : " + sql);
        }
        return queryException;
    }


    private void throwErrorWithQuery(ByteBuffer buffer, QueryException queryException) throws QueryException {
        if (getOptions().dumpQueriesOnException || queryException.getErrorCode() == 1064) {
            //log first maxQuerySizeToLog utf-8 characters
            String queryString;
            if (options.maxQuerySizeToLog == 0) {
                queryString = new String(buffer.array(), 5, buffer.limit());
            } else {
                queryString = new String(buffer.array(), 5, Math.min(buffer.limit(), (options.maxQuerySizeToLog * 3) + 5));
                if (queryString.length() > options.maxQuerySizeToLog - 3 ) {
                    queryString = queryString.substring(0, options.maxQuerySizeToLog - 3) + "...";
                }
            }
            addQueryInfo(queryString, queryException);
        }
        throw queryException;
    }

    private QueryException throwErrorWithQuery(ParameterHolder[] parameters, QueryException qex, ServerPrepareResult serverPrepareResult)
            throws QueryException {
        if (getOptions().dumpQueriesOnException || qex.getErrorCode() == 1064) {
            String sql = serverPrepareResult.getSql();
            if (serverPrepareResult.getParameters().length > 0) {
                sql += ", parameters [";
                if (parameters.length > 1) {
                    for (int i = 0; i < Math.min(parameters.length, serverPrepareResult.getParameters().length); i++) {
                        sql += parameters[i].toString() + ",";
                    }
                    sql = sql.substring(0, sql.length() - 1);
                }
                sql += "]";
            }
            if (options.maxQuerySizeToLog != 0 && sql.length() > options.maxQuerySizeToLog - 3) {
                qex.setMessage(qex.getMessage() + "\nQuery is: " + sql.substring(0, options.maxQuerySizeToLog - 3) + "...");
            } else {
                qex.setMessage(qex.getMessage() + "\nQuery is: " + sql);
            }
        }
        if (qex.getCause() instanceof SocketTimeoutException) {
            return new QueryException("Connection timed out", -1, CONNECTION_EXCEPTION.getSqlState(), qex);
        } else {
            return qex;
        }
    }

    private QueryException throwErrorWithQuery(List<ParameterHolder[]> parameterList, QueryException qex, ServerPrepareResult serverPrepareResult)
            throws QueryException {
        if (getOptions().dumpQueriesOnException || qex.getErrorCode() == 1064) {
            String querySql = serverPrepareResult.getSql();

            if (serverPrepareResult.getParameters().length > 0) {
                querySql += ", parameters ";
                for (int paramNo = 0; paramNo < parameterList.size(); paramNo++) {
                    ParameterHolder[] parameters = parameterList.get(paramNo);
                    querySql += "[";
                    if (parameters.length > 1) {
                        for (int i = 0; i < Math.min(serverPrepareResult.getParameters().length, parameters.length); i++) {
                            querySql += parameters[i].toString() + ",";
                        }
                        querySql = querySql.substring(0, querySql.length() - 1);
                    }
                    if (options.maxQuerySizeToLog > 0 && querySql.length() > options.maxQuerySizeToLog) {
                        break;
                    } else {
                        querySql += "],";
                    }
                }
                querySql = querySql.substring(0, querySql.length() - 1);
            }
            if (options.maxQuerySizeToLog != 0 && querySql.length() > options.maxQuerySizeToLog - 3) {
                qex.setMessage(qex.getMessage() + "\nQuery is: " + querySql.substring(0, options.maxQuerySizeToLog - 3) + "...");
            } else {
                qex.setMessage(qex.getMessage() + "\nQuery is: " + querySql);
            }
        }
        return qex;
    }


    @Override
    public ExecutionResult getResult(ExecutionResult executionResult, int resultSetScrollType, boolean binaryProtocol, boolean loadAllResults)
            throws QueryException {
        Buffer buffer;
        try {
            buffer = packetFetcher.getReusableBuffer();
        } catch (IOException e) {
            try {
                if (writer != null) {
                    writer.writeEmptyPacket(packetFetcher.getLastPacketSeq() + 1);
                    packetFetcher.getReusableBuffer();
                }
            } catch (IOException ee) { }
            throw new QueryException("Could not read resultset: " + e.getMessage(), -1, CONNECTION_EXCEPTION.getSqlState(), e);
        }

        switch (buffer.getByteAt(0)) {

            //*********************************************************************************************************
            //* OK response
            //*********************************************************************************************************
            case Packet.OK:
                buffer.skipByte(); //fieldCount
                final long affectedRows = buffer.getLengthEncodedBinary();
                final long insertId = buffer.getLengthEncodedBinary();
                serverStatus = buffer.readShort();
                this.hasWarnings = (buffer.readShort() > 0);
                this.moreResults = ((serverStatus & ServerStatus.MORE_RESULTS_EXISTS) != 0);

                if (!loadAllResults) {
                    return new SingleExecutionResult(executionResult.getStatement(), 0, true, false, affectedRows, insertId);
                }

                executionResult.addStats(affectedRows, insertId, moreResults);
                while (moreResults && loadAllResults && executionResult.getFetchSize() == 0) {
                    //load additional results
                    executionResult.getCachedExecutionResults().add(getResult(executionResult, ResultSet.TYPE_FORWARD_ONLY,
                            (activeStreamingResult != null) ? activeStreamingResult.isBinaryEncoded() : moreResultsTypeBinary, false));
                }
                break;

            //*********************************************************************************************************
            //* ERROR response
            //*********************************************************************************************************
            case Packet.ERROR:
                //Error packet
                this.moreResults = false;
                this.hasWarnings = false;
                buffer.skipByte();
                int errorNumber = buffer.readShort();
                String message;
                String sqlState;
                if (buffer.readByte() == '#') {
                    sqlState = new String(buffer.readRawBytes(5));
                    message = buffer.readString(StandardCharsets.UTF_8);
                } else {
                    // Pre-4.1 message, still can be output in newer versions (e.g with 'Too many connections')
                    message = new String(buffer.buf, buffer.position, buffer.limit, StandardCharsets.UTF_8);
                    sqlState = "HY000";
                }
                executionResult.addStats(Statement.EXECUTE_FAILED, Statement.SUCCESS_NO_INFO, moreResults);
                throw new QueryException(message, errorNumber, sqlState);

            //*********************************************************************************************************
            //* LOCAL INFILE response
            //*********************************************************************************************************
            case Packet.LOCAL_INFILE:
                //Send fileName
                buffer.getLengthEncodedBinary(); //field count
                String fileName = buffer.readString(StandardCharsets.UTF_8);
                try {
                    sendLocalFile(executionResult, fileName);
                } catch (IOException e) {
                    try {
                        if (writer != null) {
                            writer.writeEmptyPacket(packetFetcher.getLastPacketSeq() + 1);
                            packetFetcher.getReusableBuffer();
                        }
                    } catch (IOException ee) { }
                    throw new QueryException("Could not read resultset: " + e.getMessage(), -1, CONNECTION_EXCEPTION.getSqlState(), e);
                }
                break;

            //*********************************************************************************************************
            //* EOF response
            //*********************************************************************************************************
            case Packet.EOF:
                if (buffer.remaining() < 9) {
                    throw new QueryException("Could not parse result", (short) -1, INTERRUPTED_EXCEPTION.getSqlState());
                }
                //no break, because if size >=9 it's a resultset

            //*********************************************************************************************************
            //* RESULTSET
            //*********************************************************************************************************
            default:
                this.hasWarnings = false;
                long fieldCount = buffer.getLengthEncodedBinary();

                try {
                    boolean callableResult = false;

                    //read columns infos
                    ColumnInformation[] ci = new ColumnInformation[(int) fieldCount];
                    for (int i = 0; i < fieldCount; i++) {
                        ci[i] = new ColumnInformation(packetFetcher.getPacket());
                    }

                    //read EOF packet
                    Buffer bufferEof = packetFetcher.getReusableBuffer();
                    if (bufferEof.getByteAt(0) != Packet.EOF) {
                        throw new QueryException("Packets out of order when reading field packets, expected was EOF stream. "
                                + "Packet contents (hex) = " + MasterProtocol.hexdump(bufferEof.buf, 0));
                    } else if (executionResult.isCanHaveCallableResultset() || !isMariaServer) {
                        //Identify if this is a "callable OUT packet" (callableResult=true)
                        //needed because :
                        // - will permit for callableStatement to identify the output result packet
                        // - after "OUT packet", a OK packet is send, but mysql send the "OUT packet with a bad "more result flag",
                        //   so need to check that this is a "OUT packet" to known there is another packet.
                        EndOfFilePacket endOfFilePacket = new EndOfFilePacket(bufferEof);
                        callableResult = (endOfFilePacket.getStatusFlags() & ServerStatus.PS_OUT_PARAMETERS) != 0;
                    }

                    //fetch Select result
                    MariaSelectResultSet mariaSelectResultset = new MariaSelectResultSet(ci, executionResult.getStatement(), this, packetFetcher,
                            binaryProtocol, resultSetScrollType, executionResult.getFetchSize(), callableResult);
                    mariaSelectResultset.initFetch();

                    if (!executionResult.isSelectPossible()) {
                        while (moreResults && loadAllResults && executionResult.getFetchSize() == 0) {
                            try {
                                getResult(executionResult, ResultSet.TYPE_FORWARD_ONLY,
                                        (activeStreamingResult != null) ? activeStreamingResult.isBinaryEncoded() : moreResultsTypeBinary, false);
                            } catch (QueryException e) { }
                        }
                        throw new QueryException("Select command are not permitted via executeBatch() command");
                    }
                    if (!loadAllResults) return new SingleExecutionResult(executionResult.getStatement(), 0, true, false, mariaSelectResultset);

                    executionResult.addResultSet(mariaSelectResultset, moreResults);

                    //load additional results
                    while (moreResults && loadAllResults && executionResult.getFetchSize() == 0) {
                        executionResult.getCachedExecutionResults().add(getResult(executionResult, ResultSet.TYPE_FORWARD_ONLY,
                                (activeStreamingResult != null) ? activeStreamingResult.isBinaryEncoded() : moreResultsTypeBinary, false));
                    }

                } catch (IOException e) {
                    throw new QueryException("Could not read result set: " + e.getMessage(), -1, CONNECTION_EXCEPTION.getSqlState(), e);
                }
                break;

        }
        return executionResult;
    }

    public void prologProxy(ServerPrepareResult serverPrepareResult, ExecutionResult executionResult, int maxRows, boolean hasProxy,
                            MariaDbConnection connection, Statement statement) throws SQLException {
        prolog(executionResult, maxRows, hasProxy, connection, statement);
    }

    /**
     * Preparation before command.
     * @param executionResult result
     * @param maxRows query max rows
     * @param hasProxy has proxy
     * @param connection current connection
     * @param statement current statement
     * @throws SQLException if any error occur.
     */
    public void prolog(ExecutionResult executionResult, int maxRows, boolean hasProxy, MariaDbConnection connection, Statement statement)
            throws SQLException {
        if (explicitClosed) {
            throw new SQLException("execute() is called on closed connection");
        }
        //old failover handling
        if (!hasProxy) {
            if (shouldReconnectWithoutProxy()) {
                try {
                    connectWithoutProxy();
                } catch (QueryException qe) {
                    ExceptionMapper.throwException(qe, connection, statement);
                }
            }
        }

        try {
            setMaxRows(maxRows);
            fetchActiveStreamingResult();
            while (hasMoreResults()) {
                getMoreResults(executionResult);
            }
        } catch (QueryException qe) {
            ExceptionMapper.throwException(qe, connection, statement);
        }

        connection.reenableWarnings();
    }

    public ServerPrepareResult addPrepareInCache(String key, ServerPrepareResult serverPrepareResult) {
        return serverPrepareStatementCache.put(key, serverPrepareResult);
    }

    private void cmdPrologue() throws QueryException {
        if (activeStreamingResult != null) {
            throw new QueryException("There is an open result set on the current connection, which must be "
                    + "closed prior to executing a query");
        }
        this.moreResults = false;
        if (!this.connected) throw new QueryException("Connection is close", 1220, "08000");
    }

    /**
     * Hexdump.
     *
     * @param buffer byte array
     * @param offset offset
     * @return String
     */
    public static String hexdump(byte[] buffer, int offset) {
        StringBuffer dump = new StringBuffer();
        if ((buffer.length - offset) > 0) {
            dump.append(String.format("%02x", buffer[offset]));
            for (int i = offset + 1; i < buffer.length; i++) {
                dump.append(String.format("%02x", buffer[i]));
            }
        }
        return dump.toString();
    }

}
