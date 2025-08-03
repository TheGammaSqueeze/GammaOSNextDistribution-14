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

package com.android.ondevicepersonalization.services.manifest;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

/**
 * Helper class for parsing and checking app manifest configs
 */
public final class AppManifestConfigHelper {
    private static final String ON_DEVICE_PERSONALIZATION_CONFIG_PROPERTY =
            "android.ondevicepersonalization.ON_DEVICE_PERSONALIZATION_CONFIG";

    private AppManifestConfigHelper() {
    }

    /**
     * Determines if the given package's manifest contains ODP settings
     *
     * @param context     the context of the API call.
     * @param packageName the packageName of the package whose manifest config will be read
     * @return true if the ODP setting exists, false otherwise
     */
    public static Boolean manifestContainsOdpSettings(Context context,
            String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getProperty(ON_DEVICE_PERSONALIZATION_CONFIG_PROPERTY, packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }

    /** Returns the ODP manifest config for a package. */
    public static AppManifestConfig getAppManifestConfig(Context context,
            String packageName) {
        if (!manifestContainsOdpSettings(context, packageName)) {
            // TODO(b/241941021) Determine correct exception to throw
            throw new IllegalArgumentException(
                    "OdpSettings not found for package: " + packageName.toString());
        }
        PackageManager pm = context.getPackageManager();
        try {
            int resId = pm.getProperty(ON_DEVICE_PERSONALIZATION_CONFIG_PROPERTY,
                    packageName).getResourceId();
            Resources resources = pm.getResourcesForApplication(packageName);
            XmlResourceParser xmlParser = resources.getXml(resId);
            // TODO(b/239479120) Update to avoid re-parsing the XML too frequently if required
            return AppManifestConfigParser.getConfig(xmlParser);
        } catch (Exception e) {
            // TODO(b/241941021) Determine correct exception to throw
            throw new IllegalArgumentException(
                    "Failed to parse manifest for package: " + packageName, e);
        }
    }

    /**
     * Gets the download URL from package's ODP settings config
     *
     * @param context     the context of the API call.
     * @param packageName the packageName of the package whose manifest config will be read
     */
    public static String getDownloadUrlFromOdpSettings(Context context, String packageName) {
        return getAppManifestConfig(context, packageName).getDownloadUrl();
    }

    /**
     * Gets the service name from package's ODP settings config
     *
     * @param context     the context of the API call.
     * @param packageName the packageName of the package whose manifest config will be read
     */
    public static String getServiceNameFromOdpSettings(Context context,
            String packageName) {
        return getAppManifestConfig(context, packageName).getServiceName();
    }

    /**
     * Gets the federated compute service remote server url from package's ODP settings config
     *
     * @param context     the context of the API call.
     * @param packageName the packageName of the package whose manifest config will be read
     */
    public static String getFcRemoteServerUrlFromOdpSettings(Context context,
            String packageName) {
        return getAppManifestConfig(context, packageName).getFcRemoteServerUrl();
    }
}
