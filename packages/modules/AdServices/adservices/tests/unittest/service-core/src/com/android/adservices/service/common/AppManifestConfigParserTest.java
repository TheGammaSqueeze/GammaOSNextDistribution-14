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

import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_BY_APP;
import static com.android.adservices.service.common.AppManifestConfigCall.resultToString;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.content.res.XmlResourceParser;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.exception.XmlParseException;
import com.android.adservices.servicecoretest.R;

import org.junit.Test;
import org.xmlpull.v1.XmlPullParserException;

@SmallTest
public final class AppManifestConfigParserTest extends AdServicesUnitTestCase {

    @Test
    public void testValidXml() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config);

        AppManifestConfig appManifestConfig = AppManifestConfigParser.getConfig(parser);
        assertWithMessage("manifest for ad_services_config").that(appManifestConfig).isNotNull();

        // Verify IncludesSdkLibrary tags.
        AppManifestIncludesSdkLibraryConfig includesSdkLibraryConfig =
                appManifestConfig.getIncludesSdkLibraryConfig();
        expect.withMessage("getIncludesSdkLibraryConfig()")
                .that(includesSdkLibraryConfig)
                .isNotNull();
        if (includesSdkLibraryConfig != null) {
            expect.withMessage("getIncludesSdkLibraryConfig().isEmpty()")
                    .that(appManifestConfig.getIncludesSdkLibraryConfig().isEmpty())
                    .isFalse();
            expect.withMessage("getIncludesSdkLibraryConfig().contains(1234)")
                    .that(appManifestConfig.getIncludesSdkLibraryConfig().contains("1234"))
                    .isTrue();
            expect.withMessage("getIncludesSdkLibraryConfig().contains(1234)")
                    .that(appManifestConfig.getIncludesSdkLibraryConfig().contains("4567"))
                    .isTrue();
            expect.withMessage("getIncludesSdkLibraryConfig().contains(1234)")
                    .that(appManifestConfig.getIncludesSdkLibraryConfig().contains("89"))
                    .isTrue();
            expect.withMessage("getIncludesSdkLibraryConfig().contains(1234)")
                    .that(appManifestConfig.getIncludesSdkLibraryConfig().contains("1234567"))
                    .isTrue();
        }

        // Verify Attribution tags.
        expect.withMessage("getAttributionConfig().getAllowAdPartnersToAccess()")
                .that(appManifestConfig.isAllowedAttributionAccess("1234"))
                .isEqualTo(RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID);
        expect.withMessage("isAllowedAttributionAccess()")
                .that(appManifestConfig.isAllowedAttributionAccess("108"))
                .isEqualTo(RESULT_DISALLOWED_BY_APP);
        AppManifestAttributionConfig attributionConfig = appManifestConfig.getAttributionConfig();
        expect.withMessage("getAttributionConfig()").that(attributionConfig).isNotNull();
        if (attributionConfig != null) {
            expect.withMessage("getAttributionConfig().getAllowAllToAccess()")
                    .that(attributionConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getAttributionConfig().getAllowAdPartnersToAccess()")
                    .that(appManifestConfig.getAttributionConfig().getAllowAdPartnersToAccess())
                    .containsExactly("1234");
        }

        // Verify Custom Audience tags.
        expect.withMessage("isAllowedCustomAudiencesAccess()")
                .that(appManifestConfig.isAllowedCustomAudiencesAccess("108"))
                .isEqualTo(RESULT_DISALLOWED_BY_APP);
        AppManifestCustomAudiencesConfig customAudiencesConfig =
                appManifestConfig.getCustomAudiencesConfig();
        expect.withMessage("getCustomAudiencesConfig()").that(customAudiencesConfig).isNotNull();
        if (customAudiencesConfig != null) {
            expect.withMessage("getCustomAudiencesConfig().getAllowAllToAccess()")
                    .that(customAudiencesConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getCustomAudiencesConfig().getAllowAdPartnersToAccess()")
                    .that(customAudiencesConfig.getAllowAdPartnersToAccess())
                    .hasSize(2);
            expect.withMessage("getCustomAudiencesConfig().getAllowAdPartnersToAccess()")
                    .that(customAudiencesConfig.getAllowAdPartnersToAccess())
                    .containsExactly("1234", "4567");
        }

        // Verify Protected Signals tags.
        expect.withMessage("isAllowedProtectedSignalsAccess()")
                .that(appManifestConfig.isAllowedProtectedSignalsAccess("108"))
                .isEqualTo(RESULT_DISALLOWED_BY_APP);
        AppManifestProtectedSignalsConfig protectedSignalsConfig =
                appManifestConfig.getProtectedSignalsConfig();
        expect.withMessage("getProtectedSignalsConfig()").that(protectedSignalsConfig).isNotNull();
        if (customAudiencesConfig != null) {
            expect.withMessage("getProtectedSignalsConfig().getAllowAllToAccess()")
                    .that(protectedSignalsConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getProtectedSignalsConfig().getAllowAdPartnersToAccess()")
                    .that(protectedSignalsConfig.getAllowAdPartnersToAccess())
                    .hasSize(2);
            expect.withMessage("getProtectedSignalsConfig().getAllowAdPartnersToAccess()")
                    .that(protectedSignalsConfig.getAllowAdPartnersToAccess())
                    .containsExactly("42", "43");
        }

        // Verify Ad Selection tags.
        expect.withMessage("isAllowedAdSelectionAccess()")
                .that(appManifestConfig.isAllowedAdSelectionAccess("108"))
                .isEqualTo(RESULT_DISALLOWED_BY_APP);
        AppManifestAdSelectionConfig adSelectionConfig = appManifestConfig.getAdSelectionConfig();
        expect.withMessage("getAdSelectionConfig()").that(adSelectionConfig).isNotNull();
        if (adSelectionConfig != null) {
            expect.withMessage("getAdSelectionConfig().getAllowAllToAccess()")
                    .that(adSelectionConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getAdSelectionConfig().getAllowAdPartnersToAccess()")
                    .that(adSelectionConfig.getAllowAdPartnersToAccess())
                    .hasSize(2);
            expect.withMessage("getAdSelectionConfig().getAllowAdPartnersToAccess()")
                    .that(adSelectionConfig.getAllowAdPartnersToAccess())
                    .containsExactly("44", "45");
        }

        // Verify Topics tags.
        expect.withMessage("1234567()")
                .that(appManifestConfig.isAllowedTopicsAccess("1234567"))
                .isEqualTo(RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID);
        expect.withMessage("isAllowedTopicsAccess()")
                .that(appManifestConfig.isAllowedTopicsAccess("108"))
                .isEqualTo(RESULT_DISALLOWED_BY_APP);
        AppManifestTopicsConfig topicsConfig = appManifestConfig.getTopicsConfig();
        expect.withMessage("getTopicsConfig()").that(topicsConfig).isNotNull();
        if (topicsConfig != null) {
            expect.withMessage("getTopicsConfig().getAllowAllToAccess()")
                    .that(topicsConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getTopicsConfig().getAllowAdPartnersToAccess()")
                    .that(topicsConfig.getAllowAdPartnersToAccess())
                    .contains("1234567");
        }

        // Verify AppId tags.
        expect.withMessage("isAllowedAdIdAccess()")
                .that(appManifestConfig.isAllowedAdIdAccess("42"))
                .isEqualTo(RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID);
        expect.withMessage("isAllowedAdIdAccess()")
                .that(appManifestConfig.isAllowedAdIdAccess("108"))
                .isEqualTo(RESULT_DISALLOWED_BY_APP);
        AppManifestAdIdConfig adIdConfig = appManifestConfig.getAdIdConfig();
        expect.withMessage("getAdIdConfig()").that(adIdConfig).isNotNull();
        if (adIdConfig != null) {
            expect.withMessage("getAdIdConfig().getAllowAllToAccess()")
                    .that(adIdConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getAdIdConfig().getAllowAdPartnersToAccess()")
                    .that(adIdConfig.getAllowAdPartnersToAccess())
                    .containsExactly("4", "8", "15", "16", "23", "42");
        }

        // Verify AppSetId tags.
        expect.withMessage("isAllowedAppSetIdAccess()")
                .that(appManifestConfig.isAllowedAppSetIdAccess("42"))
                .isEqualTo(RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID);
        expect.withMessage("isAllowedAppSetIdAccess()")
                .that(appManifestConfig.isAllowedAppSetIdAccess("108"))
                .isEqualTo(RESULT_DISALLOWED_BY_APP);
        AppManifestAppSetIdConfig appSetIdConfig = appManifestConfig.getAppSetIdConfig();
        expect.withMessage("getAppSetIdConfig()").that(appSetIdConfig).isNotNull();
        if (appSetIdConfig != null) {
            expect.withMessage("getAppSetIdConfig().getAllowAllToAccess()")
                    .that(appSetIdConfig.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("getAppSetIdConfig().getAllowAdPartnersToAccess()")
                    .that(appSetIdConfig.getAllowAdPartnersToAccess())
                    .containsExactly("4", "8", "15", "16", "23", "42");
        }
    }

    @Test
    public void testInvalidXml_missingSdkLibrary() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_missing_sdk_name);

        Exception e =
                assertThrows(
                        XmlParseException.class, () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e)
                .hasMessageThat()
                .isEqualTo("Sdk name not mentioned in <includes-sdk-library>");
    }

    @Test
    public void testInvalidXml_incorrectValues() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_incorrect_values);

        Exception e =
                assertThrows(
                        XmlParseException.class, () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e)
                .hasMessageThat()
                .isEqualTo("allowAll cannot be set to true when allowAdPartners is also set");
    }

    @Test
    public void testValidXml_disabledByDefault_missingAllTags() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_missing_tags);
        AppManifestConfig appManifestConfig = AppManifestConfigParser.getConfig(parser);
        assertWithMessage("manifest for ad_services_config_missing_tags")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ false);

        AppManifestAttributionConfig attributionConfig = appManifestConfig.getAttributionConfig();
        expect.withMessage("getAttributionConfig()").that(attributionConfig).isNull();
        assertResult(
                "isAllowedAttributionAccess()",
                appManifestConfig.isAllowedAttributionAccess("not actually there"),
                RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION);

        AppManifestCustomAudiencesConfig customAudiencesConfig =
                appManifestConfig.getCustomAudiencesConfig();
        expect.withMessage("getCustomAudiencesConfig()").that(customAudiencesConfig).isNull();
        assertResult(
                "isAllowedCustomAudiencesAccess()",
                appManifestConfig.isAllowedCustomAudiencesAccess("not actually there"),
                RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION);

        AppManifestProtectedSignalsConfig protectedSignalsConfig =
                appManifestConfig.getProtectedSignalsConfig();
        expect.withMessage("getProtectedSignalsConfig()").that(protectedSignalsConfig).isNull();
        assertResult(
                "isAllowedProtectedSignalsAccess()",
                appManifestConfig.isAllowedProtectedSignalsAccess("not actually there"),
                RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION);

        AppManifestAdSelectionConfig addSelectionConfig = appManifestConfig.getAdSelectionConfig();
        expect.withMessage("getAdSelectionConfig()").that(addSelectionConfig).isNull();
        assertResult(
                "isAllowedAdSelectionAccess()",
                appManifestConfig.isAllowedAdSelectionAccess("not actually there"),
                RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION);

        AppManifestTopicsConfig topicsConfig = appManifestConfig.getTopicsConfig();
        expect.withMessage("getTopicsConfig()").that(topicsConfig).isNull();
        assertResult(
                "isAllowedTopicsAccess()",
                appManifestConfig.isAllowedTopicsAccess("not actually there"),
                RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION);

        AppManifestAdIdConfig adIdConfig = appManifestConfig.getAdIdConfig();
        expect.withMessage("getAdIdConfig()").that(adIdConfig).isNull();
        assertResult(
                "isAllowedAdIdAccess()",
                appManifestConfig.isAllowedTopicsAccess("not actually there"),
                RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION);

        AppManifestAppSetIdConfig appSetIdConfig = appManifestConfig.getAppSetIdConfig();
        expect.withMessage("getAppSetIdConfig()").that(appSetIdConfig).isNull();
        assertResult(
                "isAllowedAppSetIdAccess()",
                appManifestConfig.isAllowedAppSetIdAccess("not actually there"),
                RESULT_DISALLOWED_APP_HAS_CONFIG_WITHOUT_API_SECTION);
    }

    @Test
    public void testValidXml_enabledByDefault_missingAllTags() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_missing_tags);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_missing_tags")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ true);
        assertAttributionConfigIsAllowed(
                appManifestConfig, RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION);
        assertCustomAudienceConfigIsAllowed(
                appManifestConfig, RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION);
        assertProtectedSignalsConfigIsAllowed(
                appManifestConfig, RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION);
        assertAdSelectionConfigIsAllowed(
                appManifestConfig, RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION);
        assertTopicsConfigIsAllowed(
                appManifestConfig, RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION);
        assertAdIdConfigIsAllowed(
                appManifestConfig, RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION);
        assertAppSetIdConfigIsAllowed(
                appManifestConfig, RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION);
    }

    @Test
    public void testValidXml_enabledByDefault_missingAttribution() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_all_false_missing_attribution);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_missing_attribution")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ true);
        assertAttributionConfigIsAllowed(
                appManifestConfig, RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION);
        assertCustomAudienceConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertProtectedSignalsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdSelectionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertTopicsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAppSetIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
    }

    @Test
    public void testValidXml_enabledByDefault_missingCustomAudiences() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_all_false_missing_custom_audiences);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_missing_custom_audiences")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ true);
        assertAttributionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertCustomAudienceConfigIsAllowed(
                appManifestConfig, RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION);
        assertProtectedSignalsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdSelectionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertTopicsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAppSetIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
    }

    @Test
    public void testValidXml_enabledByDefault_missingProtectedSignals() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_all_false_missing_protected_signals);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_missing_protected_signals")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ true);
        assertAttributionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertCustomAudienceConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertProtectedSignalsConfigIsAllowed(
                appManifestConfig, RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION);
        assertAdSelectionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertTopicsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAppSetIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
    }

    @Test
    public void testValidXml_enabledByDefault_missingAdSelection() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_all_false_missing_ad_selection);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_missing_ad_selection")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ true);
        assertAttributionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertCustomAudienceConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertProtectedSignalsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdSelectionConfigIsAllowed(
                appManifestConfig, RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION);
        assertTopicsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAppSetIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
    }

    @Test
    public void testValidXml_enabledByDefault_missingTopics() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_all_false_missing_topics);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_missing_topics")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ true);
        assertAttributionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertCustomAudienceConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertProtectedSignalsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdSelectionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertTopicsConfigIsAllowed(
                appManifestConfig, RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION);
        assertAdIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAppSetIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
    }

    @Test
    public void testValidXml_enabledByDefault_missingAdId() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_all_false_missing_adid);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_missing_adid")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ true);
        assertAttributionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertCustomAudienceConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertProtectedSignalsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdSelectionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertTopicsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertTopicsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdIdConfigIsAllowed(
                appManifestConfig, RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION);
        assertAppSetIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
    }

    @Test
    public void testValidXml_enabledByDefault_missingAppsetId() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_all_false_missing_appsetid);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_missing_appsetid")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ true);
        assertAttributionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertCustomAudienceConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertProtectedSignalsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdSelectionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertTopicsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAppSetIdConfigIsAllowed(
                appManifestConfig, RESULT_ALLOWED_BY_DEFAULT_APP_HAS_CONFIG_WITHOUT_API_SECTION);
    }

    @Test
    public void testValidXml_enabledByDefault_missingSdkLibraries() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_all_false_missing_sdk_libraries);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_missing_sdk_libraries")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ true);
        assertAttributionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertCustomAudienceConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertProtectedSignalsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdSelectionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertTopicsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAppSetIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
    }

    @Test
    public void testValidXml_enabledByDefault_withSdkLibraries() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        // This XML contains only 42 and 108
                        .getXml(R.xml.ad_services_config_all_false_with_sdk_libraries);
        AppManifestConfig appManifestConfig =
                AppManifestConfigParser.getConfig(parser, /* enabledByDefault= */ true);
        assertWithMessage("manifest for ad_services_config_all_false_with_sdk_libraries")
                .that(appManifestConfig)
                .isNotNull();

        AppManifestIncludesSdkLibraryConfig sdkLibrary =
                appManifestConfig.getIncludesSdkLibraryConfig();
        expect.withMessage("getIncludesSdkLibraryConfig()").that(sdkLibrary).isNotNull();
        expect.withMessage("getIncludesSdkLibraryConfig().isEmpty()")
                .that(sdkLibrary.isEmpty())
                .isFalse();
        expect.withMessage("getIncludesSdkLibraryConfig().contains(42)")
                .that(sdkLibrary.contains("42"))
                .isTrue();
        expect.withMessage("getIncludesSdkLibraryConfig().contains(108)")
                .that(sdkLibrary.contains("108"))
                .isTrue();
        expect.withMessage("getIncludesSdkLibraryConfig().contains(4815162342)")
                .that(sdkLibrary.contains("4815162342"))
                .isFalse();

        assertAttributionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertCustomAudienceConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertProtectedSignalsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdSelectionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertTopicsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAppSetIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
    }

    @Test
    public void testValidXml_missingValues() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_missing_values);
        AppManifestConfig appManifestConfig = AppManifestConfigParser.getConfig(parser);
        assertWithMessage("manifest for ad_services_config_missing_values")
                .that(appManifestConfig)
                .isNotNull();

        assertSdkLibraryConfigIsEmpty(appManifestConfig, /* containsByDefault= */ false);
        assertAttributionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertCustomAudienceConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertProtectedSignalsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdSelectionConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertTopicsConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAdIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
        assertAppSetIdConfigIsAllowed(appManifestConfig, RESULT_DISALLOWED_BY_APP);
    }

    @Test
    public void testInvalidXml_repeatTags() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_repeat_tags);

        Exception e =
                assertThrows(
                        XmlParseException.class, () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e).hasMessageThat().isEqualTo("Tag custom-audiences appears more than once");
    }

    @Test
    public void testInvalidXml_incorrectStartTag() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_incorrect_start_tag);

        Exception e =
                assertThrows(
                        XmlPullParserException.class,
                        () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e).hasMessageThat().isEqualTo("expected START_TAGBinary XML file line #17");
    }

    @Test
    public void testInvalidXml_incorrectTag() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_incorrect_tag);

        Exception e =
                assertThrows(
                        XmlParseException.class, () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e)
                .hasMessageThat()
                .isEqualTo("Unknown tag: foobar [Tags and attributes are case sensitive]");
    }

    @Test
    public void testInvalidXml_incorrectAttr() throws Exception {
        XmlResourceParser parser =
                mContext.getPackageManager()
                        .getResourcesForApplication(mPackageName)
                        .getXml(R.xml.ad_services_config_incorrect_attr);

        Exception e =
                assertThrows(
                        XmlParseException.class, () -> AppManifestConfigParser.getConfig(parser));
        expect.that(e)
                .hasMessageThat()
                .isEqualTo("Unknown attribute: foobar [Tags and attributes are case sensitive]");
    }

    private void assertSdkLibraryConfigIsEmpty(
            AppManifestConfig appManifestConfig, boolean containsByDefault) {
        AppManifestIncludesSdkLibraryConfig sdkLibrary =
                appManifestConfig.getIncludesSdkLibraryConfig();
        expect.withMessage("getIncludesSdkLibraryConfig()").that(sdkLibrary).isNotNull();
        expect.withMessage("getIncludesSdkLibraryConfig().isEmpty()")
                .that(sdkLibrary.isEmpty())
                .isTrue();
        if (containsByDefault) {
            expect.withMessage("getIncludesSdkLibraryConfig().contains(42)")
                    .that(sdkLibrary.contains("42"))
                    .isTrue();
        } else {
            expect.withMessage("getIncludesSdkLibraryConfig().contains(42)")
                    .that(sdkLibrary.contains("42"))
                    .isFalse();
        }
    }

    private void assertResult(String method, int actualResult, int expectedResult) {
        expect.withMessage(
                        "%s (where %s=%s and %s=%s)",
                        method,
                        actualResult,
                        resultToString(actualResult),
                        expectedResult,
                        resultToString(expectedResult))
                .that(actualResult)
                .isEqualTo(expectedResult);
    }

    private void assertAttributionConfigIsAllowed(
            AppManifestConfig appManifestConfig, int expectedResult) {
        int actualResult = appManifestConfig.isAllowedAttributionAccess("not actually there");
        assertResult("getAttributionConfig()", actualResult, expectedResult);
    }

    private void assertCustomAudienceConfigIsAllowed(
            AppManifestConfig appManifestConfig, int expectedResult) {
        int actualResult = appManifestConfig.isAllowedCustomAudiencesAccess("not actually there");
        assertResult("getCustomAudiencesConfig()", actualResult, expectedResult);
    }

    private void assertProtectedSignalsConfigIsAllowed(
            AppManifestConfig appManifestConfig, int expectedResult) {
        int actualResult = appManifestConfig.isAllowedProtectedSignalsAccess("not actually there");
        assertResult("getProtectedSignalsConfig()", actualResult, expectedResult);
    }

    private void assertAdSelectionConfigIsAllowed(
            AppManifestConfig appManifestConfig, int expectedResult) {
        int actualResult = appManifestConfig.isAllowedAdSelectionAccess("not actually there");
        assertResult("getAdSelectionConfig()", actualResult, expectedResult);
    }

    private void assertTopicsConfigIsAllowed(
            AppManifestConfig appManifestConfig, int expectedResult) {
        int actualResult = appManifestConfig.isAllowedTopicsAccess("not actually there");
        assertResult("getTopicsConfig()", actualResult, expectedResult);
    }

    private void assertAdIdConfigIsAllowed(
            AppManifestConfig appManifestConfig, int expectedResult) {
        int actualResult = appManifestConfig.isAllowedAdIdAccess("not actually there");
        assertResult("getAdIdConfig()", actualResult, expectedResult);
    }

    private void assertAppSetIdConfigIsAllowed(
            AppManifestConfig appManifestConfig, int expectedResult) {
        int actualResult = appManifestConfig.isAllowedAppSetIdAccess("not actually there");
        assertResult("getAppSetIdConfig()", actualResult, expectedResult);
    }

    private void assertApiConfigIsDefault(String name, AppManifestApiConfig config) {
        expect.withMessage(name).that(config).isNotNull();
        if (config != null) {
            expect.withMessage("%s.getAllowAllToAccess()", name)
                    .that(config.getAllowAllToAccess())
                    .isTrue();
            expect.withMessage("%s.getAllowAdPartnersToAccess()", name)
                    .that(config.getAllowAdPartnersToAccess())
                    .isEmpty();
        }
    }

    private void assertApiConfigIsFalse(String name, AppManifestApiConfig config) {
        expect.withMessage(name).that(config).isNotNull();
        if (config != null) {
            expect.withMessage("%s.getAllowAllToAccess()", name)
                    .that(config.getAllowAllToAccess())
                    .isFalse();
            expect.withMessage("%s.getAllowAdPartnersToAccess()", name)
                    .that(config.getAllowAdPartnersToAccess())
                    .isEmpty();
        }
    }
}
