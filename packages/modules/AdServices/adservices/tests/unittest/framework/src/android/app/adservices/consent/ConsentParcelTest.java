/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.app.adservices.consent;

import static android.app.adservices.consent.ConsentParcel.ALL_API;
import static android.app.adservices.consent.ConsentParcel.FLEDGE;
import static android.app.adservices.consent.ConsentParcel.MEASUREMENT;
import static android.app.adservices.consent.ConsentParcel.TOPICS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

/** Unit tests for {@link ConsentParcel} */
public final class ConsentParcelTest {

    @Test
    public void testConsentParcel() {
        ConsentParcel consentParcel =
                new ConsentParcel.Builder().setConsentApiType(ALL_API).setIsGiven(true).build();
        assertThat(consentParcel.getConsentApiType()).isEqualTo(ALL_API);
        assertThat(consentParcel.isIsGiven()).isTrue();

        consentParcel =
                new ConsentParcel.Builder().setConsentApiType(TOPICS).setIsGiven(false).build();
        assertThat(consentParcel.getConsentApiType()).isEqualTo(TOPICS);
        assertThat(consentParcel.isIsGiven()).isFalse();

        consentParcel =
                new ConsentParcel.Builder().setConsentApiType(FLEDGE).setIsGiven(false).build();
        assertThat(consentParcel.getConsentApiType()).isEqualTo(FLEDGE);
        assertThat(consentParcel.isIsGiven()).isFalse();

        consentParcel =
                new ConsentParcel.Builder().setConsentApiType(MEASUREMENT).setIsGiven(true).build();
        assertThat(consentParcel.getConsentApiType()).isEqualTo(MEASUREMENT);
        assertThat(consentParcel.isIsGiven()).isTrue();
    }

    @Test
    public void testConsentParcel_notSetConsentApiType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new ConsentParcel.Builder()
                            // Not set the ConsentApiType.
                            // .setConsentApiType(xxx)
                            .setIsGiven(true)
                            .build();
                });
    }
}
