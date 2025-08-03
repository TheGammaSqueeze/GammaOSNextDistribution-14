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

import android.app.slice.Slice;
import android.content.Intent;
import android.credentials.cts.unittests.TestUtils;
import android.credentials.selection.AuthenticationEntry;
import android.net.Uri;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class AuthenticationEntryTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String KEY = "key";
    private static final String SUBKEY = "subKey";
    private static final int STATUS = -1;
    private static final Intent FRAMEWORK_EXTRAS_INTENT = new Intent("mock_action");
    private static final Slice SLICE = new Slice.Builder(Uri.parse("foo://bar"), null)
            .addText("some text", null, List.of(Slice.HINT_TITLE)).build();

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testConstructor() {
        AuthenticationEntry entry =
                new AuthenticationEntry(KEY, SUBKEY, SLICE, STATUS, FRAMEWORK_EXTRAS_INTENT);

        assertThat(entry.getKey()).isEqualTo(KEY);
        assertThat(entry.getSubkey()).isEqualTo(SUBKEY);
        TestUtils.assertEquals(entry.getSlice(), SLICE);
        assertThat(entry.getStatus()).isEqualTo(STATUS);
        assertThat(entry.getFrameworkExtrasIntent().getAction()).isEqualTo(
                FRAMEWORK_EXTRAS_INTENT.getAction());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testWriteToParcel() {
        final AuthenticationEntry entry1 = new AuthenticationEntry(KEY, SUBKEY, SLICE, STATUS,
                FRAMEWORK_EXTRAS_INTENT);
        final AuthenticationEntry entry2 = TestUtils.cloneParcelable(entry1);

        assertThat(entry2.getKey()).isEqualTo(entry1.getKey());
        assertThat(entry2.getSubkey()).isEqualTo(entry1.getSubkey());
        TestUtils.assertEquals(entry2.getSlice(), entry1.getSlice());
        assertThat(entry2.getStatus()).isEqualTo(entry1.getStatus());
        assertThat(entry2.getFrameworkExtrasIntent().getAction()).isEqualTo(
                entry1.getFrameworkExtrasIntent().getAction());
    }
}
