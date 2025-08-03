/* GENERATED SOURCE. DO NOT MODIFY. */
// ? 2018 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.text;

import java.text.Format.Field;

/**
 * Represents a span of a string containing a given field.
 *
 * This class differs from FieldPosition in the following ways:
 *
 *   1. It has information on the field category.
 *   2. It allows you to set constraints to use when iterating over field positions.
 *   3. It is used for the newer FormattedValue APIs.
 *
 * @author sffc
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class ConstrainedFieldPosition {

/**
 * Initializes a CategoryFieldPosition.
 *
 * By default, the CategoryFieldPosition has no iteration constraints.
 */

public ConstrainedFieldPosition() { throw new RuntimeException("Stub!"); }

/**
 * Resets this ConstrainedFieldPosition to its initial state, as if it were newly created:
 *
 * - Removes any constraints that may have been set on the instance.
 * - Resets the iteration position.
 */

public void reset() { throw new RuntimeException("Stub!"); }

/**
 * Sets a constraint on the field.
 *
 * When this instance of ConstrainedFieldPosition is passed to {@link android.icu.text.FormattedValue#nextPosition FormattedValue#nextPosition}, positions are
 * skipped unless they have the given field.
 *
 * Any previously set constraints are cleared.
 *
 * For example, to loop over all grouping separators:
 *
 * <pre>
 * ConstrainedFieldPosition cfpos;
 * cfpos.constrainField(NumberFormat.Field.GROUPING_SEPARATOR);
 * while (fmtval.nextPosition(cfpos)) {
 *   // handle the grouping separator position
 * }
 * </pre>
 *
 * Changing the constraint while in the middle of iterating over a FormattedValue
 * does not generally have well-defined behavior.
 *
 * @param field
 *            The field to fix when iterating.
 */

public void constrainField(java.text.Format.Field field) { throw new RuntimeException("Stub!"); }

/**
 * Sets a constraint on the field class.
 *
 * When this instance of ConstrainedFieldPosition is passed to {@link android.icu.text.FormattedValue#nextPosition FormattedValue#nextPosition}, positions are
 * skipped unless the field is an instance of the class constraint, including subclasses.
 *
 * Any previously set constraints are cleared.
 *
 * For example, to loop over only the number-related fields:
 *
 * <pre>
 * ConstrainedFieldPosition cfpos;
 * cfpos.constrainClass(NumberFormat.Field.class);
 * while (fmtval.nextPosition(cfpos)) {
 *   // handle the number-related field position
 * }
 * </pre>
 *
 * @param classConstraint
 *            The field class to fix when iterating.
 */

public void constrainClass(java.lang.Class<?> classConstraint) { throw new RuntimeException("Stub!"); }

/**
 * Gets the field for the current position.
 *
 * The return value is well-defined and non-null only after
 * FormattedValue#nextPosition returns TRUE.
 *
 * @return The field saved in the instance. See above for null conditions.
 */

public java.text.Format.Field getField() { throw new RuntimeException("Stub!"); }

/**
 * Gets the INCLUSIVE start index for the current position.
 *
 * The return value is well-defined only after FormattedValue#nextPosition returns TRUE.
 *
 * @return The start index saved in the instance.
 */

public int getStart() { throw new RuntimeException("Stub!"); }

/**
 * Gets the EXCLUSIVE end index stored for the current position.
 *
 * The return value is well-defined only after FormattedValue#nextPosition returns TRUE.
 *
 * @return The end index saved in the instance.
 */

public int getLimit() { throw new RuntimeException("Stub!"); }

/**
 * Gets the value associated with the current field position. The field value is often not set.
 *
 * The return value is well-defined only after FormattedValue#nextPosition returns TRUE.
 *
 * @return The value for the current position. Might be null.
 */

public java.lang.Object getFieldValue() { throw new RuntimeException("Stub!"); }

/**
 * Gets an int64 that FormattedValue implementations may use for storage.
 *
 * The initial value is zero.
 *
 * Users of FormattedValue should not need to call this method.
 *
 * @return The current iteration context from {@link #setInt64IterationContext}.
 */

public long getInt64IterationContext() { throw new RuntimeException("Stub!"); }

/**
 * Sets an int64 that FormattedValue implementations may use for storage.
 *
 * Intended to be used by FormattedValue implementations.
 *
 * @param context
 *            The new iteration context.
 */

public void setInt64IterationContext(long context) { throw new RuntimeException("Stub!"); }

/**
 * Sets new values for the primary public getters.
 *
 * Intended to be used by FormattedValue implementations.
 *
 * It is up to the implementation to ensure that the user-requested
 * constraints are satisfied. This method does not check!
 *
 * @param field
 *            The new field.
 * @param value
 *            The new field value. Should be null if there is no value.
 * @param start
 *            The new inclusive start index.
 * @param limit
 *            The new exclusive end index.
 */

public void setState(java.text.Format.Field field, java.lang.Object value, int start, int limit) { throw new RuntimeException("Stub!"); }

/**
 * Determines whether a given field and value should be included given the
 * constraints.
 *
 * Intended to be used by FormattedValue implementations.
 *
 * @param field The field to test.
 * @param fieldValue The field value to test. Should be null if there is no value.
 * @return Whether the field should be included given the constraints.
 */

public boolean matchesField(java.text.Format.Field field, java.lang.Object fieldValue) { throw new RuntimeException("Stub!"); }

/**
 * {@inheritDoc}
 */

public java.lang.String toString() { throw new RuntimeException("Stub!"); }
}

