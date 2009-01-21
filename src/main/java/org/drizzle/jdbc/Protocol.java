package org.drizzle.jdbc;

import java.io.IOException;

/**
 * User: marcuse
 * Date: Jan 14, 2009
 * Time: 4:05:47 PM
 */
public interface Protocol {

    void connect(String username, String password) throws UnauthorizedException, IOException;

    void close() throws IOException;

    boolean isClosed();

    DrizzleRows executeQuery(String s) throws IOException;

    void selectDB(String database) throws IOException;

    public void clearInputStream() throws IOException;
}
