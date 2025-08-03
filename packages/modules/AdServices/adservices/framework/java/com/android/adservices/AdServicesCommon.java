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
package com.android.adservices;

import android.adservices.adid.AdIdProviderService;
import android.adservices.appsetid.AppSetIdProviderService;
import android.adservices.cobalt.AdServicesCobaltUploadService;
import android.adservices.extdata.AdServicesExtDataStorageService;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import java.util.List;

// TODO(b/295321663): need to split constants into AdServicesCommonConstants so they can be used by
// host-side test artifacts.

/**
 * Common constants for AdServices
 *
 * @hide
 */
public class AdServicesCommon {
    private AdServicesCommon() {}

    /** Intent action to discover the Topics service in the APK. */
    public static final String ACTION_TOPICS_SERVICE = "android.adservices.TOPICS_SERVICE";

    /** Intent action to discover the Custom Audience service in the APK. */
    public static final String ACTION_CUSTOM_AUDIENCE_SERVICE =
            "android.adservices.customaudience.CUSTOM_AUDIENCE_SERVICE";

    /** Intent action to discover the AdSelection service in the APK. */
    public static final String ACTION_AD_SELECTION_SERVICE =
            "android.adservices.adselection.AD_SELECTION_SERVICE";

    /** Intent action to discover the protected signals service in the APK. */
    public static final String ACTION_PROTECTED_SIGNALS_SERVICE =
            "android.adservices.signals.PROTECTED_SIGNALS_SERVICE";

    /** Intent action to discover the Measurement service in the APK. */
    public static final String ACTION_MEASUREMENT_SERVICE =
            "android.adservices.MEASUREMENT_SERVICE";

    /** Intent action to discover the AdId service in the APK. */
    public static final String ACTION_ADID_SERVICE = "android.adservices.ADID_SERVICE";

    /** Intent action to discover the AdId Provider service. */
    public static final String ACTION_ADID_PROVIDER_SERVICE = AdIdProviderService.SERVICE_INTERFACE;

    /** Intent action to discover the AppSetId service in the APK. */
    public static final String ACTION_APPSETID_SERVICE = "android.adservices.APPSETID_SERVICE";

    /** Intent action to discover the AppSetId Provider service. */
    public static final String ACTION_APPSETID_PROVIDER_SERVICE =
            AppSetIdProviderService.SERVICE_INTERFACE;

    /** Intent action to discover the AdServicesExtDataStorageService. */
    public static final String ACTION_AD_EXT_DATA_STORAGE_SERVICE =
            AdServicesExtDataStorageService.SERVICE_INTERFACE;

    /** Intent action to discover the AdServicesCommon service in the APK. */
    public static final String ACTION_AD_SERVICES_COMMON_SERVICE =
            "android.adservices.AD_SERVICES_COMMON_SERVICE";

    /** Intent action to discover the AdServices Cobalt upload service. */
    public static final String ACTION_AD_SERVICES_COBALT_UPLOAD_SERVICE =
            AdServicesCobaltUploadService.SERVICE_INTERFACE;

    /** Intent action to discover the Shell Command service in the APK. */
    public static final String ACTION_SHELL_COMMAND_SERVICE =
            "android.adservices.SHELL_COMMAND_SERVICE";

    // Used to differentiate between AdServices APK package name and AdExtServices APK package name.
    // The AdExtServices APK package name suffix is android.ext.services.
    public static final String ADSERVICES_APK_PACKAGE_NAME_SUFFIX = "android.adservices.api";

    /** The package name suffix of the ExtServices APK on R/S */
    public static final String ADEXTSERVICES_PACKAGE_NAME_SUFFIX = "android.ext.services";

    /** Suffix for the AdServices APEX package name. */
    public static final String ADSERVICES_APEX_NAME_SUFFIX = "android.adservices";

    /**
     * Suffix for the ExtServices APEX Package name. Used to figure out the installed apex version.
     */
    public static final String EXTSERVICES_APEX_NAME_SUFFIX = "android.extservices";

    /**
     * Prefix for system properties used for debugging purposes (like simulating unsupported devices
     * or change some behavior without changing a flag).
     */
    public static final String SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX = "debug.adservices.";

    /** System property used to simulate AdServices behavior in a device that doesn't support it. */
    public static final String SYSTEM_PROPERTY_FOR_DEBUGGING_SUPPORTED_ON_DEVICE =
            SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX + "supported";

    /** System property used to simulate AdServices behavior on devices with low memory. */
    public static final String SYSTEM_PROPERTY_FOR_DEBUGGING_FEATURE_RAM_LOW =
            SYSTEM_PROPERTY_FOR_DEBUGGING_PREFIX + "low_ram_device";

    /** Path name for Adservice class names */
    public static final String ADSERVICES_CLASS_PATH_PREFIX = "com.android.adservices.";

    /** The package name of the active AdServices APK on this device. */
    public static ServiceInfo resolveAdServicesService(
            List<ResolveInfo> intentResolveInfos, String intentAction) {
        if (intentResolveInfos == null || intentResolveInfos.isEmpty()) {
            LogUtil.e(
                    "Failed to find resolveInfo for adServices service. Intent action: "
                            + intentAction);
            return null;
        }

        // On T+ devices, we may have two versions of the services present due to
        // b/263904312.
        if (intentResolveInfos.size() > 2) {
            StringBuilder intents = new StringBuilder("");
            for (ResolveInfo intentResolveInfo : intentResolveInfos) {
                if (intentResolveInfo != null && intentResolveInfo.serviceInfo != null) {
                    intents.append(intentResolveInfo.serviceInfo.packageName);
                }
            }
            LogUtil.e("Found multiple services " + intents + " for " + intentAction);
            return null;
        }

        // On T+ devices, only use the service that comes from AdServices APK. The package name of
        // AdService is com.[google.]android.adservices.api while the package name of ExtServices
        // APK is com.[google.]android.ext.services.
        ServiceInfo serviceInfo = null;

        // We have already checked if there are 0 OR more than 2 services returned.
        switch (intentResolveInfos.size()) {
            case 2:
                // In the case of 2, always use the one from AdServicesApk.
                if (intentResolveInfos.get(0) != null
                        && intentResolveInfos.get(0).serviceInfo != null
                        && intentResolveInfos.get(0).serviceInfo.packageName != null
                        && intentResolveInfos
                                .get(0)
                                .serviceInfo
                                .packageName
                                .endsWith(ADSERVICES_APK_PACKAGE_NAME_SUFFIX)) {
                    serviceInfo = intentResolveInfos.get(0).serviceInfo;
                } else if (intentResolveInfos.get(1) != null
                        && intentResolveInfos.get(1).serviceInfo != null
                        && intentResolveInfos.get(1).serviceInfo.packageName != null
                        && intentResolveInfos
                                .get(1)
                                .serviceInfo
                                .packageName
                                .endsWith(ADSERVICES_APK_PACKAGE_NAME_SUFFIX)) {
                    serviceInfo = intentResolveInfos.get(1).serviceInfo;
                }
                break;

            case 1:
                serviceInfo = intentResolveInfos.get(0).serviceInfo;
                break;
        }
        return serviceInfo;
    }
}
