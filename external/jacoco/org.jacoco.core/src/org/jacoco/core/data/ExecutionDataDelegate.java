// BEGIN android-change
package org.jacoco.core.data;

import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Class that delegates calls to {@link IExecutionData} interface methods to another instance.
 * Also has the ability to convert the underlying implementation to a {@link MappedExecutionData}
 * instance.
 */
public class ExecutionDataDelegate implements IExecutionData {
	private IExecutionData delegate;

	public ExecutionDataDelegate(
		final long id, final String name, final int probeCount, final FileChannel channel) {
		if ((channel != null) && canMapData(name)) {
			try {
				delegate = new MappedExecutionData(id, name, probeCount, channel);
			} catch (IOException e) {
				delegate = new ExecutionData(id, name, probeCount);
			}
		} else {
			delegate = new ExecutionData(id, name, probeCount);
		}
	}

	/**
	 * Converts the existing delegate into a {@link MappedExecutionData} instance if it is not
	 * already one, and copies any probe data into the new instance.
	 */
	public synchronized void convert(final FileChannel channel) throws IOException {
		if (!(delegate instanceof MappedExecutionData) && canMapData(getName())) {
			IExecutionData newDelegate = new MappedExecutionData(
				getId(), getName(), getProbeCount(), channel);
			newDelegate.merge(delegate);
			delegate = newDelegate;
		}
	}

	/**
	 * Determines if a class can be converted to memory-mapped.
	 */
	private static boolean canMapData(final String name) {
		if (name.startsWith("android/app/")
			|| name.startsWith("android/os/")
			|| name.startsWith("com/android/internal/util/")
			|| name.startsWith("java/")) {
			return false;
		}
		return true;
	}

	/**
	 * Return the unique identifier for this class. The identifier is the CRC64
	 * checksum of the raw class file definition.
	 *
	 * @return class identifier
	 */
	public long getId() {
		return delegate.getId();
	}

	/**
	 * The VM name of the class.
	 *
	 * @return VM name
	 */
	public String getName() {
		return delegate.getName();
	}

	/**
	 * The number of instrumentation probes for this class.
	 *
	 * @return number of probes
	 */
	public int getProbeCount() {
		return delegate.getProbeCount();
	}

	/**
	 * Returns a copy of the probe data as a boolean array.
	 *
	 * Changes to the returned array will not be reflected in the execution data.
	 *
	 * @return copy of the probe data
	 */
	public boolean[] getProbesCopy() {
		return delegate.getProbesCopy();
	}

	/**
	 * Sets all probes to <code>false</code>.
	 */
	public void reset() {
		delegate.reset();
	}

	/**
	 * Checks whether any probe has been hit.
	 *
	 * @return <code>true</code>, if at least one probe has been hit
	 */
	public boolean hasHits() {
		return delegate.hasHits();
	}

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
	public void merge(final IExecutionData other) {
		delegate.merge(other);
	}

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
	public void merge(final IExecutionData other, boolean flag) {
		delegate.merge(other, flag);
	}

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
	public void assertCompatibility(final long id, final String name, final int probeCount)
			throws IllegalStateException {
		delegate.assertCompatibility(id, name, probeCount);
	}

	/**
	 * Returns the execution data probe for a given index. A value of
	 * <code>true</code> indicates that the corresponding probe was
	 * executed.
	 *
	 * @param index  the probe's index to look up
	 *
	 * @return probe data
	 */
	public boolean getProbe(final int index) {
		return delegate.getProbe(index);
	}

	/**
	 * Sets the execution data probe at the given index to <code>true</code>.
	 *
	 * @param index  the probe's index to set
	 * @param value  the value to set the probe to
	 */
	public void setProbe(final int index) {
		delegate.setProbe(index);
	}
 }
 // END android-change
