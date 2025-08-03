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
package com.android.adservices.service.measurement.util;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

@SmallTest
@SpyStatic(AppManifestConfigHelper.class)
public final class EnrollmentTest extends AdServicesExtendedMockitoTestCase {

    private static final Uri REGISTRATION_URI = Uri.parse("https://ad-tech.test/register");
    private static final String ENROLLMENT_ID = "enrollment-id";
    private static final String PACKAGE_NAME = "com.package.name";
    private static final EnrollmentData ENROLLMENT =
            new EnrollmentData.Builder()
                    .setEnrollmentId("enrollment-id")
                    .setAttributionReportingUrl(
                            List.of("https://origin1.test", "https://origin2.test"))
                    .build();
    @Mock private EnrollmentDao mEnrollmentDao;

    @Mock private Flags mFlags;
    @Test
    public void testMaybeGetEnrollmentId_success() {
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(eq(REGISTRATION_URI)))
                .thenReturn(ENROLLMENT);
        when(mFlags.isEnrollmentBlocklisted(any())).thenReturn(false);
        mockIsAllowedAttributionAccess(/* allowed= */ true);

        assertEquals(
                Optional.of(ENROLLMENT.getEnrollmentId()),
                Enrollment.getValidEnrollmentId(
                        REGISTRATION_URI, PACKAGE_NAME, mEnrollmentDao, sContext, mFlags));
    }

    @Test
    public void testMaybeGetEnrollmentId_enrollmentDataNull() {
        // Simulating failure
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(eq(REGISTRATION_URI)))
                .thenReturn(null);
        when(mFlags.isEnrollmentBlocklisted(any())).thenReturn(false);
        mockIsAllowedAttributionAccess(/* allowed= */ true);

        assertEquals(
                Optional.empty(),
                Enrollment.getValidEnrollmentId(
                        REGISTRATION_URI, PACKAGE_NAME, mEnrollmentDao, sContext, mFlags));
    }

    @Test
    public void testMaybeGetEnrollmentId_blockedByEnrollmentBlockList() {
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(eq(REGISTRATION_URI)))
                .thenReturn(ENROLLMENT);
        // Simulating failure
        when(mFlags.isEnrollmentBlocklisted(any())).thenReturn(true);
        mockIsAllowedAttributionAccess(/* allowed= */ true);

        assertEquals(
                Optional.empty(),
                Enrollment.getValidEnrollmentId(
                        REGISTRATION_URI, PACKAGE_NAME, mEnrollmentDao, sContext, mFlags));
    }

    @Test
    public void testMaybeGetEnrollmentId_packageManifestCheckFailure() {
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(eq(REGISTRATION_URI)))
                .thenReturn(ENROLLMENT);
        when(mFlags.isEnrollmentBlocklisted(any())).thenReturn(false);
        // Simulating failure
        mockIsAllowedAttributionAccess(/* allowed= */ false);

        assertEquals(
                Optional.empty(),
                Enrollment.getValidEnrollmentId(
                        REGISTRATION_URI, PACKAGE_NAME, mEnrollmentDao, sContext, mFlags));
    }

    @Test
    public void testMaybeGetEnrollmentId_localhost_success() {
        String localhost = "https://localhost";
        String localhostWithPort = localhost + ":5678";
        String localhostWithPortAndPath = localhostWithPort + "/path";
        String localhostWithPortAndPathAndParams = localhostWithPort + "/path?param=2";
        String localhostWithPath = localhost + "/path";
        String localhostWithPathAndParams = localhostWithPath + "/path?param=2";

        assertEquals(
                Optional.of(Enrollment.LOCALHOST_ENROLLMENT_ID),
                Enrollment.getValidEnrollmentId(
                        Uri.parse(localhost), PACKAGE_NAME, mEnrollmentDao, sContext, mFlags));
        assertEquals(
                Optional.of(Enrollment.LOCALHOST_ENROLLMENT_ID),
                Enrollment.getValidEnrollmentId(
                        Uri.parse(localhostWithPort), PACKAGE_NAME, mEnrollmentDao, sContext,
                        mFlags));
        assertEquals(
                Optional.of(Enrollment.LOCALHOST_ENROLLMENT_ID),
                Enrollment.getValidEnrollmentId(
                        Uri.parse(localhostWithPortAndPath), PACKAGE_NAME, mEnrollmentDao, sContext,
                        mFlags));
        assertEquals(
                Optional.of(Enrollment.LOCALHOST_ENROLLMENT_ID),
                Enrollment.getValidEnrollmentId(
                        Uri.parse(localhostWithPortAndPathAndParams), PACKAGE_NAME, mEnrollmentDao,
                        sContext, mFlags));
        assertEquals(
                Optional.of(Enrollment.LOCALHOST_ENROLLMENT_ID),
                Enrollment.getValidEnrollmentId(
                        Uri.parse(localhostWithPath), PACKAGE_NAME, mEnrollmentDao, sContext,
                        mFlags));
        assertEquals(
                Optional.of(Enrollment.LOCALHOST_ENROLLMENT_ID),
                Enrollment.getValidEnrollmentId(
                        Uri.parse(localhostWithPathAndParams), PACKAGE_NAME, mEnrollmentDao,
                        sContext, mFlags));
    }

    @Test
    public void testMaybeGetEnrollmentId_localhostIp_success() {
        String localhostIp = "https://127.0.0.1";
        String localhostIpWithPort = localhostIp + ":5678";
        String localhostIpWithPortAndPath = localhostIpWithPort + "/path";
        String localhostIpWithPortAndPathAndParams = localhostIpWithPort + "/path?param=2";
        String localhostIpWithPath = localhostIp + "/path";
        String localhostIpWithPathAndParams = localhostIpWithPath + "/path?param=2";

        assertEquals(
                Optional.of(Enrollment.LOCALHOST_IP_ENROLLMENT_ID),
                Enrollment.getValidEnrollmentId(
                        Uri.parse(localhostIp), PACKAGE_NAME, mEnrollmentDao, sContext, mFlags));
        assertEquals(
                Optional.of(Enrollment.LOCALHOST_IP_ENROLLMENT_ID),
                Enrollment.getValidEnrollmentId(
                        Uri.parse(localhostIpWithPort), PACKAGE_NAME, mEnrollmentDao, sContext,
                        mFlags));
        assertEquals(
                Optional.of(Enrollment.LOCALHOST_IP_ENROLLMENT_ID),
                Enrollment.getValidEnrollmentId(
                        Uri.parse(localhostIpWithPortAndPath), PACKAGE_NAME, mEnrollmentDao,
                        sContext, mFlags));
        assertEquals(
                Optional.of(Enrollment.LOCALHOST_IP_ENROLLMENT_ID),
                Enrollment.getValidEnrollmentId(
                        Uri.parse(localhostIpWithPortAndPathAndParams), PACKAGE_NAME,
                        mEnrollmentDao, sContext, mFlags));
        assertEquals(
                Optional.of(Enrollment.LOCALHOST_IP_ENROLLMENT_ID),
                Enrollment.getValidEnrollmentId(
                        Uri.parse(localhostIpWithPath), PACKAGE_NAME, mEnrollmentDao, sContext,
                        mFlags));
        assertEquals(
                Optional.of(Enrollment.LOCALHOST_IP_ENROLLMENT_ID),
                Enrollment.getValidEnrollmentId(
                        Uri.parse(localhostIpWithPathAndParams), PACKAGE_NAME, mEnrollmentDao,
                        sContext, mFlags));
    }

    private void mockIsAllowedAttributionAccess(boolean allowed) {
        doReturn(allowed)
                .when(
                        () ->
                                AppManifestConfigHelper.isAllowedAttributionAccess(
                                        PACKAGE_NAME, ENROLLMENT_ID));
    }
}
