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
import static org.junit.Assert.assertThrows;

import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CuckooFilterConfigTest {

  public static final class TestHashFunction implements CuckooFilterConfig.HashFunction {
    @Override
    public <T> HashCode hash(T element, Funnel<? super T> funnel) {
      return Hashing.murmur3_128().hashObject(element, funnel);
    }
  }

  public static final class TestStrategy implements CuckooFilterConfig.Strategy {
    @Override
    public long computeFingerprint(HashCode hash, int fingerprintLength) {
      return 20;
    }

    @Override
    public int computeBucketIndex(HashCode hash, int bucketCount) {
      return 0;
    }

    @Override
    public int computeOtherBucketIndex(
        long fingerprint,
        int bucketIndex,
        int bucketCount,
        CuckooFilterConfig.HashFunction hashFunction) {
      return 1;
    }
  }

  @Test
  public void build_buildsCuckooFilterConfig() {
    CuckooFilterConfig config =
        CuckooFilterConfig.newBuilder()
            .setSize(
                CuckooFilterConfig.Size.newBuilder()
                    .setBucketCount(100)
                    .setBucketCapacity(4)
                    .setFingerprintLength(16)
                    .build())
            .setHashFunction(new TestHashFunction())
            .setStrategy(new TestStrategy())
            .setUseSpaceOptimization(true)
            .build();

    CuckooFilterConfig.Size size = config.size();
    assertThat(size.bucketCount()).isEqualTo(100);
    assertThat(size.bucketCapacity()).isEqualTo(4);
    assertThat(size.fingerprintLength()).isEqualTo(16);

    Funnel<Long> funnel = Funnels.longFunnel();
    CuckooFilterConfig.HashFunction hashFunction = config.hashFunction();
    assertThat(hashFunction.hash(100L, funnel))
        .isEqualTo(Hashing.murmur3_128().hashObject(100L, funnel));

    CuckooFilterConfig.Strategy strategy = config.strategy();
    HashCode randomHash = HashCode.fromLong(100L);
    assertThat(strategy.computeFingerprint(randomHash, 16)).isEqualTo(20);
    assertThat(strategy.computeBucketIndex(randomHash, 100)).isEqualTo(0);
    assertThat(strategy.computeOtherBucketIndex(0, 5, 100, config.hashFunction())).isEqualTo(1);
    assertThat(strategy.maxReplacementCount()).isEqualTo(500);

    assertThat(config.useSpaceOptimization()).isTrue();
  }

  @Test
  public void build_failsWithUnsetSize() {
    String message =
        assertThrows(IllegalArgumentException.class, () -> CuckooFilterConfig.newBuilder().build())
            .getMessage();

    assertThat(message).isEqualTo("Size must be set.");
  }

  @Test
  public void build_failsWithUnsetHashFunction() {
    String message =
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    CuckooFilterConfig.newBuilder()
                        .setSize(
                            CuckooFilterConfig.Size.newBuilder()
                                .setBucketCount(100)
                                .setBucketCapacity(4)
                                .setFingerprintLength(16)
                                .build())
                        .build())
            .getMessage();

    assertThat(message).isEqualTo("Hash function must be set.");
  }

  @Test
  public void build_failsWithUnsetStrategy() {
    String message =
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    CuckooFilterConfig.newBuilder()
                        .setSize(
                            CuckooFilterConfig.Size.newBuilder()
                                .setBucketCount(100)
                                .setBucketCapacity(4)
                                .setFingerprintLength(16)
                                .build())
                        .setHashFunction(new TestHashFunction())
                        .build())
            .getMessage();

    assertThat(message).isEqualTo("Strategy must be set.");
  }

  @Test
  public void buildSize_failsWithInvalidBucketCount() {
    String message =
        assertThrows(
                IllegalArgumentException.class,
                () -> CuckooFilterConfig.Size.newBuilder().setBucketCount(0).build())
            .getMessage();

    assertThat(message).isEqualTo("bucketCount must be > 0: 0 given instead.");
  }

  @Test
  public void buildSize_failsWithInvalidBucketCapacity() {
    String messageLower =
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    CuckooFilterConfig.Size.newBuilder()
                        .setBucketCount(1)
                        .setBucketCapacity(0)
                        .build())
            .getMessage();

    assertThat(messageLower)
        .isEqualTo("bucketCapacity must be in range (0, 128]: 0 given instead.");

    String messageHigher =
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    CuckooFilterConfig.Size.newBuilder()
                        .setBucketCount(1)
                        .setBucketCapacity(129)
                        .build())
            .getMessage();

    assertThat(messageHigher)
        .isEqualTo("bucketCapacity must be in range (0, 128]: 129 given instead.");
  }

  @Test
  public void buildSize_failsWithInvalidFingerprintLength() {
    String messageLower =
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    CuckooFilterConfig.Size.newBuilder()
                        .setBucketCount(1)
                        .setBucketCapacity(1)
                        .setFingerprintLength(0)
                        .build())
            .getMessage();

    assertThat(messageLower)
        .isEqualTo("fingerprintLength must be in range (0, 64]: 0 given instead.");

    String messageHigher =
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    CuckooFilterConfig.Size.newBuilder()
                        .setBucketCount(1)
                        .setBucketCapacity(1)
                        .setFingerprintLength(65)
                        .build())
            .getMessage();

    assertThat(messageHigher)
        .isEqualTo("fingerprintLength must be in range (0, 64]: 65 given instead.");
  }

  @Test
  public void computeEfficientSize_failsWithInvalidFalsePositiveRate() {
    String messageLower =
        assertThrows(
                IllegalArgumentException.class,
                () -> CuckooFilterConfig.Size.computeEfficientSize(0, 5))
            .getMessage();

    assertThat(messageLower)
        .isEqualTo("targetFalsePositiveRate must be in range (0, 1): 0.0 given.");

    String messageHigher =
        assertThrows(
                IllegalArgumentException.class,
                () -> CuckooFilterConfig.Size.computeEfficientSize(1, 5))
            .getMessage();

    assertThat(messageHigher)
        .isEqualTo("targetFalsePositiveRate must be in range (0, 1): 1.0 given.");
  }

  @Test
  public void computeEfficientSize_failsWithInvalidElementsCountUpperBound() {
    String message =
        assertThrows(
                IllegalArgumentException.class,
                () -> CuckooFilterConfig.Size.computeEfficientSize(0.5, 0))
            .getMessage();

    assertThat(message).isEqualTo("elementsCountUpperBound must be > 0: 0 given.");
  }

  @Test
  public void computeEfficientSize_failsIfElementsCountUpperBoundTooBig() {
    String message =
        assertThrows(
                IllegalArgumentException.class,
                () -> CuckooFilterConfig.Size.computeEfficientSize(0.5, 5000L * Integer.MAX_VALUE))
            .getMessage();

    assertThat(message)
        .isEqualTo(
            "Could not compute suitable cuckoo filter size based on the given input. Either the"
                + " target false positive rate is too low, or the computed size is too big.");
  }

  @Test
  public void computeEfficientSize_failsIfFalsePositiveRateTooLow() {
    String message =
        assertThrows(
                IllegalArgumentException.class,
                () -> CuckooFilterConfig.Size.computeEfficientSize(Double.MIN_NORMAL, 100))
            .getMessage();

    assertThat(message)
        .isEqualTo(
            "Could not compute suitable cuckoo filter size based on the given input. Either the"
                + " target false positive rate is too low, or the computed size is too big.");
  }
}
