/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.shared.common;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public class ServiceUnavailableExceptionTest extends AdServicesUnitTestCase {

    public static final String ERROR_MESSAGE = "error_message";

    @Test
    public void testAdServicesUnavailableException_default() {
        ServiceUnavailableException exception = new ServiceUnavailableException();

        expect.that(exception).hasMessageThat().isNull();
    }

    @Test
    public void testAdServicesUnavailableException_correctErrorMsg() {
        ServiceUnavailableException exception = new ServiceUnavailableException(ERROR_MESSAGE);

        expect.that(exception).hasMessageThat().isEqualTo(ERROR_MESSAGE);
    }

    @Test
    public void testAdServicesUnavailableException_isOfTypeIllegalStateException() {
        ServiceUnavailableException exception = new ServiceUnavailableException(ERROR_MESSAGE);

        expect.that(exception).isInstanceOf(IllegalStateException.class);
    }
}
