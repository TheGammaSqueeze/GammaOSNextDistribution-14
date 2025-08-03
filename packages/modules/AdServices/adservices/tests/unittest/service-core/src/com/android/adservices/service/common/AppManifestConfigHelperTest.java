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

package com.android.adservices.service.common;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockIsAtLeastS;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.doNothingOnErrorLogUtilError;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.verifyErrorLogUtilError;
import static com.android.adservices.service.common.AppManifestConfigCall.API_AD_SELECTION;
import static com.android.adservices.service.common.AppManifestConfigCall.API_ATTRIBUTION;
import static com.android.adservices.service.common.AppManifestConfigCall.API_CUSTOM_AUDIENCES;
import static com.android.adservices.service.common.AppManifestConfigCall.API_PROTECTED_SIGNALS;
import static com.android.adservices.service.common.AppManifestConfigCall.API_TOPICS;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_APP_ALLOWS_ALL;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_APP_DOES_NOT_EXIST;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_BY_APP;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_MANIFEST_CONFIG_PARSING_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManager.Property;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.exception.XmlParseException;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.verification.VerificationMode;

@SpyStatic(AppManifestConfigParser.class)
@SpyStatic(AppManifestConfigMetricsLogger.class)
@SpyStatic(AndroidManifestConfigParser.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(SdkLevel.class)
@SpyStatic(ErrorLogUtil.class)
public final class AppManifestConfigHelperTest extends AdServicesExtendedMockitoTestCase {

    // The extra calls to CA and PAS to verify if they work for ad selection contribute here
    private static final int NUM_COMPONENTS = 8;
    private static final int RESOURCE_ID = 123;
    private static final String AD_SERVICES_CONFIG_PROPERTY =
            "android.adservices.AD_SERVICES_CONFIG";
    private static final String PACKAGE_NAME = "TEST_PACKAGE";
    private static final String ENROLLMENT_ID = "ENROLLMENT_ID";

    // Constants for generic allowed / disallowed calls - the "type" doesn't matter
    private static final int RESULT_ALLOWED = RESULT_ALLOWED_APP_ALLOWS_ALL;

    @Mock private AppManifestConfig mMockAppManifestConfig;
    @Mock private AppManifestIncludesSdkLibraryConfig mMockSdkLibraryConfig;
    @Mock private Context mMockContext;
    @Mock private PackageManager mMockPackageManager;
    @Mock private AssetManager mMockAssetManager;
    @Mock private Resources mMockResources;
    @Mock private XmlResourceParser mMockParser;
    @Mock private Flags mMockFlags;

    @Before
    public void setCommonExpectations() {
        appContext.set(mMockContext);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        extendedMockito.mockGetFlags(mMockFlags);
        setEnabledByDefault(false);
        doNothingOnErrorLogUtilError();
        doNothing().when(() -> AppManifestConfigMetricsLogger.logUsage(any()));
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedAttributionAccess_sPlus() throws Exception {
        mockGetPropertySucceeds(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY, RESOURCE_ID);
        mockAppManifestConfigParserGetConfigSucceeds();
        mockIsAllowedAttributionAccess(ENROLLMENT_ID, RESULT_ALLOWED);

        assertWithMessage("isAllowedAttributionAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedAttributionAccess(
                                PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();

        verifyLogUsage(API_ATTRIBUTION, RESULT_ALLOWED);
    }

    @Test
    public void testIsAllowedAttributionAccess_rMinus() throws Exception {
        mockSdkLevelR();
        mockGetAssetSucceeds(PACKAGE_NAME, RESOURCE_ID);
        mockAppManifestConfigParserGetConfigSucceeds();
        mockIsAllowedAttributionAccess(ENROLLMENT_ID, RESULT_ALLOWED);

        assertWithMessage("isAllowedAttributionAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedAttributionAccess(
                                PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();

        verifyLogUsage(API_ATTRIBUTION, RESULT_ALLOWED);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedCustomAudiencesAccess_sPlus() throws Exception {
        mockGetPropertySucceeds(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY, RESOURCE_ID);
        mockAppManifestConfigParserGetConfigSucceeds();
        mockIsAllowedCustomAudiencesAccess(ENROLLMENT_ID, RESULT_ALLOWED);

        assertWithMessage(
                        "isAllowedCustomAudiencesAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();

        verifyLogUsage(API_CUSTOM_AUDIENCES, RESULT_ALLOWED);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedProtectedSignalsAccess_sPlus() throws Exception {
        mockGetPropertySucceeds(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY, RESOURCE_ID);
        mockAppManifestConfigParserGetConfigSucceeds();
        mockIsAllowedProtectedSignalsAccess(ENROLLMENT_ID, RESULT_ALLOWED);

        assertWithMessage(
                        "isAllowedProtectedSignalsAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedProtectedSignalsAccess(
                                PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();

        verifyLogUsage(API_PROTECTED_SIGNALS, RESULT_ALLOWED);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedAdSelectionAccess_sPlus() throws Exception {
        mockGetPropertySucceeds(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY, RESOURCE_ID);
        mockAppManifestConfigParserGetConfigSucceeds();
        mockIsAllowedAdSelectionAccess(ENROLLMENT_ID, RESULT_ALLOWED);

        assertWithMessage("isAllowedAdSelectionAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedAdSelectionAccess(
                                PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();

        verifyLogUsage(API_AD_SELECTION, RESULT_ALLOWED);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedAdSelectionAccessCustomAudienceTag() throws Exception {
        mockGetPropertySucceeds(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY, RESOURCE_ID);
        mockAppManifestConfigParserGetConfigSucceeds();
        mockIsAllowedAdSelectionAccess(ENROLLMENT_ID, RESULT_DISALLOWED_BY_APP);
        mockIsAllowedCustomAudiencesAccess(ENROLLMENT_ID, RESULT_ALLOWED);

        assertWithMessage("isAllowedAdSelectionAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedAdSelectionAccess(
                                PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();

        verifyLogUsage(API_CUSTOM_AUDIENCES, RESULT_ALLOWED);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedAdSelectionAccessProtectedSignalsTag() throws Exception {
        mockGetPropertySucceeds(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY, RESOURCE_ID);
        mockAppManifestConfigParserGetConfigSucceeds();
        mockIsAllowedAdSelectionAccess(ENROLLMENT_ID, RESULT_DISALLOWED_BY_APP);
        mockIsAllowedProtectedSignalsAccess(ENROLLMENT_ID, RESULT_ALLOWED);

        assertWithMessage("isAllowedAdSelectionAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedAdSelectionAccess(
                                PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();

        verifyLogUsage(API_PROTECTED_SIGNALS, RESULT_ALLOWED);
    }

    @Test
    public void testIsAllowedCustomAudiencesAccess_rMinus() throws Exception {
        mockSdkLevelR();
        mockGetAssetSucceeds(PACKAGE_NAME, RESOURCE_ID);
        mockAppManifestConfigParserGetConfigSucceeds();
        mockIsAllowedCustomAudiencesAccess(ENROLLMENT_ID, RESULT_ALLOWED);

        assertWithMessage(
                        "isAllowedCustomAudiencesAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();

        verifyLogUsage(API_CUSTOM_AUDIENCES, RESULT_ALLOWED);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedTopicsAccessFromSandbox_allowed_sPlus() throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ false,
                /* useSandboxCheck= */ true,
                /* containsSdk= */ false,
                /* topicsAllowed= */ true,
                /* expectedAllowed= */ true);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedTopicsAccessFromSandbox_notAllowed_sPlus() throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ false,
                /* useSandboxCheck= */ true,
                /* containsSdk= */ false,
                /* topicsAllowed= */ false,
                /* expectedAllowed= */ false);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedTopicsAccessFromApp_allowed_sPlus() throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ false,
                /* useSandboxCheck= */ false,
                /* containsSdk= */ true,
                /* topicsAllowed= */ true,
                /* expectedAllowed= */ true);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedTopicsAccessFromApp_notAllowedBecauseOfSdk_sPlus() throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ false,
                /* useSandboxCheck= */ false,
                /* containsSdk= */ false,
                /* topicsAllowed= */ true,
                /* expectedAllowed= */ false);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedTopicsAccessFromApp_notAllowedBecauseOfTopics_sPlus()
            throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ false,
                /* useSandboxCheck= */ false,
                /* containsSdk= */ true,
                /* topicsAllowed= */ false,
                /* expectedAllowed= */ false);
    }

    @Test
    public void testIsAllowedTopicsAccessFromSandbox_allowed_rMinus() throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ true,
                /* useSandboxCheck= */ true,
                /* containsSdk= */ false,
                /* topicsAllowed= */ true,
                /* expectedAllowed= */ true);
    }

    @Test
    public void testIsAllowedTopicsAccessFromSandbox_notAllowed_rMinus() throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ true,
                /* useSandboxCheck= */ true,
                /* containsSdk= */ false,
                /* topicsAllowed= */ false,
                /* expectedAllowed= */ false);
    }

    @Test
    public void testIsAllowedTopicsAccessFromApp_allowed_rMinus() throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ true,
                /* useSandboxCheck= */ false,
                /* containsSdk= */ true,
                /* topicsAllowed= */ true,
                /* expectedAllowed= */ true);
    }

    @Test
    public void testIsAllowedTopicsAccessFromApp_notAllowedBecauseOfSdk_rMinus() throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ true,
                /* useSandboxCheck= */ false,
                /* containsSdk= */ false,
                /* topicsAllowed= */ true,
                /* expectedAllowed= */ false);
    }

    @Test
    public void testIsAllowedTopicsAccessFromApp_notAllowedBecauseOfTopics_rMinus()
            throws Exception {
        executeIsAllowedTopicAccessTest(
                /* isRMinus= */ true,
                /* useSandboxCheck= */ false,
                /* containsSdk= */ true,
                /* topicsAllowed= */ false,
                /* expectedAllowed= */ false);
    }

    private void executeIsAllowedTopicAccessTest(
            boolean isRMinus,
            boolean useSandboxCheck,
            boolean containsSdk,
            boolean topicsAllowed,
            boolean expectedAllowed)
            throws Exception {
        if (isRMinus) {
            mockSdkLevelR();
            mockGetAssetSucceeds(PACKAGE_NAME, RESOURCE_ID);
        } else {
            mockGetPropertySucceeds(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY, RESOURCE_ID);
        }
        mockAppManifestConfigParserGetConfigSucceeds();
        mockContainsSdk(ENROLLMENT_ID, containsSdk);
        int result =
                expectedAllowed ? RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID : RESULT_DISALLOWED_BY_APP;
        mockIsAllowedTopicsAccess(ENROLLMENT_ID, result);
        assertWithMessage("isAllowedTopicsAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedTopicsAccess(
                                /* useSandboxCheck= */ useSandboxCheck,
                                PACKAGE_NAME,
                                ENROLLMENT_ID))
                .isEqualTo(expectedAllowed);

        verifyLogUsage(API_TOPICS, result);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedApiAccess_parsingExceptionSwallowed_sPlus() throws Exception {
        mockGetPropertySucceeds(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY, RESOURCE_ID);
        Exception e = mockAppManifestConfigParserGetConfigThrows();

        assertNoAccessAllowed();

        verifyErrorLogUtilErrorLogged(e, times(NUM_COMPONENTS)); // Called once for each API
        verifyLogUsageForAllApis(RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR);
    }

    @Test
    public void testIsAllowedApiAccess_parsingExceptionSwallowed_rMinus() throws Exception {
        mockSdkLevelR();
        mockGetAssetSucceeds(PACKAGE_NAME, RESOURCE_ID);
        Exception e = mockAppManifestConfigParserGetConfigThrows();

        assertNoAccessAllowed();

        verifyErrorLogUtilErrorLogged(e, times(NUM_COMPONENTS)); // Called once for each API
        verifyLogUsageForAllApis(RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedApiAccess_parsingExceptionSwallowed_enabledByDefault_sPlus()
            throws Exception {
        setEnabledByDefault(true);
        mockGetPropertySucceeds(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY, RESOURCE_ID);
        Exception e = mockAppManifestConfigParserGetConfigThrows();

        assertNoAccessAllowed();

        verifyErrorLogUtilErrorLogged(e, times(NUM_COMPONENTS)); // Called once for each API
        verifyLogUsageForAllApis(RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR);
    }

    @Test
    public void testIsAllowedApiAccess_parsingExceptionSwallowed_enabledByDefault_rMinus()
            throws Exception {
        setEnabledByDefault(true);
        mockSdkLevelR();
        mockGetAssetSucceeds(PACKAGE_NAME, RESOURCE_ID);
        Exception e = mockAppManifestConfigParserGetConfigThrows();

        assertNoAccessAllowed();

        verifyErrorLogUtilErrorLogged(e, times(NUM_COMPONENTS)); // Called once for each API
        verifyLogUsageForAllApis(RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR);
    }

    @Test
    public void testIsAllowedApiAccess_packageNotFound() throws Exception {
        mockAppNotFound(PACKAGE_NAME);

        assertNoAccessAllowed();

        verifyLogUsageForAllApis(RESULT_DISALLOWED_APP_DOES_NOT_EXIST);
    }

    @Test
    public void testIsAllowedApiAccess_packageNotFound_enabledByDefault() throws Exception {
        setEnabledByDefault(true);
        mockAppNotFound(PACKAGE_NAME);

        assertNoAccessAllowed();

        verifyLogUsageForAllApis(RESULT_DISALLOWED_APP_DOES_NOT_EXIST);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedApiAccess_noConfigXmlForPackage_sPlus() throws Exception {
        mockAppFound(PACKAGE_NAME);
        mockGetPropertyNotFound(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY);

        assertNoAccessAllowed();

        verifyLogUsageForAllApis(RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG);
    }

    @Test
    public void testIsAllowedApiAccess_noConfigXmlForPackage_rMinus() throws Exception {
        mockSdkLevelR();
        mockAppFound(PACKAGE_NAME);
        mockGetAssetNotFound(PACKAGE_NAME);

        assertNoAccessAllowed();

        verifyLogUsageForAllApis(RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG);
    }

    @Test
    @RequiresSdkLevelAtLeastS(reason = "Uses PackageManager API not available on R")
    public void testIsAllowedApiAccess_noConfigXmlForPackage_enabledByDefault_sPlus()
            throws Exception {
        setEnabledByDefault(true);
        mockAppFound(PACKAGE_NAME);
        mockGetPropertyNotFound(PACKAGE_NAME, AD_SERVICES_CONFIG_PROPERTY);

        assertAllAccessAllowed();

        verifyLogUsageForAllApis(RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG);
    }

    @Test
    public void testIsAllowedApiAccess_noConfigXmlForPackage_enabledByDefault_rMinus()
            throws Exception {
        setEnabledByDefault(true);
        mockSdkLevelR();
        mockAppFound(PACKAGE_NAME);
        mockGetAssetNotFound(PACKAGE_NAME);

        assertAllAccessAllowed();

        verifyLogUsageForAllApis(RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG);
    }

    private void mockSdkLevelR() {
        if (SdkLevel.isAtLeastS()) {
            mockIsAtLeastS(false);
        } else {
            Log.v(mTag, "mockSdkLevelR(): not needed, device is not at least S");
        }
    }

    private void mockGetPropertySucceeds(String pkgName, String propName, int resId)
            throws Exception {
        Property property = mock(Property.class);
        mockAppFound(pkgName);
        when(mMockPackageManager.getProperty(propName, pkgName)).thenReturn(property);
        when(property.getResourceId()).thenReturn(resId);
        when(mMockResources.getXml(resId)).thenReturn(mMockParser);
    }

    private void mockGetPropertyNotFound(String pkgName, String propName) throws Exception {
        mockAppFound(pkgName);
        when(mMockPackageManager.getProperty(propName, pkgName))
                .thenThrow(new NameNotFoundException("A property has no name."));
    }

    private void mockGetAssetSucceeds(String pkgName, int resId) throws Exception {
        mockAppFound(pkgName);
        when(mMockContext.createPackageContext(pkgName, /* flags= */ 0)).thenReturn(mMockContext);
        when(mMockContext.getAssets()).thenReturn(mMockAssetManager);
        when(mMockAssetManager.openXmlResourceParser(anyString())).thenReturn(mMockParser);
        doReturn(resId)
                .when(
                        () ->
                                AndroidManifestConfigParser.getAdServicesConfigResourceId(
                                        mMockParser, mMockResources));
        when(mMockResources.getXml(resId)).thenReturn(mMockParser);
    }

    private void mockGetAssetNotFound(String pkgName) throws Exception {
        mockAppFound(pkgName);
        when(mMockContext.createPackageContext(pkgName, /* flags= */ 0)).thenReturn(mMockContext);
        when(mMockContext.getAssets()).thenReturn(mMockAssetManager);
        when(mMockAssetManager.openXmlResourceParser(anyString())).thenReturn(mMockParser);
        doReturn(null)
                .when(
                        () ->
                                AndroidManifestConfigParser.getAdServicesConfigResourceId(
                                        mMockParser, mMockResources));
    }

    private void mockAppFound(String pkgName) throws Exception {
        when(mMockPackageManager.getResourcesForApplication(pkgName)).thenReturn(mMockResources);
    }

    private void mockAppNotFound(String pkgName) throws Exception {
        when(mMockPackageManager.getResourcesForApplication(pkgName))
                .thenThrow(new NameNotFoundException("A package has no name."));
    }

    private void mockAppManifestConfigParserGetConfigSucceeds() throws Exception {
        doReturn(mMockAppManifestConfig)
                .when(() -> AppManifestConfigParser.getConfig(eq(mMockParser), anyBoolean()));
    }

    private XmlParseException mockAppManifestConfigParserGetConfigThrows() throws Exception {
        XmlParseException e = new XmlParseException("D'OH!");
        doThrow(e).when(() -> AppManifestConfigParser.getConfig(eq(mMockParser), anyBoolean()));
        return e;
    }

    private void mockIsAllowedAttributionAccess(String partnerId, int result) {
        when(mMockAppManifestConfig.isAllowedAttributionAccess(partnerId)).thenReturn(result);
    }

    private void mockIsAllowedCustomAudiencesAccess(String partnerId, int result) {
        when(mMockAppManifestConfig.isAllowedCustomAudiencesAccess(partnerId)).thenReturn(result);
    }

    private void mockIsAllowedProtectedSignalsAccess(String partnerId, int result) {
        when(mMockAppManifestConfig.isAllowedProtectedSignalsAccess(partnerId)).thenReturn(result);
    }

    private void mockIsAllowedAdSelectionAccess(String partnerId, int result) {
        when(mMockAppManifestConfig.isAllowedAdSelectionAccess(partnerId)).thenReturn(result);
    }

    private void mockIsAllowedTopicsAccess(String partnerId, int result) {
        when(mMockAppManifestConfig.isAllowedTopicsAccess(partnerId)).thenReturn(result);
    }

    private void mockContainsSdk(String partnerId, boolean value) {
        when(mMockAppManifestConfig.getIncludesSdkLibraryConfig())
                .thenReturn(mMockSdkLibraryConfig);
        when(mMockSdkLibraryConfig.contains(partnerId)).thenReturn(value);
    }

    private void assertNoAccessAllowed() {
        expect.withMessage("isAllowedAttributionAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedAttributionAccess(
                                PACKAGE_NAME, ENROLLMENT_ID))
                .isFalse();
        expect.withMessage(
                        "isAllowedCustomAudiencesAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                PACKAGE_NAME, ENROLLMENT_ID))
                .isFalse();
        expect.withMessage(
                        "isAllowedProtectedSignalsAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedProtectedSignalsAccess(
                                PACKAGE_NAME, ENROLLMENT_ID))
                .isFalse();
        expect.withMessage("isAllowedAdSelectionAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedAdSelectionAccess(
                                PACKAGE_NAME, ENROLLMENT_ID))
                .isFalse();
        expect.withMessage("isAllowedTopicsAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedTopicsAccess(
                                /* useSandboxCheck= */ true, PACKAGE_NAME, ENROLLMENT_ID))
                .isFalse();
        expect.withMessage("isAllowedTopicsAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedTopicsAccess(
                                /* useSandboxCheck= */ false,
                                PACKAGE_NAME,
                                ENROLLMENT_ID))
                .isFalse();
    }

    private void assertAllAccessAllowed() {
        expect.withMessage("isAllowedAttributionAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedAttributionAccess(
                                PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();
        expect.withMessage(
                        "isAllowedCustomAudiencesAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();
        expect.withMessage(
                        "isAllowedProtectedSignalsAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedProtectedSignalsAccess(
                                PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();
        expect.withMessage("isAllowedAdSelectionAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedAdSelectionAccess(
                                PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();
        expect.withMessage("isAllowedTopicsAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedTopicsAccess(
                                /* useSandboxCheck= */ true, PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();
        expect.withMessage("isAllowedTopicsAccess(ctx, %s, %s)", PACKAGE_NAME, ENROLLMENT_ID)
                .that(
                        AppManifestConfigHelper.isAllowedTopicsAccess(
                                /* useSandboxCheck= */ false, PACKAGE_NAME, ENROLLMENT_ID))
                .isTrue();

        verifyErrorLogUtilErrorLogged(any(), never());
    }

    private void verifyErrorLogUtilErrorLogged(Exception e, VerificationMode mode) {
        // NOTE: e is null when passed as any().
        Exception exceptionMatcher = e == null ? e : eq(e);
        verifyErrorLogUtilError(
                exceptionMatcher,
                eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_MANIFEST_CONFIG_PARSING_ERROR),
                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON),
                mode);
    }

    private void verifyLogUsage(int api, int result) {
        verifyLogUsage(api, result, times(1));
    }

    private void verifyLogUsage(int api, int result, VerificationMode mode) {
        AppManifestConfigCall call = new AppManifestConfigCall(PACKAGE_NAME, api);
        call.result = result;

        verify(() -> AppManifestConfigMetricsLogger.logUsage(call), mode);
    }

    private void verifyLogUsageForAllApis(int result) {
        // Cannot use anyInt() for the APIs as logUsage() uses a custom object / matcher - it would
        // be too coplicate to create a generic one for it
        verifyLogUsage(API_TOPICS, result, times(2));
        verifyLogUsage(API_ATTRIBUTION, result);
        if (result == RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG) {
            verifyLogUsage(API_CUSTOM_AUDIENCES, result);
            verifyLogUsage(API_PROTECTED_SIGNALS, result);
        } else {
            verifyLogUsage(API_CUSTOM_AUDIENCES, result, times(2));
            verifyLogUsage(API_PROTECTED_SIGNALS, result, times(2));
        }
        verifyLogUsage(API_AD_SELECTION, result);
    }

    private void setEnabledByDefault(boolean value) {
        when(mMockFlags.getAppConfigReturnsEnabledByDefault()).thenReturn(value);
    }
}
