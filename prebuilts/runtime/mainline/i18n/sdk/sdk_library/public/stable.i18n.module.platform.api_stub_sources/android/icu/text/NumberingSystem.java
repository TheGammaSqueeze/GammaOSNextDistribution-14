/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2009-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */


package android.icu.text;

import android.icu.util.ULocale;
import android.icu.util.ULocale.Category;

/**
 * <code>NumberingSystem</code> is the base class for all number
 * systems. This class provides the interface for setting different numbering
 * system types, whether it be a simple alternate digit system such as
 * Thai digits or Devanagari digits, or an algorithmic numbering system such
 * as Hebrew numbering or Chinese numbering.
 *
 * @author       John Emmons
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class NumberingSystem {

/**
 * Default constructor.  Returns a numbering system that uses the Latin-script decimal
 * digits 0 through 9.  This should be equivalent to NumberingSystem.LATIN.
 */

public NumberingSystem() { throw new RuntimeException("Stub!"); }

/**
 * Factory method for creating a numbering system.
 * @param radix_in The radix for this numbering system.  ICU currently
 * supports only numbering systems whose radix is 10.
 * @param isAlgorithmic_in Specifies whether the numbering system is algorithmic
 * (true) or numeric (false).
 * @param desc_in String used to describe the characteristics of the numbering
 * system.  For numeric systems, this string contains the digits used by the
 * numbering system, in order, starting from zero.  For algorithmic numbering
 * systems, the string contains the name of the RBNF ruleset in the locale's
 * NumberingSystemRules section that will be used to format numbers using
 * this numbering system.
 */

public static android.icu.text.NumberingSystem getInstance(int radix_in, boolean isAlgorithmic_in, java.lang.String desc_in) { throw new RuntimeException("Stub!"); }

/**
 * Returns the default numbering system for the specified locale.
 */

public static android.icu.text.NumberingSystem getInstance(java.util.Locale inLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the default numbering system for the specified ULocale.
 */

public static android.icu.text.NumberingSystem getInstance(android.icu.util.ULocale locale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the default numbering system for the default <code>FORMAT</code> locale.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public static android.icu.text.NumberingSystem getInstance() { throw new RuntimeException("Stub!"); }

/**
 * Returns a numbering system from one of the predefined numbering systems
 * known to ICU.  Numbering system names are based on the numbering systems
 * defined in CLDR.  To get a list of available numbering systems, use the
 * getAvailableNames method.
 * @param name The name of the desired numbering system.  Numbering system
 * names often correspond with the name of the script they are associated
 * with.  For example, "thai" for Thai digits, "hebr" for Hebrew numerals.
 * @return The NumberingSystem instance, or null if not available.
 */

public static android.icu.text.NumberingSystem getInstanceByName(java.lang.String name) { throw new RuntimeException("Stub!"); }

/**
 * Returns a string array containing a list of the names of numbering systems
 * currently known to ICU.
 *
 * @return An array of strings in alphabetical (invariant) order.
 */

public static java.lang.String[] getAvailableNames() { throw new RuntimeException("Stub!"); }

/**
 * Convenience method to determine if a given digit string is valid for use as a
 * descriptor of a numeric ( non-algorithmic ) numbering system.  In order for
 * a digit string to be valid, it must contain exactly ten Unicode code points.
 */

public static boolean isValidDigitString(java.lang.String str) { throw new RuntimeException("Stub!"); }

/**
 * Returns the radix of the current numbering system.
 */

public int getRadix() { throw new RuntimeException("Stub!"); }

/**
 * Returns the description string of the current numbering system.
 * The description string describes the characteristics of the numbering
 * system.  For numeric systems, this string contains the digits used by the
 * numbering system, in order, starting from zero.  For algorithmic numbering
 * systems, the string contains the name of the RBNF ruleset in the locale's
 * NumberingSystemRules section that will be used to format numbers using
 * this numbering system.
 */

public java.lang.String getDescription() { throw new RuntimeException("Stub!"); }

/**
 * Returns the string representing the name of the numbering system.
 */

public java.lang.String getName() { throw new RuntimeException("Stub!"); }

/**
 * Returns the numbering system's algorithmic status.  If true,
 * the numbering system is algorithmic and uses an RBNF formatter to
 * format numerals.  If false, the numbering system is numeric and
 * uses a fixed set of digits.
 */

public boolean isAlgorithmic() { throw new RuntimeException("Stub!"); }

/**
 * For convenience, an instance representing the <em>latn</em> numbering system, which
 * corresponds to digits in the ASCII range '0' through '9'.
 */

public static final android.icu.text.NumberingSystem LATIN;
static { LATIN = null; }
}

