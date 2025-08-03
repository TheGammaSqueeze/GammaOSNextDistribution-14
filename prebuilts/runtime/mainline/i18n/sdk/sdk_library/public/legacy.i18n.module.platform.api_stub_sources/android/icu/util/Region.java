/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2011-2016, International Business Machines Corporation
 * All Rights Reserved.
 *******************************************************************************
 */

package android.icu.util;


/**
 * <code>Region</code> is the class representing a Unicode Region Code, also known as a
 * Unicode Region Subtag, which is defined based upon the BCP 47 standard. We often think of
 * "regions" as "countries" when defining the characteristics of a locale.  Region codes There are different
 * types of region codes that are important to distinguish.
 * <p>
 *  Macroregion - A code for a "macro geographical (continental) region, geographical sub-region, or
 *  selected economic and other grouping" as defined in
 *  UN M.49 (http://unstats.un.org/unsd/methods/m49/m49regin.htm).
 *  These are typically 3-digit codes, but contain some 2-letter codes, such as the LDML code QO
 *  added for Outlying Oceania.  Not all UNM.49 codes are defined in LDML, but most of them are.
 *  Macroregions are represented in ICU by one of three region types: WORLD ( region code 001 ),
 *  CONTINENTS ( regions contained directly by WORLD ), and SUBCONTINENTS ( things contained directly
 *  by a continent ).
 *  <p>
 *  TERRITORY - A Region that is not a Macroregion. These are typically codes for countries, but also
 *  include areas that are not separate countries, such as the code "AQ" for Antarctica or the code
 *  "HK" for Hong Kong (SAR China). Overseas dependencies of countries may or may not have separate
 *  codes. The codes are typically 2-letter codes aligned with the ISO 3166 standard, but BCP47 allows
 *  for the use of 3-digit codes in the future.
 *  <p>
 *  UNKNOWN - The code ZZ is defined by Unicode LDML for use to indicate that the Region is unknown,
 *  or that the value supplied as a region was invalid.
 *  <p>
 *  DEPRECATED - Region codes that have been defined in the past but are no longer in modern usage,
 *  usually due to a country splitting into multiple territories or changing its name.
 *  <p>
 *  GROUPING - A widely understood grouping of territories that has a well defined membership such
 *  that a region code has been assigned for it.  Some of these are UNM.49 codes that do't fall into
 *  the world/continent/sub-continent hierarchy, while others are just well known groupings that have
 *  their own region code. Region "EU" (European Union) is one such region code that is a grouping.
 *  Groupings will never be returned by the getContainingRegion() API, since a different type of region
 *  ( WORLD, CONTINENT, or SUBCONTINENT ) will always be the containing region instead.
 *
 * @author       John Emmons
 * @hide Only a subset of ICU is exposed in Android
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class Region implements java.lang.Comparable<android.icu.util.Region> {

private Region() { throw new RuntimeException("Stub!"); }

/** Used to retrieve all available regions of a specific type.
 *
 * @param type The type of regions to be returned ( TERRITORY, MACROREGION, etc. )
 * @return An unmodifiable set of all known regions that match the given type.
 */

public static java.util.Set<android.icu.util.Region> getAvailable(android.icu.util.Region.RegionType type) { throw new RuntimeException("Stub!"); }

/** Returns the string representation of this region
 *
 * @return The string representation of this region, which is its ID.
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int compareTo(android.icu.util.Region other) { throw new RuntimeException("Stub!"); }
/**
 * RegionType is an enumeration defining the different types of regions.  Current possible
 * values are WORLD, CONTINENT, SUBCONTINENT, TERRITORY, GROUPING, DEPRECATED, and UNKNOWN.
 *
 * @hide Only a subset of ICU is exposed in Android
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum RegionType {
/**
 * Type representing a territory.
 */

TERRITORY;
}

}

