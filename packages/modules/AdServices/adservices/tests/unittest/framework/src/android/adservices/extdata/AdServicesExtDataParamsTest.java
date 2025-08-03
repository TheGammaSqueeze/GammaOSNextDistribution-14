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

package android.adservices.extdata;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import android.os.Parcel;

import org.junit.Test;

public class AdServicesExtDataParamsTest {
    @Test
    public void testWriteToParcel() throws Exception {
        AdServicesExtDataParams params =
                new AdServicesExtDataParams.Builder()
                        .setNotificationDisplayed(1)
                        .setMsmtConsent(-1)
                        .setIsU18Account(0)
                        .setIsAdultAccount(1)
                        .setManualInteractionWithConsentStatus(-1)
                        .setMsmtRollbackApexVersion(200L)
                        .build();

        Parcel parcel = Parcel.obtain();
        params.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        AdServicesExtDataParams fromParcel =
                AdServicesExtDataParams.CREATOR.createFromParcel(parcel);
        assertEquals(AdServicesExtDataParams.CREATOR.newArray(1).length, 1);

        assertEquals(1, fromParcel.getIsNotificationDisplayed());
        assertEquals(-1, fromParcel.getIsMeasurementConsented());
        assertEquals(0, fromParcel.getIsU18Account());
        assertEquals(1, fromParcel.getIsAdultAccount());
        assertEquals(-1, fromParcel.getManualInteractionWithConsentStatus());
        assertEquals(200L, fromParcel.getMeasurementRollbackApexVersion());

        assertEquals(0, fromParcel.describeContents());
        assertThat(fromParcel.toString()).isNotNull();
    }

    @Test
    public void testToString() {
        AdServicesExtDataParams params =
                new AdServicesExtDataParams.Builder()
                        .setNotificationDisplayed(1)
                        .setMsmtConsent(-1)
                        .setIsU18Account(0)
                        .setIsAdultAccount(1)
                        .setManualInteractionWithConsentStatus(-1)
                        .setMsmtRollbackApexVersion(200L)
                        .build();

        String expectedResult =
                "AdServicesExtDataParams{"
                        + "mIsNotificationDisplayed=1, "
                        + "mIsMsmtConsented=-1, "
                        + "mIsU18Account=0, "
                        + "mIsAdultAccount=1, "
                        + "mManualInteractionWithConsentStatus=-1, "
                        + "mMsmtRollbackApexVersion=200}";

        assertEquals(expectedResult, params.toString());
    }
}
