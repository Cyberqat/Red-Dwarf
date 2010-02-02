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

package com.sun.sgs.test.impl.service.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;

import com.sun.sgs.impl.auth.IdentityImpl;

import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.kernel.TaskOwnerImpl;

import com.sun.sgs.impl.service.data.DataServiceImpl;

import com.sun.sgs.impl.util.AbstractKernelRunnable;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.service.DataService;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;

import com.sun.sgs.test.impl.service.task.TestTaskServiceImpl.Counter;
import com.sun.sgs.test.impl.service.task.TestTaskServiceImpl.ManagedHandle;

import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.UtilProperties;

import java.io.File;
import java.io.Serializable;

import java.util.Properties;

import java.util.concurrent.atomic.AtomicLong;

import junit.framework.TestCase;

import static org.junit.Assert.*;


/**
 * Test the {@code TaskServiceImpl} class for specific cases with multiple
 * nodes. Rather than use the production Node Mapping Service, these tests
 * use a dummy implementation (and therefore a backing dummy Watchdog
 * Service) so that explicit movement and interaction cases can be tested
 * that cannot be done with the production service. This assumes that basic
 * interaction tests are already passing from {code TestTaskServiceImpl}.
 */
public class TestMultiNodeTaskServiceImpl extends TestCase {

    /** The node that creates the servers */
    private SgsTestNode serverNode;
    /** Any additional nodes, for tests needing more than one node */
    private SgsTestNode additionalNodes[];

    /** Common system components. */
    private TaskScheduler taskSchedulerZero;
    private TaskScheduler taskSchedulerOne;
    private DataService dataServiceZero;
    private DataService dataServiceOne;
    private TaskService taskServiceZero;
    private TaskService taskServiceOne;
    private DummyNodeMappingService mappingServiceZero;
    private DummyNodeMappingService mappingServiceOne;

    private TaskOwner taskOwner;

    private static AtomicLong lastNodeUsed;

    /** Test Management. */
    
    public TestMultiNodeTaskServiceImpl(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        System.err.println("Testcase: " + getName());

        lastNodeUsed = new AtomicLong(-1);

        String appName = "TestMultiNodeTaskServiceImpl";
        String dbDirectory = System.getProperty("java.io.tmpdir") +
	    File.separator + appName + ".db";

        serverNode = new SgsTestNode(appName, null,
                                     createProps(true, appName, dbDirectory));
        addNodes(createProps(false, appName, dbDirectory), 1);
        
        taskSchedulerZero = serverNode.getSystemRegistry().
            getComponent(TaskScheduler.class);
        taskSchedulerOne = additionalNodes[0].getSystemRegistry().
            getComponent(TaskScheduler.class);

        dataServiceZero = serverNode.getDataService();
        dataServiceOne = additionalNodes[0].getDataService();
        taskServiceZero = serverNode.getTaskService();
        taskServiceOne = additionalNodes[0].getTaskService();

	mappingServiceZero =
	    (DummyNodeMappingService)(serverNode.getNodeMappingService());
        mappingServiceOne =
            (DummyNodeMappingService)(additionalNodes[0].
				      getNodeMappingService());

        taskOwner = serverNode.getProxy().getCurrentOwner();

        // add a counter for use in some of the tests, so we don't have to
        // check later if it's present
        taskSchedulerZero.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() throws Exception {
                    dataServiceZero.setBinding("counter", new Counter());
                }
            }, taskOwner);
    }

    protected void tearDown() throws Exception {
        if (additionalNodes != null) {
            for (SgsTestNode node : additionalNodes)
                node.shutdown(false);
        }
        serverNode.shutdown(true);
    }

    /** Tests. */

    public void testMoveImmediateTask() throws Exception {
        IdentityImpl id = new IdentityImpl("fred");
        TaskOwnerImpl owner = new TaskOwnerImpl(id, taskOwner.getContext());
        long expectedNode = additionalNodes[0].getNodeId();
        DummyNodeMappingService.assignIdentity(getClass(), id, expectedNode);
        taskSchedulerZero.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    Counter counter = getClearedCounter();
                    taskServiceZero.scheduleTask(new TestTask());
                    counter.increment();
                }
            }, owner);

        Thread.sleep(500);
        assertCounterClearXAction("An immediate task did not run");
        assertEquals(expectedNode, lastNodeUsed.get());
    }

    public void testMoveDelayedTask() throws Exception {
        IdentityImpl id = new IdentityImpl("fred");
        TaskOwnerImpl owner = new TaskOwnerImpl(id, taskOwner.getContext());
        long expectedNode = additionalNodes[0].getNodeId();
        DummyNodeMappingService.assignIdentity(getClass(), id, expectedNode);
        taskSchedulerZero.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    Counter counter = getClearedCounter();
                    taskServiceZero.scheduleTask(new TestTask(), 100L);
                    counter.increment();
                }
            }, owner);

        Thread.sleep(500);
        assertCounterClearXAction("A delayed task did not run");
        assertEquals(expectedNode, lastNodeUsed.get());
    }

    public void testMoveAfterScheduledDelayedTask() throws Exception {
        IdentityImpl id = new IdentityImpl("fred");
        TaskOwnerImpl owner = new TaskOwnerImpl(id, taskOwner.getContext());
        DummyNodeMappingService.assignIdentity(getClass(), id,
                                               serverNode.getNodeId());
        taskSchedulerZero.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    Counter counter = getClearedCounter();
                    taskServiceZero.scheduleTask(new TestTask(), 100L);
                    counter.increment();
                }
            }, owner);

        long expectedNode = additionalNodes[0].getNodeId();
        mappingServiceZero.moveIdentity(getClass(), id, expectedNode);

        Thread.sleep(500);
        assertCounterClearXAction("A delayed task did not run");
        assertEquals(expectedNode, lastNodeUsed.get());
    }

    public void testMovePeriodicTask() throws Exception {
        IdentityImpl id = new IdentityImpl("fred");
        TaskOwnerImpl owner = new TaskOwnerImpl(id, taskOwner.getContext());
        long expectedNode = serverNode.getNodeId();
        DummyNodeMappingService.assignIdentity(getClass(), id, expectedNode);
        taskSchedulerZero.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    Counter counter = getClearedCounter();
                    taskServiceZero.schedulePeriodicTask(new TestTask(), 0L,
                                                         500L);
                    counter.increment();
                    counter.increment();
                }
            }, owner);

        Thread.sleep(250);
        assertEquals(expectedNode, lastNodeUsed.get());

        expectedNode = additionalNodes[0].getNodeId();
        mappingServiceZero.moveIdentity(getClass(), id, expectedNode);
        Thread.sleep(500);
        assertCounterClearXAction("Some periodic tasks did not run");
        assertEquals(expectedNode, lastNodeUsed.get());
    }

    public void testMoveAfterScheduledPeriodicTask() throws Exception {
        IdentityImpl id = new IdentityImpl("fred");
        TaskOwnerImpl owner = new TaskOwnerImpl(id, taskOwner.getContext());
        DummyNodeMappingService.assignIdentity(getClass(), id,
                                               serverNode.getNodeId());
        taskSchedulerZero.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    Counter counter = getClearedCounter();
                    taskServiceZero.schedulePeriodicTask(new TestTask(), 100L,
                                                         500L);
                    counter.increment();
                }
            }, owner);

        long expectedNode = additionalNodes[0].getNodeId();
        mappingServiceZero.moveIdentity(getClass(), id, expectedNode);

        Thread.sleep(400);
        assertCounterClearXAction("A periodic task did not run");
        assertEquals(expectedNode, lastNodeUsed.get());
    }

    public void testCancelPeriodicHandle() throws Exception {
        IdentityImpl id = new IdentityImpl("fred");
        TaskOwnerImpl owner = new TaskOwnerImpl(id, taskOwner.getContext());
        DummyNodeMappingService.assignIdentity(getClass(), id,
                                               serverNode.getNodeId());
        taskSchedulerZero.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    Counter counter = getClearedCounter();
                    PeriodicTaskHandle h =
                        taskServiceZero.schedulePeriodicTask(new TestTask(),
                                                             250L, 500L);
                    dataServiceZero.setBinding("handle", new ManagedHandle(h));
                }
            }, owner);

        taskSchedulerOne.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    try {
                        dataServiceOne.
                            getBinding("handle", ManagedHandle.class).cancel();
                    } catch (Exception e) {
                        fail("Did not expect exception: " + e);
                    }
                }
            }, owner);

        Thread.sleep(500);
        assertCounterClearXAction("Unexpected run of a periodic task");
    }

    public void testActiveCountBasic() throws Exception {
        IdentityImpl id = new IdentityImpl("fred");
        TaskOwnerImpl owner = new TaskOwnerImpl(id, taskOwner.getContext());
        assertEquals(DummyNodeMappingService.getActiveCount(id), 0);
        DummyNodeMappingService.assignIdentity(getClass(), id,
                                               serverNode.getNodeId());
        assertEquals(DummyNodeMappingService.getActiveCount(id), 1);
        taskSchedulerZero.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    Counter counter = getClearedCounter();
                    taskServiceZero.scheduleTask(new TestTask(), 300L);
                }
            }, owner);

        Thread.sleep(200);
        assertEquals(DummyNodeMappingService.getActiveCount(id), 2);

        Thread.sleep(300);
        assertEquals(DummyNodeMappingService.getActiveCount(id), 1);

        taskSchedulerZero.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    Counter counter = getClearedCounter();
                    for (int i = 0; i < 5; i++)
                        taskServiceZero.scheduleTask(new TestTask(), 300L);
                }
            }, owner);

        Thread.sleep(200);
        assertEquals(DummyNodeMappingService.getActiveCount(id), 2);

        Thread.sleep(300);
        assertEquals(DummyNodeMappingService.getActiveCount(id), 1);
    }

    /** Utility methods. */

    private Properties createProps(boolean server, String appName,
                                   String dbDirectory) throws Exception {
        String isServer = String.valueOf(server);
        int port = server ? 0 :
            SgsTestNode.getDataServerPort((DataServiceImpl)
					  (serverNode.getDataService()));
        String portStr = String.valueOf(port);

        return UtilProperties.createProperties(
            "com.sun.sgs.app.name", appName,
            "com.sun.sgs.app.port", "0",
            "com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
                dbDirectory,
            StandardProperties.APP_LISTENER,
                SgsTestNode.DummyAppListener.class.getName(),
            StandardProperties.NODE_MAPPING_SERVICE,
                "com.sun.sgs.test.impl.service.task.DummyNodeMappingService",
            StandardProperties.WATCHDOG_SERVICE,
                "com.sun.sgs.test.impl.service.task.DummyWatchdogService",
            StandardProperties.MANAGERS,
                "com.sun.sgs.test.impl.service.task." +
                "TestMultiNodeTaskServiceImpl$NodeIdManagerImpl",
            StandardProperties.SERVICES,
                "com.sun.sgs.test.impl.service.task." +
                "TestMultiNodeTaskServiceImpl$NodeIdService",
            "com.sun.sgs.impl.service.data.DataServiceImpl.data.store.class",
                "com.sun.sgs.impl.service.data.store.net.DataStoreClient",
            "com.sun.sgs.impl.service.data.store.net.server.host", "localhost",
            "com.sun.sgs.impl.service.task.TaskServiceImpl.handoff.start", "0",
            "com.sun.sgs.impl.service.task.TaskServiceImpl.handoff.period",
                "50",
            "com.sun.sgs.impl.service.task.TaskServiceImpl.vote.delay", "50",
            "com.sun.sgs.impl.service.data.store.net.server.run", isServer,
            "com.sun.sgs.impl.service.data.store.net.server.port", portStr,
            "DummyServer", isServer
        );
    }

    private void addNodes(Properties props, int numNodes) throws Exception {
        additionalNodes = new SgsTestNode[numNodes];
        for (int i = 0; i < numNodes; i++) {
            SgsTestNode node =  new SgsTestNode(serverNode, null, props);
            additionalNodes[i] = node;
        }
    }

    private Counter getClearedCounter() {
        Counter counter = dataServiceZero.getBinding("counter", Counter.class);
        dataServiceZero.markForUpdate(counter);
        counter.clear();
        return counter;
    }

    private void assertCounterClear(String message) {
        Counter counter = dataServiceZero.getBinding("counter", Counter.class);
        if (! counter.isZero())
            fail(message);
    }
    
    private void assertCounterClearXAction(final String message) 
        throws Exception
    {
        taskSchedulerZero.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    assertCounterClear(message);
                }
        }, taskOwner);
    }

    /** Utility classes. */

    public static class TestTask implements Task, Serializable {
        private static final long serialVersionUID = 1;
        public void run() throws Exception {
            TestMultiNodeTaskServiceImpl.lastNodeUsed.
                set(AppContext.getManager(NodeIdManager.class).getNodeId());
            DataManager dataManager = AppContext.getDataManager();
            Counter counter = dataManager.getBinding("counter", Counter.class);
            dataManager.markForUpdate(counter);
            counter.decrement();
        }
    }

    public interface NodeIdManager {
        public long getNodeId();
    }

    public static class NodeIdManagerImpl implements NodeIdManager {
        private final NodeIdManager backingManager;
        public NodeIdManagerImpl(NodeIdManager backingManager) {
            this.backingManager = backingManager;
        }
        public long getNodeId() { return backingManager.getNodeId(); }
    }

    public static class NodeIdService implements Service, NodeIdManager {
        private final long nodeId;
        public NodeIdService(Properties p, ComponentRegistry cr,
                             TransactionProxy tp) {
            nodeId = tp.getService(WatchdogService.class).getLocalNodeId();
        }
        public String getName() { return getClass().getName(); }
        public void ready() throws Exception {}
        public boolean shutdown() { return true; }
        public long getNodeId() { return nodeId; }
    }

}
