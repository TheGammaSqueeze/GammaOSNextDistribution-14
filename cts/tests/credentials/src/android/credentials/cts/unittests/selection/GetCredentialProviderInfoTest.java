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
import android.credentials.selection.AuthenticationEntry;
import android.credentials.selection.Entry;
import android.credentials.selection.GetCredentialProviderInfo;
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
public class GetCredentialProviderInfoTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final Slice SLICE = new Slice.Builder(Uri.parse("foo://bar"), null)
            .addText("some text", null, List.of(Slice.HINT_TITLE)).build();

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testConstructor() {
        final List<Entry> credEntryList = new ArrayList<>();
        credEntryList.add(new Entry("key1", "subkey1", SLICE, new Intent("action1")));
        credEntryList.add(new Entry("key2", "subkey1", new Slice.Builder(Uri.parse("scheme://foo"),
                null)
                .addText("text 2", null, List.of(Slice.HINT_LARGE)).build(),
                new Intent("action2")));
        credEntryList.add(new Entry("key3", "subkey", new Slice.Builder(Uri.parse("scheme://bar"),
                null)
                .addText("text 3", null, List.of(Slice.HINT_LIST_ITEM)).build(),
                new Intent("action3")));
        final List<Entry> actionList = new ArrayList<>();
        credEntryList.add(new Entry("key4", "subkey1", new Slice.Builder(Uri.parse("foo://action1"),
                null)
                .addText("text 4", null, List.of(Slice.HINT_TITLE)).build(),
                new Intent("action4")));
        credEntryList.add(new Entry("key4", "subkey2", new Slice.Builder(Uri.parse("foo://action2"),
                null)
                .addText("text 5", null, List.of(Slice.HINT_LARGE)).build(),
                new Intent("action5")));
        final List<AuthenticationEntry> authEntryList = new ArrayList<>();
        authEntryList.add(
                new AuthenticationEntry("authkey", "1", SLICE, AuthenticationEntry.STATUS_LOCKED,
                        new Intent("action6")));
        authEntryList.add(new AuthenticationEntry("authkey", "2", SLICE,
                AuthenticationEntry.STATUS_UNLOCKED_BUT_EMPTY_LESS_RECENT, new Intent("action7")));
        final Entry remoteEntry = new Entry("remotekey", "1", SLICE, new Intent("action8"));

        GetCredentialProviderInfo getCredentialProviderInfo =
                new GetCredentialProviderInfo.Builder("com.android.test/.ProviderService")
                        .setCredentialEntries(credEntryList)
                        .setActionChips(actionList)
                        .setRemoteEntry(remoteEntry)
                        .setAuthenticationEntries(authEntryList)
                        .build();

        assertThat(getCredentialProviderInfo.getProviderName()).isEqualTo(
                "com.android.test/.ProviderService");
        assertThat(getCredentialProviderInfo.getCredentialEntries()).containsExactlyElementsIn(
                credEntryList).inOrder();
        assertThat(getCredentialProviderInfo.getActionChips()).containsExactlyElementsIn(
                actionList).inOrder();
        assertThat(getCredentialProviderInfo.getAuthenticationEntries()).containsExactlyElementsIn(
                authEntryList).inOrder();
        assertThat(getCredentialProviderInfo.getRemoteEntry()).isEqualTo(remoteEntry);
    }
}

