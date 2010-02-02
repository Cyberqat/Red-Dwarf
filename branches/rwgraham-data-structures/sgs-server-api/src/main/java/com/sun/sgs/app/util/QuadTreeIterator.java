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

package com.sun.sgs.app.util;

import java.util.Iterator;

/**
 * A simple abstract class which defines some common functionality
 * for all regions permitted in the quadtree. 
 */
public interface QuadTreeIterator<T> extends Iterator<T> {
	
	/**
	 * Returns the x-coordinate of the element currently pointed
	 * to by the iterator
	 * @return the x-coordinate of the element currently pointed
	 * to by the iterator, or {@code NaN} if there is no element
	 * currently pointed to by the iterator
	 */
	double currentX();
	
	/**
	 * Returns the y-coordinate of the element currently pointed
	 * to by the iterator
	 * @return the y-coordinate of the element currently pointed
	 * to by the iterator, or {@code NaN} if there is no element
	 * currently pointed to by the iterator
	 */
	double currentY();
	
	/**
	 * Advances the iterator without returning the next element in the
	 * sequence. Instead, {@code true} is returned if an
	 * {@code ObjectNotFoundException} was not issued, and {@code false}
	 * in the event that the referenced element no longer exists. If the
	 * element is missing, the iterator does not remove it from the tree.
	 * If {@code false} is returned, the element can still be removed by
	 * calling the {@code remove()} method.
	 * 
	 * @return {@code true} if the reference to the element exists, and
	 * {@code false} if the reference to the element does not exist (which
	 * would cause an {@code ObjectNotFoundException} to be thrown if
	 * {@code next()} had been called instead
	 */
	boolean nextNoReturn();
	
	/**
	 * Returns the element the cursor is pointing to. This should be used
	 * in conjunction with {@code nextWithoutReturningElement()} in order
	 * to retrieve the element if it exists. If this method is called
	 * after {@code next()}, the same element will be returned. Multiple
	 * calls to this method will return the same element.
	 * 
	 * @return the element that the cursor is pointing to, or {@code null}
	 * if there is no reference to the element
	 */
	T current();
}