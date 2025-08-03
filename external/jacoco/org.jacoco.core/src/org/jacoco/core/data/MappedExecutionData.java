// BEGIN android-change
package org.jacoco.core.data;

import static java.lang.String.format;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

import org.jacoco.core.data.ExecutionDataWriter;

/**
 * Memory-mapped execution data implementation.
 *
 * Stores the probe data in a section of a memory-mapped file, so that it will be available without
 * requiring a flush of the coverage data.
 */
public final class MappedExecutionData implements IExecutionData {
	private final long id;
	private final String name;
	private final int probeCount;

	// Stores the memory-mapped byte buffer containing the probe data, packed as bits.
	private MappedByteBuffer probeBuffer;

	/**
	 * Creates the mapped execution data.
	 */
	public MappedExecutionData(
		final long id, final String name, final int probeCount, final FileChannel channel)
			throws IOException {
		this.id = id;
		this.name = name;
		this.probeCount = probeCount;

		createMemoryMappedProbeArray(id, name, probeCount, channel);
	}

	public MappedExecutionData(
			final long id,
			final String name,
			final boolean[] probes,
			final FileChannel channel) throws IOException {
		this.id = id;
		this.name = name;
		this.probeCount = probes.length;

		createMemoryMappedProbeArray(id, name, probes.length, channel);
		for (int i = 0; i < probes.length; i++) {
			if (probes[i]) {
				setProbe(i);
			}
		}
	}

	/**
	 * Maps a section of the already-opened file for the probe data.
	 *
	 * @param id  the class id
	 * @param name  the VM class name
	 * @param probeCount  the number of probes for this class
	 */
	private void createMemoryMappedProbeArray(
		final long id, final String name, final int probeCount, final FileChannel channel)
			throws IOException {
		synchronized (channel) {
			int byteCount = (probeCount + 7) / 8;

			// Write the ExecutionData block info.
			ByteBuffer execDataBuffer = ByteBuffer.allocate(11);
			execDataBuffer.put(ExecutionDataWriter.BLOCK_EXECUTIONDATA);
			execDataBuffer.putLong(id);
			execDataBuffer.putShort((short) name.length());
			execDataBuffer.flip();
			channel.write(execDataBuffer);
			channel.write(ByteBuffer.wrap(name.getBytes(Charset.forName("UTF-8"))));

			// Write the probe info and map part of this file for the probe data.
			channel.write(toVarIntByteBuffer(probeCount));
			probeBuffer = channel.map(FileChannel.MapMode.READ_WRITE, channel.position(), byteCount);
			channel.position(channel.position() + byteCount);
		}
	}

	/**
	 * Writes a variable-length int to a {@link ByteBuffer}.
	 *
	 * @param value  the value to write
	 * @return a ByteBuffer that can be used to write to a FileChannel containing the value
	 */
	private ByteBuffer toVarIntByteBuffer(int value) {
		ByteBuffer buffer = ByteBuffer.allocate(5);
		if (value == 0) {
			buffer.put((byte) 0);
		} else {
			while (value > 0) {
				if ((value & 0xFFFFFF80) == 0) {
					buffer.put((byte) value);
				} else {
					buffer.put((byte) (0x80 | (value & 0x7F)));
				}
				value >>>= 7;
			}
		}
		buffer.flip();
		return buffer;
	}

	/**
	 * Return the unique identifier for this class. The identifier is the CRC64
	 * checksum of the raw class file definition.
	 *
	 * @return class identifier
	 */
	public long getId() {
		return id;
	}

	/**
	 * The VM name of the class.
	 *
	 * @return VM name
	 */
	public String getName() {
		return name;
	}

	/**
	 * The number of instrumentation probes for this class.
	 *
	 * @return number of probes
	 */
	public int getProbeCount() {
		return probeCount;
	}

	/**
	 * Returns a copy of the probe data as a boolean array.
	 *
	 * Changes to the returned array will not be reflected in the execution data.
	 *
	 * @return copy of the probe data
	 */
	public boolean[] getProbesCopy() {
		final int bytes = (probeCount + 7) / 8;
		boolean[] probes = new boolean[probeCount];
		for (int index = 0; index < probeCount; index += 8) {
			byte byteProbe = probeBuffer.get(index / 8);
			for (int bit = 0; (bit < 8) && ((index + bit) < probeCount); bit++) {
				probes[index + bit] = ((byteProbe & 0x1) > 0);
				byteProbe >>>= 1;
			}
		}
		return probes;
	}

	/**
	 * Sets all probes to <code>false</code>.
	 */
	public void reset() {
		final int bytes = (probeCount + 7) / 8;
		synchronized (probeBuffer) {
			for (int i = 0; i < bytes; i++) {
				probeBuffer.put(i, (byte) 0);
			}
		}
	}

	/**
	 * Checks whether any probe has been hit.
	 *
	 * @return <code>true</code>, if at least one probe has been hit
	 */
	public boolean hasHits() {
		final int bytes = (probeCount + 7) / 8;
		synchronized (probeBuffer) {
			for (int i = 0; i < bytes; i++) {
				if (probeBuffer.get(i) > 0) {
					return true;
				}
			}
		}
		return false;
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
		merge(other, true);
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
	public void merge(final IExecutionData other, final boolean flag) {
		synchronized (probeBuffer) {
			for (int i = 0; i < probeCount; i++) {
				if (other.getProbe(i)) {
					setProbe(i);
				}
			}
		}
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
		if (this.id != id) {
			throw new IllegalStateException(format(
					"Different ids (%016x and %016x).", Long.valueOf(this.id),
					Long.valueOf(id)));
		}
		if (!this.name.equals(name)) {
			throw new IllegalStateException(format(
					"Different class names %s and %s for id %016x.", this.name,
					name, Long.valueOf(id)));
		}
		if (this.probeCount != probeCount) {
			throw new IllegalStateException(format(
					"Incompatible execution data for class %s with id %016x.",
					name, Long.valueOf(id)));
		}
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
		int offset = index / 8;
		int bit = 1 << (index % 8);
		return (probeBuffer.get(offset) & bit) != 0;
	}

	/**
	 * Sets the execution data probe at the given index to <code>true</code>.
	 *
	 * @param index  the probe's index to set
	 */
	public void setProbe(final int index) {
		int offset = index / 8;
		int bit = 1 << (index % 8);
		byte currentValue = probeBuffer.get(offset);
		if ((currentValue & bit) == 0) {
			synchronized (probeBuffer) {
				probeBuffer.put(offset, (byte) (probeBuffer.get(offset) | bit));
			}
		}
	}
}
// END android-change
