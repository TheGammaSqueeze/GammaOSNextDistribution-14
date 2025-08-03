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

import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CuckooFilterArrayTest {

  @Test
  public void createsNewArray_invalidLength() {
    String message =
        assertThrows(IllegalArgumentException.class, () -> new CuckooFilterArray(0, 20))
            .getMessage();
    assertThat(message)
        .isEqualTo(
            String.format(
                "length must be in range (0, %s).", (long) Integer.MAX_VALUE * Long.SIZE));
  }

  @Test
  public void createsNewArray_invalidBitsPerElement() {
    String message =
        assertThrows(IllegalArgumentException.class, () -> new CuckooFilterArray(5, 0))
            .getMessage();
    assertThat(message).isEqualTo("bitsPerElement must be in range [1, 64].");

    message =
        assertThrows(IllegalArgumentException.class, () -> new CuckooFilterArray(5, 65))
            .getMessage();
    assertThat(message).isEqualTo("bitsPerElement must be in range [1, 64].");
  }

  @Test
  public void createsNewArray_tooLarge() {
    String message =
        assertThrows(
                IllegalArgumentException.class,
                () -> new CuckooFilterArray((long) Integer.MAX_VALUE * 63, 20))
            .getMessage();
    assertThat(message)
        .isEqualTo(
            String.format(
                "Too large: could not create CuckooFilterArray with length %s and bitsPerElement"
                    + " 20.",
                (long) Integer.MAX_VALUE * 63));
  }

  @Test
  public void createsExistingArray_invalidLength() {
    String message =
        assertThrows(
                IllegalArgumentException.class, () -> new CuckooFilterArray(0, 20, new byte[1]))
            .getMessage();
    assertThat(message)
        .isEqualTo(
            String.format(
                "length must be in range (0, %s).", (long) Integer.MAX_VALUE * Long.SIZE));
  }

  @Test
  public void createsExistingArray_invalidBitsPerElement() {
    String message =
        assertThrows(IllegalArgumentException.class, () -> new CuckooFilterArray(5, 0, new byte[1]))
            .getMessage();
    assertThat(message).isEqualTo("bitsPerElement must be in range [1, 64].");

    message =
        assertThrows(
                IllegalArgumentException.class, () -> new CuckooFilterArray(5, 65, new byte[1]))
            .getMessage();
    assertThat(message).isEqualTo("bitsPerElement must be in range [1, 64].");
  }

  @Test
  public void creatExistingArray() {
    CuckooFilterArray array = new CuckooFilterArray(100, 20);
    array.set(0, 1);
    array.set(1, 2);

    byte[] byteArray = array.toByteArray();

    CuckooFilterArray existing = new CuckooFilterArray(100, 20, byteArray);

    assertThat(existing.getAsLong(0)).isEqualTo(1);
    assertThat(existing.getAsLong(1)).isEqualTo(2);
    for (int i = 2; i < existing.length(); i++) {
      assertThat(existing.getAsLong(i)).isEqualTo(0);
    }
  }

  @Test
  public void length() {
    CuckooFilterArray array = new CuckooFilterArray(100, 20);

    assertThat(array.length()).isEqualTo(100);
  }

  @Test
  public void bitsPerElement() {
    CuckooFilterArray array = new CuckooFilterArray(100, 20);

    assertThat(array.bitsPerElement()).isEqualTo(20);
  }

  @Test
  public void getAsLong_indexOutOfBounds() {
    CuckooFilterArray array = new CuckooFilterArray(100, 20);

    String message =
        assertThrows(IllegalArgumentException.class, () -> array.getAsLong(-1)).getMessage();
    assertThat(message).isEqualTo("Index is out of bounds: -1.");

    message = assertThrows(IllegalArgumentException.class, () -> array.getAsLong(100)).getMessage();
    assertThat(message).isEqualTo("Index is out of bounds: 100.");
  }

  @Test
  public void set_indexOutOfBounds() {
    CuckooFilterArray array = new CuckooFilterArray(100, 20);

    String message =
        assertThrows(IllegalArgumentException.class, () -> array.set(-1, 20)).getMessage();
    assertThat(message).isEqualTo("Index is out of bounds: -1.");

    message = assertThrows(IllegalArgumentException.class, () -> array.set(100, 20)).getMessage();
    assertThat(message).isEqualTo("Index is out of bounds: 100.");
  }

  @Test
  public void setAndGet() {
    for (int bitsPerElement = 1; bitsPerElement <= 64; bitsPerElement++) {
      CuckooFilterArray array = new CuckooFilterArray(100, bitsPerElement);

      for (int i = 0; i < array.length(); i++) {
        array.set(i, -1L - i);
      }

      for (int i = 0; i < array.length(); i++) {
        assertThat(array.getAsLong(i)).isEqualTo((-1L - i) & mask(bitsPerElement));
      }
    }
  }

  @Test
  public void setAndGet2() {
    for (int bitsPerElement = 1; bitsPerElement <= 64; bitsPerElement++) {
      CuckooFilterArray array = new CuckooFilterArray(10000, bitsPerElement);

      Random rand = new Random();
      long[] inserted = new long[(int) array.length()];
      for (int i = 0; i < array.length(); i++) {
        long v = rand.nextLong() & mask(bitsPerElement);
        inserted[i] = v;
        array.set(i, v);
      }

      for (int i = 0; i < array.length(); i++) {
        long v = rand.nextLong() & mask(bitsPerElement);
        inserted[i] = v;
        array.set(i, v);
      }

      for (int i = 0; i < array.length(); i += 2) {
        inserted[i] = 0;
        array.set(i, 0);
      }

      for (int i = 0; i < array.length(); i++) {
        assertThat(array.getAsLong(i)).isEqualTo(inserted[i]);
      }
    }
  }

  private static long mask(int length) {
    if (length == 64) {
      return -1;
    }
    return (1L << length) - 1;
  }
}
