/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.service.NoNodesAvailableException;
import com.sun.sgs.impl.service.nodemap.policy.RoundRobinPolicy;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.test.util.SimpleTestIdentityAuthenticator.DummyIdentity;
import java.util.Properties;

public class DirectiveNodeAssignmentPolicy extends RoundRobinPolicy {
    
    private final NodeMappingServerImpl server;

    private volatile boolean roundRobin = true;

    public static volatile DirectiveNodeAssignmentPolicy instance;
    
    /** Creates a new instance of RoundRobinPolicy */
    public DirectiveNodeAssignmentPolicy(
	Properties props, NodeMappingServerImpl server)
    {
	super(props);
        this.server = server;
	instance = this;
    }

    public void setRoundRobin(boolean roundRobin) {
	this.roundRobin = roundRobin;
    }
    
    /** {@inheritDoc} */
    @Override
    public long chooseNode(long requestingNode)
        throws NoNodesAvailableException 
    {
	if (roundRobin || requestingNode == -1) {
	    return super.chooseNode(requestingNode);
	} else {
	    return requestingNode;
	}
    }

    /**
     * Assigns the identity with the specified {@code name} from {@code
     * oldNode} to {@code newNode}.
     */
    public void moveIdentity(String name, long oldNode, long newNode)
	throws Exception
    {
	boolean saveRoundRobin = roundRobin;
	try {
	    roundRobin = false;
	    GetNodeTask task =
		new GetNodeTask(server.watchdogService, oldNode);
	    server.runTransactionally(task);
	    server.mapToNewNode(new DummyIdentity(name), null,
				task.node, -1, newNode);
	} finally {
	    roundRobin = saveRoundRobin;
	}
    }
    
    /**
     *  Task to support arbitrary identity movement.  Returns the 
     *  first identity on a given nodeId, and looks up the {@code Node}
     *  for the nodeId.
     */
    static class GetNodeTask extends AbstractKernelRunnable {

        private final long nodeId;
        private final WatchdogService watchdogService;
	Node node = null;
        
        GetNodeTask(WatchdogService watchdogService, long nodeId) {
	    super(null);
            this.watchdogService = watchdogService;
            this.nodeId = nodeId;
        }
        
        public void run() throws Exception {
	    node = watchdogService.getNode(nodeId);
        }
    }
}
