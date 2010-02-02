/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import com.sun.sgs.example.hack.share.CharacterStats;


/**
 * The is the <code>Character</code> interface. All interactive things in a
 * game (players, NPCs, and monsters) are <code>Character</code>s. This
 * interface not extends <code>GLO</code> because the typical use of
 * <code>Character</code> implementations is as state associated with
 * <code>GLO</code> that is shared only for processing specific commands.
 */
public interface Character {

    /**
     * Returns this entity's identifier. Typically this maps to the sprite
     * used on the client-side to render this entity.
     *
     * @return the identifier
     */
    public int getID();

    /**
     * Returns the name of this entity.
     *
     * @return the name
     */
    public String getName();

    /**
     * Returns the statistics associated with this character.
     *
     * @return the character's statistics
     */
    public CharacterStats getStatistics();

    /**
     * This method tells the character that their stats have changed. This
     * could be the result of any number of actions, but is often the
     * result of a retaliatory attack.
     */
    public void notifyStatsChanged();

    /**
     * Called when the given character collides with us. This typically
     * uses a double-dispatch model where we call back the other
     * character through their <code>collidedInto</code> method.
     *
     * @param character the character that collided with us
     *
     * @return the result of processing our interaction
     */
    public ActionResult collidedFrom(Character character);

    /**
     * Called when you collide with the character.
     *
     * @param character the character that we collided with
     *
     * @return true if this caused our stats to change, false otherwise
     */
    public boolean collidedInto(Character character);

    /**
     * Sends a text message to the character's manager.
     *
     * @param message the message to send
     */
    public void sendMessage(String message);

}
