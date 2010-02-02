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

package com.sun.sgs.impl.auth;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.auth.IdentityAuthenticator;
import com.sun.sgs.auth.IdentityCredentials;

import com.sun.sgs.kernel.KernelAppContext;

import java.util.Properties;

import javax.security.auth.login.CredentialException;


/**
 * A very simple implementation of <code>IdentityAuthenticator</code> that
 * always authenticates a user based on <code>NamePasswordCredentials</code>.
 * This is primarily useful for testing applications.
 */
public class NullAuthenticator implements IdentityAuthenticator {

    // flag to make sure that the context only get assigned once
    private boolean contextAssigned;

    /**
     * Creates an instance of <code>NullAuthenticator</code>.
     *
     * @param properties the application's configuration properties
     */
    public NullAuthenticator(Properties properties) {
        contextAssigned = false;
    }

    /**
     * {@inheritDoc}
     */
    public String [] getSupportedCredentialTypes() {
        return new String [] { NamePasswordCredentials.TYPE_IDENTIFIER };
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void assignContext(KernelAppContext ctx) {
        if (contextAssigned)
            throw new IllegalStateException("Context was already assigned");
        contextAssigned = true;
    }

    /**
     * Always authenticates the given user unless the credentials are not
     * of type <code>NamePasswordCredentials</code>, in which case a
     * <code>CredentialException</code> is thrown.
     *
     * @param credentials the identity's credentials, which must be an
     *                    instance of <code>NamePasswordCredentials</code>
     *
     * @return the identity of the given user
     *
     * @throws CredentialException if the wrong type of credentials were
     *                             provided
     */
    public Identity authenticateIdentity(IdentityCredentials credentials)
        throws CredentialException
    {
        if (! (credentials instanceof NamePasswordCredentials))
            throw new CredentialException("unsupported credentials type");
        return new IdentityImpl(((NamePasswordCredentials)credentials).
                                getName());
    }

}
