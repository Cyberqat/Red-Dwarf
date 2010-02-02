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

package com.sun.sgs.test.impl.service.watchdog;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.auth.IdentityImpl;
import com.sun.sgs.impl.kernel.KernelShutdownController;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import com.sun.sgs.impl.service.watchdog.WatchdogServerImpl;
import com.sun.sgs.impl.service.watchdog.WatchdogServiceImpl;
import com.sun.sgs.impl.service.nodemap.NodeMappingServiceImpl;
import com.sun.sgs.impl.util.AbstractService.Version;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.NodeType;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.SimpleCompletionHandler;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.BindException;
import static com.sun.sgs.test.util.UtilProperties.createProperties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test the {@link WatchdogServiceImpl} class. */
@RunWith(FilteredNameRunner.class)
public class TestWatchdogServiceImpl extends Assert {

    /** The name of the WatchdogServerImpl class. */
    private static final String WatchdogServerPropertyPrefix =
	"com.sun.sgs.impl.service.watchdog.server";

    /* The number of additional nodes to create if tests need them */
    private static final int NUM_WATCHDOGS = 5;

    /** The node that creates the servers */
    private SgsTestNode serverNode;

    /** Version information from WatchdogServiceImpl class. */
    private static String VERSION_KEY;
    private static int MAJOR_VERSION;
    private static int MINOR_VERSION;
    
    /** Any additional nodes, for tests needing more than one node */
    private SgsTestNode additionalNodes[];

    /** System components found from the serverNode */
    private TransactionProxy txnProxy;
    private ComponentRegistry systemRegistry;
    private Properties serviceProps;

    /** A specific property we started with */
    private int renewTime;

    /** The transaction scheduler. */
    private TransactionScheduler txnScheduler;

    /** The owner for tasks I initiate. */
    private Identity taskOwner;

    /** The data service for serverNode. */
    private DataService dataService;
    
    /** The watchdog service for serverNode */
    private WatchdogServiceImpl watchdogService;
    
    /** A dummy shutdown controller */
    private static DummyKernelShutdownController dummyShutdownCtrl = 
            new DummyKernelShutdownController();

    private static Field getField(Class cl, String name) throws Exception {
	Field field = cl.getDeclaredField(name);
	field.setAccessible(true);
	return field;
    }

    /** Constructs a test instance. */
    @BeforeClass public static void setUpClass() throws Exception {
	Class cl = WatchdogServiceImpl.class;
	VERSION_KEY = (String) getField(cl, "VERSION_KEY").get(null);
	MAJOR_VERSION = getField(cl, "MAJOR_VERSION").getInt(null);
	MINOR_VERSION = getField(cl, "MINOR_VERSION").getInt(null);
    }

    /** Test setup. */
    @Before public void setUp() throws Exception {
        dummyShutdownCtrl.reset();
        Properties props = new Properties();
        setUp(null, true);
    }

    protected void setUp(Properties props, boolean clean) throws Exception {
	
        serverNode = new SgsTestNode("TestWatchdogServiceImpl", 
				     null, null, props, clean);
        txnProxy = serverNode.getProxy();
        systemRegistry = serverNode.getSystemRegistry();
        serviceProps = serverNode.getServiceProperties();
        renewTime = Integer.valueOf(
            serviceProps.getProperty(
                "com.sun.sgs.impl.service.watchdog.server.renew.interval"));

        txnScheduler = systemRegistry.getComponent(TransactionScheduler.class);
        taskOwner = txnProxy.getCurrentOwner();
	dataService = serverNode.getDataService();
        watchdogService = (WatchdogServiceImpl) serverNode.getWatchdogService();
    }

    /** 
     * Add additional nodes.  We only do this as required by the tests. 
     *
     * @param props properties for node creation, or {@code null} if default
     *     properties should be used
     * @parm num the number of nodes to add
     */
    private void addNodes(Properties props, int num) throws Exception {
        // Create the other nodes
        additionalNodes = new SgsTestNode[num];

        for (int i = 0; i < num; i++) {
            SgsTestNode node = new SgsTestNode(serverNode, null, props); 
            additionalNodes[i] = node;
            System.err.println("watchdog service id: " +
                                   node.getWatchdogService().getLocalNodeId());

        }
    }

    /** Shut down the nodes. */
    @After public void tearDown() throws Exception {
        tearDown(true);
    }

    protected void tearDown(boolean clean) throws Exception {
        if (additionalNodes != null) {
            for (SgsTestNode node : additionalNodes) {
                node.shutdown(false);
            }
            additionalNodes = null;
        }
        if (serverNode != null)
            serverNode.shutdown(clean);
	/* Wait for sockets to close down. */
	Thread.sleep(100);
    }

    /* -- Test constructor -- */

    @Test public void testConstructor() throws Exception {
        WatchdogServiceImpl watchdog = null;
        try {
            watchdog = new WatchdogServiceImpl(
		SgsTestNode.getDefaultProperties(
		    "TestWatchdogServiceImpl", null, null),
		systemRegistry, txnProxy, dummyShutdownCtrl);  
            WatchdogServerImpl server = watchdog.getServer();
            System.err.println("watchdog server: " + server);
            server.shutdown();
        } finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullProperties() throws Exception {
        WatchdogServiceImpl watchdog = null;
	try {
	    watchdog = new WatchdogServiceImpl(null, systemRegistry, txnProxy,
					       dummyShutdownCtrl);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullRegistry() throws Exception {
        WatchdogServiceImpl watchdog = null;
	try {
	    watchdog = new WatchdogServiceImpl(serviceProps, null, txnProxy,
					       dummyShutdownCtrl);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullProxy() throws Exception {
        WatchdogServiceImpl watchdog = null;
	try {
	    watchdog =
                    new WatchdogServiceImpl(serviceProps, systemRegistry, null,
					    dummyShutdownCtrl);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }
    
    @Test(expected = NullPointerException.class)
    public void testConstructorNullShutdownCtrl() throws Exception {
        WatchdogServiceImpl watchdog = null;
        try {
            watchdog = new WatchdogServiceImpl(serviceProps, systemRegistry,
					       txnProxy, null);
        } finally {
            if (watchdog != null) {
                watchdog.shutdown();
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNoAppName() throws Exception {
        Properties properties = createProperties(
            WatchdogServerPropertyPrefix + ".port", "0");
	new WatchdogServiceImpl(properties, systemRegistry, txnProxy, 
				dummyShutdownCtrl);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorAppButNoServerHost() throws Exception {
        // Server start is false but we didn't specify a server host
        int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.NODE_TYPE, NodeType.appNode.name(),
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
	new WatchdogServiceImpl(props, systemRegistry, txnProxy,
				dummyShutdownCtrl);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNegativePort() throws Exception {
        WatchdogServiceImpl watchdog = null;
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(-1));
	try {
	    watchdog = 
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy,
					dummyShutdownCtrl);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorPortTooLarge() throws Exception {
        WatchdogServiceImpl watchdog = null;
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(65536));
	try {
	    watchdog =
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy,
					dummyShutdownCtrl);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorStartServerRenewIntervalTooSmall()
	throws Exception
    {
        WatchdogServiceImpl watchdog = null;
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.NODE_TYPE, NodeType.coreServerNode.name(),
	    WatchdogServerPropertyPrefix + ".port", "0",
	    WatchdogServerPropertyPrefix + ".renew.interval", "0");
	try {
	    watchdog =
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy,
					dummyShutdownCtrl);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    @Test public void testConstructorStartServerWithLargeRenewInterval()
	throws Exception
    {
        WatchdogServiceImpl watchdog = null;
	Properties properties = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.NODE_TYPE, NodeType.coreServerNode.name(),
	    WatchdogServerPropertyPrefix + ".port", "0",
	    WatchdogServerPropertyPrefix + ".renew.interval",
		Integer.toString(Integer.MAX_VALUE));
	try {
	    watchdog =
                new WatchdogServiceImpl(properties, systemRegistry, txnProxy,
					dummyShutdownCtrl);
	} finally {
            if (watchdog != null) watchdog.shutdown();
        }
    }

    @Test public void testConstructedVersion() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version = (Version)
			dataService.getServiceBinding(VERSION_KEY);
		    if (version.getMajorVersion() != MAJOR_VERSION ||
			version.getMinorVersion() != MINOR_VERSION)
		    {
			fail("Expected service version (major=" +
			     MAJOR_VERSION + ", minor=" + MINOR_VERSION +
			     "), got:" + version);
		    }
		}}, taskOwner);
    }
    
    @Test public void testConstructorWithCurrentVersion() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version = new Version(MAJOR_VERSION, MINOR_VERSION);
		    dataService.setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(
		SgsTestNode.getDefaultProperties(
		    "TestWatchdogServiceImpl", null, null),
		systemRegistry, txnProxy, dummyShutdownCtrl);  
	watchdog.shutdown();
    }

    @Test(expected = IllegalStateException.class)
    public void testConstructorWithMajorVersionMismatch()
	throws Exception
    {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version =
			new Version(MAJOR_VERSION + 1, MINOR_VERSION);
		    dataService.setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy,
				dummyShutdownCtrl);  
    }

    @Test(expected = IllegalStateException.class)
    public void testConstructorWithMinorVersionMismatch()
	throws Exception
    {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version =
			new Version(MAJOR_VERSION, MINOR_VERSION + 1);
		    dataService.setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	new WatchdogServiceImpl(serviceProps, systemRegistry, txnProxy,
				dummyShutdownCtrl);  
    }
    
    /* -- Test getLocalNodeId -- */

    @Test public void testGetLocalNodeId() throws Exception {
	long id = watchdogService.getLocalNodeId();
	if (id != 1) {
	    fail("Expected id 1, got " + id);
	}
	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.NODE_TYPE, NodeType.appNode.name(),
            WatchdogServerPropertyPrefix + ".host", "localhost",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(props, systemRegistry, txnProxy,
				    dummyShutdownCtrl);
	try {
	    id = watchdog.getLocalNodeId();
	    if (id != 2) {
		fail("Expected id 2, got " + id);
	    }
	} finally {
	    watchdog.shutdown();
	}
    }

    @Test public void testGetLocalNodeIdInTxn() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
		assertTrue(watchdogService.getLocalNodeId() > 0);
            }
        }, taskOwner);
    }

    @Test(expected = IllegalStateException.class)
    public void testGetLocalNodeIdServiceShuttingDown() throws Exception {
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(
		SgsTestNode.getDefaultProperties(
		    "TestWatchdogServiceImpl", null, null),
		systemRegistry, txnProxy, dummyShutdownCtrl);
	watchdog.shutdown();
	watchdog.getLocalNodeId();
    }

    /* -- Test isLocalNodeAlive -- */

    @Test public void testIsLocalNodeAlive() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                if (! watchdogService.isLocalNodeAlive()) {
                    fail("Expected watchdogService.isLocalNodeAlive() " +
                          "to return true");
                }
            }
        }, taskOwner);

	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.NODE_TYPE, NodeType.appNode.name(),
            WatchdogServerPropertyPrefix + ".host", "localhost",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
	final WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(props, systemRegistry, txnProxy,
				    dummyShutdownCtrl);
	try {
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    if (! watchdogService.isLocalNodeAlive()) {
                        fail("Expected watchdogService.isLocalNodeAlive() " +
                          "to return true");
                    }
                }
            }, taskOwner);

	    watchdogService.shutdown();
	    // wait for watchdog's renew to fail...
	    Thread.sleep(renewTime * 4);

            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    if (watchdog.isLocalNodeAlive()) {
                        fail("Expected watchdogService.isLocalNodeAlive() " +
                          "to return false");
                    }
                }
            }, taskOwner);
	    
	} finally {
	    watchdog.shutdown();
	}
    }

    @Test(expected = IllegalStateException.class)
    public void testIsLocalNodeAliveServiceShuttingDown() throws Exception {
	WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, dummyShutdownCtrl);
	watchdog.shutdown();
	watchdog.isLocalNodeAlive();
    }

    @Test(expected = TransactionNotActiveException.class)
    public void testIsLocalNodeAliveNoTransaction() throws Exception {
	watchdogService.isLocalNodeAlive();
    }

    /* -- Test isLocalNodeAliveNonTransactional -- */

    @Test public void testIsLocalNodeAliveNonTransactional() throws Exception {
	if (! watchdogService.isLocalNodeAliveNonTransactional()) {
	    fail("Expected watchdogService.isLocalNodeAlive" +
		 "NonTransactional() to return true");
	}

	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
	    "com.sun.sgs.impl.service.nodemap.client.port",
	        String.valueOf(SgsTestNode.getNextUniquePort()),
	    "com.sun.sgs.impl.service.watchdog.client.port",
	        String.valueOf(SgsTestNode.getNextUniquePort()),
            StandardProperties.NODE_TYPE, NodeType.appNode.name(),
            WatchdogServerPropertyPrefix + ".host", "localhost",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));
	WatchdogServiceImpl watchdog =
	    new WatchdogServiceImpl(props, systemRegistry, txnProxy,
				    dummyShutdownCtrl);
	try {
	    if (! watchdog.isLocalNodeAliveNonTransactional()) {
		fail("Expected watchdog.isLocalNodeAliveNonTransactional() " +
		     "to return true");
	    }
	    watchdogService.shutdown();
	    // wait for watchdog's renew to fail...
	    Thread.sleep(renewTime * 4);
	    if (watchdog.isLocalNodeAliveNonTransactional()) {
		fail("Expected watchdog.isLocalNodeAliveNonTransactional() " +
		     "to return false");
	    }
	    
	} finally {
	    watchdog.shutdown();
	}
    }

    @Test(expected = IllegalStateException.class)
    public void testIsLocalNodeAliveNonTransactionalServiceShuttingDown()
	throws Exception
    {
	WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, dummyShutdownCtrl);
	watchdog.shutdown();
	watchdog.isLocalNodeAliveNonTransactional();
    }

    @Test public void testIsLocalNodeAliveNonTransactionalNoTransaction() {
	assertTrue(watchdogService.isLocalNodeAliveNonTransactional());
    }
    
    public void testIsLocalNodeAliveNonTransactionalInTransaction()
	throws Exception
    {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		watchdogService.isLocalNodeAliveNonTransactional();
	    }}, taskOwner);
    }

    /* -- Test getNodes -- */

    @Test public void testGetNodes() throws Exception {
        addNodes(null, NUM_WATCHDOGS);
        Thread.sleep(renewTime);
        CountNodesTask task = new CountNodesTask();
        txnScheduler.runTask(task, taskOwner);
        int numNodes = task.numNodes;

        int expectedNodes = NUM_WATCHDOGS + 1;
        if (numNodes != expectedNodes) {
            fail("Expected " + expectedNodes +
                 " watchdogs, got " + numNodes);
        }
    }

    /** 
     * Task to count the number of nodes.
     */
    private class CountNodesTask extends TestAbstractKernelRunnable {
        int numNodes;
        public void run() {
            Iterator<Node> iter = watchdogService.getNodes();
            numNodes = 0;
            while (iter.hasNext()) {
                Node node = iter.next();
                System.err.println(node);
                numNodes++;
            }
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testGetNodesServiceShuttingDown() throws Exception {
	final WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, dummyShutdownCtrl);
	watchdog.shutdown();

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
		    watchdog.getNodes();
                }
            }, taskOwner);
    }

    @Test(expected = TransactionNotActiveException.class)
    public void testGetNodesNoTransaction() throws Exception {
	watchdogService.getNodes();
    }

    /* -- Test getNode -- */

    @Test public void testGetNode() throws Exception {
        addNodes(null, NUM_WATCHDOGS);

        for (SgsTestNode node : additionalNodes) {
            WatchdogService watchdog = node.getWatchdogService();
            final long id  = watchdog.getLocalNodeId();
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    Node node = watchdogService.getNode(id);
                    if (node == null) {
                        fail("Expected node for ID " + id + " got " +  node);
                    }
                    System.err.println(node);
                    if (id != node.getId()) {
                        fail("Expected node ID " + id + 
                                " got, " + node.getId());
                    } else if (! node.isAlive()) {
                        fail("Node " + id + " is not alive!");
                    }
                }
            }, taskOwner);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testGetNodeServiceShuttingDown() throws Exception {
	final WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, dummyShutdownCtrl);
	watchdog.shutdown();
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
		    watchdog.getNode(0);
                }
            }, taskOwner);
    }

    @Test(expected = TransactionNotActiveException.class)
    public void testGetNodeNoTransaction() throws Exception {
	watchdogService.getNode(0);
    }

    @Test public void testGetNodeNonexistentNode() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                Node node = watchdogService.getNode(29);
                System.err.println(node);
                if (node != null) {
                    fail("Expected null node, got " + node);
                }
            }
        }, taskOwner);
    }

    /* -- Test addNodeListener -- */

    @Test(expected = IllegalStateException.class)
    public void testAddNodeListenerServiceShuttingDown()
	throws Exception
    {
	final WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, dummyShutdownCtrl);
	watchdog.shutdown();
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
		watchdog.addNodeListener(new DummyNodeListener());
            }
        }, taskOwner);
    }

    @Test(expected = NullPointerException.class)
    public void testAddNodeListenerNullListener() throws Exception {
	watchdogService.addNodeListener(null);
    }

    @Test(expected = IllegalStateException.class)
    public void TestAddNodeListenerInTransaction() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
		watchdogService.addNodeListener(new DummyNodeListener());
            } }, taskOwner);
    }
    
    @Test public void testAddNodeListenerNodeStarted() throws Exception {
        DummyNodeListener listener = new DummyNodeListener();
	watchdogService.addNodeListener(listener);
        addNodes(null, NUM_WATCHDOGS);

        // wait for all nodes to get notified...
        Thread.sleep(renewTime * 4);

        Set<Node> nodes = listener.getStartedNodes();
        System.err.println("startedNodes: " + nodes);
        if (nodes.size() != NUM_WATCHDOGS) {
            fail("Expected " + NUM_WATCHDOGS + " started nodes, got " + 
                    nodes.size());
        }
        for (Node node : nodes) {
            System.err.println(node);
            if (!node.isAlive()) {
                fail("Node " + node.getId() + " is not alive!");
            }
        }
    }

    @Test public void testAddNodeListenerNodeFailed() throws Exception {
        DummyNodeListener listener = new DummyNodeListener();
	watchdogService.addNodeListener(listener);
        addNodes(null, NUM_WATCHDOGS);
        for (SgsTestNode node : additionalNodes) {
            WatchdogService watchdog = node.getWatchdogService();
            final long id  = watchdog.getLocalNodeId();
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    Node node = watchdogService.getNode(id);
                    if (node == null) {
                        fail("Expected node for ID " + id + " got " +  node);
                    }
                    System.err.println(node);
                    if (id != node.getId()) {
                        fail("Expected node ID " + id + 
                                " got, " + node.getId());
                    } else if (! node.isAlive()) {
                        fail("Node " + id + " is not alive!");
                    }
                }
            }, taskOwner);
        }
        // shutdown nodes...
	if (additionalNodes != null) {
            for (SgsTestNode node : additionalNodes) {
                node.shutdown(false);
            }
            additionalNodes = null;
        }

	// wait for all nodes to fail...
	Thread.sleep(renewTime * 4);
        
	Set<Node> nodes = listener.getFailedNodes();
	System.err.println("failedNodes: " + nodes);
	if (nodes.size() != 5) {
	    fail("Expected 5 failed nodes, got " + nodes.size());
	}
	for (Node node : nodes) {
	    System.err.println(node);
	    if (node.isAlive()) {
		fail("Node " + node.getId() + " is alive!");
	    }
	}
    }

    /* -- test shutdown -- */

    @Test public void testShutdownAndNotifyFailedNodes() throws Exception {
	Map<WatchdogServiceImpl, DummyNodeListener> watchdogMap =
	    new HashMap<WatchdogServiceImpl, DummyNodeListener>();
	int port = watchdogService.getServer().getPort();
	Properties props = createProperties(
 	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.NODE_TYPE, NodeType.appNode.name(),
            WatchdogServerPropertyPrefix + ".host", "localhost",
	    WatchdogServerPropertyPrefix + ".port", Integer.toString(port));

	try {
	    for (int i = 0; i < 5; i++) {
		WatchdogServiceImpl watchdog =
		    new WatchdogServiceImpl(props, systemRegistry, txnProxy,
					    dummyShutdownCtrl);
		DummyNodeListener listener = new DummyNodeListener();
		watchdog.addNodeListener(listener);
		watchdogMap.put(watchdog, listener);
	    }
	
	    // shutdown watchdog server
	    watchdogService.shutdown();

	    for (WatchdogServiceImpl watchdog : watchdogMap.keySet()) {
		DummyNodeListener listener = watchdogMap.get(watchdog);
		Set<Node> nodes = listener.getFailedNodes();
		System.err.println(
		    "failedNodes for " + watchdog.getLocalNodeId() +
		    ": " + nodes);
		if (nodes.size() != 6) {
		    fail("Expected 6 failed nodes, got " + nodes.size());
		}
		for (Node node : nodes) {
		    System.err.println(node);
		    if (node.isAlive()) {
			fail("Node " + node.getId() + " is alive!");
		    }
		}
	    }
	} finally {
	    for (WatchdogServiceImpl watchdog : watchdogMap.keySet()) {
		watchdog.shutdown();
	    }
	}
    }

    /* -- test addRecoveryListener -- */

    @Test(expected = IllegalStateException.class)
    public void testAddRecoveryListenerServiceShuttingDown()
	throws Exception
    {
	WatchdogServiceImpl watchdog = new WatchdogServiceImpl(
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null),
	    systemRegistry, txnProxy, dummyShutdownCtrl);
	watchdog.shutdown();
	watchdog.addRecoveryListener(new DummyRecoveryListener());
    }

    @Test(expected = NullPointerException.class)
    public void testAddRecoveryListenerNullListener() throws Exception {
	watchdogService.addRecoveryListener(null);
    }

    @Test(expected = IllegalStateException.class)
    public void TestAddRecoveryListenerInTransaction() throws Exception {
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
		watchdogService.
		    addRecoveryListener(new DummyRecoveryListener());
            } }, taskOwner);
    }
    
    /* -- test recovery -- */

    @Test public void testRecovery() throws Exception {
	Map<Long, WatchdogServiceImpl> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogServiceImpl>();
	List<Long> shutdownIds = new ArrayList<Long>();

	int totalWatchdogs = 5;
	int numWatchdogsToShutdown = 3;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	serverNode.getWatchdogService().addRecoveryListener(listener);
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogServiceImpl watchdog = createWatchdog(listener);
		watchdogs.put(watchdog.getLocalNodeId(), watchdog);
	    }

	    // shut down a few watchdog services
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		if (numWatchdogsToShutdown == 0) {
		    break;
		}
		numWatchdogsToShutdown--;
		long id = watchdog.getLocalNodeId();
		System.err.println("shutting down node: " + id);
		shutdownIds.add(id);
		watchdog.shutdown();
		watchdogs.remove(id);
	    }

	    listener.checkRecoveryNotifications(shutdownIds.size());
	    checkNodesFailed(shutdownIds, true);
	    listener.notifyCompletionHandlers();
	    checkNodesRemoved(shutdownIds);
	    checkNodesAlive(watchdogs.keySet());

	} finally {
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	}
    }

    @Test public void testRecoveryWithBackupFailureDuringRecovery()
	throws Exception
    {
	Map<Long, WatchdogServiceImpl> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogServiceImpl>();
	List<Long> shutdownIds = new ArrayList<Long>();
	int totalWatchdogs = 8;
	int numWatchdogsToShutdown = 3;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	serverNode.getWatchdogService().addRecoveryListener(listener);
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogServiceImpl watchdog = createWatchdog(listener);
		watchdogs.put(watchdog.getLocalNodeId(), watchdog);
	    }

	    // shut down a few watchdog services
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		if (numWatchdogsToShutdown == 0) {
		    break;
		}
		numWatchdogsToShutdown--;
		long id = watchdog.getLocalNodeId();
		System.err.println("shutting down node: " + id);
		shutdownIds.add(id);
		watchdog.shutdown();
		watchdogs.remove(id);
	    }

	    listener.checkRecoveryNotifications(shutdownIds.size());
	    Set<Node> backups = checkNodesFailed(shutdownIds, true);

	    // shutdown backups
	    for (Node backup : backups) {
		long backupId = backup.getId();
		WatchdogServiceImpl watchdog = watchdogs.get(backupId);
		if (watchdog != null) {
		    System.err.println("shutting down backup: " + backupId);
		    shutdownIds.add(backupId);
		    watchdog.shutdown();
		    watchdogs.remove(backupId);
		}
	    }

	    Thread.sleep(4 * renewTime);
	    listener.checkRecoveryNotifications(shutdownIds.size());
	    listener.notifyCompletionHandlers();
	    checkNodesRemoved(shutdownIds);
	    checkNodesAlive(watchdogs.keySet());

	} finally {
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	}
    }

    @Test public void testRecoveryWithDelayedBackupAssignment()
	throws Exception
    {
	List<Long> shutdownIds = new ArrayList<Long>();
	long serverNodeId = serverNode.getWatchdogService().getLocalNodeId();
	crashAndRestartServer();
	shutdownIds.add(serverNodeId);
	Map<Long, WatchdogServiceImpl> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogServiceImpl>();
	int totalWatchdogs = 5;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogServiceImpl watchdog = createWatchdog(listener);
		watchdogs.put(watchdog.getLocalNodeId(), watchdog);
	    }

	    // shut down all watchdog services.
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		long id = watchdog.getLocalNodeId();
		System.err.println("shutting down node: " + id);
		shutdownIds.add(id);
		watchdog.shutdown();
	    }

	    watchdogs.clear();

	    // pause for watchdog server to detect failure and
	    // reassign backups.
	    Thread.sleep(4 * renewTime);

	    checkNodesFailed(shutdownIds, false);

	    // Create new node to be (belatedly) assigned as backup
	    // for failed nodes.
	    WatchdogServiceImpl watchdog = createWatchdog(listener);
	    watchdogs.put(watchdog.getLocalNodeId(), watchdog);

	    listener.checkRecoveryNotifications(shutdownIds.size());
	    listener.notifyCompletionHandlers();
	    checkNodesRemoved(shutdownIds);
	    checkNodesAlive(watchdogs.keySet());

	} finally {
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	}
    }

    @Test public void testRecoveryAfterServerCrash() throws Exception {
	Map<Long, WatchdogServiceImpl> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogServiceImpl>();
	List<Long> shutdownIds = new ArrayList<Long>();
	int totalWatchdogs = 5;
	WatchdogServiceImpl newWatchdog = null;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogServiceImpl watchdog = createWatchdog(listener);
		watchdogs.put(watchdog.getLocalNodeId(), watchdog);
	    }
	    
	    // simulate crash
	    crashAndRestartServer();

	    checkNodesFailed(watchdogs.keySet(), false);
	    
	    // Create new node to be (belatedly) assigned as backup
	    // for failed nodes.
	    newWatchdog = createWatchdog(listener);

	    listener.checkRecoveryNotifications(totalWatchdogs + 1);
	    listener.notifyCompletionHandlers();
	    checkNodesRemoved(watchdogs.keySet());

	} finally {
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	    if (newWatchdog != null) {
		newWatchdog.shutdown();
	    }
	}
    }

    @Test public void testRecoveryAfterAllNodesAndServerCrash()
	throws Exception
    {
	Map<Long, WatchdogServiceImpl> watchdogs =
	    new ConcurrentHashMap<Long, WatchdogServiceImpl>();
	List<Long> shutdownIds = new ArrayList<Long>();
	int totalWatchdogs = 5;

	DummyRecoveryListener listener = new DummyRecoveryListener();
	try {
	    for (int i = 0; i < totalWatchdogs; i++) {
		WatchdogServiceImpl watchdog = createWatchdog(listener);
		watchdogs.put(watchdog.getLocalNodeId(), watchdog);
	    }

	    // shut down all watchdog services.
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		long id = watchdog.getLocalNodeId();
		System.err.println("shutting down node: " + id);
		shutdownIds.add(id);
		watchdog.shutdown();
	    }

	    watchdogs.clear();

	    // simulate crash
	    crashAndRestartServer();

	    // pause for watchdog server to detect failure and
	    // reassign backups.
	    Thread.sleep(4 * renewTime);

	    checkNodesFailed(shutdownIds, false);

	    // Create new node to be (belatedly) assigned as backup
	    // for failed nodes.
	    WatchdogServiceImpl watchdog = createWatchdog(listener); 
	    watchdogs.put(watchdog.getLocalNodeId(), watchdog);

	    listener.checkRecoveryNotifications(shutdownIds.size() + 1);
	    listener.notifyCompletionHandlers();

	    checkNodesRemoved(shutdownIds);
	    checkNodesAlive(watchdogs.keySet());

	} finally {
	    for (WatchdogServiceImpl watchdog : watchdogs.values()) {
		watchdog.shutdown();
	    }
	}
    }
    
    /** Test creating two nodes at the same host and port  */
    @Test public void testReuseHostPort() throws Exception {
        addNodes(null, 1);
        Properties props = additionalNodes[0].getServiceProperties();
	props.setProperty("com.sun.sgs.impl.service.nodemap.client.port",
			  String.valueOf(SgsTestNode.getNextUniquePort()));
	props.setProperty("com.sun.sgs.impl.service.watchdog.client.port",
			  String.valueOf(SgsTestNode.getNextUniquePort()));
        props.setProperty("com.sun.sgs.impl.service.session.server.port",
                          String.valueOf(SgsTestNode.getNextUniquePort()));
        SgsTestNode node = null;
        try {
            node = new SgsTestNode(serverNode, null, props);
            fail("Expected BindException");
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            // The kernel constructs the services through reflection, and the
            // SgsTestNode creates the kernel through reflection - burrow down
            // to the root cause to be sure it's of the expected type.
            while ((target instanceof InvocationTargetException) ||
                   (target instanceof RuntimeException)) {
                System.err.println("unwrapping target exception");
                target = target.getCause();
            }
            if (!(target instanceof BindException)) {
                fail("Expected BindException, got " + target);
            }
        } finally {
            if (node != null) {
                node.shutdown(false);
            }
        }
    }

    /** Test creating two single nodes at the same host and port  */
    @Test public void testReuseHostPortSingleNode() throws Exception {
        final String appName = "ReuseHostPort";
        SgsTestNode node = null;
        SgsTestNode node1 = null;
        try {
 	    Properties props = getPropsForApplication(appName);
	    node = new SgsTestNode(appName, null, props, true);
            
            // This node is independent of the one above;  it'll have a new
            // server.  We expect to see a socket BindException rather
            // than an IllegalArgumentException.
 	    Properties props1 = getPropsForApplication(appName + "1");
 	    props1.setProperty(
                com.sun.sgs.impl.transport.tcp.TcpTransport.LISTEN_PORT_PROPERTY,
                props.getProperty(
                    com.sun.sgs.impl.transport.tcp.TcpTransport.LISTEN_PORT_PROPERTY));
	    node1 = new SgsTestNode(appName, null, props1, true);
            fail ("Expected BindException");
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            // We wrap our exceptions a bit in the kernel....
            while ((target instanceof InvocationTargetException) ||
                   (target instanceof RuntimeException)) {
                System.err.println("unwrapping target exception");
                target = target.getCause();
            }
            if (!(target instanceof BindException)) {
                fail("Expected BindException, got " + target);
            }
        } finally {
            if (node != null) {
                node.shutdown(true);
            }
            if (node1 != null) {
                node1.shutdown(true);
            }
        }
    }

    /** Test that an application node can be restarted on the same host
     *  and port after a crash.
     */
    @Test public void testNodeCrashAndRestart() throws Exception {
        SgsTestNode node = null;
        SgsTestNode node1 = null;
        try {
            node = new SgsTestNode(serverNode, null, null);
            Properties props = node.getServiceProperties();
            System.err.println("node properties are " + props);
            
            System.err.println("shutting down node");
            node.shutdown(false);
            node = null;
            // Note that we need to wait for the system to detect the
            // failed node.
            Thread.sleep(renewTime * 2);

            System.err.println("attempting to restart failed node");
            node1 = new SgsTestNode("TestWatchdogServiceImpl", 
				     null, null, props, false);
        } finally {
            if (node != null) {
                node.shutdown(false);
            }
            if (node1 != null) {
                node1.shutdown(false);
            }
        }
    }       
    
    /** Check that we can restart a single node system on the same
     *  host and port after a crash.
     */
    @Test public void testSingleNodeServerCrashAndRestart() throws Exception {
        final String appName = "TestServerCrash";
        SgsTestNode node = null;
        SgsTestNode node1 = null;
        try {
            node = new SgsTestNode(appName, null,
                                   getPropsForApplication(appName), true);
            Properties props = node.getServiceProperties();
            System.err.println("node properties are " + props);
            
            System.err.println("shutting down single node");
            node.shutdown(false);
            node = null;

            // Note that in this case we don't have to wait for the system
            // to see the failed node - the entire system crashed, and the
            // check for reuse is implemented with a transient data structure.
            System.err.println("attempting to restart failed single node");
	    props.setProperty(
		"com.sun.sgs.impl.service.nodemap.server.port",
		String.valueOf(SgsTestNode.getNextUniquePort()));
	    props.setProperty(
		"com.sun.sgs.impl.service.watchdog.server.port",
		String.valueOf(SgsTestNode.getNextUniquePort()));
            node1 = new SgsTestNode(appName, null, null, props, false);
        } finally {
            if (node1 != null) {
                node1.shutdown(false);
            }
            if (node != null) {
                node.shutdown(true);
            }
        }
    }

    /* --- test node health reporting procedures --- */

    @Test(expected = NullPointerException.class)
    public void testReportNodeHealthNullHealth() {
	watchdogService.reportHealth(watchdogService.getLocalNodeId(),
                                     null, getClass().getName());
    }

    @Test(expected = NullPointerException.class)
    public void testReportNodeHealthNullClassName() {
	watchdogService.reportHealth(watchdogService.getLocalNodeId(),
                                     Node.Health.GREEN, null);
    }

    @Test(expected = IllegalStateException.class)
    public void testReportNodeHealthInTransaction() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		watchdogService.reportHealth(1, Node.Health.GREEN,
                                             getClass().getName());
	    }}, taskOwner);
    }

    /**
     * Check that a node can report a change in node health
     */
    @Test public void testReportLocalHealth() {
        try {
            final String appName = "TestReportLocalHealth";
            Properties properties = createProperties(
                    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
                    WatchdogServerPropertyPrefix + ".port",
		    	Integer.toString(65530));

            // Create a dummy shutdown controller to log calls to the shutdown
            // method. NOTE: The controller does not actually shutdown the node
            WatchdogServiceImpl watchdogService =
                    new WatchdogServiceImpl(properties, systemRegistry,
                    txnProxy, dummyShutdownCtrl);

            // Report a health change
            watchdogService.reportHealth(watchdogService.getLocalNodeId(),
                                         Node.Health.YELLOW, appName);

            try {
                assertTrue(watchdogService.getLocalNodeHealth().
                                                    equals(Node.Health.YELLOW));
            } catch (Exception e) {
                fail(e);
            }

            // The shutdown controller should not be incremented as a result
            // of the health set to YELLOW
            assertEquals(0, dummyShutdownCtrl.getShutdownCount());
            watchdogService.shutdown();
        } catch (Exception e) {
            fail(e);
        }
    }

    private void fail(Exception e) {
        fail("Not expecting an Exception: " + e.getLocalizedMessage());
    }

    /**
     * Check that a node can report multiple changes in node health.
     */
    @Test public void testCycleLocalHealth() {
        try {
            final String appName = "TestReportLocalHealth";
            Properties properties = createProperties(
                    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
                    WatchdogServerPropertyPrefix + ".port",
		    	Integer.toString(65530));

            // Create a dummy shutdown controller to log calls to the shutdown
            // method. NOTE: The controller does not actually shutdown the node
            WatchdogServiceImpl watchdogService =
                    new WatchdogServiceImpl(properties, systemRegistry,
                    txnProxy, dummyShutdownCtrl);

            // Report multiple health changes
            watchdogService.reportHealth(watchdogService.getLocalNodeId(),
                                         Node.Health.YELLOW, appName);
            try {
                assertTrue(watchdogService.getLocalNodeHealth().
                                                    equals(Node.Health.YELLOW));
            } catch (Exception e) {
                fail(e);
            }
            watchdogService.reportHealth(watchdogService.getLocalNodeId(),
                                         Node.Health.GREEN, appName);
            try {
                assertTrue(watchdogService.getLocalNodeHealth().
                                                    equals(Node.Health.GREEN));
            } catch (Exception e) {
                fail(e);
            }

            // The shutdown controller should not be incremented as a result
            // of the health changes
            assertEquals(0, dummyShutdownCtrl.getShutdownCount());
            watchdogService.shutdown();
        } catch (Exception e) {
            fail(e);
        }
    }

    /**
     * Check that a node can report multiple changes in node health.
     */
    @Test public void testMultiLocalHealthReporters() {
        try {
            final String appName = "TestReportLocalHealth";
            Properties properties = createProperties(
                    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
                    WatchdogServerPropertyPrefix + ".port",
		    	Integer.toString(65530));

            // Create a dummy shutdown controller to log calls to the shutdown
            // method. NOTE: The controller does not actually shutdown the node
            WatchdogServiceImpl watchdogService =
                    new WatchdogServiceImpl(properties, systemRegistry,
                    txnProxy, dummyShutdownCtrl);

            // Report multiple health changes. The sequence is:
            //  A -> YELLOW
            //  B -> ORANGE     - node health should report ORANGE
            //  B -> GREEN      - node health should report YELLOW
            //  A -> GREEN      - node health should report GREEN
            try {
                watchdogService.reportHealth(watchdogService.getLocalNodeId(),
                                             Node.Health.YELLOW, appName + "A");
                watchdogService.reportHealth(watchdogService.getLocalNodeId(),
                                             Node.Health.ORANGE, appName + "B");
                assertTrue(watchdogService.getLocalNodeHealth().
                                                    equals(Node.Health.ORANGE));
                watchdogService.reportHealth(watchdogService.getLocalNodeId(),
                                         Node.Health.GREEN, appName + "B");
                assertTrue(watchdogService.getLocalNodeHealth().
                                                    equals(Node.Health.YELLOW));
                watchdogService.reportHealth(watchdogService.getLocalNodeId(),
                                         Node.Health.GREEN, appName + "A");
                assertTrue(watchdogService.getLocalNodeHealth().
                                                    equals(Node.Health.GREEN));
            } catch (Exception e) {
                fail(e);
            }

            // The shutdown controller should not be incremented as a result
            // of the health changes
            assertEquals(0, dummyShutdownCtrl.getShutdownCount());
            watchdogService.shutdown();
        } catch (Exception e) {
            fail(e);
        }
    }

    /* --- test shutdown procedures --- */

    @Test(expected = NullPointerException.class)
    public void testReportFailureNullClassName() {
	watchdogService.reportFailure(watchdogService.getLocalNodeId(), null);
    }

    @Test(expected = IllegalStateException.class)
    public void testReportFailureInTransaction() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() {
		watchdogService.reportFailure(1, getClass().getName());
	    }}, taskOwner);
    }
    
    /** 
     * Check that a node can report a failure and shutdown itself down by
     * notifying the watchdog service
     */
    @Test public void testReportLocalFailure() {
        try {
            final String appName = "TestReportFailure";
            Properties properties = createProperties(
                    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
                    WatchdogServerPropertyPrefix + ".port",
		    	Integer.toString(65530));

            // Create a dummy shutdown controller to log calls to the shutdown
            // method. NOTE: The controller does not actually shutdown the node
            WatchdogServiceImpl watchdogService = 
                    new WatchdogServiceImpl(properties, systemRegistry, 
                    txnProxy, dummyShutdownCtrl);

            // Report a failure, which should shutdown the node
            watchdogService.reportFailure(watchdogService.getLocalNodeId(), 
                    appName);

            // Node should not be alive since we reported a failure
            try {
                assertFalse(watchdogService.isLocalNodeAliveNonTransactional());
            } catch (Exception e) {
                fail(e);
            }
            
            // The shutdown controller should be incremented as a result of the 
            // failure being reported
            assertEquals(1, dummyShutdownCtrl.getShutdownCount());
            watchdogService.shutdown();
        } catch (Exception e) {
            fail(e);
        }
    }

    /**
     * Check that a node can shutdown and report a failure when it detects 
     * that the renew process from the watchdog service fails
     */
    @Test public void testReportFailureDueToNoRenewal() throws Exception {        
        final SgsTestNode node = new SgsTestNode(serverNode, null, null);
        
        // Shutdown the server 
        serverNode.shutdown(true);
        serverNode = null;

        // Wait for the renew to fail and the shutdown to begin
        Thread.sleep(renewTime*4);

        try {
            // The node should be shut down
            assertFalse(node.getWatchdogService().isLocalNodeAliveNonTransactional());
        } catch (IllegalStateException ise) {
            // May happen if service is shutting down.
        } catch (Exception e) {
            fail (e);
        }
    }

    /**
     * Check that a node can report a failure in a remote node and 
     * the failed node should shutdown accordingly
     */
    @Test public void testReportRemoteFailure() throws Exception {
        final String appName = "TestReportRemoteFailure_node";
        try {
            // Instantiate two nodes
            final SgsTestNode server = new SgsTestNode(appName, null, 
                    getPropsForApplication(appName));
            final SgsTestNode node = new SgsTestNode(server, null, null);

            // Report that the second node failed
            System.err.println("server node id: " + server.getNodeId());
            System.err.println("   new node id: " + node.getNodeId());

            server.getWatchdogService().reportFailure(node.getNodeId(), 
                    WatchdogService.class.getName());

            // The server node that reported the remote 
            // failure should be unaffected
            TransactionScheduler sched = server.getSystemRegistry().
                    getComponent(TransactionScheduler.class);
            Identity own = server.getProxy().getCurrentOwner();
            sched.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    assertTrue(server.getWatchdogService().isLocalNodeAlive());
                }
            }, own);

            try {
                // The node should have failed
                sched = node.getSystemRegistry().
                        getComponent(TransactionScheduler.class);
                own = node.getProxy().getCurrentOwner();
                sched.runTask(new TestAbstractKernelRunnable() {
                    public void run() throws Exception {
                        if (node.getWatchdogService().isLocalNodeAlive()) {
                            fail("Expected watchdogService.isLocalNodeAlive() " +
                                    "to return false");
                        }
                    }
                }, own);
            } catch (IllegalStateException ise) {
                // Expected
            } catch (Exception e) {
                fail("Not expecting an Exception (1)");
            }
        } catch (Exception e) {
            fail("Not expecting an Exception (2)");
        }
    }

    /**
     * Check that a server that has lost communication with it's service will
     * issue a shutdown of the node with the failed service
     */
    @Test public void testReportFailureServerSide() {
        final String appName = "TestFailureServerSide";
        try {
             final SgsTestNode appNode = new SgsTestNode(serverNode, null, null);
            
            // Find the node mapping server
            Field mapServer =
                    NodeMappingServiceImpl.class.getDeclaredField("serverImpl");
            mapServer.setAccessible(true);
            final NodeMappingService nodeMappingService = 
                    serverNode.getNodeMappingService();
            NodeMappingServerImpl nodeMappingServer =
                    (NodeMappingServerImpl) mapServer.get(nodeMappingService);
            
            // Create a new identity and assign it to a node
            // Since there is only 1 app node, it will be assigned to that one
            final Identity id = new IdentityImpl(appName + "_identity");
            nodeMappingService.assignNode(NodeMappingService.class, id);
            System.err.println("AppNode id: "+appNode.getNodeId());

            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    // See if the right node has the identity
                    long nodeid = nodeMappingService.getNode(id).getId();
                    System.err.println("Identity is on node: "+nodeid);
                    if (nodeid != appNode.getNodeId())
                        fail("Identity is on the wrong node");
                }
            }, taskOwner);

            // Convince the Node Mapping server that the identity 
            // has been removed. This ensures that rtask.isDead() is true
            appNode.getNodeMappingService().setStatus(
                    NodeMappingService.class, id, false);

            // Unexport the NodeMappingService on the appNode to shutdown the
            // service without removing the node listener. This should
            // cause an IOException in the RemoveTask of the server when 
            // removing the identity.
            Field privateField =
                    NodeMappingServiceImpl.class.getDeclaredField("exporter");
            privateField.setAccessible(true);
            Exporter<?> exporter = (Exporter<?>) privateField.get(
                    appNode.getNodeMappingService());
            exporter.unexport();
            
            Thread.sleep(renewTime); // Let it shutdown
            nodeMappingServer.canRemove(id); // Remove the identity
            Thread.sleep(renewTime); // Wait for RemoveThread to run on server
            
            txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    try {
                        // The appNode should be shutting down or shut down
                        appNode.getWatchdogService().isLocalNodeAlive();
                        fail("Expected IllegalStateException");
                    } catch (IllegalStateException ise) {
                        // Expected
                    } catch (Exception e) {
                        e.printStackTrace();
                        fail("Unexpected Exception");
                    }
                }
            }, taskOwner);
            
        } catch (Exception e) {
            e.printStackTrace();
            fail("Unexpected Exception");
        }
    }
    
    /**
     * Check that if two concurrent shutdowns are issued for a node, the second 
     * shutdown will fail quietly without throwing any exceptions.
     */
    @Test public void testConcurrentShutdowns() throws Exception {
        final SgsTestNode appNode = new SgsTestNode(serverNode, null, null);
        // issue a shutdown; this shutdown runs in a seperate thread
        appNode.getWatchdogService().reportFailure(appNode.getNodeId(),
                appNode.getClass().getName());
        // issue another shutdown; set clean = false since we do not want this
        // test case to fail due to an error trying to delete a missing file
        appNode.shutdown(false);
    }
    
    /**
     * Check if a node shutdown can be issued from a component successfully
     */
    @Test(expected = IllegalStateException.class)
    public void testComponentShutdown() throws Exception {
        final SgsTestNode node = new SgsTestNode(serverNode, null, null);
        
        // Simulate shutdown being called from a component by passing a
        // a component object
        node.getShutdownCtrl().shutdownNode(node.getSystemRegistry().
                getComponent(TransactionScheduler.class));
        Thread.sleep(renewTime); // let it shutdown
        
	// The node should be shutting down or shut down
	node.getWatchdogService().isLocalNodeAliveNonTransactional();
    }
    
    
    /**
     * Fakes out a KernelShutdownController for test purposes
     */
    private static class DummyKernelShutdownController implements
	    KernelShutdownController {
	private int shutdownCount = 0;

	public void shutdownNode(Object caller) {
	    shutdownCount++;
	}

	int getShutdownCount() {
	    return shutdownCount;
	}
        
        void reset() {
            shutdownCount = 0;
        }
    }

    /** Creates node properties with a db directory based on the app name. */
    private Properties getPropsForApplication(String appName)
	throws Exception
    {
        String dir = System.getProperty("java.io.tmpdir") +
            File.separator + appName + ".db";
        Properties props =
	    SgsTestNode.getDefaultProperties(appName, null, null);
        props.setProperty(
            "com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
            dir);
        return props;
    }

    /** Creates a watchdog service with the specified recovery listener. */
    private WatchdogServiceImpl createWatchdog(RecoveryListener listener)
	throws Exception
    {
	Properties props = createProperties(
 	    StandardProperties.APP_NAME, "TestWatchdogServiceImpl",
            StandardProperties.NODE_TYPE, NodeType.appNode.name(),
            WatchdogServerPropertyPrefix + ".host", "localhost",
	    WatchdogServerPropertyPrefix + ".port",
	    Integer.toString(watchdogService.getServer().getPort()));
	WatchdogServiceImpl watchdog = 
	    new WatchdogServiceImpl(props, systemRegistry, txnProxy, 
            dummyShutdownCtrl);
	watchdog.addRecoveryListener(listener);
	watchdog.ready();
	System.err.println("Created node (" + watchdog.getLocalNodeId() + ")");
	return watchdog;
    }
    
    /** Tears down the server node and restarts it as a server-only stack. */
    private void crashAndRestartServer() throws Exception {
	System.err.println("simulate watchdog server crash...");
	tearDown(false);
	Properties props =
	    SgsTestNode.getDefaultProperties(
		"TestWatchdogServiceImpl", null, null);
        props.setProperty(
            StandardProperties.NODE_TYPE,
            NodeType.coreServerNode.name());
	setUp(props, false);
    }
    
    private Set<Node> checkNodesFailed(Collection<Long> ids, boolean hasBackup)
	throws Exception
    {
        CheckNodesFailedTask task = new CheckNodesFailedTask(ids, hasBackup);
        txnScheduler.runTask(task, taskOwner);
        return task.backups;
    }

    private class CheckNodesFailedTask extends TestAbstractKernelRunnable {
	Set<Node> backups = new HashSet<Node>();
        Collection<Long> ids;
        boolean hasBackup;

        CheckNodesFailedTask(Collection<Long> ids, boolean hasBackup) {
            this.ids = ids;
            this.hasBackup = hasBackup;
        }
        public void run() {
	    System.err.println("Get shutdown nodes (should be marked failed)");
	    for (Long longId : ids) {
	        long id = longId.longValue();
	        Node node = watchdogService.getNode(id);
	        System.err.println("node (" + id + "):" +
			           (node == null ? "(removed)" : node));
	        if (node == null) {
		    fail("Node removed before recovery complete: " + id);
	        }
	        if (node.isAlive()) {
		    fail("Node not marked as failed: " + id);
	        }
	        Node backup = watchdogService.getBackup(id);
	        if (hasBackup) {
		    if (backup == null) {
		        fail("failed node (" + id + ") has no backup");
		    } else {
		        backups.add(backup);
		    }
	        } else if (!hasBackup && backup != null) {
		    fail("failed node (" + id + ") assigned backup: " +
		         backup);
	        }
	    }
        }
    }

    private void checkNodesRemoved(final Collection<Long> ids) throws Exception {
	Thread.sleep(250);
	System.err.println("Get shutdown nodes (should be removed)...");
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
	        for (Long longId : ids) {
	            long id = longId.longValue();
	            Node node = watchdogService.getNode(id);
	            System.err.println("node (" + id + "):" +
			               (node == null ? "(removed)" : node));
	            if (node != null) {
		        fail("Expected node to be removed: " + node);
	            }
	        }
            }
        }, taskOwner);
    }

    private void checkNodesAlive(final Collection<Long> ids) throws Exception {
	System.err.println("Get live nodes...");
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
	        for (Long longId : ids) {
	            long id = longId.longValue();
	            Node node = watchdogService.getNode(id);
	            System.err.println("node (" + id + "): " + node);
	            if (node == null || !node.isAlive()) {
		        fail("Expected alive node");
	            }
	        }
            }
        }, taskOwner);
    }

    private static class DummyRecoveryListener implements RecoveryListener {

	private final Map<Node, SimpleCompletionHandler> nodes =
	    Collections.synchronizedMap(
		new HashMap<Node, SimpleCompletionHandler>());

	DummyRecoveryListener() {}

	public void recover(Node node, SimpleCompletionHandler handler) {
            assert(node != null);
            assert(handler != null);

	    synchronized (nodes) {
		if (nodes.get(node) == null) {
		    System.err.println(
			"DummyRecoveryListener.recover: adding node: " + node);
		} else {
		    System.err.println(
			"DummyRecoveryListener.recover: REPLACING node: " + node);
		}
		nodes.put(node, handler);
		nodes.notifyAll();
	    }
	    
	}

	void checkRecoveryNotifications(int expectedSize) {
	    long endTime = System.currentTimeMillis() + 5000;
	    synchronized (nodes) {
		while (nodes.size() != expectedSize &&
		       System.currentTimeMillis() < endTime)
		{
		    try {
			nodes.wait(500);
		    } catch (InterruptedException e) {
		    }
		}
		if (nodes.size() != expectedSize) {
		    fail("Expected " + expectedSize + " recover requests, " +
			 "received: " + nodes.size());
		}
	    }
	}

	void notifyCompletionHandlers() {
	    for (SimpleCompletionHandler handler : nodes.values()) {
		handler.completed();
	    }
	}
    }

    /* -- other methods -- */

    private static class DummyNodeListener implements NodeListener {

	private final Set<Node> failedNodes = new HashSet<Node>();
	private final Set<Node> startedNodes = new HashSet<Node>();
	

	public void nodeStarted(Node node) {
	    startedNodes.add(node);
	}

        public void nodeHealthChange(Node node) {
            if (!node.isAlive()) {
                failedNodes.add(node);
            }
        }

	Set<Node> getFailedNodes() {
	    return failedNodes;
	}

	Set<Node> getStartedNodes() {
	    return startedNodes;
	}
    }
}