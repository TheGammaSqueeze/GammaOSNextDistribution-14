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
package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.adservices.AdServicesState;
import android.adservices.AdServicesVersion;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * CTS test for APIs provided by {@link android.adservices.AdServicesVersion} and other generic
 * features.
 */
@RunWith(AndroidJUnit4.class)
public class AdServicesJUnit4DeviceTest extends CtsAdServicesDeviceTestCase {
    @Test
    public void testApiVersion() {
        // Note that this version constant has been @removed from the public API,
        // but there is some value in keeping the test around to verify
        // - that the test APK can access the field (even though it's hidden)
        // - that the value is kept the same
        assertThat(AdServicesVersion.API_VERSION).isAtLeast(2);
    }

    @Test
    public void testAdServicesState() {
        assertThat(AdServicesState.isAdServicesStateEnabled()).isTrue();
    }

    @Test
    public void testBinderServiceIsPublished() {
        assumeTrue("SDK level must be at least U", SdkLevel.isAtLeastU());
        String cmd = "service check adservices_manager";

        assertWithMessage("output of '%s'", cmd)
                .that(ShellUtils.runShellCommand(cmd))
                .doesNotContain("not found");
    }
}

