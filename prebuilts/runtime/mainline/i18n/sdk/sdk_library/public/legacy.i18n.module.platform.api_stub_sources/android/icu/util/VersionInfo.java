/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */


package android.icu.util;


/**
 * Class to store version numbers of the form major.minor.milli.micro.
 * @author synwee
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class VersionInfo implements java.lang.Comparable<android.icu.util.VersionInfo> {

private VersionInfo() { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance of VersionInfo with the argument version.
 * @param version version String in the format of "major.minor.milli.micro"
 *                or "major.minor.milli" or "major.minor" or "major",
 *                where major, minor, milli, micro are non-negative numbers
 *                &lt;= 255. If the trailing version numbers are
 *                not specified they are taken as 0s. E.g. Version "3.1" is
 *                equivalent to "3.1.0.0".
 * @return an instance of VersionInfo with the argument version.
 * @exception java.lang.IllegalArgumentException when the argument version
 *                is not in the right format
 */

public static android.icu.util.VersionInfo getInstance(java.lang.String version) { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance of VersionInfo with the argument version.
 * @param major major version, non-negative number &lt;= 255.
 * @param minor minor version, non-negative number &lt;= 255.
 * @param milli milli version, non-negative number &lt;= 255.
 * @param micro micro version, non-negative number &lt;= 255.
 * @exception java.lang.IllegalArgumentException when either arguments are negative or &gt; 255
 */

public static android.icu.util.VersionInfo getInstance(int major, int minor, int milli, int micro) { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance of VersionInfo with the argument version.
 * Equivalent to getInstance(major, minor, milli, 0).
 * @param major major version, non-negative number &lt;= 255.
 * @param minor minor version, non-negative number &lt;= 255.
 * @param milli milli version, non-negative number &lt;= 255.
 * @exception java.lang.IllegalArgumentException when either arguments are
 *                                     negative or &gt; 255
 */

public static android.icu.util.VersionInfo getInstance(int major, int minor, int milli) { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance of VersionInfo with the argument version.
 * Equivalent to getInstance(major, minor, 0, 0).
 * @param major major version, non-negative number &lt;= 255.
 * @param minor minor version, non-negative number &lt;= 255.
 * @exception java.lang.IllegalArgumentException when either arguments are
 *                                     negative or &gt; 255
 */

public static android.icu.util.VersionInfo getInstance(int major, int minor) { throw new RuntimeException("Stub!"); }

/**
 * Returns an instance of VersionInfo with the argument version.
 * Equivalent to getInstance(major, 0, 0, 0).
 * @param major major version, non-negative number &lt;= 255.
 * @exception java.lang.IllegalArgumentException when either arguments are
 *                                     negative or &gt; 255
 */

public static android.icu.util.VersionInfo getInstance(int major) { throw new RuntimeException("Stub!"); }

/**
 * Returns the String representative of VersionInfo in the format of
 * "major.minor.milli.micro"
 * @return String representative of VersionInfo
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * Returns the major version number
 * @return the major version number
 */

public int getMajor() { throw new RuntimeException("Stub!"); }

/**
 * Returns the minor version number
 * @return the minor version number
 */

public int getMinor() { throw new RuntimeException("Stub!"); }

/**
 * Returns the milli version number
 * @return the milli version number
 */

public int getMilli() { throw new RuntimeException("Stub!"); }

/**
 * Returns the micro version number
 * @return the micro version number
 */

public int getMicro() { throw new RuntimeException("Stub!"); }

/**
 * Checks if this version information is equals to the argument version
 * @param other object to be compared
 * @return true if other is equals to this object's version information,
 *         false otherwise
 */

public boolean equals(java.lang.Object other) { throw new RuntimeException("Stub!"); }

/**
 * Returns the hash code value for this set.
 *
 * @return the hash code value for this set.
 * @see java.lang.Object#hashCode()
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Compares other with this VersionInfo.
 * @param other VersionInfo to be compared
 * @return 0 if the argument is a VersionInfo object that has version
 *           information equals to this object.
 *           Less than 0 if the argument is a VersionInfo object that has
 *           version information greater than this object.
 *           Greater than 0 if the argument is a VersionInfo object that
 *           has version information less than this object.
 */

public int compareTo(android.icu.util.VersionInfo other) { throw new RuntimeException("Stub!"); }

/**
 * ICU4J current release version
 */

public static final android.icu.util.VersionInfo ICU_VERSION;
static { ICU_VERSION = null; }

/**
 * Collation builder code version.
 * When this is different, the same tailoring might result
 * in assigning different collation elements to code points.
 * This value may change in subsequent releases of ICU.
 */

public static final android.icu.util.VersionInfo UCOL_BUILDER_VERSION;
static { UCOL_BUILDER_VERSION = null; }

/**
 * Collation runtime version (sort key generator, string comparisons).
 * If the version is different, sort keys for the same string could be different.
 * This value may change in subsequent releases of ICU.
 */

public static final android.icu.util.VersionInfo UCOL_RUNTIME_VERSION;
static { UCOL_RUNTIME_VERSION = null; }

/**
 * Unicode 10.0 version
 */

public static final android.icu.util.VersionInfo UNICODE_10_0;
static { UNICODE_10_0 = null; }

/**
 * Unicode 11.0 version
 */

public static final android.icu.util.VersionInfo UNICODE_11_0;
static { UNICODE_11_0 = null; }

/**
 * Unicode 12.0 version
 */

public static final android.icu.util.VersionInfo UNICODE_12_0;
static { UNICODE_12_0 = null; }

/**
 * Unicode 12.1 version
 */

public static final android.icu.util.VersionInfo UNICODE_12_1;
static { UNICODE_12_1 = null; }

/**
 * Unicode 13.0 version
 */

public static final android.icu.util.VersionInfo UNICODE_13_0;
static { UNICODE_13_0 = null; }

/**
 * Unicode 1.0 version
 */

public static final android.icu.util.VersionInfo UNICODE_1_0;
static { UNICODE_1_0 = null; }

/**
 * Unicode 1.0.1 version
 */

public static final android.icu.util.VersionInfo UNICODE_1_0_1;
static { UNICODE_1_0_1 = null; }

/**
 * Unicode 1.1.0 version
 */

public static final android.icu.util.VersionInfo UNICODE_1_1_0;
static { UNICODE_1_1_0 = null; }

/**
 * Unicode 1.1.5 version
 */

public static final android.icu.util.VersionInfo UNICODE_1_1_5;
static { UNICODE_1_1_5 = null; }

/**
 * Unicode 2.0 version
 */

public static final android.icu.util.VersionInfo UNICODE_2_0;
static { UNICODE_2_0 = null; }

/**
 * Unicode 2.1.2 version
 */

public static final android.icu.util.VersionInfo UNICODE_2_1_2;
static { UNICODE_2_1_2 = null; }

/**
 * Unicode 2.1.5 version
 */

public static final android.icu.util.VersionInfo UNICODE_2_1_5;
static { UNICODE_2_1_5 = null; }

/**
 * Unicode 2.1.8 version
 */

public static final android.icu.util.VersionInfo UNICODE_2_1_8;
static { UNICODE_2_1_8 = null; }

/**
 * Unicode 2.1.9 version
 */

public static final android.icu.util.VersionInfo UNICODE_2_1_9;
static { UNICODE_2_1_9 = null; }

/**
 * Unicode 3.0 version
 */

public static final android.icu.util.VersionInfo UNICODE_3_0;
static { UNICODE_3_0 = null; }

/**
 * Unicode 3.0.1 version
 */

public static final android.icu.util.VersionInfo UNICODE_3_0_1;
static { UNICODE_3_0_1 = null; }

/**
 * Unicode 3.1.0 version
 */

public static final android.icu.util.VersionInfo UNICODE_3_1_0;
static { UNICODE_3_1_0 = null; }

/**
 * Unicode 3.1.1 version
 */

public static final android.icu.util.VersionInfo UNICODE_3_1_1;
static { UNICODE_3_1_1 = null; }

/**
 * Unicode 3.2 version
 */

public static final android.icu.util.VersionInfo UNICODE_3_2;
static { UNICODE_3_2 = null; }

/**
 * Unicode 4.0 version
 */

public static final android.icu.util.VersionInfo UNICODE_4_0;
static { UNICODE_4_0 = null; }

/**
 * Unicode 4.0.1 version
 */

public static final android.icu.util.VersionInfo UNICODE_4_0_1;
static { UNICODE_4_0_1 = null; }

/**
 * Unicode 4.1 version
 */

public static final android.icu.util.VersionInfo UNICODE_4_1;
static { UNICODE_4_1 = null; }

/**
 * Unicode 5.0 version
 */

public static final android.icu.util.VersionInfo UNICODE_5_0;
static { UNICODE_5_0 = null; }

/**
 * Unicode 5.1 version
 */

public static final android.icu.util.VersionInfo UNICODE_5_1;
static { UNICODE_5_1 = null; }

/**
 * Unicode 5.2 version
 */

public static final android.icu.util.VersionInfo UNICODE_5_2;
static { UNICODE_5_2 = null; }

/**
 * Unicode 6.0 version
 */

public static final android.icu.util.VersionInfo UNICODE_6_0;
static { UNICODE_6_0 = null; }

/**
 * Unicode 6.1 version
 */

public static final android.icu.util.VersionInfo UNICODE_6_1;
static { UNICODE_6_1 = null; }

/**
 * Unicode 6.2 version
 */

public static final android.icu.util.VersionInfo UNICODE_6_2;
static { UNICODE_6_2 = null; }

/**
 * Unicode 6.3 version
 */

public static final android.icu.util.VersionInfo UNICODE_6_3;
static { UNICODE_6_3 = null; }

/**
 * Unicode 7.0 version
 */

public static final android.icu.util.VersionInfo UNICODE_7_0;
static { UNICODE_7_0 = null; }

/**
 * Unicode 8.0 version
 */

public static final android.icu.util.VersionInfo UNICODE_8_0;
static { UNICODE_8_0 = null; }

/**
 * Unicode 9.0 version
 */

public static final android.icu.util.VersionInfo UNICODE_9_0;
static { UNICODE_9_0 = null; }
}

