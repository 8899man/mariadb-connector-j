/*
 * Drizzle JDBC
 *
 * Copyright (C) 2009 Marcus Eriksson (krummas@gmail.com)
 * All rights reserved.
 *
 * Use and distribution licensed under the BSD license.
 */

package org.drizzle.jdbc.internal.mysql;

import org.drizzle.jdbc.internal.common.packet.*;
import org.drizzle.jdbc.internal.common.packet.commands.*;
import org.drizzle.jdbc.internal.common.packet.buffer.ReadUtil;
import org.drizzle.jdbc.internal.common.query.Query;
import org.drizzle.jdbc.internal.common.query.DrizzleQuery;
import org.drizzle.jdbc.internal.common.queryresults.*;
import org.drizzle.jdbc.internal.common.*;
import org.drizzle.jdbc.internal.mysql.packet.commands.MySQLClientAuthPacket;
import org.drizzle.jdbc.internal.mysql.packet.commands.MySQLPingPacket;
import org.drizzle.jdbc.internal.mysql.packet.commands.MySQLBinlogDumpPacket;
import org.drizzle.jdbc.internal.mysql.packet.MySQLGreetingReadPacket;
import org.drizzle.jdbc.internal.SQLExceptionMapper;

import javax.net.SocketFactory;
import java.net.Socket;
import java.io.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * TODO: refactor, clean up
 * TODO: when should i read up the resultset?
 * TODO: thread safety?
 * TODO: exception handling
 * User: marcuse
 * Date: Jan 14, 2009
 * Time: 4:06:26 PM
 */
public class MySQLProtocol implements Protocol {
    private final static Logger log = Logger.getLogger(MySQLProtocol.class.getName());
    private boolean connected=false;
    private final Socket socket;
    private final BufferedOutputStream writer;
    private final String version;
    private boolean readOnly=false;
    private boolean autoCommit;
    private final String host;
    private final int port;
    private String database;
    private final String username;
    private final String password;
    private final List<Query> batchList;
    private long totalTime=0;
    private int queryCount;
    private PacketFetcher packetFetcher;

    /**
     * Get a protocol instance
     *
     * @param host the host to connect to
     * @param port the port to connect to
     * @param database the initial database
     * @param username the username
     * @param password the password
     * @throws org.drizzle.jdbc.internal.common.QueryException if there is a problem reading / sending the packets
     */
    public MySQLProtocol(String host, int port, String database, String username, String password) throws QueryException {
        log.info("initiating a mysql protocol");
        this.host=host;
        this.port=port;
        this.database=(database==null?"":database);
        this.username=(username==null?"":username);
        this.password=(password==null?"":password);

        SocketFactory socketFactory = SocketFactory.getDefault();
        try {
            socket = socketFactory.createSocket(host,port);
        } catch (IOException e) {
            throw new QueryException("Could not connect: "+e.getMessage(),-1, SQLExceptionMapper.SQLStates.CONNECTION_EXCEPTION.getSqlState(),e);
        }
        log.info("Connected to: "+host+":"+port);
        batchList=new ArrayList<Query>();
        try {
            InputStream reader = socket.getInputStream();
            writer = new BufferedOutputStream(socket.getOutputStream(),16384);
            MySQLGreetingReadPacket greetingPacket = new MySQLGreetingReadPacket(reader);
            log.finest("Got greeting packet");
            this.version=greetingPacket.getServerVersion();
            Set<MySQLServerCapabilities> capabilities = EnumSet.of(MySQLServerCapabilities.LONG_PASSWORD,MySQLServerCapabilities.CONNECT_WITH_DB,MySQLServerCapabilities.IGNORE_SPACE,MySQLServerCapabilities.CLIENT_PROTOCOL_41, MySQLServerCapabilities.TRANSACTIONS,MySQLServerCapabilities.SECURE_CONNECTION);
            MySQLClientAuthPacket cap = new MySQLClientAuthPacket(this.username,this.password,this.database,capabilities,greetingPacket.getSeed());
            cap.send(writer);
            log.finest("Sending auth packet");
            packetFetcher = new AsyncPacketFetcher(reader);
            RawPacket rp = packetFetcher.getRawPacket();
            ResultPacket resultPacket = ResultPacketFactory.createResultPacket(rp);
            if(resultPacket.getResultType()==ResultPacket.ResultType.ERROR){
                ErrorPacket ep = (ErrorPacket)resultPacket;
                String message = ep.getMessage();
                throw new QueryException("Could not connect: "+message);
            } else if(resultPacket.getResultType()==ResultPacket.ResultType.OK) {
                OKPacket okp = (OKPacket)resultPacket;
                if(!okp.getServerStatus().contains(ServerStatus.AUTOCOMMIT))
                    setAutoCommit(true);
            }

        } catch (IOException e) {
            throw new QueryException("Could not connect: "+e.getMessage(),-1, SQLExceptionMapper.SQLStates.CONNECTION_EXCEPTION.getSqlState(),e);
        }
    }

    /**
     * Closes socket and stream readers/writers
     * @throws org.drizzle.jdbc.internal.common.QueryException if the socket or readers/writes cannot be closed
     */
    public void close() throws QueryException {
        log.info("Closing...");
        try {
            packetFetcher.close();
            ClosePacket closePacket = new ClosePacket();
            closePacket.send(writer);
            packetFetcher.close();
            writer.close();
            socket.close();

        } catch(IOException e){
            throw new QueryException("Could not close connection: "+e.getMessage(),-1, SQLExceptionMapper.SQLStates.CONNECTION_EXCEPTION.getSqlState(),e);
        }
        this.connected=false;
    }

    /**
     *
     * @return true if the connection is closed
     */
    public boolean isClosed() {
        return !this.connected;
    }

    /**
     * create a DrizzleQueryResult - precondition is that a result set packet has been read
     * @param packet the result set packet from the server
     * @return a DrizzleQueryResult
     * @throws java.io.IOException when something goes wrong while reading/writing from the server
     */
    private QueryResult createDrizzleQueryResult(ResultSetPacket packet) throws IOException {
        List<ColumnInformation> columnInformation = new ArrayList<ColumnInformation>();
        for(int i=0;i<packet.getFieldCount();i++) {
            RawPacket rawPacket = packetFetcher.getRawPacket();
            ColumnInformation columnInfo = FieldPacket.columnInformationFactory(rawPacket);
            columnInformation.add(columnInfo);
        }
        packetFetcher.getRawPacket();
        List<List<ValueObject>> valueObjects = new ArrayList<List<ValueObject>>();

        while(true) {
            RawPacket rawPacket = packetFetcher.getRawPacket();
            if(ReadUtil.eofIsNext(rawPacket)) {
                EOFPacket eofPacket = (EOFPacket) ResultPacketFactory.createResultPacket(rawPacket);
                return new DrizzleQueryResult(columnInformation,valueObjects,eofPacket.getWarningCount());
            }
            RowPacket rowPacket = new RowPacket(rawPacket,columnInformation);
            valueObjects.add(rowPacket.getRow());
        }
    }

    public void selectDB(String database) throws QueryException {
        log.finest("Selecting db "+database);
        SelectDBPacket packet = new SelectDBPacket(database);
        try {
            packet.send(writer);
            RawPacket rawPacket = packetFetcher.getRawPacket();
            ResultPacketFactory.createResultPacket(rawPacket);
        } catch (IOException e) {
            throw new QueryException("Could not select database: "+e.getMessage(),-1, SQLExceptionMapper.SQLStates.CONNECTION_EXCEPTION.getSqlState(),e);
        }
        this.database=database;
    }

    public String getVersion() {
        return version;
    }

    public void setReadonly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean getReadonly() {
        return readOnly;
    }

    public void commit() throws QueryException {
        log.finest("commiting transaction");
        executeQuery(new DrizzleQuery("COMMIT"));
    }

    public void rollback() throws QueryException {
        log.finest("rolling transaction back");
        executeQuery(new DrizzleQuery("ROLLBACK"));
    }

    public void rollback(String savepoint) throws QueryException {
        log.finest("rolling back to savepoint "+savepoint);
        executeQuery(new DrizzleQuery("ROLLBACK TO SAVEPOINT "+savepoint));
    }

    public void setSavepoint(String savepoint) throws QueryException {
        log.info("setting a savepoint named "+savepoint);
        executeQuery(new DrizzleQuery("SAVEPOINT "+savepoint));
    }
    public void releaseSavepoint(String savepoint) throws QueryException {
        log.info("releasing savepoint named "+savepoint);
        executeQuery(new DrizzleQuery("RELEASE SAVEPOINT "+savepoint));
    }

    public void setAutoCommit(boolean autoCommit) throws QueryException {
        this.autoCommit = autoCommit;
        executeQuery(new DrizzleQuery("SET autocommit="+(autoCommit?"1":"0")));
    }

    public boolean getAutoCommit() {
        return autoCommit;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean ping() throws QueryException {
        MySQLPingPacket pingPacket = new MySQLPingPacket();
        try {
            pingPacket.send(writer);
            log.finest("Sent ping packet");
            RawPacket rawPacket = packetFetcher.getRawPacket();
            return ResultPacketFactory.createResultPacket(rawPacket).getResultType()==ResultPacket.ResultType.OK;
        } catch (IOException e) {
            throw new QueryException("Could not ping: "+e.getMessage(),-1, SQLExceptionMapper.SQLStates.CONNECTION_EXCEPTION.getSqlState(),e);
        }
    }

    public QueryResult executeQuery(Query dQuery) throws QueryException {
        log.finest("Executing streamed query: "+dQuery);
        StreamedQueryPacket packet = new StreamedQueryPacket(dQuery);
        int i=0;
        try {
            packet.send(writer);
        } catch (IOException e) {
            throw new QueryException("Could not send query: "+e.getMessage(),-1, SQLExceptionMapper.SQLStates.CONNECTION_EXCEPTION.getSqlState(),e);
        }

        RawPacket rawPacket = null;
        try {
            rawPacket = packetFetcher.getRawPacket();
        } catch (IOException e) {
            throw new QueryException("Could not read resultset: "+e.getMessage(),-1, SQLExceptionMapper.SQLStates.CONNECTION_EXCEPTION.getSqlState(),e);
        }
        ResultPacket resultPacket = ResultPacketFactory.createResultPacket(rawPacket);

        switch(resultPacket.getResultType()) {
            case ERROR:
                log.warning("Could not execute query "+dQuery+": "+ ((ErrorPacket)resultPacket).getMessage());
                throw new QueryException("Could not execute query: "+((ErrorPacket)resultPacket).getMessage());
            case OK:
                OKPacket okpacket = (OKPacket)resultPacket;
                QueryResult updateResult = new DrizzleUpdateResult(okpacket.getAffectedRows(),
                                                                            okpacket.getWarnings(),
                                                                            okpacket.getMessage(),
                                                                            okpacket.getInsertId());
                log.fine("OK, "+ okpacket.getAffectedRows());
                return updateResult;
            case RESULTSET:
                log.fine("SELECT executed, fetching result set");
                try {
                    return this.createDrizzleQueryResult((ResultSetPacket)resultPacket);
                } catch (IOException e) {
                    throw new QueryException("Could not read result set: "+e.getMessage(),-1, SQLExceptionMapper.SQLStates.CONNECTION_EXCEPTION.getSqlState(),e);
                }
            default:
                log.severe("Could not parse result...");
                throw new QueryException("Could not parse result");
        }

    }

    public void addToBatch(Query dQuery) {
        log.info("Adding query to batch");
        batchList.add(dQuery);
    }
    public List<QueryResult> executeBatch() throws QueryException {
        log.info("executing batch");
        List<QueryResult> retList = new ArrayList<QueryResult>(batchList.size());
        int i=0;
        for(Query query : batchList) {
            log.info("executing batch query");
            retList.add(executeQuery(query));
        }
        clearBatch();
        return retList;

    }

    public void clearBatch() {
        batchList.clear();
    }

    public List<RawPacket> startBinlogDump(int startPos, String filename) throws BinlogDumpException {
        log.info("starting binlog ("+filename+") dump at pos "+startPos);
        MySQLBinlogDumpPacket mbdp = new MySQLBinlogDumpPacket(startPos, filename);
        try {
            mbdp.send(writer);
            List<RawPacket> rpList = new LinkedList<RawPacket>();
            while(true) {
                RawPacket rp = this.packetFetcher.getRawPacket();
                if(ReadUtil.eofIsNext(rp)) {
                    return rpList;
                }
                rpList.add(rp);
            }
        } catch (IOException e) {
            throw new BinlogDumpException("Could not read binlog",e);
        }
    }
}