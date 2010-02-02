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

package com.sun.sgs.impl.protocol.simple;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.impl.protocol.ProtocolDescriptorImpl;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityCoordinator;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.protocol.Protocol;
import com.sun.sgs.protocol.ProtocolConnectionHandler;
import com.sun.sgs.protocol.ProtocolDescriptor;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.WatchdogService;
import com.sun.sgs.transport.ConnectionHandler;
import com.sun.sgs.transport.Transport;
import com.sun.sgs.transport.TransportDescriptor;
import com.sun.sgs.transport.TransportFactory;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A protocol factory for creating new {@code SimpleProtocolMessageChannel}
 * instances.  Such channels implement the {@link SimpleSgsProtocolImpl}.
 * The {@link #SimpleSgsProtocolImpl constructor} supports the following
 * properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #READ_BUFFER_SIZE_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_READ_BUFFER_SIZE}
 *
 * <dd style="padding-top: .5em">Specifies the read buffer size. Value must be
 * between 8192 and {@code Integer.MAX_VALUE}.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #TRANSPORT_CLASS_NAME_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> Required property.<br>
 *
 * <dd style="padding-top: .5em"> 
 *	Specifies the class name of the transport to use.<p>
 * 
 * </dl> <p>
 */
public class SimpleSgsProtocolImpl implements Protocol, ConnectionHandler {
    
    /** The package name. */
    public static final String PKG_NAME = "com.sun.sgs.impl.protocol.simple";
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PKG_NAME));

    /** The name of the read buffer size property. */
    public static final String READ_BUFFER_SIZE_PROPERTY =
        PKG_NAME + ".buffer.read.max";

    public static final String TRANSPORT_CLASS_NAME_PROPERTY =
        PKG_NAME + ".transport";
            
    /** The default read buffer size: {@value #DEFAULT_READ_BUFFER_SIZE} */
    public static final int DEFAULT_READ_BUFFER_SIZE = 128 * 1024;
    
    /** The transaction proxy, or null if configure has not been called. */    
    private static volatile TransactionProxy txnProxy = null;

    /** The task scheduler. */
    private final TaskScheduler taskScheduler;
    
    /** The transaction scheduler. */
    final TransactionScheduler transactionScheduler;

    /** The node mapping service. */
    final NodeMappingService nodeMapService;
    
    /** The identity manager. */
    final IdentityCoordinator identityManager;
    
    /** The local node's ID. */
    final long localNodeId;
    
    /** The task owner. */
    private final Identity taskOwner;

    /** The read buffer size for new connections. */
    private final int readBufferSize;

    private final Transport transport;
    
    final ProtocolConnectionHandler connectionHandler;
    
    final ProtocolDescriptor protocolDesc;
    
    /**
     * Constructs an instance with the specified {@code properties},
     * {@code systemRegistry}, and {@code txnProxy}.
     *
     * @param	properties the configuration properties
     * @param	systemRegistry the system registry
     * @param	txnProxy a transaction proxy
     */
    public SimpleSgsProtocolImpl(Properties properties,
                                 ComponentRegistry systemRegistry,
                                 TransactionProxy txnProxy,
                                 ProtocolConnectionHandler connectionHandler)
    {
	logger.log(Level.CONFIG,
		   "Creating SimpleSgsProtcolFactory properties:{0}",
		   properties);
	
	if (properties == null) {
	    throw new NullPointerException("null properties");
	} else if (systemRegistry == null) {
	    throw new NullPointerException("null systemRegistry");
	} else if (txnProxy == null) {
	    throw new NullPointerException("null txnProxy");
	}
	
	synchronized (SimpleSgsProtocolImpl.class) {
	    if (SimpleSgsProtocolImpl.txnProxy == null) {
		SimpleSgsProtocolImpl.txnProxy = txnProxy;
	    } else {
		assert SimpleSgsProtocolImpl.txnProxy == txnProxy;
	    }
	}
        identityManager =
		systemRegistry.getComponent(IdentityCoordinator.class);
        transactionScheduler =
	    systemRegistry.getComponent(TransactionScheduler.class);
        nodeMapService = txnProxy.getService(NodeMappingService.class);
        localNodeId = txnProxy.getService(WatchdogService.class).getLocalNodeId();

	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	try {
            readBufferSize = wrappedProps.getIntProperty(
                READ_BUFFER_SIZE_PROPERTY, DEFAULT_READ_BUFFER_SIZE,
                8192, Integer.MAX_VALUE);
	    
	    taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
	    taskOwner = txnProxy.getCurrentOwner();
	    
            TransportFactory transportFactory =
                systemRegistry.getComponent(TransportFactory.class);
            
            String transportClassName =
                    wrappedProps.getProperty(TRANSPORT_CLASS_NAME_PROPERTY);
            if (transportClassName == null)
                throw new IllegalArgumentException(
                        "a transport must be specified");
            
            transport = transportFactory.startTransport(transportClassName,
                                                        properties,
                                                        this);
            
            if (!transport.getDescriptor().canSupport(Delivery.RELIABLE)) {
                transport.shutdown();
                throw new IllegalArgumentException(
                        "transport must support RELIABLE delivery");
            }
            protocolDesc = new ProtocolDescriptorImpl(SimpleSgsProtocol.TYPE,
                                                      transport.getDescriptor());
            this.connectionHandler = connectionHandler;
	} catch (Exception e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to create SimpleSgsProtcolImpl");
	    }
            if (e instanceof RuntimeException)
                throw (RuntimeException)e;
            else
                throw new RuntimeException(e);
	}
    }
    
    /* -- Implement Protocol -- */
    
    /** {@inheritDoc} */
    @Override
    public ProtocolDescriptor getDescriptor() {
        return protocolDesc;
    }

    /** {@inheritDoc} */
    @Override
    public void shutdown() {
        transport.shutdown();
    }
    
    /* -- Implement ConnectionHandler -- */
    
    /** {@inheritDoc} */
    @Override
    public void newConnection(AsynchronousByteChannel byteChannel,
                              TransportDescriptor descriptor)
        throws Exception
    {
        new SimpleSgsProtocolMessageChannel(byteChannel, this, readBufferSize);
    }

    /**
     * Schedules a non-durable, non-transactional {@code task}.
     *
     * @param	task a non-durable, non-transactional task
     */
    void scheduleNonTransactionalTask(KernelRunnable task) {
        taskScheduler.scheduleTask(task, taskOwner);
    }

    /**
     * Runs the specified {@code task} immediately, in a transaction.
     */
    void runTransactionalTask(KernelRunnable task, Identity ownerIdentity)
	throws Exception
    {
	if (ownerIdentity == null) {
	    throw new NullPointerException("Owner identity cannot be null");
	}
	transactionScheduler.runTask(task, ownerIdentity);
    }
}
