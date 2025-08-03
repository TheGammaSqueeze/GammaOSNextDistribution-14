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

import android.credentials.Credential;
import android.credentials.GetCredentialResponse;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class GetCredentialResponseTest {

    @Test
    public void testConstructor_nullCredential() {
        assertThrows(NullPointerException.class, () -> new GetCredentialResponse(null));
    }

    @Test
    public void testConstructor() {
        final String type = Credential.TYPE_PASSWORD_CREDENTIAL;
        final Bundle data = new Bundle();
        data.putString("foo", "bar");

        final Credential cred = new Credential(type, data);
        final GetCredentialResponse response = new GetCredentialResponse(cred);

        assertThat(response.getCredential().getType()).isEqualTo(type);
        TestUtils.assertEquals(response.getCredential().getData(), data);
    }

    @Test
    public void testWriteToParcel() {
        final Bundle data = new Bundle();
        data.putString("foo", "bar");

        final GetCredentialResponse response = new GetCredentialResponse(
                new Credential(Credential.TYPE_PASSWORD_CREDENTIAL, data)
        );

        final GetCredentialResponse response2 = TestUtils.cloneParcelable(response);
        assertThat(response2.getCredential().getType()).isEqualTo(
                response2.getCredential().getType());

        TestUtils.assertEquals(response2.getCredential().getData(),
                response.getCredential().getData());
    }
}
