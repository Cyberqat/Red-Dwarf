/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */


package com.sun.sgs.example.hack.client;

/*import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;*/

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;

import com.sun.sgs.impl.util.HexDumper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import java.nio.ByteBuffer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * This abstract class is the base for all game-specific listeners.
 */
public abstract class GameChannelListener implements ClientChannelListener
{

    // the chat listener that accepts all incoming chat messages
    private ChatListener chatListener;

    /**
     * Creates an instance of <code>GameChannelListener</code>.
     */
    protected GameChannelListener(ChatListener chatListener) {
        this.chatListener = chatListener;
    }

    /**
     * NOTE that this is part of the new API, but it was never needed
     * under the EA APIs for this client, so for now it's just implemented
     * here and ignored..
     */
    public void leftChannel(ClientChannel channel) {
        
    }

    /**
     *
     */
    protected void notifyJoinOrLeave(ByteBuffer data, boolean joined) {
        byte [] bytes = new byte[data.remaining()];
        data.get(bytes);
        SessionId sessionId = SessionId.fromBytes(bytes);
        if (joined)
            chatListener.playerJoined(sessionId);
        else
            chatListener.playerLeft(sessionId);
    }

    /**
     * Notifies this listener that a chat message arrived from the
     * given player.
     *
     * @param playerID the player's identifier
     * @param data the chat message
     */
    protected void notifyChatMessage(SessionId session, ByteBuffer data) {
        byte [] bytes = new byte[data.remaining()];
        data.get(bytes);
        String message = new String(bytes);
        chatListener.messageArrived(session, message);
    }

    /**
     * Notifies this listener of new user identifier mappings.
     *
     * @param data encoded mapping from user identifier to string
     */
    protected void addUidMappings(ByteBuffer data) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String,String> map = (Map<String,String>)(getObject(data));
        HashMap<SessionId,String> sessionMap = new HashMap<SessionId,String>();
        for (String hexString : map.keySet()) {
            SessionId sid = SessionId.fromBytes(HexDumper.fromHexString(hexString));
            sessionMap.put(sid, map.get(hexString));
        }
        chatListener.addUidMappings(sessionMap);
    }

    /**
     * Retrieves a serialized object from the given buffer.
     *
     * @param data the encoded object to retrieve
     */
    protected Object getObject(ByteBuffer data) throws IOException {
        try {
            byte [] bytes = new byte[data.remaining()];
            data.get(bytes);

            ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bin);
            return ois.readObject();
        } catch (ClassNotFoundException cnfe) {
            throw new IOException(cnfe.getMessage());
        }
    }

}
