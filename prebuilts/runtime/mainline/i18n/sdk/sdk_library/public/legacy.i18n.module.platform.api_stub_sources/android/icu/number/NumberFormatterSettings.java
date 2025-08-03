/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.number;

import android.icu.util.MeasureUnit;
import android.icu.util.Currency;
import android.icu.util.Measure;
import android.icu.number.NumberFormatter.GroupingStrategy;
import android.icu.util.ULocale;
import android.icu.text.DecimalFormatSymbols;
import android.icu.text.NumberingSystem;
import android.icu.number.NumberFormatter.UnitWidth;
import android.icu.number.NumberFormatter.SignDisplay;
import android.icu.number.NumberFormatter.DecimalSeparatorDisplay;

/**
 * An abstract base class for specifying settings related to number formatting. This class is implemented
 * by {@link android.icu.number.UnlocalizedNumberFormatter UnlocalizedNumberFormatter} and {@link android.icu.number.LocalizedNumberFormatter LocalizedNumberFormatter}. This class is not intended
 * for public subclassing.
 *
 * @see android.icu.number.NumberFormatter
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class NumberFormatterSettings<T extends android.icu.number.NumberFormatterSettings<?>> {

NumberFormatterSettings() { throw new RuntimeException("Stub!"); }

/**
 * Specifies the notation style (simple, scientific, or compact) for rendering numbers.
 *
 * <ul>
 * <li>Simple notation: "12,300"
 * <li>Scientific notation: "1.23E4"
 * <li>Compact notation: "12K"
 * </ul>
 *
 * <p>
 * All notation styles will be properly localized with locale data, and all notation styles are
 * compatible with units, rounding strategies, and other number formatter settings.
 *
 * <p>
 * Pass this method the return value of a {@link android.icu.number.Notation Notation} factory method. For example:
 *
 * <pre>
 * NumberFormatter.with().notation(Notation.compactShort())
 * </pre>
 *
 * The default is to use simple notation.
 *
 * @param notation
 *            The notation strategy to use.
 * @return The fluent chain.
 * @see android.icu.number.Notation
 */

public T notation(android.icu.number.Notation notation) { throw new RuntimeException("Stub!"); }

/**
 * Specifies the unit (unit of measure, currency, or percent) to associate with rendered numbers.
 *
 * <ul>
 * <li>Unit of measure: "12.3 meters"
 * <li>Currency: "$12.30"
 * <li>Percent: "12.3%"
 * </ul>
 *
 * <p>
 * <strong>Note:</strong> The unit can also be specified by passing a {@link android.icu.util.Measure Measure} to
 * {@link android.icu.number.LocalizedNumberFormatter#format(android.icu.util.Measure) LocalizedNumberFormatter#format(Measure)}. Units specified via the format method take
 * precedence over units specified here. This setter is designed for situations when the unit is
 * constant for the duration of the number formatting process.
 *
 * <p>
 * All units will be properly localized with locale data, and all units are compatible with notation
 * styles, rounding strategies, and other number formatter settings.
 *
 * <p>
 * Pass this method any instance of {@link android.icu.util.MeasureUnit MeasureUnit}. For units of measure:
 *
 * <pre>
 * NumberFormatter.with().unit(MeasureUnit.METER)
 * </pre>
 *
 * Currency:
 *
 * <pre>
 * NumberFormatter.with().unit(Currency.getInstance("USD"))
 * </pre>
 *
 * <p>
 * See {@link #perUnit} for information on how to format strings like "5 meters per second".
 *
 * <p>
 * If the input usage is correctly set the output unit <b>will change</b>
 * according to `usage`, `locale` and `unit` value.
 * </p>
 *
 * @param unit
 *            The unit to render.
 * @return The fluent chain.
 * @see android.icu.util.MeasureUnit
 * @see android.icu.util.Currency
 * @see #perUnit
 */

public T unit(android.icu.util.MeasureUnit unit) { throw new RuntimeException("Stub!"); }

/**
 * Sets a unit to be used in the denominator. For example, to format "3 m/s", pass METER to the unit
 * and SECOND to the perUnit.
 *
 * <p>
 * Pass this method any instance of {@link android.icu.util.MeasureUnit MeasureUnit}. For example:
 *
 * <pre>
 * NumberFormatter.with().unit(MeasureUnit.METER).perUnit(MeasureUnit.SECOND)
 * </pre>
 *
 * <p>
 * The default is not to display any unit in the denominator.
 *
 * <p>
 * If a per-unit is specified without a primary unit via {@link #unit}, the behavior is undefined.
 *
 * @param perUnit
 *            The unit to render in the denominator.
 * @return The fluent chain
 * @see #unit
 */

public T perUnit(android.icu.util.MeasureUnit perUnit) { throw new RuntimeException("Stub!"); }

/**
 * Specifies the rounding precision to use when formatting numbers.
 *
 * <ul>
 * <li>Round to 3 decimal places: "3.142"
 * <li>Round to 3 significant figures: "3.14"
 * <li>Round to the closest nickel: "3.15"
 * <li>Do not perform rounding: "3.1415926..."
 * </ul>
 *
 * <p>
 * Pass this method the return value of one of the factory methods on {@link android.icu.number.Precision Precision}. For example:
 *
 * <pre>
 * NumberFormatter.with().precision(Precision.fixedFraction(2))
 * </pre>
 *
 * <p>
 * In most cases, the default rounding precision is to round to 6 fraction places; i.e.,
 * <code>Precision.maxFraction(6)</code>. The exceptions are if compact notation is being used, then
 * the compact notation rounding precision is used (see {@link android.icu.number.Notation#compactShort Notation#compactShort} for details), or
 * if the unit is a currency, then standard currency rounding is used, which varies from currency to
 * currency (see {@link android.icu.number.Precision#currency Precision#currency} for details).
 *
 * @param precision
 *            The rounding precision to use.
 * @return The fluent chain.
 * @see android.icu.number.Precision
 */

public T precision(android.icu.number.Precision precision) { throw new RuntimeException("Stub!"); }

/**
 * Specifies how to determine the direction to round a number when it has more digits than fit in the
 * desired precision.  When formatting 1.235:
 *
 * <ul>
 * <li>Ceiling rounding mode with integer precision: "2"
 * <li>Half-down rounding mode with 2 fixed fraction digits: "1.23"
 * <li>Half-up rounding mode with 2 fixed fraction digits: "1.24"
 * </ul>
 *
 * The default is HALF_EVEN. For more information on rounding mode, see the ICU userguide here:
 *
 * https://unicode-org.github.io/icu/userguide/format_parse/numbers/rounding-modes
 *
 * @param roundingMode
 *            The rounding mode to use.
 * @return The fluent chain.
 * @see android.icu.number.Precision
 */

public T roundingMode(java.math.RoundingMode roundingMode) { throw new RuntimeException("Stub!"); }

/**
 * Specifies the grouping strategy to use when formatting numbers.
 *
 * <ul>
 * <li>Default grouping: "12,300" and "1,230"
 * <li>Grouping with at least 2 digits: "12,300" and "1230"
 * <li>No grouping: "12300" and "1230"
 * </ul>
 *
 * <p>
 * The exact grouping widths will be chosen based on the locale.
 *
 * <p>
 * Pass this method an element from the {@link android.icu.number.NumberFormatter.GroupingStrategy GroupingStrategy} enum. For example:
 *
 * <pre>
 * NumberFormatter.with().grouping(GroupingStrategy.MIN2)
 * </pre>
 *
 * The default is to perform grouping according to locale data; most locales, but not all locales,
 * enable it by default.
 *
 * @param strategy
 *            The grouping strategy to use.
 * @return The fluent chain.
 * @see android.icu.number.NumberFormatter.GroupingStrategy
 */

public T grouping(android.icu.number.NumberFormatter.GroupingStrategy strategy) { throw new RuntimeException("Stub!"); }

/**
 * Specifies the minimum and maximum number of digits to render before the decimal mark.
 *
 * <ul>
 * <li>Zero minimum integer digits: ".08"
 * <li>One minimum integer digit: "0.08"
 * <li>Two minimum integer digits: "00.08"
 * </ul>
 *
 * <p>
 * Pass this method the return value of {@link android.icu.number.IntegerWidth#zeroFillTo(int) IntegerWidth#zeroFillTo(int)}. For example:
 *
 * <pre>
 * NumberFormatter.with().integerWidth(IntegerWidth.zeroFillTo(2))
 * </pre>
 *
 * The default is to have one minimum integer digit.
 *
 * @param style
 *            The integer width to use.
 * @return The fluent chain.
 * @see android.icu.number.IntegerWidth
 */

public T integerWidth(android.icu.number.IntegerWidth style) { throw new RuntimeException("Stub!"); }

/**
 * Specifies the symbols (decimal separator, grouping separator, percent sign, numerals, etc.) to use
 * when rendering numbers.
 *
 * <ul>
 * <li><em>en_US</em> symbols: "12,345.67"
 * <li><em>fr_FR</em> symbols: "12&nbsp;345,67"
 * <li><em>de_CH</em> symbols: "12?345.67"
 * <li><em>my_MY</em> symbols: "??,???.??"
 * </ul>
 *
 * <p>
 * Pass this method an instance of {@link android.icu.text.DecimalFormatSymbols DecimalFormatSymbols}. For example:
 *
 * <pre>
 * NumberFormatter.with().symbols(DecimalFormatSymbols.getInstance(new ULocale("de_CH")))
 * </pre>
 *
 * <p>
 * <strong>Note:</strong> DecimalFormatSymbols automatically chooses the best numbering system based
 * on the locale. In the examples above, the first three are using the Latin numbering system, and
 * the fourth is using the Myanmar numbering system.
 *
 * <p>
 * <strong>Note:</strong> The instance of DecimalFormatSymbols will be copied: changes made to the
 * symbols object after passing it into the fluent chain will not be seen.
 *
 * <p>
 * <strong>Note:</strong> Calling this method will override the NumberingSystem previously specified
 * in {@link #symbols(android.icu.text.NumberingSystem)}.
 *
 * <p>
 * The default is to choose the symbols based on the locale specified in the fluent chain.
 *
 * @param symbols
 *            The DecimalFormatSymbols to use.
 * @return The fluent chain.
 * @see android.icu.text.DecimalFormatSymbols
 */

public T symbols(android.icu.text.DecimalFormatSymbols symbols) { throw new RuntimeException("Stub!"); }

/**
 * Specifies that the given numbering system should be used when fetching symbols.
 *
 * <ul>
 * <li>Latin numbering system: "12,345"
 * <li>Myanmar numbering system: "??,???"
 * <li>Math Sans Bold numbering system: "??,???"
 * </ul>
 *
 * <p>
 * Pass this method an instance of {@link android.icu.text.NumberingSystem NumberingSystem}. For example, to force the locale to
 * always use the Latin alphabet numbering system (ASCII digits):
 *
 * <pre>
 * NumberFormatter.with().symbols(NumberingSystem.LATIN)
 * </pre>
 *
 * <p>
 * <strong>Note:</strong> Calling this method will override the DecimalFormatSymbols previously
 * specified in {@link #symbols(android.icu.text.DecimalFormatSymbols)}.
 *
 * <p>
 * The default is to choose the best numbering system for the locale.
 *
 * @param ns
 *            The NumberingSystem to use.
 * @return The fluent chain.
 * @see android.icu.text.NumberingSystem
 */

public T symbols(android.icu.text.NumberingSystem ns) { throw new RuntimeException("Stub!"); }

/**
 * Sets the width of the unit (measure unit or currency). Most common values:
 *
 * <ul>
 * <li>Short: "$12.00", "12 m"
 * <li>ISO Code: "USD 12.00"
 * <li>Full name: "12.00 US dollars", "12 meters"
 * </ul>
 *
 * <p>
 * Pass an element from the {@link android.icu.number.NumberFormatter.UnitWidth UnitWidth} enum to this setter. For example:
 *
 * <pre>
 * NumberFormatter.with().unitWidth(UnitWidth.FULL_NAME)
 * </pre>
 *
 * <p>
 * The default is the SHORT width.
 *
 * @param style
 *            The width to use when rendering numbers.
 * @return The fluent chain
 * @see android.icu.number.NumberFormatter.UnitWidth
 */

public T unitWidth(android.icu.number.NumberFormatter.UnitWidth style) { throw new RuntimeException("Stub!"); }

/**
 * Sets the plus/minus sign display strategy. Most common values:
 *
 * <ul>
 * <li>Auto: "123", "-123"
 * <li>Always: "+123", "-123"
 * <li>Accounting: "$123", "($123)"
 * </ul>
 *
 * <p>
 * Pass an element from the {@link android.icu.number.NumberFormatter.SignDisplay SignDisplay} enum to this setter. For example:
 *
 * <pre>
 * NumberFormatter.with().sign(SignDisplay.ALWAYS)
 * </pre>
 *
 * <p>
 * The default is AUTO sign display.
 *
 * @param style
 *            The sign display strategy to use when rendering numbers.
 * @return The fluent chain
 * @see android.icu.number.NumberFormatter.SignDisplay
 */

public T sign(android.icu.number.NumberFormatter.SignDisplay style) { throw new RuntimeException("Stub!"); }

/**
 * Sets the decimal separator display strategy. This affects integer numbers with no fraction part.
 * Most common values:
 *
 * <ul>
 * <li>Auto: "1"
 * <li>Always: "1."
 * </ul>
 *
 * <p>
 * Pass an element from the {@link android.icu.number.NumberFormatter.DecimalSeparatorDisplay DecimalSeparatorDisplay} enum to this setter. For example:
 *
 * <pre>
 * NumberFormatter.with().decimal(DecimalSeparatorDisplay.ALWAYS)
 * </pre>
 *
 * <p>
 * The default is AUTO decimal separator display.
 *
 * @param style
 *            The decimal separator display strategy to use when rendering numbers.
 * @return The fluent chain
 * @see android.icu.number.NumberFormatter.DecimalSeparatorDisplay
 */

public T decimal(android.icu.number.NumberFormatter.DecimalSeparatorDisplay style) { throw new RuntimeException("Stub!"); }

/**
 * Sets a scale (multiplier) to be used to scale the number by an arbitrary amount before formatting.
 * Most common values:
 *
 * <ul>
 * <li>Multiply by 100: useful for percentages.
 * <li>Multiply by an arbitrary value: useful for unit conversions.
 * </ul>
 *
 * <p>
 * Pass an element from a {@link android.icu.number.Scale Scale} factory method to this setter. For example:
 *
 * <pre>
 * NumberFormatter.with().scale(Scale.powerOfTen(2))
 * </pre>
 *
 * <p>
 * The default is to not apply any multiplier.
 *
 * @param scale
 *            An amount to be multiplied against numbers before formatting.
 * @return The fluent chain
 * @see android.icu.number.Scale
 */

public T scale(android.icu.number.Scale scale) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public boolean equals(java.lang.Object other) { throw new RuntimeException("Stub!"); }
}

