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

package com.sun.sgs.impl.kernel;

import java.util.MissingResourceException;

import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagerNotFoundException;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.kernel.KernelAppContext;

import com.sun.sgs.service.Service;


/**
 * Abstract class that is the base representation of a task's context. All
 * actions run in either the context of an application or the system. The
 * former is represented by <code>AppKernelAppContext</code> and the latter
 * by <code>SystemKernelAppContext</code>.
 */
abstract class AbstractKernelAppContext implements KernelAppContext {

    // the application's name and cached hash code
    private final String applicationName;
    private final int applicationCode;

    /**
     * Creates an instance of <code>AbstractKernelAppContext</code>.
     *
     * @param applicationName the name of the application represented by
     *                        this context
     */
    AbstractKernelAppContext(String applicationName) {
        this.applicationName = applicationName;

        // the hash code is the hash of the application name, which never
        // changes, so the hash code gets pre-cached here
        applicationCode = applicationName.hashCode();
    }

    /**
     * Returns the <code>ChannelManager</code> used in this context.
     *
     * @return the context's <code>ChannelManager</code>
     */
    abstract ChannelManager getChannelManager();

    /**
     * Returns the <code>DataManager</code> used in this context.
     *
     * @return the context's <code>DataManager</code>
     */
    abstract DataManager getDataManager();

    /**
     * Returns the <code>TaskManager</code> used in this context.
     *
     * @return the context's <code>TaskManager</code>
     */
    abstract TaskManager getTaskManager();

    /**
     * Returns a manager based on the given type. If the manager type is
     * unknown, or if there is more than one manager of the given type,
     * <code>ManagerNotFoundException</code> is thrown. This may be used
     * to find any available manager, including the three standard
     * managers.
     *
     * @param type the <code>Class</code> of the requested manager
     *
     * @return the requested manager
     *
     * @throws ManagerNotFoundException if there wasn't exactly one match to
     *                                  the requested type
     */
    abstract <T> T getManager(Class<T> type);

    /**
     * Returns a <code>Service</code> based on the given type. If the type is
     * unknown, or if there is more than one <code>Service</code> of the
     * given type, <code>MissingResourceException</code> is thrown. This is
     * the only way to resolve service components directly, and should be
     * used with care, as <code>Service</code>s should not be resolved and
     * invoked directly outside of a transactional context.
     *
     * @param type the <code>Class</code> of the requested <code>Service</code>
     *
     * @return the requested <code>Service</code>
     *
     * @throws MissingResourceException if there wasn't exactly one match to
     *                                  the requested type
     */
    abstract <T extends Service> T getService(Class<T> type);

    /**
     * Returns a unique representation of this context, in this case the
     * name of the application.
     *
     * @return a <code>String</code> representation of the context
     */
    public String toString() {
        return applicationName;
    }

    /**
     * Returns <code>true</code> if the provided object is an instance of
     * <code>AbstractKernelAppContext</code> that represents the same
     * application context.
     *
     * @param o an instance of <code>AbstractKernelAppContext</code>
     *
     * @return <code>true</code> if the provided object represents the same
     *         context as this object, <code>false</code> otherwise
     */
    public boolean equals(Object o) {
        if ((o == null) || (! (o instanceof AbstractKernelAppContext)))
            return false;

        AbstractKernelAppContext other = (AbstractKernelAppContext)o;

        return other.applicationName.equals(applicationName);
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return applicationCode;
    }

}
