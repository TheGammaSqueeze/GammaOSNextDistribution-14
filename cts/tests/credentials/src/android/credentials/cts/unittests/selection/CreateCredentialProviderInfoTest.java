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

import android.app.slice.Slice;
import android.content.Intent;
import android.credentials.selection.CreateCredentialProviderInfo;
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

import java.util.ArrayList;
import java.util.List;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class CreateCredentialProviderInfoTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final Slice SLICE = new Slice.Builder(Uri.parse("foo://bar"), null)
            .addText("some text", null, List.of(Slice.HINT_TITLE)).build();

    private static final Entry REMOTE_ENTRY = new Entry("remote_entry_key",
            "remote_entry_subkey", SLICE, new Intent("fake action"));

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testConstruction() {
        final List<Entry> saveEntryList = new ArrayList<>();
        saveEntryList.add(new Entry("k2", "subkey1", new Slice.Builder(Uri.parse("scheme://foo"),
                null)
                .addText("test text", null, List.of(Slice.HINT_KEYWORDS)).build(),
                new Intent("action2")));
        saveEntryList.add(new Entry("key3", "subkey", new Slice.Builder(Uri.parse("scheme://bar"),
                null)
                .addText("text", null, List.of(Slice.HINT_SEE_MORE)).build(),
                new Intent("action3")));
        saveEntryList.add(new Entry("k1", "subkey1", SLICE, new Intent("action1")));
        CreateCredentialProviderInfo createCredentialProviderInfo =
                new CreateCredentialProviderInfo.Builder("fake provider")
                        .setSaveEntries(saveEntryList)
                        .setRemoteEntry(REMOTE_ENTRY)
                        .build();
        assertThat(createCredentialProviderInfo.getProviderName()).isEqualTo("fake provider");
        assertThat(createCredentialProviderInfo.getSaveEntries()).containsExactlyElementsIn(
                saveEntryList).inOrder();
        assertThat(createCredentialProviderInfo.getRemoteEntry()).isEqualTo((REMOTE_ENTRY));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testBuilder_nullProviderInfo_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new CreateCredentialProviderInfo.Builder(null)
                        .setSaveEntries(new ArrayList<>())
                        .setRemoteEntry(REMOTE_ENTRY)
                        .build());
    }
}
