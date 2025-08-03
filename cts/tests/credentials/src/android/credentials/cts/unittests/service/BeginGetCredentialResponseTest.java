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

import android.app.slice.Slice;
import android.credentials.cts.unittests.TestUtils;
import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.service.credentials.Action;
import android.service.credentials.BeginGetCredentialOption;
import android.service.credentials.BeginGetCredentialResponse;
import android.service.credentials.CredentialEntry;
import android.service.credentials.RemoteEntry;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;


@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class BeginGetCredentialResponseTest {
    private static final BeginGetCredentialOption sCredOption2 =
            new BeginGetCredentialOption("id2", "type2", Bundle.EMPTY);

    private static final Slice sSlice = new Slice.Builder(Uri.parse("foo://bar"),
            null).addText(
            "some text", null, List.of(Slice.HINT_TITLE)).build();

    private static final Action sAction = new Action(sSlice);
    private static final Action sAuthAction = new Action(sSlice);

    private static final RemoteEntry sRemoteCred = new RemoteEntry(sSlice);
    private static final CredentialEntry sCred = new CredentialEntry(sCredOption2, sSlice);
    private static final CredentialEntry sCred2 = new CredentialEntry(sCredOption2.getId(),
            sCredOption2.getType(), sSlice);

    @Test
    public void testBuilder_addCredentialEntry_null() {
        Assert.assertThrows(NullPointerException.class,
                () -> new BeginGetCredentialResponse.Builder().addCredentialEntry(null));
    }

    @Test
    public void testBuilder_addAuthenticationAction_null() {
        Assert.assertThrows(NullPointerException.class,
                () -> new BeginGetCredentialResponse.Builder().addAuthenticationAction(null));
    }

    @Test
    public void testBuilder_addAction_null() {
        Assert.assertThrows(NullPointerException.class,
                () -> new BeginGetCredentialResponse.Builder().addAction(null));
    }

    @Test
    public void testBuilder_setActions_null() {
        Assert.assertThrows(NullPointerException.class,
                () -> new BeginGetCredentialResponse.Builder().setActions(null));
    }

    @Test
    public void testBuilder_setCredentialEntries_null() {
        Assert.assertThrows(NullPointerException.class,
                () -> new BeginGetCredentialResponse.Builder().setCredentialEntries(null));
    }

    @Test
    public void testBuilder_setAuthenticationActions_null() {
        Assert.assertThrows(NullPointerException.class,
                () -> new BeginGetCredentialResponse.Builder().setAuthenticationActions(null));
    }

    @Test
    public void testBuilder_add_build() {
        final BeginGetCredentialResponse response =
                new BeginGetCredentialResponse.Builder().setRemoteCredentialEntry(
                        sRemoteCred).addCredentialEntry(sCred)
                        .addCredentialEntry(sCred2).addAction(sAction)
                        .addAuthenticationAction(sAuthAction).build();

        assertThat(response.getRemoteCredentialEntry()).isSameInstanceAs(sRemoteCred);
        assertThat(response.getCredentialEntries()).containsExactlyElementsIn(
                List.of(sCred, sCred2));
        assertThat(response.getActions()).containsExactly(sAction);
        assertThat(response.getAuthenticationActions()).containsExactly(sAuthAction);
    }

    @Test
    public void testBuilder_set_build() {
        final BeginGetCredentialResponse response =
                new BeginGetCredentialResponse.Builder().setRemoteCredentialEntry(
                        sRemoteCred).setCredentialEntries(List.of(sCred)).setActions(
                        List.of(sAction)).setAuthenticationActions(List.of(sAuthAction)).build();

        assertThat(response.getRemoteCredentialEntry()).isSameInstanceAs(sRemoteCred);
        assertThat(response.getCredentialEntries()).containsExactly(sCred);
        assertThat(response.getActions()).containsExactly(sAction);
        assertThat(response.getAuthenticationActions()).containsExactly(sAuthAction);
    }

    @Test
    public void testWriteToParcel() {
        final BeginGetCredentialResponse resp1 =
                new BeginGetCredentialResponse.Builder().setRemoteCredentialEntry(
                        sRemoteCred).setCredentialEntries(List.of(sCred)).setActions(
                        List.of(sAction)).setAuthenticationActions(List.of(sAuthAction)).build();

        final BeginGetCredentialResponse resp2 = TestUtils.cloneParcelable(resp1);
        TestUtils.assertEquals(resp2.getRemoteCredentialEntry(), resp1.getRemoteCredentialEntry());

        assertThat(resp2.getCredentialEntries().size()).isEqualTo(1);
        assertThat(resp2.getCredentialEntries().size()).isEqualTo(
                resp1.getCredentialEntries().size());
        TestUtils.assertEquals(resp2.getCredentialEntries().get(0),
                resp1.getCredentialEntries().get(0));

        assertThat(resp2.getActions().size()).isEqualTo(1);
        assertThat(resp2.getActions().size()).isEqualTo(resp1.getActions().size());
        TestUtils.assertEquals(resp2.getActions().get(0), resp1.getActions().get(0));

        assertThat(resp2.getAuthenticationActions().size()).isEqualTo(1);
        assertThat(resp2.getAuthenticationActions().size()).isEqualTo(
                resp1.getAuthenticationActions().size());
        TestUtils.assertEquals(resp2.getAuthenticationActions().get(0),
                resp1.getAuthenticationActions().get(0));
    }
}
