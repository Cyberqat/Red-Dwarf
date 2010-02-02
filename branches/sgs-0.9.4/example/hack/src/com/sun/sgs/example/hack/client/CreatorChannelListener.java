/*
 * Copyright 2007 Sun Microsystems, Inc.
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


package com.sun.sgs.example.hack.client;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.SessionId;

import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.GameMembershipDetail;

import java.io.IOException;

import java.nio.ByteBuffer;


/**
 * This class listens for all messages from the creator game.
 */
public class CreatorChannelListener extends GameChannelListener
{

    // the listener that consumes creator messages
    private CreatorListener clistener;

    /**
     * Creates an instance of <code>CreatorChannelListener</code>.
     *
     * @param creatorListener listener for creator messages
     * @param chatListener listener for chat messages
     */
    public CreatorChannelListener(CreatorListener creatorListener,
                                  ChatListener chatListener) {
        super(chatListener);

        this.clistener = creatorListener;
    }

    /**
     * Notifies this listener that some data has arrived from a given
     * player. This should only be called with messages that pertain to
     * the creator.
     *
     * @param from the ID of the sending player.
     * @param data the packet data
     * @param reliable true if this packet was sent reliably
     */
    //public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
    public void receivedMessage(ClientChannel channel, SessionId sender,
                                byte [] message) {
        ByteBuffer data = ByteBuffer.allocate(message.length);
        data.put(message);
        data.rewind();

        if (sender == null) {
            // if this is a message from the server, then it's some
            // command that we need to process, so get the command code
            int command = (int)(data.get());

            // FIXME: this should really be an enumeration
            try {
                switch (command) {
                case 0:
                    // we got some uid to player name mapping
                    addUidMappings(data);
                    break;
                case 8:
                    notifyJoinOrLeave(data, true);
                    break;
                case 9:
                    notifyJoinOrLeave(data, true);
                    break;
                case 64:
                    // we got some new character stats
                    int id = data.getInt();
                    CharacterStats stats = (CharacterStats)(getObject(data));
                    clistener.changeStatistics(id, stats);
                    break;
                default:
                    // FIXME: we should handle this more gracefully
                    System.out.println("Unexpected lobby message: " + command);
                }
            } catch (IOException ioe) {
                // FIXME: this should probably handle the error a little more
                // gracefully, but it's unclear what the right approach is
                System.out.println("Failed to handle incoming creator object");
                ioe.printStackTrace();
            }
        } else {
            // this isn't a message from the server, so it came from some
            // other player on our channel...in this game, that can only
            // mean that we got a chat message
            notifyChatMessage(sender, data);
        }
    }

}
