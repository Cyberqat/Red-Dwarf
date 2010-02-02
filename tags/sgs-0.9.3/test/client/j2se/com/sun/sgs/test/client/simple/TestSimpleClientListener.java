/*
 * Copyright (c) 2007, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.sun.sgs.test.client.simple;

import java.io.IOException;
import java.math.BigInteger;
import java.net.PasswordAuthentication;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;
import junit.framework.TestCase;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;
import com.sun.sgs.impl.client.comm.ClientConnection;
import com.sun.sgs.impl.client.comm.ClientConnectionListener;
import com.sun.sgs.impl.client.comm.ClientConnector;
import com.sun.sgs.impl.client.comm.ClientConnectorFactory;
import com.sun.sgs.impl.client.simple.SimpleConnectorFactory;
import com.sun.sgs.impl.sharedutil.CompactId;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;

public class TestSimpleClientListener
    extends TestCase
{
    MockClientConnection mockConnection;
    MockClientConnector  mockConnector;
    Properties connectionProps;
    SimpleClient client;
    volatile ClientChannel testChannel = null;

    static final String DEFAULT_USER = "alice";
    static final String DEFAULT_PASS = "s3cR37";

    static PasswordAuthentication getDefaultAuth() {
        char[] pass = DEFAULT_PASS.toCharArray();
        return new PasswordAuthentication(DEFAULT_USER, pass);
    }

    @Override
    public void setUp() {
        mockConnection = new MockClientConnection();
        mockConnector = new MockClientConnector(mockConnection);

        MockClientConnector.setConnectorFactory(
            new MockConnectorFactory(mockConnector));

        connectionProps = new Properties();
        testChannel = null;
    }

    @Override
    public void tearDown() {
        MockClientConnector.setConnectorFactory(new SimpleConnectorFactory());
        mockConnector = null;
        mockConnection = null;
        connectionProps = null;
        testChannel = null;
        client = null;
    }

    // Tests

    public void testNullClientListener() throws IOException {
        try {
            connect(null);
        } catch (NullPointerException expected) {
            // passed
            return;
        }
        Assert.fail("Expected NullPointerException");
    }

    public void testNullPasswordAuth() throws IOException {
        ClientListenerBase listener = new ClientListenerBase(null);

        try {
            connect(listener);
            mockConnection.mockConnect();
        } catch (NullPointerException expected) {
            // passed
            return;
        }
        Assert.fail("Expected NullPointerException");
    }

    public void testNullChannelListener() throws IOException {
        byte[] message = new byte[1];
        ClientListenerBase listener = new ClientListenerBase() {
            @Override
            public ClientChannelListener joinedChannel(ClientChannel channel) {
                testChannel = channel;
                return null;
            }
        };
        connect(listener);
        mockConnection.mockConnect();
        queueLoggedIn(1, 1);
        mockConnection.mockDeliverRecv();
        queueChannelJoin("foo", 1);
        try {
            mockConnection.mockDeliverRecv();
        } catch (NullPointerException expected) {
            try {
                testChannel.send(message);
            } catch (IOException e) {
                e.printStackTrace();
                Assert.fail("Expected IllegalStateException");
            } catch (IllegalStateException e) {
                // passed
                return;                
            }
            Assert.fail("Expected failure to send on channel");
        }
        Assert.fail("Expected NullPointerException");
    }

    public void testChannelJoinedException() throws IOException {
        byte[] message = new byte[1];
        ClientListenerBase listener = new ClientListenerBase() {
            @Override
            public ClientChannelListener joinedChannel(ClientChannel channel) {
                testChannel = channel;
                throw new RuntimeException("Intentionally testing exception");
            }
        };
        connect(listener);
        mockConnection.mockConnect();
        queueLoggedIn(1, 1);
        mockConnection.mockDeliverRecv();
        queueChannelJoin("foo", 1);
        try {
            mockConnection.mockDeliverRecv();
        } catch (RuntimeException expected) {
            try {
                testChannel.send(message);
            } catch (IOException e) {
                e.printStackTrace();
                Assert.fail("Expected IllegalStateException");
            } catch (IllegalStateException e) {
                // passed
                return;                
            }
            Assert.fail("Expected failure to send on channel");
        }
        Assert.fail("Expected RuntimeException");
    }

    public void testChannelJoinedBeforeLoggedIn() throws IOException {
        ClientListenerBase listener = new ClientListenerBase() {
            boolean gotLoggedIn = false;
            @Override
            public void loggedIn()
            {
                Assert.assertFalse("Already logged in", gotLoggedIn);
                gotLoggedIn = true;
            }

            @Override
            public ClientChannelListener joinedChannel(ClientChannel channel) {
                Assert.assertFalse("Already logged in", gotLoggedIn);
                Assert.fail("Did not expect channel join; not logged in");
                return null;
            }
        };
        connect(listener);
        mockConnection.mockConnect();
        queueChannelJoin("foo", 1);
        try {
            mockConnection.mockDeliverRecv();
        } catch (IllegalStateException expected) {
            // passed
            return;
        }
        Assert.fail("Expected IllegalStateException");
    }

    /**
     * Verify that clients are allowed to send on a channel during the
     * channelJoined callback notifying them about that channel.
     *
     * @throws IOException if an I/O error occurs
     */
    public void testChannelSendInJoinCallback() throws IOException {
        ClientListenerBase listener = new ClientListenerBase() {
            boolean sentChannelMessage = false;

            @Override
            public ClientChannelListener joinedChannel(ClientChannel channel) {
                byte[] message = new byte[1];
                try {
                    int sentBefore = mockConnection.sendQueue.size();
                    channel.send(message);
                    if (mockConnection.sendQueue.size() == (sentBefore + 1)) {
                        sentChannelMessage = true;
                    } else {
                        System.err.println("Didn't send channel message");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
                return new ClientChannelListenerBase();
            }

            @Override
            void validate() {
                Assert.assertTrue(
                    "Expected to send a message during channel join",
                    sentChannelMessage);
            }
        };
        connect(listener);
        mockConnection.mockConnect();
        queueLoggedIn(1, 1);
        mockConnection.mockDeliverRecv();
        queueChannelJoin("foo", 1);
        mockConnection.mockDeliverRecv();
        listener.validate();
    }

    public void testDisconnect() throws IOException {
        ClientListenerBase listener = new ClientListenerBase() {
            boolean gotLoggedIn = false;
            boolean gotDisconnected = false;

            @Override
            public void loggedIn()
            {
                Assert.assertFalse("Already logged in", gotLoggedIn);
                gotLoggedIn = true;
            }

            @Override
            public void disconnected(boolean graceful, String reason)
            {
                Assert.assertTrue("Expected to be connected", gotLoggedIn);
                Assert.assertFalse("Already disconnected", gotDisconnected);
                gotDisconnected = true;
            }
            
            @Override
            void validate() {
                Assert.assertTrue("Expected to have connected", gotLoggedIn);
                Assert.assertTrue("Expected to have disconnected", gotDisconnected);
            }
        };
        connect(listener);
        mockConnection.mockConnect();
        queueLoggedIn(1, 1);
        mockConnection.mockDeliverRecv();
        mockConnection.mockRequestDisconnect(false, null);
        listener.validate();
    }

    /**
     * Verify that clients cannot send on a channel during the
     * channelLeft callback on that channel.
     *
     * @throws IOException if an I/O error occurs
     */
    public void testChannelSendInLeftCallback() throws IOException {
        ClientListenerBase listener = new ClientListenerBase() {
            boolean passed = false;

            @Override
            public ClientChannelListener joinedChannel(ClientChannel channel) {
                
                return new ClientChannelListenerBase() {
                    @Override
                    public void leftChannel(ClientChannel ch)
                    {
                        byte[] message = new byte[1];
                        try {
                            int sentBefore = mockConnection.sendQueue.size();
                            ch.send(message);
                            if (mockConnection.sendQueue.size() == (sentBefore + 1)) {
                                System.err.println("Sent channel message");
                            } else {
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (IllegalStateException e) {
                            passed = true;
                        }
                    }
                };
            }

            @Override
            void validate() {
                Assert.assertTrue(
                    "Expected IllegalStateExeception trying to send " +
                    "during leftChannel", passed);
            }
        };
        connect(listener);
        mockConnection.mockConnect();
        queueLoggedIn(1, 1);
        mockConnection.mockDeliverRecv();
        queueChannelJoin("foo", 1);
        mockConnection.mockDeliverRecv();
        queueChannelLeft(1);
        mockConnection.mockDeliverRecv();
        listener.validate();
    }

    public void testChannelLeftOnDisconnect() throws IOException {
        ClientListenerBase listener = new ClientListenerBase() {
            boolean gotLeftChannel = false;
            boolean gotDisconnected = false;

            @Override
            public ClientChannelListener joinedChannel(ClientChannel channel) {
                
                return new ClientChannelListenerBase() {
                    @Override
                    public void leftChannel(ClientChannel ch)
                    {
                        Assert.assertFalse("Already disconnected", gotDisconnected);
                        Assert.assertFalse("Already left channel", gotLeftChannel);
                        gotLeftChannel = true;
                    }
                };
            }

            @Override
            public void disconnected(boolean graceful, String reason)
            {
                Assert.assertTrue("Expected to have left channel", gotLeftChannel);
                Assert.assertFalse("Already disconnected", gotDisconnected);
                gotDisconnected = true;
            }
            
            @Override
            void validate() {
                Assert.assertTrue("Expected to have left channel", gotLeftChannel);
                Assert.assertTrue("Expected to have disconnected", gotDisconnected);
            }
        };
        connect(listener);
        mockConnection.mockConnect();
        queueLoggedIn(1, 1);
        mockConnection.mockDeliverRecv();
        queueChannelJoin("foo", 1);
        mockConnection.mockDeliverRecv();
        mockConnection.mockRequestDisconnect(false, null);
        listener.validate();
    }

    // Helpers
    
    void connect(SimpleClientListener listener) throws IOException {
        client = new SimpleClient(listener);
        client.login(connectionProps);
    }

    void queueLoggedIn(long sessionId, long reconnectKey) {
        byte[] sid = BigInteger.valueOf(sessionId).toByteArray();
        byte[] rkey = BigInteger.valueOf(reconnectKey).toByteArray();
        mockConnection.mockLoggedIn(new CompactId(sid), new CompactId(rkey));
    }

    void queueChannelJoin(String name, long channelId) {
        byte[] id = BigInteger.valueOf(channelId).toByteArray();
        mockConnection.mockChannelJoined(name, new CompactId(id));
    }

    void queueChannelLeft(long channelId) {
        byte[] id = BigInteger.valueOf(channelId).toByteArray();
        mockConnection.mockChannelLeft(new CompactId(id));
    }

    static class ClientListenerBase implements SimpleClientListener
    {
        PasswordAuthentication auth;

        ClientListenerBase() {
            auth = getDefaultAuth();
        }

        ClientListenerBase(PasswordAuthentication auth) {
            this.auth = auth;
        }

        void setPasswordAuthentication(PasswordAuthentication auth) {
            this.auth = auth;
        }
        
        void validate() { }

        public PasswordAuthentication getPasswordAuthentication() {
            return auth;
        }

        public void loggedIn() { }

        public void loginFailed(String reason) { }

        public void disconnected(boolean graceful, String reason) { }

        public ClientChannelListener joinedChannel(ClientChannel channel) {
            return new ClientChannelListenerBase();
        }

        public void receivedMessage(byte[] message) { }

        public void reconnected() { }

        public void reconnecting() { }
    
    }

    static class ClientChannelListenerBase
        implements ClientChannelListener
    {
        public void leftChannel(ClientChannel channel) { }

        public void receivedMessage(ClientChannel channel, SessionId sender,
            byte[] message) { }
    }
    
    static final class MockClientConnection
        implements ClientConnection
    {
        private ClientConnectionListener listener = null;
        
        private final AtomicInteger state;
        private static final int UNINITIALIZED = 0;
        private static final int CONNECTING = 1;
        private static final int CONNECTED = 2;
        private static final int DISCONNECTING = 3;
        private static final int DISCONNECTED = 4;
        
        public final LinkedList<MessageBuffer> sendQueue;
        public final LinkedList<MessageBuffer> recvQueue;

        public MockClientConnection() {
            state = new AtomicInteger(UNINITIALIZED);
            sendQueue = new LinkedList<MessageBuffer>();
            recvQueue = new LinkedList<MessageBuffer>();
        }
        
        void setListener(ClientConnectionListener listener) {
            assert listener != null;
            this.listener = listener;
            boolean success = state.compareAndSet(UNINITIALIZED, CONNECTING);
            assert success;
        }
        
        void mockConnect() {
            boolean success = state.compareAndSet(CONNECTING, CONNECTED);
            assert success;
            listener.connected(this);
        }
        
        void mockRequestDisconnect(boolean graceful, byte[] message) {
            boolean success = state.compareAndSet(CONNECTED, DISCONNECTING);
            assert success;
            listener.disconnected(graceful, message); // TODO
        }

        void mockFinishDisconnect() {
            boolean success = state.compareAndSet(DISCONNECTING, DISCONNECTED);
            assert success;
        }

        void mockDeliverRecv() {
            MessageBuffer buf = recvQueue.poll();
            assert buf != null;
            mockDeliverRecv(buf.getBuffer());
        }

        void mockDeliverRecv(byte[] message) {
            listener.receivedMessage(message);
        }

        void mockLoggedIn(CompactId sessionId, CompactId reconnectKey) {
            MessageBuffer buf =
                new MessageBuffer(3 +
                    sessionId.getExternalFormByteCount() +
                    reconnectKey.getExternalFormByteCount());
            buf.putByte(SimpleSgsProtocol.VERSION).
                putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
                putByte(SimpleSgsProtocol.LOGIN_SUCCESS).
                putBytes(sessionId.getExternalForm()).
                putBytes(reconnectKey.getExternalForm());
            recvQueue.add(buf);
        }

        void mockChannelJoined(String name, CompactId id) {
            MessageBuffer buf =
                new MessageBuffer(3 + MessageBuffer.getSize(name) +
                                  id.getExternalFormByteCount());
            buf.putByte(SimpleSgsProtocol.VERSION).
                putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
                putByte(SimpleSgsProtocol.CHANNEL_JOIN).
                putString(name).
                putBytes(id.getExternalForm());
            
            recvQueue.add(buf);
        }

        void mockChannelLeft(CompactId id) {
            MessageBuffer buf =
                new MessageBuffer(3 + id.getExternalFormByteCount());
            buf.putByte(SimpleSgsProtocol.VERSION).
                putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
                putByte(SimpleSgsProtocol.CHANNEL_LEAVE).
                putBytes(id.getExternalForm());
            
            recvQueue.add(buf);
        }

        public void disconnect() throws IOException {
            mockRequestDisconnect(true, null);
        }

        public void sendMessage(byte[] message) throws IOException {
            assert state.get() == CONNECTED;
            byte[] messageCopy = new byte[message.length];
            System.arraycopy(message, 0, messageCopy, 0, messageCopy.length);
            sendQueue.add(new MessageBuffer(messageCopy));
        }
    }
    
    static final class MockClientConnector
        extends ClientConnector
    {
        private final MockClientConnection connection;

        public static void setConnectorFactory(ClientConnectorFactory factory) {
            ClientConnector.setConnectorFactory(factory);
        }

        public MockClientConnector(MockClientConnection connection) {
            this.connection = connection;
        }

        @Override
        public void cancel() throws IOException
        {
            throw new UnsupportedOperationException("Cancel not yet implemented");
        }

        @Override
        public void connect(ClientConnectionListener listener) throws IOException
        {
            connection.setListener(listener);
        }
        
    }
    
    static final class MockConnectorFactory
        implements ClientConnectorFactory
    {
        private final MockClientConnector connector;

        public MockConnectorFactory(MockClientConnector connector) {
            this.connector = connector;
        }

        public MockClientConnector createConnector(Properties properties) {
            return connector;
        }
        
    }
}
