/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.data;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyManagedObject;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;

/** Test concurrent operation. */
@SuppressWarnings("hiding")
public class TestConcurrent extends TestCase {

    /** Logger for this test. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(TestConcurrent.class.getName()));

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClass =
	"com.sun.sgs.impl.service.data.store.DataStoreImpl";

    /** The number of operations to perform. */
    private static final int operations =
	Integer.getInteger("test.operations", 10000);

    /** The maximum number of objects to allocate. */
    private static final int objects =
	Integer.getInteger("test.objects", 1000);

    /** The number of concurrent threads. */
    private static final int threads = Integer.getInteger("test.threads", 2);

    /** Print test parameters. */
    static {
	System.err.println("Parameters: test.operations=" + operations +
			   ", test.objects=" + objects +
			   ", test.threads=" + threads);
    }

    /** The transaction proxy. */
    final DummyTransactionProxy txnProxy = new DummyTransactionProxy();

    /** Set when the test passes. */
    private boolean passed;

    /** A per-test database directory. */
    private String directory = System.getProperty("test.directory");

    /**
     * The exception thrown by one of the threads, or null if none of the
     * threads have failed.
     */
    private Throwable failure;

    /** The total number of aborts seen by the various threads. */
    private int aborts;

    /** The number of threads that are done. */
    private int done;

    /** The service to test. */
    private DataServiceImpl service;

    /** Creates the test. */
    public TestConcurrent(String name) {
	super(name);
    }

    /** Prints the name of the test case. */
    protected void setUp() {
	System.err.println("Testcase: " + getName());
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
	passed = true;
    }

    /** Deletes the directory if the test passes. */
    protected void tearDown() throws Exception {
	if (service != null) {
	    try {
		service.shutdown();
	    } catch (RuntimeException e) {
		if (passed) {
		    throw e;
		} else {
		    e.printStackTrace();
		}
	    }
	}
	if (passed) {
	    deleteDirectory(directory);
	}
    }

    /* -- Tests -- */

    public void testConcurrent() throws Throwable {
	Properties props = createProperties(
	    DataStoreImplClass + ".directory", createDirectory(),
	    StandardProperties.APP_NAME, "TestConcurrent");
	DummyComponentRegistry componentRegistry =
	    new DummyComponentRegistry();
	service = new DataServiceImpl(props, componentRegistry);
	DummyTransaction txn = new DummyTransaction();
	txnProxy.setCurrentTransaction(txn);
	service.configure(componentRegistry, txnProxy);
	componentRegistry.setComponent(DataManager.class, service);
	componentRegistry.registerAppContext();
	txn.commit();
	long start = System.currentTimeMillis();
	for (int i = 0; i < threads; i++) {
	    new OperationThread(i, service, txnProxy);
	}
	while (true) {
	    synchronized (this) {
		if (failure != null || done >= threads) {
		    break;
		}
		try {
		    wait();
		} catch (InterruptedException e) {
		    failure = e;
		    break;
		}
	    }
	}
	long stop = System.currentTimeMillis();
	if (failure != null) {
	    throw failure;
	}
	long ms = stop - start;
	double s = (stop - start) / 1000.0d;
	System.err.println(
	    "Time: " + ms + " ms\n" +
	    "Aborts: " + aborts + "\n" +
	    "Ops per second: " + Math.round((threads * operations) / s));
    }

    /* -- Other methods and classes -- */

    /**
     * Notes that a thread has completed successfully, and records the number
     * of aborts that occurred in the thread.
     */
    synchronized void threadDone(int aborts) {
	done++;
	this.aborts += aborts;
	notifyAll();
    }

    /** Notes that a thread has failed with the specified exception. */
    synchronized void threadFailed(Throwable failure) {
	this.failure = failure;
	notifyAll();
    }

    /** Performs random operations in a separate thread. */
    class OperationThread extends Thread {
	private final DataService service;
	private final DummyTransactionProxy txnProxy;
	private final Random random = new Random();
	private DummyTransaction txn;
	private int aborts;

	OperationThread(
	    int id, DataService service, DummyTransactionProxy txnProxy)
	{
	    super("OperationThread" + id);
	    this.service = service;
	    this.txnProxy = txnProxy;
	    start();
	}

	public void run() {
	    try {
		createTxn();
		for (int i = 0; i < operations; i++) {
		    if (i % 1000 == 0) {
			System.err.println(this + ": Operation " + i);
		    }
		    while (true) {
			try {
			    op(i);
			    break;
			} catch (TransactionAbortedException e) {
			    if (logger.isLoggable(Level.FINE)) {
				logger.log(Level.FINE, "{0}: {1}", this, e);
			    }
			    aborts++;
			    createTxn();
			}
		    }
		}
		txn.abort();
		threadDone(aborts);
	    } catch (Throwable t) {
		try {
		    txn.abort();
		} catch (RuntimeException e) {
		}
		threadFailed(t);
	    }
	}

	private void op(int i) throws Exception {
	    if (random.nextInt(10) == 0) {
		DummyTransaction t = txn;
		txn = null;
		t.commit();
		createTxn();
	    }
	    String name = "obj-" + (1 + random.nextInt(objects * threads));
	    switch (random.nextInt(6)) {
	    case 0:
		try {
		    service.getBinding(name, Object.class);
		} catch (NameNotBoundException e) {
		} catch (ObjectNotFoundException e) {
		}
		break;
	    case 1:
		service.setBinding(name, new DummyManagedObject());
		break;
	    case 2:
		try {
		    service.removeBinding(name);
		} catch (NameNotBoundException e) {
		}
		break;
	    case 3:
		/* Add 3 so we can include null, obj-0, and obj-N+1 */
		int r = random.nextInt(objects + 3);
		name = (r == 0) ? null : "obj-" + (r - 1);
		service.nextBoundName(name);
		break;
	    case 4:
		try {
		    service.removeObject(
			service.getBinding(name, ManagedObject.class));
		} catch (NameNotBoundException e) {
		} catch (ObjectNotFoundException e) {
		}
		break;
	    case 5:
		try {
		    service.markForUpdate(
			service.getBinding(name, ManagedObject.class));
		} catch (NameNotBoundException e) {
		} catch (ObjectNotFoundException e) {
		}
		break;
	    default:
		throw new AssertionError();
	    }
	}

	private void createTxn() {
	    txn = new DummyTransaction();
	    txnProxy.setCurrentTransaction(txn);
	}
    }

    /** Creates a per-test directory. */
    private String createDirectory() throws IOException {
	if (directory != null) {
	    new File(directory).mkdir();
	} else {
	    File dir = File.createTempFile(getName(), "dbdir");
	    if (!dir.delete()) {
		throw new RuntimeException("Problem deleting file: " + dir);
	    }
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Failed to create directory: " + dir);
	    }
	    directory = dir.getPath();
	}
	return directory;
    }

    /** Deletes the specified directory, if it exists. */
    private static void deleteDirectory(String directory) {
	File dir = new File(directory);
	if (dir.exists()) {
	    for (File f : dir.listFiles()) {
		if (!f.delete()) {
		    throw new RuntimeException("Failed to delete file: " + f);
		}
	    }
	    if (!dir.delete()) {
		throw new RuntimeException(
		    "Failed to delete directory: " + dir);
	    }
	}
    }

    /** Creates a property list with the specified keys and values. */
    private static Properties createProperties(String... args) {
	Properties props = new Properties();
	if (args.length % 2 != 0) {
	    throw new RuntimeException("Odd number of arguments");
	}
	for (int i = 0; i < args.length; i += 2) {
	    props.setProperty(args[i], args[i + 1]);
	}
	/* Include system properties and allow them to override */
	props.putAll(System.getProperties());
	return props;
    }
}
