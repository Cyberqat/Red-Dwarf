/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.kernel;


/**
 * This interface should be implemented by components that accept profiling
 * data associated with tasks that are running through the scheduler.
 * Typically each consumer is matched with a <code>ProfileProducer</code>.
 * Note that operations and counters are always handled in separate
 * namespaces, so their registrations will not collide.
 */
public interface ProfileConsumer {

    /**
     * Registers the named operation with this consumer, such that the
     * operation can be reported as part of a task's profile. Note that
     * registering the same name multiple times on the same consumer may
     * not produce the same instance of <code>ProfileOperation</code>.
     * That is, two registrations of the same name may still result in
     * operations that are reported distinctly.
     *
     * @param name the name of the operation
     *
     * @return an instance of <code>ProfileOperation</code>
     */
    public ProfileOperation registerOperation(String name);

    /**
     * Registers the named counter with this consumer, such that the
     * counter can be incremented during the run of a task. If this counter
     * is local to a task it means that each time a new task runs, the
     * counter is perceived as starting from zero for that task. Note that
     * registering the same name multiple times on the same consumer may
     * not produce the same instance of <code>ProfileCounter</code>. That
     * is, two registrations of the same name may still result in counters
     * that are managed differently. That said, it may be unsafe to report
     * increments for two counters with the same name in the same task, so
     * this behavior should be avoided.
     *
     * @param name the name of the counter
     * @param taskLocal <code>true</code> if this counter is local to tasks,
     *                  <code>false</code> otherwise
     *
     * @return an instance of <code>ProfileCounter</code>
     */
    public ProfileCounter registerCounter(String name, boolean taskLocal);

}
