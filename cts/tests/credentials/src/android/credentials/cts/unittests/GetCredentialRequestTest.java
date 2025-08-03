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

import static android.credentials.cts.unittests.TestUtils.createTestBundle;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.credentials.CredentialOption;
import android.credentials.GetCredentialRequest;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class GetCredentialRequestTest {
    private static final List<CredentialOption> sCredOptions = List.of(
            new CredentialOption.Builder("type", createTestBundle(), createTestBundle())
                    .setIsSystemProviderRequired(true)
                    .build(),
            new CredentialOption.Builder("type2", createTestBundle(), createTestBundle())
                    .setIsSystemProviderRequired(false)
                    .build(),
            new CredentialOption.Builder("type3", createTestBundle(), createTestBundle())
                    .setIsSystemProviderRequired(false)
                    .build()
    );

    private static final String ORIGIN = "origin";

    @Test
    public void testConstructor_nullData() {
        assertThrows(NullPointerException.class, () -> new GetCredentialRequest.Builder(null));
    }

    @Test
    public void testBuilderAddCredentialOption_nullOption() {
        assertThrows(NullPointerException.class,
                () -> new GetCredentialRequest.Builder(Bundle.EMPTY).addCredentialOption(null));
    }

    @Test
    public void testBuilderSetCredentialOptions_nullOptions() {
        assertThrows(NullPointerException.class,
                () -> new GetCredentialRequest.Builder(Bundle.EMPTY).setCredentialOptions(null));
    }

    @Test
    public void testGetCredentialAddOption_build() {
        final Bundle dataBundle = createTestBundle();
        final GetCredentialRequest.Builder builder = new GetCredentialRequest.Builder(dataBundle);

        for (CredentialOption opt : sCredOptions) {
            builder.addCredentialOption(opt);
        }

        final GetCredentialRequest req = builder.build();
        assertThat(req.getCredentialOptions()).isEqualTo(sCredOptions);
        assertThat(req.getData()).isSameInstanceAs(dataBundle);
    }

    @Test
    public void testGetCredentialSetOptions_build() {
        final Bundle dataBundle = createTestBundle();
        final GetCredentialRequest.Builder builder = new GetCredentialRequest.Builder(dataBundle);

        builder.setCredentialOptions(sCredOptions);

        final GetCredentialRequest req = builder.build();
        assertThat(req.getCredentialOptions()).isEqualTo(sCredOptions);
        assertThat(req.getData()).isSameInstanceAs(dataBundle);
    }

    @Test
    public void testSetOrigin_build() {
        final Bundle dataBundle = createTestBundle();
        final GetCredentialRequest.Builder builder = new GetCredentialRequest.Builder(
                dataBundle).setCredentialOptions(sCredOptions);

        builder.setOrigin(ORIGIN);

        // TODO: Add a test to check permission on extracting the ORIGIN
        final GetCredentialRequest req = builder.build();
        assertThat(req.getOrigin()).isEqualTo(ORIGIN);
        assertThat(req.getData()).isSameInstanceAs(dataBundle);
        assertThat(req.getCredentialOptions()).isEqualTo(sCredOptions);
    }

    @Test
    public void testWriteToParcel() {
        final Bundle data = createTestBundle();

        final GetCredentialRequest req = new GetCredentialRequest.Builder(
                data).setCredentialOptions(sCredOptions).build();

        final GetCredentialRequest req2 = TestUtils.cloneParcelable(req);
        TestUtils.assertEquals(req2.getData(), req.getData());

        for (int i = 0; i < req2.getCredentialOptions().size(); i++) {
            TestUtils.assertEquals(req2.getCredentialOptions().get(i),
                    req.getCredentialOptions().get(i));
        }
    }

    @Test
    public void testBuild_noOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> new GetCredentialRequest.Builder(new Bundle()).build());
    }
}
