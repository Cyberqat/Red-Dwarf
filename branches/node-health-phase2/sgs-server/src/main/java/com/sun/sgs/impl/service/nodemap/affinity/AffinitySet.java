/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.service.nodemap.affinity;

import com.sun.sgs.auth.Identity;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The affinity group information for a single node, in a format which
 * can be sent between nodes and the server.  These affinity groups are
 * data containers only.
 */
public class AffinitySet extends AbstractAffinityGroup implements Serializable {
    /** Serialization version. */
    private static final long serialVersionUID = 1L;
    
    /** The set of identities comprising the group. Note this needs
     *  to be declared a concrete class so we know it is serializable.
     */
    private final Set<Identity> identities;

    /**
     * Constructs a new affinity group with the given ID, generation number,
     * and an initial identity to include.
     * @param id the affinity group identity
     * @param generation the generation number of this group
     * @param identity the first identity in this affinity set
     */
    public AffinitySet(long id, long generation, Identity identity) {
        super(id, generation);
        identities = new HashSet<Identity>();
        identities.add(identity);
    }

    /**
     * Constructs a new affinity group with the given ID, generation number,
     * and a set of initial identities to include.
     * @param id the affinity group identity
     * @param generation the generation number of this group
     * @param identitySet the initial set of identities to include
     */
    public AffinitySet(long id, long generation, Set<Identity> identitySet) {
        super(id, generation);
        identities = new HashSet<Identity>(identitySet);
    }

    /** {@inheritDoc} */
    public synchronized Set<Identity> getIdentities() {
        return Collections.unmodifiableSet(identities);
    }
    
    /** {@inheritDoc} */
    public String toString() {
        return getClass().getName() + "[" + id +
               ", gen: " + generation +
               ", size: " + identities.size() + "]";
    }

    /**
     * Add the given identity to this affinity group.
     * @param id the identity to add
     */
    synchronized void addIdentity(Identity id) {
        identities.add(id);
        System.out.println("ADD TO " + id);
    }
}
