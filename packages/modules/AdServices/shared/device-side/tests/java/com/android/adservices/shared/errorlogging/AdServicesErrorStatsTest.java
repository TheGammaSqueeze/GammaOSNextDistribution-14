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

package com.android.adservices.shared.errorlogging;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AdServicesErrorStatsTest {
    private static final int ERROR_CODE =
            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION;
    private static final int PPAPI_NAME = 1;
    private static final String CLASS_NAME = "TopicsService";
    private static final String METHOD_NAME = "getTopics";
    private static final int LINE_NUMBER = 100;
    private static final String EXCEPTION_NAME = "SQLiteException";

    @Test
    public void testBuilderCreateSuccess() {
        AdServicesErrorStats errorData =
                AdServicesErrorStats.builder()
                        .setErrorCode(ERROR_CODE)
                        .setPpapiName(PPAPI_NAME)
                        .setClassName(CLASS_NAME)
                        .setMethodName(METHOD_NAME)
                        .setLineNumber(LINE_NUMBER)
                        .setLastObservedExceptionName(EXCEPTION_NAME)
                        .build();

        assertEquals(ERROR_CODE, errorData.getErrorCode());
        assertEquals(PPAPI_NAME, errorData.getPpapiName());
        assertEquals(CLASS_NAME, errorData.getClassName());
        assertEquals(METHOD_NAME, errorData.getMethodName());
        assertEquals(LINE_NUMBER, errorData.getLineNumber());
        assertEquals(EXCEPTION_NAME, errorData.getLastObservedExceptionName());
    }

    @Test
    public void testBuilderCreateSuccess_lineNumberMissing_ppapiNameMissing() {
        AdServicesErrorStats errorData =
                AdServicesErrorStats.builder()
                        .setErrorCode(ERROR_CODE)
                        .setClassName(CLASS_NAME)
                        .setMethodName(METHOD_NAME)
                        .setLastObservedExceptionName(EXCEPTION_NAME)
                        .build();

        assertEquals(ERROR_CODE, errorData.getErrorCode());
        assertEquals(0, errorData.getPpapiName());
        assertEquals(CLASS_NAME, errorData.getClassName());
        assertEquals(METHOD_NAME, errorData.getMethodName());
        assertEquals(0, errorData.getLineNumber());
        assertEquals(EXCEPTION_NAME, errorData.getLastObservedExceptionName());
    }

    @Test
    public void testBuilderCreateSuccess_exceptionInfoMissing() {
        AdServicesErrorStats errorData =
                AdServicesErrorStats.builder()
                        .setErrorCode(ERROR_CODE)
                        .setPpapiName(PPAPI_NAME)
                        .build();

        assertEquals(ERROR_CODE, errorData.getErrorCode());
        assertEquals(PPAPI_NAME, errorData.getPpapiName());
        assertEquals("", errorData.getClassName());
        assertEquals("", errorData.getMethodName());
        assertEquals(0, errorData.getLineNumber());
        assertEquals("", errorData.getLastObservedExceptionName());
    }
}
