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

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Funnel;
import com.google.common.hash.HashCode;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Map;

/**
 * Specification for the cuckoo filter.
 *
 * <p>This class is immutable.
 */
// TODO: Handle serialization.
public final class CuckooFilterConfig {
  private final Size size;
  private final HashFunction hashFunction;
  private final Strategy strategy;
  private final boolean useSpaceOptimization;

  private CuckooFilterConfig(
      Size size, HashFunction hashFunction, Strategy strategy, boolean useSpaceOptimization) {
    this.size = size;
    this.hashFunction = hashFunction;
    this.strategy = strategy;
    this.useSpaceOptimization = useSpaceOptimization;
  }

  public Size size() {
    return size;
  }

  public HashFunction hashFunction() {
    return hashFunction;
  }

  public Strategy strategy() {
    return strategy;
  }

  public boolean useSpaceOptimization() {
    return useSpaceOptimization;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  /** Builder for the {@link CuckooFilterConfig}. */
  public static class Builder {
    private Size size;
    private HashFunction hashFunction;
    private Strategy strategy;
    private boolean useSpaceOptimization;

    private Builder() {}

    @CanIgnoreReturnValue
    public Builder setSize(Size size) {
      this.size = size;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setHashFunction(HashFunction hashFunction) {
      this.hashFunction = hashFunction;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setStrategy(Strategy strategy) {
      this.strategy = strategy;
      return this;
    }

    /**
     * Whether to use space optimized filter representation (if possible).
     *
     * <p>Setting this field to {@code true} does not guarantee the optimization algorithm to always
     * apply - it is best effort.
     *
     * <p>In general, using this may result in slower filter operations, and incurs an additional
     * fixed space overhead. Thus, it is possible for the "optimized" version of the filter to
     * actually take more space than the non optimized one.
     */
    @CanIgnoreReturnValue
    public Builder setUseSpaceOptimization(boolean useSpaceOptimization) {
      this.useSpaceOptimization = useSpaceOptimization;
      return this;
    }

    /**
     * Builds {@link CuckooFilterConfig}.
     *
     * @throws IllegalArgumentException if the required parameters are not set.
     */
    public CuckooFilterConfig build() {
      checkArgument(size != null, "Size must be set.");
      checkArgument(hashFunction != null, "Hash function must be set.");
      checkArgument(strategy != null, "Strategy must be set.");

      return new CuckooFilterConfig(size, hashFunction, strategy, useSpaceOptimization);
    }
  }

  /**
   * Specification of the cuckoo filter size.
   *
   * <p>A cuckoo filter's size can be defined as a tuple (bucketCount, bucketCapacity,
   * fingeprintLength); this means that there are bucketCount number of buckets, where each bucket
   * can store up to bucketCapacity fingerprints, and each fingerprint is of length
   * fingerprintLength bits.
   *
   * <p>All fields are required and must be set explicitly.
   *
   * <p>This class is immutable.
   */
  public static class Size {
    private static final int MAX_BUCKET_CAPACITY = 128;
    private static final int MAX_FINGERPRINT_LENGTH = 64;
    /** Empirical load by the bucket capacity. */
    private static final ImmutableMap<Integer, Double> APPROX_LOAD_BY_BUCKET_CAPACITY =
        ImmutableMap.<Integer, Double>builder()
            .put(2, 0.85)
            .put(3, 0.91)
            .put(4, 0.95)
            .put(5, 0.96)
            .put(6, 0.97)
            .put(7, 0.98)
            .put(8, 0.98)
            .buildOrThrow();

    private final int bucketCount;
    private final int bucketCapacity;
    private final int fingerprintLength;

    private Size(int bucketCount, int bucketCapacity, int fingerprintLength) {
      this.bucketCount = bucketCount;
      this.bucketCapacity = bucketCapacity;
      this.fingerprintLength = fingerprintLength;
    }

    /**
     * Automatically computes a reasonably efficient cuckoo filter {@link Size} that ensures (with
     * high probability) storing up to {@code elementsCountUpperBound} elements (with high
     * probability) with the given {@code targetFalsePositiveRate}.
     *
     * @throws IllegalArgumentException if {@code targetFalsePositiveRate} is not in range [0, 1] or
     *     {@code elementsCountUpperBound} is <= 0, or a suitable cuckoo filter size could not be
     *     computed based on the given input.
     */
    public static Size computeEfficientSize(
        double targetFalsePositiveRate, long elementsCountUpperBound) {
      checkArgument(
          0 < targetFalsePositiveRate && targetFalsePositiveRate < 1,
          "targetFalsePositiveRate must be in range (0, 1): %s given.",
          targetFalsePositiveRate);
      checkArgument(
          elementsCountUpperBound > 0,
          "elementsCountUpperBound must be > 0: %s given.",
          elementsCountUpperBound);

      long bestCuckooFilterSizeInBits = -1;
      int bestBucketCount = 0;
      int bestBucketCapacity = 0;
      int bestFingerprintLength = 0;
      for (Map.Entry<Integer, Double> entry : APPROX_LOAD_BY_BUCKET_CAPACITY.entrySet()) {
        int bucketCapacity = entry.getKey();
        double load = entry.getValue();

        int fingerprintLength =
            (int) Math.ceil(-log2(targetFalsePositiveRate) + log2(bucketCapacity) + 1);
        long bucketCount = (long) Math.ceil(elementsCountUpperBound / (bucketCapacity * load));

        // The computed size is invalid if fingerprint length is larger than max length or the
        // bucket count that is larger than max integer.
        if (fingerprintLength > MAX_FINGERPRINT_LENGTH || bucketCount >= Integer.MAX_VALUE) {
          continue;
        }

        long totalBits = bucketCount * bucketCapacity * fingerprintLength;
        if (bestCuckooFilterSizeInBits == -1 || bestCuckooFilterSizeInBits > totalBits) {
          bestCuckooFilterSizeInBits = totalBits;
          bestBucketCount = (int) bucketCount;
          bestBucketCapacity = bucketCapacity;
          bestFingerprintLength = fingerprintLength;
        }
      }

      checkArgument(
          bestCuckooFilterSizeInBits != -1,
          "Could not compute suitable cuckoo filter size based on the given input. Either the"
              + " target false positive rate is too low, or the computed size is too big.");

      return Size.newBuilder()
          .setBucketCount(bestBucketCount)
          .setBucketCapacity(bestBucketCapacity)
          .setFingerprintLength(bestFingerprintLength)
          .build();
    }

    public static Builder newBuilder() {
      return new Builder();
    }

    /** Returns the total number of buckets in the cuckoo filter. */
    public int bucketCount() {
      return bucketCount;
    }

    /** Returns the maximum number of fingerprints each bucket can hold. */
    public int bucketCapacity() {
      return bucketCapacity;
    }

    /** Returns the length of the fingerprint in bits. */
    public int fingerprintLength() {
      return fingerprintLength;
    }

    /** Builder for the {@link Size}. */
    public static class Builder {
      private int bucketCount;
      private int bucketCapacity;
      private int fingerprintLength;

      private Builder() {}

      /**
       * Sets the number of buckets in the cuckoo filter.
       *
       * <p>{@code bucketCount} must be > 0.
       */
      @CanIgnoreReturnValue
      public Builder setBucketCount(int bucketCount) {
        this.bucketCount = bucketCount;
        return this;
      }

      /**
       * Sets the maximum number of fingerprints each bucket can hold.
       *
       * <p>{@code bucketCapacity} must be in range (0, {@value #MAX_BUCKET_CAPACITY}].
       */
      @CanIgnoreReturnValue
      public Builder setBucketCapacity(int bucketCapacity) {
        this.bucketCapacity = bucketCapacity;
        return this;
      }

      /**
       * Sets the length of each fingerprint in bits.
       *
       * <p>{@code fingerprintLength} must be in range (0, {@value #MAX_FINGERPRINT_LENGTH}].
       */
      @CanIgnoreReturnValue
      public Builder setFingerprintLength(int fingerprintLength) {
        this.fingerprintLength = fingerprintLength;
        return this;
      }

      /**
       * Builds {@link Size}.
       *
       * @throws IllegalArgumentException if the configured parameters are invalid.
       */
      public Size build() {
        checkArgument(bucketCount > 0, "bucketCount must be > 0: %s given instead.", bucketCount);
        checkArgument(
            0 < bucketCapacity && bucketCapacity <= MAX_BUCKET_CAPACITY,
            "bucketCapacity must be in range (0, %s]: %s given instead.",
            MAX_BUCKET_CAPACITY,
            bucketCapacity);
        checkArgument(
            0 < fingerprintLength && fingerprintLength <= MAX_FINGERPRINT_LENGTH,
            "fingerprintLength must be in range (0, %s]: %s given instead.",
            MAX_FINGERPRINT_LENGTH,
            fingerprintLength);

        return new Size(bucketCount, bucketCapacity, fingerprintLength);
      }
    }

    private static double log2(double x) {
      return Math.log(x) / Math.log(2);
    }
  }

  /** Hash function for transforming an arbitrary type element to a {@link HashCode}. */
  public interface HashFunction {
    /** Hashes given {@code element} to a {@link HashCode}, using the given {@code funnel}. */
    <T> HashCode hash(T element, Funnel<? super T> funnel);
  }

  /**
   * Strategy for computing fingerprints and where these fingerprints belong in the cuckoo filter
   * table.
   */
  public interface Strategy {

    /**
     * Computes the fingerprint value given the element's {@code hash} output from {@link
     * HashFunction}.
     *
     * <p>The returned value should be in range (0, 2^{@code fingerprintLength}). Otherwise, the
     * behavior of the cuckoo filter is undefined. Note that the interval is an open interval, so 0
     * and 2^{@code fingerprintLength} are not included.
     */
    long computeFingerprint(HashCode hash, int fingerprintLength);

    /**
     * Computes one of the bucket indices given the element's {@code hash} output from {@link
     * HashFunction} and {@code bucketCount} of the cuckoo filter.
     *
     * <p>The returned value should be in range [0, {@code bucketCount}). Otherwise, the behavior of
     * the cuckoo filter is undefined.
     */
    int computeBucketIndex(HashCode hash, int bucketCount);

    /**
     * Computes the element's other bucket index given the element's {@code fingerprint} value and
     * its initial {@code bucketIndex}.
     *
     * <p>{@code hashFunction} corresponds to the {@link HashFunction} that was supplied when the
     * config was constructed. Depending on the implementation, {@code hashFunction} may or may not
     * be used.
     *
     * <p>The returned value should be in range [0, {@code bucketCount}), and the method needs to be
     * an involution with respect to {@code bucketIndex}. That is, with other parameters fixed, the
     * method needs to satisfy <b>bucketIndex =
     * computeOtherBucketIndex(computeOtherBucketIndex(bucketIndex))</b> for all valid
     * <b>bucketIndex</b>. Note that other parameters are omitted for brevity. If these properties
     * don't hold, the behavior of the cuckoo filter is undefined.
     */
    int computeOtherBucketIndex(
        long fingerprint, int bucketIndex, int bucketCount, HashFunction hashFunction);

    /**
     * Maximum number of replacements to be made during insertion, before declaring that the
     * insertion has failed.
     *
     * <p>If not overridden, set to 500 as a default.
     */
    default int maxReplacementCount() {
      return 500;
    }
  }
}
