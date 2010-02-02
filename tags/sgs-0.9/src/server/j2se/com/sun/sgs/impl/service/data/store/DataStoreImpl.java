/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store;

import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.db.CacheFileStats;
import com.sleepycat.db.Cursor;
import com.sleepycat.db.Database;
import com.sleepycat.db.DatabaseConfig;
import com.sleepycat.db.DatabaseEntry;
import com.sleepycat.db.DatabaseException;
import com.sleepycat.db.DatabaseType;
import com.sleepycat.db.DeadlockException;
import com.sleepycat.db.Environment;
import com.sleepycat.db.EnvironmentConfig;
import com.sleepycat.db.ErrorHandler;
import com.sleepycat.db.LockDetectMode;
import com.sleepycat.db.LockMode;
import com.sleepycat.db.LockNotGrantedException;
import com.sleepycat.db.MessageHandler;
import com.sleepycat.db.OperationStatus;
import com.sleepycat.db.RunRecoveryException;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.impl.util.PropertiesWrapper;
import com.sun.sgs.kernel.ProfileConsumer;
import com.sun.sgs.kernel.ProfileOperation;
import com.sun.sgs.kernel.ProfileProducer;
import com.sun.sgs.kernel.ProfileRegistrar;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * XXX: Implement recovery for prepared transactions after a crash.
 * -tjb@sun.com (11/07/2006)
 */

/**
 * Provides an implementation of <code>DataStore</code> based on <a href=
 * "http://www.oracle.com/database/berkeley-db.html">Berkeley DB</a>. <p>
 *
 * Operations on this class will throw an {@link Error} if the underlying
 * Berkeley DB database requires recovery.  In that case, callers need to
 * restart the server or create a new instance of this class. <p>
 *
 * Note that, although this class provides support for the {@link
 * TransactionParticipant#prepare TransactionParticipant.prepare} method, it
 * does not provide facilities for resolving prepared transactions after a
 * crash.  Callers can work around this limitation by insuring that the
 * transaction implementation calls {@link
 * TransactionParticipant#prepareAndCommit
 * TransactionParticipant.prepareAndCommit} to commit transactions on this
 * class.  The current transaction implementation calls
 * <code>prepareAndCommit</code> on durable participants, such as this class,
 * so the inability to resolve prepared transactions should have no effect at
 * present. <p>
 *
 * The {@link #DataStoreImpl constructor} supports the following properties:
 * <p>
 *
 * <ul>
 *
 * <li> <i>Key:</i> <code>com.sun.sgs.txnTimeout</code> <br>
 *	<i>Default:</i> <code>1000</code> <br>
 *	The maximum amount of time in milliseconds that a transaction will be
 *	permitted to run before it is a candidate for being aborted. <p>
 *
 * <li> <i>Key:</i>
 *	<code>com.sun.sgs.impl.service.data.store.DataStoreImpl.directory
 *	</code> <br>
 *	<i>Default:</i> <code>${com.sun.sgs.app.root}"/dsdb"</code> <br>
 *	The directory in which to store database files.  Each instance of
 *	<code>DataStoreImpl</code> requires its own, unique directory. <p>
 *
 * <li> <i>Key:</i> <code>
 *	com.sun.sgs.impl.service.data.store.DataStoreImpl.allocationBlockSize
 *	</code> <br>
 *	<i>Default:</i> <code>100</code> <br>
 *	The number of object IDs to allocate at a time.  Object IDs are
 *	allocated in an independent transaction, and are discarded if a
 *	transaction aborts, if a managed object is made reachable within the
 *	data store but is removed from the store before the transaction
 *	commits, or if the program exits before it uses the object IDs it has
 *	allocated.  This number limits the maximum number of object IDs that
 *	would be discarded when the program exits. <p>
 *
 * <li> <i>Key:</i>
 *	<code>com.sun.sgs.impl.service.data.store.DataStoreImpl.cacheSize
 *	</code> <br>
 *	<i>Default:</i> <code>1000000</code> <br>
 *	The size in bytes of the Berkeley DB cache.  This value must not be
 *	less than 20000. <p>
 *
 * <li> <i>Key:</i>
 *	<code>com.sun.sgs.impl.service.data.store.DataStoreImpl.flushToDisk
 *	</code> <br>
 *	<i>Default:</i> <code>false</code>
 *	Whether to flush changes to disk when a transaction commits.  If
 *	<code>false</code>, the modifications made in some of the most recent
 *	transactions may be lost if the host crashes, although data integrity
 *	will be maintained.  Flushing changes to disk avoids data loss but
 *	introduces a significant reduction in performance. <p>
 *
 * <li> <i>Key:</i>
 *	<code>com.sun.sgs.impl.service.data.store.DataStoreImpl.logStats</code>
 *	<br>
 *	<i>Default:</i> <code>Integer.MAX_VALUE</code> <br>
 *	The number of transactions between logging database statistics. <p>
 *
 * </ul> <p>
 *
 * This class uses the {@link Logger} named
 * <code>com.sun.sgs.impl.service.data.DataStoreImpl</code> to log information
 * at the following logging levels: <p>
 *
 * <ul>
 * <li> {@link Level#SEVERE SEVERE} - Initialization failures
 * <li> {@link Level#WARNING WARNING} - Berkeley DB errors
 * <li> {@link Level#INFO INFO} - Berkeley DB statistics
 * <li> {@link Level#CONFIG CONFIG} - Constructor properties, data store
 *	headers
 * <li> {@link Level#FINE FINE} - Berkeley DB messages, allocating blocks of
 *	object IDs
 * <li> {@link Level#FINER FINER} - Transaction operations
 * <li> {@link Level#FINEST FINEST} - Name and object operations
 * </ul> <p>
 *
 */
public final class DataStoreImpl implements DataStore, TransactionParticipant,
                                            ProfileProducer {

    /** The property that specifies the transaction timeout in milliseconds. */
    private static final String TXN_TIMEOUT_PROPERTY =
	"com.sun.sgs.txnTimeout";

    /** The default transaction timeout in milliseconds. */
    private static final long DEFAULT_TXN_TIMEOUT = 1000;

    /** The name of this class. */
    private static final String CLASSNAME = DataStoreImpl.class.getName();

    /**
     * The property that specifies the directory in which to store database
     * files.
     */
    private static final String DIRECTORY_PROPERTY = CLASSNAME + ".directory";

    /** The default directory for database files from the app root. */
    private static final String DEFAULT_DIRECTORY = "dsdb";

    /**
     * The property that specifies the number of object IDs to allocate at one
     * time.
     */
    private static final String ALLOCATION_BLOCK_SIZE_PROPERTY =
	CLASSNAME + ".allocationBlockSize";

    /** The default for the number of object IDs to allocate at one time. */
    private static final int DEFAULT_ALLOCATION_BLOCK_SIZE = 100;

    /**
     * The property that specifies the size in bytes of the Berkeley DB cache.
     */
    private static final String CACHE_SIZE_PROPERTY = CLASSNAME + ".cacheSize";

    /** The minimum cache size, as specified by Berkeley DB */
    private static final long MIN_CACHE_SIZE = 20000;

    /** The default cache size. */
    private static final long DEFAULT_CACHE_SIZE = 1000000L;

    /**
     * The property that specifies whether to flush changes to disk on
     * transaction boundaries.  The property is set to false by default.  If
     * false, some recent transactions may be lost in the event of a crash,
     * although integrity will be maintained.
     */
    private static final String FLUSH_TO_DISK_PROPERTY =
	CLASSNAME + ".flushToDisk";

    /**
     * The property that specifies the number of transactions between logging
     * database statistics.
     */
    private static final String LOG_STATS_PROPERTY = CLASSNAME + ".logStats";

    /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));

    /** An empty array returned when Berkeley DB returns null for a value. */
    private static final byte[] NO_BYTES = { };

    /** The directory in which to store database files. */
    private final String directory;

    /** The number of object IDs to allocate at one time. */
    private final int allocationBlockSize;

    /** The number of transactions between logging database statistics. */
    private final int logStats;

    /** The Berkeley DB environment. */
    private final Environment env;

    /**
     * The Berkeley DB database that holds version and next object ID
     * information.  This information is stored in a separate database to avoid
     * concurrency conflicts between the object ID and other data.
     */
    private final Database info;

    /** The Berkeley DB database that maps object IDs to object bytes. */
    private final Database oids;

    /** The Berkeley DB database that maps name bindings to object IDs. */
    private final Database names;

    /** Provides information about the transaction for the current thread. */
    private final ThreadLocal<TxnInfo> threadTxnInfo =
	new ThreadLocal<TxnInfo>();

    /**
     * Object to synchronize on when accessing nextObjectId and
     * lastObjectId.
     */
    private final Object objectIdLock = new Object();

    /**
     * The next object ID to use for creating an object.  Valid if not greater
     * than lastObjectId.
     */
    private long nextObjectId = 0;

    /**
     * The last object ID that is free for allocating an object before needing
     * to obtain more IDs from the database.
     */
    private long lastObjectId = -1;

    /**
     * The number of transactions since the database statistics were logged.
     */
    private int logStatsCount = 0;

    /** Object to synchronize on when accessing txnCount and allOps. */
    private final Object txnCountLock = new Object();

    /** The number of currently active transactions. */
    private int txnCount = 0;

    /* -- The operations -- DataStore API -- */
    private ProfileOperation createObjectOp = null;
    private ProfileOperation markForUpdateOp = null;
    private ProfileOperation getObjectOp = null;
    private ProfileOperation getObjectForUpdateOp = null;
    private ProfileOperation setObjectOp = null;
    private ProfileOperation removeObjectOp = null;
    private ProfileOperation getBindingOp = null;
    private ProfileOperation setBindingOp = null;
    private ProfileOperation removeBindingOp = null;
    private ProfileOperation nextBoundNameOp = null;

    /** Stores transaction information. */
    private static class TxnInfo {

	/** The SGS transaction. */
	final Transaction txn;

	/** The associated Berkeley DB transaction. */
	final com.sleepycat.db.Transaction bdbTxn;

	/** Whether preparation of the transaction has started. */
	boolean prepared;

	/** Whether any changes have been made in this transaction. */
	boolean modified;

	/**
	 * The currently open Berkeley DB cursor or null.  The cursor must be
	 * closed before the transaction is prepared, committed, or aborted.
	 * Note that the Berkeley DB documentation for prepare doesn't say you
	 * need to close cursors, but my testing shows that you do.
	 * -tjb@sun.com (12/14/2006)
	 */
	private Cursor cursor;

	/** The last key returned by the cursor or null. */
	private String lastCursorKey;

	TxnInfo(Transaction txn, Environment env) throws DatabaseException {
	    this.txn = txn;
	    bdbTxn = env.beginTransaction(null, null);
	}

	/** Prepares the transaction, first closing the cursor, if present. */
	void prepare(byte[] gid) throws DatabaseException {
	    maybeCloseCursor();
	    bdbTxn.prepare(gid);
	}

	/**
	 * Commits the transaction, first closing the cursor, if present, and
	 * returning the operations count for this transaction.
	 */
	void commit() throws DatabaseException {
	    maybeCloseCursor();
	    bdbTxn.commit();
	}

	/**
	 * Aborts the transaction, first closing the cursor, if present, and
	 * returning the operations count for this transaction.
	 */
	void abort() throws DatabaseException {
	    maybeCloseCursor();
	    bdbTxn.abort();
	}

	/** Returns the next name in the names database. */
	String nextName(String name, Database names) throws DatabaseException {
	    if (cursor == null) {
		cursor = names.openCursor(bdbTxn, null);
	    }
	    DatabaseEntry key = new DatabaseEntry();
	    DatabaseEntry value = new DatabaseEntry();
	    if (name == null) {
		OperationStatus status = cursor.getFirst(key, value, null);
		lastCursorKey = getNextBoundNameResult(name, status, key);
	    } else {
		boolean matchesLast = name.equals(lastCursorKey);
		if (!matchesLast) {
		    StringBinding.stringToEntry(name, key);
		    OperationStatus status =
			cursor.getSearchKeyRange(key, value, null);
		    lastCursorKey = getNextBoundNameResult(name, status, key);
		    matchesLast = name.equals(lastCursorKey);
		}
		if (matchesLast) {
		    OperationStatus status = cursor.getNext(key, value, null);
		    lastCursorKey = getNextBoundNameResult(name, status, key);
		}
	    }
	    return lastCursorKey;
	}

	/**
	 * Close the cursor if it is open.  Always null the cursor field, since
	 * the Berkeley DB API doesn't permit closing a cursor after an attempt
	 * to close it.
	 */
	private void maybeCloseCursor() throws DatabaseException {
	    if (cursor != null) {
		Cursor c = cursor;
		cursor = null;
		c.close();
	    }
	}

	/**
	 * Returns the name of the next binding given the results of a cursor
	 * operation and the associated key.
	 */
	private String getNextBoundNameResult(
	    String name, OperationStatus status, DatabaseEntry key)
	{
	    if (status == OperationStatus.NOTFOUND) {
		return null;
	    } else if (status == OperationStatus.SUCCESS) {
		return StringBinding.entryToString(key);
	    } else {
		throw new DataStoreException(
		    "nextBoundName txn:" + txn + ", name:" + name +
		    " failed: " + status);
	    }
	}
    }

    /** A Berkeley DB message handler that uses logging. */
    private static class LoggingMessageHandler implements MessageHandler {
	public void message(Environment env, String message) {
	    logger.log(Level.FINE, "Database message: {0}", message);
	}
    }

    /** A Berkeley DB error handler that uses logging. */
    private static class LoggingErrorHandler implements ErrorHandler {
	public void error(Environment env, String prefix, String message) {
	    if (logger.isLoggable(Level.WARNING)) {
		logger.logThrow(Level.WARNING, new Exception("Stacktrace"),
				"Database error message: {0}{1}",
				prefix != null ? prefix : "", message);
	    }
	}
    }

    /**
     * Creates an instance of this class configured with the specified
     * properties.  See the {@link DataStoreImpl class documentation} for a
     * list of supported properties.
     *
     * @param	properties the properties for configuring this instance
     * @throws	DataStoreException if there is a problem with the database
     * @throws	IllegalArgumentException if neither
     *		<code>com.sun.sgs.app.root</code> nor <code>
     *		com.sun.sgs.impl.service.data.store.DataStoreImpl.directory
     *		</code> is provided, or the <code>
     *		com.sun.sgs.impl.service.data.store.DataStoreImpl.allocationBlockSize
     *		</code> property is not a valid integer greater than zero
     */
    public DataStoreImpl(Properties properties) {
	logger.log(
	    Level.CONFIG, "Creating DataStoreImpl properties:{0}", properties);
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	String specifiedDirectory =
	    wrappedProps.getProperty(DIRECTORY_PROPERTY);
	if (specifiedDirectory == null) {
	    String rootDir =
		properties.getProperty(StandardProperties.APP_ROOT);
	    if (rootDir == null) {
		throw new IllegalArgumentException(
		    "A value for the property " + StandardProperties.APP_ROOT +
		    " must be specified");
	    }
	    specifiedDirectory = rootDir + File.separator + DEFAULT_DIRECTORY;
	}
	/*
	 * Use an absolute path to avoid problems on Windows.
	 * -tjb@sun.com (02/16/2007)
	 */
	directory = new File(specifiedDirectory).getAbsolutePath();
	allocationBlockSize = wrappedProps.getIntProperty(
	    ALLOCATION_BLOCK_SIZE_PROPERTY, DEFAULT_ALLOCATION_BLOCK_SIZE);
	if (allocationBlockSize < 1) {
	    throw new IllegalArgumentException(
		"The allocation block size must be greater than zero");
	}
	logStats = wrappedProps.getIntProperty(
	    LOG_STATS_PROPERTY, Integer.MAX_VALUE);
	com.sleepycat.db.Transaction bdbTxn = null;
	boolean done = false;
	try {
	    env = getEnvironment(properties);
	    bdbTxn = env.beginTransaction(null, null);
	    DatabaseConfig createConfig = new DatabaseConfig();
	    createConfig.setType(DatabaseType.BTREE);
	    createConfig.setAllowCreate(true);
	    boolean create = false;
	    String infoFileName = directory + File.separator + "info";
	    Database infoTmp;
	    try {
		infoTmp = env.openDatabase(bdbTxn, infoFileName, null, null);
		int minorVersion = DataStoreHeader.verify(infoTmp, bdbTxn);
		if (logger.isLoggable(Level.CONFIG)) {
		    logger.log(Level.CONFIG, "Found existing header {0}",
			       DataStoreHeader.headerString(minorVersion));
		}
	    } catch (FileNotFoundException e) {
		try {
		    infoTmp = env.openDatabase(
			bdbTxn, infoFileName, null, createConfig);
		} catch (FileNotFoundException e2) {
		    throw new DataStoreException(
			"Problem creating database: " + e2.getMessage(),
			e2);
		}
		DataStoreHeader.create(infoTmp, bdbTxn);
		if (logger.isLoggable(Level.CONFIG)) {
		    logger.log(Level.CONFIG, "Created new header {0}",
			       DataStoreHeader.headerString());
		}
		create = true;
	    }
	    info = infoTmp;
	    try {
		oids = env.openDatabase(
		    bdbTxn, directory + File.separator + "oids", null,
		    create ? createConfig : null);
	    } catch (FileNotFoundException e) {
		throw new DataStoreException(
		    "Oids database not found: " + e.getMessage(), e);
	    }
	    try {
		names = env.openDatabase(
		    bdbTxn, directory + File.separator + "names", null,
		    create ? createConfig : null);
	    } catch (FileNotFoundException e) {
		throw new DataStoreException(
		    "Names database not found: " + e.getMessage(), e);
	    }
	    done = true;
	    bdbTxn.commit();
	} catch (DatabaseException e) {
	    throw convertException(
		null, Level.SEVERE, e, "DataStore initialization");
	} finally {
	    if (bdbTxn != null && !done) {
		try {
		    bdbTxn.abort();
		} catch (DatabaseException e) {
		    logger.logThrow(Level.FINE, e, "Exception during abort");
		}
	    }
	}
    }

    /**
     * Obtains a Berkeley DB environment suitable for the specified
     * properties.
     */
    private Environment getEnvironment(Properties properties)
	throws DatabaseException
    {
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	long timeout = 1000L * wrappedProps.getLongProperty(
	    TXN_TIMEOUT_PROPERTY, DEFAULT_TXN_TIMEOUT);
	boolean flushToDisk = wrappedProps.getBooleanProperty(
	    FLUSH_TO_DISK_PROPERTY, false);
	long cacheSize = wrappedProps.getLongProperty(
	    CACHE_SIZE_PROPERTY, DEFAULT_CACHE_SIZE);
	if (cacheSize < MIN_CACHE_SIZE) {
	    throw new IllegalArgumentException(
		"The cache size must not be less than " + MIN_CACHE_SIZE);
	}
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
	config.setCacheSize(cacheSize);
	config.setErrorHandler(new LoggingErrorHandler());
        config.setInitializeCache(true);
        config.setInitializeLocking(true);
        config.setInitializeLogging(true);
        config.setLockDetectMode(LockDetectMode.YOUNGEST);
	config.setLockTimeout(timeout);
	config.setMessageHandler(new LoggingMessageHandler());
        config.setRunRecovery(true);
        config.setTransactional(true);
	config.setTxnTimeout(timeout);
	config.setTxnWriteNoSync(!flushToDisk);
	try {
	    return new Environment(new File(directory), config);
	} catch (FileNotFoundException e) {
	    throw new DataStoreException(
		"DataStore directory does not exist: " + directory);
	}
    }

    /* -- Implement DataStore -- */

    /** {@inheritDoc} */
    public long createObject(Transaction txn) {
	logger.log(Level.FINEST, "createObject txn:{0}", txn);
	Exception exception;
	try {
	    checkTxn(txn, createObjectOp);
	    long result;
	    synchronized (objectIdLock) {
		if (nextObjectId > lastObjectId) {
		    logger.log(Level.FINE, "Allocate more object IDs");
		    long newNextObjectId;
		    com.sleepycat.db.Transaction bdbTxn =
			env.beginTransaction(null, null);
		    boolean done = false;
		    try {
			newNextObjectId = DataStoreHeader.getNextId(
			    info, bdbTxn, allocationBlockSize);
			done = true;
			bdbTxn.commit();
		    } finally {
			if (!done) {
			    bdbTxn.abort();
			}
		    }
		    nextObjectId = newNextObjectId;
		    lastObjectId = newNextObjectId + allocationBlockSize - 1;
		}
		result = nextObjectId++;
	    }
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "createObject txn:{0} returns oid:{1,number,#}",
			   txn, result);
	    }
	    return result;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(
	    txn, Level.FINEST, exception, "createObject txn:" + txn);
    }

    /** {@inheritDoc} */
    public void markForUpdate(Transaction txn, long oid) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "markForUpdate txn:{0}, oid:{1,number,#}",
		       txn, oid);
	}
	/*
	 * Berkeley DB doesn't seem to provide a way to obtain a write lock
	 * without reading or writing, so get the object and ask for a write
	 * lock.  -tjb@sun.com (10/06/2006)
	 */
	Exception exception;
	try {
	    getObjectInternal(txn, oid, true, markForUpdateOp);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "markForUpdate txn:{0}, oid:{1,number,#} returns",
			   txn, oid);
	    }
	    return;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(txn, Level.FINEST, exception,
			       "markForUpdate txn:" + txn + ", oid:" + oid);
    }

    /** {@inheritDoc} */
    public byte[] getObject(Transaction txn, long oid, boolean forUpdate) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST,
		       "getObject txn:{0}, oid:{1,number,#}, forUpdate:{2}",
		       txn, oid, forUpdate);
	}
	Exception exception;
	try {
	    byte[] result = getObjectInternal(
		txn, oid, forUpdate,
		forUpdate ? getObjectForUpdateOp : getObjectOp);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "getObject txn:{0}, oid:{1,number,#}, forUpdate:{2} " +
		    "returns",
		    txn, oid, forUpdate);
	    }
	    return result;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(txn, Level.FINEST, exception,
			       "getObject txn:" + txn + ", oid:" + oid +
			       ", forUpdate:" + forUpdate);
    }

    /** Implement getObject, without logging. */
    private byte[] getObjectInternal(
	Transaction txn, long oid, boolean forUpdate, ProfileOperation op)
	throws DatabaseException
    {
	checkId(oid);
	TxnInfo txnInfo = checkTxn(txn, op);
	DatabaseEntry key = new DatabaseEntry();
	LongBinding.longToEntry(oid, key);
	DatabaseEntry value = new DatabaseEntry();
	OperationStatus status = oids.get(
	    txnInfo.bdbTxn, key, value, forUpdate ? LockMode.RMW : null);
	if (status == OperationStatus.NOTFOUND) {
	    throw new ObjectNotFoundException("Object not found: " + oid);
	} else if (status != OperationStatus.SUCCESS) {
	    throw new DataStoreException("getObject txn:" + txn + ", oid:" +
					 oid + ", forUpdate:" + forUpdate +
					 " failed: " + status);
	}
	byte[] result = value.getData();
	/* Berkeley DB returns null if the data is empty. */
	return result != null ? result : NO_BYTES;
    }

    /** {@inheritDoc} */
    public void setObject(Transaction txn, long oid, byte[] data) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "setObject txn:{0}, oid:{1,number,#}",
		       txn, oid);
	}
	Exception exception;
	try {
	    checkId(oid);
	    if (data == null) {
		throw new NullPointerException("The data must not be null");
	    }
	    TxnInfo txnInfo = checkTxn(txn, setObjectOp);
	    DatabaseEntry key = new DatabaseEntry();
	    LongBinding.longToEntry(oid, key);
	    DatabaseEntry value = new DatabaseEntry(data);
	    OperationStatus status = oids.put(txnInfo.bdbTxn, key, value);
	    if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "setObject txn: " + txn + ", oid:" + oid + " failed: " +
		    status);
	    }
	    txnInfo.modified = true;
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "setObject txn:{0}, oid:{1,number,#} returns",
			   txn, oid);
	    }
	    return;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(txn, Level.FINEST, exception,
			       "setObject txn:" + txn + ", oid:" + oid);
    }

    /** {@inheritDoc} */
    public void removeObject(Transaction txn, long oid) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "removeObject txn:{0}, oid:{1,number,#}",
		       txn, oid);
	}
	Exception exception;
	try {
	    checkId(oid);
	    TxnInfo txnInfo = checkTxn(txn, removeObjectOp);
	    DatabaseEntry key = new DatabaseEntry();
	    LongBinding.longToEntry(oid, key);
	    OperationStatus status = oids.delete(txnInfo.bdbTxn, key);
	    if (status == OperationStatus.NOTFOUND) {
		throw new ObjectNotFoundException("Object not found: " + oid);
	    } else if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "removeObject txn:" + txn + ", oid:" + oid + " failed: " +
		    status);
	    }
	    txnInfo.modified = true;
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "removeObject txn:{0}, oid:{1,number,#} returns",
			   txn, oid);
	    }
	    return;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(txn, Level.FINEST, exception,
			       "removeObject txn:" + txn + ", oid:" + oid);
    }

    /** {@inheritDoc} */
    public long getBinding(Transaction txn, String name) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST, "getBinding txn:{0}, name:{1}", txn, name);
	}
	Exception exception;
	try {
	    if (name == null) {
		throw new NullPointerException("Name must not be null");
	    }
	    TxnInfo txnInfo = checkTxn(txn, getBindingOp);
	    DatabaseEntry key = new DatabaseEntry();
	    StringBinding.stringToEntry(name, key);
	    DatabaseEntry value = new DatabaseEntry();
	    OperationStatus status =
		names.get(txnInfo.bdbTxn, key, value, null);
	    if (status == OperationStatus.NOTFOUND) {
		throw new NameNotBoundException("Name not bound: " + name);
	    } else if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "getBinding txn:" + txn + ", name:" + name + " failed: " +
		    status);
	    }
	    long result = LongBinding.entryToLong(value);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "getBinding txn:{0}, name:{1} returns oid:{2,number,#}",
		    txn, name, result);
	    }
	    return result;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(txn, Level.FINEST, exception,
			       "getBinding txn:" + txn + ", name:" + name);
    }

    /** {@inheritDoc} */
    public void setBinding(Transaction txn, String name, long oid) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST, "setBinding txn:{0}, name:{1}, oid:{2,number,#}",
		txn, name, oid);
	}
	Exception exception;
	try {
	    if (name == null) {
		throw new NullPointerException("Name must not be null");
	    }
	    checkId(oid);
	    TxnInfo txnInfo = checkTxn(txn, setBindingOp);
	    DatabaseEntry key = new DatabaseEntry();
	    StringBinding.stringToEntry(name, key);
	    DatabaseEntry value = new DatabaseEntry();
	    LongBinding.longToEntry(oid, value);
	    OperationStatus status = names.put(txnInfo.bdbTxn, key, value);
	    if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "setBinding txn:" + txn + ", name:" + name + " failed: " +
		    status);
	    }
	    txnInfo.modified = true;
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST,
		    "setBinding txn:{0}, name:{1}, oid:{2,number,#} returns",
		    txn, name, oid);
	    }
	    return;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(
	    txn, Level.FINEST, exception,
	    "setBinding txn:" + txn + ", name:" + name + ", oid:" + oid);
    }

    /** {@inheritDoc} */
    public void removeBinding(Transaction txn, String name) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST, "removeBinding txn:{0}, name:{1}", txn, name);
	}
	Exception exception;
	try {
	    if (name == null) {
		throw new NullPointerException("Name must not be null");
	    }
	    TxnInfo txnInfo = checkTxn(txn, removeBindingOp);
	    DatabaseEntry key = new DatabaseEntry();
	    StringBinding.stringToEntry(name, key);
	    OperationStatus status = names.delete(txnInfo.bdbTxn, key);
	    if (status == OperationStatus.NOTFOUND) {
		throw new NameNotBoundException("Name not bound: " + name);
	    } else if (status != OperationStatus.SUCCESS) {
		throw new DataStoreException(
		    "removeBinding txn:" + txn + ", name:" + name +
		    " failed: " + status);
	    }
	    txnInfo.modified = true;
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(
		    Level.FINEST, "removeBinding txn:{0}, name:{1} returns",
		    txn, name);
	    }
	    return;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(txn, Level.FINEST, exception,
			       "removeBinding txn:" + txn + ", name:" + name);
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation uses a single cursor, so it provides better
     * performance when used to iterate over names in order.
     */
    public String nextBoundName(Transaction txn, String name) {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(
		Level.FINEST, "nextBoundName txn:{0}, name:{1}", txn, name);
	}
	Exception exception;
	try {
	    TxnInfo txnInfo = checkTxn(txn, nextBoundNameOp);
	    String result = txnInfo.nextName(name, names);
	    if (logger.isLoggable(Level.FINEST)) {
		logger.log(Level.FINEST,
			   "nextBoundName txn:{0}, name:{1} returns {2}",
			   txn, name, result);
	    }
	    return result;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(txn, Level.FINEST, exception,
			       "nextBoundName txn:" + txn + ", name:" + name);
    }

    /* -- Implement TransactionParticipant -- */

    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) {
	logger.log(Level.FINER, "prepare txn:{0}", txn);
	Exception exception;
	try {
	    TxnInfo txnInfo = checkTxnNoJoin(txn, true);
	    if (txnInfo.prepared) {
		throw new IllegalStateException(
		    "Transaction has already been prepared");
	    }
	    if (txnInfo.modified) {
		byte[] oid = txn.getId();
		/*
		 * Berkeley DB requires transaction IDs to be at least 128
		 * bytes long.  -tjb@sun.com (11/07/2006)
		 */
		byte[] gid = new byte[128];
		System.arraycopy(oid, 0, gid, 128 - oid.length, oid.length);
		txnInfo.prepare(gid);
		txnInfo.prepared = true;
	    } else {
		/*
		 * Make sure to clear the transaction information, regardless
		 * of whether the Berkeley DB commit operation succeeds, since
		 * Berkeley DB doesn't permit operating on its transaction
		 * object after commit is called.
		 */
		try {
		    threadTxnInfo.set(null);
		    txnInfo.commit();
		} finally {
		    decrementTxnCount();
		} 
	    }
	    boolean result = !txnInfo.modified;
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(
		    Level.FINER, "prepare txn:{0} returns {1}", txn, result);
	    }
	    return result;
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(
	    txn, Level.FINER, exception, "prepare txn:" + txn);
    }

    /** {@inheritDoc} */
    public void commit(Transaction txn) {
	logger.log(Level.FINER, "commit txn:{0}", txn);
	Exception exception;
	try {
	    TxnInfo txnInfo = checkTxnNoJoin(txn, true);
	    if (!txnInfo.prepared) {
		throw new IllegalStateException(
		    "Transaction has not been prepared");
	    }
	    /*
	     * Make sure to clear the transaction information, regardless of
	     * whether the Berkeley DB commit operation succeeds, since
	     * Berkeley DB doesn't permit operating on its transaction object
	     * after commit is called.
	     */
	    threadTxnInfo.set(null);
	    try {
		txnInfo.commit();
		logger.log(Level.FINER, "commit txn:{0} returns", txn);
		return;
	    } finally {
		decrementTxnCount();
	    }
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(
	    txn, Level.FINER, exception, "commit txn:" + txn);
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(Transaction txn) {
	logger.log(Level.FINER, "prepareAndCommit txn:{0}", txn);
	Exception exception;
	try {
	    TxnInfo txnInfo = checkTxnNoJoin(txn, true);
	    if (txnInfo.prepared) {
		throw new IllegalStateException(
		    "Transaction has already been prepared");
	    }
	    /*
	     * Make sure to clear the transaction information, regardless of
	     * whether the Berkeley DB commit operation succeeds, since
	     * Berkeley DB doesn't permit operating on its transaction object
	     * after commit is called.
	     */
	    threadTxnInfo.set(null);
	    try {
		txnInfo.commit();
		logger.log(
		    Level.FINER, "prepareAndCommit txn:{0} returns", txn);
		return;
	    } finally {
		decrementTxnCount();
	    }
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(
	    txn, Level.FINER, exception, "prepareAndCommit txn:" + txn);
    }

    /** {@inheritDoc} */
    public void abort(Transaction txn) {
	logger.log(Level.FINER, "abort txn:{0}", txn);
	Exception exception;
	try {
	    TxnInfo txnInfo = checkTxnNoJoin(txn, false);
	    /*
	     * Make sure to clear the transaction information, regardless of
	     * whether the Berkeley DB commit operation succeeds, since
	     * Berkeley DB doesn't permit operating on its transaction object
	     * after commit is called.
	     */
	    threadTxnInfo.set(null);
	    try {
		txnInfo.abort();
		logger.log(Level.FINER, "abort txn:{0} returns", txn);
		return;
	    } finally {
		decrementTxnCount();
	    }
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(
	    txn, Level.FINER, exception, "abort txn:" + txn);
    }

    /* -- Implements ProfileProducer -- */

    /** {@inheritDoc} */
    public void setProfileRegistrar(ProfileRegistrar profileRegistrar) {
        ProfileConsumer consumer =
            profileRegistrar.registerProfileProducer(this);

	if (consumer != null) {
	    getBindingOp = consumer.registerOperation("getBinding");
	    setBindingOp = consumer.registerOperation("setBinding");
	    removeBindingOp = consumer.registerOperation("removeBinding");
	    nextBoundNameOp = consumer.registerOperation("nextBoundName");
	    removeObjectOp = consumer.registerOperation("removeObject");
	    markForUpdateOp = consumer.registerOperation("markForUpdate");
	    createObjectOp = consumer.registerOperation("createObject");
	    getObjectOp = consumer.registerOperation("getObject");
	    getObjectForUpdateOp =
		consumer.registerOperation("getObjectForUpdate");
	    setObjectOp = consumer.registerOperation("setObject");
	}
    }

    /* -- Other public methods -- */

    /** {@inheritDoc} */
    public boolean shutdown() {
	logger.log(Level.FINER, "shutdown");
	Exception exception;
	try {
	    synchronized (txnCountLock) {
		while (txnCount > 0) {
		    try {
			logger.log(Level.FINEST,
				   "shutdown waiting for {0} transactions",
				   txnCount);
			txnCountLock.wait();
		    } catch (InterruptedException e) {
			logger.log(Level.FINEST, "shutdown interrupted");
			break;
		    }
		}
		if (txnCount < 0) {
		    throw new IllegalStateException("DataStore is shut down");
		}
		boolean ok = (txnCount == 0);
		if (ok) {
		    info.close();
		    oids.close();
		    names.close();
		    env.close();
		    txnCount = -1;
		}
		logger.log(Level.FINER, "shutdown returns {0}", ok);
		return ok;
	    }
	} catch (DatabaseException e) {
	    exception = e;
	} catch (RuntimeException e) {
	    exception = e;
	}
	throw convertException(null, Level.FINER, exception, "shutdown");
    }

    /**
     * Returns a string representation of this object.
     *
     * @return	a string representation of this object
     */
    public String toString() {
	return "DataStoreImpl[directory=\"" + directory + "\"]";
    }

    /* -- Private methods -- */

    /** Checks that the object ID argument is not negative. */
    private void checkId(long oid) {
	if (oid < 0) {
	    throw new IllegalArgumentException(
		"Object ID must not be negative");
	}
    }

    /**
     * Checks that the correct transaction is in progress, and join if none is
     * in progress.  The op argument, if non-null, specifies the operation
     * being performed under the specified transaction.
     */
    private TxnInfo checkTxn(Transaction txn, ProfileOperation op)
	throws DatabaseException
    {
	if (txn == null) {
	    throw new NullPointerException("Transaction must not be null");
	}
	TxnInfo txnInfo = threadTxnInfo.get();
	if (txnInfo == null) {
	    synchronized (txnCountLock) {
		if (txnCount < 0) {
		    throw new IllegalStateException("Service is shut down");
		}
		txnCount++;
	    }
	    boolean joined = false;
	    try {
		txn.join(this);
		joined = true;
	    } finally {
		if (!joined) {
		    decrementTxnCount();
		}
	    }
	    txnInfo = new TxnInfo(txn, env);
	    threadTxnInfo.set(txnInfo);
	    if (++logStatsCount >= logStats) {
		logStatsCount = 0;
		logStats(txnInfo);
	    }
	} else if (!txnInfo.txn.equals(txn)) {
	    throw new IllegalStateException("Wrong transaction");
	} else if (txnInfo.prepared) {
	    throw new IllegalStateException(
		"Transaction has been prepared");
	}
        if (op != null)
            op.report();
	return txnInfo;
    }

    /**
     * Checks that the correct transaction is in progress, throwing an
     * exception if the transaction has not been joined.  Checks if the store
     * is shutting down if requested.  Does not check the prepared state of the
     * transaction.
     */
    private TxnInfo checkTxnNoJoin(
	Transaction txn, boolean checkShuttingDown)
    {
	if (txn == null) {
	    throw new NullPointerException("Transaction must not be null");
	}
	TxnInfo txnInfo = threadTxnInfo.get();
	if (txnInfo == null) {
	    throw new IllegalStateException("Transaction is not active");
	} else if (!txnInfo.txn.equals(txn)) {
	    throw new IllegalStateException("Wrong transaction");
	} else if (checkShuttingDown && getTxnCount() < 0) {
	    throw new IllegalStateException("DataStore is shutting down");
	}
	return txnInfo;
    }

    /**
     * Returns the correct SGS exception for a Berkeley DB DatabaseException
     * thrown during an operation.  Throws an Error if recovery is needed.  The
     * txn argument, if non-null, is used to abort the transaction if a
     * TransactionAbortedException is going to be thrown.  The level argument
     * is used to log the exception.  The operation argument will be included
     * in newly created exceptions and the log, and should describe the
     * operation that was underway when the exception was thrown.  The supplied
     * exception may also be a RuntimeException, which will be logged and
     * returned.
     */
    private RuntimeException convertException(
	Transaction txn, Level level, Exception e, String operation)
    {
	RuntimeException re;
	if (e instanceof LockNotGrantedException) {
	    re = new TransactionTimeoutException(
		operation + " failed due to timeout: " + e.getMessage(), e);
	} else if (e instanceof DeadlockException) {
	    re = new TransactionConflictException(
		operation + " failed due to deadlock: " + e.getMessage(), e);
	} else if (e instanceof RunRecoveryException) {
	    /*
	     * It is tricky to clean up the data structures in this instance in
	     * order to reopen the Berkeley DB databases, because it's hard to
	     * know when they are no longer in use.  It's OK to catch this
	     * Error and create a new DataStoreImpl instance, but this instance
	     * is dead.  -tjb@sun.com (10/19/2006)
	     */
	    Error error = new Error(
		operation + " failed: " +
		"Database requires recovery -- need to restart the server " +
		"or create a new instance of DataStoreImpl: " + e.getMessage(),
		e);
	    logger.logThrow(Level.SEVERE, error, "{0} throws", operation);
	    throw error;
	} else if (e instanceof DatabaseException) {
	    re = new DataStoreException(
		operation + " failed: " + e.getMessage(), e);
	} else {
	    re = (RuntimeException) e;
	}
	/*
	 * If we're throwing an exception saying that the transaction was
	 * aborted, then make sure to abort the transaction now.
	 */
	if (re instanceof TransactionAbortedException && txn != null) {
	    txn.abort(re);
	}
	logger.logThrow(Level.FINEST, re, "{0} throws", operation);
	return re;
    }

    /** Log statistics for the specified transaction. */
    private void logStats(TxnInfo txnInfo) throws DatabaseException {
	if (logger.isLoggable(Level.INFO)) {
	    StringBuilder allCacheFileStats = new StringBuilder();
	    boolean first = true;
	    for (CacheFileStats stats : env.getCacheFileStats(null)) {
		if (first) {
		    first = false;
		} else {
		    allCacheFileStats.append('\n');
		}
		allCacheFileStats.append(stats);
	    }
	    logger.log(Level.INFO,
		       "Berkeley DB statistics:\n" +
		       "Info database: {0}\n" +
		       "Oids database: {1}\n" +
		       "Names database: {2}\n" +
		       "{3}\n" +
		       "{4}\n" +
		       "{5}\n" +
		       "{6}\n" +
		       "{7}\n" +
		       "{8}",
		       info.getStats(txnInfo.bdbTxn, null),
		       oids.getStats(txnInfo.bdbTxn, null),
		       names.getStats(txnInfo.bdbTxn, null),
		       allCacheFileStats,
		       env.getCacheStats(null),
		       env.getLockStats(null),
		       env.getLogStats(null),
		       env.getMutexStats(null),
		       env.getTransactionStats(null));
	}
    }

    /** Returns the current transaction count. */
    private int getTxnCount() {
	synchronized (txnCountLock) {
	    return txnCount;
	}
    }

    /**
     * Decrements the current transaction count.  If the argument is not null,
     * tallies the operations that were recorded for the transaction.
     */
    private void decrementTxnCount() {
	synchronized (txnCountLock) {
	    txnCount--;
	    if (txnCount <= 0) {
		txnCountLock.notifyAll();
	    }
	}
    }
}
