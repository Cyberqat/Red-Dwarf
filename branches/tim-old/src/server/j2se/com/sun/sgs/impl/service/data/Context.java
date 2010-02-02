/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.util.MaybeRetryableTransactionNotActiveException;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.service.TransactionProxy;

/** Stores information for a specific transaction. */
final class Context {

    /** The data store. */
    final DataStore store;

    /**
     * The wrapped transaction, to be passed to the data store.  The wrapping
     * allows the data service to manage the data store's transaction
     * participation by itself, rather than revealing it to the transaction
     * coordinator.
     */
    final TxnTrampoline txn;

    /** The transaction proxy, for obtaining the current active transaction. */
    final TransactionProxy txnProxy;

    /**
     * The number of operations to skip between checks of the consistency of
     * the reference table.
     */
    private final int debugCheckInterval;

    /** Whether to detect modifications. */
    final boolean detectModifications;

    /**
     * The number of operations performed -- used to determine when to make
     * checks on the reference table.
     */
    private int count = 0;

    /**
     * The participant object for the data store, or null if the data store has
     * not yet joined the transaction.
     */
    TransactionParticipant storeParticipant;

    /**
     * Stores information about managed references.  This field is logically
     * part of the ManagedReferenceImpl class.
     */
    final ReferenceTable refs = new ReferenceTable();

    /** Creates an instance of this class. */
    Context(DataStore store,
	    Transaction txn,
	    TransactionProxy txnProxy,
	    int debugCheckInterval,
	    boolean detectModifications)
    {
	assert store != null && txn != null && txnProxy != null
	    : "Store, txn, or txnProxy is null";
	this.store = store;
	this.txn = new TxnTrampoline(txn);
	this.txnProxy = txnProxy;
	this.debugCheckInterval = debugCheckInterval;
	this.detectModifications = detectModifications;
    }

    /**
     * Defines a transaction that forwards all operations to another
     * transaction, except for join, which records the participant.  Pass
     * instances of this class to the DataStore in order to mediate its
     * participation in the transaction.
     */
    private final class TxnTrampoline implements Transaction {

	/** The original transaction. */
	private final Transaction originalTxn;

	/** Whether this transaction is inactive. */
	private boolean inactive;

	/** Creates an instance. */
	TxnTrampoline(Transaction originalTxn) {
	    this.originalTxn = originalTxn;
	}

	/* -- Implement Transaction -- */

	public byte[] getId() {
	    return originalTxn.getId();
	}

	public long getCreationTime() {
	    return originalTxn.getCreationTime();
	}

	public void join(TransactionParticipant participant) {
	    if (originalTxn.isAborted()) {
		throw new MaybeRetryableTransactionNotActiveException(
		    "Transaction is not active", originalTxn.getAbortCause());
	    } else if (inactive) {
		throw new IllegalStateException(
		    "Attempt to join a transaction that is not active");
	    } else if (participant == null) {
		throw new NullPointerException("Participant must not be null");
	    } else if (storeParticipant == null) {
		storeParticipant = participant;
	    } else if (!storeParticipant.equals(participant)) {
		throw new IllegalStateException(
		    "Attempt to join with different participant");
	    }
	}

	public void abort(Throwable cause) {
	    originalTxn.abort(cause);
	}

	public boolean isAborted() {
	    return originalTxn.isAborted();
	}

	public Throwable getAbortCause() {
	    return originalTxn.getAbortCause();
	}

	/* -- Object methods -- */

	public boolean equals(Object object) {
	    return object instanceof TxnTrampoline &&
		originalTxn.equals(((TxnTrampoline) object).originalTxn);
	}

	public int hashCode() {
	    return originalTxn.hashCode();
	}

	public String toString() {
	    return "TxnTrampoline[originalTxn:" + originalTxn + "]";
	}

	/* -- Other methods -- */

	/**
	 * Checks that the specified transaction equals the original one, and
	 * throws IllegalStateException if not.
	 */
	void check(Transaction otherTxn) {
	    if (!originalTxn.equals(otherTxn)) {
		throw new IllegalStateException(
		    "Wrong transaction: Expected " + originalTxn +
		    ", found " + otherTxn);
	    }
	}

	/** Notes that this transaction is inactive. */
	void setInactive() {
	    inactive = true;
	}
    }

    /* -- Methods for obtaining references -- */

    /** Obtains the reference associated with the specified object. */
    ManagedReferenceImpl getReference(ManagedObject object, boolean newArea) {
	return ManagedReferenceImpl.getReference(this, object, newArea);
    }

    /**
     * Finds the existing reference associated with the specified object,
     * returning null if it is not found.
     */
    ManagedReferenceImpl findReference(ManagedObject object) {
	return ManagedReferenceImpl.findReference(this, object);
    }

    /** Obtains the reference associated with the specified ID. */
    ManagedReferenceImpl getReference(long oid) {
	return ManagedReferenceImpl.getReference(this, oid);
    }

    /* -- Methods for bindings -- */

    /** Obtains the object associated with the specified internal name. */
    <T> T getBinding(String internalName, Class<T> type) {
	long id = store.getBinding(txn, internalName);
	assert id >= 0 : "Object ID must not be negative";
	return getReference(id).get(type);
    }

    /** Sets the object associated with the specified internal name. */
    void setBinding(String internalName, ManagedObject object) {
	store.setBinding(txn, internalName, getReference(object, false).oid);
    }

    /** Removes the object associated with the specified internal name. */
    void removeBinding(String internalName) {
	store.removeBinding(txn, internalName);
    }

    /** Returns the next bound name. */
    String nextBoundName(String internalName) {
	return store.nextBoundName(txn, internalName);
    }

    /* -- Methods for TransactionParticipant -- */

    boolean prepare() throws Exception {
	txn.setInactive();
	ManagedReferenceImpl.flushAll(this);
	if (storeParticipant == null) {
	    return true;
	} else {
	    return storeParticipant.prepare(txn);
	}
    }

    void commit() {
	txn.setInactive();
	if (storeParticipant != null) {
	    storeParticipant.commit(txn);
	}
    }

    void prepareAndCommit() throws Exception {
	txn.setInactive();
	ManagedReferenceImpl.flushAll(this);
	if (storeParticipant != null) {
	    storeParticipant.prepareAndCommit(txn);
	}
    }

    void abort() {
	txn.setInactive();
	if (storeParticipant != null) {
	    storeParticipant.abort(txn);
	}
    }

    /* -- Other methods -- */

    /**
     * Checks the consistency of the reference table if the operation count
     * equals the check interval.  Throws an IllegalStateException if it
     * encounters a problem.
     */
    void maybeCheckReferenceTable() {
	if (++count > debugCheckInterval) {
	    count = 0;
	    ManagedReferenceImpl.checkAllState(this);
	}
    }

    /**
     * Check that the specified transaction equals the original one, and throw
     * IllegalStateException if not.
     */
    void checkTxn(Transaction otherTxn) {
	txn.check(otherTxn);
    }
}
