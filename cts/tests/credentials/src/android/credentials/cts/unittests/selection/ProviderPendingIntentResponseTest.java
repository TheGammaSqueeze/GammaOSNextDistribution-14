/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.credentials.cts.unittests.selection;

import static android.credentials.cts.unittests.TestUtils.cloneParcelable;
import static android.credentials.flags.Flags.FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.credentials.selection.ProviderPendingIntentResponse;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class ProviderPendingIntentResponseTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void construction_success() {
        final int expectedResultCode = -1;
        final Intent expectedResultData = new Intent();
        expectedResultData.putExtra("extra-name", "extra-value");

        final ProviderPendingIntentResponse obj = new ProviderPendingIntentResponse(
                expectedResultCode, expectedResultData);

        assertThat(obj.getResultCode()).isEqualTo(expectedResultCode);
        assertThat(obj.getResultData()).isEqualTo(expectedResultData);
        assertThat(obj.getResultData().getStringExtra("extra-name")).isEqualTo("extra-value");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void writeToParcel_success() {
        final int expectedResultCode = -1;
        final Intent expectedResultData = new Intent();
        expectedResultData.putExtra("key1", 1);
        expectedResultData.putExtra("key2", "val");
        final ProviderPendingIntentResponse fromObj = new ProviderPendingIntentResponse(
                expectedResultCode, expectedResultData);

        final ProviderPendingIntentResponse toObj = cloneParcelable(fromObj);

        assertThat(toObj.getResultCode()).isEqualTo(expectedResultCode);
        assertThat(toObj.getResultData().getIntExtra("key1", 0)).isEqualTo(1);
        assertThat(toObj.getResultData().getStringExtra("key2")).isEqualTo("val");
    }
}
