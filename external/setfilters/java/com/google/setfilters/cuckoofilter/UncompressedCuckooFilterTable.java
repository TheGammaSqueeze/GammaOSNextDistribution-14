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

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Random;

/**
 * Implementation of the {@link CuckooFilterTable} that doesn't use the semi-sorting bucket
 * compression scheme in the original paper by Fan et al
 * (https://www.cs.cmu.edu/~dga/papers/cuckoo-conext2014.pdf) - see section 5.2 for what
 * semi-sorting bucket compression scheme is.
 *
 * <p>Thus, if a bucket can hold up to bucketCapacity number of fingerprints and each fingerprint is
 * of length fingerprintLength bits, it takes bucketCapacity * fingerprintLength bits to represent
 * each bucket.
 */
final class UncompressedCuckooFilterTable implements CuckooFilterTable {
  // Implementation type of the table, to be encoded in the serialization.
  public static final int TABLE_TYPE = 0;

  private final CuckooFilterConfig.Size size;
  private final Random random;
  private final CuckooFilterArray cuckooFilterArray;

  /**
   * Creates a new uncompressed cuckoo filter table of the given size.
   *
   * <p>Uses the given source of {@code random} to choose the replaced fingerprint in {@code
   * insertWithReplacement} method.
   */
  public UncompressedCuckooFilterTable(CuckooFilterConfig.Size size, Random random) {
    this.size = size;
    this.random = random;
    // bucketCapacity <= 128 and fingerprintLength <= 64, so we can assume that it will always fit
    // into a long.
    cuckooFilterArray =
        new CuckooFilterArray(
            (long) size.bucketCount() * size.bucketCapacity(), size.fingerprintLength());
  }

  /** Creates {@link UncompressedCuckooFilterTable} from {@link SerializedCuckooFilterTable}. */
  public UncompressedCuckooFilterTable(
      CuckooFilterConfig.Size size, byte[] bitArray, Random random) {
    this.size = size;
    this.random = random;
    cuckooFilterArray =
        new CuckooFilterArray(
            (long) size.bucketCount() * size.bucketCapacity(), size.fingerprintLength(), bitArray);
  }

  @Override
  public Optional<Long> insertWithReplacement(int bucketIndex, long fingerprint) {
    for (int slotIndex = 0; slotIndex < size.bucketCapacity(); slotIndex++) {
      long arrayIndex = toArrayIndex(bucketIndex, slotIndex);
      if (cuckooFilterArray.getAsLong(arrayIndex) == CuckooFilterTable.EMPTY_SLOT) {
        cuckooFilterArray.set(arrayIndex, fingerprint);
        return Optional.empty();
      }
    }
    int replacedSlotIndex = random.nextInt(size.bucketCapacity());
    long replacedArrayIndex = toArrayIndex(bucketIndex, replacedSlotIndex);
    long replacedFingerprint = cuckooFilterArray.getAsLong(replacedArrayIndex);
    cuckooFilterArray.set(replacedArrayIndex, fingerprint);
    return Optional.of(replacedFingerprint);
  }

  @Override
  public boolean contains(int bucketIndex, long fingerprint) {
    for (int slotIndex = 0; slotIndex < size.bucketCapacity(); slotIndex++) {
      long arrayIndex = toArrayIndex(bucketIndex, slotIndex);
      if (cuckooFilterArray.getAsLong(arrayIndex) == fingerprint) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean delete(int bucketIndex, long fingerprint) {
    for (int slotIndex = 0; slotIndex < size.bucketCapacity(); slotIndex++) {
      long arrayIndex = toArrayIndex(bucketIndex, slotIndex);
      if (cuckooFilterArray.getAsLong(arrayIndex) == fingerprint) {
        cuckooFilterArray.set(arrayIndex, CuckooFilterTable.EMPTY_SLOT);
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isFull(int bucketIndex) {
    return !contains(bucketIndex, CuckooFilterTable.EMPTY_SLOT);
  }

  @Override
  public CuckooFilterConfig.Size size() {
    return size;
  }

  @Override
  public SerializedCuckooFilterTable serialize() {
    byte[] serializedArray = cuckooFilterArray.toByteArray();

    // The first 16 bytes specifies the implementation type and the size of the table (defined by
    // tuple (type, bucketCount,
    // bucketCapacity, fingerprintLength)).
    // Rest is the bit array.
    ByteBuffer encoded = ByteBuffer.allocate(16 + serializedArray.length);
    return SerializedCuckooFilterTable.createFromByteArray(
        encoded
            .putInt(TABLE_TYPE)
            .putInt(size.bucketCount())
            .putInt(size.bucketCapacity())
            .putInt(size.fingerprintLength())
            .put(serializedArray)
            .array());
  }

  private long toArrayIndex(int bucketIndex, int slotIndex) {
    return (long) bucketIndex * size.bucketCapacity() + slotIndex;
  }
}
