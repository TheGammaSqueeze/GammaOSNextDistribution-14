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

import static android.credentials.cts.unittests.TestUtils.createTestBundle;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.SigningInfo;
import android.credentials.cts.unittests.TestUtils;
import android.platform.test.annotations.AppModeFull;
import android.service.credentials.BeginGetCredentialOption;
import android.service.credentials.BeginGetCredentialRequest;
import android.service.credentials.CallingAppInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class BeginGetCredentialsRequestTest {

    private static final List<BeginGetCredentialOption> sCredOptions = List.of(
            new BeginGetCredentialOption("id1", "type", createTestBundle()),
            new BeginGetCredentialOption("id2", "type2", createTestBundle()),
            new BeginGetCredentialOption("id3", "type3", createTestBundle()));

    private CallingAppInfo mCallingAppInfo;

    @Before
    public void setup() throws PackageManager.NameNotFoundException {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final SigningInfo signingInfo = context.getPackageManager().getPackageInfo(
                context.getOpPackageName(), PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_SIGNING_CERTIFICATES)).signingInfo;
        mCallingAppInfo = new CallingAppInfo(context.getOpPackageName(), signingInfo);
    }

    @Test
    public void testBuilder_setBeginGetCredentialOptions_null() {
        assertThrows(NullPointerException.class,
                () -> new BeginGetCredentialRequest.Builder().setBeginGetCredentialOptions(null));
    }

    @Test
    public void testBuilder_setBeginGetCredentialOptions_empty() {
        assertThrows(IllegalArgumentException.class,
                () -> new BeginGetCredentialRequest.Builder().setBeginGetCredentialOptions(
                        List.of()));
    }

    @Test
    public void testBuilder_setBeginGetCredentialOptions_nullOption() {
        assertThrows(NullPointerException.class,
                () -> new BeginGetCredentialRequest.Builder().setBeginGetCredentialOptions(
                        List.of(null)));
    }

    @Test
    public void testBuilder_addBeginGetCredentialOption_null() {
        assertThrows(NullPointerException.class,
                () -> new BeginGetCredentialRequest.Builder().addBeginGetCredentialOption(null));
    }

    @Test
    public void testBuilder_setBeginGetCredentialOptions() {
        final BeginGetCredentialRequest request =
                new BeginGetCredentialRequest.Builder().setBeginGetCredentialOptions(
                        sCredOptions).build();

        assertThat(request.getBeginGetCredentialOptions()).isSameInstanceAs(sCredOptions);
    }

    @Test
    public void testBuilder_addBeginGetCredentialOption() {
        final BeginGetCredentialOption opt = sCredOptions.get(0);
        final BeginGetCredentialRequest request =
                new BeginGetCredentialRequest.Builder().addBeginGetCredentialOption(opt).build();

        assertThat(request.getBeginGetCredentialOptions().get(0)).isSameInstanceAs(opt);
    }

    @Test
    public void testWriteToParcel() {
        final BeginGetCredentialRequest request1 =
                new BeginGetCredentialRequest.Builder().setBeginGetCredentialOptions(
                        sCredOptions).setCallingAppInfo(mCallingAppInfo).build();

        final BeginGetCredentialRequest request2 = TestUtils.cloneParcelable(request1);
        assertThat(request2.getCallingAppInfo().getPackageName()).isEqualTo(
                request1.getCallingAppInfo().getPackageName());

        final SigningInfo signing1 = request1.getCallingAppInfo().getSigningInfo();
        final SigningInfo signing2 = request2.getCallingAppInfo().getSigningInfo();
        assertThat(signing2.getApkContentsSigners()).isEqualTo(signing1.getApkContentsSigners());
        assertThat(signing2.getSigningCertificateHistory()).isEqualTo(
                signing1.getSigningCertificateHistory());
        assertThat(signing2.hasPastSigningCertificates()).isEqualTo(
                signing1.hasPastSigningCertificates());
        assertThat(signing2.hasMultipleSigners()).isEqualTo(signing2.hasMultipleSigners());
    }
}
