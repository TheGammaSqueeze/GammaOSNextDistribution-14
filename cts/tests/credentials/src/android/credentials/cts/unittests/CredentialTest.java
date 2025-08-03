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
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class CredentialTest {

    @Test
    public void testConstructor_nullType() {
        assertThrows(IllegalArgumentException.class, () -> new Credential(null, new Bundle()));
    }

    @Test
    public void testConstructor_emptyType() {
        assertThrows(IllegalArgumentException.class, () -> new Credential("", new Bundle()));
    }

    @Test
    public void testConstructor() {
        final String type = Credential.TYPE_PASSWORD_CREDENTIAL;
        final Bundle data = new Bundle();
        data.putString("foo", "bar");

        final Credential cred = new Credential(type, data);
        assertThat(cred.getType()).isEqualTo(type);
        TestUtils.assertEquals(cred.getData(), data);
    }

    @Test
    public void testConstructor_nullBundle() {
        assertThrows(NullPointerException.class, () ->
                new Credential(Credential.TYPE_PASSWORD_CREDENTIAL, null));
    }

    @Test
    public void testGetType() {
        assertThat(new Credential(Credential.TYPE_PASSWORD_CREDENTIAL,
                new Bundle()).getType()).isSameInstanceAs(Credential.TYPE_PASSWORD_CREDENTIAL);
    }

    @Test
    public void testGetData() {
        final Bundle bundle = new Bundle();
        assertThat(new Credential(Credential.TYPE_PASSWORD_CREDENTIAL, bundle)
                .getData()).isSameInstanceAs(bundle);
    }

    @Test
    public void testWriteToParcel() {
        final Bundle data = new Bundle();
        data.putString("foo", "bar");
        final Credential inCred = new Credential(Credential.TYPE_PASSWORD_CREDENTIAL, data);

        final Credential outCred = TestUtils.cloneParcelable(inCred);
        assertThat(outCred.getType()).isEqualTo(inCred.getType());
        assertThat(outCred.getData()).isEqualTo(outCred.getData());
    }
}
