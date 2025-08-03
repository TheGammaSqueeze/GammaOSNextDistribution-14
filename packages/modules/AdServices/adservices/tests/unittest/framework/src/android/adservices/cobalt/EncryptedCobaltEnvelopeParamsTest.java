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

package android.adservices.cobalt;

import static android.adservices.cobalt.EncryptedCobaltEnvelopeParams.ENVIRONMENT_PROD;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class EncryptedCobaltEnvelopeParamsTest {
    private static final byte[] BYTES = {0x0a, 0x0b, 0x0c};
    private static final int KEY_INDEX = 5;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void nullCiphertext_throws() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new EncryptedCobaltEnvelopeParams(0, 0, null);
                });
    }

    @Test
    public void getMethods_returnExpectedValues() throws Exception {
        EncryptedCobaltEnvelopeParams params =
                new EncryptedCobaltEnvelopeParams(ENVIRONMENT_PROD, KEY_INDEX, BYTES);
        assertThat(params.getEnvironment()).isEqualTo(ENVIRONMENT_PROD);
        assertThat(params.getKeyIndex()).isEqualTo(KEY_INDEX);
        assertThat(params.getCipherText()).isEqualTo(BYTES);
    }

    @Test
    public void parcelContents_areEqualToInput() throws Exception {
        EncryptedCobaltEnvelopeParams inputParams =
                new EncryptedCobaltEnvelopeParams(ENVIRONMENT_PROD, KEY_INDEX, BYTES);

        Parcel parcel = Parcel.obtain();
        inputParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        EncryptedCobaltEnvelopeParams outputParams =
                EncryptedCobaltEnvelopeParams.CREATOR.createFromParcel(parcel);
        assertThat(outputParams.getEnvironment()).isEqualTo(ENVIRONMENT_PROD);
        assertThat(outputParams.getKeyIndex()).isEqualTo(KEY_INDEX);
        assertThat(outputParams.getCipherText()).isEqualTo(BYTES);
    }
}
