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

package android.net.wifi.cts;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;

import java.io.File;
import java.util.List;

public class WifiResourceUtil {
    private static final String ACTION_RESOURCES_APK =
            "com.android.server.wifi.intent.action.SERVICE_WIFI_RESOURCES_APK";

    private static final String WIFI_APEX_NAME = "com.android.wifi";

    private static final String WIFI_APEX_PATH =
            new File("/apex", WIFI_APEX_NAME).getAbsolutePath();

    private final Resources sWifiResources;
    private final Context sContext;
    private String sWifiResourcesPackageName;

    public WifiResourceUtil(Context context) {
        sContext = context;
        sWifiResources = getWifiResources();
    }

    private Resources getWifiResources() {
        final PackageManager pm = sContext.getPackageManager();
        List<ResolveInfo> resolveInfos;
        resolveInfos = pm.queryIntentActivities(new Intent(ACTION_RESOURCES_APK),
                PackageManager.MATCH_SYSTEM_ONLY);
        // remove apps that don't live in the Wifi apex
        resolveInfos.removeIf(info ->
                !info.activityInfo.applicationInfo.sourceDir.startsWith(WIFI_APEX_PATH));

        assertEquals(1, resolveInfos.size());
        sWifiResourcesPackageName = resolveInfos.get(0).activityInfo.applicationInfo.packageName;
        try {
            return sContext.createPackageContext(sWifiResourcesPackageName, 0).getResources();
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean getWifiBoolean(String name) throws PackageManager.NameNotFoundException {
        return sWifiResources.getBoolean(sWifiResources.getIdentifier(name, "bool",
                sWifiResourcesPackageName));
    }
}
