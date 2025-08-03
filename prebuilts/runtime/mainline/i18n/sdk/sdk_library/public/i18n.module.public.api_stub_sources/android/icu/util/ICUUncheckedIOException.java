/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2014-2015, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package android.icu.util;


/**
 * Unchecked version of {@link java.io.IOException}.
 * Some ICU APIs do not throw the standard exception but instead wrap it
 * into this unchecked version.
 *
 * <p>This currently extends {@link java.lang.RuntimeException RuntimeException},
 * but when ICU can rely on Java 8 this class should be changed to extend
 * java.io.UncheckedIOException instead.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class ICUUncheckedIOException extends java.lang.RuntimeException {

/**
 * Default constructor.
 */

public ICUUncheckedIOException() { throw new RuntimeException("Stub!"); }

/**
 * Constructor.
 *
 * @param message exception message string
 */

public ICUUncheckedIOException(java.lang.String message) { throw new RuntimeException("Stub!"); }

/**
 * Constructor.
 *
 * @param cause original exception (normally a {@link java.io.IOException})
 */

public ICUUncheckedIOException(java.lang.Throwable cause) { throw new RuntimeException("Stub!"); }

/**
 * Constructor.
 *
 * @param message exception message string
 * @param cause original exception (normally a {@link java.io.IOException})
 */

public ICUUncheckedIOException(java.lang.String message, java.lang.Throwable cause) { throw new RuntimeException("Stub!"); }
}

