/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2009-2012, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package android.icu.util;


/**
 * Thrown by methods in {@link android.icu.util.ULocale ULocale} and {@link android.icu.util.ULocale.Builder ULocale.Builder} to
 * indicate that an argument is not a well-formed BCP 47 tag.
 *
 * @see android.icu.util.ULocale
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class IllformedLocaleException extends java.lang.RuntimeException {

/**
 * Constructs a new <code>IllformedLocaleException</code> with no
 * detail message and -1 as the error index.
 */

public IllformedLocaleException() { throw new RuntimeException("Stub!"); }

/**
 * Constructs a new <code>IllformedLocaleException</code> with the
 * given message and -1 as the error index.
 *
 * @param message the message
 */

public IllformedLocaleException(java.lang.String message) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a new <code>IllformedLocaleException</code> with the
 * given message and the error index.  The error index is the approximate
 * offset from the start of the ill-formed value to the point where the
 * parse first detected an error.  A negative error index value indicates
 * either the error index is not applicable or unknown.
 *
 * @param message the message
 * @param errorIndex the index
 */

public IllformedLocaleException(java.lang.String message, int errorIndex) { throw new RuntimeException("Stub!"); }

/**
 * Returns the index where the error was found. A negative value indicates
 * either the error index is not applicable or unknown.
 *
 * @return the error index
 */

public int getErrorIndex() { throw new RuntimeException("Stub!"); }
}

