/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.util;

import java.util.Properties;

/**
 * Wrapper around a {@link Properties} that provides convenience methods for
 * accessing primitives.
 */
public class PropertiesWrapper {

    /** The underlying properties. */
    private final Properties properties;

    /**
     * Creates an instance that delegates to the given <code>Properties</code>.
     *
     * @param	properties the <code>Properties</code> to wrap
     */
    public PropertiesWrapper(Properties properties) {
	if (properties == null) {
	    throw new NullPointerException("The argument must not be null");
	}
	this.properties = properties;
    }
    
    /**
     * Returns the associated <code>Properties</code>.
     *
     * @return	the associated <code>Properties</code>
     */
    public Properties getProperties() {
	return properties;
    }

    /**
     * Returns the value of a property as a <code>String</code>, or
     * <code>null</code> if the property is not found.
     *
     * @param	name the property name
     * @return	the value or <code>null</code>
     */
    public String getProperty(String name) {
	return properties.getProperty(name);
    }

    /**
     * Returns the value of a property as a <code>String</code>, or the default
     * value if the property is not found.
     *
     * @param	name the property name
     * @param	defaultValue the default value
     * @return	the value
     */
    public String getProperty(String name, String defaultValue) {
	return properties.getProperty(name, defaultValue);
    }

    /**
     * Returns the value of a <code>boolean</code> property.
     *
     * @param	name the property name
     * @param	defaultValue the default value
     * @return	the value
     */
    public boolean getBooleanProperty(String name, boolean defaultValue) {
	String value = properties.getProperty(name);
	return value == null ? defaultValue : Boolean.valueOf(value);
    }

    /**
     * Returns the value of an <code>int</code> property.
     *
     * @param	name the property name
     * @param	defaultValue the default value
     * @return	the value
     * @throws	NumberFormatException if the value does not contain a parsable
     *		<code>int</code>
     */
    public int getIntProperty(String name, int defaultValue) {
	String value = properties.getProperty(name);
	if (value == null) {
	    return defaultValue;
	}
	try {
	    return Integer.parseInt(value);
	} catch (NumberFormatException e) {
	    throw (NumberFormatException) new NumberFormatException(
		"The value of the " + name + " property must be a valid " +
		"int: \"" + value + "\"").initCause(e);
	}
    }

    /**
     * Returns the value of an <code>long</code> property.
     *
     * @param	name the property name
     * @param	defaultValue the default value
     * @return	the value
     * @throws	NumberFormatException if the value does not contain a parsable
     *		<code>long</code>
     */
    public long getLongProperty(String name, long defaultValue) {
	String value = properties.getProperty(name);
	if (value == null) {
	    return defaultValue;
	}
	try {
	    return Long.parseLong(value);
	} catch (NumberFormatException e) {
	    throw (NumberFormatException) new NumberFormatException(
		"The value of the " + name + " property must be a valid " +
		"long: \"" + value + "\"").initCause(e);
	}
    }
}	
