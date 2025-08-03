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

package android.credentials.cts.unittests.service;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.slice.Slice;
import android.credentials.cts.unittests.TestUtils;
import android.net.Uri;
import android.platform.test.annotations.AppModeFull;
import android.service.credentials.BeginCreateCredentialResponse;
import android.service.credentials.CreateEntry;
import android.service.credentials.RemoteEntry;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class BeginCreateCredentialResponseTest {
    private final Slice mSlice = new Slice.Builder(Uri.parse("foo://bar"), null).addText(
            "some text", null, List.of(Slice.HINT_TITLE)).build();

    @Test
    public void testBuilder_setCreateEntries_nullEntries() {
        assertThrows(NullPointerException.class,
                () -> new BeginCreateCredentialResponse.Builder().setCreateEntries(null));
    }

    @Test
    public void testBuilder_setCreateEntries_emptyEntries() {
        assertThrows(IllegalArgumentException.class,
                () -> new BeginCreateCredentialResponse.Builder().setCreateEntries(List.of()));
    }

    @Test
    public void testBuilder_setCreateEntries() {
        final BeginCreateCredentialResponse.Builder builder =
                new BeginCreateCredentialResponse.Builder();

        final List<CreateEntry> entries = List.of(new CreateEntry(mSlice));
        assertThat(builder.setCreateEntries(entries)).isSameInstanceAs(builder);
        assertThat(builder.build().getCreateEntries()).isSameInstanceAs(entries);
    }

    @Test
    public void testBuilder_addCreateEntry_null() {
        assertThrows(NullPointerException.class,
                () -> new BeginCreateCredentialResponse.Builder().addCreateEntry(null));
    }

    @Test
    public void testBuilder_addCreateEntry() {
        final BeginCreateCredentialResponse.Builder builder =
                new BeginCreateCredentialResponse.Builder();

        final CreateEntry entry = new CreateEntry(mSlice);
        assertThat(builder.addCreateEntry(entry)).isSameInstanceAs(builder);
        assertThat(builder.build().getCreateEntries()).containsExactly(entry);
    }

    @Test
    public void testBuilder_setRemoteCreateEntry() {
        final BeginCreateCredentialResponse.Builder builder =
                new BeginCreateCredentialResponse.Builder();

        final List<CreateEntry> entries = List.of(new CreateEntry(mSlice));
        assertThat(builder.setCreateEntries(entries)).isSameInstanceAs(builder);

        final RemoteEntry remoteEntry = new RemoteEntry(mSlice);
        assertThat(builder.setRemoteCreateEntry(remoteEntry)).isSameInstanceAs(builder);
        assertThat(builder.build().getRemoteCreateEntry()).isSameInstanceAs(remoteEntry);
    }

    @Test
    public void testWriteParcelable() {
        final BeginCreateCredentialResponse response1 =
                new BeginCreateCredentialResponse.Builder()
                        .addCreateEntry(new CreateEntry(mSlice))
                        .setRemoteCreateEntry(new RemoteEntry(mSlice))
                        .build();


        final BeginCreateCredentialResponse response2 = TestUtils.cloneParcelable(response1);
        assertThat(response2.getCreateEntries().size()).isEqualTo(
                response1.getCreateEntries().size());
        assertThat(response2.getRemoteCreateEntry()).isNotNull();

        TestUtils.assertEquals(response2.getRemoteCreateEntry(), response1.getRemoteCreateEntry());
    }
}
