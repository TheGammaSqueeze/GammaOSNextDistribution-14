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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

import android.credentials.CredentialDescription;
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
public class CredentialDescriptionTest {

    private static final String CREDENTIAL_TYPE = "MDOC";
    private static final Set<String> FLATTENED_REQUEST = new HashSet<>(List.of("FLATTENED_REQ"));

    @Test
    public void testConstructor_nullType_shouldThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new CredentialDescription(null, FLATTENED_REQUEST,
                        Collections.emptyList()));
    }

    @Test
    public void testConstructor_nullFlattenedRequest_shouldThrowNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new CredentialDescription(CREDENTIAL_TYPE, null,
                        Collections.emptyList()));
    }

    @Test
    public void testConstructor_nullEntriesList_shouldThrowNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> new CredentialDescription(CREDENTIAL_TYPE, FLATTENED_REQUEST,
                        null));
    }

    @Test
    public void testConstructor_getters_shouldSucceed() {
        CredentialDescription credentialDescription = new CredentialDescription(CREDENTIAL_TYPE,
                FLATTENED_REQUEST,
                Collections.EMPTY_LIST);

        assertEquals(credentialDescription.getType(), CREDENTIAL_TYPE);
        assertEquals(credentialDescription.getSupportedElementKeys(), FLATTENED_REQUEST);
        assertEquals(credentialDescription.getCredentialEntries(), Collections.EMPTY_LIST);
    }

    @Test
    public void testWriteToParcel() {
        final CredentialDescription credentialDescription =
                new CredentialDescription(CREDENTIAL_TYPE, FLATTENED_REQUEST,
                Collections.EMPTY_LIST);

        final CredentialDescription credentialDescription2 =
                TestUtils.cloneParcelable(credentialDescription);

        assertEquals(credentialDescription2.getType(), CREDENTIAL_TYPE);
        assertEquals(credentialDescription2.getSupportedElementKeys(), FLATTENED_REQUEST);
        assertEquals(credentialDescription2.getCredentialEntries(), Collections.EMPTY_LIST);
    }

}
