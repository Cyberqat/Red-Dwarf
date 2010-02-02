/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.nodemap.coordinator.affinity.user;

import com.sun.sgs.app.AffinityGroupManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.impl.service.session.ClientSessionImpl;

/**
 * A manager through which hints can be provided to the underlying affinity
 * group mechanism.
 *
 * TODO... Could this be generic, and just change the backing service?
 */
public final class UserGroupManager implements AffinityGroupManager {

    private final UserGroupService service;

    public UserGroupManager(UserGroupService service) {
        this.service = service;
    }

    @Override
    public long createGroup() {
        return service.createGroup();
    }

    @Override
    public void associate(ClientSession session, long groupId) {
        service.associate(ClientSessionImpl.getIdentity(session), groupId);
    }
}
