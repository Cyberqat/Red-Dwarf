/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.server.ai;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.level.Level;
import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import com.sun.sgs.example.hack.server.Character;
import com.sun.sgs.example.hack.server.CharacterManager;

import com.sun.sgs.example.hack.share.CharacterStats;

import java.io.Serializable;


/**
 * This is a very simple monster that implements the "yellow box" logic
 * for the game of secret collect. It does little more than die as soon
 * as a character collides with it. While on its own this isn't very
 * useful, this could be the base for more interesting functionality.
 */
public class CollectMonster extends MonsterCharacter implements Serializable {

    private static final long serialVersionUID = 1;

    // our stats, which are used only as a placeholder
    private CharacterStats stats;

    /**
     * Creates an instance of <code>CollectMonster</code> using the
     * default identifier.
     *
     * @param mgrRef a reference to our manager
     */
    public CollectMonster(AICharacterManager mgr) {
        this(4, mgr);
    }

    /**
     * Creates an instance of <code>CollectMonster</code> based on the
     * given identifier.
     *
     * @param id the identifier for this monster
     * @param mgrRef a reference to our manager
     */
    public CollectMonster(int id, AICharacterManager mgr) {
        super(id, "yellow thing", mgr);

        stats = new CharacterStats("yellow thing", 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Returns the statistics associated with this character.
     *
     * @return the character's statistics
     */
    public CharacterStats getStatistics() {
        return stats;
    }

    /**
     * Called when the given character collides with us. We never attack,
     * always hold our ground, and reply with some message
     *
     * @param character the character that collided with us
     *
     * @return <code>ActionResult.SUCCESS</code>
     */
    public ActionResult collidedFrom(Character character) {
        notifyStatsChanged();

        return ActionResult.SUCCESS;
    }

    /**
     * Called when you collide with the character. This always returns false,
     * since we do nothing when we hit other characters, and because we don't
     * move, so we should be able to initiate fighting.
     *
     * @param character the character that we collided with
     *
     * @return <code>false</code>
     */
    public boolean collidedInto(Character character) {
        // we don't move, so we never collide into people
        return false;
    }

    /**
     * Sends a text message to the character's manager.
     *
     * @param message the message to send
     */
    public void sendMessage(String message) {
        // ignore the messeg
    }

    /**
     * Called periodically, but this is always ignored, because this creature
     * never moves.
     */
    public void run() {
        // we don't move
    }

    /**
     * This has no affect on <code>CollectMonster</code> since thyey have no
     * meaningful statistics.
     */
    public void regenerate() {
        // we have no stats, so there's nothing to re-generate
    }

}
