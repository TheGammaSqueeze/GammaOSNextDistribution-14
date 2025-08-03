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

import static android.credentials.cts.unittests.TestUtils.assertEquals;
import static android.credentials.cts.unittests.TestUtils.createTestBundle;
import static android.credentials.flags.Flags.FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED;
import static android.credentials.selection.RequestInfo.TYPE_CREATE;
import static android.credentials.selection.RequestInfo.TYPE_GET;

import static com.google.common.truth.Truth.assertThat;

import android.credentials.CreateCredentialRequest;
import android.credentials.Credential;
import android.credentials.CredentialOption;
import android.credentials.GetCredentialRequest;
import android.credentials.cts.unittests.TestUtils;
import android.credentials.selection.RequestInfo;
import android.credentials.selection.RequestToken;
import android.os.Binder;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.List;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class RequestInfoTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final IBinder TOKEN = new Binder();

    private static final List<String> DEFAULT_PROVIDER_IDS = List.of("default_provider_1",
            "default_provider_2");

    private static final String APP_PACKAGE_NAME = "mock_package";

    private static final boolean HAS_PERMISSION_TO_OVERRIDE_DEFAULT = true;
    private static final boolean IS_SHOW_ALL_OPTIONS_REQUESTED = true;

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testConstructCreateRequestInfo() {
        final String type = Credential.TYPE_PASSWORD_CREDENTIAL;
        final Bundle data = new Bundle();
        data.putString("foo", "bar");

        final CreateCredentialRequest createCredentialRequest =
                new CreateCredentialRequest.Builder(type, data, data).build();
        RequestInfo requestInfo = RequestInfo.newCreateRequestInfo(
                TOKEN, createCredentialRequest, APP_PACKAGE_NAME,
                HAS_PERMISSION_TO_OVERRIDE_DEFAULT, DEFAULT_PROVIDER_IDS,
                IS_SHOW_ALL_OPTIONS_REQUESTED);

        assertThat(requestInfo.getRequestToken()).isEqualTo(new RequestToken(TOKEN));
        assertThat(requestInfo.getType()).isEqualTo(TYPE_CREATE);
        assertThat(requestInfo.getPackageName()).isEqualTo(APP_PACKAGE_NAME);
        assertThat(requestInfo.hasPermissionToOverrideDefault())
                .isEqualTo(HAS_PERMISSION_TO_OVERRIDE_DEFAULT);
        assertThat(requestInfo.isShowAllOptionsRequested())
                .isEqualTo(IS_SHOW_ALL_OPTIONS_REQUESTED);
        assertThat(requestInfo.getDefaultProviderIds())
                .containsExactlyElementsIn(DEFAULT_PROVIDER_IDS);
        assertThat(requestInfo.getGetCredentialRequest()).isNull();
        assertThat(requestInfo.getCreateCredentialRequest()).isEqualTo(createCredentialRequest);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testConstructGetRequestInfo() {
        final Bundle data = new Bundle();
        data.putString("foo", "bar");
        List<CredentialOption> credentialOptions = List.of(
                new CredentialOption.Builder("type1", createTestBundle(), createTestBundle())
                        .setIsSystemProviderRequired(true).build(),
                new CredentialOption.Builder("type2", createTestBundle(), createTestBundle())
                        .build());

        final GetCredentialRequest getCredentialRequest =
                new GetCredentialRequest.Builder(data)
                        .setCredentialOptions(credentialOptions).build();

        RequestInfo requestInfo = RequestInfo.newGetRequestInfo(
                TOKEN, getCredentialRequest, APP_PACKAGE_NAME, HAS_PERMISSION_TO_OVERRIDE_DEFAULT,
                IS_SHOW_ALL_OPTIONS_REQUESTED);

        assertThat(requestInfo.getRequestToken()).isEqualTo(new RequestToken(TOKEN));
        assertThat(requestInfo.getType()).isEqualTo(TYPE_GET);
        assertThat(requestInfo.getPackageName()).isEqualTo(APP_PACKAGE_NAME);
        assertThat(requestInfo.getGetCredentialRequest().getCredentialOptions())
                .containsExactlyElementsIn(credentialOptions);
        assertThat(requestInfo.hasPermissionToOverrideDefault())
                .isEqualTo(HAS_PERMISSION_TO_OVERRIDE_DEFAULT);
        assertThat(requestInfo.isShowAllOptionsRequested())
                .isEqualTo(IS_SHOW_ALL_OPTIONS_REQUESTED);
        assertThat(requestInfo.getDefaultProviderIds()).isEmpty();
        assertThat(requestInfo.getCreateCredentialRequest()).isNull();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testWriteToParcel() {
        final Bundle credentialData = new Bundle();
        credentialData.putString("foo", "bar");
        final Bundle candidateQueryData = new Bundle();
        candidateQueryData.putInt("foo", 20);
        candidateQueryData.putLong("bar", 20);
        final IBinder token = new Binder();

        final CreateCredentialRequest createCredentialRequest =
                new CreateCredentialRequest.Builder("test type", credentialData,
                        candidateQueryData).build();
        RequestInfo requestInfo1 = RequestInfo.newCreateRequestInfo(
                token, createCredentialRequest, "package name",
                /*hasPermissionToOverrideDefault=*/ false, new ArrayList<>(),
                /*isShowAllOptionsRequested=*/ true);

        RequestInfo requestInfo2 = TestUtils.cloneParcelable(requestInfo1);
        assertThat(requestInfo2.getRequestToken()).isEqualTo(new RequestToken(token));
        assertThat(requestInfo2.getType()).isEqualTo(TYPE_CREATE);
        assertThat(requestInfo2.getPackageName()).isEqualTo("package name");
        assertThat(requestInfo2.getCreateCredentialRequest().getType()).isEqualTo("test type");
        assertEquals(requestInfo2.getCreateCredentialRequest().getCredentialData(), credentialData);
        assertEquals(requestInfo2.getCreateCredentialRequest().getCandidateQueryData(),
                candidateQueryData);
        assertThat(requestInfo2.hasPermissionToOverrideDefault()).isFalse();
        assertThat(requestInfo2.isShowAllOptionsRequested()).isTrue();
        assertThat(requestInfo2.getDefaultProviderIds()).isEmpty();
    }
}
