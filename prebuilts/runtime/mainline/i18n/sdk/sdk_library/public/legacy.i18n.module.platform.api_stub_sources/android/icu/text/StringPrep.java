/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2003-2015, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */


package android.icu.text;


/**
 * StringPrep API implements the StingPrep framework as described by
 * <a href="http://www.ietf.org/rfc/rfc3454.txt">RFC 3454</a>.
 * StringPrep prepares Unicode strings for use in network protocols.
 * Profiles of StingPrep are set of rules and data according to which the
 * Unicode Strings are prepared. Each profiles contains tables which describe
 * how a code point should be treated. The tables are broadly classied into
 * <ul>
 *     <li> Unassigned Table: Contains code points that are unassigned
 *          in the Unicode Version supported by StringPrep. Currently
 *          RFC 3454 supports Unicode 3.2. </li>
 *     <li> Prohibited Table: Contains code points that are prohibted from
 *          the output of the StringPrep processing function. </li>
 *     <li> Mapping Table: Contains code ponts that are deleted from the output or case mapped. </li>
 * </ul>
 *
 * The procedure for preparing Unicode strings:
 * <ol>
 *      <li> Map: For each character in the input, check if it has a mapping
 *           and, if so, replace it with its mapping. </li>
 *      <li> Normalize: Possibly normalize the result of step 1 using Unicode
 *           normalization. </li>
 *      <li> Prohibit: Check for any characters that are not allowed in the
 *           output.  If any are found, return an error.</li>
 *      <li> Check bidi: Possibly check for right-to-left characters, and if
 *           any are found, make sure that the whole string satisfies the
 *           requirements for bidirectional strings.  If the string does not
 *           satisfy the requirements for bidirectional strings, return an
 *           error.  </li>
 * </ol>
 * @author Ram Viswanadha
 * @hide Only a subset of ICU is exposed in Android
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class StringPrep {

StringPrep() { throw new RuntimeException("Stub!"); }

/**
 * Gets a StringPrep instance for the specified profile
 *
 * @param profile The profile passed to find the StringPrep instance.
 */

public static android.icu.text.StringPrep getInstance(int profile) { throw new RuntimeException("Stub!"); }

/**
 * Prepare the input String for use in applications with the given profile. This operation maps, normalizes(NFKC),
 * checks for prohibited and BiDi characters in the order defined by RFC 3454
 * depending on the options specified in the profile.
 *
 * @param src           A string
 * @param options       A bit set of options:
 *   <ul>
 *     <li>{@link #DEFAULT} Prohibit processing of unassigned code points in the input</li>
 *     <li>{@link #ALLOW_UNASSIGNED} Treat the unassigned code points are in the input
 *          as normal Unicode code points.</li>
 *   </ul>
 * @return String A String containing the output
 * @throws android.icu.text.StringPrepParseException An exception when parsing or preparing a string is invalid.
 */

public java.lang.String prepare(java.lang.String src, int options) throws android.icu.text.StringPrepParseException { throw new RuntimeException("Stub!"); }

/** 
 * Option to prohibit processing of unassigned code points in the input
 *
 * @see   #prepare
 */

public static final int DEFAULT = 0; // 0x0

/**
 * Profile type: RFC3920 XMPP Resourceprep
 * @see #getInstance(int)
 */

public static final int RFC3920_RESOURCEPREP = 8; // 0x8
}

