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

import android.credentials.cts.unittests.TestUtils;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.service.credentials.BeginGetCredentialOption;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class BeginGetCredentialOptionTest {

    @Test
    public void testConstructor_nullId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new BeginGetCredentialOption(null, "type", Bundle.EMPTY));
    }

    @Test
    public void testConstructor_emptyId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new BeginGetCredentialOption("", "type", Bundle.EMPTY));
    }

    @Test
    public void testConstructor_nullType_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new BeginGetCredentialOption("id", null, Bundle.EMPTY));
    }

    @Test
    public void testConstructor_emptyType_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new BeginGetCredentialOption("id", "", Bundle.EMPTY));
    }

    @Test
    public void testConstructor_nullBundle_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new BeginGetCredentialOption("id", "", null));
    }

    @Test
    public void testConstructor_success() {
        final String id = "id";
        final String type = "type";
        final Bundle data = TestUtils.createTestBundle();
        final BeginGetCredentialOption option = new BeginGetCredentialOption(id, type, data);

        assertThat(option.getId()).isEqualTo(id);
        assertThat(option.getType()).isEqualTo(type);

        // The data bundle has an additional key/value pair containing the id for some reason,
        // so we cannot compare them directly.
        for (String key : data.keySet()) {
            assertThat(data.get(key)).isEqualTo(option.getCandidateQueryData().get(key));
        }
    }

    @Test
    public void testWriteToParcel_success() {
        final BeginGetCredentialOption option1 = new BeginGetCredentialOption("id", "type",
                TestUtils.createTestBundle());
        final BeginGetCredentialOption option2 = TestUtils.cloneParcelable(option1);

        assertThat(option2.getId()).isEqualTo(option1.getId());
        assertThat(option2.getType()).isEqualTo(option1.getType());
        TestUtils.assertEquals(option2.getCandidateQueryData(), option1.getCandidateQueryData());
    }

}
