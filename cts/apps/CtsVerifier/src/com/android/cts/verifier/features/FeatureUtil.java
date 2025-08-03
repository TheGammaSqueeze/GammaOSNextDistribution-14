/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.verifier.features;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserManager;

/**
 * Feature without feature flag for now will be skipped based on the devices temporarily.
 * TODO(b/189282625): replace device feature with a more specific feature.
 */
public final class FeatureUtil {

    private FeatureUtil() {
        throw new AssertionError();
    }

    /**
     * Checks whether the device supports configing (e.g. disable, enable) location
     */
    public static boolean isConfigLocationSupported(Context context) {
        return !isWatch(context);
    }

    /**
     * Checks whether the device supports configing lock screen
     */
    public static boolean isConfigLockScreenSupported(Context context) {
        return !isWatchOrAutomotive(context);
    }

    /**
     * Checks whether the device supports customizing the lock screen message
     */
    public static boolean isCustomizeLockScreenMessageSupported(Context context) {
        return !isWatch(context);
    }

    /**
     * Checks whether the device supports password
     */
    public static boolean isPasswordSupported(Context context) {
        return !isWatch(context);
    }

    /**
     * Checks whether the device supports high password complexity
     */
    public static boolean isHighPasswordComplexitySupported(Context context) {
        return !isWatch(context);
    }

    /**
     * Checks whether the device supports Easter egg / game
     */
    public static boolean isFunSupported(Context context) {
        return !isWatchOrAutomotive(context);
    }

    /**
     * Checks whether the device supports screen timeout
     */
    public static boolean isScreenTimeoutSupported(Context context) {
        return !isWatchOrAutomotive(context);
    }

    /**
     * Checks whether the device supports third party accessibility service
     */
    public static boolean isThirdPartyAccessibilityServiceSupported(Context context) {
        return !isWatchOrAutomotive(context);
    }

    /**
     * Checks whether the device supports configuring VPN
     */
    public static boolean isConfigVpnSupported(Context context) {
        return !isWatchOrAutomotive(context);
    }
    /**
     * Checks if Disabling Keyguard is supported.
     */
    public static boolean isDisableKeyguardSupported(Context context) {
        return !isWatch(context);
    }

    /**
     * Checks if Lock Task is supported.
     */
    public static boolean isLockTaskSupported(Context context) {
        return !isWatch(context) && !isTelevision(context);
    }

    /**
     * Checks if Status Bar is supported.
     */
    public static boolean isStatusBarSupported(Context context) {
        return !isWatch(context) && !isTelevision(context);
    }

    /**
     * Checks if Data Roaming is supported.
     */
    public static boolean isDataRoamingSupported(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) && !isWatch(context);
    }

    /**
     * Checks if Swipe To Unlock is supported.
     */
    public static boolean isSwipeToUnlockSupported(Context context) {
        return !isAutomotive(context);
    }

    /**
     * Checks whether the device supports installing from unknown sources
     */
    public static boolean isInstallUnknownSourcesSupported(Context context) {
        return !isWatchOrAutomotive(context);
    }

    /**
     * Checks whether the device requires new user disclaimer acknowledgement for managed user.
     */
    public static boolean isNewManagerUserDisclaimerRequired(Context context) {
        return isAutomotive(context);
    }

    /**
     * Checks whether the device supports file transfer.
     */
    public static boolean isUsbFileTransferSupported(Context context) {
        return !isWatchOrAutomotive(context) && !isTelevision(context);
    }

    /**
     * Checks whether back touch is supported on the window.
     */
    public static boolean isBackTouchesSupported(Context context) {
        return !isWatchOrAutomotiveOrTv(context);
    }

    /**
     * Checks whether the device is watch .
     */
    public static boolean isWatch(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    /**
     * Checks whether the device is a TV
     */
    public static boolean isTelevision(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    /**
     * Checks whether the device is watch or automotive
     */
    private static boolean isWatchOrAutomotive(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                || pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    /**
     * Checks whether the device is watch, automotive or TV
     */
    public static boolean isWatchOrAutomotiveOrTv(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                || pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY);
    }


    /**
     * Checks whether the device is automotive
     */
    public static boolean isAutomotive(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    /**
     * Checks whether the device supports managed secondary users.
     */
    public static boolean supportManagedSecondaryUsers(Context context) {
        PackageManager pm = context.getPackageManager();

        // TODO(b/283140235): Remove this check once splitscreen_multitasking device supports
        //  FEATURE_MANAGED_USERS
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAR_SPLITSCREEN_MULTITASKING)
                && isAutomotive(context)) {
            return false;
        }

        return (pm.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)
                || UserManager.isHeadlessSystemUserMode()) && UserManager.supportsMultipleUsers();
    }

    /**
     * Checks whether the device shows keyguard when the user doesn't have credentials.
     */
    public static boolean isKeyguardShownWhenUserDoesntHaveCredentials(Context context) {
        return !isAutomotive(context) && !isWatch(context);
    }

    /**
     * Checks whether the device supports camera.
     */
    public static boolean supportCameraFeature(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }
}
