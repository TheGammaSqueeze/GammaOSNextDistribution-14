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

import com.google.common.hash.Funnel;
import com.google.common.hash.HashCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * A space efficient, probabilistic multiset data structure that supports membership check,
 * insertion, and deletion of the elements.
 *
 * <p>Cuckoo filter enables tradeoffs between its space efficiency and the false positive
 * probability of the membership check.
 *
 * <p>See the original paper https://www.cs.cmu.edu/~dga/papers/cuckoo-conext2014.pdf for more
 * details.
 *
 * <p>This class is not thread-safe.
 */
public final class CuckooFilter<T> {
  private final CuckooFilterConfig config;
  private final CuckooFilterTable table;
  private final Funnel<? super T> funnel;
  private final Random random;

  /** Counts the total number of elements in the cuckoo filter. */
  private long count;

  /** Instantiates a new cuckoo filter. */
  public static <T> CuckooFilter<T> createNew(CuckooFilterConfig config, Funnel<? super T> funnel) {
    Random random = new Random();
    CuckooFilterTable table =
        CuckooFilterTable.create(config.size(), config.useSpaceOptimization(), random);
    return new CuckooFilter<T>(config, table, funnel, random);
  }

  /**
   * Instantiates a cuckoo filter from serialized cuckoo filter table.
   *
   * <p>Note that {@link SerializedCuckooFilterTable} does not contain any data on {@link
   * CuckooFilterConfig.HashFunction}, {@link CuckooFilterConfig.Strategy}, or {@link Funnel} used,
   * so it is up to the user to supply appropriate hash function, strategy, and funnel that were
   * used to generate the {@link SerializedCuckooFilterTable}.
   */
  public static <T> CuckooFilter<T> createFromSerializedTable(
      SerializedCuckooFilterTable serializedTable,
      CuckooFilterConfig.HashFunction hashFunction,
      CuckooFilterConfig.Strategy strategy,
      Funnel<? super T> funnel) {
    Random random = new Random();
    CuckooFilterTable table = CuckooFilterTable.createFromSerialization(serializedTable, random);
    return new CuckooFilter<T>(
        CuckooFilterConfig.newBuilder()
            .setSize(table.size())
            .setHashFunction(hashFunction)
            .setStrategy(strategy)
            .build(),
        table,
        funnel,
        random);
  }

  private CuckooFilter(
      CuckooFilterConfig config, CuckooFilterTable table, Funnel<? super T> funnel, Random random) {
    this.config = config;
    this.table = table;
    this.funnel = funnel;
    this.random = random;
    count = 0;
  }

  /**
   * Returns true if {@code element} is in the cuckoo filter.
   *
   * <p>By the probabilistic nature of the cuckoo filter data structure, this method may return a
   * false positive result. In other words, this method may incorrectly return true for an element
   * that was actually never inserted. This probability can depend on various factors, including the
   * size of the cuckoo filter and the hash function used.
   *
   * <p>However, it is guaranteed that this method never returns a false negative result, as long as
   * {@code delete} method is called on an element that exists in the filter. Please see {@code
   * delete} method for more details.
   */
  public boolean contains(T element) {
    HashCode hash = config.hashFunction().hash(element, funnel);
    long fingerprint =
        config.strategy().computeFingerprint(hash, config.size().fingerprintLength());
    int bucketIndex = config.strategy().computeBucketIndex(hash, config.size().bucketCount());
    int otherBucketIndex =
        config
            .strategy()
            .computeOtherBucketIndex(
                fingerprint, bucketIndex, config.size().bucketCount(), config.hashFunction());
    return table.contains(bucketIndex, fingerprint)
        || table.contains(otherBucketIndex, fingerprint);
  }

  /**
   * Inserts {@code element} to the cuckoo filter, returning true if the element was inserted
   * successfully.
   *
   * <p>Insertion of {@code element} will fail if there is no room for {@code element}. Note that
   * even when the insertion of {@code element} fails, it is possible for another element to be
   * inserted successfully. Even then, the insertion failure should be a good indicator that the
   * filter is getting close to its maximum capacity.
   */
  public boolean insert(T element) {
    HashCode hash = config.hashFunction().hash(element, funnel);
    long fingerprint =
        config.strategy().computeFingerprint(hash, config.size().fingerprintLength());
    int bucketIndex = config.strategy().computeBucketIndex(hash, config.size().bucketCount());
    int otherBucketIndex =
        config
            .strategy()
            .computeOtherBucketIndex(
                fingerprint, bucketIndex, config.size().bucketCount(), config.hashFunction());

    // First attempt to insert the fingerprint to one of the two assigned buckets.
    if (attemptInsertion(fingerprint, bucketIndex, otherBucketIndex)) {
      count++;
      return true;
    }

    // If both buckets are full, execute insertion with repeated replacements algorithm.
    int startBucketIndex = (random.nextInt(2) == 0) ? bucketIndex : otherBucketIndex;
    boolean inserted = insertWithRepeatedReplacements(fingerprint, startBucketIndex);
    if (inserted) {
      count++;
    }
    return inserted;
  }

  /**
   * Deletes {@code element} from the cuckoo filter, returning true if the element was deleted
   * successfully.
   *
   * <p>It is critical for {@code delete} to be called on an already existing element. Otherwise,
   * the filter may incorrectly delete a wrong element. When this happens, it is possible for {@code
   * contains} method to return a false negative result.
   */
  public boolean delete(T element) {
    HashCode hash = config.hashFunction().hash(element, funnel);
    long fingerprint =
        config.strategy().computeFingerprint(hash, config.size().fingerprintLength());
    int bucketIndex = config.strategy().computeBucketIndex(hash, config.size().bucketCount());
    int otherBucketIndex =
        config
            .strategy()
            .computeOtherBucketIndex(
                fingerprint, bucketIndex, config.size().bucketCount(), config.hashFunction());
    boolean deleted =
        table.delete(bucketIndex, fingerprint) || table.delete(otherBucketIndex, fingerprint);
    if (deleted) {
      count--;
    }
    return deleted;
  }

  /** Returns the size of the cuckoo filter. */
  public CuckooFilterConfig.Size size() {
    return config.size();
  }

  /** Returns the count of the elements in the cuckoo filter. */
  public long count() {
    return count;
  }

  /**
   * Returns the ratio of the total number of elements in the cuckoo filter and the theoretical max
   * capacity.
   *
   * <p>The returned value is in range [0, 1].
   */
  public double load() {
    return count / ((double) config.size().bucketCount() * config.size().bucketCapacity());
  }

  /**
   * Serializes the state of the cuckoo filter table.
   *
   * <p>Note that this method does not serialize hash function, strategy, and funnel. When
   * instantiating a cuckoo filter from the returned {@link SerializedCuckooFilterTable}, it is up
   * to the user to supply appropriate hash function, strategy, and funnel that were used.
   */
  public SerializedCuckooFilterTable serializeTable() {
    return table.serialize();
  }

  /**
   * Attempts to insert {@code fingerprint} to one of the buckets with indices {@code bucketIndex}
   * and {@code otherBucketIndex}, returning true when successful. Returns false if both buckets are
   * full and the insertion failed.
   */
  private boolean attemptInsertion(long fingerprint, int bucketIndex, int otherBucketIndex) {
    if (!table.isFull(bucketIndex)) {
      table.insertWithReplacement(bucketIndex, fingerprint);
      return true;
    }
    if (!table.isFull(otherBucketIndex)) {
      table.insertWithReplacement(otherBucketIndex, fingerprint);
      return true;
    }
    return false;
  }

  /**
   * Randomly traverses the cuckoo graph to find an available bucket for insertion.
   *
   * <p>At a high level, this algorithm starts at vertex {@code bucketIndex} and performs a random
   * walk of length at most {@link CuckooFilterConfig.Strategy#maxReplacementCount}. If an available
   * bucket is found, the algorithm "pushes" all the fingerprints (edges) that are visited (note
   * that in the cuckoo graph, the edges are the fingerprints) to their alternate buckets, and make
   * room for {@code fingerprint} to be inserted.
   *
   * <p>If during the random walk an available bucket is not found, the insertion fails and the
   * method returns false.
   *
   * <p>Note that it is possible to deterministically find an available bucket by performing breadth
   * first search in the cuckoo graph, but this is usually slower and the extra chance of successful
   * insertion is negligibly small in practice.
   */
  private boolean insertWithRepeatedReplacements(long fingerprint, int bucketIndex) {
    List<Integer> visitedBucketIndices = new ArrayList<>();
    List<Long> replacedFingerprints = new ArrayList<>();

    long currFingerprint = fingerprint;
    int currBucketIndex = bucketIndex;
    visitedBucketIndices.add(-1); // Just for index alignment purpose.
    replacedFingerprints.add(currFingerprint);
    for (int i = 0; i < config.strategy().maxReplacementCount(); i++) {
      Optional<Long> replacedFingerprint =
          table.insertWithReplacement(currBucketIndex, currFingerprint);
      // Found an available bucket, and the insertion is successful.
      if (replacedFingerprint.isEmpty()) {
        return true;
      }

      visitedBucketIndices.add(currBucketIndex);
      replacedFingerprints.add(replacedFingerprint.get());

      currFingerprint = replacedFingerprint.get();
      currBucketIndex =
          config
              .strategy()
              .computeOtherBucketIndex(
                  currFingerprint,
                  currBucketIndex,
                  config.size().bucketCount(),
                  config.hashFunction());
    }

    // Failed to find a bucket to insert. Reverse the replacements and declare that the insertion
    // failed.
    for (int i = visitedBucketIndices.size() - 1; i > 0; i--) {
      int previousBucketIndex = visitedBucketIndices.get(i);
      table.delete(previousBucketIndex, replacedFingerprints.get(i - 1));
      table.insertWithReplacement(previousBucketIndex, replacedFingerprints.get(i));
    }
    return false;
  }
}
