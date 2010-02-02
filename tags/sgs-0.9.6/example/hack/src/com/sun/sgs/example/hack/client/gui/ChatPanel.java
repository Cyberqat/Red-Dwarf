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

package com.sun.sgs.example.hack.client.gui;

import com.sun.sgs.example.hack.client.ChatListener;
import com.sun.sgs.example.hack.client.ChatManager;

import java.awt.BorderLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;


/**
 * This implements a simple chat front-end with a text field for sending
 * messages and a larger text area for displaying messages. It is driven by
 * a listener/manager model, so you can hook this panel up to any backing
 * system you like.
 */
public class ChatPanel extends JPanel implements ActionListener, ChatListener
{

    private static final long serialVersionUID = 1;

    // the display area
    private JTextArea textArea;

    // the entry field
    private JTextField textField;

    // the manager that we notify with chat messages
    private ChatManager chatManager;

    // the panel that we re-focus when we done typing
    private JComponent focusPanel;

    // the mapping from uid to name
    private Map<BigInteger,String> uidMap;

    // the client's current session id
    private BigInteger currentSession;

    /**
     * Creates a <code>ChatManager</code>.
     *
     * @param chatManager the manager that receives chat messages
     * @param focusPanel the panel that shares focus with us
     */
    public ChatPanel(ChatManager chatManager, JComponent focusPanel) {
        super(new BorderLayout(4, 4));

        uidMap = new HashMap<BigInteger,String>();

        // track the manager, and add ourselves as a listener
        this.chatManager = chatManager;
        chatManager.addChatListener(this);

        this.focusPanel = focusPanel;

        // create a 7-column display area
        textArea = new JTextArea();
        textArea.setRows(7);
        textArea.setLineWrap(true);
        textArea.setEditable(false);

        // create the entry field, and capture return key-presses
        textField = new JTextField();
        textField.addActionListener(this);

        add(new JScrollPane(textArea), BorderLayout.CENTER);
        add(textField, BorderLayout.SOUTH);
    }

    /**
     *
     */
    public void setSessionId(BigInteger session) {
        uidMap.remove(currentSession);
        uidMap.put(session, "[You]");
        currentSession = session;
    }

    /**
     * Clears all the current messages in the display area.
     */
    public void clearMessages() {
        textArea.setText("");
    }

    /**
     * Called when return is typed from the entry field.
     *
     * @param e details about the action
     */
    public void actionPerformed(ActionEvent e) {
        String message = textField.getText();

        // get the current text and send it off, and clear the entry field
        chatManager.sendMessage(message);
        messageArrived(currentSession, message);
        textField.setText("");

        // return focus to the game panel
        focusPanel.requestFocusInWindow();
    }

    /**
     * Called when a player joins the chat.
     *
     * @param uid the identifier of the player that joined
     */
    public void playerJoined(BigInteger uid) {
        if (uidMap.containsKey(uid))
            textArea.append(uidMap.get(uid) + ": *joined*\n");
    }

    /**
     * Called when a player leaves the chat.
     *
     * @param uid the identifier of the player that left
     */
    public void playerLeft(BigInteger uid) {
        if (uidMap.containsKey(uid))
            textArea.append(uidMap.get(uid) + ": *left*\n");
    }

    /**
     * Callback that is invoked when a message arrives.
     *
     * @param sender the name of the sender
     * @param message the message itself
     */
    public void messageArrived(BigInteger sender, String message) {
        if (uidMap.containsKey(sender))
            textArea.append(uidMap.get(sender) + ": " + message + "\n");
    }

    /**
     * Called when there is new information about the mapping from user
     * identifiers to user names.
     *
     * @param uidMap the mapping from identifiers to names
     */
    public void addUidMappings(Map<BigInteger,String> uidMap) {
        this.uidMap.putAll(uidMap);
    }

}
