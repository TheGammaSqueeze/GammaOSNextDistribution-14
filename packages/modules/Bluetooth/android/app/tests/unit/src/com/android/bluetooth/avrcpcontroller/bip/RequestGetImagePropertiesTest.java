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

package com.android.bluetooth.avrcpcontroller;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RequestGetImagePropertiesTest {
    private static final String TEST_IMAGE_HANDLE = "test_image_handle";

    @Test
    public void constructor() {
        RequestGetImageProperties requestGetImageProperties = new RequestGetImageProperties(
                TEST_IMAGE_HANDLE);

        assertThat(requestGetImageProperties.getImageHandle()).isEqualTo(TEST_IMAGE_HANDLE);
    }

    @Test
    public void getType() {
        RequestGetImageProperties requestGetImageProperties = new RequestGetImageProperties(
                TEST_IMAGE_HANDLE);

        assertThat(requestGetImageProperties.getType()).isEqualTo(
                BipRequest.TYPE_GET_IMAGE_PROPERTIES);
    }
}