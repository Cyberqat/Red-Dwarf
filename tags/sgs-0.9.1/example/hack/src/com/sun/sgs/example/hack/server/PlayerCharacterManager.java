/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.level.Level;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;

import java.io.Serializable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;


/**
 * This is an implementation of <code>CharacterManager</code> used to manage
 * <code>PlayerCharacter</code>s. This manager can contain any number of
 * characters, though only one is ever the current one playing.
 */
public class PlayerCharacterManager extends BasicCharacterManager
    implements Serializable {

    private static final long serialVersionUID = 1;

    // a reference to the owning player
    private ManagedReference playerRef;

    // the characters currently owned by this manager
    private HashMap<String,PlayerCharacter> characterMap;

    // the currently playing character
    private PlayerCharacter currentCharacter;

    /**
     * Creates an instance of <code>PlayerCharacterManager</code>.
     *
     * @param playerRef a reference to the owning <code>Player</code>
     */
    public PlayerCharacterManager(Player player) {
        super("player:" + player.getName());

        playerRef = AppContext.getDataManager().createReference(player);

        characterMap = new HashMap<String,PlayerCharacter>();
        currentCharacter = null;
    }

    /**
     * Returns a reference to the <code>Player</code> that owns this manager.
     *
     * @return the owning <code>Player</code>
     */
    public Player getPlayer() {
        return playerRef.get(Player.class);
    }

    /**
     * Sets the current character, if the given name is known.
     *
     * @param characterName the name of the character
     *
     * @return true if the current character was set, false otherwise
     */
    public boolean setCurrentCharacter(String characterName) {
        AppContext.getDataManager().markForUpdate(this);

        currentCharacter = characterMap.get(characterName);

        return (currentCharacter != null);
    }

    /**
     * Returns the current character being played through this manager.
     *
     * @return the current character
     */
    public Character getCurrentCharacter() {
        return currentCharacter;
    }

    /**
     * Returns the number of characters managed by this class.
     *
     * @return the number of manager characters
     */
    public int getCharacterCount() {
        return characterMap.size();
    }

    /**
     * Returns the characters managed by this class.
     *
     * @return the managed characters
     */
    public Collection<PlayerCharacter> getCharacters() {
        return characterMap.values();
    }

    /**
     * Returns the names of the characters managed by this class.
     *
     * @return the names of the managed characters 
     */
    public Set<String> getCharacterNames() {
        return characterMap.keySet();
    }

    /**
     * Adds a character to this manager.
     *
     * @param character the character to add
     */
    public void addCharacter(PlayerCharacter character) {
        AppContext.getDataManager().markForUpdate(this);

        characterMap.put(character.getName(), character);
    }

    /**
     * Tries to remove the given sharacter from the manager.
     *
     * @param name the character to remove
     */
    public void removeCharacter(String name) {
        AppContext.getDataManager().markForUpdate(this);

        characterMap.remove(name);
    }

    /**
     * Sends the given board to the player.
     *
     * @param board the board to send
     */
    public void sendBoard(Board board) {
        playerRef.get(Player.class).sendBoard(board);
    }

    /**
     * Sends space updates to the player.
     *
     * @param updates the updates to send
     */
    public void sendUpdate(Collection<BoardSpace> updates) {
        playerRef.get(Player.class).sendUpdate(updates);
    }

    /**
     * Sends the current character's stats to the player.
     */
    public void sendCharacter() {
        playerRef.get(Player.class).sendCharacter(currentCharacter);
    }

}
