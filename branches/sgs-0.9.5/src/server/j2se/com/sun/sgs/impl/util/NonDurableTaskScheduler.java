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
import com.sun.sgs.impl.kernel.TaskOwnerImpl;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionRunner;

/**
 * Utility class for scheduling non-durable tasks to run either inside
 * or outside of a transaction.
 */
public class NonDurableTaskScheduler {

    private final TaskOwner owner;
    private final TaskScheduler taskScheduler;
    private final TaskService taskService;

    /**
     * Constructs an instance of this class with the specified task
     * scheduler, task owner, and task service.
     *
     * @param taskScheduler a task scheduler
     * @param owner a task owner
     * @param taskService a task service
     */
    public NonDurableTaskScheduler(
	TaskScheduler taskScheduler,
        TaskOwner owner,
        TaskService taskService)
    {
	if (taskScheduler == null || owner == null || taskService == null) {
	    throw new NullPointerException("null argument");
	}
	this.taskScheduler = taskScheduler;
	this.owner = owner;
	this.taskService = taskService;
    }

    /**
     * Schedules a non-durable, transactional task using the task
     * scheduler and task owner specified during construction.
     *
     * @param task a task
     */
    public void scheduleTask(KernelRunnable task) {
	scheduleNonTransactionalTask(new TransactionRunner(task));
    }

    /**
     * Schedules a non-durable, transactional task using the task
     * scheduler specified during construction and a task owner
     * created from the given {@code Identity}.  If the given identity
     * is null, uses the task owner specified during construction.
     *
     * @param task a task
     * @param identity an identity
     */
    public void scheduleTask(KernelRunnable task, Identity identity) {
        scheduleNonTransactionalTask(new TransactionRunner(task), identity);
    }

    /**
     * Schedules a non-durable, non-transactional task using the task
     * scheduler and task owner specified during construction.
     *
     * @param task a task
     */
    public void scheduleNonTransactionalTask(KernelRunnable task) {
	taskScheduler.scheduleTask(task, owner);
    }

    /**
     * Schedules a non-durable, non-transactional task using the task
     * scheduler specified during construction and a task owner
     * created from the given {@code Identity}.  If the given identity
     * is null, uses the task owner specified during construction.
     *
     * @param task a task
     * @param identity an identity
     */
    public void scheduleNonTransactionalTask(KernelRunnable task,
            Identity identity)
    {
        if (identity == null) {
            scheduleNonTransactionalTask(task);
            return;
        }

        taskScheduler.scheduleTask(task,
                new TaskOwnerImpl(identity, owner.getContext()));
    }

    /**
     * Schedules a non-durable, transactional task using the task
     * service specified during construction.
     *
     * @param task a task
     */
    public void scheduleTaskOnCommit(KernelRunnable task) {
	taskService.scheduleNonDurableTask(new TransactionRunner(task));
    }
    
    /**
     * Schedules a non-durable, non-transactional task using the task
     * service specified during construction.
     *
     * @param task a task
     */
    public void scheduleNonTransactionalTaskOnCommit(KernelRunnable task) {
	taskService.scheduleNonDurableTask(task);
    }
}
