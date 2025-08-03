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

import static com.android.adservices.service.common.AppManifestConfigCall.API_AD_SELECTION;
import static com.android.adservices.service.common.AppManifestConfigCall.API_ATTRIBUTION;
import static com.android.adservices.service.common.AppManifestConfigCall.API_CUSTOM_AUDIENCES;
import static com.android.adservices.service.common.AppManifestConfigCall.API_PROTECTED_SIGNALS;
import static com.android.adservices.service.common.AppManifestConfigCall.API_TOPICS;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_APP_DOES_NOT_EXIST;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG;
import static com.android.adservices.service.common.AppManifestConfigCall.RESULT_DISALLOWED_BY_APP;
import static com.android.adservices.service.common.AppManifestConfigCall.isAllowed;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_MANIFEST_CONFIG_PARSING_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import com.android.adservices.LogUtil;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.exception.XmlParseException;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.modules.utils.build.SdkLevel;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Objects;

/** Helper class for parsing and checking the app manifest config (<ad-services-config>). */
// TODO(b/213488783): Add persistence, so that lookup/parse is not on every request.
// Also consider if this should execute in the background.
public final class AppManifestConfigHelper {

    public static final String AD_SERVICES_CONFIG_PROPERTY =
            "android.adservices.AD_SERVICES_CONFIG";
    private static final String ANDROID_MANIFEST_FILE = "AndroidManifest.xml";

    /**
     * Parses the app's manifest config to determine whether this sdk is permitted to use the
     * Attribution API.
     *
     * @param appPackageName the package name of the app whose manifest config will be read.
     * @param enrollmentId the enrollment ID of the sdk that will be checked against the app's
     *     manifest config.
     * @return {@code true} if API access is allowed, {@code false} if it's not or if there was an
     *     error parsing the app manifest config.
     */
    public static boolean isAllowedAttributionAccess(String appPackageName, String enrollmentId) {
        return isAllowedApiAccess(
                "isAllowedAttributionAccess()",
                API_ATTRIBUTION,
                appPackageName,
                enrollmentId,
                config -> config.isAllowedAttributionAccess(enrollmentId));
    }

    /**
     * Parses the app's manifest config to determine whether the given {@code enrollmentId}
     * associated with an ad tech is permitted to use the Custom Audience API.
     *
     * @param appPackageName the package name of the app whose manifest config will be read
     * @param enrollmentId the enrollment ID associate with the ad tech
     * @return {@code true} if API access is allowed, {@code false} if it's not or if there was an
     *     error parsing the app manifest config.
     */
    public static boolean isAllowedCustomAudiencesAccess(
            String appPackageName, String enrollmentId) {
        return isAllowedApiAccess(
                "isAllowedCustomAudiencesAccess()",
                API_CUSTOM_AUDIENCES,
                appPackageName,
                enrollmentId,
                config -> config.isAllowedCustomAudiencesAccess(enrollmentId));
    }

    /**
     * Parses the app's manifest config to determine whether the given {@code enrollmentId}
     * associated with an ad tech is permitted to use the Protected Signals API.
     *
     * @param appPackageName the package name of the app whose manifest config will be read
     * @param enrollmentId the enrollment ID associate with the ad tech
     * @return {@code true} if API access is allowed, {@code false} if it's not or if there was an
     *     error parsing the app manifest config.
     */
    public static boolean isAllowedProtectedSignalsAccess(
            String appPackageName, String enrollmentId) {
        return isAllowedApiAccess(
                "isAllowedProtectedSignalsAccess()",
                API_PROTECTED_SIGNALS,
                appPackageName,
                enrollmentId,
                config -> config.isAllowedProtectedSignalsAccess(enrollmentId));
    }

    /**
     * Parses the app's manifest config to determine whether the given {@code enrollmentId}
     * associated with an ad tech is permitted to use the Ad Selection API.
     *
     * @param appPackageName the package name of the app whose manifest config will be read
     * @param enrollmentId the enrollment ID associate with the ad tech
     * @return {@code true} if API access is allowed, {@code false} if it's not or if there was an
     *     error parsing the app manifest config.
     */
    public static boolean isAllowedAdSelectionAccess(String appPackageName, String enrollmentId) {
        boolean adSelectionAccess =
                isAllowedApiAccess(
                        "isAllowedAdSelectionAccess()",
                        API_AD_SELECTION,
                        appPackageName,
                        enrollmentId,
                        config -> config.isAllowedAdSelectionAccess(enrollmentId));
        // You can use the ad selection APIs with any of the 3 manifest permissions
        return adSelectionAccess
                || isAllowedCustomAudiencesAccess(appPackageName, enrollmentId)
                || isAllowedProtectedSignalsAccess(appPackageName, enrollmentId);
    }

    /**
     * Parses the app's manifest config to determine whether this sdk is permitted to use the Topics
     * API.
     *
     * @param useSandboxCheck whether to use the sandbox check.
     * @param appPackageName the package name of the app whose manifest config will be read.
     * @param enrollmentId the enrollment ID of the sdk that will be checked against the app's
     *     manifest config.
     * @return {@code true} if API access is allowed, {@code false} if it's not or if there was an
     *     error parsing the app manifest config.
     */
    public static boolean isAllowedTopicsAccess(
            boolean useSandboxCheck, String appPackageName, String enrollmentId) {
        return isAllowedApiAccess(
                "isAllowedTopicsAccess()",
                API_TOPICS,
                appPackageName,
                enrollmentId,
                config -> {
                    // If the request comes directly from the app, check that the app has declared
                    // that it includes this Sdk library.
                    if (!useSandboxCheck) {
                        return (config.getIncludesSdkLibraryConfig().contains(enrollmentId)
                                        && isAllowed(config.isAllowedTopicsAccess(enrollmentId)))
                                ? RESULT_ALLOWED_APP_ALLOWS_SPECIFIC_ID
                                : RESULT_DISALLOWED_BY_APP;
                    }

                    // If the request comes from the SdkRuntime, then the app had to have declared
                    // the Sdk using <uses-sdk-library>, so no need to check.
                    return config.isAllowedTopicsAccess(enrollmentId);
                });
    }

    @Nullable
    private static XmlResourceParser getXmlParser(String appPackageName)
            throws NameNotFoundException, XmlParseException, XmlPullParserException, IOException {
        Context context = ApplicationContextSingleton.get();
        LogUtil.v("getXmlParser(%s): context=%s", appPackageName, context);

        // NOTE: resources is only used pre-S, but it must be called regardless to make sure the app
        // exists
        Resources resources =
                context.getPackageManager().getResourcesForApplication(appPackageName);
        Integer resId =
                SdkLevel.isAtLeastS()
                        ? getAdServicesConfigResourceIdOnExistingPackageOnSPlus(
                                context, appPackageName)
                        : getAdServicesConfigResourceIdOnRMinus(context, resources, appPackageName);

        return resId != null ? resources.getXml(resId) : null;
    }

    @Nullable
    private static Integer getAdServicesConfigResourceIdOnExistingPackageOnSPlus(
            Context context, String appPackageName) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageManager.Property property =
                    pm.getProperty(AD_SERVICES_CONFIG_PROPERTY, appPackageName);
            return property.getResourceId();
        } catch (NameNotFoundException e) {
            LogUtil.v("getAdServicesConfigResourceIdOnSPlus(%s) failed: %s", appPackageName, e);
            return null;
        }
    }

    @Nullable
    private static Integer getAdServicesConfigResourceIdOnRMinus(
            Context context, Resources resources, String appPackageName)
            throws NameNotFoundException, XmlPullParserException, IOException {
        // PackageManager::getProperty(..) API is only available on S+. For R-, we will need to load
        // app's manifest and parse. See go/rbp-manifest.
        AssetManager assetManager =
                context.createPackageContext(appPackageName, /* flags= */ 0).getAssets();
        XmlResourceParser parser = assetManager.openXmlResourceParser(ANDROID_MANIFEST_FILE);
        return AndroidManifestConfigParser.getAdServicesConfigResourceId(parser, resources);
    }

    private static boolean isAllowedApiAccess(
            String method,
            int api,
            String appPackageName,
            String enrollmentId,
            ApiAccessChecker checker) {
        Objects.requireNonNull(appPackageName);
        Objects.requireNonNull(enrollmentId);

        AppManifestConfigCall call = new AppManifestConfigCall(appPackageName, api);
        boolean enabledByDefault = FlagsFactory.getFlags().getAppConfigReturnsEnabledByDefault();

        try {
            XmlResourceParser in = getXmlParser(appPackageName);
            if (in == null) {
                call.result =
                        enabledByDefault
                                ? RESULT_ALLOWED_BY_DEFAULT_APP_DOES_NOT_HAVE_CONFIG
                                : RESULT_DISALLOWED_APP_DOES_NOT_HAVE_CONFIG;
                LogUtil.v(
                        "%s: returning %b for app (%s) that doesn't have the AdServices XML config",
                        method, enabledByDefault, appPackageName);
                return enabledByDefault;
            }
            AppManifestConfig appManifestConfig =
                    AppManifestConfigParser.getConfig(in, enabledByDefault);
            call.result = checker.isAllowedAccess(appManifestConfig);
        } catch (NameNotFoundException e) {
            call.result = RESULT_DISALLOWED_APP_DOES_NOT_EXIST;
            LogUtil.v(
                    "Name not found while looking for manifest for app %s: %s", appPackageName, e);
        } catch (Exception e) {
            call.result = RESULT_DISALLOWED_APP_CONFIG_PARSING_ERROR;
            LogUtil.e(e, "App manifest parse failed.");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_MANIFEST_CONFIG_PARSING_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        } finally {
            AppManifestConfigMetricsLogger.logUsage(call);
        }
        return isAllowed(call.result);
    }

    private interface ApiAccessChecker {
        int isAllowedAccess(AppManifestConfig config);
    }

    private AppManifestConfigHelper() {
        throw new UnsupportedOperationException("provides only static methods");
    }
}
