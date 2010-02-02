/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.util;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.kernel.EmptyKernelAppContext;
import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.TaskOwner;
import java.lang.Math;

/** Provides a simple implementation of TaskOwner, for testing. */
public class DummyTaskOwner implements TaskOwner {

    /** The identity. */
    private final Identity identity = new DummyIdentity();

    /** The kernel application context. */
    private final KernelAppContext kernelAppContext =
        new EmptyKernelAppContext("dummyApp-" + Math.random());

    /** Creates an instance of this class. */
    public DummyTaskOwner() { }

    /* -- Implement TaskOwner -- */

    public KernelAppContext getContext() {
	return kernelAppContext;
    }

    public Identity getIdentity() {
	return identity;
    }
}
