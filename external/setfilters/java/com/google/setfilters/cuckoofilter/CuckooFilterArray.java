// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.setfilters.cuckoofilter;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Static array where each element is an integer of size {@code bitsPerElement} bits.
 *
 * <p>Supports up to 64 bits per element. This will be used internally by cuckoo filter.
 */
final class CuckooFilterArray {
  private final long length;
  private final int bitsPerElement;
  private final long[] bitArray;

  /**
   * Constructs a new cuckoo filter array with length {@code length}, with each element of length
   * {@code bitsPerElement} bits.
   *
   * @throws IllegalArgumentException if {@code length} <= 0 or {@code bitsPerElement} <= 0 or
   *     {@code bitsPerElement} > 64.
   */
  public CuckooFilterArray(long length, int bitsPerElement) {
    checkLengthIsValid(length);
    checkBitsPerElementIsValid(bitsPerElement);

    this.length = length;
    this.bitsPerElement = bitsPerElement;
    long totalBits = length * bitsPerElement;
    // ceil(totalBits / 64) number of elements.
    long longArrayLength = (totalBits + Long.SIZE - 1) / Long.SIZE;
    checkArgument(
        longArrayLength < Integer.MAX_VALUE,
        "Too large: could not create CuckooFilterArray with length %s and bitsPerElement %s.",
        length,
        bitsPerElement);
    bitArray = new long[(int) longArrayLength];
  }

  /**
   * Constructs a cuckoo filter array with length {@code length}, with each element of length {@code
   * bitsPerElement}, from {@code byteArray}.
   */
  public CuckooFilterArray(long length, int bitsPerElement, byte[] byteArray) {
    this(length, bitsPerElement);
    ByteBuffer buffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < bitArray.length; i++) {
      bitArray[i] = buffer.getLong();
    }
  }

  /** Returns the length of the array. */
  public long length() {
    return length;
  }

  /** Returns the number of bits per element. */
  public int bitsPerElement() {
    return bitsPerElement;
  }

  /**
   * Returns the element at the {@code index}th position as a long.
   *
   * <p>The lowest {@code bitsPerElement} bits will correspond to the value of the element.
   *
   * @throws IllegalArgumentException if {@code index} is out of bounds.
   */
  public long getAsLong(long index) {
    checkIndexOutOfBounds(index);
    long bitStart = index * bitsPerElement;
    long bitEnd = bitStart + bitsPerElement;
    int arrayIndex1 = (int) (bitStart / Long.SIZE);
    int arrayIndex2 = (int) ((bitEnd - 1) / Long.SIZE);

    int a = (int) (bitStart % Long.SIZE);
    // The element intersects the two array indices.
    if (arrayIndex1 < arrayIndex2) {
      int b = a + bitsPerElement - Long.SIZE;
      long value1 = bitArray[arrayIndex1] >>> a;
      long value2 = bitArray[arrayIndex2] & mask(b);
      return (value1 | (value2 << (Long.SIZE - a)));
    }
    // Element is contained in one array index.
    return (bitArray[arrayIndex1] >>> a) & mask(bitsPerElement);
  }

  /**
   * Sets the element at {@code index}th position as {@code value}, using the lowest {@code
   * bitsPerElement} bits as the value of the element.
   *
   * @throws IllegalArgumentException if {@code index} is out of bounds.
   */
  public void set(long index, long value) {
    checkIndexOutOfBounds(index);
    long bitStart = index * bitsPerElement;
    long bitEnd = bitStart + bitsPerElement;
    int arrayIndex1 = (int) (bitStart / Long.SIZE);
    int arrayIndex2 = (int) ((bitEnd - 1) / Long.SIZE);

    // Use the lowest bitsPerElement bits and clear all other bits.
    value &= mask(bitsPerElement);

    int a = (int) (bitStart % Long.SIZE);
    // The element intersects the two array indices.
    if (arrayIndex1 < arrayIndex2) {
      int b = a + bitsPerElement - Long.SIZE;
      bitArray[arrayIndex1] &= clearMask(Long.SIZE, a, Long.SIZE);
      bitArray[arrayIndex1] |= (value << a);
      bitArray[arrayIndex2] &= clearMask(Long.SIZE, 0, b);
      bitArray[arrayIndex2] |= (value >>> (Long.SIZE - a));
    } else {
      // Element is contained in one array index.
      int b = a + bitsPerElement;
      bitArray[arrayIndex1] &= clearMask(Long.SIZE, a, b);
      bitArray[arrayIndex1] |= (value << a);
    }
  }

  /** Returns byte array representation of the {@link CuckooFilterArray}. */
  public byte[] toByteArray() {
    byte[] byteArray = new byte[bitArray.length * Long.BYTES];
    for (int i = 0; i < bitArray.length; i++) {
      long value = bitArray[i];
      for (int j = 0; j < Long.BYTES; j++) {
        // Explicit conversion from long to byte will truncate to lowest 8 bits.
        byteArray[i * Long.BYTES + j] = (byte) value;
        value >>>= Byte.SIZE;
      }
    }
    return byteArray;
  }

  // Theoretical max size of a long array is Integer.MAX_VALUE. Assuming each element is 1 bit,
  // we can support up to Integer.MAX_VALUE * 64 number of elements.
  private void checkLengthIsValid(long length) {
    checkArgument(
        0 < length && length < (long) Integer.MAX_VALUE * Long.SIZE,
        "length must be in range (0, %s).",
        (long) Integer.MAX_VALUE * Long.SIZE);
  }

  private void checkBitsPerElementIsValid(int bitsPerElement) {
    checkArgument(
        0 < bitsPerElement && bitsPerElement <= 64, "bitsPerElement must be in range [1, 64].");
  }

  private void checkIndexOutOfBounds(long index) {
    checkArgument(0 <= index && index < length, "Index is out of bounds: %s.", index);
  }

  private static long mask(int length) {
    if (length == Long.SIZE) {
      // -1 in 2s complement is 0xFFFFFFFFFFFFFFFF.
      return -1;
    }
    return (1L << length) - 1;
  }

  // Mask for clearing bits in range [a, b).
  private static long clearMask(int length, int a, int b) {
    long mask1 = mask(length);
    long mask2 = mask(b - a);
    return mask1 ^ (mask2 << a);
  }
}
