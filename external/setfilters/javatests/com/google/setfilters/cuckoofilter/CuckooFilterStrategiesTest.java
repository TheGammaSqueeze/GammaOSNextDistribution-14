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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.hash.HashCode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CuckooFilterStrategiesTest {

  private static final int FINGERPRINT_LENGTH = 16;
  private static final int MAX_FINGERPRINT_LENGTH = 64;
  private static final int BUCKET_COUNT = 100;

  @Test
  public void simpleModStrategy_computeFingerprint_zeroMapsToOne() {
    assertThat(
            CuckooFilterStrategies.SIMPLE_MOD.computeFingerprint(
                HashCode.fromLong(0L), FINGERPRINT_LENGTH))
        .isEqualTo(1L);
    assertThat(
            CuckooFilterStrategies.SIMPLE_MOD.computeFingerprint(
                HashCode.fromLong(1L << (FINGERPRINT_LENGTH + 1)), FINGERPRINT_LENGTH))
        .isEqualTo(1L);
    assertThat(
            CuckooFilterStrategies.SIMPLE_MOD.computeFingerprint(
                HashCode.fromLong(0L), MAX_FINGERPRINT_LENGTH))
        .isEqualTo(1L);
  }

  @Test
  public void simpleModStrategy_computeFingerprint_mostSignificantBits() {
    assertThat(
            CuckooFilterStrategies.SIMPLE_MOD.computeFingerprint(
                HashCode.fromLong(-1L), FINGERPRINT_LENGTH))
        .isEqualTo((1L << 16) - 1);
    assertThat(
            CuckooFilterStrategies.SIMPLE_MOD.computeFingerprint(
                HashCode.fromLong(-1L), MAX_FINGERPRINT_LENGTH))
        .isEqualTo(-1L);
  }

  @Test
  public void simpleModStrategy_computeBucketIndex_smallerThanDivisorStaysUnchanged() {
    assertThat(
            CuckooFilterStrategies.SIMPLE_MOD.computeBucketIndex(
                HashCode.fromLong(0L), BUCKET_COUNT))
        .isEqualTo(0);
    assertThat(
            CuckooFilterStrategies.SIMPLE_MOD.computeBucketIndex(
                HashCode.fromLong(99L), BUCKET_COUNT))
        .isEqualTo(99);
  }

  @Test
  public void simpleModStrategy_computeBucketIndex_largerThanDivisorUsesRemainder() {
    assertThat(
            CuckooFilterStrategies.SIMPLE_MOD.computeBucketIndex(
                HashCode.fromLong(100), BUCKET_COUNT))
        .isEqualTo(0);
    assertThat(
            CuckooFilterStrategies.SIMPLE_MOD.computeBucketIndex(
                HashCode.fromLong(199L), BUCKET_COUNT))
        .isEqualTo(99);
  }

  @Test
  public void simpleModStrategy_computeOtherBucketIndex_involution() {
    for (long fingerprint = 1; fingerprint < 1000; fingerprint += 10) {
      for (int bucketIndex = 0; bucketIndex < BUCKET_COUNT; bucketIndex++) {
        int otherBucketIndex =
            CuckooFilterStrategies.SIMPLE_MOD.computeOtherBucketIndex(
                fingerprint, bucketIndex, BUCKET_COUNT, CuckooFilterHashFunctions.MURMUR3_128);

        assertThat(otherBucketIndex).isAtLeast(0);
        assertThat(otherBucketIndex).isLessThan(BUCKET_COUNT);
        assertThat(
                CuckooFilterStrategies.SIMPLE_MOD.computeOtherBucketIndex(
                    fingerprint,
                    otherBucketIndex,
                    BUCKET_COUNT,
                    CuckooFilterHashFunctions.MURMUR3_128))
            .isEqualTo(bucketIndex);
      }
    }
  }
}
