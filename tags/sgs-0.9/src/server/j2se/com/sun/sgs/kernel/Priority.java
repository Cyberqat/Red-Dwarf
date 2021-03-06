/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.kernel;


/**
 * The priority descriptions for the system.
 *
 * @since 1.0
 * @author David Jurgens
 */
public enum Priority {

    /**
     * The highest priority for tasks.
     */
    HIGH        (256) {
        public Priority higher() { return HIGH; }
        public Priority lower()  { return MEDIUM_HIGH; }
    },

    /**
     * A medium high priority for tasks.
     */
    MEDIUM_HIGH (192) { 
        public Priority higher() { return HIGH; }
        public Priority lower()  { return MEDIUM; }
    },

    /**
     * The default priority for tasks.
     */
    MEDIUM      (128) {
        public Priority higher() { return MEDIUM_HIGH; }
        public Priority lower()  { return MEDIUM_LOW; }
    },

    /**
     * A medium low priority for tasks.
     */
    MEDIUM_LOW  (64) {
        public Priority higher() { return MEDIUM; }
        public Priority lower()  { return LOW; }
    },

    /**
     * The lowest priority for tasks.
     */
    LOW         (16) {
        public Priority higher() { return MEDIUM_LOW; }
        public Priority lower()  { return LOW; }
    };

    // The numeric value of this priority
    private final int value;
    
    /**
     * The private constructor that ensures no additional
     * <code>Priority</code> types can ever be created.
     */
    private Priority(int value) {
        this.value = value;
    }

    /**
     * Returns the numeric value that backs this priority.  These
     * values are only important in deciding the relative distance
     * between priorities.
     *
     * @return the numeric value of this priority
     */
    public int getValue() {
        return value;
    }

    /**
     * Returns the default <code>Priority</code> used by the system.
     *
     * @return the default <code>Priority</code>
     */
    public static Priority getDefaultPriority() {
        return MEDIUM;
    }

    /**
     * Returns the priority that is higher than this priority,
     * or this priority if this priority is the highest priority
     * defined.
     *
     * @return the priority higher than this priority, or this
     *         priority if this is the highest priority
     */
    public abstract Priority higher();

    /**
     * Returns the priority that is lower than this priority,
     * or this priority if this priority is the lowest priority
     * defined.
     *
     * @return the priority lower than this priority, or this
     *         priority if this is the lowest priority
     */
    public abstract Priority lower();

}
