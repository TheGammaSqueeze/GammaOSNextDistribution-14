/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2003-2016, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */


package android.icu.text;


/**
 * Abstract base class for IDNA processing.
 * See http://www.unicode.org/reports/tr46/
 * and http://www.ietf.org/rfc/rfc3490.txt
 * <p>
 * The IDNA class is not intended for public subclassing.
 * <p>
 * The non-static methods implement UTS #46 and IDNA2008.
 * IDNA2008 is implemented according to UTS #46, see getUTS46Instance().
 * <p>
 * IDNA2003 is obsolete. The static methods implement IDNA2003. They are all deprecated.
 * <p>
 * IDNA2003 API Overview:
 * <p>
 * The static IDNA API methods implement the IDNA protocol as defined in the
 * <a href="http://www.ietf.org/rfc/rfc3490.txt">IDNA RFC</a>.
 * The draft defines 2 operations: ToASCII and ToUnicode. Domain labels
 * containing non-ASCII code points are required to be processed by
 * ToASCII operation before passing it to resolver libraries. Domain names
 * that are obtained from resolver libraries are required to be processed by
 * ToUnicode operation before displaying the domain name to the user.
 * IDNA requires that implementations process input strings with
 * <a href="http://www.ietf.org/rfc/rfc3491.txt">Nameprep</a>,
 * which is a profile of <a href="http://www.ietf.org/rfc/rfc3454.txt">Stringprep</a> ,
 * and then with <a href="http://www.ietf.org/rfc/rfc3492.txt">Punycode</a>.
 * Implementations of IDNA MUST fully implement Nameprep and Punycode;
 * neither Nameprep nor Punycode are optional.
 * The input and output of ToASCII and ToUnicode operations are Unicode
 * and are designed to be chainable, i.e., applying ToASCII or ToUnicode operations
 * multiple times to an input string will yield the same result as applying the operation
 * once.
 * ToUnicode(ToUnicode(ToUnicode...(ToUnicode(string)))) == ToUnicode(string)
 * ToASCII(ToASCII(ToASCII...(ToASCII(string))) == ToASCII(string).
 *
 * @author Ram Viswanadha, Markus Scherer
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class IDNA {

IDNA() { throw new RuntimeException("Stub!"); }

/**
 * Returns an IDNA instance which implements UTS #46.
 * Returns an unmodifiable instance, owned by the caller.
 * Cache it for multiple operations, and delete it when done.
 * The instance is thread-safe, that is, it can be used concurrently.
 * <p>
 * UTS #46 defines Unicode IDNA Compatibility Processing,
 * updated to the latest version of Unicode and compatible with both
 * IDNA2003 and IDNA2008.
 * <p>
 * The worker functions use transitional processing, including deviation mappings,
 * unless NONTRANSITIONAL_TO_ASCII or NONTRANSITIONAL_TO_UNICODE
 * is used in which case the deviation characters are passed through without change.
 * <p>
 * Disallowed characters are mapped to U+FFFD.
 * <p>
 * Operations with the UTS #46 instance do not support the
 * ALLOW_UNASSIGNED option.
 * <p>
 * By default, the UTS #46 implementation allows all ASCII characters (as valid or mapped).
 * When the USE_STD3_RULES option is used, ASCII characters other than
 * letters, digits, hyphen (LDH) and dot/full stop are disallowed and mapped to U+FFFD.
 *
 * @param options Bit set to modify the processing and error checking.
 * @return the UTS #46 IDNA instance, if successful
 */

public static android.icu.text.IDNA getUTS46Instance(int options) { throw new RuntimeException("Stub!"); }

/**
 * Converts a single domain name label into its ASCII form for DNS lookup.
 * If any processing step fails, then info.hasErrors() will be true and
 * the result might not be an ASCII string.
 * The label might be modified according to the types of errors.
 * Labels with severe errors will be left in (or turned into) their Unicode form.
 *
 * @param label Input domain name label
 * @param dest Destination string object
 * @param info Output container of IDNA processing details.
 * @return dest
 */

public abstract java.lang.StringBuilder labelToASCII(java.lang.CharSequence label, java.lang.StringBuilder dest, android.icu.text.IDNA.Info info);

/**
 * Converts a single domain name label into its Unicode form for human-readable display.
 * If any processing step fails, then info.hasErrors() will be true.
 * The label might be modified according to the types of errors.
 *
 * @param label Input domain name label
 * @param dest Destination string object
 * @param info Output container of IDNA processing details.
 * @return dest
 */

public abstract java.lang.StringBuilder labelToUnicode(java.lang.CharSequence label, java.lang.StringBuilder dest, android.icu.text.IDNA.Info info);

/**
 * Converts a whole domain name into its ASCII form for DNS lookup.
 * If any processing step fails, then info.hasErrors() will be true and
 * the result might not be an ASCII string.
 * The domain name might be modified according to the types of errors.
 * Labels with severe errors will be left in (or turned into) their Unicode form.
 *
 * @param name Input domain name
 * @param dest Destination string object
 * @param info Output container of IDNA processing details.
 * @return dest
 */

public abstract java.lang.StringBuilder nameToASCII(java.lang.CharSequence name, java.lang.StringBuilder dest, android.icu.text.IDNA.Info info);

/**
 * Converts a whole domain name into its Unicode form for human-readable display.
 * If any processing step fails, then info.hasErrors() will be true.
 * The domain name might be modified according to the types of errors.
 *
 * @param name Input domain name
 * @param dest Destination string object
 * @param info Output container of IDNA processing details.
 * @return dest
 */

public abstract java.lang.StringBuilder nameToUnicode(java.lang.CharSequence name, java.lang.StringBuilder dest, android.icu.text.IDNA.Info info);

/**
 * IDNA option to check for whether the input conforms to the BiDi rules.
 * For use in static worker and factory methods.
 * <p>This option is ignored by the IDNA2003 implementation.
 * (IDNA2003 always performs a BiDi check.)
 */

public static final int CHECK_BIDI = 4; // 0x4

/**
 * IDNA option to check for whether the input conforms to the CONTEXTJ rules.
 * For use in static worker and factory methods.
 * <p>This option is ignored by the IDNA2003 implementation.
 * (The CONTEXTJ check is new in IDNA2008.)
 */

public static final int CHECK_CONTEXTJ = 8; // 0x8

/**
 * IDNA option to check for whether the input conforms to the CONTEXTO rules.
 * For use in static worker and factory methods.
 * <p>This option is ignored by the IDNA2003 implementation.
 * (The CONTEXTO check is new in IDNA2008.)
 * <p>This is for use by registries for IDNA2008 conformance.
 * UTS #46 does not require the CONTEXTO check.
 */

public static final int CHECK_CONTEXTO = 64; // 0x40

/** 
 * Default options value: None of the other options are set.
 * For use in static worker and factory methods.
 */

public static final int DEFAULT = 0; // 0x0

/**
 * IDNA option for nontransitional processing in ToASCII().
 * For use in static worker and factory methods.
 * <p>By default, ToASCII() uses transitional processing.
 * <p>This option is ignored by the IDNA2003 implementation.
 * (This is only relevant for compatibility of newer IDNA implementations with IDNA2003.)
 */

public static final int NONTRANSITIONAL_TO_ASCII = 16; // 0x10

/**
 * IDNA option for nontransitional processing in ToUnicode().
 * For use in static worker and factory methods.
 * <p>By default, ToUnicode() uses transitional processing.
 * <p>This option is ignored by the IDNA2003 implementation.
 * (This is only relevant for compatibility of newer IDNA implementations with IDNA2003.)
 */

public static final int NONTRANSITIONAL_TO_UNICODE = 32; // 0x20

/** 
 * Option to check whether the input conforms to the STD3 ASCII rules,
 * for example the restriction of labels to LDH characters
 * (ASCII Letters, Digits and Hyphen-Minus).
 * For use in static worker and factory methods.
 */

public static final int USE_STD3_RULES = 2; // 0x2
/**
 * IDNA error bit set values.
 * When a domain name or label fails a processing step or does not meet the
 * validity criteria, then one or more of these error bits are set.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum Error {
/**
 * A non-final domain name label (or the whole domain name) is empty.
 */

EMPTY_LABEL,
/**
 * A domain name label is longer than 63 bytes.
 * (See STD13/RFC1034 3.1. Name space specifications and terminology.)
 * This is only checked in ToASCII operations, and only if the output label is all-ASCII.
 */

LABEL_TOO_LONG,
/**
 * A domain name is longer than 255 bytes in its storage form.
 * (See STD13/RFC1034 3.1. Name space specifications and terminology.)
 * This is only checked in ToASCII operations, and only if the output domain name is all-ASCII.
 */

DOMAIN_NAME_TOO_LONG,
/**
 * A label starts with a hyphen-minus ('-').
 */

LEADING_HYPHEN,
/**
 * A label ends with a hyphen-minus ('-').
 */

TRAILING_HYPHEN,
/**
 * A label contains hyphen-minus ('-') in the third and fourth positions.
 */

HYPHEN_3_4,
/**
 * A label starts with a combining mark.
 */

LEADING_COMBINING_MARK,
/**
 * A label or domain name contains disallowed characters.
 */

DISALLOWED,
/**
 * A label starts with "xn--" but does not contain valid Punycode.
 * That is, an xn-- label failed Punycode decoding.
 */

PUNYCODE,
/**
 * A label contains a dot=full stop.
 * This can occur in an input string for a single-label function.
 */

LABEL_HAS_DOT,
/**
 * An ACE label does not contain a valid label string.
 * The label was successfully ACE (Punycode) decoded but the resulting
 * string had severe validation errors. For example,
 * it might contain characters that are not allowed in ACE labels,
 * or it might not be normalized.
 */

INVALID_ACE_LABEL,
/**
 * A label does not meet the IDNA BiDi requirements (for right-to-left characters).
 */

BIDI,
/**
 * A label does not meet the IDNA CONTEXTJ requirements.
 */

CONTEXTJ,
/**
 * A label does not meet the IDNA CONTEXTO requirements for punctuation characters.
 * Some punctuation characters "Would otherwise have been DISALLOWED"
 * but are allowed in certain contexts. (RFC 5892)
 */

CONTEXTO_PUNCTUATION,
/**
 * A label does not meet the IDNA CONTEXTO requirements for digits.
 * Arabic-Indic Digits (U+066x) must not be mixed with Extended Arabic-Indic Digits (U+06Fx).
 */

CONTEXTO_DIGITS;
}

/**
 * Output container for IDNA processing errors.
 * The Info class is not suitable for subclassing.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class Info {

/**
 * Constructor.
 */

public Info() { throw new RuntimeException("Stub!"); }

/**
 * Were there IDNA processing errors?
 * @return true if there were processing errors
 */

public boolean hasErrors() { throw new RuntimeException("Stub!"); }

/**
 * Returns a set indicating IDNA processing errors.
 * @return set of processing errors (modifiable, and not null)
 */

public java.util.Set<android.icu.text.IDNA.Error> getErrors() { throw new RuntimeException("Stub!"); }

/**
 * Returns true if transitional and nontransitional processing produce different results.
 * This is the case when the input label or domain name contains
 * one or more deviation characters outside a Punycode label (see UTS #46).
 * <ul>
 * <li>With nontransitional processing, such characters are
 * copied to the destination string.
 * <li>With transitional processing, such characters are
 * mapped (sharp s/sigma) or removed (joiner/nonjoiner).
 * </ul>
 * @return true if transitional and nontransitional processing produce different results
 */

public boolean isTransitionalDifferent() { throw new RuntimeException("Stub!"); }
}

}

