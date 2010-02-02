/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.share;

import java.io.Serializable;


/**
 * This class represents the basic statistics and information associated
 * with characters.
 */
public class CharacterStats implements Serializable {

    private static final long serialVersionUID = 1;

    // the name of the character
    private String name;

    // the numeric statistics for the character
    private int strength;
    private int intelligence;
    private int dexterity;
    private int wisdom;
    private int constitution;
    private int charisma;
    private int hitPoints;
    private int maxHitPoints;

    /**
     * Creates an instance of <code>CharacterStats</code>.
     *
     * @param name the character's name
     * @param strength the character's strength
     * @param intelligence the character's intelligence
     * @param dexterity the character's dexterity
     * @param wisdom the character's wisdom
     * @param constitution the character's constitution
     * @param charisma the character's charisma
     * @param hitPoints the character's hitPoints
     * @param maxHitPoints the character's maxHitPoints
     */
    public CharacterStats(String name, int strength, int intelligence,
                          int dexterity, int wisdom, int constitution,
                          int charisma, int hitPoints, int maxHitPoints) {
        this.name = name;
        this.strength = strength;
        this.intelligence = intelligence;
        this.dexterity = dexterity;
        this.wisdom = wisdom;
        this.constitution = constitution;
        this.charisma = charisma;
        this.hitPoints = hitPoints;
        this.maxHitPoints = maxHitPoints;
    }

    /**
     *
     */
    public String getName() {
        return name;
    }

    /**
     *
     */
    public int getStrength() {
        return strength;
    }

    /**
     *
     */
    public void setStrength(int strength) {
        this.strength = strength;
    }

    /**
     *
     */
    public int getIntelligence() {
        return intelligence;
    }

    /**
     *
     */
    public void setIntelligence(int intelligence) {
        this.intelligence = intelligence;
    }

    /**
     *
     */
    public int getDexterity() {
        return dexterity;
    }

    /**
     *
     */
    public void setDexterity(int dexterity) {
        this.dexterity = dexterity;
    }

    /**
     *
     */
    public int getWisdom() {
        return wisdom;
    }

    /**
     *
     */
    public void setWisdom(int wisdom) {
        this.wisdom = wisdom;
    }

    /**
     *
     */
    public int getConstitution() {
        return constitution;
    }

    /**
     *
     */
    public void setConstitution(int constitution) {
        this.constitution = constitution;
    }

    /**
     *
     */
    public int getCharisma() {
        return charisma;
    }

    /**
     *
     */
    public void setCharisma(int charisma) {
        this.charisma = charisma;
    }

    /**
     *
     */
    public int getHitPoints() {
        return hitPoints;
    }

    /**
     *
     */
    public void setHitPoints(int hitPoints) {
        this.hitPoints = hitPoints;
    }

    /**
     *
     */
    public int getMaxHitPoints() {
        return maxHitPoints;
    }

    /**
     *
     */
    public void setMaxHitPoints(int maxHitPoints) {
        this.maxHitPoints = maxHitPoints;
    }

}
