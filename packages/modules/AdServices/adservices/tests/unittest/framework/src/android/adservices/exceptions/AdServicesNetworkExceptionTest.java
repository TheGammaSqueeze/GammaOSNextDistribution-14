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

package android.adservices.exceptions;

import static android.adservices.exceptions.AdServicesNetworkException.ERROR_TOO_MANY_REQUESTS;
import static android.adservices.exceptions.AdServicesNetworkException.INVALID_ERROR_CODE_MESSAGE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import static java.util.Locale.ENGLISH;

import androidx.test.filters.SmallTest;

import org.junit.Test;

// TODO(b/278016822): Move to CTS tests once public APIs are unhidden
@SmallTest
public class AdServicesNetworkExceptionTest {
    private static final int VALID_ERROR_CODE = ERROR_TOO_MANY_REQUESTS;
    private static final int INVALID_ERROR_CODE = 1000;

    @Test
    public void testExceptionWithErrorCode_valid() {
        AdServicesNetworkException exception = new AdServicesNetworkException(VALID_ERROR_CODE);

        assertThat(exception.getErrorCode()).isEqualTo(VALID_ERROR_CODE);
        assertThat(exception.getMessage()).isNull();
        assertThat(exception.toString())
                .isEqualTo(getHumanReadableAdServicesNetworkException(VALID_ERROR_CODE));
    }

    @Test
    public void testExceptionWithErrorCode_errorCodeInvalid() {
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new AdServicesNetworkException(INVALID_ERROR_CODE));
        assertThat(exception.getMessage()).isEqualTo(INVALID_ERROR_CODE_MESSAGE);
    }

    private String getHumanReadableAdServicesNetworkException(int errorCode) {
        return String.format(
                ENGLISH,
                "%s: {Error code: %s}",
                AdServicesNetworkException.class.getCanonicalName(),
                errorCode);
    }
}
