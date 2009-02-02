package org.drizzle.jdbc;

import org.drizzle.jdbc.packet.*;
import org.drizzle.jdbc.packet.buffer.ReadBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.SocketFactory;
import java.net.Socket;
import java.io.*;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;

/**
 * TODO: logging!
 * TODO: refactor, clean up
 * TODO: when should i read up the resultset?
 * TODO: thread safety?
 * User: marcuse
 * Date: Jan 14, 2009
 * Time: 4:06:26 PM
 */
public class DrizzleProtocol implements Protocol {
    private final static Logger log = LoggerFactory.getLogger(DrizzleProtocol.class);
    private boolean connected=false;
    private Socket socket;
    private BufferedInputStream reader;
    private BufferedOutputStream writer;
    private String version;
    private boolean readOnly=false;

    /**
     * Get a protocol instance
     *
     * @param host the host to connect to
     * @param port the port to connect to
     * @param database the initial database
     * @param username the username
     * @param password the password
     * @throws IOException if there is a problem reading / sending the packets
     * @throws UnauthorizedException if the user is unauthorized
     */
    public DrizzleProtocol(String host, int port, String database, String username, String password) throws IOException, UnauthorizedException {
        SocketFactory socketFactory = SocketFactory.getDefault();
        socket = socketFactory.createSocket(host,port);
        log.info("Connected to: {}:{}",host,port);
        reader = new BufferedInputStream(socket.getInputStream(),16384);
        writer = new BufferedOutputStream(socket.getOutputStream(),16384);
        this.connect(username,password,database);
    }

    /**
     * Connect to database
     * @param username the username to use
     * @param password the password for the user
     * @param database initial database
     * @throws IOException ifsomething is wrong while reading / writing streams 
     */
    private void connect(String username, String password, String database) throws IOException {
        this.connected=true;
        byte packetSeqNum = 1;
        GreetingReadPacket greetingPacket = new GreetingReadPacket(reader);
        this.version=greetingPacket.getServerVersion();
        log.debug("Got greeting packet: {}",greetingPacket);
        ClientAuthPacket cap = new ClientAuthPacket(username,password,database);
        cap.setServerCapabilities(greetingPacket.getServerCapabilities());
        cap.setServerLanguage(greetingPacket.getServerLanguage());
        byte [] a = cap.toBytes(packetSeqNum);
        writer.write(a);
        writer.flush();
        log.debug("Sending auth packet: {}",cap);
        ResultPacket resultPacket = ResultPacketFactory.createResultPacket(reader);
        log.debug("Got result: {}",resultPacket);
        selectDB(database);
    }

    /**
     * Closes socket and stream readers/writers
     * @throws IOException
     */
    public void close() throws IOException {
        log.debug("Closing...");
        writer.close();
        reader.close();
        socket.close();
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
     * executes a query, eagerly fetches the results
     * @param query the query to execute
     * @return the query result
     * @throws IOException
     * @throws SQLException
     */
    public DrizzleQueryResult executeQuery(String query) throws IOException, SQLException {
        log.debug("Executing query: {}",query);
        QueryPacket packet = new QueryPacket(query);
        byte packetSeqNum=0;
        byte [] toWrite = packet.toBytes(packetSeqNum);
        writer.write(toWrite);
        writer.flush();
        ResultPacket resultPacket = ResultPacketFactory.createResultPacket(reader);
        switch(resultPacket.getResultType()) {
            case ERROR:
                log.warn("Could not execute query: {}",((ErrorPacket)resultPacket).getMessage());
                throw new SQLException("Could not execute query: "+((ErrorPacket)resultPacket).getMessage());
            case OK:
                DrizzleQueryResult dqr = new DrizzleQueryResult();
                OKPacket okpacket = (OKPacket)resultPacket;
                dqr.setUpdateCount((int)okpacket.getAffectedRows());
                dqr.setWarnings(okpacket.getWarnings());
                dqr.setMessage(okpacket.getMessage());
                dqr.setInsertId(okpacket.getInsertId());
                log.info("OK, {}", ((OKPacket)resultPacket).getAffectedRows());
                return dqr;
            case RESULTSET:
                log.info("SELECT executed, fetching result set");
                return this.createDrizzleQueryResult((ResultSetPacket)resultPacket);
            default:
                log.error("Could not parse result...");
                throw new SQLException("Could not parse result");
        }
    }

    /**
     * create a DrizzleQueryResult - precondition is that 
     * @param packet the result set packet from the server
     * @return a DrizzleQueryResult
     * @throws IOException when something goes wrong while reading/writing from the server
     */
    private DrizzleQueryResult createDrizzleQueryResult(ResultSetPacket packet) throws IOException {
        List<FieldPacket> fieldPackets = new ArrayList<FieldPacket>();
        for(int i=0;i<packet.getFieldCount();i++) {
            FieldPacket fieldPacket = new FieldPacket(new ReadBuffer(reader));
            fieldPackets.add(fieldPacket);
        }
        ReadBuffer readBuffer = new ReadBuffer(reader);
        if( (readBuffer.getByteAt(0)==(byte)0xfe) && (readBuffer.getLength()<9)) { //check for EOF
        } else {
            throw new IOException("Could not parse result");
        }
        DrizzleQueryResult dqr = new DrizzleQueryResult(fieldPackets);
        while(true) {
            readBuffer = new ReadBuffer(reader);
            if((readBuffer.getByteAt(0)==(byte)0xfe) && (readBuffer.getLength()<9)) { //check for EOF
                return dqr;
            }
            RowPacket rowPacket = new RowPacket(readBuffer,packet.getFieldCount());
            dqr.addRow(rowPacket.getRow());
        }
    }
    public void selectDB(String database) throws IOException {
        SelectDBPacket packet = new SelectDBPacket(database);
        byte packetSeqNum=0;
        byte [] b = packet.getBytes(packetSeqNum);
        writer.write(b);
        writer.flush();
        ResultPacket resultPacket = ResultPacketFactory.createResultPacket(reader);
        packetSeqNum=(byte)(resultPacket.getPacketSeq()+1);
    }

    public void clearInputStream() throws IOException {
        if(reader.available() > 0) {
            byte [] aa = new byte[reader.available()];
            reader.read(aa);
        }
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

}
