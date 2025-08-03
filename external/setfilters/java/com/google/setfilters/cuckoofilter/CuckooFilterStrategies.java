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

import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;

/** A set of predefined {@link CuckooFilterConfig.Strategy}s. */
public enum CuckooFilterStrategies implements CuckooFilterConfig.Strategy {

  /**
   * A strategy that uses a mod operator to produce the desired outputs.
   *
   * <p>The {@link HashCode} generated with the hash function should be at least 64 bits. This will
   * achieve good false positive rate when fingerprintLength <= 32.
   */
  SIMPLE_MOD() {
    @Override
    public long computeFingerprint(HashCode hash, int fingerprintLength) {
      // Use the most significant fingerprintLength bits. This is needed to get rid of the
      // correlation with the bucket index.
      long fingerprint = hash.asLong() >>> (Long.SIZE - fingerprintLength);
      // Value 0 is reserved, so instead map to 1. This means that the generated fingerprint value
      // is skewed (1 is twice as more likely to be generated than any other value). Note that, we
      // could have taken mod (2^fingerprintLength - 1) and added 1, which would produce a more
      // uniform distribution. However, for performance reason, we choose to take this approach
      // instead.
      if (fingerprint == 0) {
        return 1L;
      }
      return fingerprint;
    }

    @Override
    public int computeBucketIndex(HashCode hash, int bucketCount) {
      return Math.floorMod(hash.asLong(), bucketCount);
    }

    @Override
    public int computeOtherBucketIndex(
        long fingerprint,
        int bucketIndex,
        int bucketCount,
        CuckooFilterConfig.HashFunction hashFunction) {
      long fingerprintHash = hashFunction.hash(fingerprint, Funnels.longFunnel()).asLong();
      // Use (hash(fingerprint) - bucketIndex) mod bucketCount as the involution.
      return Math.floorMod(fingerprintHash - bucketIndex, bucketCount);
    }
  }
}
