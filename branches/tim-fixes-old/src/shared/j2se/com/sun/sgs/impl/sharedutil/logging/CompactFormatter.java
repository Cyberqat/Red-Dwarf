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

package com.sun.sgs.impl.sharedutil.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * Defines a logging {@code Formatter} that provides a more compact, and
 * customizable, time format, and that by default does not include stack
 * traces. <p>
 *
 * This class recognizes the following {@link LogManager} configuration
 * properties:
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <b>{@value #TIME_FORMAT_PROPERTY}</b> <br>
 *	<i>Default:</i> {@value #DEFAULT_TIME_FORMAT}
 *
 * <dd style="padding-top: .5em">
 *	Specifies the format string that will be used in a call to {@link
 *	Formatter#format Formatter.format} to display the time of a logging
 *	call.  The default prints the time in the format {@code 2008-02-14
 *	11:52:59.679}. <p>
 *
 * <dt> <i>Property:</i> <b>{@value #PRINT_STACK_PROPERTY}</b> <br>
 *	<i>Default:</i> {@code false}
 *
 * <dd style="padding-top: .5em">
 *	Specifies whether to print stack traces when logging a thrown
 *	exception.
 *
 * </dl>
 */
public class CompactFormatter extends java.util.logging.Formatter {
    
    /** The default time format string. */
    public static final String DEFAULT_TIME_FORMAT = "%tF %1$tT.%1$tL";

    /** The logging property for specifying the time format. */
    public static final String TIME_FORMAT_PROPERTY =
	"com.sun.sgs.impl.sharedutil.logging.CompactFormatter.time.format";

    /**
     * The logging property for specifying whether to print stack traces when
     * logging a thrown exception.
     */
    public static final String PRINT_STACK_PROPERTY =
	"com.sun.sgs.impl.sharedutil.logging.CompactFormatter.print.stack";

    /** The time format. */
    private final String timeFormat;

    /** Whether to print stack traces. */
    private final boolean printStack;

    /** Creates an instance of this class. */
    public CompactFormatter() {
	LogManager logManager = LogManager.getLogManager();
	String value = logManager.getProperty(TIME_FORMAT_PROPERTY);
	timeFormat = (value != null) ? value : DEFAULT_TIME_FORMAT;
	value = logManager.getProperty(PRINT_STACK_PROPERTY);
	printStack = (value != null) && Boolean.parseBoolean(value);
    }

    /* -- Implement java.util.logging.Formatter -- */

    /** {@inheritDoc} */
    public String format(LogRecord record) {
	Formatter formatter = new Formatter();
	formatter.format(timeFormat + ": ", record.getMillis());
	if (record.getSourceClassName() != null) {	
	    formatter.format("%s", record.getSourceClassName());
	} else {
	    formatter.format("%s", record.getLoggerName());
	}
	if (record.getSourceMethodName() != null) {	
	    formatter.format(".%s", record.getSourceMethodName());
	}
	formatter.format("%n%s: %s%n",
			 record.getLevel().getLocalizedName(),
			 formatMessage(record));
	if (record.getThrown() != null) {
	    if (printStack) {
		try {
		    StringWriter sw = new StringWriter();
		    PrintWriter pw = new PrintWriter(sw);
		    record.getThrown().printStackTrace(pw);
		    pw.close();
		    formatter.format("%s%n", sw.toString());
		} catch (Exception ex) {
		}
	    } else {
		formatter.format("%s%n", record.getThrown());
	    }
	}
	return formatter.toString();
    }
}
