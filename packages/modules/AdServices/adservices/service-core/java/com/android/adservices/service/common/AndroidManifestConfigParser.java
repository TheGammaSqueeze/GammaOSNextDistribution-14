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

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import android.annotation.NonNull;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/** Parsing utils for AndroidManifest XML. */
public final class AndroidManifestConfigParser {
    private static final String APPLICATION_TAG = "application";
    private static final String PROPERTY_TAG = "property";
    private static final String NAME_ATTRIBUTE = "name";
    private static final String RESOURCE_ATTRIBUTE = "resource";
    private static final String RESOLVED_RESOURCE_ID_PREFIX = "@";
    // The <property> is strictly expected to reside inside <application>, which must be defined
    // inside <manifest>. Therefore, the expected depth for the AdServices config property is 3.
    private static final int AD_SERVICES_CONFIG_PROPERTY_DEPTH = 3;

    private AndroidManifestConfigParser() {
        // Prevent instantiation.
    }

    /**
     * Parses an app's compiled AndroidManifest XML resource to obtain AdServices config resource
     * ID. The property is expected to be defined strictly inside <application>, which can only be
     * defined inside <manifest> at an overall element depth of 3 in the XML file. The parser is
     * intentionally meant to be lightweight and doesn't attempt to validate anything in the XML
     * file apart from the AdServices config property itself. Returns {@code null} if AdServices
     * config's resource ID cannot be detected in the XML.
     *
     * @param parser the XmlResourceParser representing the app's AndroidManifest compiled XML file.
     * @param resources the resources belonging to the app.
     */
    @Nullable
    public static Integer getAdServicesConfigResourceId(
            @NonNull XmlResourceParser parser, @NonNull Resources resources)
            throws XmlPullParserException, IOException {
        if (SdkLevel.isAtLeastS()) {
            throw new IllegalStateException(
                    "Attempt to custom parse AndroidManifest on Android S+. Use "
                            + "PackageManager::getProperty to parse property instead!");
        }
        Objects.requireNonNull(parser);
        Objects.requireNonNull(resources);

        boolean isInsideApplication = false;
        int eventType = parser.next();
        while (eventType != END_DOCUMENT && !hasReachedEndOfApplication(parser)) {
            if (eventType == START_TAG) {
                if (!isInsideApplication && isTagType(parser, APPLICATION_TAG)) {
                    isInsideApplication = true;
                } else if (isInsideApplication
                        && parser.getDepth() == AD_SERVICES_CONFIG_PROPERTY_DEPTH
                        && isTagType(parser, PROPERTY_TAG)) {
                    Optional<Integer> maybeResourceId =
                            getResourceIdIfAdServicesProperty(parser, resources);
                    if (maybeResourceId.isPresent()) return maybeResourceId.get();
                }
            }
            eventType = parser.next();
        }

        return null;
    }

    private static boolean hasReachedEndOfApplication(@NonNull XmlResourceParser parser)
            throws XmlPullParserException {
        return parser.getEventType() == END_TAG && isTagType(parser, APPLICATION_TAG);
    }

    private static boolean isTagType(@NonNull XmlResourceParser parser, @NonNull String tag) {
        return tag.equals(parser.getName());
    }

    private static Optional<Integer> getResourceIdIfAdServicesProperty(
            @NonNull XmlResourceParser parser, @NonNull Resources resources) {
        Optional<String> resourceValue = Optional.empty();
        boolean isAdServicesProperty = false;
        final int numAttributes = parser.getAttributeCount();
        for (int attrIndex = 0; attrIndex < numAttributes; attrIndex++) {
            if (isAttributeType(parser, NAME_ATTRIBUTE, attrIndex)) {
                final String attributeVal = parser.getAttributeValue(attrIndex);
                isAdServicesProperty = isAdServicesPropertyName(attributeVal, resources);
                if (!isAdServicesProperty) break;
            } else if (isAttributeType(parser, RESOURCE_ATTRIBUTE, attrIndex)) {
                final String attributeVal = parser.getAttributeValue(attrIndex);
                resourceValue = Optional.ofNullable(attributeVal);
            }

            // Already extracted raw resource value belonging to AdServices config property.
            // No need to check other attributes.
            if (isAdServicesProperty && resourceValue.isPresent()) break;
        }

        if (!isAdServicesProperty) return Optional.empty();

        if (resourceValue.isEmpty()) {
            throw new NoSuchElementException(
                    "Missing resource attribute in AdServices config property!");
        }

        final String rawResourceVal = resourceValue.get();
        Objects.requireNonNull(rawResourceVal);
        final Optional<Integer> resId = maybeGetResolvedResourceId(rawResourceVal.strip());
        if (resId.isEmpty()) {
            throw new IllegalStateException(
                    "AdServices config property resource not resolved to a resource ID!");
        }

        return resId;
    }

    private static boolean isAttributeType(
            @NonNull XmlResourceParser parser, @NonNull String attr, int attrIndex) {
        return attr.equals(parser.getAttributeName(attrIndex));
    }

    private static boolean isAdServicesPropertyName(
            @Nullable String name, @NonNull Resources resources) {
        if (name == null) return false;
        final String resolvedName = resolvePropertyName(name.strip(), resources);
        return AppManifestConfigHelper.AD_SERVICES_CONFIG_PROPERTY.equals(resolvedName);
    }

    /**
     * The android:name value can be defined in one of two ways: <property
     * android:name="@string/property_name_ref" .. /> OR <property android:name="property_name" ../>
     *
     * <p>For option 1, property_name_ref is expected to be resolved to a resource ID in the form of
     * "@<resourceId>" where resourceId is an integer. This will require an additional step in
     * resolving the resourceId to the property_name itself.
     *
     * <p>In the case of option 2, we can return property_name as-is.
     *
     * @param name value of android:name to be processed.
     * @param resources resources for the app.
     */
    private static String resolvePropertyName(@NonNull String name, @NonNull Resources resources) {
        final Optional<Integer> resId = maybeGetResolvedResourceId(name);
        return resId.isPresent() ? resources.getString(resId.get()) : name;
    }

    private static Optional<Integer> maybeGetResolvedResourceId(@NonNull String value) {
        try {
            // Values that are resolved to resource IDs in the compiled XML will look like
            // "@902323". Ensure raw value can be converted into an int after omitting "@".
            if (value.length() <= 1 || !value.startsWith(RESOLVED_RESOURCE_ID_PREFIX)) {
                return Optional.empty();
            }
            return Optional.of(Integer.parseInt(value.substring(1)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
