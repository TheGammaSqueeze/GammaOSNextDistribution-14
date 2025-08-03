/**
*******************************************************************************
* Copyright (C) 1996-2005, International Business Machines Corporation and    *
* others. All Rights Reserved.                                                *
*******************************************************************************
*
*******************************************************************************
*/


package com.android.icu.charset;


/**
 * This class is used from native code associated with {@link com.android.icu.charset.NativeConverter NativeConverter}.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class CharsetICU extends java.nio.charset.Charset {

/**
 * Called by native codes.
 */

protected CharsetICU(java.lang.String canonicalName, java.lang.String icuCanonName, java.lang.String[] aliases) { super(null, null); throw new RuntimeException("Stub!"); }

public java.nio.charset.CharsetDecoder newDecoder() { throw new RuntimeException("Stub!"); }

public java.nio.charset.CharsetEncoder newEncoder() { throw new RuntimeException("Stub!"); }

public boolean contains(java.nio.charset.Charset cs) { throw new RuntimeException("Stub!"); }
}

