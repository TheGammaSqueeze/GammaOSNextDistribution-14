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

import android.credentials.selection.FailureResult;
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
public class FailureResultTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void construction_success() {
        final String expectedErrorMessage = "msg";

        final FailureResult obj = new FailureResult(
                FailureResult.ERROR_CODE_DIALOG_CANCELED_BY_USER, expectedErrorMessage);

        assertThat(obj.getErrorCode()).isEqualTo(FailureResult.ERROR_CODE_DIALOG_CANCELED_BY_USER);
        assertThat(obj.getErrorMessage()).isEqualTo(expectedErrorMessage);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void errorCodes() {
        assertThat(FailureResult.ERROR_CODE_UI_FAILURE).isEqualTo(0);
        assertThat(FailureResult.ERROR_CODE_DIALOG_CANCELED_BY_USER).isEqualTo(1);
        assertThat(FailureResult.ERROR_CODE_CANCELED_AND_LAUNCHED_SETTINGS).isEqualTo(2);
    }
}
