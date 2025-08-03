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

package com.android.adservices.service.common;

import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE;
import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_PROTECTED_SIGNALS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_PERMISSION_NOT_REQUESTED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;

import static com.android.adservices.service.common.AppManifestConfigCall.API_AD_SELECTION;
import static com.android.adservices.service.common.AppManifestConfigCall.API_CUSTOM_AUDIENCES;
import static com.android.adservices.service.common.AppManifestConfigCall.API_PROTECTED_SIGNALS;
import static com.android.adservices.service.common.FledgeAuthorizationFilter.INVALID_API_TYPE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyZeroInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdServicesPermissions;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.enrollment.EnrollmentStatus;
import com.android.adservices.service.enrollment.EnrollmentUtil;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;

@RequiresSdkLevelAtLeastS()
@MockStatic(PermissionHelper.class)
@MockStatic(AppManifestConfigHelper.class)
@MockStatic(FlagsFactory.class)
public final class FledgeAuthorizationFilterTest extends AdServicesExtendedMockitoTestCase {

    private static final int INVALID_API = 42;
    private static final int UID = 111;
    private static final int API_NAME_LOGGING_ID =
            AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
    private static final String PACKAGE_NAME = "pkg_name";
    private static final String PACKAGE_NAME_OTHER = "other_pkg_name";
    private static final String ENROLLMENT_ID = "enroll_id";
    private static final Uri URI_FOR_AD_TECH =
            CommonFixture.getUriWithValidSubdomain(CommonFixture.VALID_BUYER_1.toString(), "/path");
    private static final EnrollmentData ENROLLMENT_DATA =
            new EnrollmentData.Builder().setEnrollmentId(ENROLLMENT_ID).build();

    @Mock private PackageManager mPackageManagerMock;
    @Mock private EnrollmentDao mEnrollmentDaoMock;
    @Mock private EnrollmentUtil mEnrollmentUtilMock;
    @Mock private Flags mFlags;

    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);

    private FledgeAuthorizationFilter mChecker;

    @Before
    public void setup() {
        extendedMockito.mockGetFlags(mFlags);

        mChecker =
                new FledgeAuthorizationFilter(
                        mPackageManagerMock,
                        mEnrollmentDaoMock,
                        mAdServicesLoggerMock,
                        mEnrollmentUtilMock);
    }

    @Test
    public void testAssertCallingPackageName_isCallingPackageName() {
        when(mPackageManagerMock.getPackagesForUid(UID))
                .thenReturn(new String[] {PACKAGE_NAME, PACKAGE_NAME_OTHER});

        mChecker.assertCallingPackageName(PACKAGE_NAME, UID, API_NAME_LOGGING_ID);

        verify(mPackageManagerMock).getPackagesForUid(UID);
        verifyNoMoreInteractions(mPackageManagerMock);
        verifyZeroInteractions(mAdServicesLoggerMock, mEnrollmentDaoMock);
    }

    @Test
    public void testAssertCallingPackageName_nullPackageName_throwNpe() {
        assertThrows(
                NullPointerException.class,
                () -> mChecker.assertCallingPackageName(null, UID, API_NAME_LOGGING_ID));

        verifyZeroInteractions(mPackageManagerMock, mAdServicesLoggerMock, mEnrollmentDaoMock);
    }

    @Test
    public void testAssertCallingPackageName_isNotCallingPackageName_throwSecurityException() {
        when(mPackageManagerMock.getPackagesForUid(UID))
                .thenReturn(new String[] {PACKAGE_NAME_OTHER});

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mChecker.assertCallingPackageName(
                                        PACKAGE_NAME, UID, API_NAME_LOGGING_ID));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE,
                exception.getMessage());
        verify(mPackageManagerMock).getPackagesForUid(UID);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID),
                        eq(PACKAGE_NAME),
                        eq(STATUS_UNAUTHORIZED),
                        anyInt());
        verifyNoMoreInteractions(mPackageManagerMock, mAdServicesLoggerMock, mEnrollmentDaoMock);
    }

    @Test
    public void testAssertCallingPackageName_packageNotExist_throwSecurityException() {
        when(mPackageManagerMock.getPackagesForUid(UID)).thenReturn(new String[] {});

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mChecker.assertCallingPackageName(
                                        PACKAGE_NAME, UID, API_NAME_LOGGING_ID));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE,
                exception.getMessage());
        verify(mPackageManagerMock).getPackagesForUid(UID);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID),
                        eq(PACKAGE_NAME),
                        eq(STATUS_UNAUTHORIZED),
                        anyInt());
        verifyNoMoreInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAppHasCaPermission_appHasPermission() throws Exception {
        PackageInfo packageInfoGrant = new PackageInfo();
        packageInfoGrant.requestedPermissions = new String[] {ACCESS_ADSERVICES_CUSTOM_AUDIENCE};
        doReturn(packageInfoGrant)
                .when(mPackageManagerMock)
                .getPackageInfo(
                        eq(CustomAudienceFixture.VALID_OWNER), eq(PackageManager.GET_PERMISSIONS));

        when(PermissionHelper.hasPermission(
                        sContext,
                        sPackageName,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE))
                .thenReturn(true);

        mChecker.assertAppDeclaredPermission(
                sContext,
                CustomAudienceFixture.VALID_OWNER,
                API_NAME_LOGGING_ID,
                AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAppHasPasPermission_appHasPermission()
            throws PackageManager.NameNotFoundException {
        PackageInfo packageInfoGrant = new PackageInfo();
        packageInfoGrant.requestedPermissions = new String[] {ACCESS_ADSERVICES_PROTECTED_SIGNALS};
        doReturn(packageInfoGrant)
                .when(mPackageManagerMock)
                .getPackageInfo(
                        eq(CustomAudienceFixture.VALID_OWNER), eq(PackageManager.GET_PERMISSIONS));

        when(PermissionHelper.hasPermission(
                        sContext,
                        sContext.getPackageName(),
                        AdServicesPermissions.ACCESS_ADSERVICES_PROTECTED_SIGNALS))
                .thenReturn(true);

        mChecker.assertAppDeclaredPermission(
                sContext,
                CustomAudienceFixture.VALID_OWNER,
                API_NAME_LOGGING_ID,
                AdServicesPermissions.ACCESS_ADSERVICES_PROTECTED_SIGNALS);

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAppHasAnyPermission_appHasAllPermissions() throws Exception {
        PackageInfo packageInfoGrant = new PackageInfo();
        packageInfoGrant.requestedPermissions = new String[] {ACCESS_ADSERVICES_PROTECTED_SIGNALS};
        doReturn(packageInfoGrant)
                .when(mPackageManagerMock)
                .getPackageInfo(
                        eq(CustomAudienceFixture.VALID_OWNER), eq(PackageManager.GET_PERMISSIONS));

        when(PermissionHelper.hasPermission(
                        sContext, sPackageName, ACCESS_ADSERVICES_PROTECTED_SIGNALS))
                .thenReturn(true);
        when(PermissionHelper.hasPermission(
                        sContext, sPackageName, ACCESS_ADSERVICES_CUSTOM_AUDIENCE))
                .thenReturn(true);
        when(PermissionHelper.hasPermission(
                        sContext,
                        sPackageName,
                        AdServicesPermissions.ACCESS_ADSERVICES_AD_SELECTION))
                .thenReturn(true);

        mChecker.assertAppDeclaredAnyPermission(
                sContext,
                CustomAudienceFixture.VALID_OWNER,
                API_NAME_LOGGING_ID,
                new HashSet<>(
                        Arrays.asList(
                                AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE,
                                AdServicesPermissions.ACCESS_ADSERVICES_AD_SELECTION,
                                AdServicesPermissions.ACCESS_ADSERVICES_PROTECTED_SIGNALS)));

        verifyZeroInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAppHasAnyPermission_appHasOnePermission() throws Exception {
        PackageInfo packageInfoGrant = new PackageInfo();
        packageInfoGrant.requestedPermissions = new String[] {ACCESS_ADSERVICES_PROTECTED_SIGNALS};
        doReturn(packageInfoGrant)
                .when(mPackageManagerMock)
                .getPackageInfo(
                        eq(CustomAudienceFixture.VALID_OWNER), eq(PackageManager.GET_PERMISSIONS));

        when(PermissionHelper.hasPermission(
                        sContext, sPackageName, ACCESS_ADSERVICES_PROTECTED_SIGNALS))
                .thenReturn(false);
        when(PermissionHelper.hasPermission(
                        sContext, sPackageName, ACCESS_ADSERVICES_CUSTOM_AUDIENCE))
                .thenReturn(false);
        when(PermissionHelper.hasPermission(
                        sContext,
                        sPackageName,
                        AdServicesPermissions.ACCESS_ADSERVICES_AD_SELECTION))
                .thenReturn(true);

        mChecker.assertAppDeclaredAnyPermission(
                sContext,
                CustomAudienceFixture.VALID_OWNER,
                API_NAME_LOGGING_ID,
                new HashSet<>(
                        Arrays.asList(
                                AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE,
                                AdServicesPermissions.ACCESS_ADSERVICES_AD_SELECTION,
                                AdServicesPermissions.ACCESS_ADSERVICES_PROTECTED_SIGNALS)));

        verifyZeroInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAppHasAnyPermission_appHasTwoPermissions() throws Exception {
        PackageInfo packageInfoGrant = new PackageInfo();
        packageInfoGrant.requestedPermissions = new String[] {ACCESS_ADSERVICES_PROTECTED_SIGNALS};
        doReturn(packageInfoGrant)
                .when(mPackageManagerMock)
                .getPackageInfo(
                        eq(CustomAudienceFixture.VALID_OWNER), eq(PackageManager.GET_PERMISSIONS));

        when(PermissionHelper.hasPermission(
                        sContext, sPackageName, ACCESS_ADSERVICES_PROTECTED_SIGNALS))
                .thenReturn(true);
        when(PermissionHelper.hasPermission(
                        sContext, sPackageName, ACCESS_ADSERVICES_CUSTOM_AUDIENCE))
                .thenReturn(false);
        when(PermissionHelper.hasPermission(
                        sContext,
                        sPackageName,
                        AdServicesPermissions.ACCESS_ADSERVICES_AD_SELECTION))
                .thenReturn(true);

        mChecker.assertAppDeclaredAnyPermission(
                sContext,
                CustomAudienceFixture.VALID_OWNER,
                API_NAME_LOGGING_ID,
                new HashSet<>(
                        Arrays.asList(
                                AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE,
                                AdServicesPermissions.ACCESS_ADSERVICES_AD_SELECTION,
                                AdServicesPermissions.ACCESS_ADSERVICES_PROTECTED_SIGNALS)));

        verifyZeroInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAppHasCaPermission_appDoesNotHavePermission_throwSecurityException()
            throws PackageManager.NameNotFoundException {
        doReturn(new PackageInfo())
                .when(mPackageManagerMock)
                .getPackageInfo(
                        eq(CustomAudienceFixture.VALID_OWNER), eq(PackageManager.GET_PERMISSIONS));
        when(PermissionHelper.hasPermission(
                        sContext,
                        sPackageName,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE))
                .thenReturn(false);

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mChecker.assertAppDeclaredPermission(
                                        sContext,
                                        CustomAudienceFixture.VALID_OWNER,
                                        API_NAME_LOGGING_ID,
                                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_PERMISSION_NOT_REQUESTED_ERROR_MESSAGE,
                exception.getMessage());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID),
                        eq(CustomAudienceFixture.VALID_OWNER),
                        eq(STATUS_PERMISSION_NOT_REQUESTED),
                        anyInt());
        verifyNoMoreInteractions(mAdServicesLoggerMock);
        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock);
    }

    @Test
    public void testAssertAppHasAnyPermission_appDoesNotHaveAnyPermissions_throwSecurityException()
            throws Exception {
        doReturn(new PackageInfo())
                .when(mPackageManagerMock)
                .getPackageInfo(
                        eq(CustomAudienceFixture.VALID_OWNER), eq(PackageManager.GET_PERMISSIONS));
        when(PermissionHelper.hasPermission(
                        sContext, sPackageName, ACCESS_ADSERVICES_PROTECTED_SIGNALS))
                .thenReturn(false);
        when(PermissionHelper.hasPermission(
                        sContext, sPackageName, ACCESS_ADSERVICES_CUSTOM_AUDIENCE))
                .thenReturn(false);
        when(PermissionHelper.hasPermission(
                        sContext,
                        sPackageName,
                        AdServicesPermissions.ACCESS_ADSERVICES_AD_SELECTION))
                .thenReturn(false);

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mChecker.assertAppDeclaredAnyPermission(
                                        sContext,
                                        CustomAudienceFixture.VALID_OWNER,
                                        API_NAME_LOGGING_ID,
                                        new HashSet<>(
                                                Arrays.asList(
                                                        AdServicesPermissions
                                                                .ACCESS_ADSERVICES_CUSTOM_AUDIENCE,
                                                        AdServicesPermissions
                                                                .ACCESS_ADSERVICES_AD_SELECTION,
                                                        AdServicesPermissions
                                                                .ACCESS_ADSERVICES_PROTECTED_SIGNALS))));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_PERMISSION_NOT_REQUESTED_ERROR_MESSAGE,
                exception.getMessage());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID),
                        eq(CustomAudienceFixture.VALID_OWNER),
                        eq(STATUS_PERMISSION_NOT_REQUESTED),
                        anyInt());
        verifyNoMoreInteractions(mAdServicesLoggerMock);
        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock);
    }

    @Test
    public void testAssertAppHasCaPermission_mismatchedAppPackageName_throwSecurityException()
            throws PackageManager.NameNotFoundException {
        doReturn(new PackageInfo())
                .when(mPackageManagerMock)
                .getPackageInfo(
                        eq(CustomAudienceFixture.VALID_OWNER), eq(PackageManager.GET_PERMISSIONS));
        when(PermissionHelper.hasPermission(
                        sContext,
                        sPackageName,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE))
                .thenReturn(false);

        String mismatchedAppPackageName = "mismatchedAppPackageName";
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mChecker.assertAppDeclaredPermission(
                                        sContext,
                                        mismatchedAppPackageName,
                                        API_NAME_LOGGING_ID,
                                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_PERMISSION_NOT_REQUESTED_ERROR_MESSAGE,
                exception.getMessage());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID),
                        eq(mismatchedAppPackageName),
                        eq(STATUS_PERMISSION_NOT_REQUESTED),
                        anyInt());
        verifyNoMoreInteractions(mAdServicesLoggerMock);
        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock);
    }

    @Test
    public void testAssertAppHasPasPermission_mismatchedAppPackageName_throwSecurityException()
            throws PackageManager.NameNotFoundException {
        doReturn(new PackageInfo())
                .when(mPackageManagerMock)
                .getPackageInfo(
                        eq(CustomAudienceFixture.VALID_OWNER), eq(PackageManager.GET_PERMISSIONS));
        when(PermissionHelper.hasPermission(
                        sContext,
                        sContext.getPackageName(),
                        AdServicesPermissions.ACCESS_ADSERVICES_PROTECTED_SIGNALS))
                .thenReturn(false);

        String mismatchedAppPackageName = "mismatchedAppPackageName";
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mChecker.assertAppDeclaredPermission(
                                        sContext,
                                        mismatchedAppPackageName,
                                        API_NAME_LOGGING_ID,
                                        AdServicesPermissions.ACCESS_ADSERVICES_PROTECTED_SIGNALS));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_PERMISSION_NOT_REQUESTED_ERROR_MESSAGE,
                exception.getMessage());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID),
                        eq(mismatchedAppPackageName),
                        eq(STATUS_PERMISSION_NOT_REQUESTED),
                        anyInt());
        verifyNoMoreInteractions(mAdServicesLoggerMock);
        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock);
    }

    @Test
    public void testAssertAppHasCaPermission_nullContext_throwNpe() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.assertAppDeclaredPermission(
                                null,
                                CustomAudienceFixture.VALID_OWNER,
                                API_NAME_LOGGING_ID,
                                AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE));

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAppHasPasPermission_nullContext_throwNpe() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.assertAppDeclaredPermission(
                                null,
                                CustomAudienceFixture.VALID_OWNER,
                                API_NAME_LOGGING_ID,
                                AdServicesPermissions.ACCESS_ADSERVICES_PROTECTED_SIGNALS));

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAdTechHasPermission_hasPermission() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(-1);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(0);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(0);
        when(mEnrollmentDaoMock.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(ENROLLMENT_DATA);
        when(AppManifestConfigHelper.isAllowedCustomAudiencesAccess(PACKAGE_NAME, ENROLLMENT_ID))
                .thenReturn(true);

        mChecker.assertAdTechAllowed(
                sContext,
                PACKAGE_NAME,
                CommonFixture.VALID_BUYER_1,
                API_NAME_LOGGING_ID,
                API_CUSTOM_AUDIENCES);

        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verifyNoMoreInteractions(mEnrollmentDaoMock);
        verifyZeroInteractions(mPackageManagerMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAdTechHasSignalsPermission_hasSignalsPermission() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(-1);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(0);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(0);
        when(mEnrollmentDaoMock.getEnrollmentDataForPASByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(ENROLLMENT_DATA);
        when(AppManifestConfigHelper.isAllowedProtectedSignalsAccess(PACKAGE_NAME, ENROLLMENT_ID))
                .thenReturn(true);

        mChecker.assertAdTechAllowed(
                sContext,
                PACKAGE_NAME,
                CommonFixture.VALID_BUYER_1,
                API_NAME_LOGGING_ID,
                API_PROTECTED_SIGNALS);

        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForPASByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verifyNoMoreInteractions(mEnrollmentDaoMock);
        verifyZeroInteractions(mPackageManagerMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAdTechHasCAPermission_hasAdSelectionPermission() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(-1);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(0);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(0);
        when(mEnrollmentDaoMock.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(ENROLLMENT_DATA);
        when(mEnrollmentDaoMock.getEnrollmentDataForPASByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(null);
        when(AppManifestConfigHelper.isAllowedAdSelectionAccess(PACKAGE_NAME, ENROLLMENT_ID))
                .thenReturn(true);

        mChecker.assertAdTechAllowed(
                sContext,
                PACKAGE_NAME,
                CommonFixture.VALID_BUYER_1,
                API_NAME_LOGGING_ID,
                API_AD_SELECTION);

        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verifyNoMoreInteractions(mEnrollmentDaoMock);
        verifyZeroInteractions(mPackageManagerMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAdTechHasSignalsPermission_hasAdSelectionPermission() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(-1);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(0);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(0);
        when(mEnrollmentDaoMock.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(null);
        when(mEnrollmentDaoMock.getEnrollmentDataForPASByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(ENROLLMENT_DATA);
        when(AppManifestConfigHelper.isAllowedAdSelectionAccess(PACKAGE_NAME, ENROLLMENT_ID))
                .thenReturn(true);

        mChecker.assertAdTechAllowed(
                sContext,
                PACKAGE_NAME,
                CommonFixture.VALID_BUYER_1,
                API_NAME_LOGGING_ID,
                API_AD_SELECTION);

        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForPASByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verifyNoMoreInteractions(mEnrollmentDaoMock);
        verifyZeroInteractions(mPackageManagerMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAdTechHasPermission_badApiType_throwsIllegalState() {
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                mChecker.assertAdTechAllowed(
                                        sContext,
                                        PACKAGE_NAME,
                                        CommonFixture.VALID_BUYER_1,
                                        API_NAME_LOGGING_ID,
                                        INVALID_API));

        assertEquals(
                String.format(Locale.ENGLISH, INVALID_API_TYPE, INVALID_API),
                exception.getMessage());
    }

    @Test
    public void testAssertAdTechHasPermission_noEnrollmentForAdTech_throwSecurityException() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(2);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(1);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(3);
        when(mEnrollmentDaoMock.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(null);

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mChecker.assertAdTechAllowed(
                                        sContext,
                                        PACKAGE_NAME,
                                        CommonFixture.VALID_BUYER_1,
                                        API_NAME_LOGGING_ID,
                                        API_CUSTOM_AUDIENCES));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE,
                exception.getMessage());
        verify(mEnrollmentUtilMock).getBuildId();
        verify(mEnrollmentUtilMock).getFileGroupStatus();
        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID),
                        eq(PACKAGE_NAME),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        anyInt());
        verify(mEnrollmentUtilMock)
                .logEnrollmentFailedStats(
                        eq(mAdServicesLoggerMock),
                        eq(2),
                        eq(1),
                        eq(3),
                        eq(CommonFixture.VALID_BUYER_1.toString()),
                        eq(
                                EnrollmentStatus.ErrorCause.ENROLLMENT_NOT_FOUND_ERROR_CAUSE
                                        .getValue()));
        verifyNoMoreInteractions(mEnrollmentDaoMock, mAdServicesLoggerMock);
        verifyZeroInteractions(mPackageManagerMock);
    }

    @Test
    public void testAssertAdTechHasPermission_appManifestNoPermission_throwSecurityException() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(2);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(1);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(3);
        when(mEnrollmentDaoMock.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(ENROLLMENT_DATA);
        when(AppManifestConfigHelper.isAllowedCustomAudiencesAccess(PACKAGE_NAME, ENROLLMENT_ID))
                .thenReturn(false);

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mChecker.assertAdTechAllowed(
                                        sContext,
                                        PACKAGE_NAME,
                                        CommonFixture.VALID_BUYER_1,
                                        API_NAME_LOGGING_ID,
                                        API_CUSTOM_AUDIENCES));

        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE,
                exception.getMessage());
        verify(mEnrollmentUtilMock).getBuildId();
        verify(mEnrollmentUtilMock).getFileGroupStatus();
        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID),
                        eq(PACKAGE_NAME),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        anyInt());
        verify(mEnrollmentUtilMock)
                .logEnrollmentFailedStats(
                        eq(mAdServicesLoggerMock),
                        eq(2),
                        eq(1),
                        eq(3),
                        eq(CommonFixture.VALID_BUYER_1.toString()),
                        eq(EnrollmentStatus.ErrorCause.UNKNOWN_ERROR_CAUSE.getValue()));
        verifyNoMoreInteractions(mEnrollmentDaoMock, mAdServicesLoggerMock);
        verifyZeroInteractions(mPackageManagerMock);
    }

    @Test
    public void testAdTechInBlocklist_throwSecurityException() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(2);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(1);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(3);
        when(mEnrollmentDaoMock.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(ENROLLMENT_DATA);
        when(AppManifestConfigHelper.isAllowedCustomAudiencesAccess(PACKAGE_NAME, ENROLLMENT_ID))
                .thenReturn(true);
        // Add ENROLLMENT_ID to blocklist.
        when(mFlags.isEnrollmentBlocklisted(ENROLLMENT_ID)).thenReturn(true);

        assertThrows(
                SecurityException.class,
                () ->
                        mChecker.assertAdTechAllowed(
                                sContext,
                                PACKAGE_NAME,
                                CommonFixture.VALID_BUYER_1,
                                API_NAME_LOGGING_ID,
                                API_CUSTOM_AUDIENCES));

        verify(mEnrollmentUtilMock).getBuildId();
        verify(mEnrollmentUtilMock).getFileGroupStatus();
        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID),
                        eq(PACKAGE_NAME),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        anyInt());
        verify(mEnrollmentUtilMock)
                .logEnrollmentFailedStats(
                        eq(mAdServicesLoggerMock),
                        eq(2),
                        eq(1),
                        eq(3),
                        eq(CommonFixture.VALID_BUYER_1.toString()),
                        eq(
                                EnrollmentStatus.ErrorCause.ENROLLMENT_BLOCKLISTED_ERROR_CAUSE
                                        .getValue()));
        verifyNoMoreInteractions(mEnrollmentDaoMock, mAdServicesLoggerMock);
        verifyZeroInteractions(mPackageManagerMock);
    }

    @Test
    public void testAssertAdTechHasPermission_nullContext_throwNpe() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.assertAdTechAllowed(
                                null,
                                PACKAGE_NAME,
                                CommonFixture.VALID_BUYER_1,
                                API_NAME_LOGGING_ID,
                                API_CUSTOM_AUDIENCES));

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAdTechHasPermission_nullPackageName_throwNpe() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.assertAdTechAllowed(
                                sContext,
                                null,
                                CommonFixture.VALID_BUYER_1,
                                API_NAME_LOGGING_ID,
                                API_CUSTOM_AUDIENCES));

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertAdTechHasPermission_nullAdTechIdentifier_throwNpe() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.assertAdTechAllowed(
                                sContext,
                                PACKAGE_NAME,
                                null,
                                API_NAME_LOGGING_ID,
                                API_CUSTOM_AUDIENCES));

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAdTechNotEnrolled_throwSecurityException() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(-1);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(0);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(3);
        when(mEnrollmentDaoMock.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(null);

        assertThrows(
                SecurityException.class,
                () ->
                        mChecker.assertAdTechEnrolled(
                                CommonFixture.VALID_BUYER_1, API_NAME_LOGGING_ID));

        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID), eq(STATUS_CALLER_NOT_ALLOWED), anyInt());
        verify(mEnrollmentUtilMock)
                .logEnrollmentFailedStats(
                        eq(mAdServicesLoggerMock),
                        eq(-1),
                        eq(0),
                        eq(3),
                        eq(CommonFixture.VALID_BUYER_1.toString()),
                        eq(
                                EnrollmentStatus.ErrorCause.ENROLLMENT_NOT_FOUND_ERROR_CAUSE
                                        .getValue()));
    }

    @Test
    public void testAdTechEnrolled_isEnrolled() {
        when(mEnrollmentDaoMock.getEnrollmentDataForFledgeByAdTechIdentifier(
                        CommonFixture.VALID_BUYER_1))
                .thenReturn(ENROLLMENT_DATA);

        mChecker.assertAdTechEnrolled(CommonFixture.VALID_BUYER_1, API_NAME_LOGGING_ID);

        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(CommonFixture.VALID_BUYER_1);
        verifyNoMoreInteractions(mEnrollmentDaoMock);
    }

    @Test
    public void testGetAndAssertAdTechFromUriAllowed_nullContext_throwsNullPointerException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.getAndAssertAdTechFromUriAllowed(
                                /* context= */ null,
                                PACKAGE_NAME,
                                URI_FOR_AD_TECH,
                                API_NAME_LOGGING_ID,
                                API_CUSTOM_AUDIENCES));

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void
            testGetAndAssertAdTechFromUriAllowed_nullAppPackageName_throwsNullPointerException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.getAndAssertAdTechFromUriAllowed(
                                sContext,
                                /* appPackageName= */ null,
                                URI_FOR_AD_TECH,
                                API_NAME_LOGGING_ID,
                                API_CUSTOM_AUDIENCES));

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testGetAndAssertAdTechFromUriAllowed_nullUriForAdTech_throwsNullPointerException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.getAndAssertAdTechFromUriAllowed(
                                sContext,
                                PACKAGE_NAME,
                                /* uriForAdTech= */ null,
                                API_NAME_LOGGING_ID,
                                API_CUSTOM_AUDIENCES));

        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testGetAndAssertAdTechFromUriAllowed_notEnrolled_throwsNotAllowedException() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(2);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(1);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(3);
        doReturn(null)
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));

        assertThrows(
                FledgeAuthorizationFilter.AdTechNotAllowedException.class,
                () ->
                        mChecker.getAndAssertAdTechFromUriAllowed(
                                sContext,
                                PACKAGE_NAME,
                                URI_FOR_AD_TECH,
                                API_NAME_LOGGING_ID,
                                API_CUSTOM_AUDIENCES));

        verify(mEnrollmentUtilMock).getBuildId();
        verify(mEnrollmentUtilMock).getFileGroupStatus();
        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID),
                        eq(PACKAGE_NAME),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        anyInt());
        verify(mEnrollmentUtilMock)
                .logEnrollmentFailedStats(
                        eq(mAdServicesLoggerMock),
                        eq(2),
                        eq(1),
                        eq(3),
                        eq(URI_FOR_AD_TECH.toString()),
                        eq(
                                EnrollmentStatus.ErrorCause.ENROLLMENT_NOT_FOUND_ERROR_CAUSE
                                        .getValue()));
        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testGetAndAssertAdTechFromUriAllowed_notAllowedByApp_throwsNotAllowedException() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(2);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(1);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(3);
        doReturn(new Pair<>(CommonFixture.VALID_BUYER_1, ENROLLMENT_DATA))
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        doReturn(false)
                .when(
                        () ->
                                AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                        PACKAGE_NAME, ENROLLMENT_ID));
        when(mFlags.isEnrollmentBlocklisted(ENROLLMENT_ID)).thenReturn(false);

        assertThrows(
                FledgeAuthorizationFilter.AdTechNotAllowedException.class,
                () ->
                        mChecker.getAndAssertAdTechFromUriAllowed(
                                sContext,
                                PACKAGE_NAME,
                                URI_FOR_AD_TECH,
                                API_NAME_LOGGING_ID,
                                API_CUSTOM_AUDIENCES));

        verify(mEnrollmentUtilMock).getBuildId();
        verify(mEnrollmentUtilMock).getFileGroupStatus();
        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        verify(
                () ->
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                PACKAGE_NAME, ENROLLMENT_ID));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID),
                        eq(PACKAGE_NAME),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        anyInt());
        verify(mEnrollmentUtilMock)
                .logEnrollmentFailedStats(
                        eq(mAdServicesLoggerMock),
                        eq(2),
                        eq(1),
                        eq(3),
                        eq(URI_FOR_AD_TECH.toString()),
                        eq(EnrollmentStatus.ErrorCause.UNKNOWN_ERROR_CAUSE.getValue()));
        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testGetAndAssertAdTechFromUriAllowed_adTechBlocklisted_throwsNotAllowedException() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(2);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(1);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(3);
        doReturn(new Pair<>(CommonFixture.VALID_BUYER_1, ENROLLMENT_DATA))
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        doReturn(true)
                .when(
                        () ->
                                AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                        PACKAGE_NAME, ENROLLMENT_ID));
        when(mFlags.isEnrollmentBlocklisted(ENROLLMENT_ID)).thenReturn(true);

        assertThrows(
                FledgeAuthorizationFilter.AdTechNotAllowedException.class,
                () ->
                        mChecker.getAndAssertAdTechFromUriAllowed(
                                sContext,
                                PACKAGE_NAME,
                                URI_FOR_AD_TECH,
                                API_NAME_LOGGING_ID,
                                API_CUSTOM_AUDIENCES));

        verify(mEnrollmentUtilMock).getBuildId();
        verify(mEnrollmentUtilMock).getFileGroupStatus();
        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        verify(
                () ->
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                PACKAGE_NAME, ENROLLMENT_ID));
        verify(mFlags).isEnrollmentBlocklisted(ENROLLMENT_ID);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME_LOGGING_ID),
                        eq(PACKAGE_NAME),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        anyInt());
        verify(mEnrollmentUtilMock)
                .logEnrollmentFailedStats(
                        eq(mAdServicesLoggerMock),
                        eq(2),
                        eq(1),
                        eq(3),
                        eq(URI_FOR_AD_TECH.toString()),
                        eq(
                                EnrollmentStatus.ErrorCause.ENROLLMENT_BLOCKLISTED_ERROR_CAUSE
                                        .getValue()));
        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testGetAndAssertAdTechFromUriAllowed_enrolled_returnsAdTechIdentifier() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(-1);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(0);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(0);
        doReturn(new Pair<>(CommonFixture.VALID_BUYER_1, ENROLLMENT_DATA))
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        doReturn(true)
                .when(
                        () ->
                                AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                        PACKAGE_NAME, ENROLLMENT_ID));
        when(mFlags.isEnrollmentBlocklisted(ENROLLMENT_ID)).thenReturn(false);

        AdTechIdentifier returnedAdTechIdentifier =
                mChecker.getAndAssertAdTechFromUriAllowed(
                        sContext,
                        PACKAGE_NAME,
                        URI_FOR_AD_TECH,
                        API_NAME_LOGGING_ID,
                        API_CUSTOM_AUDIENCES);

        assertWithMessage("Returned AdTechIdentifier")
                .that(returnedAdTechIdentifier)
                .isEqualTo(CommonFixture.VALID_BUYER_1);

        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        verify(
                () ->
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                PACKAGE_NAME, ENROLLMENT_ID));
        verify(mFlags).isEnrollmentBlocklisted(ENROLLMENT_ID);
        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testGetAndAssertAdTechFromUriAllowedSignals_enrolled_returnsAdTechIdentifier() {
        when(mEnrollmentUtilMock.getBuildId()).thenReturn(-1);
        when(mEnrollmentUtilMock.getFileGroupStatus()).thenReturn(0);
        when(mEnrollmentDaoMock.getEnrollmentRecordCountForLogging()).thenReturn(0);
        doReturn(new Pair<>(CommonFixture.VALID_BUYER_1, ENROLLMENT_DATA))
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForPASByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        doReturn(true)
                .when(
                        () ->
                                AppManifestConfigHelper.isAllowedProtectedSignalsAccess(
                                        PACKAGE_NAME, ENROLLMENT_ID));
        when(mFlags.isEnrollmentBlocklisted(ENROLLMENT_ID)).thenReturn(false);

        AdTechIdentifier returnedAdTechIdentifier =
                mChecker.getAndAssertAdTechFromUriAllowed(
                        sContext,
                        PACKAGE_NAME,
                        URI_FOR_AD_TECH,
                        API_NAME_LOGGING_ID,
                        API_PROTECTED_SIGNALS);

        assertWithMessage("Returned AdTechIdentifier")
                .that(returnedAdTechIdentifier)
                .isEqualTo(CommonFixture.VALID_BUYER_1);

        verify(mEnrollmentDaoMock).getEnrollmentRecordCountForLogging();
        verify(mEnrollmentDaoMock)
                .getEnrollmentDataForPASByMatchingAdTechIdentifier(eq(URI_FOR_AD_TECH));
        verify(
                () ->
                        AppManifestConfigHelper.isAllowedProtectedSignalsAccess(
                                PACKAGE_NAME, ENROLLMENT_ID));
        verify(mFlags).isEnrollmentBlocklisted(ENROLLMENT_ID);
        verifyZeroInteractions(mPackageManagerMock, mEnrollmentDaoMock, mAdServicesLoggerMock);
    }

    @Test
    public void testGetAndAssertAdTechFromUriAllowed_badApiType_throwsIllegalState() {
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                mChecker.getAndAssertAdTechFromUriAllowed(
                                        sContext,
                                        PACKAGE_NAME,
                                        URI_FOR_AD_TECH,
                                        API_NAME_LOGGING_ID,
                                        INVALID_API));
        assertEquals(
                String.format(Locale.ENGLISH, INVALID_API_TYPE, INVALID_API),
                exception.getMessage());
    }
}
