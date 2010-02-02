package com.sun.sgs.test.impl.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.sun.sgs.impl.io.ServerSocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.io.AcceptorListener;
import com.sun.sgs.io.Acceptor;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;

/**
 * A test harness for the server {@code Acceptor} code.
 */
public class ServerTest implements ConnectionListener {

    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final String DEFAULT_PORT = "5150";
    
    Acceptor<SocketAddress> acceptor;
    private int numConnections;

    public ServerTest() { }

    public void start() {
        String host = System.getProperty("host", DEFAULT_HOST);
        String portString = System.getProperty("port", DEFAULT_PORT);
        int port = Integer.valueOf(portString);
        try {
            acceptor = new ServerSocketEndpoint(
                    new InetSocketAddress(host, port),
                   TransportType.RELIABLE).createAcceptor();
            acceptor.listen(new AcceptorListener() {
                /**
                 * {@inheritDoc}
                 */
                public ConnectionListener newConnection() {
                    return ServerTest.this;
                }

                /**
                 * {@inheritDoc}
                 */
                public void disconnected() {
                    // TODO Auto-generated method stub
                }
            });
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        System.out.println("Listening on " +
                acceptor.getBoundEndpoint().getAddress());
    }

    public final static void main(String[] args) {
        ServerTest server = new ServerTest();
        synchronized(server) {
            server.start();
            try {
                server.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /** {@inheritDoc} */
    public void connected(Connection conn) {
        synchronized (this) {
            numConnections++;
        }
        System.out.println("ServerTest: connected");
    }

    /** {@inheritDoc} */
    public void disconnected(Connection conn) {
        synchronized (this) {
            numConnections--;
            if (numConnections <= 0) {
                acceptor.shutdown();
            }
            this.notifyAll();
        }
    }

    /** {@inheritDoc} */
    public void exceptionThrown(Connection conn, Throwable exception) {
        System.out.print("ServerTest: exceptionThrown ");
        exception.printStackTrace();
    }

    /** {@inheritDoc} */
    public void bytesReceived(Connection conn, byte[] message) {
        byte[] buffer = new byte[message.length]; 
        System.arraycopy(message, 0, buffer, 0, message.length); 
        try {
            conn.sendBytes(buffer);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
        
    }

}
