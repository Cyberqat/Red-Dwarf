/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.util.LoggerWrapper;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.service.NonDurableTransactionParticipant;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Utility class for scheduling non-durable, transactional tasks to be
 * run in succession.  Tasks are added to the queue from a
 * non-transactional context.  This implementation is thread-safe.
 */
public class NonDurableTaskQueue implements NonDurableTransactionParticipant {

    /** The name of this class. */
    private static final String CLASSNAME =
	NonDurableTaskQueue.class.getName();

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));
    
    /** Stores the transaction for the current thread. */
    private static final ThreadLocal<Transaction> currentTransaction =
	new ThreadLocal<Transaction>();
    
    /** The transaction proxy, or null if configure has not been called. */    
    private TransactionProxy txnProxy;
    
    /** The task scheduler. */
    private final NonDurableTaskScheduler nonDurableTaskScheduler;

    /** The identity for tasks. */
    private final Identity identity;

    /** The lock for accessing state. */
    private final Object lock = new Object();

    /** The queue of tasks to run. */
    private final LinkedList<KernelRunnable> tasks =
	new LinkedList<KernelRunnable>();

    /** The task to process the head of the queue. */
    private ProcessQueueTask processQueueTask = null;

    /**
     * Constructs a {@code NonDurableTaskQueue} with the given {@code
     * scheduler} and {@code identity}.
     *
     * @param	proxy the transaction proxy
     * @param	scheduler a {@code NonDurableTaskScheduler}
     * @param	identity an identity
     */
    public NonDurableTaskQueue(
	TransactionProxy proxy,
	NonDurableTaskScheduler scheduler,
	Identity identity)		       
    {
	if (proxy == null || scheduler == null || identity == null) {
	    throw new NullPointerException("null argument");
	}
	this.txnProxy = proxy;
	this.nonDurableTaskScheduler = scheduler;
	this.identity = identity;
    }

    /**
     * Adds to this task queue a {@code task} that is scheduled using
     * the {@code NonDurableTaskScheduler} and {@code identity}
     * specified during construction.  The given {@code task} will be
     * run after all preceeding tasks either successfully complete, or fail
     * with a non-retryable exception.
     *
     * @param	task a task
     */
    public void addTask(KernelRunnable task) {
	if (task == null) {
	    throw new NullPointerException("null task");
	}
	synchronized (lock) {
	    tasks.add(task);
	    if (processQueueTask == null) {
		processQueueTask = new ProcessQueueTask();
		nonDurableTaskScheduler.
		    scheduleTask(processQueueTask, identity);
	    }
	}
    }

    /* -- Implement NonDurableTransactionParticipant -- */
       
    /** {@inheritDoc} */
    public boolean prepare(Transaction txn) throws Exception {
	try {
	    checkTransaction(txn);
	    boolean readOnly = false;
	    if (logger.isLoggable(Level.FINE)) {
		logger.log(Level.FINER, "prepare txn:{0} returns {1}",
			   txn, readOnly);
	    }
	    return readOnly;
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINER, e, "prepare txn:{0} throws", txn);
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void commit(Transaction txn) {
	try {
	    checkTransaction(txn);
	    removeTask();
	    currentTransaction.set(null);
	    logger.log(Level.FINER, "commit txn:{0} returns", txn);
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINER, e, "commit txn:{0} throws", txn);
	    throw e;
	}
    }

    /** {@inheritDoc} */
    public void prepareAndCommit(Transaction txn) throws Exception {
        if (!prepare(txn)) {
            commit(txn);
        }
    }

    /** {@inheritDoc} */
    public void abort(Transaction txn) {
	try {
	    checkTransaction(txn);

	    // FIXME: This is a hack to find out why the transaction
	    // aborted.  It should be replaced by a way to inspect a
	    // transaction's state. - ann (3/1/07)
	    try {
		txn.join(this);
		logger.log(Level.SEVERE, "joining aborted txn:{0} succeeded", txn);
	    } catch (Exception e) {
		if (! isRetryable(e)) {
		    removeTask();
		}
	    }
	    currentTransaction.set(null);
	    logger.log(Level.FINER, "abort txn:{0} returns", txn);
	    
	} catch (RuntimeException e) {
	    logger.logThrow(Level.FINER, e, "abort txn:{0} throws", txn);
	    throw e;
	}
    }

    /**
     * Task for processing the head of the task queue.
     *
     * <p>This {@code ProcessQueueTask} joins the current transaction
     * and invokes the {@code run} method on the task at the head of
     * the queue.
     */
    private class ProcessQueueTask implements KernelRunnable {

	/** {@inheritDoc} */
	public void run() throws Exception {

	    KernelRunnable task = null;
	    try {
		synchronized (lock) {
		    task = tasks.peek();
		}
		if (task == null) {
		    logger.log(Level.WARNING, "task queue unexpectedly empty");
		    return;
		}
		joinTransaction();
		logger.log(Level.FINER, "running task:{0}", task);
		task.run();
		
	    } catch (Exception e) {
		logger.logThrow(Level.FINER, e, "run task:{0} throws", task);
		throw e;
	    }
	}
    }

    /**
     * Removes the processed task from the head of the queue, and, if
     * after task removal the task queue is non-empty, then
     * reschedules another {@code ProcessQueueTask} to process the
     * next task at the head of the queue.
     */
    private void removeTask() {
	synchronized (lock) {
	    // FIXME: The task queue should not be empty when this
	    // method is called, but comment out assertion below and guard
	    // against empty queue just in case the abort case doesn't
	    // detect a retryable exception.  ann (3/1/07)
	    // assert ! tasks.isEmpty();
	    
	    if (!tasks.isEmpty()) {
		KernelRunnable task = tasks.remove();
		logger.log(Level.FINER, "removed task:{0}", task);
	    } else {
		logger.log(Level.WARNING, "task queue unexpectedly empty");
	    }
	    if (tasks.isEmpty()) {
		processQueueTask = null;
	    } else {
		nonDurableTaskScheduler.scheduleTask(
		    processQueueTask, identity);
	    }
	}
    }
    /**
     * Returns {@code true} if the given {@code Throwable} is a
     * "retryable" exception, meaning that it implements {@code
     * ExceptionRetryStatus}, and invoking its {@link
     * ExceptionRetryStatus#shouldRetry shouldRetry} method returns
     * {@code true}.
     *
     * @param	t   a throwable
     */
    private static boolean isRetryable(Throwable t) {
	return
	    t instanceof ExceptionRetryStatus &&
	    ((ExceptionRetryStatus) t).shouldRetry();
    }

    /**
     * Checks the specified transaction, throwing {@code
     * IllegalStateException} if the current context is {@code null}
     * or if the specified transaction is not equal to the transaction
     * in the current context.  If the specified transaction does not
     * match the current context's transaction, then sets the current
     * context to (@code null}.
     */
    private void checkTransaction(Transaction txn) {
        if (txn == null) {
            throw new NullPointerException("null transaction");
        }
        Transaction currTxn = currentTransaction.get();
        if (currTxn == null) {
            throw new IllegalStateException("null current transaction");
        }
        if (!txn.equals(currTxn)) {
            currentTransaction.set(null);
            throw new IllegalStateException(
                "Wrong transaction: Expected " + currTxn + ", found " + txn);
        }
    }
    
   /**
     * Joins the current transaction if not already joined, throwing a
     * {@code TransactionNotActiveException} if there is no current
     * transaction, and throwing {@code IllegalStateException} if
     * there is a problem with the state of the transaction.
     */
    private void joinTransaction() {
	Transaction txn;
	synchronized (lock) {
	    txn = txnProxy.getCurrentTransaction();
	}
	if (txn == null) {
	    throw new TransactionNotActiveException(
		"No transaction is active");
	}
	Transaction currTxn = currentTransaction.get();
	if (currTxn == null) {
	    if (logger.isLoggable(Level.FINER)) {
		logger.log(Level.FINER, "join txn:{0}", txn);
	    }
	    txn.join(this);
	    currentTransaction.set(txn);

	} else if (!txn.equals(currTxn)) {
	    currentTransaction.set(null);
	    throw new IllegalStateException(
		"Wrong transaction: Expected " + currTxn +
		", found " + txn);
	}
    }
}
