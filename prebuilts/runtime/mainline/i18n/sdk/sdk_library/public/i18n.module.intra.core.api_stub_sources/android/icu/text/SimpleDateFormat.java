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
import android.icu.lang.UCharacter;
import android.icu.util.Calendar;
import java.util.Date;
import android.icu.util.TimeZone;
import java.text.Format;
import java.text.ParsePosition;
import android.icu.util.ULocale.Category;
import java.text.AttributedCharacterIterator;

/**
 * <strong>[icu enhancement]</strong> ICU's replacement for {@link java.text.SimpleDateFormat}.&nbsp;Methods, fields, and other functionality specific to ICU are labeled '<strong>[icu]</strong>'.
 *
 * <p><code>SimpleDateFormat</code> is a concrete class for formatting and
 * parsing dates in a locale-sensitive manner. It allows for formatting
 * (date -&gt; text), parsing (text -&gt; date), and normalization.
 *
 * <p>
 * <code>SimpleDateFormat</code> allows you to start by choosing
 * any user-defined patterns for date-time formatting. However, you
 * are encouraged to create a date-time formatter with either
 * <code>getTimeInstance</code>, <code>getDateInstance</code>, or
 * <code>getDateTimeInstance</code> in <code>DateFormat</code>. Each
 * of these class methods can return a date/time formatter initialized
 * with a default format pattern. You may modify the format pattern
 * using the <code>applyPattern</code> methods as desired.
 * For more information on using these methods, see
 * {@link android.icu.text.DateFormat DateFormat}.
 *
 * <p><strong>Date and Time Patterns:</strong></p>
 *
 * <p>Date and time formats are specified by <em>date and time pattern</em> strings.
 * Within date and time pattern strings, all unquoted ASCII letters [A-Za-z] are reserved
 * as pattern letters representing calendar fields. <code>SimpleDateFormat</code> supports
 * the date and time formatting algorithm and pattern letters defined by <a href="http://www.unicode.org/reports/tr35/">UTS#35
 * Unicode Locale Data Markup Language (LDML)</a>. The following pattern letters are
 * currently available (note that the actual values depend on CLDR and may change from the
 * examples shown here):</p>
 * <blockquote>
 * <table border="1">
 *     <tr>
 *         <th>Field</th>
 *         <th style="text-align: center">Sym.</th>
 *         <th style="text-align: center">No.</th>
 *         <th>Example</th>
 *         <th>Description</th>
 *     </tr>
 *     <tr>
 *         <th rowspan="3">era</th>
 *         <td style="text-align: center" rowspan="3">G</td>
 *         <td style="text-align: center">1..3</td>
 *         <td>AD</td>
 *         <td rowspan="3">Era - Replaced with the Era string for the current date. One to three letters for the
 *         abbreviated form, four letters for the long (wide) form, five for the narrow form.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">4</td>
 *         <td>Anno Domini</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">5</td>
 *         <td>A</td>
 *     </tr>
 *     <tr>
 *         <th rowspan="6">year</th>
 *         <td style="text-align: center">y</td>
 *         <td style="text-align: center">1..n</td>
 *         <td>1996</td>
 *         <td>Year. Normally the length specifies the padding, but for two letters it also specifies the maximum
 *         length. Example:<div style="text-align: center">
 *             <center>
 *             <table border="1" cellpadding="2" cellspacing="0">
 *                 <tr>
 *                     <th>Year</th>
 *                     <th style="text-align: right">y</th>
 *                     <th style="text-align: right">yy</th>
 *                     <th style="text-align: right">yyy</th>
 *                     <th style="text-align: right">yyyy</th>
 *                     <th style="text-align: right">yyyyy</th>
 *                 </tr>
 *                 <tr>
 *                     <td>AD 1</td>
 *                     <td style="text-align: right">1</td>
 *                     <td style="text-align: right">01</td>
 *                     <td style="text-align: right">001</td>
 *                     <td style="text-align: right">0001</td>
 *                     <td style="text-align: right">00001</td>
 *                 </tr>
 *                 <tr>
 *                     <td>AD 12</td>
 *                     <td style="text-align: right">12</td>
 *                     <td style="text-align: right">12</td>
 *                     <td style="text-align: right">012</td>
 *                     <td style="text-align: right">0012</td>
 *                     <td style="text-align: right">00012</td>
 *                 </tr>
 *                 <tr>
 *                     <td>AD 123</td>
 *                     <td style="text-align: right">123</td>
 *                     <td style="text-align: right">23</td>
 *                     <td style="text-align: right">123</td>
 *                     <td style="text-align: right">0123</td>
 *                     <td style="text-align: right">00123</td>
 *                 </tr>
 *                 <tr>
 *                     <td>AD 1234</td>
 *                     <td style="text-align: right">1234</td>
 *                     <td style="text-align: right">34</td>
 *                     <td style="text-align: right">1234</td>
 *                     <td style="text-align: right">1234</td>
 *                     <td style="text-align: right">01234</td>
 *                 </tr>
 *                 <tr>
 *                     <td>AD 12345</td>
 *                     <td style="text-align: right">12345</td>
 *                     <td style="text-align: right">45</td>
 *                     <td style="text-align: right">12345</td>
 *                     <td style="text-align: right">12345</td>
 *                     <td style="text-align: right">12345</td>
 *                 </tr>
 *             </table>
 *             </center></div>
 *         </td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">Y</td>
 *         <td style="text-align: center">1..n</td>
 *         <td>1997</td>
 *         <td>Year (in "Week of Year" based calendars). Normally the length specifies the padding,
 *         but for two letters it also specifies the maximum length. This year designation is used in ISO
 *         year-week calendar as defined by ISO 8601, but can be used in non-Gregorian based calendar systems
 *         where week date processing is desired. May not always be the same value as calendar year.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">u</td>
 *         <td style="text-align: center">1..n</td>
 *         <td>4601</td>
 *         <td>Extended year. This is a single number designating the year of this calendar system, encompassing
 *         all supra-year fields. For example, for the Julian calendar system, year numbers are positive, with an
 *         era of BCE or CE. An extended year value for the Julian calendar system assigns positive values to CE
 *         years and negative values to BCE years, with 1 BCE being year 0.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center" rowspan="3">U</td>
 *         <td style="text-align: center">1..3</td>
 *         <td>??</td>
 *         <td rowspan="3">Cyclic year name. Calendars such as the Chinese lunar calendar (and related calendars)
 *         and the Hindu calendars use 60-year cycles of year names. Use one through three letters for the abbreviated
 *         name, four for the full (wide) name, or five for the narrow name (currently the data only provides abbreviated names,
 *         which will be used for all requested name widths). If the calendar does not provide cyclic year name data,
 *         or if the year value to be formatted is out of the range of years for which cyclic name data is provided,
 *         then numeric formatting is used (behaves like 'y').</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">4</td>
 *         <td>(currently also ??)</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">5</td>
 *         <td>(currently also ??)</td>
 *     </tr>
 *     <tr>
 *         <th rowspan="6">quarter</th>
 *         <td rowspan="3" style="text-align: center">Q</td>
 *         <td style="text-align: center">1..2</td>
 *         <td>02</td>
 *         <td rowspan="3">Quarter - Use one or two for the numerical quarter, three for the abbreviation, or four
 *         for the full (wide) name (five for the narrow name is not yet supported).</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">3</td>
 *         <td>Q2</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">4</td>
 *         <td>2nd quarter</td>
 *     </tr>
 *     <tr>
 *         <td rowspan="3" style="text-align: center">q</td>
 *         <td style="text-align: center">1..2</td>
 *         <td>02</td>
 *         <td rowspan="3"><b>Stand-Alone</b> Quarter - Use one or two for the numerical quarter, three for the abbreviation,
 *         or four for the full name (five for the narrow name is not yet supported).</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">3</td>
 *         <td>Q2</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">4</td>
 *         <td>2nd quarter</td>
 *     </tr>
 *     <tr>
 *         <th rowspan="8">month</th>
 *         <td rowspan="4" style="text-align: center">M</td>
 *         <td style="text-align: center">1..2</td>
 *         <td>09</td>
 *         <td rowspan="4">Month - Use one or two for the numerical month, three for the abbreviation, four for
 *         the full (wide) name, or five for the narrow name. With two ("MM"), the month number is zero-padded
 *         if necessary (e.g. "08").</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">3</td>
 *         <td>Sep</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">4</td>
 *         <td>September</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">5</td>
 *         <td>S</td>
 *     </tr>
 *     <tr>
 *         <td rowspan="4" style="text-align: center">L</td>
 *         <td style="text-align: center">1..2</td>
 *         <td>09</td>
 *         <td rowspan="4"><b>Stand-Alone</b> Month - Use one or two for the numerical month, three for the abbreviation,
 *         four for the full (wide) name, or 5 for the narrow name. With two ("LL"), the month number is zero-padded if
 *         necessary (e.g. "08").</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">3</td>
 *         <td>Sep</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">4</td>
 *         <td>September</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">5</td>
 *         <td>S</td>
 *     </tr>
 *     <tr>
 *         <th rowspan="2">week</th>
 *         <td style="text-align: center">w</td>
 *         <td style="text-align: center">1..2</td>
 *         <td>27</td>
 *         <td>Week of Year. Use "w" to show the minimum number of digits, or "ww" to always show two digits
 *         (zero-padding if necessary, e.g. "08").</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">W</td>
 *         <td style="text-align: center">1</td>
 *         <td>3</td>
 *         <td>Week of Month</td>
 *     </tr>
 *     <tr>
 *         <th rowspan="4">day</th>
 *         <td style="text-align: center">d</td>
 *         <td style="text-align: center">1..2</td>
 *         <td>1</td>
 *         <td>Date - Day of the month. Use "d" to show the minimum number of digits, or "dd" to always show
 *         two digits (zero-padding if necessary, e.g. "08").</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">D</td>
 *         <td style="text-align: center">1..3</td>
 *         <td>345</td>
 *         <td>Day of year</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">F</td>
 *         <td style="text-align: center">1</td>
 *         <td>2</td>
 *         <td>Day of Week in Month. The example is for the 2nd Wed in July</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">g</td>
 *         <td style="text-align: center">1..n</td>
 *         <td>2451334</td>
 *         <td>Modified Julian day. This is different from the conventional Julian day number in two regards.
 *         First, it demarcates days at local zone midnight, rather than noon GMT. Second, it is a local number;
 *         that is, it depends on the local time zone. It can be thought of as a single number that encompasses
 *         all the date-related fields.</td>
 *     </tr>
 *     <tr>
 *         <th rowspan="14">week<br>
 *         day</th>
 *         <td rowspan="4" style="text-align: center">E</td>
 *         <td style="text-align: center">1..3</td>
 *         <td>Tue</td>
 *         <td rowspan="4">Day of week - Use one through three letters for the short day, four for the full (wide) name,
 *         five for the narrow name, or six for the short name.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">4</td>
 *         <td>Tuesday</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">5</td>
 *         <td>T</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">6</td>
 *         <td>Tu</td>
 *     </tr>
 *     <tr>
 *         <td rowspan="5" style="text-align: center">e</td>
 *         <td style="text-align: center">1..2</td>
 *         <td>2</td>
 *         <td rowspan="5">Local day of week. Same as E except adds a numeric value that will depend on the local
 *         starting day of the week, using one or two letters. For this example, Monday is the first day of the week.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">3</td>
 *         <td>Tue</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">4</td>
 *         <td>Tuesday</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">5</td>
 *         <td>T</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">6</td>
 *         <td>Tu</td>
 *     </tr>
 *     <tr>
 *         <td rowspan="5" style="text-align: center">c</td>
 *         <td style="text-align: center">1</td>
 *         <td>2</td>
 *         <td rowspan="5"><b>Stand-Alone</b> local day of week - Use one letter for the local numeric value (same
 *         as 'e'), three for the short day, four for the full (wide) name, five for the narrow name, or six for
 *         the short name.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">3</td>
 *         <td>Tue</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">4</td>
 *         <td>Tuesday</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">5</td>
 *         <td>T</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">6</td>
 *         <td>Tu</td>
 *     </tr>
 *     <tr>
 *         <th>period</th>
 *         <td style="text-align: center">a</td>
 *         <td style="text-align: center">1</td>
 *         <td>AM</td>
 *         <td>AM or PM</td>
 *     </tr>
 *     <tr>
 *         <th rowspan="4">hour</th>
 *         <td style="text-align: center">h</td>
 *         <td style="text-align: center">1..2</td>
 *         <td>11</td>
 *         <td>Hour [1-12]. When used in skeleton data or in a skeleton passed in an API for flexible data pattern
 *         generation, it should match the 12-hour-cycle format preferred by the locale (h or K); it should not match
 *         a 24-hour-cycle format (H or k). Use hh for zero padding.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">H</td>
 *         <td style="text-align: center">1..2</td>
 *         <td>13</td>
 *         <td>Hour [0-23]. When used in skeleton data or in a skeleton passed in an API for flexible data pattern
 *         generation, it should match the 24-hour-cycle format preferred by the locale (H or k); it should not match a
 *         12-hour-cycle format (h or K). Use HH for zero padding.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">K</td>
 *         <td style="text-align: center">1..2</td>
 *         <td>0</td>
 *         <td>Hour [0-11]. When used in a skeleton, only matches K or h, see above. Use KK for zero padding.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">k</td>
 *         <td style="text-align: center">1..2</td>
 *         <td>24</td>
 *         <td>Hour [1-24]. When used in a skeleton, only matches k or H, see above. Use kk for zero padding.</td>
 *     </tr>
 *     <tr>
 *         <th>minute</th>
 *         <td style="text-align: center">m</td>
 *         <td style="text-align: center">1..2</td>
 *         <td>59</td>
 *         <td>Minute. Use "m" to show the minimum number of digits, or "mm" to always show two digits
 *         (zero-padding if necessary, e.g. "08")..</td>
 *     </tr>
 *     <tr>
 *         <th rowspan="3">second</th>
 *         <td style="text-align: center">s</td>
 *         <td style="text-align: center">1..2</td>
 *         <td>12</td>
 *         <td>Second. Use "s" to show the minimum number of digits, or "ss" to always show two digits
 *         (zero-padding if necessary, e.g. "08").</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">S</td>
 *         <td style="text-align: center">1..n</td>
 *         <td>3450</td>
 *         <td>Fractional Second - truncates (like other time fields) to the count of letters when formatting. Appends zeros if more than 3 letters specified. Truncates at three significant digits when parsing.
 *         (example shows display using pattern SSSS for seconds value 12.34567)</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">A</td>
 *         <td style="text-align: center">1..n</td>
 *         <td>69540000</td>
 *         <td>Milliseconds in day. This field behaves <i>exactly</i> like a composite of all time-related fields,
 *         not including the zone fields. As such, it also reflects discontinuities of those fields on DST transition
 *         days. On a day of DST onset, it will jump forward. On a day of DST cessation, it will jump backward. This
 *         reflects the fact that is must be combined with the offset field to obtain a unique local time value.</td>
 *     </tr>
 *     <tr>
 *         <th rowspan="23">zone</th>
 *         <td rowspan="2" style="text-align: center">z</td>
 *         <td style="text-align: center">1..3</td>
 *         <td>PDT</td>
 *         <td>The <i>short specific non-location format</i>.
 *         Where that is unavailable, falls back to the <i>short localized GMT format</i> ("O").</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">4</td>
 *         <td>Pacific Daylight Time</td>
 *         <td>The <i>long specific non-location format</i>.
 *         Where that is unavailable, falls back to the <i>long localized GMT format</i> ("OOOO").</td>
 *     </tr>
 *     <tr>
 *         <td rowspan="3" style="text-align: center">Z</td>
 *         <td style="text-align: center">1..3</td>
 *         <td>-0800</td>
 *         <td>The <i>ISO8601 basic format</i> with hours, minutes and optional seconds fields.
 *         The format is equivalent to RFC 822 zone format (when optional seconds field is absent).
 *         This is equivalent to the "xxxx" specifier.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">4</td>
 *         <td>GMT-8:00</td>
 *         <td>The <i>long localized GMT format</i>.
 *         This is equivalent to the "OOOO" specifier.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">5</td>
 *         <td>-08:00<br>
 *         -07:52:58</td>
 *         <td>The <i>ISO8601 extended format</i> with hours, minutes and optional seconds fields.
 *         The ISO8601 UTC indicator "Z" is used when local time offset is 0.
 *         This is equivalent to the "XXXXX" specifier.</td>
 *     </tr>
 *     <tr>
 *         <td rowspan="2" style="text-align: center">O</td>
 *         <td style="text-align: center">1</td>
 *         <td>GMT-8</td>
 *         <td>The <i>short localized GMT format</i>.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">4</td>
 *         <td>GMT-08:00</td>
 *         <td>The <i>long localized GMT format</i>.</td>
 *     </tr>
 *     <tr>
 *         <td rowspan="2" style="text-align: center">v</td>
 *         <td style="text-align: center">1</td>
 *         <td>PT</td>
 *         <td>The <i>short generic non-location format</i>.
 *         Where that is unavailable, falls back to the <i>generic location format</i> ("VVVV"),
 *         then the <i>short localized GMT format</i> as the final fallback.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">4</td>
 *         <td>Pacific Time</td>
 *         <td>The <i>long generic non-location format</i>.
 *         Where that is unavailable, falls back to <i>generic location format</i> ("VVVV").
 *     </tr>
 *     <tr>
 *         <td rowspan="4" style="text-align: center">V</td>
 *         <td style="text-align: center">1</td>
 *         <td>uslax</td>
 *         <td>The short time zone ID.
 *         Where that is unavailable, the special short time zone ID <i>unk</i> (Unknown Zone) is used.<br>
 *         <i><b>Note</b>: This specifier was originally used for a variant of the short specific non-location format,
 *         but it was deprecated in the later version of the LDML specification. In CLDR 23/ICU 51, the definition of
 *         the specifier was changed to designate a short time zone ID.</i></td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">2</td>
 *         <td>America/Los_Angeles</td>
 *         <td>The long time zone ID.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">3</td>
 *         <td>Los Angeles</td>
 *         <td>The exemplar city (location) for the time zone.
 *         Where that is unavailable, the localized exemplar city name for the special zone <i>Etc/Unknown</i> is used
 *         as the fallback (for example, "Unknown City"). </td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">4</td>
 *         <td>Los Angeles Time</td>
 *         <td>The <i>generic location format</i>.
 *         Where that is unavailable, falls back to the <i>long localized GMT format</i> ("OOOO";
 *         Note: Fallback is only necessary with a GMT-style Time Zone ID, like Etc/GMT-830.)<br>
 *         This is especially useful when presenting possible timezone choices for user selection,
 *         since the naming is more uniform than the "v" format.</td>
 *     </tr>
 *     <tr>
 *         <td rowspan="5" style="text-align: center">X</td>
 *         <td style="text-align: center">1</td>
 *         <td>-08<br>
 *         +0530<br>
 *         Z</td>
 *         <td>The <i>ISO8601 basic format</i> with hours field and optional minutes field.
 *         The ISO8601 UTC indicator "Z" is used when local time offset is 0.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">2</td>
 *         <td>-0800<br>
 *         Z</td>
 *         <td>The <i>ISO8601 basic format</i> with hours and minutes fields.
 *         The ISO8601 UTC indicator "Z" is used when local time offset is 0.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">3</td>
 *         <td>-08:00<br>
 *         Z</td>
 *         <td>The <i>ISO8601 extended format</i> with hours and minutes fields.
 *         The ISO8601 UTC indicator "Z" is used when local time offset is 0.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">4</td>
 *         <td>-0800<br>
 *         -075258<br>
 *         Z</td>
 *         <td>The <i>ISO8601 basic format</i> with hours, minutes and optional seconds fields.
 *         (Note: The seconds field is not supported by the ISO8601 specification.)
 *         The ISO8601 UTC indicator "Z" is used when local time offset is 0.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">5</td>
 *         <td>-08:00<br>
 *         -07:52:58<br>
 *         Z</td>
 *         <td>The <i>ISO8601 extended format</i> with hours, minutes and optional seconds fields.
 *         (Note: The seconds field is not supported by the ISO8601 specification.)
 *         The ISO8601 UTC indicator "Z" is used when local time offset is 0.</td>
 *     </tr>
 *     <tr>
 *         <td rowspan="5" style="text-align: center">x</td>
 *         <td style="text-align: center">1</td>
 *         <td>-08<br>
 *         +0530</td>
 *         <td>The <i>ISO8601 basic format</i> with hours field and optional minutes field.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">2</td>
 *         <td>-0800</td>
 *         <td>The <i>ISO8601 basic format</i> with hours and minutes fields.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">3</td>
 *         <td>-08:00</td>
 *         <td>The <i>ISO8601 extended format</i> with hours and minutes fields.</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">4</td>
 *         <td>-0800<br>
 *         -075258</td>
 *         <td>The <i>ISO8601 basic format</i> with hours, minutes and optional seconds fields.
 *         (Note: The seconds field is not supported by the ISO8601 specification.)</td>
 *     </tr>
 *     <tr>
 *         <td style="text-align: center">5</td>
 *         <td>-08:00<br>
 *         -07:52:58</td>
 *         <td>The <i>ISO8601 extended format</i> with hours, minutes and optional seconds fields.
 *         (Note: The seconds field is not supported by the ISO8601 specification.)</td>
 *     </tr>
 * </table>
 *
 * </blockquote>
 * <p>
 * Any characters in the pattern that are not in the ranges of ['a'..'z']
 * and ['A'..'Z'] will be treated as quoted text. For instance, characters
 * like ':', '.', ' ', '#' and '@' will appear in the resulting time text
 * even they are not embraced within single quotes.
 * <p>
 * A pattern containing any invalid pattern letter will result in a thrown
 * exception during formatting or parsing.
 *
 * <p>
 * <strong>Examples Using the US Locale:</strong>
 * <blockquote>
 * <pre>
 * Format Pattern                         Result
 * --------------                         -------
 * "yyyy.MM.dd G 'at' HH:mm:ss vvvv" -&gt;&gt;  1996.07.10 AD at 15:08:56 Pacific Time
 * "EEE, MMM d, ''yy"                -&gt;&gt;  Wed, July 10, '96
 * "h:mm a"                          -&gt;&gt;  12:08 PM
 * "hh 'o''clock' a, zzzz"           -&gt;&gt;  12 o'clock PM, Pacific Daylight Time
 * "K:mm a, vvv"                     -&gt;&gt;  0:00 PM, PT
 * "yyyyy.MMMMM.dd GGG hh:mm aaa"    -&gt;&gt;  01996.July.10 AD 12:08 PM
 * </pre>
 * </blockquote>
 * <strong>Code Sample:</strong>
 * <blockquote>
 * <pre>
 * SimpleTimeZone pdt = new SimpleTimeZone(-8 * 60 * 60 * 1000, "PST");
 * pdt.setStartRule(Calendar.APRIL, 1, Calendar.SUNDAY, 2*60*60*1000);
 * pdt.setEndRule(Calendar.OCTOBER, -1, Calendar.SUNDAY, 2*60*60*1000);
 * <br>
 * // Format the current time.
 * SimpleDateFormat formatter
 *     = new SimpleDateFormat ("yyyy.MM.dd G 'at' hh:mm:ss a zzz");
 * Date currentTime_1 = new Date();
 * String dateString = formatter.format(currentTime_1);
 * <br>
 * // Parse the previous string back into a Date.
 * ParsePosition pos = new ParsePosition(0);
 * Date currentTime_2 = formatter.parse(dateString, pos);
 * </pre>
 * </blockquote>
 * In the example, the time value <code>currentTime_2</code> obtained from
 * parsing will be equal to <code>currentTime_1</code>. However, they may not be
 * equal if the am/pm marker 'a' is left out from the format pattern while
 * the "hour in am/pm" pattern symbol is used. This information loss can
 * happen when formatting the time in PM.
 *
 * <p>When parsing a date string using the abbreviated year pattern ("yy"),
 * SimpleDateFormat must interpret the abbreviated year
 * relative to some century.  It does this by adjusting dates to be
 * within 80 years before and 20 years after the time the SimpleDateFormat
 * instance is created. For example, using a pattern of "MM/dd/yy" and a
 * SimpleDateFormat instance created on Jan 1, 1997,  the string
 * "01/11/12" would be interpreted as Jan 11, 2012 while the string "05/04/64"
 * would be interpreted as May 4, 1964.
 * During parsing, only strings consisting of exactly two digits, as defined by
 * {@link android.icu.lang.UCharacter#isDigit(int)}, will be parsed into the default
 * century.
 * Any other numeric string, such as a one digit string, a three or more digit
 * string, or a two digit string that isn't all digits (for example, "-1"), is
 * interpreted literally.  So "01/02/3" or "01/02/003" are parsed, using the
 * same pattern, as Jan 2, 3 AD.  Likewise, "01/02/-3" is parsed as Jan 2, 4 BC.
 *
 * <p>If the year pattern does not have exactly two 'y' characters, the year is
 * interpreted literally, regardless of the number of digits.  So using the
 * pattern "MM/dd/yyyy", "01/11/12" parses to Jan 11, 12 A.D.
 *
 * <p>When numeric fields abut one another directly, with no intervening delimiter
 * characters, they constitute a run of abutting numeric fields.  Such runs are
 * parsed specially.  For example, the format "HHmmss" parses the input text
 * "123456" to 12:34:56, parses the input text "12345" to 1:23:45, and fails to
 * parse "1234".  In other words, the leftmost field of the run is flexible,
 * while the others keep a fixed width.  If the parse fails anywhere in the run,
 * then the leftmost field is shortened by one character, and the entire run is
 * parsed again. This is repeated until either the parse succeeds or the
 * leftmost field is one character in length.  If the parse still fails at that
 * point, the parse of the run fails.
 *
 * <p>For time zones that have no names, use strings GMT+hours:minutes or
 * GMT-hours:minutes.
 *
 * <p>The calendar defines what is the first day of the week, the first week
 * of the year, whether hours are zero based or not (0 vs 12 or 24), and the
 * time zone. There is one common decimal format to handle all the numbers;
 * the digit count is handled programmatically according to the pattern.
 *
 * <h3>Synchronization</h3>
 *
 * Date formats are not synchronized. It is recommended to create separate
 * format instances for each thread. If multiple threads access a format
 * concurrently, it must be synchronized externally.
 *
 * @see          android.icu.util.Calendar
 * @see          android.icu.util.GregorianCalendar
 * @see          android.icu.util.TimeZone
 * @see          android.icu.text.DateFormat
 * @see          android.icu.text.DateFormatSymbols
 * @see          android.icu.text.DecimalFormat
 * @see          android.icu.text.TimeZoneFormat
 * @author       Mark Davis, Chen-Lieh Huang, Alan Liu
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class SimpleDateFormat extends android.icu.text.DateFormat {

/**
 * Constructs a SimpleDateFormat using the default pattern for the default <code>FORMAT</code>
 * locale.  <b>Note:</b> Not all locales support SimpleDateFormat; for full
 * generality, use the factory methods in the DateFormat class.
 *
 * @see android.icu.text.DateFormat
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public SimpleDateFormat() { throw new RuntimeException("Stub!"); }

/**
 * Constructs a SimpleDateFormat using the given pattern in the default <code>FORMAT</code>
 * locale.  <b>Note:</b> Not all locales support SimpleDateFormat; for full
 * generality, use the factory methods in the DateFormat class.
 * @see android.icu.util.ULocale.Category#FORMAT
 */

public SimpleDateFormat(java.lang.String pattern) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a SimpleDateFormat using the given pattern and locale.
 * <b>Note:</b> Not all locales support SimpleDateFormat; for full
 * generality, use the factory methods in the DateFormat class.
 */

public SimpleDateFormat(java.lang.String pattern, java.util.Locale loc) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a SimpleDateFormat using the given pattern and locale.
 * <b>Note:</b> Not all locales support SimpleDateFormat; for full
 * generality, use the factory methods in the DateFormat class.
 */

public SimpleDateFormat(java.lang.String pattern, android.icu.util.ULocale loc) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a SimpleDateFormat using the given pattern , override and locale.
 * @param pattern The pattern to be used
 * @param override The override string.  A numbering system override string can take one of the following forms:
 *     1). If just a numbering system name is specified, it applies to all numeric fields in the date format pattern.
 *     2). To specify an alternate numbering system on a field by field basis, use the field letters from the pattern
 *         followed by an = sign, followed by the numbering system name.  For example, to specify that just the year
 *         be formatted using Hebrew digits, use the override "y=hebr".  Multiple overrides can be specified in a single
 *         string by separating them with a semi-colon. For example, the override string "m=thai;y=deva" would format using
 *         Thai digits for the month and Devanagari digits for the year.
 * @param loc The locale to be used
 */

public SimpleDateFormat(java.lang.String pattern, java.lang.String override, android.icu.util.ULocale loc) { throw new RuntimeException("Stub!"); }

/**
 * Constructs a SimpleDateFormat using the given pattern and
 * locale-specific symbol data.
 * Warning: uses default <code>FORMAT</code> locale for digits!
 */

public SimpleDateFormat(java.lang.String pattern, android.icu.text.DateFormatSymbols formatData) { throw new RuntimeException("Stub!"); }

/**
 * Sets the 100-year period 2-digit years will be interpreted as being in
 * to begin on the date the user specifies.
 * @param startDate During parsing, two digit years will be placed in the range
 * <code>startDate</code> to <code>startDate + 100 years</code>.
 */

public void set2DigitYearStart(java.util.Date startDate) { throw new RuntimeException("Stub!"); }

/**
 * Returns the beginning date of the 100-year period 2-digit years are interpreted
 * as being within.
 * @return the start of the 100-year period into which two digit years are
 * parsed
 */

public java.util.Date get2DigitYearStart() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Set a particular DisplayContext value in the formatter,
 * such as CAPITALIZATION_FOR_STANDALONE. Note: For getContext, see
 * DateFormat.
 *
 * @param context The DisplayContext value to set.
 */

public void setContext(android.icu.text.DisplayContext context) { throw new RuntimeException("Stub!"); }

/**
 * Formats a date or time, which is the standard millis
 * since January 1, 1970, 00:00:00 GMT.
 * <p>Example: using the US locale:
 * "yyyy.MM.dd G 'at' HH:mm:ss zzz" -&gt;&gt; 1996.07.10 AD at 15:08:56 PDT
 * @param cal the calendar whose date-time value is to be formatted into a date-time string
 * @param toAppendTo where the new date-time text is to be appended
 * @param pos the formatting position. On input: an alignment field,
 * if desired. On output: the offsets of the alignment field.
 * @return the formatted date-time string.
 * @see android.icu.text.DateFormat
 */

public java.lang.StringBuffer format(android.icu.util.Calendar cal, java.lang.StringBuffer toAppendTo, java.text.FieldPosition pos) { throw new RuntimeException("Stub!"); }

/**
 * Returns a DateFormat.Field constant associated with the specified format pattern
 * character.
 *
 * @param ch The pattern character
 * @return DateFormat.Field associated with the pattern character
 */

protected android.icu.text.DateFormat.Field patternCharToDateFormatField(char ch) { throw new RuntimeException("Stub!"); }

/**
 * Formats a single field, given its pattern character.  Subclasses may
 * override this method in order to modify or add formatting
 * capabilities.
 * @param ch the pattern character
 * @param count the number of times ch is repeated in the pattern
 * @param beginOffset the offset of the output string at the start of
 * this field; used to set pos when appropriate
 * @param pos receives the position of a field, when appropriate
 * @param fmtData the symbols for this formatter
 */

protected java.lang.String subFormat(char ch, int count, int beginOffset, java.text.FieldPosition pos, android.icu.text.DateFormatSymbols fmtData, android.icu.util.Calendar cal) throws java.lang.IllegalArgumentException { throw new RuntimeException("Stub!"); }

/**
 * Overrides superclass method and
 * This method also clears per field NumberFormat instances
 * previously set by {@link #setNumberFormat(java.lang.String,android.icu.text.NumberFormat)}
 */

public void setNumberFormat(android.icu.text.NumberFormat newNumberFormat) { throw new RuntimeException("Stub!"); }

/**
 * Formats a number with the specified minimum and maximum number of digits.
 */

protected java.lang.String zeroPaddingNumber(long value, int minDigits, int maxDigits) { throw new RuntimeException("Stub!"); }

/**
 * Overrides DateFormat
 * @see android.icu.text.DateFormat
 */

public void parse(java.lang.String text, android.icu.util.Calendar cal, java.text.ParsePosition parsePos) { throw new RuntimeException("Stub!"); }

/**
 * Attempt to match the text at a given position against an array of
 * strings.  Since multiple strings in the array may match (for
 * example, if the array contains "a", "ab", and "abc", all will match
 * the input string "abcd") the longest match is returned.  As a side
 * effect, the given field of <code>cal</code> is set to the index
 * of the best match, if there is one.
 * @param text the time text being parsed.
 * @param start where to start parsing.
 * @param field the date field being parsed.
 * @param data the string array to parsed.
 * @param cal
 * @return the new start position if matching succeeded; a negative
 * number indicating matching failure, otherwise.  As a side effect,
 * sets the <code>cal</code> field <code>field</code> to the index
 * of the best match, if matching succeeded.
 */

protected int matchString(java.lang.String text, int start, int field, java.lang.String[] data, android.icu.util.Calendar cal) { throw new RuntimeException("Stub!"); }

/**
 * Attempt to match the text at a given position against an array of quarter
 * strings.  Since multiple strings in the array may match (for
 * example, if the array contains "a", "ab", and "abc", all will match
 * the input string "abcd") the longest match is returned.  As a side
 * effect, the given field of <code>cal</code> is set to the index
 * of the best match, if there is one.
 * @param text the time text being parsed.
 * @param start where to start parsing.
 * @param field the date field being parsed.
 * @param data the string array to parsed.
 * @return the new start position if matching succeeded; a negative
 * number indicating matching failure, otherwise.  As a side effect,
 * sets the <code>cal</code> field <code>field</code> to the index
 * of the best match, if matching succeeded.
 */

protected int matchQuarterString(java.lang.String text, int start, int field, java.lang.String[] data, android.icu.util.Calendar cal) { throw new RuntimeException("Stub!"); }

/**
 * Protected method that converts one field of the input string into a
 * numeric field value in <code>cal</code>.  Returns -start (for
 * ParsePosition) if failed.  Subclasses may override this method to
 * modify or add parsing capabilities.
 * @param text the time text to be parsed.
 * @param start where to start parsing.
 * @param ch the pattern character for the date field text to be parsed.
 * @param count the count of a pattern character.
 * @param obeyCount if true, then the next field directly abuts this one,
 * and we should use the count to know when to stop parsing.
 * @param ambiguousYear return parameter; upon return, if ambiguousYear[0]
 * is true, then a two-digit year was parsed and may need to be readjusted.
 * @param cal
 * @return the new start position if matching succeeded; a negative
 * number indicating matching failure, otherwise.  As a side effect,
 * set the appropriate field of <code>cal</code> with the parsed
 * value.
 */

protected int subParse(java.lang.String text, int start, char ch, int count, boolean obeyCount, boolean allowNegative, boolean[] ambiguousYear, android.icu.util.Calendar cal) { throw new RuntimeException("Stub!"); }

/**
 * Return a pattern string describing this date format.
 */

public java.lang.String toPattern() { throw new RuntimeException("Stub!"); }

/**
 * Return a localized pattern string describing this date format.
 * <p>
 * <b>Note:</b> This implementation depends on {@link android.icu.text.DateFormatSymbols#getLocalPatternChars() DateFormatSymbols#getLocalPatternChars()}
 * to get localized format pattern characters. ICU does not include
 * localized pattern character data, therefore, unless user sets localized
 * pattern characters manually, this method returns the same result as
 * {@link #toPattern()}.
 */

public java.lang.String toLocalizedPattern() { throw new RuntimeException("Stub!"); }

/**
 * Apply the given unlocalized pattern string to this date format.
 */

public void applyPattern(java.lang.String pat) { throw new RuntimeException("Stub!"); }

/**
 * Apply the given localized pattern string to this date format.
 */

public void applyLocalizedPattern(java.lang.String pat) { throw new RuntimeException("Stub!"); }

/**
 * Gets the date/time formatting data.
 * @return a copy of the date-time formatting data associated
 * with this date-time formatter.
 */

public android.icu.text.DateFormatSymbols getDateFormatSymbols() { throw new RuntimeException("Stub!"); }

/**
 * Allows you to set the date/time formatting data.
 * @param newFormatSymbols the new symbols
 */

public void setDateFormatSymbols(android.icu.text.DateFormatSymbols newFormatSymbols) { throw new RuntimeException("Stub!"); }

/**
 * Method for subclasses to access the DateFormatSymbols.
 */

protected android.icu.text.DateFormatSymbols getSymbols() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Gets the time zone formatter which this date/time
 * formatter uses to format and parse a time zone.
 *
 * @return the time zone formatter which this date/time
 * formatter uses.
 */

public android.icu.text.TimeZoneFormat getTimeZoneFormat() { throw new RuntimeException("Stub!"); }

/**
 * <strong>[icu]</strong> Allows you to set the time zone formatter.
 *
 * @param tzfmt the new time zone formatter
 */

public void setTimeZoneFormat(android.icu.text.TimeZoneFormat tzfmt) { throw new RuntimeException("Stub!"); }

/**
 * Overrides Cloneable
 */

public java.lang.Object clone() { throw new RuntimeException("Stub!"); }

/**
 * Override hashCode.
 * Generates the hash code for the SimpleDateFormat object
 */

public int hashCode() { throw new RuntimeException("Stub!"); }

/**
 * Override equals.
 */

public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * Format the object to an attributed string, and return the corresponding iterator
 * Overrides superclass method.
 *
 * @param obj The object to format
 * @return <code>AttributedCharacterIterator</code> describing the formatted value.
 */

public java.text.AttributedCharacterIterator formatToCharacterIterator(java.lang.Object obj) { throw new RuntimeException("Stub!"); }

/**
 * allow the user to set the NumberFormat for several fields
 * It can be a single field like: "y"(year) or "M"(month)
 * It can be several field combined together: "yMd"(year, month and date)
 * Note:
 * 1 symbol field is enough for multiple symbol fields (so "y" will override "yy", "yyy")
 * If the field is not numeric, then override has no effect (like "MMM" will use abbreviation, not numerical field)
 *
 * @param fields the fields to override
 * @param overrideNF the NumbeferFormat used
 * @exception java.lang.IllegalArgumentException when the fields contain invalid field
 */

public void setNumberFormat(java.lang.String fields, android.icu.text.NumberFormat overrideNF) { throw new RuntimeException("Stub!"); }

/**
 * give the NumberFormat used for the field like 'y'(year) and 'M'(year)
 *
 * @param field the field the user wants
 * @return override NumberFormat used for the field
 */

public android.icu.text.NumberFormat getNumberFormat(char field) { throw new RuntimeException("Stub!"); }
}

