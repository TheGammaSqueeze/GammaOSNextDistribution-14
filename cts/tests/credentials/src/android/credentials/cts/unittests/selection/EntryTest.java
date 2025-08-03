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
import android.credentials.selection.Entry;
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
public class EntryTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testConstructor() {
        Slice slice = new Slice.Builder(Uri.parse("foo://bar"), null)
                .addText("some text", null, List.of(Slice.HINT_TITLE)).build();
        Intent intent = new Intent("testConstructor");
        intent.putExtra("extra1", "val1");
        intent.putExtra("extra2", 10);

        Entry testEntry = new Entry("key", "subkey", slice, intent);

        assertThat(testEntry.getKey()).isEqualTo("key");
        assertThat(testEntry.getSubkey()).isEqualTo("subkey");
        TestUtils.assertEquals(testEntry.getSlice(), slice);
        assertThat(testEntry.getFrameworkExtrasIntent()).isEqualTo(intent);
        assertThat(testEntry.getFrameworkExtrasIntent().getStringExtra("extra1")).isEqualTo("val1");
        assertThat(testEntry.getFrameworkExtrasIntent().getIntExtra("extra2", -1)).isEqualTo(10);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testWriteToParcel() {
        Slice slice = new Slice.Builder(Uri.parse("bar://foo"), null)
                .addText("text", "subtype", List.of(Slice.HINT_ERROR)).build();
        Intent intent = new Intent("test action");
        intent.putExtra("extra1", "val");
        intent.putExtra("extra2", 20L);
        final Entry entry1 = new Entry("k1", "k2", slice, intent);

        final Entry entry2 = TestUtils.cloneParcelable(entry1);

        assertThat(entry2.getKey()).isEqualTo("k1");
        assertThat(entry2.getSubkey()).isEqualTo("k2");
        TestUtils.assertEquals(entry2.getSlice(), slice);
        assertThat(entry2.getFrameworkExtrasIntent().getAction()).isEqualTo("test action");
        assertThat(entry2.getFrameworkExtrasIntent().getStringExtra("extra1")).isEqualTo("val");
        assertThat(entry2.getFrameworkExtrasIntent().getLongExtra("extra2", -1L)).isEqualTo(20L);
    }
}
