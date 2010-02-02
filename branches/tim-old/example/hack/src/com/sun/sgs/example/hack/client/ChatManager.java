/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.client;

//import com.sun.gi.comm.users.client.ClientChannel;

import com.sun.sgs.client.ClientChannel;

//import com.sun.gi.utils.SGSUUID;

import com.sun.sgs.client.SessionId;

import java.nio.ByteBuffer;

import java.util.HashSet;
import java.util.Map;


/**
 * This class manages chat communications, acting both as a listener notifier
 * for incoming messages and as a broadcast point for outgoing messages.
 */
public class ChatManager implements ChatListener
{

    // the set of listeners
    private HashSet<ChatListener> listeners;

    // the chanel for both directions of communications
    private ClientChannel channel;

    /**
     * Creates an instance of <code>ChatManager</code>.
     */
    public ChatManager() {
        listeners = new HashSet<ChatListener>();
        channel = null;
    }

    /**
     * Sets the channel that is used for incoming and outgoing communication.
     *
     * @param channel the communications channel
     */
    public void setChannel(ClientChannel channel) {
        this.channel = channel;
    }

    /**
     * Adds a listener to the set that will be notified when a message
     * arrives at this manager.
     *
     * @param listener the chat message listener
     */
    public void addChatListener(ChatListener listener) {
        listeners.add(listener);
    }

    /**
     * Sends a broadcast chat message to all participants on the current
     * channel.
     *
     * @param message the chat message to send
     */
    public void sendMessage(String message) {
        try {
            if (channel != null)
                channel.send(message.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Called when a player joins the chat group. This notifies all of the
     * registered listeners.
     *
     * @param uid the identifier for the player
     */
    public void playerJoined(SessionId uid) {
        for (ChatListener listener : listeners)
            listener.playerJoined(uid);
    }

    /**
     * Called when a player leaves the chat group. This notifies all of the
     * registered listeners.
     *
     * @param uid the identifier for the player
     */
    public void playerLeft(SessionId uid) {
        for (ChatListener listener : listeners)
            listener.playerLeft(uid);
    }

    /**
     * Notify the manager when a message has arrived. This notifies all of
     * the registered listeners.
     *
     * @param sender the id of the sender
     * @param message the messsage itself
     */
    public void messageArrived(SessionId sender, String message) {
        for (ChatListener listener : listeners)
            listener.messageArrived(sender, message);
    }

    /**
     * Notifies the manager about some set of mappings from identifier
     * to user name. This notifies all of the registered listeners.
     */
    public void addUidMappings(Map<SessionId,String> uidMap) {
        for (ChatListener listener : listeners)
            listener.addUidMappings(uidMap);
    }

}
