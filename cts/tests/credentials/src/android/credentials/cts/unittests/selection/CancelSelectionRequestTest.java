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

import android.credentials.cts.unittests.TestUtils;
import android.credentials.selection.CancelSelectionRequest;
import android.credentials.selection.RequestToken;
import android.os.Binder;
import android.os.IBinder;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class CancelSelectionRequestTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final IBinder TOKEN = new Binder();
    private static final boolean SHOULD_SHOW_CANCELLATION_UI = true;
    private static final String PACKAGE_NAME = "mock_package";

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testConstructor() {
        final CancelSelectionRequest cancelUiRequest =
                new CancelSelectionRequest(new RequestToken(TOKEN), SHOULD_SHOW_CANCELLATION_UI,
                        PACKAGE_NAME);
        assertThat(cancelUiRequest.getRequestToken()).isEqualTo(new RequestToken(TOKEN));
        assertThat(cancelUiRequest.getPackageName()).isEqualTo(PACKAGE_NAME);
        assertThat(cancelUiRequest.shouldShowCancellationExplanation()).isEqualTo(
                SHOULD_SHOW_CANCELLATION_UI);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testWriteToParcel() {
        final CancelSelectionRequest cancelUiRequest1 =
                new CancelSelectionRequest(new RequestToken(TOKEN), SHOULD_SHOW_CANCELLATION_UI,
                        PACKAGE_NAME);
        final CancelSelectionRequest cancelUiRequest2 = TestUtils.cloneParcelable(cancelUiRequest1);
        assertThat(cancelUiRequest2.getRequestToken()).isEqualTo(new RequestToken(TOKEN));
        assertThat(cancelUiRequest2.getPackageName()).isEqualTo(PACKAGE_NAME);
        assertThat(cancelUiRequest2.shouldShowCancellationExplanation())
                .isEqualTo(SHOULD_SHOW_CANCELLATION_UI);
    }
}
