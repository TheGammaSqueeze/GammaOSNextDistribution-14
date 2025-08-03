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

import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SerializedCuckooFilterTableTest {

  @Test
  public void construct_byteArrayCopied() {
    byte[] array = new byte[] {0, 1, 2, 3, 4};
    byte[] copied = Arrays.copyOf(array, array.length);

    SerializedCuckooFilterTable serializedTable =
        SerializedCuckooFilterTable.createFromByteArray(array);
    array[0] = 2;

    byte[] asByteArray = serializedTable.asByteArray();
    assertThat(asByteArray).isEqualTo(copied);
  }

  @Test
  public void asByteArray_byteArrayCopied() {
    byte[] array = new byte[] {0, 1, 2, 3, 4};

    SerializedCuckooFilterTable serializedTable =
        SerializedCuckooFilterTable.createFromByteArray(array);

    byte[] asByteArray = serializedTable.asByteArray();
    asByteArray[0] = 1;
    assertThat(serializedTable.asByteArray()).isEqualTo(array);
  }
}
