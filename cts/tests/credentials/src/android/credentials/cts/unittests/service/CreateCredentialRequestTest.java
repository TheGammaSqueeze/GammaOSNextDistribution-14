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

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.SigningInfo;
import android.credentials.Credential;
import android.credentials.cts.unittests.TestUtils;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.service.credentials.CallingAppInfo;
import android.service.credentials.CreateCredentialRequest;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class CreateCredentialRequestTest {

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
    public void testConstructor_nullCallingInfo() {
        assertThrows(NullPointerException.class,
                () -> new CreateCredentialRequest(null, Credential.TYPE_PASSWORD_CREDENTIAL,
                        Bundle.EMPTY));
    }

    @Test
    public void testConstructor_nullType() {
        assertThrows(IllegalArgumentException.class,
                () -> new CreateCredentialRequest(mCallingAppInfo, null, Bundle.EMPTY));
    }

    @Test
    public void testConstructor_emptyType() {
        assertThrows(IllegalArgumentException.class,
                () -> new CreateCredentialRequest(mCallingAppInfo, "", Bundle.EMPTY));
    }

    @Test
    public void testConstructor_nullData() {
        assertThrows(NullPointerException.class, () -> new CreateCredentialRequest(mCallingAppInfo,
                Credential.TYPE_PASSWORD_CREDENTIAL, null));
    }

    @Test
    public void testConstructor() {
        final String type = Credential.TYPE_PASSWORD_CREDENTIAL;
        final Bundle data = new Bundle();
        data.putString("foo", "bar");

        final CreateCredentialRequest request = new CreateCredentialRequest(mCallingAppInfo, type,
                data);
        assertThat(request.getType()).isEqualTo(type);
        assertThat(request.getCallingAppInfo()).isSameInstanceAs(mCallingAppInfo);
        assertThat(request.getData()).isSameInstanceAs(data);
    }

    @Test
    public void testWriteToParcel() {
        final String type = Credential.TYPE_PASSWORD_CREDENTIAL;
        final Bundle data = new Bundle();
        data.putString("foo", "bar");

        final CreateCredentialRequest request1 = new CreateCredentialRequest(mCallingAppInfo, type,
                data);
        final CreateCredentialRequest request2 = TestUtils.cloneParcelable(request1);
        assertThat(request2.getType()).isEqualTo(request1.getType());

        TestUtils.assertEquals(request2.getCallingAppInfo(), request1.getCallingAppInfo());
    }
}
