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

package com.sun.sgs.profile;

import com.sun.sgs.profile.ProfileCollector.ProfileLevel;

/**
 * This interface should be implemented by components that accept
 * profiling data associated with tasks that are running through the
 * scheduler.  Typically each consumer is matched with a
 * <code>ProfileProducer</code>.  Note that operations, counters and
 * samples are always handled in separate namespaces, so their
 * registrations will not collide.
 *
 * @see ProfileCounter
 * @see ProfileOperation
 * @see ProfileSample
 */
public interface ProfileConsumer {

    /**
     * Set the local profiling level for this consumer.  Setting the global
     * profiling level via 
     * {@link ProfileCollector#setDefaultProfileLevel(ProfileLevel)} will
     * override this value.
     * 
     * @param level the profiling level
     */
    void setProfileLevel(ProfileLevel level);
    
    /**
     * Get the local profiling level for this consumer. Defaults to the
     * value of {@link ProfileCollector#getDefaultProfileLevel()}.
     * 
     * @return the profiling level
     */
    ProfileLevel getProfileLevel();
    
    /**
     * Registers the named operation with this consumer, such that the
     * operation can be reported as part of a task's profile. Note
     * that registering the same name multiple times on the same
     * consumer will produce the same canonical instance of
     * <code>ProfileOperation</code>.  
     *
     * @param name the name of the operation
     * @param minLevel the minimum level of profiling that must be set to report
     *              this operation
     *
     * @return an instance of <code>ProfileOperation</code>
     */
    ProfileOperation registerOperation(String name, ProfileLevel minLevel);

    /**
     * Registers the named counter with this consumer, such that the
     * counter can be incremented during the run of a task. If this
     * counter is local to a task it means that each time a new task
     * runs, the counter is perceived as starting from zero for that
     * task. Note that registering the same name multiple times on the
     * same consumer <i>will</i> produce the same canonical instance of
     * {@code ProfileCounter}.
     *
     * @param name the name of the counter
     * @param taskLocal <code>true</code> if this counter is local to
     *        tasks, <code>false</code> otherwise
     * @param minLevel the minimum level of profiling that must be set to update
     *              this counter
     *
     * @return an instance of <code>ProfileCounter</code>
     */
    ProfileCounter registerCounter(String name, boolean taskLocal,
                                   ProfileLevel minLevel);

    /**
     * Registers the named source of data samples, and returns a
     * {@code ProfileSample} that can record each new datum during
     * the lifetime of a task or the application.  If the sample
     * counting should be local to a task, the current list of samples
     * will be empty upon each start of a task.  Note that registering
     * the same name multiple times on the same consumer <i>will</i>
     * produce the same canonical instance of {@code ProfileSample}.
     * <p>
     *  A negative value for {@code maxSamples} indicates an infinite
     *  number of samples.  Note that for non-task-local sample
     *  sources, this is a potential memory leak as the number of
     *  samples increases.  Once the limit of samples has been
     *  reached, older samples will be dropped to make room for the
     *  newest samples
     *
     * @param name a name or description of the sample type
     * @param taskLocal <code>true</code> if this counter is local to
     *        tasks, <code>false</code> otherwise
     * @param maxSamples the maximum number of samples to keep.
     * @param minLevel the minimum level of profiling that must be set to record
     *              this sample  
     *
     * @return a {@code ProfileSample} that collects the data
     */
    ProfileSample registerSampleSource(String name, boolean taskLocal,
				       long maxSamples, ProfileLevel minLevel);

    /**
     * Each profile consumer has a unique name.
     *
     * @return the name of this consumer
     */
    String getName();
}
