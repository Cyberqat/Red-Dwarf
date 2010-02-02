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

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.app.TaskRejectedException;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.kernel.TaskHandler;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskReservation;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;

import com.sun.sgs.service.TransactionRunner;

import java.beans.PropertyChangeEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;

import java.util.concurrent.atomic.AtomicInteger;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is the root scheduler class that is used by the rest of the system
 * to schedule tasks. It handles incoming tasks and profiling data, and
 * passes these on to an <code>ApplicationScheduler</code>. Essentially, this 
 * root scheduler handles basic configuration and marshalling, but leaves all
 * the real work to its child scheduler.
 */
public class MasterTaskScheduler implements ProfileListener, TaskScheduler {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(MasterTaskScheduler.
                                           class.getName()));

    // the scheduler used for this system
    private final ApplicationScheduler scheduler;

    // the runner used to execute tasks
    private final TaskExecutor taskExecutor;

    // the default scheduler
    private static final String DEFAULT_APPLICATION_SCHEDULER =
            "com.sun.sgs.impl.kernel.schedule.FIFOApplicationScheduler";
    
    /**
     * The property used to define the default number of initial consumer
     * threads.
     */
    public static final String INITIAL_CONSUMER_THREADS_PROPERTY =
        "com.sun.sgs.impl.kernel.schedule.InitialConsumerThreads";

    // the default number of initial consumer threads
    private static final String DEFAULT_INITIAL_CONSUMER_THREADS = "4";

    // the actual number of threads we're currently using
    private AtomicInteger threadCount;

    // the default priority for tasks
    private static Priority defaultPriority = Priority.getDefaultPriority();

    // the collector used for reporting profiling data
    private final ProfileCollector profileCollector;

    /**
     * Creates an instance of <code>MasterTaskScheduler</code>.
     *
     * @param properties the system properties
     * @param resourceCoordinator the system's <code>ResourceCoordinator</code>
     * @param taskHandler the system's <code>TaskHandler</code>
     * @param profileCollector the collector used for profiling data, or
     *                         <code>null</code> if profiling is disabled
     *
     * @throws Exception if there are any failures during configuration
     */
    public MasterTaskScheduler(Properties properties,
                               ResourceCoordinator resourceCoordinator,
                               TaskHandler taskHandler,
                               ProfileCollector profileCollector)
        throws Exception
    {
        logger.log(Level.CONFIG, "Creating the Master Task Scheduler");

        if (properties == null)
            throw new NullPointerException("Properties cannot be null");
        if (resourceCoordinator == null)
            throw new NullPointerException("Resource Coord cannot be null");
        if (taskHandler == null)
            throw new NullPointerException("Task Handler cannot be null");

        // try to get the scheduler, falling back on the default
        // if the specified one isn't available, or if none was specified
        
        String schedulerName =
            properties.getProperty(ApplicationScheduler.
                                   APPLICATION_SCHEDULER_PROPERTY,
                                   DEFAULT_APPLICATION_SCHEDULER);
        try {
            Class<?> schedulerClass = Class.forName(schedulerName);
            Constructor<?> schedulerCtor = 
                    schedulerClass.getConstructor(Properties.class);
            scheduler = 
                    (ApplicationScheduler) (schedulerCtor.newInstance(properties));
        } catch (InvocationTargetException e) {
            if (logger.isLoggable(Level.CONFIG)) 
 	        logger.logThrow(Level.CONFIG, e.getCause(), "Scheduler {0} " +
 	                        "failed to initialize", schedulerName);
            throw e;
 	} catch (Exception e) {
            if (logger.isLoggable(Level.CONFIG))
                logger.logThrow(Level.CONFIG, e, "Scheduler {0} unavailable",
                                schedulerName);
            throw e;
        }
        
        int startingThreads =
            Integer.parseInt(properties.
                    getProperty(INITIAL_CONSUMER_THREADS_PROPERTY,
                                DEFAULT_INITIAL_CONSUMER_THREADS));
        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "Using {0} initial consumer threads",
                       startingThreads);
        threadCount = new AtomicInteger(startingThreads);

        this.taskExecutor =
            new TaskExecutor(taskHandler, scheduler, profileCollector);
        this.profileCollector = profileCollector;

        // create the initial consuming threads
        for (int i = 0; i < startingThreads; i++) {
            resourceCoordinator.
                startTask(new MasterTaskConsumer(this, scheduler,
                                                 taskExecutor),
                          null);
            if (profileCollector != null)
                profileCollector.notifyThreadAdded();
        }
    }

    /**
     * Notifies the scheduler that a thread has been interrupted and is
     * finishing its work.
     */
    void notifyThreadLeaving() {
        // NOTE: we're not yet trying to adapt the number of threads being
        // used, so we assume that threads are only lost when the system
        // wants to shutdown...in practice, this should look at some
        // threshold and see if another consumer needs to be created
        if (threadCount.decrementAndGet() == 0) {
            logger.log(Level.CONFIG, "No more threads are consuming tasks");
            if (profileCollector != null)
                profileCollector.notifyThreadRemoved();
            shutdown();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void propertyChange(PropertyChangeEvent event) {
        // ignored but see comment in notifyThreadLeaving
    }

    /**
     * {@inheritDoc}
     */
    public void report(ProfileReport profileReport) {
        // see comment in notifyThreadLeaving
    }

    /**
     * {@inheritDoc}
     */
    public void shutdown() {
        scheduler.shutdown();
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(KernelRunnable task, Identity owner) {
        ScheduledTask t = new ScheduledTask(task, owner, defaultPriority,
                                            System.currentTimeMillis());
        return scheduler.reserveTask(t);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(KernelRunnable task, Identity owner,
                                       Priority priority) {
        ScheduledTask t = new ScheduledTask(task, owner, priority,
                                            System.currentTimeMillis());
        return scheduler.reserveTask(t);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTask(KernelRunnable task, Identity owner,
                                       long startTime) {
        ScheduledTask t = new ScheduledTask(task, owner, defaultPriority,
                                            startTime);
        return scheduler.reserveTask(t);
    }

    /**
     * {@inheritDoc}
     */
    public TaskReservation reserveTasks(Collection<? extends KernelRunnable>
                                        tasks, Identity owner) {
        if (tasks == null)
            throw new NullPointerException("Collection cannot be null");

        HashSet<TaskReservation> reservations = new HashSet<TaskReservation>();
        try {
            // make sure that all the reservations can be made...
            for (KernelRunnable task : tasks) {
                ScheduledTask t =
                    new ScheduledTask(task, owner, defaultPriority,
                                      System.currentTimeMillis());
                reservations.add(scheduler.reserveTask(t));
            } 
        } catch (TaskRejectedException tre) {
            // ...and if any fails, cancel all the reservations that were
            // made successfully
            for (TaskReservation reservation : reservations)
                reservation.cancel();
            throw tre;
        }

        return new GroupTaskReservation(reservations);
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, Identity owner) {
        scheduler.
            addTask(new ScheduledTask(task, owner, defaultPriority,
                                      System.currentTimeMillis()));
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, Identity owner,
                             Priority priority) {
        scheduler.
            addTask(new ScheduledTask(task, owner, priority,
                                      System.currentTimeMillis()));
    }

    /**
     * {@inheritDoc}
     */
    public void scheduleTask(KernelRunnable task, Identity owner,
                             long startTime) {
        scheduler.
            addTask(new ScheduledTask(task, owner, defaultPriority,
                                      startTime));
    }

    /**
     * {@inheritDoc}
     */
    public RecurringTaskHandle scheduleRecurringTask(KernelRunnable task,
                                                     Identity owner,
                                                     long startTime,
                                                     long period) {
        return scheduler.
            addRecurringTask(new ScheduledTask(task, owner, defaultPriority,
                                               startTime, period));
    }

    /**
     * {@inheritDoc}
     */
    public void runTask(KernelRunnable task, Identity owner, boolean retry)
        throws Exception
    {
        // check that we're not already running a direct task
        if (ThreadState.isSchedulerThread())
            throw new IllegalStateException("Cannot invoke runTask from a " +
                                            "task run through this scheduler");
        ThreadState.setAsSchedulerThread(true);

        // NOTE: since there isn't much fairness being guaranteed by the
        // system yet, this method simply runs the task directly. When
        // there is more use of policy and priority in the scheduler, and
        // when the resource coordinator is being used effectively to
        // manage resources like threads, then this method should be
        // re-visited to consider alternate policies
        try {
            ScheduledTask directTask =
                new ScheduledTask(task, owner, defaultPriority,
                                  System.currentTimeMillis());
            taskExecutor.runTask(directTask, retry, true);
        } finally {
            ThreadState.setAsSchedulerThread(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void runTransactionalTask(KernelRunnable task, Identity owner)
        throws Exception
    {
        // if we're not in the context of a scheduler thread, then we can
        // safely start the context of a new task, otherwise this should
        // just be run in place
        if (! ThreadState.isSchedulerThread())
            runTask(new TransactionRunner(task), owner, true);
        else
            TaskHandler.runTransactionally(task);
    }

    /**
     * Private inner class that represents a group of reservations. When a
     * reservation is made for a group, they are all accepted or none of them
     * is accepted. When used or cancelled, it also applies to all tasks.
     */
    private static class GroupTaskReservation implements TaskReservation {
        private HashSet<TaskReservation> reservations;
        private boolean finished = false;
        GroupTaskReservation(HashSet<TaskReservation> reservations) {
            this.reservations = reservations;
        }
        /** @{inheritDoc} */
        public void cancel() {
            if (finished)
                throw new IllegalStateException("cannot cancel reservation");
            for (TaskReservation reservation : reservations)
                reservation.cancel();
            finished = true;
        }
        /** @{inheritDoc} */
        public void use() {
            if (finished)
                throw new IllegalStateException("cannot use reservation");
            for (TaskReservation reservation : reservations)
                reservation.use();
            finished = true;
        }
    }

}
