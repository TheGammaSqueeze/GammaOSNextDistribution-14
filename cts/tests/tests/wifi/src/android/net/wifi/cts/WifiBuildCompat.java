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

package android.net.wifi.cts;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.android.compatibility.common.util.ApiLevelUtil;

/**
 * Wrapper class for checking the wifi module version.
 *
 * Wifi CTS tests for a dessert release can be run on older dessert releases as a part of MTS.
 * Since wifi module is optional, not all older dessert release will contain the wifi module from
 * the provided dessert release (which means we cannot use new wifi API's on those devices).
 *
 * <p>
 * This utility tries to help solve that problem by trying to check if the device is running at
 * least
 * <li> The provided dessert release using {@link ApiLevelUtil}, OR</li>
 * <li> The wifi module from the provided dessert release on an older dessert release device</li>
 *
 * In either case above, we can somewhat safely assume that the wifi API's from the provided dessert
 * release are present and behave the way we expect to.
 * </p>
 *
 * Note: This does not check for granular wifi module version codes, only that it is some version
 * of the module from the provided dessert release.
 */
public class WifiBuildCompat {
    private static final String TAG = "WifiBuildCompat";

    private static final String WIFI_PACKAGE_NAME_SUFFIX = ".android.wifi";

    private static final long WIFI_APEX_BASE_VERSION_CODE_FOR_S = 310000000;

    private static long getWifiApexVersionCode(@NonNull Context ctx) {
        PackageManager packageManager = ctx.getPackageManager();
        long wifiStackVersion = 0;
        String wifiPackageName = null;
        for (PackageInfo packageInfo : packageManager
                .getInstalledPackages(PackageManager.MATCH_APEX)) {
            if (packageInfo.packageName.endsWith(WIFI_PACKAGE_NAME_SUFFIX)) {
                wifiPackageName = packageInfo.packageName;
                wifiStackVersion = packageInfo.getLongVersionCode();
                break;
            }
        }
        Log.v(TAG, "Wifi Module package name is " + wifiPackageName
                + ", version is " + wifiStackVersion);
        return wifiStackVersion;
    }

    private WifiBuildCompat() { }

    public static boolean isPlatformOrWifiModuleAtLeastS(@NonNull Context ctx) {
        return ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)
                || getWifiApexVersionCode(ctx) >= WIFI_APEX_BASE_VERSION_CODE_FOR_S;
    }
}
