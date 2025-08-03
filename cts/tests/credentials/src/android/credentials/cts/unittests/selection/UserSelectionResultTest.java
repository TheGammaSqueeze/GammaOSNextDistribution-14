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

import static android.credentials.flags.Flags.FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.Intent;
import android.credentials.selection.ProviderPendingIntentResponse;
import android.credentials.selection.UserSelectionResult;
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
public class UserSelectionResultTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void construction_success() {
        final String expectedProviderId = "id";
        final String expectedEntryKey = "entry";
        final String expectedEntrySubkey = "subkey";
        final ProviderPendingIntentResponse expectedProviderPendingIntentResponse =
                new ProviderPendingIntentResponse(0, new Intent());

        final UserSelectionResult obj = new UserSelectionResult(expectedProviderId,
                expectedEntryKey,
                expectedEntrySubkey, expectedProviderPendingIntentResponse);

        assertThat(obj.getProviderId()).isEqualTo(expectedProviderId);
        assertThat(obj.getEntryKey()).isEqualTo(expectedEntryKey);
        assertThat(obj.getEntrySubkey()).isEqualTo(expectedEntrySubkey);
        assertThat(obj.getPendingIntentProviderResponse()).isEqualTo(
                expectedProviderPendingIntentResponse);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void construction_nullProviderId_throws() {
        assertThrows(IllegalArgumentException.class, () -> new UserSelectionResult(null, "key",
                "subkey", null));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void construction_emptyProviderId_throws() {
        assertThrows(IllegalArgumentException.class, () -> new UserSelectionResult(
                "", "key", "subkey", null));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void construction_nullKey_throws() {
        assertThrows(IllegalArgumentException.class, () -> new UserSelectionResult("id", null,
                "subkey", null));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void construction_emptyKey_throws() {
        assertThrows(IllegalArgumentException.class, () -> new UserSelectionResult(
                "id", "", "subkey", null));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void construction_nullSubkey_throws() {
        assertThrows(IllegalArgumentException.class, () -> new UserSelectionResult("id", "key",
                null, null));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void construction_emptySubkey_throws() {
        assertThrows(IllegalArgumentException.class, () -> new UserSelectionResult(
                "id", "key", "", null));
    }
}
