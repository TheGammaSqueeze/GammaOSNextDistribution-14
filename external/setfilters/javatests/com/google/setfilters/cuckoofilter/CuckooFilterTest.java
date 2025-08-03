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

import com.google.common.hash.Funnels;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class CuckooFilterTest {

  @Parameters
  public static List<? extends Object> data() {
    return Arrays.asList(true, false);
  }

  @Parameter public boolean useSpaceOptimization;

  private CuckooFilterConfig config =
      CuckooFilterConfig.newBuilder()
          .setSize(
              CuckooFilterConfig.Size.newBuilder()
                  .setBucketCount(100)
                  .setBucketCapacity(4)
                  .setFingerprintLength(16)
                  .build())
          .setHashFunction(CuckooFilterHashFunctions.MURMUR3_128)
          .setStrategy(CuckooFilterStrategies.SIMPLE_MOD)
          .build();

  private CuckooFilter<Integer> cuckooFilter;

  @Before
  public void setUp() {
    config =
        CuckooFilterConfig.newBuilder()
            .setSize(
                CuckooFilterConfig.Size.newBuilder()
                    .setBucketCount(100)
                    .setBucketCapacity(4)
                    .setFingerprintLength(16)
                    .build())
            .setHashFunction(CuckooFilterHashFunctions.MURMUR3_128)
            .setStrategy(CuckooFilterStrategies.SIMPLE_MOD)
            .setUseSpaceOptimization(useSpaceOptimization)
            .build();
    cuckooFilter = CuckooFilter.createNew(config, Funnels.integerFunnel());
  }

  @Test
  public void insertAndContains() {
    final int insertedElementsCount = 380;

    for (int i = 0; i < insertedElementsCount; i++) {
      assertThat(cuckooFilter.insert(i)).isTrue();
    }

    for (int i = 0; i < insertedElementsCount; i++) {
      assertThat(cuckooFilter.contains(i)).isTrue();
    }

    final int testCountNonExistentElements = 300;

    for (int i = 0; i < testCountNonExistentElements; i++) {
      assertThat(cuckooFilter.contains(i + insertedElementsCount)).isFalse();
    }
  }

  @Test
  public void insert_failsWhenFull_insertSameElements() {
    // Exhaust two buckets that element 0 can belong to.
    for (int i = 0; i < 2 * config.size().bucketCapacity(); i++) {
      assertThat(cuckooFilter.insert(0)).isTrue();
    }

    assertThat(cuckooFilter.insert(0)).isFalse();
  }

  @Test
  public void insert_insertFailureReversesTheReplacements() {
    int insertedCount = 0;
    while (true) {
      if (!cuckooFilter.insert(insertedCount)) {
        break;
      }
      insertedCount++;
    }

    for (int i = 0; i < insertedCount; i++) {
      assertThat(cuckooFilter.contains(i)).isTrue();
    }
    assertThat(cuckooFilter.contains(insertedCount)).isFalse();
  }

  @Test
  public void delete_deletesExistingElements() {
    final int insertedElementsCount = 150;

    for (int i = 0; i < insertedElementsCount; i++) {
      assertThat(cuckooFilter.insert(i)).isTrue();
      assertThat(cuckooFilter.insert(i)).isTrue();
    }

    for (int i = 0; i < insertedElementsCount; i++) {
      assertThat(cuckooFilter.delete(i)).isTrue();
      assertThat(cuckooFilter.delete(i)).isTrue();
    }
  }

  @Test
  public void delete_deletingNonExistingElementsFails() {
    final int insertedElementsCount = 150;

    for (int i = 0; i < insertedElementsCount; i++) {
      assertThat(cuckooFilter.delete(i)).isFalse();
    }
  }

  @Test
  public void size() {
    assertThat(cuckooFilter.size()).isEqualTo(config.size());
  }

  @Test
  public void count() {
    final int insertedElementsCount = 300;
    final int deletedElementCount = 150;

    for (int i = 0; i < insertedElementsCount; i++) {
      assertThat(cuckooFilter.insert(i)).isTrue();
    }
    assertThat(cuckooFilter.count()).isEqualTo(insertedElementsCount);

    for (int i = 0; i < deletedElementCount; i++) {
      assertThat(cuckooFilter.delete(i)).isTrue();
    }
    assertThat(cuckooFilter.count()).isEqualTo(insertedElementsCount - deletedElementCount);

    // Attempt to delete non existing elements.
    for (int i = 0; i < deletedElementCount; i++) {
      assertThat(cuckooFilter.delete(insertedElementsCount + i)).isFalse();
    }
    assertThat(cuckooFilter.count()).isEqualTo(insertedElementsCount - deletedElementCount);
  }

  @Test
  public void serializeAndDeserialize() {
    final int insertedElementsCount = 300;

    for (int i = 0; i < insertedElementsCount; i++) {
      assertThat(cuckooFilter.insert(i)).isTrue();
    }

    SerializedCuckooFilterTable serializedTable = cuckooFilter.serializeTable();

    CuckooFilter<Integer> anotherCuckooFilter =
        CuckooFilter.createFromSerializedTable(
            serializedTable, config.hashFunction(), config.strategy(), Funnels.integerFunnel());

    for (int i = 0; i < insertedElementsCount; i++) {
      assertThat(anotherCuckooFilter.contains(i)).isTrue();
    }
    assertThat(anotherCuckooFilter.contains(insertedElementsCount)).isFalse();
  }

  @Test
  public void load() {
    final int insertedElementsCount = 300;

    for (int i = 0; i < insertedElementsCount; i++) {
      assertThat(cuckooFilter.insert(i)).isTrue();
    }

    assertThat(cuckooFilter.load())
        .isWithin(0.00000001)
        .of(
            (double) insertedElementsCount
                / (config.size().bucketCount() * config.size().bucketCapacity()));
  }

  @Test
  public void loadIsHigh() {
    final int[] bucketCounts = {1000, 10000, 100000, 1000000};
    final int[] bucketCapacities = {4, 5, 6, 7, 8};
    final int fingerprintLength = 16;

    for (int bucketCount : bucketCounts) {
      for (int bucketCapacity : bucketCapacities) {
        CuckooFilter<Integer> cuckooFilter =
            CuckooFilter.createNew(
                CuckooFilterConfig.newBuilder()
                    .setSize(
                        CuckooFilterConfig.Size.newBuilder()
                            .setBucketCount(bucketCount)
                            .setBucketCapacity(bucketCapacity)
                            .setFingerprintLength(fingerprintLength)
                            .build())
                    .setHashFunction(CuckooFilterHashFunctions.MURMUR3_128)
                    .setStrategy(CuckooFilterStrategies.SIMPLE_MOD)
                    .setUseSpaceOptimization(useSpaceOptimization)
                    .build(),
                Funnels.integerFunnel());

        int element = 0;
        while (cuckooFilter.insert(element)) {
          element++;
        }

        assertThat(cuckooFilter.load()).isAtLeast(0.95);
      }
    }
  }

  @Test
  public void computeEfficientSize_achievesTargetFalsePositiveRateAndCapacity() {
    final double[] targetFalsePositiveRates = {0.05, 0.01, 0.001};
    final long[] elementsCountUpperBounds = {100, 1000, 10000};

    for (double targetFalsePositiveRate : targetFalsePositiveRates) {
      for (long elementsCountUpperBound : elementsCountUpperBounds) {
        CuckooFilter<Integer> cuckooFilter =
            CuckooFilter.createNew(
                CuckooFilterConfig.newBuilder()
                    .setSize(
                        CuckooFilterConfig.Size.computeEfficientSize(
                            targetFalsePositiveRate, elementsCountUpperBound))
                    .setHashFunction(CuckooFilterHashFunctions.MURMUR3_128)
                    .setStrategy(CuckooFilterStrategies.SIMPLE_MOD)
                    .build(),
                Funnels.integerFunnel());

        int element = 0;
        while (cuckooFilter.insert(element)) {
          element++;
        }

        assertThat(computeFalsePositiveRate(cuckooFilter, 1000000))
            .isAtMost(targetFalsePositiveRate);
        assertThat(cuckooFilter.count()).isAtLeast(elementsCountUpperBound);
      }
    }
  }

  @Test
  public void closeToTheoreticalFalsePositiveRate() {
    final int bucketCount = 1000;
    final int[] bucketCapacities = {2, 3, 4, 5, 6, 7, 8};
    for (int bucketCapacity : bucketCapacities) {
      // Due to time out issue, we only go up to 12 bits (otherwise we have to sample too many times
      // to get a reliable measurement).
      // TODO: Add a separate benchmark to test for longer fingerprint length.
      for (int fingerprintLength = 8; fingerprintLength <= 12; fingerprintLength++) {
        CuckooFilter<Integer> cuckooFilter =
            CuckooFilter.createNew(
                CuckooFilterConfig.newBuilder()
                    .setSize(
                        CuckooFilterConfig.Size.newBuilder()
                            .setBucketCount(bucketCount)
                            .setBucketCapacity(bucketCapacity)
                            .setFingerprintLength(fingerprintLength)
                            .build())
                    .setHashFunction(CuckooFilterHashFunctions.MURMUR3_128)
                    .setStrategy(CuckooFilterStrategies.SIMPLE_MOD)
                    .build(),
                Funnels.integerFunnel());

        int element = 0;
        while (cuckooFilter.insert(element)) {
          element++;
        }

        // Let f = fingerprintLength. A random element not in the cuckoo filter has 1 / (2^f - 1)
        // probability of matching a random fingerprint, and the probability it matches at least one
        // of the x fingerprints is 1 - (1 - 1 / (2^f - 1))^x which is approximately x / (2^f - 1)
        // when x << 2^f - 1.
        //
        // If X is a random variable denoting number of fingerprints in a randomly chosen two
        // buckets, false positive probability is roughly E[X / (2^f - 1)] = E[X] / (2^f - 1).
        // Let a be the cuckoo filter's load and b be the bucketCapacity. Then E[X] = a * 2b.
        // Thus, theoretical false positive rate is ~ a * 2b / (2^f - 1).
        double load = cuckooFilter.load();
        double theoreticalFalsePositiveRate =
            load * 2 * bucketCapacity / ((1 << fingerprintLength) - 1);

        double relativeDiff =
            Math.abs(computeFalsePositiveRate(cuckooFilter, 2000000) - theoreticalFalsePositiveRate)
                / theoreticalFalsePositiveRate;
        assertThat(relativeDiff).isAtMost(0.03);
      }
    }
  }

  private static double computeFalsePositiveRate(
      CuckooFilter<Integer> cuckooFilter, int sampleCount) {
    int falsePositiveCount = 0;
    for (int i = 0; i < sampleCount; i++) {
      if (cuckooFilter.contains(-i - 1)) {
        falsePositiveCount++;
      }
    }
    return (double) falsePositiveCount / sampleCount;
  }
}
