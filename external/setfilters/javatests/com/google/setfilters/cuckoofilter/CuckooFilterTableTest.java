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
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class CuckooFilterTableTest {
  private static final int BUCKET_COUNT = 10000;
  private static final int BUCKET_CAPACITY = 4;
  private static final int FINGERPRINT_LENGTH = 16;

  private Random random;
  private CuckooFilterTable table;

  private interface CuckooFilterTableFactory {
    public CuckooFilterTable create(CuckooFilterConfig.Size size, Random random);

    public default CuckooFilterTable createExisting(
        SerializedCuckooFilterTable serializedTable, Random random) {
      return CuckooFilterTable.createFromSerialization(serializedTable, random);
    }
  }

  private static class SemiSortedCuckooFilterTableFactory implements CuckooFilterTableFactory {
    @Override
    public CuckooFilterTable create(CuckooFilterConfig.Size size, Random random) {
      return new SemiSortedCuckooFilterTable(size, random);
    }
  }

  private static class UncompressedCuckooFilterTableFactory implements CuckooFilterTableFactory {
    @Override
    public CuckooFilterTable create(CuckooFilterConfig.Size size, Random random) {
      return new UncompressedCuckooFilterTable(size, random);
    }
  }

  @Parameters
  public static List<? extends Object> data() {
    return Arrays.asList(
        new SemiSortedCuckooFilterTableFactory(), new UncompressedCuckooFilterTableFactory());
  }

  @Parameter public CuckooFilterTableFactory tableFactory;

  @Before
  public void setUp() {
    random = mock(Random.class);
    table =
        tableFactory.create(
            CuckooFilterConfig.Size.newBuilder()
                .setBucketCount(BUCKET_COUNT)
                .setBucketCapacity(BUCKET_CAPACITY)
                .setFingerprintLength(FINGERPRINT_LENGTH)
                .build(),
            random);
  }

  @Test
  public void insertWithReplacement() {
    for (int i = 0; i < BUCKET_COUNT; i++) {
      long offset = (long) i * BUCKET_CAPACITY;
      for (int j = 0; j < BUCKET_CAPACITY; j++) {
        assertThat(table.insertWithReplacement(i, offset + j + 1)).isEmpty();
      }
      when(random.nextInt(BUCKET_CAPACITY)).thenReturn(0);

      Optional<Long> replaced = table.insertWithReplacement(i, offset + BUCKET_CAPACITY + 1);

      boolean anyOf = false;
      for (int j = 0; j < BUCKET_CAPACITY; j++) {
        anyOf = anyOf || (replaced.get() == offset + j + 1);
      }
      assertThat(anyOf).isTrue();
      assertThat(table.contains(i, replaced.get())).isFalse();
      for (long fingerprint = offset + 1;
          fingerprint < offset + BUCKET_CAPACITY + 2;
          fingerprint++) {
        if (fingerprint != replaced.get()) {
          assertThat(table.contains(i, fingerprint)).isTrue();
        }
      }
    }
  }

  @Test
  public void contains_containsFingerprint() {
    assertThat(table.insertWithReplacement(0, 1L)).isEmpty();

    assertThat(table.contains(0, 1L)).isTrue();
  }

  @Test
  public void contains_doesNotContainFingerprint() {
    assertThat(table.contains(0, 1L)).isFalse();
  }

  @Test
  public void delete_deletesExistingFingerprint() {
    assertThat(table.insertWithReplacement(0, 1L)).isEmpty();
    assertThat(table.contains(0, 1L)).isTrue();

    assertThat(table.delete(0, 1L)).isTrue();
    assertThat(table.contains(0, 1L)).isFalse();
  }

  @Test
  public void delete_deletesOneFingerprintAtATime() {
    assertThat(table.insertWithReplacement(0, 1L)).isEmpty();
    assertThat(table.insertWithReplacement(0, 1L)).isEmpty();
    assertThat(table.contains(0, 1L)).isTrue();

    assertThat(table.delete(0, 1L)).isTrue();
    assertThat(table.contains(0, 1L)).isTrue();
    assertThat(table.delete(0, 1L)).isTrue();
    assertThat(table.contains(0, 1L)).isFalse();
  }

  @Test
  public void delete_deletesNonExistingFingerprint() {
    assertThat(table.delete(0, 1L)).isFalse();
  }

  @Test
  public void isFull() {
    for (int j = 0; j < BUCKET_CAPACITY; j++) {
      assertThat(table.isFull(0)).isFalse();
      assertThat(table.insertWithReplacement(0, j + 1)).isEmpty();
    }
    assertThat(table.isFull(0)).isTrue();
  }

  @Test
  public void size() {
    CuckooFilterConfig.Size size = table.size();

    assertThat(size.bucketCount()).isEqualTo(BUCKET_COUNT);
    assertThat(size.bucketCapacity()).isEqualTo(BUCKET_CAPACITY);
    assertThat(size.fingerprintLength()).isEqualTo(FINGERPRINT_LENGTH);
  }

  @Test
  public void serializeAndDeserialize() {
    for (int i = 0; i < BUCKET_CAPACITY; i++) {
      long offset = (long) i * BUCKET_CAPACITY;
      for (int j = 0; j < BUCKET_CAPACITY; j++) {
        assertThat(table.insertWithReplacement(i, offset + j + 1)).isEmpty();
      }
    }

    SerializedCuckooFilterTable serializedTable = table.serialize();
    CuckooFilterTable existingTable = tableFactory.createExisting(serializedTable, new Random());

    for (int i = 0; i < BUCKET_CAPACITY; i++) {
      long offset = (long) i * BUCKET_CAPACITY;
      for (int j = 0; j < BUCKET_CAPACITY; j++) {
        assertThat(existingTable.contains(i, offset + j + 1)).isTrue();
      }
    }
  }

  @Test
  public void deserialize_failsWithInvalidSerialization() {
    SerializedCuckooFilterTable serializedTable =
        SerializedCuckooFilterTable.createFromByteArray(new byte[12]);

    String message =
        assertThrows(
                IllegalArgumentException.class,
                () -> tableFactory.createExisting(serializedTable, new Random()))
            .getMessage();
    assertThat(message).isEqualTo("Unable to parse the SerializedCuckooFilterTable.");
  }
}
