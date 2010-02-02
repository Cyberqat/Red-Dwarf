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

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.Exporter;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Node;
import com.sun.sgs.service.NodeListener;
import com.sun.sgs.service.RecoveryCompleteFuture;
import com.sun.sgs.service.RecoveryListener;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import java.io.IOException;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * TODO: Modify implementation to not accept calls before service is ready.
 * The server should not service incoming remote calls (registerNode, etc.)
 * until it receives the 'ready' invocation (or finishes construction
 * successfully).  Some of the fields used in registerNode aren't initialized
 * until after the server is exported, so it can cause problems if the server
 * receives an incoming request before it has completed initializing.  In
 * practice, this flaw is not a problem so long as the server is started first
 * before starting other nodes.
 */

/**
 * The {@link WatchdogService} implementation. <p>
 *
 * The {@link #WatchdogServiceImpl constructor} supports the following
 * properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.watchdog.server.start
 *	</b></code><br>
 *	<i>Default:</i> {@code true} <br>
 *	Specifies whether the watchdog server should be started by this service.
 *	If {@code true}, the watchdog server is started.  If this property value
 *	is {@code true}, then the properties supported by the
 *	{@link WatchdogServerImpl} class should be specified.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.watchdog.server.host
 *	</b></code><br>
 *	<i>Default:</i> the local host name <br>
 *
 * <dd style="padding-top: .5em">
 *	Specifies the host name for the watchdog server that this service
 *	contacts.  If the {@code
 *	com.sun.sgs.impl.service.watchdog.server.start} property
 *	is {@code true}, then this property's default is used (since
 *	the watchdog server to contact will be the one started on
 *	the local host).
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.watchdog.server.port
 *	</b></code><br>
 *	<i>Default:</i> {@code 44533} <br>
 *
 * <dd style="padding-top: .5em">
 *	Specifies the network port for the watchdog server that this service
 *	contacts (and, optionally, starts).  If the {@code
 *	com.sun.sgs.impl.service.watchdog.server.start} property
 *	is {@code true}, then the value must be greater than or equal to
 *	{@code 0} and no greater than {@code 65535}, otherwise the value
 *	must be greater than {@code 0}, and no greater than {@code 65535}.<p>
 * 
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.watchdog.client.host
 *	</b></code><br>
 *	<i>Default:</i> the local host name <br>
 *
 * <dd style="padding-top: .5em">
 *	Specifies the host name for the watchdog client used when
 *	registering the node with the watchdog service.
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.watchdog.client.port
 *	</b></code><br>
 *	<i>Default:</i> {@code 0} (anonymous port) <br>
 *
 * <dd style="padding-top: .5em">
 *	Specifies the network port for this watchdog service for receiving
 *	node status change notifications from the watchdog server.  The value
 *	must be greater than or equal to {@code 0} and no greater than
 *	{@code 65535}.<p>
 * </dl> <p>
 */
public class WatchdogServiceImpl implements WatchdogService {

    /**  The name of this class. */
    private static final String CLASSNAME =
	WatchdogServiceImpl.class.getName();

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger("com.sun.sgs.impl.service.watchdog.service"));

    /** The prefix for server properties. */
    private static final String SERVER_PROPERTY_PREFIX =
	"com.sun.sgs.impl.service.watchdog.server";

    /** The prefix for client properties. */
    private static final String CLIENT_PROPERTY_PREFIX =
	"com.sun.sgs.impl.service.watchdog.client";

    /** The property to specify that the watchdog server should be started. */
    private static final String START_SERVER_PROPERTY =
	SERVER_PROPERTY_PREFIX + ".start";

    /** The property name for the watchdog server host. */
    private static final String HOST_PROPERTY =
	SERVER_PROPERTY_PREFIX +  ".host";

    /** The property name for the watchdog server port. */
    private static final String SERVER_PORT_PROPERTY =
	WatchdogServerImpl.PORT_PROPERTY;

    /** The default value of the server port. */
    private static final int DEFAULT_SERVER_PORT =
	WatchdogServerImpl.DEFAULT_PORT;

    /** The property name for the watchdog client host. */
    private static final String CLIENT_HOST_PROPERTY =
	CLIENT_PROPERTY_PREFIX + ".host";
    
    /** The property name for the watchdog client port. */
    private static final String CLIENT_PORT_PROPERTY =
	CLIENT_PROPERTY_PREFIX + ".port";

    /** The default value of the client port. */
    private static final int DEFAULT_CLIENT_PORT = 0;

    /** The minimum renew interval. */
    private static final long MIN_RENEW_INTERVAL = 25;

    /** The transaction proxy for this class. */
    private static TransactionProxy txnProxy;

    /** The lock for this service's state. */
    final Object stateLock = new Object();
    
    /** The application name */
    private final String appName;

    /** The exporter for this server. */
    private final Exporter<WatchdogClient> exporter;

    /** The task scheduler. */
    private final TaskScheduler taskScheduler;

    /** The task owner. */
    volatile TaskOwner taskOwner;

    /** The watchdog server impl. */
    final WatchdogServerImpl serverImpl;

    /** The watchdog server proxy, or {@code null}. */
    final WatchdogServer serverProxy;

    /** The watchdog client impl. */
    private final WatchdogClientImpl clientImpl;

    /** The watchdog client proxy. */
    final WatchdogClient clientProxy;

    /** The name of the local host. */
    final String localHost;
    
    /** The thread that renews the node with the watchdog server. */
    final Thread renewThread = new RenewThread();

    /** The local nodeId. */
    final long localNodeId;

    /** The interval for renewals with the watchdog server. */
    private final long renewInterval;

    /** The set of node listeners for all nodes. */
    private final ConcurrentMap<NodeListener, NodeListener> nodeListeners =
	new ConcurrentHashMap<NodeListener, NodeListener>();

    /** The set of recovery listeners for this node. */
    private final ConcurrentMap<RecoveryListener, RecoveryListener>
	recoveryListeners =
	    new ConcurrentHashMap<RecoveryListener, RecoveryListener>();

    /** The queues of RecoveryCompleteFutures, keyed by node being recovered. */
    private final ConcurrentMap<Node, Queue<RecoveryCompleteFuture>>
	recoveryFutures =
	    new ConcurrentHashMap<Node, Queue<RecoveryCompleteFuture>>();

    /** The data service. */
    final DataService dataService;

    /** If true, this node is alive; initially, true. */
    private boolean isAlive = true;

    /** If true, this service is shutting down; initially, false. */
    private boolean shuttingDown = false;
    
    /**
     * Constructs an instance of this class with the specified properties.
     * See the {@link WatchdogServiceImpl class documentation} for a list
     * of supported properties.
     *
     * @param	properties service (and server) properties
     * @param	systemRegistry system registry
     * @param	txnProxy transaction proxy
     * @throws	Exception if a problem occurs constructing the service/server
     */
    public WatchdogServiceImpl(Properties properties,
			       ComponentRegistry systemRegistry,
			       TransactionProxy txnProxy)
	throws Exception
    {
	logger.log(Level.CONFIG, "Creating WatchdogServiceImpl properties:{0}",
		   properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

	try {
	    if (systemRegistry == null) {
		throw new NullPointerException("null systemRegistry");
	    } else if (txnProxy == null) {
		throw new NullPointerException("null txnProxy");
	    }
	    appName = wrappedProps.getProperty(StandardProperties.APP_NAME);
	    if (appName == null) {
		throw new IllegalArgumentException(
		    "The " + StandardProperties.APP_NAME +
		    " property must be specified");
	    }
	    boolean startServer = wrappedProps.getBooleanProperty(
 		START_SERVER_PROPERTY, true);
	    localHost = InetAddress.getLocalHost().getHostName();
            
	    int clientPort = wrappedProps.getIntProperty(
		CLIENT_PORT_PROPERTY, DEFAULT_CLIENT_PORT, 0, 65535);

	    String clientHost = wrappedProps.getProperty(
		CLIENT_HOST_PROPERTY, localHost);

	    clientImpl = new WatchdogClientImpl();
	    exporter = new Exporter<WatchdogClient>(WatchdogClient.class);
	    exporter.export(clientImpl, clientPort);
	    clientProxy = exporter.getProxy();
            
	    String host;
	    int serverPort;
	    if (startServer) {
		serverImpl = new WatchdogServerImpl(
		    properties, systemRegistry, txnProxy, 
                    clientHost, clientProxy);
		host = localHost;
		serverPort = serverImpl.getPort();
	    } else {
		serverImpl = null;
		host = wrappedProps.getProperty(HOST_PROPERTY, localHost);
		serverPort = wrappedProps.getIntProperty(
		    SERVER_PORT_PROPERTY, DEFAULT_SERVER_PORT, 1, 65535);
	    }

	    Registry rmiRegistry = LocateRegistry.getRegistry(host, serverPort);
	    serverProxy = (WatchdogServer)
		rmiRegistry.lookup(WatchdogServerImpl.WATCHDOG_SERVER_NAME);
	    
	    taskScheduler = systemRegistry.getComponent(TaskScheduler.class);

	    synchronized (WatchdogServiceImpl.class) {
		if (WatchdogServiceImpl.txnProxy == null) {
		    WatchdogServiceImpl.txnProxy = txnProxy;
		} else {
		    assert WatchdogServiceImpl.txnProxy == txnProxy;
		}
	    }
	    dataService = txnProxy.getService(DataService.class);
	    // TBD: This task owner is probably not correct because it
	    // contains the system context.
	    taskOwner = txnProxy.getCurrentOwner();

            if (startServer) {
                localNodeId = serverImpl.localNodeId;
                renewInterval = serverImpl.renewInterval;
            } else {
                long[] values = serverProxy.registerNode(clientHost, 
                                                         clientProxy);
                if (values == null || values.length < 2) {
                    setFailedThenNotify(false);
                    throw new IllegalArgumentException(
                        "registerNode returned improper array: " +
			Arrays.toString(values));
                }
                localNodeId = values[0];
                renewInterval = values[1];
            }
            renewThread.start();
            
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.log(Level.CONFIG,
			   "node registered, host:{0}, localNodeId:{1}",
			   clientHost, localNodeId);
	    }
	    
	} catch (Exception e) {
	    logger.logThrow(
		Level.CONFIG, e,
		"Failed to create WatchdogServiceImpl");
	    throw e;
	}
    }

    /* -- Implement Service -- */

    /** {@inheritDoc} */
    public String getName() {
	return toString();
    }
    
    /** {@inheritDoc} */
    public void ready() {
	// TBD: the client shouldn't accept incoming calls until this
	// service is ready which would give all RecoveryListeners a
	// chance to register and ensure that the taskOwner has the
	// correct context for callbacks.
	taskOwner = txnProxy.getCurrentOwner();
        if (serverImpl != null) {
            serverImpl.ready();
        }
    }

    /** {@inheritDoc} */
    public boolean shutdown() {
	synchronized (stateLock) {
	    if (shuttingDown) {
		return false;
	    }
	    shuttingDown = true;
	}
	renewThread.interrupt();
	try {
	    renewThread.join();
	} catch (InterruptedException e) {
	    return false;
	}
	if (serverImpl != null) {
	    serverImpl.shutdown();
	}
	return true;
    }
	
    /* -- Implement WatchdogService -- */

    /** {@inheritDoc} */
    public long getLocalNodeId() {
	checkState();
	return localNodeId;
    }

    /** {@inheritDoc} */
    public boolean isLocalNodeAlive() {
	checkState();
	if (getIsAlive() == false) {
	    return false;
	} else {
	    Node node = NodeImpl.getNode(dataService, localNodeId);
	    if (node == null || node.isAlive() == false) {
		setFailedThenNotify(true);
		return false;
	    } else {
		return true;
	    }
	}
    }

    /** {@inheritDoc} */
    public boolean isLocalNodeAliveNonTransactional() {
	checkState();
	return getIsAlive();
    }
    
    /** {@inheritDoc} */
    public Iterator<Node> getNodes() {
	checkState();
	txnProxy.getCurrentTransaction();
	return NodeImpl.getNodes(dataService);
    }

    /** {@inheritDoc} */
    public Node getNode(long nodeId) {
	checkState();
	if (nodeId < 0) {
	    throw new IllegalArgumentException("invalid nodeId: " + nodeId);
	}
	return NodeImpl.getNode(dataService, nodeId);
    }

    /** {@inheritDoc} */
    public void addNodeListener(NodeListener listener) {
	checkState();
	if (listener == null) {
	    throw new NullPointerException("null listener");
	}
	nodeListeners.putIfAbsent(listener, listener);
    }

    /** {@inheritDoc} */
    public Node getBackup(long nodeId) {
	NodeImpl node = (NodeImpl) getNode(nodeId);
	return
	    (node != null && node.hasBackup()) ?
	    getNode(node.getBackupId()) :
	    null;
    }

    /** {@inheritDoc} */
    public void addRecoveryListener(RecoveryListener listener) {
	checkState();
	if (listener == null) {
	    throw new NullPointerException("null listener");
	}
	recoveryListeners.putIfAbsent(listener, listener);
    }

    /**
     * This thread continuously renews this node with the watchdog server
     * before the renew interval (returned when registering the node) expires.
     */
    private final class RenewThread extends Thread {

	/** Constructs an instance of this class as a daemon thread. */
	RenewThread() {
	    super(CLASSNAME + "$RenewThread");
	    setDaemon(true);
	}

	/**
	 * Registers the node with the watchdog server, and sends
	 * periodic renew requests.  This thread terminates if the
	 * node is no longer considered alive or if the watchdog
	 * service is shutdown.
	 */
	public void run() {
	    long startRenewInterval = renewInterval / 2;
	    long nextRenewInterval = startRenewInterval;
	    long lastRenewTime = System.currentTimeMillis();

	    while (getIsAlive() == true && ! shuttingDown()) {

		try {
		    Thread.currentThread().sleep(nextRenewInterval);
		} catch (InterruptedException e) {
		    return;
		}

		boolean renewed = false;
		try {
		    if (! serverProxy.renewNode(localNodeId)) {
			setFailedThenNotify(true);
			break;
		    }
		    renewed = true;
		    nextRenewInterval = startRenewInterval;
		    
		} catch (IOException e) {
		    /*
		     * Adjust renew interval in order to renew with
		     * server again before the renew interval expires.
		     */
		    logger.logThrow(
			Level.INFO, e,
			"renewing with watchdog server throws");
		    nextRenewInterval =
			Math.max(nextRenewInterval / 2, MIN_RENEW_INTERVAL);
		}
		long now = System.currentTimeMillis();
		if (now - lastRenewTime > renewInterval) {
		    setFailedThenNotify(true);
		    break;
		}
		if (renewed) {
		    lastRenewTime = now;
		}
	    }
	}
    }

    /* -- other methods -- */

    /**
     * Returns the server.  This method is used for testing.
     *
     * @return	the server
     */
    public WatchdogServerImpl getServer() {
	return serverImpl;
    }
    
    /**
     * Throws {@code IllegalStateException} if this service is shutting down.
     */
    private void checkState() {
	if (shuttingDown()) {
	    throw new IllegalStateException("service shutting down");
	}
    }

    /**
     * Returns {@code true} if this service is shutting down.
     */
    private boolean shuttingDown() {
	synchronized (stateLock) {
	    return shuttingDown;
	}
    }
    
    /**
     * Returns the local alive status: {@code true} if this node is
     * considered alive.
     */
    private boolean getIsAlive() {
	synchronized (stateLock) {
	    return isAlive;
	}
    }

    /**
     * Sets the local alive status of this node to {@code false}, and
     * if {@code notify} is {@code true}, notifies appropriate
     * registered node listeners of this node's failure.  This method
     * is called when this node is no longer considered alive.
     * Subsequent calls to {@link #isAlive isAlive} will return {@code
     * false}.  If this node's local alive status was already set to
     * {@code false}, then this method does nothing.
     *
     * @param	notify	if {@code true}, notifies appropriate registered
     *		node listeners of this node's failure
     */
    private void setFailedThenNotify(boolean notify) {
	synchronized (stateLock) {
	    if (! isAlive) {
		return;
	    }
	    isAlive = false;
	}

	if (notify) {
	    Node node = new NodeImpl(localNodeId, localHost, false);
	    notifyNodeListeners(node);
	}
    }

    /**
     * Notifies the appropriate registered node listeners of the
     * status change of the specified {@code node}.  If invoking
     * {@link Node#isAlive isAlive} on the {@code node} returns
     * {@code false}, the {@code NodeListener#nodeFailed nodeFailed}
     * method is invoked on each node listener, otherwise the {@code
     * NodeListener#nodeStarted nodeStarted} method is invoked on each
     * node listener.
     *
     * @param	node a node
     * @throws  IllegalStateException if this service is shutting down
     */
    private void notifyNodeListeners(final Node node) {

	for (NodeListener listener : nodeListeners.keySet()) {
	    final NodeListener nodeListener = listener;
	    taskScheduler.scheduleTask(
		new AbstractKernelRunnable() {
		    public void run() {
			if (! shuttingDown() &&
                            isLocalNodeAliveNonTransactional()) 
			{
			    if (node.isAlive()) {
				nodeListener.nodeStarted(node);
			    } else {
				nodeListener.nodeFailed(node);
			    }
			}
		    }
		}, taskOwner);
	}
    }

    /**
     * Notifies the registered recovery listeners that the specified
     * {@code node} needs to be recovered.
     *
     * @param	node a node	
     */
    private void notifyRecoveryListeners(final Node node) {
	if (logger.isLoggable(Level.INFO)) {
	    logger.log(Level.INFO, "Node:{0} recovering for node:{1}",
		       localNodeId, node.getId());
	}
	Queue<RecoveryCompleteFuture> futureQueue =
	    new ConcurrentLinkedQueue<RecoveryCompleteFuture>();
	if (recoveryFutures.putIfAbsent(node, futureQueue) != null) {
	    // recovery for node already being handled
	    return;
	}
	
	for (RecoveryListener listener : recoveryListeners.keySet()) {
	    final RecoveryListener recoveryListener = listener;
	    final RecoveryCompleteFuture future =
		new RecoveryCompleteFutureImpl(node, listener);
	    futureQueue.add(future);
	    taskScheduler.scheduleTask(
		new AbstractKernelRunnable() {
		    public void run() {
			try {
			    if (! shuttingDown() &&
				isLocalNodeAliveNonTransactional())
			    {
				recoveryListener.recover(node, future);
			    }
			} catch (Exception e) {
			    logger.logThrow(
			        Level.WARNING, e,
				"Notifying recovery listener on node:{0} " +
				"with node:{1}, future:{2} throws",
				localNodeId, node, future);
			}
		    }
		}, taskOwner);
	}
    }

    /**
     * Implements the WatchdogClient that receives callbacks from the
     * WatchdogServer.
     */
    private final class WatchdogClientImpl implements WatchdogClient {

	/** {@inheritDoc} */
	public void nodeStatusChanges(
 	    long[] ids, String hosts[], boolean[] status, long[] backups)
	{
	    if (ids.length != hosts.length || hosts.length != status.length ||
		status.length != backups.length)
	    {
		throw new IllegalArgumentException("array lengths don't match");
	    }
	    for (int i = 0; i < ids.length; i++) {
		if (ids[i] == localNodeId && status[i]) {
		    /* Don't notify the local node that it is alive. */
		    continue;
		}
		Node node =
		    new NodeImpl(ids[i], hosts[i], status[i], backups[i]);
		notifyNodeListeners(node);
		if (status[i] == false && backups[i] == localNodeId) {
		    notifyRecoveryListeners(node);
		}
	    }
	}
    }

    /**
     * The {@code RecoveryCompleteFuture} implementation.  When {@code
     * done} is invoked, the future instance is removed from the recovery
     * future queue for the associated node.  If a given future is the
     * last one to be removed from a node's queue, then recovery is
     * complete for that node, and the data store is updated to clean
     * up recovery information for that node.
     */
    private final class RecoveryCompleteFutureImpl
	implements RecoveryCompleteFuture
    {
	private final Node node;
	private final RecoveryListener listener;
	private boolean isDone = false;

	/**
	 * Constructs an instance with the specified {@code node} and
	 * recovery {@code listener}.
	 */
	RecoveryCompleteFutureImpl(Node node, RecoveryListener listener) {
	    this.node = node;
	    this.listener = listener;
	}

	/** {@inheritDoc} */
	public void done() {
	    synchronized (this) {
		if (isDone) {
		    return;
		}
		isDone = true;
	    }

	    Queue<RecoveryCompleteFuture> futureQueue =
		recoveryFutures.get(node);
	    assert futureQueue != null;
	    futureQueue.remove(this);
	    if (futureQueue.isEmpty()) {
		// recovery for the node is complete, so remove node
		// from table of recovery futures
		if (recoveryFutures.remove(node) != null) {
		    try {
			if (isLocalNodeAliveNonTransactional()) {
			    serverProxy.recoveredNode(node.getId(), localNodeId);
			}
		    } catch (Exception e) {
			logger.logThrow(
			    Level.WARNING, e,
			    "Problem invoking WatchdogServer.recoveredNode " +
			    "for node:{0} backup:{1}",  node, localNodeId);
		    }
		}
	    }
	}

	/** {@inheritDoc} */
	synchronized public boolean isDone() {
	    return isDone;
	}
    }
}
