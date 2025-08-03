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

package android.adservices.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link FledgeErrorResponse} */
@SmallTest
public final class FledgeErrorResponseTest {

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildFledgeErrorResponse() {
        String notImplementedMessage = "Not Implemented!";
        FledgeErrorResponse response =
                new FledgeErrorResponse.Builder()
                        .setStatusCode(AdServicesStatusUtils.STATUS_INTERNAL_ERROR)
                        .setErrorMessage(notImplementedMessage)
                        .build();

        assertThat(response.getStatusCode()).isEqualTo(AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
        assertThat(response.getErrorMessage()).isEqualTo(notImplementedMessage);
    }

    @Test
    public void testWriteToParcel() {
        String notImplementedMessage = "Not Implemented!";
        FledgeErrorResponse response =
                new FledgeErrorResponse.Builder()
                        .setStatusCode(AdServicesStatusUtils.STATUS_INTERNAL_ERROR)
                        .setErrorMessage(notImplementedMessage)
                        .build();
        Parcel p = Parcel.obtain();
        response.writeToParcel(p, 0);
        p.setDataPosition(0);

        FledgeErrorResponse fromParcel = FledgeErrorResponse.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getStatusCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
        assertThat(fromParcel.getErrorMessage()).isEqualTo(notImplementedMessage);
    }

    @Test
    public void testWriteToParcelEmptyMessage() {
        FledgeErrorResponse response =
                new FledgeErrorResponse.Builder()
                        .setStatusCode(AdServicesStatusUtils.STATUS_SUCCESS)
                        .build();
        Parcel p = Parcel.obtain();
        response.writeToParcel(p, 0);
        p.setDataPosition(0);

        FledgeErrorResponse fromParcel = FledgeErrorResponse.CREATOR.createFromParcel(p);

        assertThat(AdServicesStatusUtils.isSuccess(fromParcel.getStatusCode())).isTrue();
        assertThat(fromParcel.getErrorMessage()).isNull();
    }

    @Test
    public void testFailsForNotSetStatus() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new FledgeErrorResponse.Builder()
                            .setErrorMessage("Status not set!")
                            // Not setting status code making it -1.
                            .build();
                });
    }

    @Test
    public void testFledgeErrorResponseDescribeContents() {
        String notImplementedMessage = "Not Implemented!";
        FledgeErrorResponse response =
                new FledgeErrorResponse.Builder()
                        .setStatusCode(AdServicesStatusUtils.STATUS_INTERNAL_ERROR)
                        .setErrorMessage(notImplementedMessage)
                        .build();

        assertEquals(0, response.describeContents());
    }

    @Test
    public void testToString() {
        String notImplementedMessage = "Not Implemented!";
        FledgeErrorResponse response =
                new FledgeErrorResponse.Builder()
                        .setStatusCode(AdServicesStatusUtils.STATUS_INTERNAL_ERROR)
                        .setErrorMessage(notImplementedMessage)
                        .build();

        assertThat(response.toString())
                .isEqualTo(
                        String.format(
                                "FledgeErrorResponse{mStatusCode=%s, mErrorMessage='%s'}",
                                AdServicesStatusUtils.STATUS_INTERNAL_ERROR,
                                notImplementedMessage));
    }
}
