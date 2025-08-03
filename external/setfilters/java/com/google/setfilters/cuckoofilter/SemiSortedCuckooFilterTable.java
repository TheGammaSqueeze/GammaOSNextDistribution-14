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
import static java.util.Comparator.comparingInt;

import com.google.common.collect.ImmutableMap;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

/**
 * Implementation of the {@link CuckooFilterTable} using the semi-sorting bucket compression scheme
 * in the original paper by Fan et al (https://www.cs.cmu.edu/~dga/papers/cuckoo-conext2014.pdf) -
 * see section 5.2.
 *
 * <p>The main idea behind the compression algorithm is that the order of the fingerprints in each
 * bucket is irrelevant - that is, the fingerprints in each bucket forms a multiset. For fingerprint
 * length f and bucket capacity b, the possible number of multisets of b fingerprints of f bits each
 * is given by C(2^f + b - 1, b), where C denotes binomial coefficient. In particular, we can encode
 * each bucket with ceil(log2(C(2^f + b - 1, b))) bits. On the other hand, naively encoding the
 * fingerprints will take b * f bits. Thus, it is theoretically possible to save b * f -
 * ceil(log2(C(2^f + b - 1, b))) bits per bucket (note that this is not information theoretically
 * tight because the distribution of the multisets is not uniform).
 *
 * <p>For performance reason, this only supports a table with bucket capacity of size 4 and
 * fingerprint length >= 4 - in many cases this is not a limitation because, for many practical
 * applications, bucket capacity of size 4 yields the optimal cuckoo filter size and fingerprint
 * length < 4 will never achieve good enough false positive rate.
 *
 * <p>Compared to the {@link UncompressedCuckooFilterTable}, this implementation can save 1 bit per
 * element, at the cost of slower filter operations by a constant factor (asymptotically, it is the
 * same as the uncompressed one). Note that for bucket capacity of size 4, saving 1 bit per element
 * is "optimal" up to rounding down, as the function 4 * f - ceil(log2(C(2^f + 3, 4))) < 5 for
 * reasonable values of f. However, this also incurs an additional fixed space overhead, so for
 * smaller filter the extra saving of 1 bit per element may not be worth it.
 */
final class SemiSortedCuckooFilterTable implements CuckooFilterTable {
  // Implementation type of the table, to be encoded in the serialization.
  public static final int TABLE_TYPE = 1;

  // Table containing all sorted 4 bit partial fingerprints of length 4 (16 bits) by its index.
  private static final short[] SORTED_PARTIAL_FINGERPRINTS = computeSortedPartialFingerprints();
  // Inverse map of SORTED_PARTIAL_FINGERPRINTS.
  private static final ImmutableMap<Short, Short> SORTED_PARTIAL_FINGERPRINTS_INDEX =
      computeSortedPartialFingerprintsIndex(SORTED_PARTIAL_FINGERPRINTS);

  private final CuckooFilterConfig.Size size;
  private final Random random;
  private final CuckooFilterArray cuckooFilterArray;

  /**
   * Creates a new uncompressed cuckoo filter table of the given size.
   *
   * <p>Uses the given source of {@code random} to choose the replaced fingerprint in {@code
   * insertWithReplacement} method.
   */
  public SemiSortedCuckooFilterTable(CuckooFilterConfig.Size size, Random random) {
    this.size = size;
    checkArgument(
        size.bucketCapacity() == 4,
        "SemiSortedCuckooFilterTable only supports bucket capacity of 4.");
    checkArgument(
        size.fingerprintLength() >= 4,
        "SemiSortedCuckooFilterTable only supports fingerprint length >= 4.");
    this.random = random;
    // bucketCapacity == 4 and fingerprintLength <= 64, so we can assume that it will always fit
    // into a long.
    cuckooFilterArray =
        new CuckooFilterArray(
            (long) size.bucketCount() * size.bucketCapacity(), size.fingerprintLength() - 1);
  }

  /** Creates {@link SemiSortedCuckooFilterTable} from {@link SerializedCuckooFilterTable}. */
  public SemiSortedCuckooFilterTable(CuckooFilterConfig.Size size, byte[] bitArray, Random random) {
    this.size = size;
    this.random = random;
    cuckooFilterArray =
        new CuckooFilterArray(
            (long) size.bucketCount() * size.bucketCapacity(),
            size.fingerprintLength() - 1,
            bitArray);
  }

  @Override
  public Optional<Long> insertWithReplacement(int bucketIndex, long fingerprint) {
    long[] fingerprints = decodeBucket(bucketIndex);
    for (int i = 0; i < size.bucketCapacity(); i++) {
      if (fingerprints[i] == EMPTY_SLOT) {
        fingerprints[i] = fingerprint;
        encodeAndPut(bucketIndex, fingerprints);
        return Optional.empty();
      }
    }

    int replacedSlotIndex = random.nextInt(size.bucketCapacity());
    long replacedFingerprint = fingerprints[replacedSlotIndex];
    fingerprints[replacedSlotIndex] = fingerprint;
    encodeAndPut(bucketIndex, fingerprints);
    return Optional.of(replacedFingerprint);
  }

  @Override
  public boolean contains(int bucketIndex, long fingerprint) {
    long[] fingerprints = decodeBucket(bucketIndex);
    for (long fingerprintInBucket : fingerprints) {
      if (fingerprintInBucket == fingerprint) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean delete(int bucketIndex, long fingerprint) {
    long[] fingerprints = decodeBucket(bucketIndex);
    for (int i = 0; i < fingerprints.length; i++) {
      if (fingerprints[i] == fingerprint) {
        fingerprints[i] = EMPTY_SLOT;
        encodeAndPut(bucketIndex, fingerprints);
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

  // TODO: Check if encoding/decoding needs to be optimized.

  // Decodes fingerprints at bucketIndex.
  private long[] decodeBucket(int bucketIndex) {
    int encodedSortedPartialFingerintsIndex = 0;
    long[] fingerprintPrefixes = new long[size.bucketCapacity()];
    for (int i = 0; i < size.bucketCapacity(); i++) {
      long arrayIndex = toArrayIndex(bucketIndex, i);
      long n = cuckooFilterArray.getAsLong(arrayIndex);
      encodedSortedPartialFingerintsIndex <<= 3;
      encodedSortedPartialFingerintsIndex |= (int) (n & 0x7);
      fingerprintPrefixes[i] = n >>> 3;
    }

    int encodedSortedPartialFingerprints =
        SORTED_PARTIAL_FINGERPRINTS[encodedSortedPartialFingerintsIndex];
    long[] fingerprints = new long[size.bucketCapacity()];
    for (int i = size.bucketCapacity() - 1; i >= 0; i--) {
      fingerprints[i] = (fingerprintPrefixes[i] << 4) | (encodedSortedPartialFingerprints & 0xF);
      encodedSortedPartialFingerprints >>>= 4;
    }
    return fingerprints;
  }

  /**
   * Encode fingerprints and put them to bucketIndex.
   *
   * <p>Encoding works as follows.
   *
   * <p>Suppose each fingerprint is logically f bits. First, sort the fingerprints by the least
   * significant 4 bits. Let's call the most significant f - 4 bits of the fingerprints as the
   * fingerprint prefixes. The least significant 4 bits of the fingerprints will be the partial
   * fingerprints, which will be encoded according to the SORTED_PARTIAL_FINGEPRRINTS_INDEX map as a
   * 12 bit value. Partition the encoded 12 bit value into four 3 bit chunks. Group each of the f -
   * 4 bit prefixes with each 3 bit chunk (f - 1 bits total) and insert it as a cuckoo filter array
   * element.
   */
  private void encodeAndPut(int bucketIndex, long[] fingerprints) {
    long[] fingerprintPrefixes = new long[size.bucketCapacity()];
    int[] partialFingerprints = new int[size.bucketCapacity()];
    for (int i = 0; i < size.bucketCapacity(); i++) {
      fingerprintPrefixes[i] = fingerprints[i] >>> 4;
      partialFingerprints[i] = (int) (fingerprints[i] & 0xF);
    }
    Integer[] indices = {0, 1, 2, 3};
    Arrays.sort(indices, comparingInt((Integer i) -> partialFingerprints[i]));
    short encodedSortedPartialFingerprints =
        (short)
            ((partialFingerprints[indices[0]] << 12)
                | (partialFingerprints[indices[1]] << 8)
                | (partialFingerprints[indices[2]] << 4)
                | partialFingerprints[indices[3]]);
    int encodedSortedPartialFingerprintsIndex =
        SORTED_PARTIAL_FINGERPRINTS_INDEX.get(encodedSortedPartialFingerprints);
    for (int i = size.bucketCapacity() - 1; i >= 0; i--) {
      long arrayIndex = toArrayIndex(bucketIndex, i);
      cuckooFilterArray.set(
          arrayIndex,
          (fingerprintPrefixes[indices[i]] << 3) | (encodedSortedPartialFingerprintsIndex & 0x7));
      encodedSortedPartialFingerprintsIndex >>>= 3;
    }
  }

  private static short[] computeSortedPartialFingerprints() {
    // (2^4 + 3 choose 4) = 3876 counts the number of multisets of size 4, with each element in
    // [0, 16).
    short[] sortedPartialFingerprints = new short[3876];

    final short fingerprintUpperBound = 16;

    int i = 0;
    for (short a = 0; a < fingerprintUpperBound; a++) {
      for (short b = a; b < fingerprintUpperBound; b++) {
        for (short c = b; c < fingerprintUpperBound; c++) {
          for (short d = c; d < fingerprintUpperBound; d++) {
            sortedPartialFingerprints[i] = (short) ((a << 12) | (b << 8) | (c << 4) | d);
            i++;
          }
        }
      }
    }
    return sortedPartialFingerprints;
  }

  private static ImmutableMap<Short, Short> computeSortedPartialFingerprintsIndex(
      short[] sortedPartialFingerprints) {
    ImmutableMap.Builder<Short, Short> map = ImmutableMap.builder();
    for (short i = 0; i < sortedPartialFingerprints.length; i++) {
      map.put(sortedPartialFingerprints[i], i);
    }
    return map.buildOrThrow();
  }
}
