/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.protocol;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

/**
 * A handler for session and channel protocol messages for an associated
 * client session.
 *
 * <p>Each operation takes a {@link RequestCompletionHandler} argument to be
 * notified when the associated request has been processed.  A caller may need
 * to know when an operation has completed so that it can throttle incoming
 * messages (for example only resuming reading when the handler completes
 * processing a request), and/or can control the number of clients connected
 * at any given time.
 */
public interface SessionProtocolHandler {

    /**
     * Processes a message sent by the associated client, and invokes the
     * {@link RequestCompletionHandler#completed completed} method on the
     * given {@code completionHandler} when this handler has completed
     * processing the message.  The message starts at the buffer's current
     * position and ends at the buffer's limit.  The buffer's position is
     * not modified by this operation.
     * 
     * <p>The {@code ByteBuffer} may be reused immediately after this method
     * returns.  Changes made to the buffer after this method returns will
     * have no effect on the message supplied to this method.
     *
     * @param	message a message
     * @param	completionHandler a completion handler
     */
    void sessionMessage(
	ByteBuffer message, RequestCompletionHandler<Void> completionHandler);

    /**
     * Processes a channel message sent by the associated client on the
     * channel with the specified {@code channelId}, and invokes the
     * {@link RequestCompletionHandler#completed completed} method on the
     * given {@code completionHandler} when this handler has completed
     * processing the channel message.  The message starts at the buffer's
     * current position and ends at the buffer's limit.  The buffer's position
     * is not modified by this operation.
     * 
     * <p>The {@code ByteBuffer} may be reused immediately after this method
     * returns.  Changes made to the buffer after this method returns will
     * have no effect on the message supplied to this method.
     *
     * @param	channelId a channel ID
     * @param	message a message
     * @param	completionHandler a completion handler
     */
    void channelMessage(BigInteger channelId, ByteBuffer message,
			RequestCompletionHandler<Void> completionHandler);
    
    /**
     * Processes a logout request from the associated client, and invokes the
     * {@link RequestCompletionHandler#completed completed} method on the
     * given {@code completionHandler} when this handler has completed
     * processing the logout request.
     *
     * @param	completionHandler a completion handler
     */
    void logoutRequest(RequestCompletionHandler<Void> completionHandler);

    /**
     * Notifies this handler that a non-graceful client disconnection has
     * occurred, and invokes the {@link RequestCompletionHandler#completed
     * completed} method on the given {@code completionHandler} when this
     * handler has completed processing the disconnection.
     *
     * @param	completionHandler a completion handler
     */
    void disconnect(RequestCompletionHandler<Void> completionHandler);
}
