/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinder;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFinderFailedException;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFactory;
import com.sun.sgs.impl.service.nodemap.affinity.graph.AffinityGraphBuilder;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.TransactionProxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A group coordinator manages groups of identities. Since grouping activities
 * may generate load on the system, the coordinator may be disabled if
 * conditions merit.<p>
 *
 * The group coordinator has two main functions, offloading identities from
 * nodes, and (if the affinity subsystem is configured) collocating identities
 * in an affinity group.<p>
 *
 * When the node mapping server requests an offload, the coordinator will
 * select identities from that node and move them to some other node. If the
 * affinity group subsystem is configured, a group of identities is selected
 * to move. If the sub-system is not configured or there are no groups on the
 * node, then individual identities will be moved. If the node has failed,
 * then all of the groups and identities are moved. Otherwise one group or
 * identity is moved.<p>
 *
 * If the affinity group subsystem is configured, the coordinator will ask
 * the group finder for the set of known groups. The set of affinity groups
 * is refreshed periodically. With each new set the coordinator will iterate
 * over them, looking for groups where not all of its member identities are
 * on the target node for that group. (See RelocatableAffinityGroup for the
 * definition of target node) If such a group is found, the identities in
 * that group which are not on the target node are moved to that node.<p>
 *
 * For a given set of groups returned by the group finder, a group that has
 * been collocated is removed from the set of groups that are eligible for
 * offloading. Likewise, all of the groups on a node to be offloaded are not
 * candidates for collocation. This is to reduce the possibility of moving
 * the same group by both the offload and collocate operations at the same
 * time.<p>
 *
 * A newly constructed coordinator will be in the disabled state.<p>
 *
 * The following property is supported:<p>
 * 
 * <dl style="margin-left: 1em">
 * 
 * <dt>	<i>Property:</i> <code><b>
 *   {@value #UPDATE_FREQ_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value DEFAULT_UPDATE_FREQ} (one minute)
 * <br>
 *
 * <dd style="padding-top: .5em">The frequency that we find affinity groups,
 *  in seconds.  The value must be between {@code 5} and {@code 65535}.<p>
 *
 * <dt>	<i>Property:</i> <code><b>
 *   {@value #COLLOCATE_DELAY_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value DEFAULT_COLLOCATE_DELAY} (milliseconds)
 * <br>
 *
 * <dd style="padding-top: .5em">The delay between collocating groups.  The value
 * must be between {@code 0} and {@code Long.MAX_VALUE}.<p>
 * </dl><p>
 */
class GroupCoordinator extends BasicState {

    /** Package name for this class. */
    private static final String PKG_NAME =
                    "com.sun.sgs.impl.service.nodemap";

    /** The property name for the update frequency. */
    static final String UPDATE_FREQ_PROPERTY = PKG_NAME + ".update.freq";

    /** The default value of the update frequency, in seconds. */
    static final int DEFAULT_UPDATE_FREQ = 60;

    /** The property name for the update frequency. */
    static final String COLLOCATE_DELAY_PROPERTY = PKG_NAME + ".collocate.delay";

    /** The default value of the update frequency, in milliseconds. */
    static final int DEFAULT_COLLOCATE_DELAY = 1000;

    private final LoggerWrapper logger =
                new LoggerWrapper(Logger.getLogger(PKG_NAME + ".coordinator"));

    // Node mapping server
    private final NodeMappingServerImpl server;

    // Data service
    private final DataService dataService;

    // Graph builder
    private final AffinityGraphBuilder builder;

    // Group finder or null if the group subsystem is not configured.
    private final AffinityGroupFinder finder;

    private final TaskScheduler taskScheduler;
    private final Identity taskOwner;
    private final long updatePeriod;

    private RecurringTaskHandle updateTask = null;

    // Task to scan the list of newly found affinity groups looking for
    // identities to collocate. == null if the affinity group sub-system is
    // not running. There is a new task for each set of groups.
    private volatile CollocateTask collocateTask = null;
    private final long collocateDelay;

    // Map of per-node groups. This map is filled-in only when necessary due
    // to a request to offload a node. The sets contain candidates
    // for movement, and once we start trying to offload a group, the group is
    // removed from the Map.
    // The mapping is targetNodeID -> groupSet where groupSet is groupId -> group
    private final Map<Long, NavigableSet<RelocatingAffinityGroup>> nodeSets;

    // Sorted set of all the groups. Access to this set must be synchronized
    // by nodeSets. If a group in this set is modified for any reason (offloading
    // or collocation) that group should be removed from the set.
    private NavigableSet<RelocatingAffinityGroup> groups = null;

    /**
     * Constructor.
     *
     * @param properties server properties
     * @param systemRegistry system registry
     * @param txnProxy transaction proxy
     * @param server node mapping server
     * @param builder the graph builder
     */
    GroupCoordinator(Properties properties,
                     ComponentRegistry systemRegistry,
                     TransactionProxy txnProxy,
                     NodeMappingServerImpl server,
                     AffinityGraphBuilder builder)
        throws Exception
    {
        dataService = txnProxy.getService(DataService.class);
        this.server = server;
        this.builder = builder;

        finder = builder != null ? builder.getAffinityGroupFinder() : null;

        if (finder != null) {
            PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
            int updateSeconds = wrappedProps.getIntProperty(UPDATE_FREQ_PROPERTY,
                                                            DEFAULT_UPDATE_FREQ,
                                                            5, 65535);
            updatePeriod = updateSeconds * 1000L;
            collocateDelay = wrappedProps.getLongProperty(COLLOCATE_DELAY_PROPERTY,
                                                          DEFAULT_COLLOCATE_DELAY,
                                                          0, Long.MAX_VALUE);
            taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
            taskOwner = txnProxy.getCurrentOwner();
            nodeSets =
                    new HashMap<Long, NavigableSet<RelocatingAffinityGroup>>();
            logger.log(Level.CONFIG,
                       "Created GroupCoordinator with finder: "
                       + finder.getClass().getName() +
                       " with properties:" +
                       "\n  " + UPDATE_FREQ_PROPERTY + "=" +
                       updateSeconds + " (seconds)" +
                       "\n  " + COLLOCATE_DELAY_PROPERTY + "=" +
                       collocateDelay + "(milliseconds)");
        } else {
            assert builder == null;
            updatePeriod = 0L;
            collocateDelay = 0L;
            taskScheduler = null;
            taskOwner = null;
            nodeSets = null;
            logger.log(Level.CONFIG,
                       "Created GroupCoordinator with null finder");
        }
    }

    /**
     * Enable coordination. If the coordinator is enabled, calling this method
     * will have no effect.
     */
    public synchronized void enable() {
        if (setEnabledState() && (builder != null)) {
            builder.enable();
            if (finder != null) {
                assert updateTask == null;
                updateTask = taskScheduler.scheduleRecurringTask(
                                    new AbstractKernelRunnable("UpdateTask") {
                                            public void run() {
                                                findGroups();
                                            } },
                                    taskOwner,
                                    System.currentTimeMillis() + updatePeriod,
                                    updatePeriod);
                updateTask.start();
            }
        }
    }

    /**
     * Disable coordination. If the coordinator is disabled, calling this method
     * will have no effect.
     *
     * TODO - should the groups be cleared out? they will be useful for some
     * time but will eventually become stale
     */
    public synchronized void disable() {
        if (setDisabledState() && (builder != null)) {
            if (updateTask != null) {
                updateTask.cancel();
                updateTask = null;
            }
            synchronized (nodeSets) {
                if (collocateTask != null) {
                    collocateTask.stop();
                    collocateTask = null;
                }
            }
            builder.disable();
        }
    }

    /**
     * Shutdown the coordinator. The coordinator is disabled and
     * all resources released. Any further method calls made on the coordinator
     * will result in a {@code IllegalStateException} being thrown.
     */
    public synchronized void shutdown() {
        if (setShutdownState()) {
            if (updateTask != null) {
                updateTask.cancel();
                updateTask = null;
            }
            // Note that the builder is shutdown by the service
        }
    }

    /**
     * Move one or more identities off of a node. If the old node is alive
     * a group of identities will be selected to move. The group selection is
     * based on how the groups are sorted in the node set. If
     * there are no groups to move, a single identity is moved.
     * If the node is not alive, all identities on that node will be moved.
     * Note that is this method does not guarantee that any identities are
     * be moved.<p>
     * 
     * Note that there should only be one active call to offload for a
     * given node, however there is no check. Failing this will likely result
     * in a ConcurrentModificationException.
     * 
     * @param node the node to offload identities
     *
     * @throws NullPointerException if {@code node} is {@code null}
     * @throws NoNodesAvailableException if no nodes are available
     * @throws IllegalStateException if we are shut down
     */
    void offload(Node node) throws NoNodesAvailableException {
        checkForShutdownState();

        if (node == null) {
            throw new NullPointerException("node can not be null");
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Request to offload {0}", node);
        }

        final long nodeId = node.getId();
        Iterator<RelocatingAffinityGroup> iter = getNodeSet(nodeId).iterator();

        while (iter.hasNext()) {
            checkForShutdownState();

            long newNodeId = server.chooseNode();

            // Could happen if things got better, so quit
            if (newNodeId == nodeId) {
                return;
            }
            RelocatingAffinityGroup group = iter.next();
            iter.remove();

            // Re-target the group, note that we do not re-insert the group into
            // the new node's node set (even if there is one).
            group.setTargetNode(newNodeId);
            collocateGroup(group);

            // If the node is alive, then just move off one group
            if (node.isAlive()) {
                return;
            }
        }
        // If we are here either 1) the group sub-system is not running or it is
        // running and no groups were found, in either case offloadSingles()
        // will move an individual identity, or 2) we were moving everyone off,
        // and offloadSingles() will move any remaining identities that were not
        // in a group.
        offloadSingles(node);
    }

    /**
     * Get the set of groups on the specified node.
     *
     * @param nodeId a node ID
     * @return a set of groups, the set may be empty
     */
    private Set<RelocatingAffinityGroup> getNodeSet(long nodeId) {
        // if the group subsystem is not running, just return an empty set
        if (nodeSets == null) {
            return Collections.emptySet();
        }

        synchronized (nodeSets) {
            NavigableSet<RelocatingAffinityGroup> nodeSet =
                                            nodeSets.get(nodeId);
            
            // If there is no set for this node, scan the groups list and
            // remove all groups which have a target node == nodeId
            if (nodeSet == null) {
                // The group order is defined by RelocatingAffinityGroup
                nodeSet = new TreeSet<RelocatingAffinityGroup>();
                nodeSets.put(nodeId, nodeSet);

                Iterator<RelocatingAffinityGroup> iter = groups.iterator();

                while (iter.hasNext()) {
                    RelocatingAffinityGroup group = iter.next();

                    if (group.getTargetNode() == nodeId) {
                        nodeSet.add(group);
                        iter.remove();
                    }
                }
                
                // If the set isn't empty we have modified the groups list,
                // so need to reset the collocate task.
                if (!nodeSet.isEmpty() && (collocateTask != null)) {
                    collocateTask.reset();
                }
            }
            return nodeSet;
        }
    }

    /**
     * Collocate the identities of a group onto the group's target node. If the
     * target node is unknown or no longer available, then pick a new target.
     *
     * @param group the group to collocate
     * @return true if the group has had any identities moved, otherwise false
     * @throws NoNodesAvailableException if the target node is unavailable, or
     *         if the target node is unknown and a new one is not available
     */
    private boolean collocateGroup(final RelocatingAffinityGroup group)
        throws NoNodesAvailableException
    {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Request to collocate {0}", group);
        }

        if (!server.isNodeAvailable(group.getTargetNode())) {
            group.setTargetNode(server.chooseNode());
        }
        final Set<Identity> identities = group.getStragglers();

        if (identities.isEmpty()) {
            return false;
        }
        // TODO - Consider keeping track of these tasks, so that a) they could be
        // aborted if a new collocation iteration comes in, or b) so we can make
        // the next iteration wait until all of the ongoing moves are done.
        taskScheduler.scheduleTask(
                new AbstractKernelRunnable("MoveTask") {
                    public void run() {
                        final long targetNodeId = group.getTargetNode();

                        if (logger.isLoggable(Level.FINER)) {
                            logger.log(Level.FINER,
                                  "moving {0} members of group {1} to node {2}",
                                   identities.size(), group.getId(),
                                   targetNodeId);
                        }
                        for (Identity identity : identities) {
                            if (logger.isLoggable(Level.FINEST)) {
                                logger.log(Level.FINEST,
                                           "moving id {0}", identity);
                            }
                            try {
                                server.moveIdentity(identity, targetNodeId);
                            } catch (NoNodesAvailableException e) {
                                logger.logThrow(Level.FINE, e,
                                            "Target node {0} node no longer available",
                                            targetNodeId);
                                // If the target is not available, all will fail so exit
                                return;
                            } catch (Exception e) {
                                logger.logThrow(Level.FINE, e,
                                                "Exception moving id {0}",
                                                identity);
                            }
                        } } },
                taskOwner);

        return true;
    }

    /**
     * Try to gather a new set of groups, pushing the results if successful.
     */
    private void findGroups() {
        checkForDisabledOrShutdownState();

        // Cause the current CollocateTask to exit if it hasn't already. This will
        // put some time between collocate tasks, reducing the likelihood of moving
        // a group already being moved.
        synchronized (nodeSets) {
            if (collocateTask != null) {
                collocateTask.stop();
            }
        }

        try {
            NavigableSet<RelocatingAffinityGroup> newGroups =
                                            new TreeSet<RelocatingAffinityGroup>();

            // TODO - use time to adjust delay between runs?
            long time = finder.findAffinityGroups(newGroups, new GroupFactoryImpl());

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER,
                           "findAffinityGroups returned {0} groups, " +
                           "taking {1} milliseconds",
                           groups.size(), time);
            }
            synchronized (nodeSets) {
                groups = newGroups;
                nodeSets.clear();
                if (groups.isEmpty()) {
                    collocateTask = null;
                } else {
                    collocateTask = new CollocateTask(newGroups);
                    taskScheduler.scheduleTask(collocateTask, taskOwner);
                }
            }
        } catch (AffinityGroupFinderFailedException e) {
            logger.logThrow(Level.INFO, e, "Affinity group finder failed");
        }
    }

    private static class GroupFactoryImpl
            implements AffinityGroupFactory<RelocatingAffinityGroup>
    {
        @Override
        public RelocatingAffinityGroup newInstance(long groupId,
                                                   long generation,
                                                   Map<Identity, Long> members)
        {
            return new RelocatingAffinityGroup(groupId, generation, members);
        }
    }

    /**
     * Task to collocate the identities in a set of groups. The
     * groups selected in ascending order from {@code newGroups}. If the
     * set is modified externally, {@code reset()} should be invoked. The task
     * will exit when {@code newGroups} becomes empty or {@code stop()} is
     * invoked.
     *
     * Groups collocation is serialized in that only one group is handled per
     * task run.
     */
    private class CollocateTask extends AbstractKernelRunnable {
        final NavigableSet<RelocatingAffinityGroup> myGroups;
        volatile Iterator<RelocatingAffinityGroup> iter;
        volatile boolean stop = false;

        CollocateTask(NavigableSet<RelocatingAffinityGroup> newGroups) {
            super("CollocateTask");
            myGroups = newGroups;
            reset(); // assert Thread.holdsLock(nodeSets);
        }

        @Override
        public void run() throws NoNodesAvailableException {

            while (true) {
                synchronized (nodeSets) {
                    if (!stop && iter.hasNext()) {

                        // collocateGroup() will throw NNAE if the group's
                        // target node is no longer alive (or is unknown: -1)
                        // and there are no other nodes to move to. Therefore
                        // since all nodes are unavailable, this task will just
                        // exit and not be rescheduled.
                        if (collocateGroup(iter.next())) {

                            // TODO - It may be useful to return the group to the
                            // original group list (or a new list) so that its
                            // available for offloading
                            iter.remove();
                            taskScheduler.scheduleTask(this, taskOwner,
                                   System.currentTimeMillis() + collocateDelay);
                            return;
                        }
                    }
                }
            }
        }

        /**
         * Cause the task to reset to the beginning of the groups list. This
         * should be called whenever the groups list is changed external to
         * this task.
         */
        void reset() {
            assert Thread.holdsLock(nodeSets);
            iter = myGroups.iterator();
        }

        /**
         * Cause this task to exit.
         */
        void stop() {
            assert Thread.holdsLock(nodeSets);
            stop = true;
        }
    }

    /**
     * Move one or more identities off of a node. If the old node is alive
     * an identity will be selected to move. If the node is not alive,
     * all identities on that node will be moved. Note that is this method does
     * not guarantee that any identities are be moved.
     *
     * @param node the node to offload identities
     *
     * @throws NoNodesAvailableException if no nodes are available
     * @throws IllegalStateException if we are shut down
     */
    private void offloadSingles(Node node) throws NoNodesAvailableException {
        final long nodeId = node.getId();

        // Look up each identity on the old node and move it
        String nodekey = NodeMapUtil.getPartialNodeKey(nodeId);
        GetIdOnNodeTask task =
                new GetIdOnNodeTask(dataService, nodekey, logger);

        do {
            checkForShutdownState();
            try {
                // Find an identity on the node
                server.runTransactionally(task);
            } catch (Exception ex) {
                logger.logThrow(Level.WARNING, ex,
                                "Failed to find identity on node {1}",
                                nodeId);
                break;
            }
            if (task.done()) {
                break;
            }
            server.moveIdentity(task.getId().getIdentity(), node, -1);

        // if the node is alive, only move one id
        } while (!node.isAlive());
    }

    /**
     * Task to find identities on a node.
     */
    private static class GetIdOnNodeTask extends AbstractKernelRunnable {
        /** Set to true when no more identities to be found (or a failure) */
        private boolean done = false;
        /** If !done, the identity we were looking for */
        private IdentityMO idmo = null;

        private final DataService dataService;
        private final String nodekey;
        private final LoggerWrapper logger;

        GetIdOnNodeTask(DataService dataService,
                        String nodekey, LoggerWrapper logger)
        {
	    super("GetIdOnNodeTask for " + nodekey);
            this.dataService = dataService;
            this.nodekey = nodekey;
            this.logger = logger;
        }

        public void run() throws Exception {
            try {
                String key = dataService.nextServiceBoundName(nodekey);
                done = (key == null || !key.contains(nodekey));
                if (!done) {
                    idmo = (IdentityMO) dataService.getServiceBinding(key);
                }
            } catch (Exception e) {
                if ((e instanceof ExceptionRetryStatus) &&
                    (((ExceptionRetryStatus) e).shouldRetry()))
                {
                    throw e;
                }
                done = true;
                logger.logThrow(Level.WARNING, e,
                                "Failed to get key or binding for {0}",
                                nodekey);
            }
        }

        /**
         * Returns true if there are no more identities to be found.
         * @return {@code true} if no more identities could be found for the
         *          node, {@code false} otherwise.
         */
        public boolean done() {
            return done;
        }

        /**
         * The identity MO retrieved from the data store, or null if
         * the task has not yet executed or there was an error while
         * executing.
         * @return the IdentityMO
         */
        public IdentityMO getId() {
            return idmo;
        }
    }
}
