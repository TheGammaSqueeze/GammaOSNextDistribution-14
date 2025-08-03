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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.servicecoretest.R;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Rule;
import org.junit.Test;

import java.util.NoSuchElementException;

@SmallTest
public final class AndroidManifestConfigParserTest {
    private static final String TEST_APP_PACKAGE_NAME = "com.android.adservices.servicecoretest";
    private static final String RESOURCE_NAME = "ad_services_config";
    private static final String RESOURCE_TYPE = "xml";
    private static final String MISSING_RESOURCE_ERROR_MSG =
            "Missing resource attribute in AdServices config property!";

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this).spyStatic(SdkLevel.class).build();

    @Test
    public void testGetAdServicesConfigResourceId_valid() throws Exception {
        mockSdkLevelR();
        XmlResourceParser parser = createParser(R.xml.android_manifest_valid);
        Resources resources =
                mContext.getPackageManager().getResourcesForApplication(TEST_APP_PACKAGE_NAME);
        int resId = AndroidManifestConfigParser.getAdServicesConfigResourceId(parser, resources);

        // Check if the resolved resource ID is pointing to the correct resource name and type.
        assertThat(resources.getResourceEntryName(resId)).isEqualTo(RESOURCE_NAME);
        assertThat(resources.getResourceTypeName(resId)).isEqualTo(RESOURCE_TYPE);
    }

    @Test
    public void testGetAdServicesConfigResourceId_attrOrderChange_valid() throws Exception {
        mockSdkLevelR();
        XmlResourceParser parser = createParser(R.xml.android_manifest_diff_attr_order_valid);
        Resources resources =
                mContext.getPackageManager().getResourcesForApplication(TEST_APP_PACKAGE_NAME);
        int resId = AndroidManifestConfigParser.getAdServicesConfigResourceId(parser, resources);

        // Check if the resolved resource ID is pointing to the correct resource name and type.
        assertThat(resources.getResourceEntryName(resId)).isEqualTo(RESOURCE_NAME);
        assertThat(resources.getResourceTypeName(resId)).isEqualTo(RESOURCE_TYPE);
    }

    @Test
    public void testGetAdServicesConfigResourceId_withPropertyNameRef_valid() throws Exception {
        mockSdkLevelR();
        XmlResourceParser parser =
                createParser(R.xml.android_manifest_property_name_referenced_valid);
        Resources resources =
                mContext.getPackageManager().getResourcesForApplication(TEST_APP_PACKAGE_NAME);
        int resId = AndroidManifestConfigParser.getAdServicesConfigResourceId(parser, resources);

        // Check if the resolved resource ID is pointing to the correct resource name and type.
        assertThat(resources.getResourceEntryName(resId)).isEqualTo(RESOURCE_NAME);
        assertThat(resources.getResourceTypeName(resId)).isEqualTo(RESOURCE_TYPE);
    }

    @Test
    public void testGetAdServicesConfigResourceId_missingProperty() throws Exception {
        mockSdkLevelR();
        XmlResourceParser parser = createParser(R.xml.android_manifest_missing_property);
        Resources resources =
                mContext.getPackageManager().getResourcesForApplication(TEST_APP_PACKAGE_NAME);
        assertWithMessage("resource id for android_manifest_missing_property.xml")
                .that(AndroidManifestConfigParser.getAdServicesConfigResourceId(parser, resources))
                .isNull();
    }

    @Test
    public void testGetAdServicesConfigResourceId_missingPropertyInsideApplication()
            throws Exception {
        mockSdkLevelR();
        XmlResourceParser parser =
                createParser(R.xml.android_manifest_missing_property_inside_application);
        Resources resources =
                mContext.getPackageManager().getResourcesForApplication(TEST_APP_PACKAGE_NAME);
        assertWithMessage(
                        "resource id for android_manifest_missing_property_inside_application.xml")
                .that(AndroidManifestConfigParser.getAdServicesConfigResourceId(parser, resources))
                .isNull();
    }

    @Test
    public void testGetAdServicesConfigResourceId_missingPropertyDueToNoNameAttr()
            throws Exception {
        mockSdkLevelR();
        XmlResourceParser parser = createParser(R.xml.android_manifest_missing_property_name);
        Resources resources =
                mContext.getPackageManager().getResourcesForApplication(TEST_APP_PACKAGE_NAME);
        assertWithMessage("resource id for android_manifest_missing_property_name.xml")
                .that(AndroidManifestConfigParser.getAdServicesConfigResourceId(parser, resources))
                .isNull();
    }

    @Test
    public void testGetAdServicesConfigResourceId_missingPropertyDueToIncorrectName()
            throws Exception {
        mockSdkLevelR();
        XmlResourceParser parser = createParser(R.xml.android_manifest_incorrect_property_name);
        Resources resources =
                mContext.getPackageManager().getResourcesForApplication(TEST_APP_PACKAGE_NAME);
        assertWithMessage("resource id for android_manifest_incorrect_property_name.xml")
                .that(AndroidManifestConfigParser.getAdServicesConfigResourceId(parser, resources))
                .isNull();
    }

    @Test
    public void testGetAdServicesConfigResourceId_missingPropertyDueToIncorrectDepth()
            throws Exception {
        mockSdkLevelR();
        XmlResourceParser parser =
                createParser(R.xml.android_manifest_missing_property_incorrect_depth);
        Resources resources =
                mContext.getPackageManager().getResourcesForApplication(TEST_APP_PACKAGE_NAME);
        assertWithMessage("resource id for android_manifest_missing_property_incorrect_depth.xml")
                .that(AndroidManifestConfigParser.getAdServicesConfigResourceId(parser, resources))
                .isNull();
    }

    @Test
    public void testGetAdServicesConfigResourceId_invalidPropertyDueToMissingResourceAttr()
            throws Exception {
        mockSdkLevelR();
        XmlResourceParser parser = createParser(R.xml.android_manifest_missing_resource);
        Resources resources =
                mContext.getPackageManager().getResourcesForApplication(TEST_APP_PACKAGE_NAME);
        Exception e =
                assertThrows(
                        NoSuchElementException.class,
                        () ->
                                AndroidManifestConfigParser.getAdServicesConfigResourceId(
                                        parser, resources));
        assertThat(e.getMessage()).isEqualTo(MISSING_RESOURCE_ERROR_MSG);
    }

    @Test
    public void testGetAdServicesConfigResourceId_onSPlus_throwsException() throws Exception {
        mockSdkLevelS();
        XmlResourceParser parser = createParser(R.xml.android_manifest_missing_resource);
        Resources resources =
                mContext.getPackageManager().getResourcesForApplication(TEST_APP_PACKAGE_NAME);
        assertThrows(
                IllegalStateException.class,
                () -> AndroidManifestConfigParser.getAdServicesConfigResourceId(parser, resources));
    }

    private XmlResourceParser createParser(int resId) throws Exception {
        return mContext.getPackageManager()
                .getResourcesForApplication(mContext.getPackageName())
                .getXml(resId);
    }

    private void mockSdkLevelS() {
        mockIsAtLeastS(true);
    }

    private void mockSdkLevelR() {
        mockIsAtLeastS(false);
    }
}
