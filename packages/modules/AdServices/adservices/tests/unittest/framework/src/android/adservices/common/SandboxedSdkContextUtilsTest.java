/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.adservices.common;

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.SandboxedSdkContext;
import android.content.Context;
import android.os.Build;
import android.test.mock.MockContext;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import org.junit.Assume;
import org.junit.Test;

/** Unit tests for {@link SandboxedSdkContextUtils} */
@SmallTest
public final class SandboxedSdkContextUtilsTest {
    @Test
    public void testGetAsSandboxedSdkContext_inputIsNotSandboxedSdkContext() {
        assertThat(SandboxedSdkContextUtils.getAsSandboxedSdkContext(null)).isNull();
        assertThat(SandboxedSdkContextUtils.getAsSandboxedSdkContext(new MockContext())).isNull();

        final Context realContext = ApplicationProvider.getApplicationContext();
        assertThat(SandboxedSdkContextUtils.getAsSandboxedSdkContext(realContext)).isNull();
    }

    @Test
    public void testGetAsSandboxedSdkContext_inputIsSandboxedSdkContext() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU);
        Context context = ApplicationProvider.getApplicationContext();
        Context sandboxedSdkContext =
                new SandboxedSdkContext(
                        /* baseContext = */ context,
                        /* classLoader = */ context.getClassLoader(),
                        /* clientPackageName = */ context.getPackageName(),
                        /* info = */ context.getApplicationInfo(),
                        /* sdkName = */ "sdkName",
                        /* sdkCeDataDir = */ null,
                        /* sdkDeDataDir = */ null,
                        /* isCustomizedSdkContextEnabled = */ false);
        assertThat(SandboxedSdkContextUtils.getAsSandboxedSdkContext(sandboxedSdkContext))
                .isSameInstanceAs(sandboxedSdkContext);
    }
}
