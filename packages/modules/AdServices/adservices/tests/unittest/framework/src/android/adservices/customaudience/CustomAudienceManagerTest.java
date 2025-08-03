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

package android.adservices.customaudience;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link CustomAudienceManager} */
public class CustomAudienceManagerTest {
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testCustomAudienceManagerCtor_TPlus() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU);
        final Context context = ApplicationProvider.getApplicationContext();
        assertThat(CustomAudienceManager.get(context)).isNotNull();
        assertThat(context.getSystemService(CustomAudienceManager.class)).isNotNull();
    }

    @Test
    public void testCustomAudiencerManagerCtor_SMinus() {
        Assume.assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU);
        final Context context = ApplicationProvider.getApplicationContext();
        assertThat(CustomAudienceManager.get(context)).isNotNull();
        assertThat(context.getSystemService(CustomAudienceManager.class)).isNull();
    }
}
