/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2003-2014, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package android.icu.text;

import java.text.ParseException;

/**
 * Exception that signals an error has occurred while parsing the
 * input to StringPrep or IDNA.
 *
 * @author Ram Viswanadha
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class StringPrepParseException extends java.text.ParseException {

/**
 * Construct a ParseException object with the given message
 * and error code
 *
 * @param message A string describing the type of error that occurred
 * @param error   The error that has occurred
 */

public StringPrepParseException(java.lang.String message, int error) { super(null, 0); throw new RuntimeException("Stub!"); }

/**
 * Construct a ParseException object with the given message and
 * error code
 *
 * @param message A string describing the type of error that occurred
 * @param error   The error that has occurred
 * @param rules   The input rules string
 * @param pos     The position of error in the rules string
 */

public StringPrepParseException(java.lang.String message, int error, java.lang.String rules, int pos) { super(null, 0); throw new RuntimeException("Stub!"); }

/**
 * Construct  a ParseException object with the given message and error code
 *
 * @param message    A string describing the type of error that occurred
 * @param error      The error that has occurred
 * @param rules      The input rules string
 * @param pos        The position of error in the rules string
 * @param lineNumber The line number at which the error has occurred.
 *                   If the parse engine is not using this field, it should set it to zero.  Otherwise
 *                   it should be a positive integer. The default value of this field
 *                   is -1. It will be set to 0 if the code populating this struct is not
 *                   using line numbers.
 */

public StringPrepParseException(java.lang.String message, int error, java.lang.String rules, int pos, int lineNumber) { super(null, 0); throw new RuntimeException("Stub!"); }

/**
 * Compare this ParseException to another and evaluate if they are equal.
 * The comparison works only on the type of error and does not compare
 * the rules strings, if any, for equality.
 *
 * @param other The exception that this object should be compared to
 * @return true if the objects are equal, false if unequal
 */

public boolean equals(java.lang.Object other) { throw new RuntimeException("Stub!"); }

/**
 * Mock implementation of hashCode(). This implementation always returns a constant
 * value. When Java assertion is enabled, this method triggers an assertion failure.
 * @return a hash code value for this object.
 * @hide original deprecated declaration
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Returns the position of error in the rules string
 *
 * @return String
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * Returns the error code of this exception.
 * This method is only used for testing to verify the error.
 * @return The error code
 */

public int getError() { throw new RuntimeException("Stub!"); }

/**
 */

public static final int ACE_PREFIX_ERROR = 6; // 0x6

/**
 */

public static final int BUFFER_OVERFLOW_ERROR = 9; // 0x9

/**
 */

public static final int CHECK_BIDI_ERROR = 4; // 0x4

/**
 */

public static final int DOMAIN_NAME_TOO_LONG_ERROR = 11; // 0xb

/**
 */

public static final int ILLEGAL_CHAR_FOUND = 1; // 0x1

/**
 */

public static final int INVALID_CHAR_FOUND = 0; // 0x0

/**
 */

public static final int LABEL_TOO_LONG_ERROR = 8; // 0x8

/**
 */

public static final int PROHIBITED_ERROR = 2; // 0x2

/**
 */

public static final int STD3_ASCII_RULES_ERROR = 5; // 0x5

/**
 */

public static final int UNASSIGNED_ERROR = 3; // 0x3

/**
 */

public static final int VERIFICATION_ERROR = 7; // 0x7

/**
 */

public static final int ZERO_LENGTH_LABEL = 10; // 0xa
}

