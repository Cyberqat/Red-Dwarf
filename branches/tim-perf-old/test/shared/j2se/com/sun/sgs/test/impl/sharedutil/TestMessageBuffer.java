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

package com.sun.sgs.test.impl.sharedutil;

import com.sun.sgs.impl.sharedutil.MessageBuffer;

import junit.framework.TestCase;

public class TestMessageBuffer extends TestCase {


    public void testConstructorNullArg() {
	try {
	    new MessageBuffer(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testPutByte() {
	int capacity = 10;
	MessageBuffer buf1 = new MessageBuffer(capacity);
	for (byte b = 0; b < capacity; b++) {
	    buf1.putByte(b);
	}
	MessageBuffer buf2 = new MessageBuffer(buf1.getBuffer());
	for (byte b = 0; b < capacity; b++) {
	    byte b2 = buf2.getByte();
	    if (b != b2) {
		fail("Mismatched bytes; b:"+ b + ", b2:" + b2);
	    }
	}
	System.err.println("bytes match");
    }

    public void testPutByteRewind() {
	int capacity = 10;
	MessageBuffer buf1 = new MessageBuffer(capacity);
	for (byte b = 0; b < capacity; b++) {
	    buf1.putByte(b);
	}
	buf1.rewind();
	for (byte b = 0; b < capacity; b++) {
	    byte b2 = buf1.getByte();
	    if (b != b2) {
		fail("Mismatched bytes; b:"+ b + ", b2:" + b2);
	    }
	}
	System.err.println("bytes match");
    }

    public void testPutByteOverflow() {
	MessageBuffer buf = new MessageBuffer(1);
	buf.putByte(0x01);
	try {
	    buf.putByte(0x02);
	    fail("Expected IndexOutOfBoundsException");
	} catch (IndexOutOfBoundsException e) {
	    System.err.println(e);
	}
    }

    public void testPutChar() {
	MessageBuffer buf = new MessageBuffer(2);
	buf.putChar('x');
	buf.rewind();
	char c = buf.getChar();
	if (c != 'x') {
	    fail("Expected char 'x', got " + c);
	}
    }

    public void testPutChars() {
	String s = "The quick brown fox jumps over the lazy dog.";
	MessageBuffer buf = new MessageBuffer(s.length() * 2);
	for (char c : s.toCharArray()) {
	    buf.putChar(c);
	    System.err.print(c);
	}
	System.err.println("\nlimit: " + buf.limit());
	buf.rewind();
	char[] charArray = new char[s.length()];
	for (int i = 0; i < s.length(); i++) {
	    charArray[i] = buf.getChar();
	    System.err.print(charArray[i]);
	}
	System.err.println();
	if (!(s.equals(new String(charArray)))) {
	    fail("strings don't match");
	}
    }

    public void testPutShort() {
	MessageBuffer buf = new MessageBuffer(2);
	short value1 = 53;
	buf.putShort(value1);
	buf.rewind();
	short value2 = buf.getShort();
	if (value1 != value2) {
	    fail("Expected short " + value1 + ", got " + value2);
	}
    }

    public void testPutShortSignedBytes() {
        MessageBuffer buf = new MessageBuffer(2);
        short value1 = 0x10ff;
        buf.putShort(value1);
        buf.rewind();
        short value2 = buf.getShort();
        if (value1 != value2) {
            fail("Expected short " + value1 + ", got " + value2);
        }
    }

    public void testGetUnsignedShort() {
        MessageBuffer buf = new MessageBuffer(2);
        int value1 = 64000;
        buf.putShort(value1);
        buf.rewind();
        int value2 = buf.getUnsignedShort();
        if (value1 != value2) {
            fail("Expected unsigned short " + value1 + ", got " + value2);
        }
        
        // test that signed getShort is different
        buf.rewind();
        value2 = buf.getShort();
        if (value1 == value2) {
            fail("Expected unequal, but got " + value2);
        }
        System.err.println("ushort " + value1 + " != " + value2);
    }

    public void testPutInt() {
        MessageBuffer buf = new MessageBuffer(4);
        int value1 = 0x01020304;
        buf.putInt(value1);
        buf.rewind();
        int value2 = buf.getInt();
        if (value1 != value2) {
            fail("Expected int " + value1 + ", got " + value2);
        }
    }

    public void testPutIntSignedBytes() {
        MessageBuffer buf = new MessageBuffer(4);
        int value1 = 0x01ff02fe;
        buf.putInt(value1);
        buf.rewind();
        int value2 = buf.getInt();
        if (value1 != value2) {
            fail("Expected int " + value1 + ", got " + value2);
        }
    }

    public void testPutLong() {
        MessageBuffer buf = new MessageBuffer(8);
        long value1 = 0x0102030405060708L;
        buf.putLong(value1);
        buf.rewind();
        long value2 = buf.getLong();
        if (value1 != value2) {
            fail("Expected long " + value1 + ", got " + value2);
        }
    }

    public void testPutLongSignedBytes() {
        MessageBuffer buf = new MessageBuffer(8);
        long value1 = 0x01f203f4f506f708L;
        buf.putLong(value1);
        buf.rewind();
        long value2 = buf.getLong();
        if (value1 != value2) {
            fail("Expected long " + value1 + ", got " + value2);
        }
    }
    
    public void testPutBytes() {
	int size = 100;
	byte[] bytes = new byte[size];
	for (int i = 0; i < bytes.length; i++) {
	    bytes[i] = (byte) i;
	}
	MessageBuffer buf = new MessageBuffer(size);
	buf.putBytes(bytes);
	buf.rewind();
	for (int i = 0; i < bytes.length; i++) {
	    if (buf.getByte() != bytes[i]) {
		fail("Expected byte " + bytes[i]);
	    }
	}
	buf.rewind();
	byte[] moreBytes = buf.getBytes(bytes.length);
	if (moreBytes.length != bytes.length) {
	    fail("Mismatched size; expected " + bytes.length +
		 ", got " + moreBytes.length);
	}
	for (int i = 0; i < bytes.length; i++) {
	    if (bytes[i] != moreBytes[i]) {
		fail("Expected byte " + bytes[i] + ", got " + moreBytes[i]);
	    }
	}
    }

    public void testPutString() {
	String s = "Supercalafragilisticexpalidocious";
	MessageBuffer buf = new MessageBuffer(MessageBuffer.getSize(s));
	buf.putString(s);
	buf.rewind();
	String newString = buf.getString();
	System.err.println("newString: " + newString);
	if (!s.equals(newString)) {
	    fail("Expected: " + s + ", got: " + newString);
	}
    }

    public void testPutStringAndInt() {
	String s = "zowie!";
	int x = 1024;
	MessageBuffer buf = new MessageBuffer(MessageBuffer.getSize(s) + 4);
	buf.putString(s);
	buf.putInt(x);
	buf.rewind();
	String newString = buf.getString();
	System.err.println("newString: " + newString);
	int newX = buf.getInt();
	System.err.println("newX: " + newX);
	if (!s.equals(newString)) {
	    fail("Expected string: " + s + ", got: " + newString);
	}
	if (x != newX) {
	    fail("Expected int: " + x + ", got: " + newX);
	}
	if (buf.position() != buf.limit()) {
	    fail("limit not equal to position; limit: " + buf.limit() +
		 ", position: " + buf.position());
	}
    }

    public void testPutStringGetUTF8() {
	String s = "The quick brown fox jumps over the lazy dog.";
	MessageBuffer buf = new MessageBuffer(MessageBuffer.getSize(s));
	buf.putString(s);
	buf.rewind();
	short utfLen = buf.getShort();
	byte[] utfBytes = buf.getBytes(utfLen);
	String newString;
	try {
	    newString = new String(utfBytes, "UTF-8");
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}
	System.err.println(newString);
	if (!s.equals(newString)) {
	    fail("Expected: " + s + ", got: " + newString);
	}
    }

    public void testPutUTF8GetString() {
	String s = "The quick brown fox jumps over the lazy dog.";
	byte[] utfBytes;
	try {
	    utfBytes = s.getBytes("UTF-8");
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}
	int utfLen = utfBytes.length;
	MessageBuffer buf = new MessageBuffer(2 + utfLen);
	buf.putShort(utfLen).
	    putBytes(utfBytes);
	buf.rewind();
	String newString = buf.getString();
	System.err.println("newString: " + newString);
	if (!s.equals(newString)) {
	    fail("Expected: " + s + ", got: " + newString);
	}
    }
}
