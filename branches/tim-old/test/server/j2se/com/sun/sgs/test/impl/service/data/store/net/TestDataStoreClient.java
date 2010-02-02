/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.data.store.net;

import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.impl.service.data.store.net.DataStoreServerImpl;
import com.sun.sgs.test.impl.service.data.store.TestDataStoreImpl;
import java.util.Properties;

/** Test the DataStoreClient class. */
public class TestDataStoreClient extends TestDataStoreImpl {

    /**
     * The name of the host running the DataStoreServer, or null to create one
     * locally.
     */
    private static final String serverHost =
	System.getProperty("test.server.host");

    /** The network port for the DataStoreServer. */
    private static final int serverPort =
	Integer.getInteger("test.server.port", 44530);
    
    /** The name of the DataStoreClient class. */
    private static final String DataStoreClientClassName =
	DataStoreClient.class.getName();

    /** The name of the DataStoreServerImpl class. */
    private static final String DataStoreServerImplClassName =
	DataStoreServerImpl.class.getName();

    /** The server. */
    private static DataStoreServerImpl server;

    /** Creates an instance. */
    public TestDataStoreClient(String name) {
	super(name);
    }

    /** Shuts down the server if the test failed. */
    protected void tearDown() throws Exception {
	super.tearDown();
	try {
	    if (!passed && server != null) {
		new ShutdownAction() {
		    protected boolean shutdown() {
			return server.shutdown();
		    }
		}.waitForDone();
	    }
	} catch (RuntimeException e) {
	    if (passed) {
		throw e;
	    } else {
		e.printStackTrace();
	    }
	} finally {
	    if (!passed) {
		server = null;
	    }
	}
    }

    /** Adds client and server properties, starting the server if needed. */
    protected Properties getProperties() throws Exception {
	Properties props = super.getProperties();
	String host = serverHost;
	int port = serverPort;
	if (server == null && host == null) {
	    props.setProperty(DataStoreServerImplClassName + ".port", "0");
	    server = new DataStoreServerImpl(props);
	}
	if (host == null) {
	    host = "localhost";
	    port = server.getPort();
	}
	props.setProperty(DataStoreClientClassName + ".server.host", host);
	props.setProperty(DataStoreClientClassName + ".server.port",
			  String.valueOf(port));
	return props;
    }

    /** Create a DataStoreClient. */
    protected DataStore createDataStore(Properties props) throws Exception {
	return new DataStoreClient(props);
    }

    /* -- Tests -- */

    /* -- Skip tests that involve properties that don't apply -- */

    public void testConstructorNoDirectory() throws Exception {
	System.err.println("Skipping");
    }
    public void testConstructorNonexistentDirectory() throws Exception {
	System.err.println("Skipping");
    }
    public void testConstructorDirectoryIsFile() throws Exception {
	System.err.println("Skipping");

    }
    public void testConstructorDirectoryNotWritable() throws Exception {
	System.err.println("Skipping");
    }

    /* -- Test constructor -- */

    public void testConstructorBadAllocationBlockSize() throws Exception {
	props.setProperty(
	    DataStoreClientClassName + ".allocation.block.size", "gorp");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNegativeAllocationBlockSize() throws Exception {
	props.setProperty(
	    DataStoreClientClassName + ".allocation.block.size", "-3");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorBadPort() throws Exception {
	props.setProperty(DataStoreClientClassName + ".server.port", "gorp");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNegativePort() throws Exception {
	props.setProperty(DataStoreClientClassName + ".server.port", "-1");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorBigPort() throws Exception {
	props.setProperty(
	    DataStoreClientClassName + ".server.port", "70000");
	try {
	    createDataStore(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }
}
