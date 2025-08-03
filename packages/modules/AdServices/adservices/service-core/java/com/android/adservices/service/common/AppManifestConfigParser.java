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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.XmlResourceParser;

import androidx.annotation.VisibleForTesting;

import com.android.adservices.service.exception.XmlParseException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Parser and validator for {@link AppManifestConfig} objects. */
public class AppManifestConfigParser {
    private static final String TAG_AD_SERVICES_CONFIG = "ad-services-config";
    private static final String TAG_INCLUDES_SDK_LIBRARY = "includes-sdk-library";
    static final String TAG_ATTRIBUTION = "attribution";
    static final String TAG_CUSTOM_AUDIENCES = "custom-audiences";
    static final String TAG_PROTECTED_SIGNALS = "protected-signals";
    static final String TAG_AD_SELECTION = "ad-selection";
    static final String TAG_TOPICS = "topics";
    static final String TAG_ADID = "adid";
    static final String TAG_APPSETID = "appsetid";
    private static final String ATTR_ALLOW_ALL_TO_ACCESS = "allowAllToAccess";
    private static final String ATTR_ALLOW_AD_PARTNERS_TO_ACCESS = "allowAdPartnersToAccess";
    private static final String ATTR_SDK_NAME = "name";

    private AppManifestConfigParser() {}

    @VisibleForTesting
    static AppManifestConfig getConfig(@NonNull XmlResourceParser parser)
            throws XmlParseException, XmlPullParserException, IOException {
        return getConfig(parser, /* enabledByDefault=*/ false);
    }

    /**
     * Parses and validates the given XML resource into a {@link AppManifestConfig} object.
     *
     * <p>It throws a {@link XmlParseException} if the given XML resource does not comply with the
     * app_manifest_config.xsd schema.
     *
     * @param parser the XmlParser representing the AdServices App Manifest configuration
     * @param enabledByDefault whether APIs should be enabled by default when missing from the
     */
    static AppManifestConfig getConfig(@NonNull XmlResourceParser parser, boolean enabledByDefault)
            throws XmlParseException, XmlPullParserException, IOException {
        AppManifestIncludesSdkLibraryConfig includesSdkLibraryConfig;
        AppManifestAttributionConfig attributionConfig = null;
        AppManifestCustomAudiencesConfig customAudiencesConfig = null;
        AppManifestProtectedSignalsConfig protectedSignalsConfig = null;
        AppManifestAdSelectionConfig adSelectionConfig = null;
        AppManifestTopicsConfig topicsConfig = null;
        AppManifestAdIdConfig adIdConfig = null;
        AppManifestAppSetIdConfig appSetIdConfig = null;
        List<String> includesSdkLibraries = null;

        // The first next goes to START_DOCUMENT, so we need another next to go to START_TAG.
        parser.next();
        parser.next();
        parser.require(XmlPullParser.START_TAG, null, TAG_AD_SERVICES_CONFIG);
        parser.next();

        // Walk through the config to parse required values.
        while (parser.getEventType() != XmlResourceParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlResourceParser.START_TAG) {
                parser.next();
                continue;
            }
            for (int i = 0; i < parser.getAttributeCount(); i++) {
                String attr = parser.getAttributeName(i);
                if (!attr.equals(ATTR_SDK_NAME)
                        && !attr.equals(ATTR_ALLOW_ALL_TO_ACCESS)
                        && !attr.equals(ATTR_ALLOW_AD_PARTNERS_TO_ACCESS)) {
                    throw new XmlParseException(
                            "Unknown attribute: "
                                    + attr
                                    + " [Tags and attributes are case sensitive]");
                }
            }
            switch (parser.getName()) {
                case TAG_AD_SERVICES_CONFIG:
                    break;

                case TAG_INCLUDES_SDK_LIBRARY:
                    String sdkLibrary = getSdkLibrary(parser);
                    if (sdkLibrary == null || sdkLibrary.isEmpty()) {
                        throw new XmlParseException(
                                "Sdk name not mentioned in <includes-sdk-library>");
                    }
                    if (includesSdkLibraries == null) {
                        includesSdkLibraries = new ArrayList<>();
                    }
                    if (!includesSdkLibraries.contains(sdkLibrary)) {
                        includesSdkLibraries.add(sdkLibrary);
                    }
                    break;

                case TAG_ATTRIBUTION:
                    boolean allowAllToAccess = getAllowAllToAccess(parser);
                    if (attributionConfig != null) {
                        throw new XmlParseException(
                                "Tag " + parser.getName() + " appears more than once");
                    }
                    attributionConfig =
                            new AppManifestAttributionConfig(
                                    allowAllToAccess,
                                    getAllowAdPartnersToAccess(parser, allowAllToAccess));
                    break;

                case TAG_CUSTOM_AUDIENCES:
                    allowAllToAccess = getAllowAllToAccess(parser);
                    if (customAudiencesConfig != null) {
                        throw new XmlParseException(
                                "Tag " + parser.getName() + " appears more than once");
                    }
                    customAudiencesConfig =
                            new AppManifestCustomAudiencesConfig(
                                    allowAllToAccess,
                                    getAllowAdPartnersToAccess(parser, allowAllToAccess));
                    break;

                case TAG_PROTECTED_SIGNALS:
                    allowAllToAccess = getAllowAllToAccess(parser);
                    if (protectedSignalsConfig != null) {
                        throw new XmlParseException(
                                "Tag " + parser.getName() + " appears more than once");
                    }
                    protectedSignalsConfig =
                            new AppManifestProtectedSignalsConfig(
                                    allowAllToAccess,
                                    getAllowAdPartnersToAccess(parser, allowAllToAccess));
                    break;

                case TAG_AD_SELECTION:
                    allowAllToAccess = getAllowAllToAccess(parser);
                    if (adSelectionConfig != null) {
                        throw new XmlParseException(
                                "Tag " + parser.getName() + " appears more than once");
                    }
                    adSelectionConfig =
                            new AppManifestAdSelectionConfig(
                                    allowAllToAccess,
                                    getAllowAdPartnersToAccess(parser, allowAllToAccess));
                    break;

                case TAG_TOPICS:
                    allowAllToAccess = getAllowAllToAccess(parser);
                    if (topicsConfig != null) {
                        throw new XmlParseException(
                                "Tag " + parser.getName() + " appears more than once");
                    }
                    topicsConfig =
                            new AppManifestTopicsConfig(
                                    allowAllToAccess,
                                    getAllowAdPartnersToAccess(parser, allowAllToAccess));
                    break;

                case TAG_ADID:
                    allowAllToAccess = getAllowAllToAccess(parser);
                    if (adIdConfig != null) {
                        throw new XmlParseException(
                                "Tag " + parser.getName() + " appears more than once");
                    }
                    adIdConfig =
                            new AppManifestAdIdConfig(
                                    allowAllToAccess,
                                    getAllowAdPartnersToAccess(parser, allowAllToAccess));
                    break;

                case TAG_APPSETID:
                    allowAllToAccess = getAllowAllToAccess(parser);
                    if (appSetIdConfig != null) {
                        throw new XmlParseException(
                                "Tag " + parser.getName() + " appears more than once");
                    }
                    appSetIdConfig =
                            new AppManifestAppSetIdConfig(
                                    allowAllToAccess,
                                    getAllowAdPartnersToAccess(parser, allowAllToAccess));
                    break;

                default:
                    throw new XmlParseException(
                            "Unknown tag: "
                                    + parser.getName()
                                    + " [Tags and attributes are case sensitive]");
            }
            parser.next();
        }

        includesSdkLibraryConfig =
                new AppManifestIncludesSdkLibraryConfig(enabledByDefault, includesSdkLibraries);
        return new AppManifestConfig(
                includesSdkLibraryConfig,
                attributionConfig,
                customAudiencesConfig,
                protectedSignalsConfig,
                adSelectionConfig,
                topicsConfig,
                adIdConfig,
                appSetIdConfig,
                enabledByDefault);
    }

    private static String getSdkLibrary(@NonNull XmlResourceParser parser) {
        return getAttributeValue(parser, ATTR_SDK_NAME);
    }

    private static boolean getAllowAllToAccess(@NonNull XmlResourceParser parser) {
        // getAttributeValue() returns null if the tag doesn't exist
        String allowAllToAccess = getAttributeValue(parser, ATTR_ALLOW_ALL_TO_ACCESS);
        return "true".equals(allowAllToAccess);
    }

    private static List<String> getAllowAdPartnersToAccess(
            @NonNull XmlResourceParser parser, @NonNull boolean allowAllToAccess)
            throws XmlParseException {
        String allowAdPartnersToAccess =
                getAttributeValue(parser, ATTR_ALLOW_AD_PARTNERS_TO_ACCESS);
        if (allowAdPartnersToAccess == null || allowAdPartnersToAccess.isEmpty()) {
            return new ArrayList<>();
        }
        if (allowAllToAccess) {
            throw new XmlParseException(
                    "allowAll cannot be set to true when allowAdPartners is also set");
        }
        return Arrays.asList(allowAdPartnersToAccess.split("\\s*,\\s*"));
    }

    @Nullable
    private static String getAttributeValue(@NonNull XmlResourceParser parser, String name) {
        String value = parser.getAttributeValue(/* namespace= */ null, name);
        return value == null ? null : value.trim();
    }
}
