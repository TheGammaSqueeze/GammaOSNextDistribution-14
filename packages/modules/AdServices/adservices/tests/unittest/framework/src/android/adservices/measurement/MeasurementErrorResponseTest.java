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

package android.adservices.measurement;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_IO_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_PERMISSION_NOT_REQUESTED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdServicesStatusUtils;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.io.IOException;

/** Unit tests for {@link MeasurementErrorResponse} */
@SmallTest
public class MeasurementErrorResponseTest {
    private static final String ERROR_MESSAGE = "Error message";
    private static final int RESULT_CODE = 500;

    @Test
    public void testDefaults() throws Exception {
        MeasurementErrorResponse response = new MeasurementErrorResponse.Builder().build();

        assertEquals(0, response.getStatusCode());
        assertNull(response.getErrorMessage());
    }

    @Test
    public void testCreationAttribution() {
        verifyExample(createExample());
    }

    @Test
    public void testParceling() {
        Parcel p = Parcel.obtain();
        createExample().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyExample(MeasurementErrorResponse.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void testAdServicesException_invalidArgument_expectIllegalArgumentException() {
        MeasurementErrorResponse response =
                new MeasurementErrorResponse.Builder()
                        .setStatusCode(STATUS_INVALID_ARGUMENT)
                        .build();
        assertTrue(AdServicesStatusUtils.asException(response) instanceof IllegalArgumentException);
    }

    @Test
    public void testAdServicesException_internalError_expectIllegalStateException() {
        MeasurementErrorResponse response =
                new MeasurementErrorResponse.Builder().setStatusCode(STATUS_INTERNAL_ERROR).build();
        assertTrue(AdServicesStatusUtils.asException(response) instanceof IllegalStateException);
    }

    @Test
    public void testAdServicesException_ioError_expectIOException() {
        MeasurementErrorResponse response =
                new MeasurementErrorResponse.Builder().setStatusCode(STATUS_IO_ERROR).build();
        assertTrue(AdServicesStatusUtils.asException(response) instanceof IOException);
    }

    @Test
    public void testAdServicesException_unauthorized_expectSecurityException() {
        MeasurementErrorResponse response =
                new MeasurementErrorResponse.Builder()
                        .setStatusCode(STATUS_PERMISSION_NOT_REQUESTED)
                        .build();
        assertTrue(AdServicesStatusUtils.asException(response) instanceof SecurityException);
    }

    @Test
    public void testAdServicesException_unrecognized_expectIllegalStateException() {
        MeasurementErrorResponse response =
                new MeasurementErrorResponse.Builder().setStatusCode(Integer.MAX_VALUE).build();
        assertTrue(AdServicesStatusUtils.asException(response) instanceof IllegalStateException);
    }

    @Test
    public void testDescribeContents() {
        assertEquals(0, createExample().describeContents());
    }

    private MeasurementErrorResponse createExample() {
        return new MeasurementErrorResponse.Builder()
                .setErrorMessage(ERROR_MESSAGE)
                .setStatusCode(RESULT_CODE)
                .build();
    }

    private void verifyExample(MeasurementErrorResponse response) {
        assertEquals(ERROR_MESSAGE, response.getErrorMessage());
        assertEquals(RESULT_CODE, response.getStatusCode());
    }
}
