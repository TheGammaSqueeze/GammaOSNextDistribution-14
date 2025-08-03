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

package com.android.adservices.service.enrollment;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.stats.AdServicesLogger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link EnrollmentUtil} */
public class EnrollmentUtilTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    @Mock AdServicesLogger mLogger;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void logEnrollmentFileDownloadStats_nullInput_defaultValuesUsed() {
        EnrollmentUtil enrollmentUtil = EnrollmentUtil.getInstance(sContext);
        boolean isSuccessful = true;
        String buildId = null;
        int defaultBuildId = -1;
        enrollmentUtil.logEnrollmentFileDownloadStats(mLogger, isSuccessful, buildId);
        verify(mLogger).logEnrollmentFileDownloadStats(eq(isSuccessful), eq(defaultBuildId));
    }

    @Test
    public void logEnrollmentFailedStats_nullInput_defaultValuesUsed() {
        EnrollmentUtil enrollmentUtil = EnrollmentUtil.getInstance(sContext);
        int buildId = -1;
        int fileGroupStatus = 0;
        int enrollmentRecordCount = 2;
        int errorCause = EnrollmentStatus.ErrorCause.ENROLLMENT_BLOCKLISTED_ERROR_CAUSE.getValue();
        String queryParameter = null;
        String defaultQueryParameter = "";
        enrollmentUtil.logEnrollmentFailedStats(
                mLogger,
                buildId,
                fileGroupStatus,
                enrollmentRecordCount,
                queryParameter,
                errorCause);
        verify(mLogger)
                .logEnrollmentFailedStats(
                        eq(buildId),
                        eq(fileGroupStatus),
                        eq(enrollmentRecordCount),
                        eq(defaultQueryParameter),
                        eq(errorCause));
    }
}
