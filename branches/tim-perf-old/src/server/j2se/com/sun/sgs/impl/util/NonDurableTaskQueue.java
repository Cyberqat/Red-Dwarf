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

package com.sun.sgs.impl.util;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Utility class for scheduling non-durable, transactional tasks to be
 * run in succession.  Tasks are added to the queue from a
 * non-transactional context.  This implementation is thread-safe.
 */
public class NonDurableTaskQueue {

    /** The name of this class. */
    private static final String CLASSNAME =
	NonDurableTaskQueue.class.getName();

    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(CLASSNAME));
    
    /** The transaction proxy. */
    private final TransactionProxy txnProxy;
    
    /** The transaction context factory. */
    private final TransactionContextFactory<Context> contextFactory;
    
    /** The task scheduler. */
    private final TaskScheduler taskScheduler;

    /** The task owner. */
    private final Identity taskOwner;

    /** The lock for accessing state. */
    private final Object lock = new Object();

    /** The queue of tasks to run. */
    private final LinkedList<KernelRunnable> tasks =
	new LinkedList<KernelRunnable>();

    /** The task to process the head of the queue. */
    private ProcessQueueTask processQueueTask = null;

    /**
     * Constructs an instance with the given transaction {@code
     * proxy}, {@code nonDurableTaskScheduler}, and {@code identity}.
     *
     * @param	proxy the transaction proxy
     * @param	nonDurableTaskScheduler a {@code NonDurableTaskScheduler}
     * @param	identity an identity, or {@code null}
     */
    public NonDurableTaskQueue(
	TransactionProxy proxy,
	NonDurableTaskScheduler nonDurableTaskScheduler,
	Identity identity)		       
    {
	this(proxy,
	     nonDurableTaskScheduler.getTaskScheduler(),
	     identity);
    }

    /**
     * Constructs an instance with the given transaction {@code
     * proxy}, {@code taskScheduler}, and {@code taskOwner}.
     *
     * @param	proxy the transaction proxy
     * @param	taskScheduler a task scheduler
     * @param	taskOwner an identity, to own the tasks
     */
    public NonDurableTaskQueue(
	TransactionProxy proxy,
	TaskScheduler taskScheduler,
	Identity taskOwner)		       
    {
	if (proxy == null || taskScheduler == null || taskOwner == null) {
	    throw new NullPointerException("null argument");
	}
	this.txnProxy = proxy;
	this.contextFactory = new ContextFactory(txnProxy);
	this.taskScheduler = taskScheduler;
	this.taskOwner = taskOwner;
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
		taskScheduler.scheduleTask(
		    new TransactionRunner(processQueueTask),
		    taskOwner);
	    }
	}
    }

    /* -- Other classes and methods -- */
       
    private class ContextFactory extends TransactionContextFactory<Context> {
	ContextFactory(TransactionProxy txnProxy) {
	    super(txnProxy, CLASSNAME);
	}
	
	public Context createContext(Transaction txn) {
	    return new Context(txn);
	}
    }

    /**
     * Stores information relating to a specific transaction
     * processing the head of the task queue.
     */
    private final class Context extends TransactionContext {

	/** The task being processed. */
	private KernelRunnable task;

	/**
	 * Constructs a context with the specified transaction.
	 */
	Context(Transaction txn) {
	    super(txn);
	}

	/**
	 * Sets this context's task to the given {@code task}.
	 */
	private void setTask(KernelRunnable task) {
	    this.task = task;
	}

	/** {@inheritDoc} */
	public void commit() {
	    if (task != null) {
		removeTask(task);
	    }
	    isCommitted = true;
	}

	/** {@inheritDoc} */
	public void abort(boolean isRetryable) {
	    if (! isRetryable) {
		if (task != null) {
		    removeTask(task);
		}
	    }
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
        public String getBaseTaskType() {
            KernelRunnable nextTask = null;
            synchronized (lock) {
                nextTask = tasks.peek();
            }
            return nextTask != null ?
                nextTask.getBaseTaskType() :
                ProcessQueueTask.class.getName();
        }

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
		Context context = contextFactory.joinTransaction();
		context.setTask(task);
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
     * next task at the head of the queue.  The task at the head of
     * the queue should match the specified {@code processedTask}.
     */
    private void removeTask(KernelRunnable processedTask) {
	synchronized (lock) {
	    // FIXME: The task queue should not be empty when this
	    // method is called, but comment out assertion below and guard
	    // against empty queue just in case the abort case doesn't
	    // detect a retryable exception.  ann (3/1/07)
	    // assert ! tasks.isEmpty();
	    
	    if (!tasks.isEmpty()) {
		if (tasks.peek() == processedTask) {
		    KernelRunnable task = tasks.remove();
		    logger.log(Level.FINER, "removed task:{0}", task);
		} else {
		    logger.log(
			Level.SEVERE,
			"unable to remove task:{0}, expected task:{1}",
			tasks.peek(), processedTask);
		}
	    } else {
		logger.log(Level.WARNING, "task queue unexpectedly empty");
	    }
	    
	    if (tasks.isEmpty()) {
		processQueueTask = null;
	    } else {
		taskScheduler.scheduleTask(
		    new TransactionRunner(processQueueTask),
		    taskOwner);
	    }
	}
    }
}
