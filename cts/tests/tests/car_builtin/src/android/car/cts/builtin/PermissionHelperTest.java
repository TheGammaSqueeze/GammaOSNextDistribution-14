/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.car.cts.builtin;

import static com.google.common.truth.Truth.assertThat;

import android.car.builtin.PermissionHelper;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class PermissionHelperTest {

    private static final String EXPECTED_MONITOR_INPUT_PERMISSION_STRING =
            "android.permission.MONITOR_INPUT";

    @Test
    public void testMonitorInputPermissionString() {
        assertThat(PermissionHelper.MONITOR_INPUT)
                .isEqualTo(EXPECTED_MONITOR_INPUT_PERMISSION_STRING);
    }
}
