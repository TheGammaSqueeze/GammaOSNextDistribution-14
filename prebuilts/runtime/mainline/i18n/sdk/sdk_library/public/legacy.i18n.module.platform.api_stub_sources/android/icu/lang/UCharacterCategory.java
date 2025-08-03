/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/**
 *******************************************************************************
 * Copyright (C) 1996-2004, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */


package android.icu.lang;


/**
 * Enumerated Unicode category types from the UnicodeData.txt file.
 * Used as return results from <a href=UCharacter.html>UCharacter</a>
 * Equivalent to icu's UCharCategory.
 * Refer to <a href="http://www.unicode.org/Public/UNIDATA/UCD.html">
 * Unicode Consortium</a> for more information about UnicodeData.txt.
 * <p>
 * <em>NOTE:</em> the UCharacterCategory values are <em>not</em> compatible with
 * those returned by java.lang.Character.getType.  UCharacterCategory values
 * match the ones used in ICU4C, while java.lang.Character type
 * values, though similar, skip the value 17.</p>
 * <p>
 * This class is not subclassable
 * </p>
 * @author Syn Wee Quek
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class UCharacterCategory implements android.icu.lang.UCharacterEnums.ECharacterCategory {

private UCharacterCategory() { throw new RuntimeException("Stub!"); }

/**
 * Gets the name of the argument category
 * @param category to retrieve name
 * @return category name
 */

public static java.lang.String toString(int category) { throw new RuntimeException("Stub!"); }
}

