/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package android.icu.text;

import java.util.Locale;
import android.icu.util.ULocale;
import android.icu.util.ULocale.Category;
import java.util.Enumeration;

/**
 * <code>Transliterator</code> is an abstract class that transliterates text from one format to another. The most common
 * kind of transliterator is a script, or alphabet, transliterator. For example, a Russian to Latin transliterator
 * changes Russian text written in Cyrillic characters to phonetically equivalent Latin characters. It does not
 * <em>translate</em> Russian to English! Transliteration, unlike translation, operates on characters, without reference
 * to the meanings of words and sentences.
 *
 * <p>
 * Although script conversion is its most common use, a transliterator can actually perform a more general class of
 * tasks. In fact, <code>Transliterator</code> defines a very general API which specifies only that a segment of the
 * input text is replaced by new text. The particulars of this conversion are determined entirely by subclasses of
 * <code>Transliterator</code>.
 *
 * <p>
 * <b>Transliterators are stateless</b>
 *
 * <p>
 * <code>Transliterator</code> objects are <em>stateless</em>; they retain no information between calls to
 * <code>transliterate()</code>. As a result, threads may share transliterators without synchronizing them. This might
 * seem to limit the complexity of the transliteration operation. In practice, subclasses perform complex
 * transliterations by delaying the replacement of text until it is known that no other replacements are possible. In
 * other words, although the <code>Transliterator</code> objects are stateless, the source text itself embodies all the
 * needed information, and delayed operation allows arbitrary complexity.
 *
 * <p>
 * <b>Batch transliteration</b>
 *
 * <p>
 * The simplest way to perform transliteration is all at once, on a string of existing text. This is referred to as
 * <em>batch</em> transliteration. For example, given a string <code>input</code> and a transliterator <code>t</code>,
 * the call
 *
 * <blockquote><code>String result = t.transliterate(input);
 * </code></blockquote>
 *
 * will transliterate it and return the result. Other methods allow the client to specify a substring to be
 * transliterated and to use {@link android.icu.text.Replaceable Replaceable} objects instead of strings, in order to preserve out-of-band
 * information (such as text styles).
 *
 * <p>
 * <b>Keyboard transliteration</b>
 *
 * <p>
 * Somewhat more involved is <em>keyboard</em>, or incremental transliteration. This is the transliteration of text that
 * is arriving from some source (typically the user's keyboard) one character at a time, or in some other piecemeal
 * fashion.
 *
 * <p>
 * In keyboard transliteration, a <code>Replaceable</code> buffer stores the text. As text is inserted, as much as
 * possible is transliterated on the fly. This means a GUI that displays the contents of the buffer may show text being
 * modified as each new character arrives.
 *
 * <p>
 * Consider the simple rule-based Transliterator:
 *
 * <blockquote><code>
 * th&gt;{theta}<br>
 * t&gt;{tau}
 * </code></blockquote>
 *
 * When the user types 't', nothing will happen, since the transliterator is waiting to see if the next character is
 * 'h'. To remedy this, we introduce the notion of a cursor, marked by a '|' in the output string:
 *
 * <blockquote><code>
 * t&gt;|{tau}<br>
 * {tau}h&gt;{theta}
 * </code></blockquote>
 *
 * Now when the user types 't', tau appears, and if the next character is 'h', the tau changes to a theta. This is
 * accomplished by maintaining a cursor position (independent of the insertion point, and invisible in the GUI) across
 * calls to <code>transliterate()</code>. Typically, the cursor will be coincident with the insertion point, but in a
 * case like the one above, it will precede the insertion point.
 *
 * <p>
 * Keyboard transliteration methods maintain a set of three indices that are updated with each call to
 * <code>transliterate()</code>, including the cursor, start, and limit. These indices are changed by the method, and
 * they are passed in and out via a Position object. The <code>start</code> index marks the beginning of the substring
 * that the transliterator will look at. It is advanced as text becomes committed (but it is not the committed index;
 * that's the <code>cursor</code>). The <code>cursor</code> index, described above, marks the point at which the
 * transliterator last stopped, either because it reached the end, or because it required more characters to
 * disambiguate between possible inputs. The <code>cursor</code> can also be explicitly set by rules.
 * Any characters before the <code>cursor</code> index are frozen; future keyboard
 * transliteration calls within this input sequence will not change them. New text is inserted at the <code>limit</code>
 * index, which marks the end of the substring that the transliterator looks at.
 *
 * <p>
 * Because keyboard transliteration assumes that more characters are to arrive, it is conservative in its operation. It
 * only transliterates when it can do so unambiguously. Otherwise it waits for more characters to arrive. When the
 * client code knows that no more characters are forthcoming, perhaps because the user has performed some input
 * termination operation, then it should call <code>finishTransliteration()</code> to complete any pending
 * transliterations.
 *
 * <p>
 * <b>Inverses</b>
 *
 * <p>
 * Pairs of transliterators may be inverses of one another. For example, if transliterator <b>A</b> transliterates
 * characters by incrementing their Unicode value (so "abc" -&gt; "def"), and transliterator <b>B</b> decrements character
 * values, then <b>A</b> is an inverse of <b>B</b> and vice versa. If we compose <b>A</b> with <b>B</b> in a compound
 * transliterator, the result is the indentity transliterator, that is, a transliterator that does not change its input
 * text.
 *
 * The <code>Transliterator</code> method <code>getInverse()</code> returns a transliterator's inverse, if one exists,
 * or <code>null</code> otherwise. However, the result of <code>getInverse()</code> usually will <em>not</em> be a true
 * mathematical inverse. This is because true inverse transliterators are difficult to formulate. For example, consider
 * two transliterators: <b>AB</b>, which transliterates the character 'A' to 'B', and <b>BA</b>, which transliterates
 * 'B' to 'A'. It might seem that these are exact inverses, since
 *
 * <blockquote>"A" x <b>AB</b> -&gt; "B"<br>
 * "B" x <b>BA</b> -&gt; "A"</blockquote>
 *
 * where 'x' represents transliteration. However,
 *
 * <blockquote>"ABCD" x <b>AB</b> -&gt; "BBCD"<br>
 * "BBCD" x <b>BA</b> -&gt; "AACD"</blockquote>
 *
 * so <b>AB</b> composed with <b>BA</b> is not the identity. Nonetheless, <b>BA</b> may be usefully considered to be
 * <b>AB</b>'s inverse, and it is on this basis that <b>AB</b><code>.getInverse()</code> could legitimately return
 * <b>BA</b>.
 *
 * <p>
 * <b>Filtering</b>
 * <p>Each transliterator has a filter, which restricts changes to those characters selected by the filter. The
 * filter affects just the characters that are changed -- the characters outside of the filter are still part of the
 * context for the filter. For example, in the following even though 'x' is filtered out, and doesn't convert to y, it does affect the conversion of 'a'.
 *
 * <pre>
 * String rules = &quot;x &gt; y; x{a} &gt; b; &quot;;
 * Transliterator tempTrans = Transliterator.createFromRules(&quot;temp&quot;, rules, Transliterator.FORWARD);
 * tempTrans.setFilter(new UnicodeSet(&quot;[a]&quot;));
 * String tempResult = tempTrans.transform(&quot;xa&quot;);
 * // results in &quot;xb&quot;
 *</pre>
 * <p>
 * <b>IDs and display names</b>
 *
 * <p>
 * A transliterator is designated by a short identifier string or <em>ID</em>. IDs follow the format
 * <em>source-destination</em>, where <em>source</em> describes the entity being replaced, and <em>destination</em>
 * describes the entity replacing <em>source</em>. The entities may be the names of scripts, particular sequences of
 * characters, or whatever else it is that the transliterator converts to or from. For example, a transliterator from
 * Russian to Latin might be named "Russian-Latin". A transliterator from keyboard escape sequences to Latin-1
 * characters might be named "KeyboardEscape-Latin1". By convention, system entity names are in English, with the
 * initial letters of words capitalized; user entity names may follow any format so long as they do not contain dashes.
 *
 * <p>
 * In addition to programmatic IDs, transliterator objects have display names for presentation in user interfaces,
 * returned by {@link #getDisplayName}.
 *
 * <p>
 * <b>Composed transliterators</b>
 *
 * <p>
 * In addition to built-in system transliterators like "Latin-Greek", there are also built-in <em>composed</em>
 * transliterators. These are implemented by composing two or more component transliterators. For example, if we have
 * scripts "A", "B", "C", and "D", and we want to transliterate between all pairs of them, then we need to write 12
 * transliterators: "A-B", "A-C", "A-D", "B-A",..., "D-A", "D-B", "D-C". If it is possible to convert all scripts to an
 * intermediate script "M", then instead of writing 12 rule sets, we only need to write 8: "A~M", "B~M", "C~M", "D~M",
 * "M~A", "M~B", "M~C", "M~D". (This might not seem like a big win, but it's really 2<em>n</em> vs. <em>n</em>
 * <sup>2</sup> - <em>n</em>, so as <em>n</em> gets larger the gain becomes significant. With 9 scripts, it's 18 vs. 72
 * rule sets, a big difference.) Note the use of "~" rather than "-" for the script separator here; this indicates that
 * the given transliterator is intended to be composed with others, rather than be used as is.
 *
 * <p>
 * Composed transliterators can be instantiated as usual. For example, the system transliterator "Devanagari-Gujarati"
 * is a composed transliterator built internally as "Devanagari~InterIndic;InterIndic~Gujarati". When this
 * transliterator is instantiated, it appears externally to be a standard transliterator (e.g., getID() returns
 * "Devanagari-Gujarati").
 *
 * <p><b>Rule syntax</b>
 *
 * <p>A set of rules determines how to perform translations.
 * Rules within a rule set are separated by semicolons (';').
 * To include a literal semicolon, prefix it with a backslash ('\').
 * Unicode Pattern_White_Space is ignored.
 * If the first non-blank character on a line is '#',
 * the entire line is ignored as a comment.
 *
 * <p>Each set of rules consists of two groups, one forward, and one
 * reverse. This is a convention that is not enforced; rules for one
 * direction may be omitted, with the result that translations in
 * that direction will not modify the source text. In addition,
 * bidirectional forward-reverse rules may be specified for
 * symmetrical transformations.
 *
 * <p>Note: Another description of the Transliterator rule syntax is available in
 * <a href="https://www.unicode.org/reports/tr35/tr35-general.html#Transform_Rules_Syntax">section
 * Transform Rules Syntax of UTS #35: Unicode LDML</a>.
 * The rules are shown there using arrow symbols ? and ? and ?.
 * ICU supports both those and the equivalent ASCII symbols &lt; and &gt; and &lt;&gt;.
 *
 * <p>Rule statements take one of the following forms:
 *
 * <dl>
 *     <dt><code>$alefmadda=\\u0622;</code></dt>
 *     <dd><strong>Variable definition.</strong> The name on the
 *         left is assigned the text on the right. In this example,
 *         after this statement, instances of the left hand name,
 *         &quot;<code>$alefmadda</code>&quot;, will be replaced by
 *         the Unicode character U+0622. Variable names must begin
 *         with a letter and consist only of letters, digits, and
 *         underscores. Case is significant. Duplicate names cause
 *         an exception to be thrown, that is, variables cannot be
 *         redefined. The right hand side may contain well-formed
 *         text of any length, including no text at all (&quot;<code>$empty=;</code>&quot;).
 *         The right hand side may contain embedded <code>UnicodeSet</code>
 *         patterns, for example, &quot;<code>$softvowel=[eiyEIY]</code>&quot;.</dd>
 *     <dt><code>ai&gt;$alefmadda;</code></dt>
 *     <dd><strong>Forward translation rule.</strong> This rule
 *         states that the string on the left will be changed to the
 *         string on the right when performing forward
 *         transliteration.</dd>
 *     <dt><code>ai&lt;$alefmadda;</code></dt>
 *     <dd><strong>Reverse translation rule.</strong> This rule
 *         states that the string on the right will be changed to
 *         the string on the left when performing reverse
 *         transliteration.</dd>
 * </dl>
 *
 * <dl>
 *     <dt><code>ai&lt;&gt;$alefmadda;</code></dt>
 *     <dd><strong>Bidirectional translation rule.</strong> This
 *         rule states that the string on the right will be changed
 *         to the string on the left when performing forward
 *         transliteration, and vice versa when performing reverse
 *         transliteration.</dd>
 * </dl>
 *
 * <p>Translation rules consist of a <em>match pattern</em> and an <em>output
 * string</em>. The match pattern consists of literal characters,
 * optionally preceded by context, and optionally followed by
 * context. Context characters, like literal pattern characters,
 * must be matched in the text being transliterated. However, unlike
 * literal pattern characters, they are not replaced by the output
 * text. For example, the pattern &quot;<code>abc{def}</code>&quot;
 * indicates the characters &quot;<code>def</code>&quot; must be
 * preceded by &quot;<code>abc</code>&quot; for a successful match.
 * If there is a successful match, &quot;<code>def</code>&quot; will
 * be replaced, but not &quot;<code>abc</code>&quot;. The final '<code>}</code>'
 * is optional, so &quot;<code>abc{def</code>&quot; is equivalent to
 * &quot;<code>abc{def}</code>&quot;. Another example is &quot;<code>{123}456</code>&quot;
 * (or &quot;<code>123}456</code>&quot;) in which the literal
 * pattern &quot;<code>123</code>&quot; must be followed by &quot;<code>456</code>&quot;.
 *
 * <p>The output string of a forward or reverse rule consists of
 * characters to replace the literal pattern characters. If the
 * output string contains the character '<code>|</code>', this is
 * taken to indicate the location of the <em>cursor</em> after
 * replacement. The cursor is the point in the text at which the
 * next replacement, if any, will be applied. The cursor is usually
 * placed within the replacement text; however, it can actually be
 * placed into the precending or following context by using the
 * special character '@'. Examples:
 *
 * <pre>
 *     a {foo} z &gt; | @ bar; # foo -&gt; bar, move cursor before a
 *     {foo} xyz &gt; bar @@|; #&nbsp;foo -&gt; bar, cursor between y and z
 * </pre>
 *
 * <p><b>UnicodeSet</b>
 *
 * <p><code>UnicodeSet</code> patterns may appear anywhere that
 * makes sense. They may appear in variable definitions.
 * Contrariwise, <code>UnicodeSet</code> patterns may themselves
 * contain variable references, such as &quot;<code>$a=[a-z];$not_a=[^$a]</code>&quot;,
 * or &quot;<code>$range=a-z;$ll=[$range]</code>&quot;.
 *
 * <p><code>UnicodeSet</code> patterns may also be embedded directly
 * into rule strings. Thus, the following two rules are equivalent:
 *
 * <pre>
 *     $vowel=[aeiou]; $vowel&gt;'*'; # One way to do this
 *     [aeiou]&gt;'*'; # Another way
 * </pre>
 *
 * <p>See {@link android.icu.text.UnicodeSet UnicodeSet} for more documentation and examples.
 *
 * <p><b>Segments</b>
 *
 * <p>Segments of the input string can be matched and copied to the
 * output string. This makes certain sets of rules simpler and more
 * general, and makes reordering possible. For example:
 *
 * <pre>
 *     ([a-z]) &gt; $1 $1; # double lowercase letters
 *     ([:Lu:]) ([:Ll:]) &gt; $2 $1; # reverse order of Lu-Ll pairs
 * </pre>
 *
 * <p>The segment of the input string to be copied is delimited by
 * &quot;<code>(</code>&quot; and &quot;<code>)</code>&quot;. Up to
 * nine segments may be defined. Segments may not overlap. In the
 * output string, &quot;<code>$1</code>&quot; through &quot;<code>$9</code>&quot;
 * represent the input string segments, in left-to-right order of
 * definition.
 *
 * <p><b>Anchors</b>
 *
 * <p>Patterns can be anchored to the beginning or the end of the text. This is done with the
 * special characters '<code>^</code>' and '<code>$</code>'. For example:
 *
 * <pre>
 *   ^ a&nbsp;&nbsp; &gt; 'BEG_A'; &nbsp;&nbsp;# match 'a' at start of text
 *   &nbsp; a&nbsp;&nbsp; &gt; 'A'; # match other instances of 'a'
 *   &nbsp; z $ &gt; 'END_Z'; &nbsp;&nbsp;# match 'z' at end of text
 *   &nbsp; z&nbsp;&nbsp; &gt; 'Z';&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; # match other instances of 'z'
 * </pre>
 *
 * <p>It is also possible to match the beginning or the end of the text using a <code>UnicodeSet</code>.
 * This is done by including a virtual anchor character '<code>$</code>' at the end of the
 * set pattern. Although this is usually the match chafacter for the end anchor, the set will
 * match either the beginning or the end of the text, depending on its placement. For
 * example:
 *
 * <pre>
 *   $x = [a-z$]; &nbsp;&nbsp;# match 'a' through 'z' OR anchor
 *   $x 1&nbsp;&nbsp;&nbsp; &gt; 2;&nbsp;&nbsp; # match '1' after a-z or at the start
 *   &nbsp;&nbsp; 3 $x &gt; 4; &nbsp;&nbsp;# match '3' before a-z or at the end
 * </pre>
 *
 * <p><b>Example</b>
 *
 * <p>The following example rules illustrate many of the features of
 * the rule language.
 *
 * <table border="0" cellpadding="4">
 *     <tr>
 *         <td style="vertical-align: top;">Rule 1.</td>
 *         <td style="vertical-align: top; write-space: nowrap;"><code>abc{def}&gt;x|y</code></td>
 *     </tr>
 *     <tr>
 *         <td style="vertical-align: top;">Rule 2.</td>
 *         <td style="vertical-align: top; write-space: nowrap;"><code>xyz&gt;r</code></td>
 *     </tr>
 *     <tr>
 *         <td style="vertical-align: top;">Rule 3.</td>
 *         <td style="vertical-align: top; write-space: nowrap;"><code>yz&gt;q</code></td>
 *     </tr>
 * </table>
 *
 * <p>Applying these rules to the string &quot;<code>adefabcdefz</code>&quot;
 * yields the following results:
 *
 * <table border="0" cellpadding="4">
 *     <tr>
 *         <td style="vertical-align: top; write-space: nowrap;"><code>|adefabcdefz</code></td>
 *         <td style="vertical-align: top;">Initial state, no rules match. Advance
 *         cursor.</td>
 *     </tr>
 *     <tr>
 *         <td style="vertical-align: top; write-space: nowrap;"><code>a|defabcdefz</code></td>
 *         <td style="vertical-align: top;">Still no match. Rule 1 does not match
 *         because the preceding context is not present.</td>
 *     </tr>
 *     <tr>
 *         <td style="vertical-align: top; write-space: nowrap;"><code>ad|efabcdefz</code></td>
 *         <td style="vertical-align: top;">Still no match. Keep advancing until
 *         there is a match...</td>
 *     </tr>
 *     <tr>
 *         <td style="vertical-align: top; write-space: nowrap;"><code>ade|fabcdefz</code></td>
 *         <td style="vertical-align: top;">...</td>
 *     </tr>
 *     <tr>
 *         <td style="vertical-align: top; write-space: nowrap;"><code>adef|abcdefz</code></td>
 *         <td style="vertical-align: top;">...</td>
 *     </tr>
 *     <tr>
 *         <td style="vertical-align: top; write-space: nowrap;"><code>adefa|bcdefz</code></td>
 *         <td style="vertical-align: top;">...</td>
 *     </tr>
 *     <tr>
 *         <td style="vertical-align: top; write-space: nowrap;"><code>adefab|cdefz</code></td>
 *         <td style="vertical-align: top;">...</td>
 *     </tr>
 *     <tr>
 *         <td style="vertical-align: top; write-space: nowrap;"><code>adefabc|defz</code></td>
 *         <td style="vertical-align: top;">Rule 1 matches; replace &quot;<code>def</code>&quot;
 *         with &quot;<code>xy</code>&quot; and back up the cursor
 *         to before the '<code>y</code>'.</td>
 *     </tr>
 *     <tr>
 *         <td style="vertical-align: top; write-space: nowrap;"><code>adefabcx|yz</code></td>
 *         <td style="vertical-align: top;">Although &quot;<code>xyz</code>&quot; is
 *         present, rule 2 does not match because the cursor is
 *         before the '<code>y</code>', not before the '<code>x</code>'.
 *         Rule 3 does match. Replace &quot;<code>yz</code>&quot;
 *         with &quot;<code>q</code>&quot;.</td>
 *     </tr>
 *     <tr>
 *         <td style="vertical-align: top; write-space: nowrap;"><code>adefabcxq|</code></td>
 *         <td style="vertical-align: top;">The cursor is at the end;
 *         transliteration is complete.</td>
 *     </tr>
 * </table>
 *
 * <p>The order of rules is significant. If multiple rules may match
 * at some point, the first matching rule is applied.
 *
 * <p>Forward and reverse rules may have an empty output string.
 * Otherwise, an empty left or right hand side of any statement is a
 * syntax error.
 *
 * <p>Single quotes are used to quote any character other than a
 * digit or letter. To specify a single quote itself, inside or
 * outside of quotes, use two single quotes in a row. For example,
 * the rule &quot;<code>'&gt;'&gt;o''clock</code>&quot; changes the
 * string &quot;<code>&gt;</code>&quot; to the string &quot;<code>o'clock</code>&quot;.
 *
 * <p><b>Notes</b>
 *
 * <p>While a Transliterator is being built from rules, it checks that
 * the rules are added in proper order. For example, if the rule
 * &quot;a&gt;x&quot; is followed by the rule &quot;ab&gt;y&quot;,
 * then the second rule will throw an exception. The reason is that
 * the second rule can never be triggered, since the first rule
 * always matches anything it matches. In other words, the first
 * rule <em>masks</em> the second rule.
 *
 * @author Alan Liu
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class Transliterator {

Transliterator() { throw new RuntimeException("Stub!"); }

/**
 * Transliterates a segment of a string, with optional filtering.
 *
 * @param text the string to be transliterated
 * @param start the beginning index, inclusive; <code>0 &lt;= start
 * &lt;= limit</code>.
 * @param limit the ending index, exclusive; <code>start &lt;= limit
 * &lt;= text.length()</code>.
 * @return The new limit index.  The text previously occupying <code>[start,
 * limit)</code> has been transliterated, possibly to a string of a different
 * length, at <code>[start, </code><em>new-limit</em><code>)</code>, where
 * <em>new-limit</em> is the return value. If the input offsets are out of bounds,
 * the returned value is -1 and the input string remains unchanged.
 */

public final int transliterate(android.icu.text.Replaceable text, int start, int limit) { throw new RuntimeException("Stub!"); }

/**
 * Transliterates an entire string in place. Convenience method.
 * @param text the string to be transliterated
 */

public final void transliterate(android.icu.text.Replaceable text) { throw new RuntimeException("Stub!"); }

/**
 * Transliterate an entire string and returns the result. Convenience method.
 *
 * @param text the string to be transliterated
 * @return The transliterated text
 */

public final java.lang.String transliterate(java.lang.String text) { throw new RuntimeException("Stub!"); }

/**
 * Transliterates the portion of the text buffer that can be
 * transliterated unambiguosly after new text has been inserted,
 * typically as a result of a keyboard event.  The new text in
 * <code>insertion</code> will be inserted into <code>text</code>
 * at <code>index.contextLimit</code>, advancing
 * <code>index.contextLimit</code> by <code>insertion.length()</code>.
 * Then the transliterator will try to transliterate characters of
 * <code>text</code> between <code>index.start</code> and
 * <code>index.contextLimit</code>.  Characters before
 * <code>index.start</code> will not be changed.
 *
 * <p>Upon return, values in <code>index</code> will be updated.
 * <code>index.contextStart</code> will be advanced to the first
 * character that future calls to this method will read.
 * <code>index.start</code> and <code>index.contextLimit</code> will
 * be adjusted to delimit the range of text that future calls to
 * this method may change.
 *
 * <p>Typical usage of this method begins with an initial call
 * with <code>index.contextStart</code> and <code>index.contextLimit</code>
 * set to indicate the portion of <code>text</code> to be
 * transliterated, and <code>index.start == index.contextStart</code>.
 * Thereafter, <code>index</code> can be used without
 * modification in future calls, provided that all changes to
 * <code>text</code> are made via this method.
 *
 * <p>This method assumes that future calls may be made that will
 * insert new text into the buffer.  As a result, it only performs
 * unambiguous transliterations.  After the last call to this
 * method, there may be untransliterated text that is waiting for
 * more input to resolve an ambiguity.  In order to perform these
 * pending transliterations, clients should call {@link
 * #finishTransliteration} after the last call to this
 * method has been made.
 *
 * @param text the buffer holding transliterated and untransliterated text
 * @param index the start and limit of the text, the position
 * of the cursor, and the start and limit of transliteration.
 * @param insertion text to be inserted and possibly
 * transliterated into the translation buffer at
 * <code>index.contextLimit</code>.  If <code>null</code> then no text
 * is inserted.
 * @exception java.lang.IllegalArgumentException if <code>index</code>
 * is invalid
 */

public final void transliterate(android.icu.text.Replaceable text, android.icu.text.Transliterator.Position index, java.lang.String insertion) { throw new RuntimeException("Stub!"); }

/**
 * Transliterates the portion of the text buffer that can be
 * transliterated unambiguosly after a new character has been
 * inserted, typically as a result of a keyboard event.  This is a
 * convenience method; see {@link #transliterate(android.icu.text.Replaceable,android.icu.text.Transliterator.Position,java.lang.String)} for details.
 * @param text the buffer holding transliterated and
 * untransliterated text
 * @param index the start and limit of the text, the position
 * of the cursor, and the start and limit of transliteration.
 * @param insertion text to be inserted and possibly
 * transliterated into the translation buffer at
 * <code>index.contextLimit</code>.
 * @see #transliterate(Replaceable, Transliterator.Position, String)
 */

public final void transliterate(android.icu.text.Replaceable text, android.icu.text.Transliterator.Position index, int insertion) { throw new RuntimeException("Stub!"); }

/**
 * Transliterates the portion of the text buffer that can be
 * transliterated unambiguosly.  This is a convenience method; see
 * {@link #transliterate(android.icu.text.Replaceable,android.icu.text.Transliterator.Position,java.lang.String)} for details.
 * @param text the buffer holding transliterated and
 * untransliterated text
 * @param index the start and limit of the text, the position
 * of the cursor, and the start and limit of transliteration.
 * @see #transliterate(Replaceable, Transliterator.Position, String)
 */

public final void transliterate(android.icu.text.Replaceable text, android.icu.text.Transliterator.Position index) { throw new RuntimeException("Stub!"); }

/**
 * Finishes any pending transliterations that were waiting for
 * more characters.  Clients should call this method as the last
 * call after a sequence of one or more calls to
 * <code>transliterate()</code>.
 * @param text the buffer holding transliterated and
 * untransliterated text.
 * @param index the array of indices previously passed to {@link
 * #transliterate}
 */

public final void finishTransliteration(android.icu.text.Replaceable text, android.icu.text.Transliterator.Position index) { throw new RuntimeException("Stub!"); }

/**
 * Transliterate a substring of text, as specified by index, taking filters
 * into account.  This method is for subclasses that need to delegate to
 * another transliterator.
 * @param text the text to be transliterated
 * @param index the position indices
 * @param incremental if TRUE, then assume more characters may be inserted
 * at index.limit, and postpone processing to accomodate future incoming
 * characters
 */

public void filteredTransliterate(android.icu.text.Replaceable text, android.icu.text.Transliterator.Position index, boolean incremental) { throw new RuntimeException("Stub!"); }

/**
 * Returns the length of the longest context required by this transliterator.
 * This is <em>preceding</em> context.  The default value is zero, but
 * subclasses can change this by calling <code>setMaximumContextLength()</code>.
 * For example, if a transliterator translates "ddd" (where
 * d is any digit) to "555" when preceded by "(ddd)", then the preceding
 * context length is 5, the length of "(ddd)".
 *
 * @return The maximum number of preceding context characters this
 * transliterator needs to examine
 */

public final int getMaximumContextLength() { throw new RuntimeException("Stub!"); }

/**
 * Returns a programmatic identifier for this transliterator.
 * If this identifier is passed to <code>getInstance()</code>, it
 * will return this object, if it has been registered.
 * @see #getAvailableIDs
 */

public final java.lang.String getID() { throw new RuntimeException("Stub!"); }

/**
 * Returns a name for this transliterator that is appropriate for
 * display to the user in the default <code>DISPLAY</code> locale.  See {@link
 * #getDisplayName(java.lang.String,java.util.Locale)} for details.
 * @see android.icu.util.ULocale.Category#DISPLAY
 */

public static final java.lang.String getDisplayName(java.lang.String ID) { throw new RuntimeException("Stub!"); }

/**
 * Returns a name for this transliterator that is appropriate for
 * display to the user in the given locale.  This name is taken
 * from the locale resource data in the standard manner of the
 * <code>java.text</code> package.
 *
 * <p>If no localized names exist in the system resource bundles,
 * a name is synthesized using a localized
 * <code>MessageFormat</code> pattern from the resource data.  The
 * arguments to this pattern are an integer followed by one or two
 * strings.  The integer is the number of strings, either 1 or 2.
 * The strings are formed by splitting the ID for this
 * transliterator at the first '-'.  If there is no '-', then the
 * entire ID forms the only string.
 * @param inLocale the Locale in which the display name should be
 * localized.
 * @see java.text.MessageFormat
 */

public static java.lang.String getDisplayName(java.lang.String id, java.util.Locale inLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns a name for this transliterator that is appropriate for
 * display to the user in the given locale.  This name is taken
 * from the locale resource data in the standard manner of the
 * <code>java.text</code> package.
 *
 * <p>If no localized names exist in the system resource bundles,
 * a name is synthesized using a localized
 * <code>MessageFormat</code> pattern from the resource data.  The
 * arguments to this pattern are an integer followed by one or two
 * strings.  The integer is the number of strings, either 1 or 2.
 * The strings are formed by splitting the ID for this
 * transliterator at the first '-'.  If there is no '-', then the
 * entire ID forms the only string.
 * @param inLocale the ULocale in which the display name should be
 * localized.
 * @see java.text.MessageFormat
 */

public static java.lang.String getDisplayName(java.lang.String id, android.icu.util.ULocale inLocale) { throw new RuntimeException("Stub!"); }

/**
 * Returns the filter used by this transliterator, or <tt>null</tt>
 * if this transliterator uses no filter.
 */

public final android.icu.text.UnicodeFilter getFilter() { throw new RuntimeException("Stub!"); }

/**
 * Changes the filter used by this transliterator.  If the filter
 * is set to <tt>null</tt> then no filtering will occur.
 *
 * <p>Callers must take care if a transliterator is in use by
 * multiple threads.  The filter should not be changed by one
 * thread while another thread may be transliterating.
 */

public void setFilter(android.icu.text.UnicodeFilter filter) { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>Transliterator</code> object given its ID.
 * The ID must be a system transliterator ID.
 *
 * @param ID a valid ID, as enumerated by <code>getAvailableIDs()</code>
 * @return A <code>Transliterator</code> object with the given ID
 * @exception java.lang.IllegalArgumentException if the given ID is invalid.
 */

public static final android.icu.text.Transliterator getInstance(java.lang.String ID) { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>Transliterator</code> object given its ID.
 * The ID must be a system transliterator ID.
 *
 * @param ID a valid ID, as enumerated by <code>getAvailableIDs()</code>
 * @param dir either FORWARD or REVERSE.  If REVERSE then the
 * inverse of the given ID is instantiated.
 * @return A <code>Transliterator</code> object with the given ID
 * @exception java.lang.IllegalArgumentException if the given ID is invalid.
 * @see #getAvailableIDs
 * @see #getID
 */

public static android.icu.text.Transliterator getInstance(java.lang.String ID, int dir) { throw new RuntimeException("Stub!"); }

/**
 * Returns a <code>Transliterator</code> object constructed from
 * the given rule string.  This will be a rule-based Transliterator,
 * if the rule string contains only rules, or a
 * compound Transliterator, if it contains ID blocks, or a
 * null Transliterator, if it contains ID blocks which parse as
 * empty for the given direction.
 *
 * @param ID the id for the transliterator.
 * @param rules rules, separated by ';'
 * @param dir either FORWARD or REVERSE.
 * @return a newly created Transliterator
 * @throws java.lang.IllegalArgumentException if there is a problem with the ID or the rules
 */

public static final android.icu.text.Transliterator createFromRules(java.lang.String ID, java.lang.String rules, int dir) { throw new RuntimeException("Stub!"); }

/**
 * Returns a rule string for this transliterator.
 * @param escapeUnprintable if true, then unprintable characters
 * will be converted to escape form backslash-'u' or
 * backslash-'U'.
 */

public java.lang.String toRules(boolean escapeUnprintable) { throw new RuntimeException("Stub!"); }

/**
 * Return the elements that make up this transliterator.  For
 * example, if the transliterator "NFD;Jamo-Latin;Latin-Greek"
 * were created, the return value of this method would be an array
 * of the three transliterator objects that make up that
 * transliterator: [NFD, Jamo-Latin, Latin-Greek].
 *
 * <p>If this transliterator is not composed of other
 * transliterators, then this method will return an array of
 * length one containing a reference to this transliterator.
 * @return an array of one or more transliterators that make up
 * this transliterator
 */

public android.icu.text.Transliterator[] getElements() { throw new RuntimeException("Stub!"); }

/**
 * Returns the set of all characters that may be modified in the
 * input text by this Transliterator.  This incorporates this
 * object's current filter; if the filter is changed, the return
 * value of this function will change.  The default implementation
 * returns an empty set. The return result is approximate in any case
 * and is intended for use by tests, tools, or utilities.
 * @see #getTargetSet
 */

public final android.icu.text.UnicodeSet getSourceSet() { throw new RuntimeException("Stub!"); }

/**
 * Returns the set of all characters that may be generated as
 * replacement text by this transliterator.  The default
 * implementation returns the empty set.  Some subclasses may
 * override this method to return a more precise result.  The
 * return result is approximate in any case and is intended for
 * use by tests, tools, or utilities requiring such
 * meta-information.
 * <p>Warning. You might expect an empty filter to always produce an empty target.
 * However, consider the following:
 * <pre>
 * [Pp]{}[\u03A3\u03C2\u03C3\u03F7\u03F8\u03FA\u03FB] &gt; \';
 * </pre>
 * With a filter of [], you still get some elements in the target set, because this rule will still match. It could
 * be recast to the following if it were important.
 * <pre>
 * [Pp]{([\u03A3\u03C2\u03C3\u03F7\u03F8\u03FA\u03FB])} &gt; \' | $1;
 * </pre>
 * @see #getTargetSet
 */

public android.icu.text.UnicodeSet getTargetSet() { throw new RuntimeException("Stub!"); }

/**
 * Returns this transliterator's inverse.  See the class
 * documentation for details.  This implementation simply inverts
 * the two entities in the ID and attempts to retrieve the
 * resulting transliterator.  That is, if <code>getID()</code>
 * returns "A-B", then this method will return the result of
 * <code>getInstance("B-A")</code>, or <code>null</code> if that
 * call fails.
 *
 * <p>Subclasses with knowledge of their inverse may wish to
 * override this method.
 *
 * @return a transliterator that is an inverse, not necessarily
 * exact, of this transliterator, or <code>null</code> if no such
 * transliterator is registered.
 */

public final android.icu.text.Transliterator getInverse() { throw new RuntimeException("Stub!"); }

/**
 * Returns an enumeration over the programmatic names of registered
 * <code>Transliterator</code> objects.  This includes both system
 * transliterators and user transliterators registered using
 * <code>registerClass()</code>.  The enumerated names may be
 * passed to <code>getInstance()</code>.
 *
 * @return An <code>Enumeration</code> over <code>String</code> objects
 * @see #getInstance
 */

public static final java.util.Enumeration<java.lang.String> getAvailableIDs() { throw new RuntimeException("Stub!"); }

/**
 * Returns an enumeration over the source names of registered
 * transliterators.  Source names may be passed to
 * getAvailableTargets() to obtain available targets for each
 * source.
 */

public static final java.util.Enumeration<java.lang.String> getAvailableSources() { throw new RuntimeException("Stub!"); }

/**
 * Returns an enumeration over the target names of registered
 * transliterators having a given source name.  Target names may
 * be passed to getAvailableVariants() to obtain available
 * variants for each source and target pair.
 */

public static final java.util.Enumeration<java.lang.String> getAvailableTargets(java.lang.String source) { throw new RuntimeException("Stub!"); }

/**
 * Returns an enumeration over the variant names of registered
 * transliterators having a given source name and target name.
 */

public static final java.util.Enumeration<java.lang.String> getAvailableVariants(java.lang.String source, java.lang.String target) { throw new RuntimeException("Stub!"); }

/**
 * Direction constant indicating the forward direction in a transliterator,
 * e.g., the forward rules of a rule-based Transliterator.  An "A-B"
 * transliterator transliterates A to B when operating in the forward
 * direction, and B to A when operating in the reverse direction.
 */

public static final int FORWARD = 0; // 0x0

/**
 * Direction constant indicating the reverse direction in a transliterator,
 * e.g., the reverse rules of a rule-based Transliterator.  An "A-B"
 * transliterator transliterates A to B when operating in the forward
 * direction, and B to A when operating in the reverse direction.
 */

public static final int REVERSE = 1; // 0x1
/**
 * Position structure for incremental transliteration.  This data
 * structure defines two substrings of the text being
 * transliterated.  The first region, [contextStart,
 * contextLimit), defines what characters the transliterator will
 * read as context.  The second region, [start, limit), defines
 * what characters will actually be transliterated.  The second
 * region should be a subset of the first.
 *
 * <p>After a transliteration operation, some of the indices in this
 * structure will be modified.  See the field descriptions for
 * details.
 *
 * <p>contextStart &lt;= start &lt;= limit &lt;= contextLimit
 *
 * <p>Note: All index values in this structure must be at code point
 * boundaries.  That is, none of them may occur between two code units
 * of a surrogate pair.  If any index does split a surrogate pair,
 * results are unspecified.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static class Position {

/**
 * Constructs a Position object with start, limit,
 * contextStart, and contextLimit all equal to zero.
 */

public Position() { throw new RuntimeException("Stub!"); }

/**
 * Constructs a Position object with the given start,
 * contextStart, and contextLimit.  The limit is set to the
 * contextLimit.
 */

public Position(int contextStart, int contextLimit, int start) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a Position object with the given start, limit,
 * contextStart, and contextLimit.
 */

public Position(int contextStart, int contextLimit, int start, int limit) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a Position object that is a copy of another.
 */

public Position(android.icu.text.Transliterator.Position pos) { throw new RuntimeException("Stub!"); }

/**
 * Copies the indices of this position from another.
 */

public void set(android.icu.text.Transliterator.Position pos) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if this Position is equal to the given object.
 */

public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Returns a string representation of this Position.
 * @return a string representation of the object.
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * Check all bounds.  If they are invalid, throw an exception.
 * @param length the length of the string this object applies to
 * @exception java.lang.IllegalArgumentException if any indices are out
 * of bounds
 */

public final void validate(int length) { throw new RuntimeException("Stub!"); }

/**
 * Ending index, exclusive, of the context to be considered for a
 * transliteration operation.  The transliterator will ignore
 * anything at or after this index.  INPUT/OUTPUT parameter: This
 * parameter is updated to reflect changes in the length of the
 * text, but points to the same logical position in the text.
 */

public int contextLimit;

/**
 * Beginning index, inclusive, of the context to be considered for
 * a transliteration operation.  The transliterator will ignore
 * anything before this index.  INPUT/OUTPUT parameter: This parameter
 * is updated by a transliteration operation to reflect the maximum
 * amount of antecontext needed by a transliterator.
 */

public int contextStart;

/**
 * Ending index, exclusive, of the text to be transliteratd.
 * INPUT/OUTPUT parameter: This parameter is updated to reflect
 * changes in the length of the text, but points to the same
 * logical position in the text.
 */

public int limit;

/**
 * Beginning index, inclusive, of the text to be transliteratd.
 * INPUT/OUTPUT parameter: This parameter is advanced past
 * characters that have already been transliterated by a
 * transliteration operation.
 */

public int start;
}

}

