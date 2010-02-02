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

package com.sun.sgs.impl.service.watchdog;

import java.io.IOException;
import java.rmi.Remote;

/**
 * A remote interface for callbacks from the Watchdog server.
 */
public interface WatchdogClient extends Remote {

    /**
     * Notifies this client that the nodes specified by corresponding
     * information in the {@code ids}, {@code hosts}, {@code instances},
     * {@code status}, and {@code backups} arrays have a status change 
     * ({@code true} for alive, and {@code false} for failed) and may need to
     * recover (if the backup ID is equal to the local node ID). The
     * {@code backups} array is only only consulted if the corresponding
     * element in {@code status} is {@code false}.  If no node has been
     * assigned as a backup, it is indicated by 
     * {@value com.sun.sgs.impl.service.watchdog.NodeImpl#INVALID_ID}.
     *
     * @param	ids an array of node IDs
     * @param	hosts an array of host names
     * @param   instances an array of node instances
     * @param	status an array of node status
     * @param	backups an array of backup node IDs
     *
     * @throws	IllegalArgumentException if array lengths don't match
     * @throws	IOException if a communication problem occurs while
     * 		invoking this method
     */
    void nodeStatusChanges(long[] ids, String[] hosts, int instances[],
			   boolean[] status, long[] backups)
	throws IOException;
}