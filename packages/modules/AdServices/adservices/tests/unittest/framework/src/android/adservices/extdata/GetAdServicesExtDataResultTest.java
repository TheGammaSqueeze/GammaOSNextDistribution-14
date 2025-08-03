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
import static org.junit.Assert.assertNull;

import android.os.Parcel;

import org.junit.Test;

public class GetAdServicesExtDataResultTest {
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

        GetAdServicesExtDataResult response =
                new GetAdServicesExtDataResult.Builder().setAdServicesExtDataParams(params).build();

        Parcel parcel = Parcel.obtain();
        response.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        GetAdServicesExtDataResult fromParcel =
                GetAdServicesExtDataResult.CREATOR.createFromParcel(parcel);
        assertEquals(GetAdServicesExtDataResult.CREATOR.newArray(1).length, 1);

        assertEquals(1, fromParcel.getAdServicesExtDataParams().getIsNotificationDisplayed());
        assertEquals(-1, fromParcel.getAdServicesExtDataParams().getIsMeasurementConsented());
        assertEquals(0, fromParcel.getAdServicesExtDataParams().getIsU18Account());
        assertEquals(1, fromParcel.getAdServicesExtDataParams().getIsAdultAccount());
        assertEquals(
                -1,
                fromParcel.getAdServicesExtDataParams().getManualInteractionWithConsentStatus());
        assertEquals(
                200L, fromParcel.getAdServicesExtDataParams().getMeasurementRollbackApexVersion());
        assertNull(fromParcel.getErrorMessage());
        assertEquals(0, fromParcel.describeContents());
        assertThat(fromParcel.toString()).isNotNull();
    }
}
