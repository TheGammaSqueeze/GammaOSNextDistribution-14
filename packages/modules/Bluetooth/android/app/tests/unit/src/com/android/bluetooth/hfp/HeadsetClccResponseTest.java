/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.bluetooth.hfp;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class HeadsetClccResponseTest {
    private static final int TEST_INDEX = 1;
    private static final int TEST_DIRECTION = 1;
    private static final int TEST_STATUS = 1;
    private static final int TEST_MODE = 1;
    private static final boolean TEST_MPTY = true;
    private static final String TEST_NUMBER = "111-1111-1111";
    private static final int TEST_TYPE = 1;

    @Test
    public void constructor() {
        HeadsetClccResponse response = new HeadsetClccResponse(TEST_INDEX, TEST_DIRECTION,
                TEST_STATUS, TEST_MODE, TEST_MPTY, TEST_NUMBER, TEST_TYPE);

        assertThat(response.mIndex).isEqualTo(TEST_INDEX);
        assertThat(response.mDirection).isEqualTo(TEST_DIRECTION);
        assertThat(response.mStatus).isEqualTo(TEST_STATUS);
        assertThat(response.mMode).isEqualTo(TEST_MODE);
        assertThat(response.mMpty).isEqualTo(TEST_MPTY);
        assertThat(response.mNumber).isEqualTo(TEST_NUMBER);
        assertThat(response.mType).isEqualTo(TEST_TYPE);
    }

    @Test
    public void buildString() {
        HeadsetClccResponse response = new HeadsetClccResponse(TEST_INDEX, TEST_DIRECTION,
                TEST_STATUS, TEST_MODE, TEST_MPTY, TEST_NUMBER, TEST_TYPE);
        StringBuilder builder = new StringBuilder();

        response.buildString(builder);

        String expectedString =
                response.getClass().getSimpleName() + "[index=" + TEST_INDEX + ", direction="
                        + TEST_DIRECTION + ", status=" + TEST_STATUS + ", callMode=" + TEST_MODE
                        + ", isMultiParty=" + TEST_MPTY + ", number=" + "***" + ", type="
                        + TEST_TYPE + "]";
        assertThat(response.toString()).isEqualTo(expectedString);
    }

    @Test
    public void buildString_withNoNumber() {
        HeadsetClccResponse response = new HeadsetClccResponse(TEST_INDEX, TEST_DIRECTION,
                TEST_STATUS, TEST_MODE, TEST_MPTY, null, TEST_TYPE);
        StringBuilder builder = new StringBuilder();

        response.buildString(builder);

        String expectedString =
                response.getClass().getSimpleName() + "[index=" + TEST_INDEX + ", direction="
                        + TEST_DIRECTION + ", status=" + TEST_STATUS + ", callMode=" + TEST_MODE
                        + ", isMultiParty=" + TEST_MPTY + ", number=" + "null" + ", type="
                        + TEST_TYPE + "]";
        assertThat(response.toString()).isEqualTo(expectedString);
    }
}
