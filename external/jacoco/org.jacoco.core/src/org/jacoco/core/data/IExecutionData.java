// BEGIN android-change
package org.jacoco.core.data;

/**
 * Interface for interacting with execution data for a single Java class.
 */
public interface IExecutionData {

	/**
	 * Return the unique identifier for this class. The identifier is the CRC64
	 * checksum of the raw class file definition.
	 * 
	 * @return class identifier
	 */
	public abstract long getId();

	/**
	 * The VM name of the class.
	 * 
	 * @return VM name
	 */
	public abstract String getName();

	/**
	 * The number of instrumentation probes for this class.
	 * 
	 * @return number of probes
	 */
	public abstract int getProbeCount();

	/**
	 * Returns a copy of the probe data as a boolean array.
	 *
	 * Changes to the returned array will not be reflected in the execution data.
	 * 
	 * @return copy of the probe data
	 */
	public abstract boolean[] getProbesCopy();

	/**
	 * Sets all probes to <code>false</code>.
	 */
	public abstract void reset();

	/**
	 * Checks whether any probe has been hit.
	 * 
	 * @return <code>true</code>, if at least one probe has been hit
	 */
	public abstract boolean hasHits();

	/**
	 * Merges the given execution data into the probe data of this object. I.e.
	 * a probe entry in this object is marked as executed (<code>true</code>) if
	 * this probe or the corresponding other probe was executed. So the result
	 * is
	 * 
	 * <pre>
	 * A or B
	 * </pre>
	 * 
	 * The probe array of the other object is not modified.
	 * 
	 * @param other
	 *            execution data to merge
	 */
	public abstract void merge(final IExecutionData other);

	/**
	 * Merges the given execution data into the probe data of this object. A
	 * probe in this object is set to the value of <code>flag</code> if the
	 * corresponding other probe was executed. For <code>flag==true</code> this
	 * corresponds to
	 * 
	 * <pre>
	 * A or B
	 * </pre>
	 * 
	 * For <code>flag==false</code> this can be considered as a subtraction
	 * 
	 * <pre>
	 * A and not B
	 * </pre>
	 * 
	 * The probe array of the other object is not modified.
	 * 
	 * @param other
	 *            execution data to merge
	 * @param flag
	 *            merge mode
	 */
	public abstract void merge(final IExecutionData other, boolean flag);

	/**
	 * Asserts that this execution data object is compatible with the given
	 * parameters. The purpose of this check is to detect a very unlikely class
	 * id collision.
	 * 
	 * @param id
	 *            other class id, must be the same
	 * @param name
	 *            other name, must be equal to this name
	 * @param probecount
	 *            probe data length, must be the same as for this data
	 * @throws IllegalStateException
	 *             if the given parameters do not match this instance
	 */
	public abstract void assertCompatibility(final long id, final String name, final int probeCount) throws IllegalStateException;

	/**
	 * Returns the execution data probe for a given index. A value of
	 * <code>true</code> indicates that the corresponding probe was
	 * executed.
	 *
	 * @param index  the probe's index to look up
	 * 
	 * @return probe data
	 */
	public abstract boolean getProbe(final int index);

	/**
	 * Sets the execution data probe at the given index to <code>true</code>.
	 *
	 * @param index  the probe's index to set
	 * @param value  the value to set the probe to
	 */
	public abstract void setProbe(final int index);
}
// END android-change
