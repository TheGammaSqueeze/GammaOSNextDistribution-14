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

package android.credentials.cts.unittests;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.ComponentName;
import android.credentials.Credential;
import android.credentials.CredentialOption;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;


@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class CredentialOptionTest {

    @Test
    public void testBuilder_nullType() {
        assertThrows(IllegalArgumentException.class,
                () -> new CredentialOption.Builder(null, Bundle.EMPTY, Bundle.EMPTY));
    }

    @Test
    public void testBuilder_nullRetrievalData() {
        assertThrows(NullPointerException.class,
                () -> new CredentialOption.Builder(Credential.TYPE_PASSWORD_CREDENTIAL,
                        null, Bundle.EMPTY));
    }

    @Test
    public void testBuilder_nullQueryData() {
        assertThrows(NullPointerException.class,
                () -> new CredentialOption.Builder(Credential.TYPE_PASSWORD_CREDENTIAL,
                        Bundle.EMPTY, null));
    }

    @Test
    public void testBuilder() {
        final String type = Credential.TYPE_PASSWORD_CREDENTIAL;
        final Bundle retrievalData = new Bundle();
        retrievalData.putString("foo", "bar");

        final Bundle queryData = new Bundle();
        queryData.putString("bar", "baz");

        final boolean isSystemProviderRequiredDefaultExpected = false;

        final CredentialOption opt = new CredentialOption.Builder(type, retrievalData, queryData)
                .build();

        assertThat(opt.getType()).isEqualTo(type);
        TestUtils.assertEquals(opt.getCredentialRetrievalData(), retrievalData);
        TestUtils.assertEquals(opt.getCandidateQueryData(), queryData);
        assertThat(opt.isSystemProviderRequired()).isEqualTo(
                isSystemProviderRequiredDefaultExpected);
        assertThat(opt.getAllowedProviders()).isEmpty();
    }

    @Test
    public void testBuilder_setIsSystemProvider() {
        final String type = Credential.TYPE_PASSWORD_CREDENTIAL;
        final Bundle retrievalData = new Bundle();
        retrievalData.putString("foo", "bar");

        final Bundle queryData = new Bundle();
        queryData.putString("bar", "baz");

        final boolean isSystemProviderRequired = true;

        final CredentialOption opt = new CredentialOption.Builder(type, retrievalData, queryData)
                .setIsSystemProviderRequired(isSystemProviderRequired)
                .build();

        assertThat(opt.getType()).isEqualTo(type);
        TestUtils.assertEquals(opt.getCredentialRetrievalData(), retrievalData);
        TestUtils.assertEquals(opt.getCandidateQueryData(), queryData);
        assertThat(opt.isSystemProviderRequired()).isEqualTo(isSystemProviderRequired);
    }

    @Test
    public void testBuilder_setCandidateProviders() {
        final String type = Credential.TYPE_PASSWORD_CREDENTIAL;
        final Bundle retrievalData = new Bundle();
        retrievalData.putString("foo", "bar");

        final Bundle queryData = new Bundle();
        queryData.putString("bar", "baz");

        ComponentName provider1 = new ComponentName("provider1.package",
                "provider1.package.servicename");
        ComponentName provider2 = new ComponentName("provider2.package",
                "provider2.package.servicename");
        final Set<ComponentName> candidateProviders = new ArraySet<>();
        candidateProviders.add(provider1);
        candidateProviders.add(provider2);

        final boolean isSystemProviderRequiredDefault = false;

        final CredentialOption opt = new CredentialOption.Builder(type, retrievalData, queryData)
                .setAllowedProviders(candidateProviders)
                .build();

        assertThat(opt.getType()).isEqualTo(type);
        TestUtils.assertEquals(opt.getCredentialRetrievalData(), retrievalData);
        TestUtils.assertEquals(opt.getCandidateQueryData(), queryData);
        assertThat(opt.isSystemProviderRequired()).isEqualTo(isSystemProviderRequiredDefault);
        assertThat(opt.getAllowedProviders()).containsExactly(provider1, provider2);
    }

    @Test
    public void testBuilder_addAllowedProviders() {
        final String type = Credential.TYPE_PASSWORD_CREDENTIAL;
        final Bundle retrievalData = new Bundle();
        retrievalData.putString("foo", "bar");

        final Bundle queryData = new Bundle();
        queryData.putString("bar", "baz");

        ComponentName provider1 = new ComponentName("provider1.package",
                "provider1.package.servicename");
        ComponentName provider2 = new ComponentName("provider2.package",
                "provider2.package.servicename");

        final boolean isSystemProviderRequiredDefault = false;

        final CredentialOption opt = new CredentialOption.Builder(type, retrievalData, queryData)
                .addAllowedProvider(provider1)
                .addAllowedProvider(provider2)
                .build();

        assertThat(opt.getType()).isEqualTo(type);
        TestUtils.assertEquals(opt.getCredentialRetrievalData(), retrievalData);
        TestUtils.assertEquals(opt.getCandidateQueryData(), queryData);
        assertThat(opt.isSystemProviderRequired()).isEqualTo(isSystemProviderRequiredDefault);
        assertThat(opt.getAllowedProviders()).containsExactly(provider1, provider2);
    }


    @Test
    public void testWriteToParcel() {
        final String type = Credential.TYPE_PASSWORD_CREDENTIAL;
        final Bundle retrievalData = new Bundle();
        retrievalData.putString("foo", "bar");

        final Bundle queryData = new Bundle();
        queryData.putString("baz", "foo");

        ComponentName provider1 = new ComponentName("provider1.package",
                "provider1.package.servicename");
        ComponentName provider2 = new ComponentName("provider2.package",
                "provider2.package.servicename");

        final CredentialOption opt1 = new CredentialOption.Builder(type, retrievalData, queryData)
                .addAllowedProvider(provider1)
                .addAllowedProvider(provider2)
                .setIsSystemProviderRequired(true)
                .build();

        final CredentialOption opt2 = TestUtils.cloneParcelable(opt1);
        TestUtils.assertEquals(opt2, opt1);
    }

    @Test
    public void testWriteToParcelWithDefaults() {
        final String type = Credential.TYPE_PASSWORD_CREDENTIAL;
        final Bundle retrievalData = new Bundle();
        retrievalData.putString("foo", "bar");

        final Bundle queryData = new Bundle();
        queryData.putString("baz", "foo");

        final CredentialOption opt1 = new CredentialOption.Builder(type, retrievalData, queryData)
                .build();

        final CredentialOption opt2 = TestUtils.cloneParcelable(opt1);
        TestUtils.assertEquals(opt2, opt1);
    }
}
