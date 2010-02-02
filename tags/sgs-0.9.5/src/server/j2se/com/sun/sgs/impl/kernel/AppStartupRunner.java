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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.NameNotBoundException;

import com.sun.sgs.kernel.KernelRunnable;

import com.sun.sgs.service.DataService;

import java.util.Properties;


/**
 * This implementation of <code>KernelRunnable</code> is one of two runnables
 * used when an application is starting up. This runnable is resposible for
 * calling the application's listener, which actually starts the application
 * running, and then reporting the successful startup to the kernel. This
 * runnable is typically scheduled by <code>ServiceConfigRunner</code>.
 * <p>
 * This runnable must be run in a transactional context.
 */
class AppStartupRunner implements KernelRunnable {

    // the type of this class
    private static final String BASE_TYPE = AppStartupRunner.class.getName();

    // the context in which this will run
    private final AppKernelAppContext appContext;

    // the properties for the application
    private final Properties properties;

    /**
     * Creates an instance of <code>AppStartupRunner</code>.
     *
     * @param appContext the context in which the application will run
     * @param properties the <code>Properties</code> to provide to the
     *                   application on startup
     */
    AppStartupRunner(AppKernelAppContext appContext, Properties properties) {
        this.appContext = appContext;
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     */
    public String getBaseTaskType() {
        return BASE_TYPE;
    }

    /**
     * Starts the application.
     *
     * @throws Exception if anything fails in starting the application
     */
    public void run() throws Exception {
        DataService dataService = appContext.getService(DataService.class);
        try {
            // test to see if this name if the listener is already bound...
            dataService.getServiceBinding(StandardProperties.APP_LISTENER,
                                          AppListener.class);
        } catch (NameNotBoundException nnbe) {
            // ...if it's not, create and then bind the listener
            String appClass =
                properties.getProperty(StandardProperties.APP_LISTENER);
            AppListener listener =
                (AppListener)(Class.forName(appClass).newInstance());
            dataService.setServiceBinding(StandardProperties.APP_LISTENER,
                                          listener);

            // since we created the listener, we're the first one to
            // start the app, so we also need to start it up
            listener.initialize(properties);
        }
    }

}
