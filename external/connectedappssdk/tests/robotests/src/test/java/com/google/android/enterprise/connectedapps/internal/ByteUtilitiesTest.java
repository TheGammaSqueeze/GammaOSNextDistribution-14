/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.enterprise.connectedapps.internal;

import static com.google.common.truth.Truth.assertThat;

import android.os.Build.VERSION_CODES;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class ByteUtilitiesTest {

  private static final byte[] FIRST_ARRAY = new byte[] {1, 2};
  private static final byte[] SECOND_ARRAY = new byte[] {3, 4, 5};

  @Test
  public void joinByteArrays_resultIsMerged() {
    // This is the merge of FIRST_ARRAY and SECOND_ARRAY
    byte[] expectedResult = new byte[] {1, 2, 3, 4, 5};

    assertThat(ByteUtilities.joinByteArrays(FIRST_ARRAY, SECOND_ARRAY)).isEqualTo(expectedResult);
  }

  @Test
  public void joinByteArrays_emptyFirstArray_equalsSecondArray() {
    assertThat(ByteUtilities.joinByteArrays(new byte[0], SECOND_ARRAY)).isEqualTo(SECOND_ARRAY);
  }

  @Test
  public void joinByteArrays_emptySecondArray_equalsFirstArray() {
    assertThat(ByteUtilities.joinByteArrays(FIRST_ARRAY, new byte[0])).isEqualTo(FIRST_ARRAY);
  }
}
