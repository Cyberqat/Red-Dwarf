/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.example.chat.app;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;

/**
 * Listener for events from a particular {@link ClientSession} logged into a
 * {@code ChatApp} application.
 * <p>
 * Note that this {@link ClientSessionListener} has no mutable state, so it
 * doesn't need to implement {@link com.sun.sgs.app.ManagedObject}.  This
 * means it will be garbage-collected once its associated session is
 * {@linkplain ChatClientSessionListener#disconnected disconnected}, so we
 * don't explicitly have to remove it from the data store.
 */
public class ChatClientSessionListener
    implements Serializable, ClientSessionListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The logger for this class. */
    private static final Logger logger =
        Logger.getLogger(ChatClientSessionListener.class.getName());

    /** The name of the global channel. */
    private static final String GLOBAL_CHANNEL_NAME = "-GLOBAL-";

    /** The {@link Charset} encoding for client/server messages. */
    private static final String MESSAGE_CHARSET = "UTF-8";

    /** The command prefix: "{@value #COMMAND_PREFIX}" */
    private static final String COMMAND_PREFIX = "/";

    /** The {@link ClientSession} this listener receives events for. */
    private final ManagedReference<ClientSession> sessionRef;

    /**
     * Creates a new listener for the given {@code ClientSession}.
     * Immediately joins the session to the global notification channel,
     * and sends membership change notifications as appropriate.
     *
     * @param session this listener's {@code ClientSession}
     *
     * @see #addToChannel
     */
    public ChatClientSessionListener(ClientSession session) {
        sessionRef = AppContext.getDataManager().createReference(session);
        addToChannel(GLOBAL_CHANNEL_NAME);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation sends a broadcast notification to other
     * sessions to inform them of the disconnected session.
     */
    public void disconnected(boolean graceful) {
        if (logger.isLoggable(Level.FINE)) {
            String grace = graceful ? "graceful" : "forced";
            logger.log(Level.FINE,
                "{0} {1} disconnect",
                new Object[] { session(), grace });
        }
        removeFromChannel(GLOBAL_CHANNEL_NAME);
    }

    /** {@inheritDoc} */
    public void receivedMessage(ByteBuffer message) {
        try {
            String messageString = fromMessageBuffer(message);

            // Check that the command begins with a forward-slash
            if (! messageString.startsWith(COMMAND_PREFIX)) {
                throw new IllegalArgumentException(
                    "Command must start with " + COMMAND_PREFIX);
            }

            // Split at the first run of whitespace, if any
            String[] args = messageString.split("\\s+", 2);

            // Find the ChatCommand enum for this command
            String commandString = args[0].substring(1).toUpperCase();
            ChatCommand command = ChatCommand.valueOf(commandString);

            switch (command) {
            case JOIN:
                addToChannel(args[1]);
                break;

            case LEAVE:
                removeFromChannel(args[1]);
                break;

            case PING:
                echo(args[1]);
                break;

            case DISCONNECT:
                logger.log(Level.INFO,
                           "Disconnect request from {0}",
                           session());
                session().disconnect();
                break;

            case SHUTDOWN:
                logger.log(Level.CONFIG,
                           "Shutdown request from {0}",
                           session());
                System.exit(0);
                break;
            }
        } catch (Exception e) {
            if (logger.isLoggable(Level.INFO)) {
                LogRecord rec = new LogRecord(Level.INFO,
                    "While processing command from {0}, disconnecting:");
                rec.setThrown(e);
                rec.setParameters(new Object[] { session() });
                logger.log(rec);
            }
            
            session().disconnect();
        }
    }

    private ClientSession session() {
        return sessionRef.get();
    }

    private static Channel findOrCreateChannel(String channelName) {
        try {
            return findChannel(channelName);
        } catch (NameNotBoundException e) {
            Channel channel =
                AppContext.getChannelManager().createChannel(Delivery.RELIABLE);
            String key = channelKey(channelName);
            AppContext.getDataManager().setBinding(key, channel);
            return channel;
        }
    }

    private static Channel findChannel(String channelName) {
        String key = channelKey(channelName);
        return (Channel) AppContext.getDataManager().getBinding(key);
    }
    
    private static String channelKey(String channelName) {
        return "channel." + channelName;
    }

    private String getSessionIdString() {
        return getIdString(sessionRef);
    }

    private String getIdString(ManagedReference<?> ref) {
        return ref.getId().toString(16);
    }

    /**
     * Joins a session to a channel and sends membership change
     * notifications as appropriate. If the channel doesn't exist,
     * it is created.
     * 
     * @param channelName the name of the channel to add this session to
     */
    private void addToChannel(String channelName) {

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,
                    "Join " + session() + " to " + channelName);
        }

        ChannelManager channelMgr = AppContext.getChannelManager();
        Channel channel = findOrCreateChannel(channelName);

        // Send the membership change first, so the new session doesn't
        // receive its own join message.
        StringBuilder changeMsg = new StringBuilder("/joined ");
        changeMsg.append(channelName);
        changeMsg.append(' ');
        changeMsg.append(getSessionIdString());
        if (channelName.equals(GLOBAL_CHANNEL_NAME)) {
            changeMsg.append(':');
            changeMsg.append(session().getName());
        }
        channel.send(toMessageBuffer(changeMsg.toString()));

        // Now add the joiner and tell it about all the members on
        // the channel, the joiner included.
        channel.join(session());

        // Send the membership list to the joining session.
        StringBuilder listMessage = new StringBuilder("/members ");
        listMessage.append(channelName);
        for (ManagedReference<ClientSession> memberRef
		 : getChannelMemberRefs(channelName))
	{
            ClientSession member = memberRef.get();
            listMessage.append(' ');
            listMessage.append(getIdString(memberRef));
            if (channelName.equals(GLOBAL_CHANNEL_NAME)) {
                listMessage.append(':');
                listMessage.append(member.getName());
            }
        }
        session().send(toMessageBuffer(listMessage.toString()));
    }

    private Set<ManagedReference<ClientSession>> getChannelMemberRefs(
	String channelName)
    {
        // FIXME implement
        return null;
    }

    private boolean channelHasSessions(String channelName) {
        // FIXME implement
        return true;
    }

    /**
     * Removes this session from the channel and sends membership change
     * notifications as appropriate. Closes the channel if this session was
     * its last member.
     * 
     * @param channelName the name of the channel to remove this session from
     */
    private void removeFromChannel(String channelName) {

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,
                    "Leave {0} from {1}",
                    new Object[] { session(), channelName });
        }

        Channel channel;
        
        try {
            channel = findChannel(channelName);
        } catch (NameNotBoundException e) {
            // The channel has been closed, so there's nothing to do.
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "Leave {0} from {1}: channel is closed",
                        new Object[] { session(), channelName });
            }
            return;
        }

        if (session().isConnected()) {
            // Remove the member first, so it doesn't get the membership
            // change message for its own departure.
            channel.leave(session());
        }

        // If the channel has no more sessions, close it.
        if (! channelHasSessions(channelName)) {
            channel.close();
            return;
        }

        // Tell the rest of the channel about the removal.
        String changeMessage = "/left " + 
                               channelName + " " +
                               getSessionIdString();
        channel.send(toMessageBuffer(changeMessage));
    }

    /**
     * Echos the given string back to the sending session as a direct message.
     *
     * @param message the message to echo
     */
    private void echo(String message) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER,
                "Echo request from {0}, contents: {1}",
                new Object[] { session(), message });
        }

        String reply = "/pong " + message;
        session().send(toMessageBuffer(reply));
    }

    /**
     * Decodes the given {@code buffer} into a message string.
     *
     * @param bytes the encoded message
     * @return the decoded message string
     */
    static String fromMessageBuffer(ByteBuffer buffer) {
        try {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return new String(bytes, MESSAGE_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required charset " +
                MESSAGE_CHARSET + " not found", e);
        }
    }

    /**
     * Encodes the given message string into a {@link ByteBuffer}.
     *
     * @param s the message string to encode
     * @return the encoded message as a {@code ByteBuffer}
     */
    static ByteBuffer toMessageBuffer(String s) {
        try {
            return ByteBuffer.wrap(s.getBytes(MESSAGE_CHARSET));
        } catch (UnsupportedEncodingException e) {
            throw new Error("Required charset " +
                MESSAGE_CHARSET + " not found", e);
        }
    }
}
