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

package android.deviceconfig.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import android.provider.UpdatableDeviceConfigServiceReadiness;

import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UpdatableDeviceConfigServiceReadinessTests {

    /**
     * Asserts that the readiness of the service is false until it has been implemented.
     */
    @Test
    public void assertReadinessState() {
        assumeTrue(SdkLevel.isAtLeastU());
        assertFalse(UpdatableDeviceConfigServiceReadiness.shouldStartUpdatableService());
    }
}
