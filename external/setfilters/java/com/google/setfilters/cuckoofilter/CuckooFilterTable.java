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

/** An array of buckets where each bucket can store a fixed number of fingerprints. */
interface CuckooFilterTable {
  /** Value of the empty "slot", which is reserved as 0. */
  public static long EMPTY_SLOT = 0L;

  /**
   * Creates an implementation of an empty cuckoo filter based on whether space optimization should
   * be used.
   *
   * <p>Space optimization is best effort, and is not guaranteed.
   */
  public static CuckooFilterTable create(
      CuckooFilterConfig.Size size, boolean useSpaceOptimization, Random random) {
    if (useSpaceOptimization && size.bucketCapacity() == 4 && size.fingerprintLength() >= 4) {
      return new SemiSortedCuckooFilterTable(size, random);
    }
    return new UncompressedCuckooFilterTable(size, random);
  }

  /** Creates an implementation of the cuckoo filter based on the serialization. */
  public static CuckooFilterTable createFromSerialization(
      SerializedCuckooFilterTable serializedTable, Random random) {
    ByteBuffer buffer = ByteBuffer.wrap(serializedTable.asByteArray());

    if (buffer.remaining() <= 16) {
      throw new IllegalArgumentException("Unable to parse the SerializedCuckooFilterTable.");
    }

    int tableType = buffer.getInt();
    int bucketCount = buffer.getInt();
    int bucketCapacity = buffer.getInt();
    int fingerprintLength = buffer.getInt();
    CuckooFilterConfig.Size size =
        CuckooFilterConfig.Size.newBuilder()
            .setBucketCount(bucketCount)
            .setBucketCapacity(bucketCapacity)
            .setFingerprintLength(fingerprintLength)
            .build();

    byte[] bitArray = new byte[buffer.remaining()];
    buffer.get(bitArray);

    if (tableType == UncompressedCuckooFilterTable.TABLE_TYPE) {
      return new UncompressedCuckooFilterTable(size, bitArray, random);
    } else if (tableType == SemiSortedCuckooFilterTable.TABLE_TYPE) {
      return new SemiSortedCuckooFilterTable(size, bitArray, random);
    } else {
      throw new IllegalArgumentException("Unable to parse the SerializedCuckooFilterTable.");
    }
  }

  /**
   * Inserts given {@code fingerprint} to the {@code bucketIndex}th bucket, replacing an arbitrary
   * fingerprint if the bucket is full.
   *
   * <p>How this arbitrary fingerprint is chosen depends on the implementation.
   *
   * @return the value of the replaced fingerprint if the bucket is full, and an empty {@link
   *     Optional} otherwise.
   */
  Optional<Long> insertWithReplacement(int bucketIndex, long fingerprint);

  /** Returns whether {@code bucketIndex}th bucket contains {@code fingerprint}. */
  boolean contains(int bucketIndex, long fingerprint);

  /**
   * Deletes a {@code fingerprint} from {@code bucketIndex}th bucket.
   *
   * <p>If a bucket contains multiple {@code fingerprint} values, this method only deletes one.
   *
   * @return {@code true} if {@code fingerprint} is in {@code bucketIndex}th bucket and is deleted,
   *     and {@code false} otherwise.
   */
  boolean delete(int bucketIndex, long fingerprint);

  /** Returns whether {@code bucketIndex}th bucket is full. */
  boolean isFull(int bucketIndex);

  /** Returns the size of {@link CuckooFilterTable}. */
  CuckooFilterConfig.Size size();

  /** Returns serialization of {@link CuckooFilterTable}. */
  SerializedCuckooFilterTable serialize();

  // TODO: Add more methods as needed.
}
