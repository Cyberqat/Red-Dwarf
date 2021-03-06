package com.sun.sgs.protocol.simple;

import java.io.DataInput;

/**
 * SGS Protocol constants.
 * <p>
 * A protocol message is constructed as follows:
 * <ul>
 * <li> (byte) version number
 * <li> (byte) service id
 * <li> (byte) operation code
 * <li> optional content, depending on the operation code.
 * </ul>
 * <p>
 * A {@code ByteArray} is encoded as follows:
 * <ul>
 * <li> (unsigned short) number of bytes in the array
 * <li> (byte[]) the bytes in the array
 * </ul>
 * <p>
 * A {@code String} is encoded as follows:
 * <ul>
 * <li> (unsigned short) number of bytes of modified UTF-8 encoded String
 * <li> (byte[]) String encoded in modified UTF-8 as described
 * in {@link DataInput}
 * </ul>
 */
public interface SimpleSgsProtocol {
    
    /**
     * The maximum length of any protocol message field defined as a
     * {@code String} or {@code byte[]}: {@value #MAX_MESSAGE_LENGTH} bytes.
     */
    final int MAX_MESSAGE_LENGTH = 65535;

    /** The version number. */
    final byte VERSION = 0x01;

    /** Application service ID. */
    final byte APPLICATION_SERVICE = 0x01;

    /** Channel service ID. */
    final byte CHANNEL_SERVICE = 0x02;

    /**
     * Login request.
     * <ul>
     * <li> (String) name
     * <li> (String) password
     * </ul>
     */
    final byte LOGIN_REQUEST = 0x10;

    /**
     * Login success (login request acknowledgment).
     * <ul>
     * <li> (ByteArray) sessionId
     * <li> (ByteArray) reconnectionKey
     * </ul>
     */
    final byte LOGIN_SUCCESS = 0x11;

    /**
     * Login failure (login request acknowledgment).
     * <ul>
     * <li> (String) reason
     * </ul>
     */
    final byte LOGIN_FAILURE = 0x12;

    /**
     * Reconnection request.
     * <ul>
     * <li> (ByteArray) reconnectionKey
     * </ul>
     */
    final byte RECONNECT_REQUEST = 0x20;

    /**
     * Reconnect success (reconnection request acknowledgment).
     * <ul>
     * <li> (ByteArray) reconnectionKey
     * </ul>
     */
    final byte RECONNECT_SUCCESS = 0x21;

    /**
     * Reconnect failure (reconnection request acknowledgment).
     * <ul>
     * <li> (String) reason
     * </ul>
     */
    final byte RECONNECT_FAILURE = 0x22;

    /**
     * Session message.  Maximum length is 64 KB minus one byte.
     * Larger messages require fragmentation and reassembly above
     * this protocol layer.
     *
     * <ul>
     * <li> (long) sequence number
     * <li> (ByteArray) message
     * </ul>
     */
    final byte SESSION_MESSAGE = 0x30;


    /**
     * Logout request.
     */
    final byte LOGOUT_REQUEST = 0x40;

    /**
     * Logout success (logout request acknowledgment).
     */
    final byte LOGOUT_SUCCESS = 0x41;

    /**
     * Channel join.
     * <ul>
     * <li> (String) channel name
     * </ul>
     */
    final byte CHANNEL_JOIN = 0x50;

    /**
     * Channel leave.
     * <ul>
     * <li> (String) channel name
     * </ul>
     */
    final byte CHANNEL_LEAVE = 0x52;
    
    /**
     * Channel send request.
     * <ul>
     * <li> (String) channel name
     * <li> (long) sequence number
     * <li> (short) number of recipients (0 = all)
     * <li> If number of recipients > 0, for each recipient:
     * <ul>
     * <li> (ByteArray) sessionId
     * </ul>
     * <li> (ByteArray) message
     * </ul>
     */
    final byte CHANNEL_SEND_REQUEST = 0x53;

    /**
     * Channel message (to recipient on channel).
     * <ul>
     * <li> (String) channel name
     * <li> (long) sequence number
     * <li> (ByteArray) sender's sessionId (zero-length if sent by server)
     * <li> (ByteArray) message
     * </ul>
     */
    final byte CHANNEL_MESSAGE = 0x54;

}
