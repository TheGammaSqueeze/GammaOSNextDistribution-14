/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.car.pm;

import static android.car.content.pm.CarPackageManager.DRIVING_SAFETY_ACTIVITY_METADATA_REGIONS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.content.pm.CarPackageManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;

import com.android.car.CarLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *  Read App meta data and look for Distraction Optimization(DO) tags.
 *  An app can tag a distraction optimized activity to be DO by adding the following meta-data
 *  to that <activity> element:
 *
 *  <pre>{@code
 *  <activity>
 *      <meta-data android:name="distractionOptimized" android:value="true"/>
 *  </activity>
 *  }</pre>
 *
 */
public class CarAppMetadataReader {

    private static final String TAG = CarLog.tagFor(CarAppMetadataReader.class);

    /** Name of the meta-data attribute of the Activity that denotes distraction optimized */
    private static final String DO_METADATA_ATTRIBUTE = "distractionOptimized";

    private static final List<String> ALL_REGION_ONLY = Collections.singletonList(
            CarPackageManager.DRIVING_SAFETY_REGION_ALL);

    @Nullable
    private static ActivityInfo[] getAllActivitiesForPackageAsUser(Context context,
            String packageName, @UserIdInt int userId)  throws NameNotFoundException {
        final PackageManager pm = context.getPackageManager();
        PackageInfo pkgInfo =
                PackageManagerHelper.getPackageInfoAsUser(pm, packageName,
                        PackageManager.GET_ACTIVITIES
                                | PackageManager.GET_META_DATA
                                | PackageManager.MATCH_DISABLED_COMPONENTS
                                | PackageManager.MATCH_DIRECT_BOOT_AWARE
                                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                        userId);
        if (pkgInfo == null) {
            return null;
        }

        return pkgInfo.activities;
    }

    /**
     * Parses the given package and returns Distraction Optimized information, if present.
     *
     * @return Array of DO activity names in the given package
     */
    @Nullable
    public static String[] findDistractionOptimizedActivitiesAsUser(Context context,
            String packageName, @UserIdInt int userId, @NonNull String drivingSafetyRegion)
            throws NameNotFoundException {


        // Check if any of the activities in the package are DO by checking all the
        // <activity> elements. MATCH_DISABLED_COMPONENTS is included so that we are immediately
        // prepared to respond to any components that toggle from disabled to enabled.
        ActivityInfo[] activities = getAllActivitiesForPackageAsUser(context, packageName, userId);
        if (activities == null) {
            if (CarPackageManagerService.DBG) {
                Slogf.d(TAG, "Null Activities for " + packageName);
            }
            return null;
        }
        List<String> optimizedActivityList = new ArrayList();
        for (ActivityInfo activity : activities) {
            Bundle metaData = activity.metaData;
            if (metaData == null) {
                continue;
            }
            String regionString = metaData.getString(DRIVING_SAFETY_ACTIVITY_METADATA_REGIONS,
                    CarPackageManager.DRIVING_SAFETY_REGION_ALL);
            if (!isRegionSupported(regionString, drivingSafetyRegion)) {
                continue;
            }
            if (metaData.getBoolean(DO_METADATA_ATTRIBUTE, false)) {
                if (CarPackageManagerService.DBG) {
                    Slogf.d(TAG,
                            "DO Activity:" + activity.packageName + "/" + activity.name);
                }
                optimizedActivityList.add(activity.name);
            }
        }
        if (optimizedActivityList.isEmpty()) {
            return null;
        }
        return optimizedActivityList.toArray(new String[optimizedActivityList.size()]);
    }

    /**
     * Check {@link CarPackageManager#getSupportedDrivingSafetyRegionsForActivityAsUser(
     * String, String, int)}.
     */
    public static List<String> getSupportedDrivingSafetyRegionsForActivityAsUser(Context context,
            String packageName, String activityClassName, @UserIdInt int userId)
            throws NameNotFoundException {
        ActivityInfo[] activities = getAllActivitiesForPackageAsUser(context, packageName, userId);
        if (activities == null) {
            throw new NameNotFoundException();
        }
        for (ActivityInfo info: activities) {
            if (!info.name.equals(activityClassName)) {
                continue;
            }
            // Found activity
            Bundle metaData = info.metaData;
            if (metaData == null) {
                return Collections.EMPTY_LIST;
            }
            if (!metaData.getBoolean(DO_METADATA_ATTRIBUTE, false)) {
                // no distractionOptimized, so region metadata does not matter.
                return Collections.EMPTY_LIST;
            }
            String regionString = metaData.getString(DRIVING_SAFETY_ACTIVITY_METADATA_REGIONS,
                    CarPackageManager.DRIVING_SAFETY_REGION_ALL);
            String[] regions = regionString.split(",");
            for (String region: regions) {
                if (CarPackageManager.DRIVING_SAFETY_REGION_ALL.equals(region)) {
                    return ALL_REGION_ONLY;
                }
            }
            return Arrays.asList(regions);
        }
        throw new NameNotFoundException();
    }

    private static boolean isRegionSupported(String regionString, String currentRegion) {
        if (regionString.isEmpty()) { // not specified means all regions.
            return true;
        }
        if (currentRegion.equals(CarPackageManager.DRIVING_SAFETY_REGION_ALL)) {
            return true;
        }
        String[] regions = regionString.split(",");
        for (String region: regions) {
            if (CarPackageManager.DRIVING_SAFETY_REGION_ALL.equals(region)) {
                return true;
            }
            if (currentRegion.equals(region)) {
                return true;
            }
        }
        // valid regions but does not match currentRegion.
        if (CarPackageManagerService.DBG) {
            Slogf.d(TAG,
                    "isRegionSupported not supported, regionString:" + regionString
                            + " region:" + currentRegion);
        }
        return false;
    }
}
