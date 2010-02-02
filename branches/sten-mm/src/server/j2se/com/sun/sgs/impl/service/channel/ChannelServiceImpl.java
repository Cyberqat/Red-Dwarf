package com.sun.sgs.impl.service.channel;

import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameExistsException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.util.HexDumper;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.impl.util.MessageBuffer;
import com.sun.sgs.impl.util.NonDurableTaskScheduler;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import com.sun.sgs.service.ClientSessionService;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.ProtocolMessageListener;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.SgsClientSession;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple ChannelService implementation.
 */
public class ChannelServiceImpl
    implements ChannelManager, Service, NonDurableTransactionParticipant
{

    /** The property that specifies the application name. */
    public static final String APP_NAME_PROPERTY = "com.sun.sgs.appName";

    /** The name of this class. */
    private static final String CLASSNAME = ChannelServiceImpl.class.getName();
    
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** Provides transaction and other information for the current thread. */
    private static final ThreadLocal<Context> currentContext =
	new ThreadLocal<Context>();
    
    /** The name of this application. */
    private final String appName;

    /** Synchronize on this object before accessing the txnProxy. */
    private final Object lock = new Object();

    /** The listener that receives incoming channel protocol messages. */
    private final ProtocolMessageListener protocolMessageListener;
    
    /** The transaction proxy, or null if configure has not been called. */    
    private TransactionProxy txnProxy;

    /** The data service. */
    private DataService dataService;

    /** The client session service. */
    private ClientSessionService sessionService;

    /** The task scheduler. */
    private TaskScheduler taskScheduler;

    /** The task scheduler for non-durable tasks. */
    NonDurableTaskScheduler nonDurableTaskScheduler;

    /** Map (with weak keys) of client sessions to sequence numbers. */
    private final WeakHashMap<SgsClientSession, AtomicLong> sequenceNumberMap =
	new WeakHashMap<SgsClientSession, AtomicLong>();
    
    /** The sequence number for channel messages originating from the server. */
    private AtomicLong sequenceNumber = new AtomicLong(0);

    /**
     * Constructs an instance of this class with the specified properties.
     *
     * @param properties service properties
     * @param systemRegistry system registry
     */
    public ChannelServiceImpl(
	Properties properties, ComponentRegistry systemRegistry)
    {
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(
		Level.CONFIG, "Creating ChannelServiceImpl properties:{0}",
		properties);
	}
	try {
	    if (systemRegistry == null) {
		throw new NullPointerException("null systemRegistry");
	    }
	    appName = properties.getProperty(APP_NAME_PROPERTY);
	    if (appName == null) {
		throw new IllegalArgumentException(
		    "The " + APP_NAME_PROPERTY +
		    " property must be specified");
	    }	
	    protocolMessageListener = new ChannelProtocolMessageListener();
	    taskScheduler = systemRegistry.getComponent(TaskScheduler.class);

	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e, "Failed to create ChannelServiceImpl");
	    }
	    throw e;
	}
    }
 
    /* -- Implement Service -- */

    /** {@inheritDoc} */
    public String getName() {
	return toString();
    }


    /** {@inheritDoc} */
    public void configure(ComponentRegistry registry, TransactionProxy proxy) {

	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG, "Configuring ChannelServiceImpl");
	}

	try {
	    if (registry == null) {
		throw new NullPointerException("null registry");
	    } else if (proxy == null) {
		throw new NullPointerException("null transaction proxy");
	    }
	    synchronized (lock) {
		if (txnProxy != null) {
		    throw new IllegalStateException("Already configured");
		}
		txnProxy = proxy;
		dataService = registry.getComponent(DataService.class);
		sessionService =
		    registry.getComponent(ClientSessionService.class);
		nonDurableTaskScheduler =
		    new NonDurableTaskScheduler(
			taskScheduler, proxy.getCurrentOwner(),
			registry.getComponent(TaskService.class));
	    }

	    /*
	     * Create and store new channel table if one does not already
	     * exist in the data store.
	     */
	    try {
		dataService.getServiceBinding(
		    ChannelTable.NAME, ChannelTable.class);
	    } catch (NameNotBoundException e) {
		dataService.setServiceBinding(
		    ChannelTable.NAME, new ChannelTable());
	    }
	    sessionService.registerProtocolMessageListener(
		SimpleSgsProtocol.CHANNEL_SERVICE, protocolMessageListener);
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.CONFIG)) {
		logger.logThrow(
		    Level.CONFIG, e,
		    "Failed to configure ChannelServiceImpl");
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public boolean shutdown() {
        // FIXME implement this
        return false;
    }

    /* -- Implement ChannelManager -- */

    /** {@inheritDoc} */
    public Channel createChannel(String name,
				 ChannelListener listener,
				 Delivery delivery)
    {
	try {
	    if (name == null) {
		throw new NullPointerException("null name");
	    }
	    if (listener != null && !(listener instanceof Serializable)) {
		throw new IllegalArgumentException("listener is not serializable");
	    }
	    Context context = checkContext();
	    Channel channel = context.createChannel(name, listener, delivery);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "createChannel name:{0} returns {1}",
		    name, channel);
	    }
	    return channel;
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "createChannel name:{0} throws", name);
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public Channel getChannel(String name) {
	try {
	    if (name == null) {
		throw new NullPointerException("null name");
	    }
	    Context context = checkContext();
	    Channel channel = context.getChannel(name);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "getChannel name:{0} returns {1}",
		    name, channel);
	    }
	    return channel;
	    
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINEST)) {
		logger.logThrow(
		    Level.FINEST, e, "getChannel name:{0} throws", name);
	    }
	    throw e;
	}
    }


    /* -- Implement NonDurableTransactionParticipant -- */
       
    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) throws Exception {
	try {
	    boolean prepared = true; // nothing to do on commit (yet).
	    handleTransaction(txn, prepared);
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINER, "prepare txn:{0} returns {1}",
			   txn, true);
	    }
	    
	    return true;
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.logThrow(Level.FINER, e, "prepare txn:{0} throws", txn);
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void commit(Transaction txn) {
	try {
	    handleTransaction(txn, true);
	    currentContext.set(null);
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, "commit txn:{0} returns", txn);
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.logThrow(Level.FINER, e, "commit txn:{0} throws", txn);
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(Transaction txn) throws Exception {
	try {
	    handleTransaction(txn, true);
	    currentContext.set(null);
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, "prepareAndCommit txn:{0} returns", txn);
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.logThrow(
		    Level.FINER, e, "prepareAndCommit txn:{0} throws", txn);
	    }
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void abort(Transaction txn) {
	try {
	    handleTransaction(txn, true);
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, "abort txn:{0} returns", txn);
	    }
	} catch (RuntimeException e) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.logThrow(Level.FINER, e, "abort txn:{0} throws", txn);
	    }
	    throw e;
	}
    }

    /* -- other methods -- */

    /**
     * Checks the specified transaction, throwing IllegalStateException
     * if the current context is null or if the specified transaction is
     * not equal to the transaction in the current context. If
     * 'nullifyContext' is 'true' or if the specified transaction does
     * not match the current context's transaction, then sets the
     * current context to null.
     */
    private void handleTransaction(Transaction txn, boolean nullifyContext) {
	if (txn == null) {
	    throw new NullPointerException("null transaction");
	}
	Context context = currentContext.get();
	if (context == null) {
	    throw new IllegalStateException("null context");
	}
	if (!txn.equals(context.txn)) {
	    currentContext.set(null);
	    throw new IllegalStateException(
		"Wrong transaction: Expected " + context.txn + ", found " + txn);
	}
	if (nullifyContext) {
	    currentContext.set(null);
	}
    }

   /**
     * Obtains information associated with the current transaction, throwing a
     * TransactionNotActiveException exception if there is no current
     * transaction, and throwing IllegalStateException if there is a problem
     * with the state of the transaction or if this service has not been
     * configured with a transaction proxy.
     */
    private Context checkContext() {
	Transaction txn;
	synchronized (lock) {
	    if (txnProxy == null) {
		throw new IllegalStateException("Not configured");
	    }
	    txn = txnProxy.getCurrentTransaction();
	}
	if (txn == null) {
	    throw new TransactionNotActiveException(
		"No transaction is active");
	}
	Context context = currentContext.get();
	if (context == null) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, "join txn:{0}", txn);
	    }
	    txn.join(this);
	    context = new Context(txn);
	    currentContext.set(context);
	} else if (!txn.equals(context.txn)) {
	    currentContext.set(null);
	    throw new IllegalStateException(
		"Wrong transaction: Expected " + context.txn +
		", found " + txn);
	}
	return context;
    }

    static Context getContext() {
	return currentContext.get();
    }

    /**
     * Checks that the specified context is currently active, throwing
     * TransactionNotActiveException if it isn't.
     */
    static void checkContext(Context context) {
	if (context != currentContext.get()) {
	    throw new TransactionNotActiveException(
		"No transaction is active");
	}
    }

    /**
     * Returns the next sequence number for the given client session.
     */
    private long nextSequenceNumber(SgsClientSession session) {
	synchronized (sequenceNumberMap) {
	    // Using AtomicLong is overkill because the map is synchronized,
	    // but it is easy at this point...
	    AtomicLong seq = sequenceNumberMap.get(session);
	    if (seq == null) {
		seq = new AtomicLong(0);
		sequenceNumberMap.put(session, seq);
	    }
	    return seq.getAndIncrement();
	}
    }

    /* -- Implement ProtocolMessageListener -- */

    private final class ChannelProtocolMessageListener
	implements ProtocolMessageListener
    {
	/** {@inheritDoc} */
	public void receivedMessage(SgsClientSession session, byte[] message) {
	    try {
		MessageBuffer buf = new MessageBuffer(message);
	    
		buf.getByte(); // discard version
		
		/*
		 * Handle service id.
		 */
		byte serviceId = buf.getByte();

		if (serviceId != SimpleSgsProtocol.CHANNEL_SERVICE) {
		    if (logger.isLoggable(Level.SEVERE)) {
			logger.log(
                            Level.SEVERE,
			    "expected channel service ID, got: {0}",
			    serviceId);
		    }
		    return;
		}

		/*
		 * Handle op code.
		 */
		
		byte opcode = buf.getByte();

		switch (opcode) {
		    
		case SimpleSgsProtocol.CHANNEL_SEND_REQUEST:
		    String name = buf.getString();
                    buf.getLong(); // TODO Check sequence num
		    short numRecipients = buf.getShort();
		    if (numRecipients < 0) {
			// TBD: log error...
			return;
		    }

		    Set<byte[]> sessions = new HashSet<byte[]>();
		    if (numRecipients > 0) {
			for (int i = 0; i < numRecipients; i++) {
			    short idLength = buf.getShort();
			    byte[] sessionId = buf.getBytes(idLength);
			    sessions.add(sessionId);
			}
		    }
		    short msgSize = buf.getShort();
		    byte[] channelMessage = buf.getBytes(msgSize);
                    long seq = nextSequenceNumber(session);
		    byte[] senderId = session.getSessionId();

                    // Immediately forward to receiving clients
		    nonDurableTaskScheduler.scheduleTask(
                        new ForwardingTask(
                            name, senderId, sessions, channelMessage, seq));
                    
                    // Notify listeners in the app in a transaction
		    nonDurableTaskScheduler.scheduleTask(
			new NotifyTask(
			    name, senderId, channelMessage));
		    
		    break;
		    
		default:
		    if (logger.isLoggable(Level.SEVERE)) {
			logger.log(
			    Level.SEVERE,
			    "receivedMessage session:{0} message:{1} " +
			    "unknown opcode:{2}",
			    session, message, opcode);
		    }
		    break;
		}

		if (logger.isLoggable(Level.FINEST)) {
		    logger.log(
			Level.FINEST,
			"receivedMessage session:{0} message:{1} returns",
			session, message);
		}
		
	    } catch (RuntimeException e) {
		if (logger.isLoggable(Level.SEVERE)) {
		    logger.logThrow(
			Level.SEVERE, e,
			"receivedMessage session:{0} message:{1} throws",
			session, message);
		}
	    }
	}
    }

    /**
     * Stores information relating to a specific transaction operating on
     * channels.
     *
     * <p>This context maintains an internal table that maps (for the
     * channels used in the context's associated transaction) channel name
     * to channel implementation.  To create, obtain, or remove a channel
     * within a transaction, the {@code createChannel},
     * {@code getChannel}, or {@code removeChannel} methods
     * (respectively) must be called on the context so that the proper
     * channel instances are used.
     */
    final class Context {

	/** The transaction. */
	final Transaction txn;

	final ChannelServiceImpl channelService;

	/** Table of all channels, obtained from the data service. */
	private final ChannelTable table;

	/**
	 * Map of channel name to transient channel impl (for those
	 * channels used during this context's associated
	 * transaction).
	 */
	private final Map<String,Channel> internalTable =
	    new HashMap<String,Channel>();

	/**
	 * Creates an instance of this class with the specified transaction.
	 */
	private Context(Transaction txn) {
	    assert txn != null;
	    this.txn = txn;
	    this.channelService = ChannelServiceImpl.this;
	    this.table = dataService.getServiceBinding(
		ChannelTable.NAME, ChannelTable.class);
	}

	/* -- ChannelManager methods -- */

	/**
	 * Creates a channel with the specified name, listener, and
	 * delivery requirement.
	 */
	private Channel createChannel(String name,
				      ChannelListener listener,
				      Delivery delivery)
	{
	    assert name != null;
	    if (table.get(name) != null) {
		throw new NameExistsException(name);
	    }

	    ChannelState channelState =
		new ChannelState(name, listener, delivery);
	    ManagedReference ref =
		dataService.createReference(channelState);
	    dataService.markForUpdate(table);
	    table.put(name, ref);
	    Channel channel = new ChannelImpl(this, channelState);
	    internalTable.put(name, channel);
	    return channel;
	}

	/**
	 * Returns a channel with the specified name.
	 */
	private Channel getChannel(String name) {
	    assert name != null;
	    Channel channel = internalTable.get(name);
	    if (channel == null) {
		ManagedReference ref = table.get(name);
		if (ref == null) {
		    throw new NameNotBoundException(name);
		}
		ChannelState channelState = ref.get(ChannelState.class);
		channel = new ChannelImpl(this, channelState);
		internalTable.put(name, channel);
	    }
	    return channel;
	}

	/**
	 * Removes the channel with the specified name.  This method is
	 * called when the 'close' method is invoked on a 'ChannelImpl'.
	 */
	void removeChannel(String name) {
	    assert name != null;
	    if (table.get(name) != null) {
		dataService.markForUpdate(table);
		table.remove(name);
		internalTable.remove(name);
	    }
	}

	<T extends Service> T getService(Class<T> type) {
	    return txnProxy.getService(type);
	}

	/**
	 * Returns the next sequence number for messages originating from
	 * this service.
	 */
	long nextSequenceNumber() {
	    return sequenceNumber.getAndIncrement();
	}
    }
    
    /**
     * Task (transactional) for notifying channel listeners.
     */
    private final class NotifyTask implements KernelRunnable {

	private final String name;
	private final byte[] senderId;
	private final byte[] message;

        NotifyTask(String name,
		 byte[] senderId,
		 byte[] message)
	{
	    this.name = name;
	    this.senderId = senderId;
	    this.message = message;
	}

        /** {@inheritDoc} */
	public void run() {
	    try {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(
                        Level.FINEST,
                        "NotifyTask.run name:{0}, message:{1}",
                        name, HexDumper.format(message));
                }
		Context context = checkContext();
		ChannelImpl channel = (ChannelImpl) context.getChannel(name);
		channel.notifyListeners(senderId, message);

	    } catch (RuntimeException e) {
		if (logger.isLoggable(Level.FINER)) {
		    logger.logThrow(
			Level.FINER, e,
			"NotifyTask.run name:{0}, message:{1} throws",
			name, HexDumper.format(message));
		}
		throw e;
	    }
	}
    }
    
    /**
     * Task (transactional) for computing the membership info needed
     * to forward a message to a channel.
     */
    private final class ForwardingTask implements KernelRunnable {

        private final String name;
        private final byte[] senderId;
        private final Set<byte[]> recipientIds;
        private final byte[] message;
        private final long seq;

        ForwardingTask(String name,
                byte[] senderId,
                Set<byte[]> recipientIds,
                byte[] message,
                long seq)
        {
            this.name = name;
            this.senderId = senderId;
            this.recipientIds = recipientIds;
            this.message = message;
            this.seq = seq;
        }

        /** {@inheritDoc} */
        public void run() {
            try {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(
                        Level.FINEST,
                        "name:{0}, message:{1}",
                        name, HexDumper.format(message));
                }
                Context context = checkContext();
                ChannelImpl channel = (ChannelImpl) context.getChannel(name);
                channel.forwardMessage(senderId, recipientIds, message, seq);

            } catch (RuntimeException e) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.logThrow(
                        Level.FINER, e,
                        "name:{0}, message:{1} throws",
                        name, HexDumper.format(message));
                }
                throw e;
            }
        }
    }
}
