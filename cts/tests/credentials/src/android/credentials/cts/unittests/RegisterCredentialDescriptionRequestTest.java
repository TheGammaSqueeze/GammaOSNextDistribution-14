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

import static org.testng.Assert.assertThrows;

import android.credentials.CredentialDescription;
import android.credentials.RegisterCredentialDescriptionRequest;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class RegisterCredentialDescriptionRequestTest {

    private static final String CREDENTIAL_TYPE = "MDOC";
    private static final Set<String> FLATTENED_REQUEST = new HashSet<>(List.of("FLATTENED_REQ"));

    @Test
    public void testConstructor_nullDescription_shouldThrowNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new RegisterCredentialDescriptionRequest((CredentialDescription) null));
    }

    @Test
    public void testConstructor_nullSet_shouldThrowNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new RegisterCredentialDescriptionRequest((Set<CredentialDescription>) null));
    }

    @Test
    public void testWriteToParcel_shouldSucceed() {
        final CredentialDescription credentialDescription =
                new CredentialDescription(CREDENTIAL_TYPE, FLATTENED_REQUEST,
                Collections.emptyList());

        final RegisterCredentialDescriptionRequest req =
                new RegisterCredentialDescriptionRequest(credentialDescription);

        final RegisterCredentialDescriptionRequest req2 = TestUtils.cloneParcelable(req);

        List<CredentialDescription> options = req.getCredentialDescriptions().stream().toList();
        List<CredentialDescription> options2 = req2.getCredentialDescriptions().stream().toList();

        for (int i = 0; i < options.size(); i++) {
            TestUtils.assertEquals(options2.get(i), options.get(i));
        }
    }
}
