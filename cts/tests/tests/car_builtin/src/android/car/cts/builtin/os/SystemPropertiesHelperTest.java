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

package android.car.cts.builtin.os;

import static org.junit.Assert.assertThrows;

import android.car.builtin.os.SystemPropertiesHelper;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SystemPropertiesHelperTest {
    private static final String TAG = SystemPropertiesHelperTest.class.getSimpleName();

    // a temporary SystemProperty for CTS.
    private static final String CTS_TEST_PROPERTY_KEY = "dev.android.car.test.cts.builtin_test";
    private static final String CTS_TEST_PROPERTY_VAL = "SystemPropertiesHelperTest";

    @Test
    public void testSet_throwsException() {
        // system properties are protected by SELinux policies. Though properties with "dev."
        // prefix are accessible via the shell domain and car shell (carservice_app) domain,
        // they are not accessible via CTS. The java RuntimeException is expected due to access
        // permission deny.
        assertThrows(RuntimeException.class,
                () -> SystemPropertiesHelper.set(CTS_TEST_PROPERTY_KEY, CTS_TEST_PROPERTY_VAL));
    }
}
