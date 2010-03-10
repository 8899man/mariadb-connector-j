/*
 * Drizzle JDBC
 *
 * Copyright (C) 2009 Marcus Eriksson (krummas@gmail.com)
 * All rights reserved.
 *
 * Copyright (C) 2009 Sun Microsystems
 * All rights reserved.
 *
 * Use and distribution licensed under the BSD license.
 */

package org.drizzle.jdbc.internal.common.packet;

import org.drizzle.jdbc.internal.common.PacketFetcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by IntelliJ IDEA. User: marcuse Date: Mar 31, 2009 Time: 2:01:30 PM To change this template use File |
 * Settings | File Templates.
 */
public class AsyncPacketFetcher implements Runnable, PacketFetcher {
    private final BlockingQueue<RawPacket> packet = new LinkedBlockingQueue<RawPacket>();
    private final InputStream inputStream;
    private final ExecutorService executorService;
    private volatile boolean shutDown = false;

    public AsyncPacketFetcher(final InputStream inputStream) {
        executorService = Executors.newSingleThreadExecutor(
                new ThreadFactory() {
                    public Thread newThread(final Runnable runnable) {
                        return new Thread(runnable, "DrizzlePacketFetcherThread");
                    }
                }
        );
        this.inputStream = inputStream;
    }

    public void start() {
        executorService.submit(this);
    }


    public void run() {
        try {
            while (!shutDown) {
                try {
                    final RawPacket rawPacket = RawPacket.nextPacket(inputStream);
                    if (rawPacket != null) {
                        packet.add(rawPacket);
                    } else {
                        Logger.getLogger(this.getClass().getName()).finest("Connection closed");
                        shutDown = true;
                        packet.add(RawPacket.IOEXCEPTION_PILL);
                    }
                } catch (IOException e) {
                    Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Got exception reading packet", e);
                    // ok got an ioexception reading that packet, lets put the IOEXCEPTION pill on the queue
                    packet.add(RawPacket.IOEXCEPTION_PILL);
                    // Does it make sense to continue to use the stream?
                    shutDown = true;
                }
            }
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();  // we are closing down, ignore any exceptions
            }
        } catch (Throwable t) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Got exception ", t);
        }
    }

    public RawPacket getRawPacket() throws IOException {
        try {
            final RawPacket rawPacket = packet.take();
            if (rawPacket == RawPacket.IOEXCEPTION_PILL) {
                throw new IOException();
            }
            return rawPacket;
        } catch (InterruptedException e) {
            throw new RuntimeException("Got interrupted while waiting for a packet", e); //Todo: fix
        }
    }


    public void awaitTermination() {
        try {
            executorService.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException("executorService shutdown problem", e);
        }
    }

    public void close() {
        this.shutDown = true;
        executorService.shutdownNow();
    }
}